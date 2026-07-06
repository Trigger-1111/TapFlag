param(
    [string]$ServerDir = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server",
    [string]$JarSrc    = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\target\TapFlag-1.0-SNAPSHOT.jar",
    [string]$JarDst    = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server\plugins\TapFlag-1.0-SNAPSHOT.jar"
)

$JAVA = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"
$MVN  = "C:\tools\apache-maven-3.9.9\bin\mvn.cmd"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"

$srcDir = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\src"

Write-Host "=== TapFlag watch-deploy ===" -ForegroundColor Cyan
Write-Host "Watching: $srcDir" -ForegroundColor Yellow
Write-Host "Deploy  : $JarDst" -ForegroundColor Yellow
Write-Host "Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

$watcher = [System.IO.FileSystemWatcher]::new($srcDir)
$watcher.IncludeSubdirectories = $true
$watcher.Filter = "*.java"
$watcher.NotifyFilter = [System.IO.NotifyFilters]::LastWrite -bor [System.IO.NotifyFilters]::FileName

$script:pendingBuild = $false
$lastBuild = [DateTime]::MinValue

$changed = Register-ObjectEvent $watcher Changed -Action { $script:pendingBuild = $true }
$renamed = Register-ObjectEvent $watcher Renamed -Action { $script:pendingBuild = $true }
$watcher.EnableRaisingEvents = $true

function Try-Reload {
    try {
        $result = & $JAVA -cp $ServerDir RconCommand "reload confirm" 2>&1
        if ($result -match "ok") {
            Write-Host "  Server reloaded via RCON" -ForegroundColor Cyan
        }
    } catch { }
}

try {
    while ($true) {
        Start-Sleep -Seconds 2
        if ($pendingBuild -and ([DateTime]::Now - $lastBuild).TotalSeconds -gt 3) {
            $pendingBuild = $false
            $lastBuild = [DateTime]::Now
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Change detected - building..." -ForegroundColor Cyan

            $result = & $MVN package -q -f "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\pom.xml" 2>&1
            if ($LASTEXITCODE -eq 0) {
                Copy-Item $JarSrc $JarDst -Force
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Deployed OK" -ForegroundColor Green
                Try-Reload
            } else {
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Build FAILED:" -ForegroundColor Red
                $result | Select-Object -Last 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
            }
        }
    }
} finally {
    Unregister-Event $changed.Id -ErrorAction SilentlyContinue
    Unregister-Event $renamed.Id -ErrorAction SilentlyContinue
    $watcher.Dispose()
}
