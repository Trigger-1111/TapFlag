Set-Location $PSScriptRoot

$JAVA = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

# Step 1: Fix server.properties (BOM-free, level-name=tap_flag)
$props = Get-Content "server.properties" -Raw
$props = $props -replace "level-name=\S+", "level-name=tap_flag"
$props = $props -replace "online-mode=true", "online-mode=false"
[System.IO.File]::WriteAllText(
    (Resolve-Path "server.properties").Path,
    $props,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Host "[1/4] server.properties updated (level-name=tap_flag)"

# Step 2: Start Purpur to generate tap_flag/level.dat, then stop gracefully
$levelDat = "tap_flag\level.dat"
$needGenerate = $true
if (Test-Path $levelDat) {
    $sz = (Get-Item $levelDat).Length
    if ($sz -gt 1000) {
        Write-Host "[2/4] level.dat OK ($sz bytes) - skipping generation"
        $needGenerate = $false
    } else {
        Write-Host "[2/4] level.dat too small ($sz bytes) - regenerating"
        Remove-Item "tap_flag" -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($needGenerate) {
    Write-Host "[2/4] Starting Purpur (watching logs/latest.log for Done, max 3 min)..."

    # Clear previous log
    if (Test-Path "logs\latest.log") {
        [System.IO.File]::WriteAllText(
            (Resolve-Path "logs\latest.log").Path,
            "",
            [System.Text.UTF8Encoding]::new($false)
        )
    }

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName               = $JAVA
    $psi.Arguments              = "-Xms512M -Xmx1G -jar purpur-1.21.4.jar --nogui"
    $psi.RedirectStandardInput  = $true
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError  = $false
    $psi.UseShellExecute        = $false
    $psi.WorkingDirectory       = $PSScriptRoot
    $psi.CreateNoWindow         = $true

    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
    } catch {
        Write-Host "ERROR: Failed to start process - $_"
        exit 1
    }

    Write-Host "  Server PID: $($proc.Id)"

    $found    = $false
    $deadline = [DateTime]::Now.AddSeconds(180)
    $logFile  = "logs\latest.log"
    $lastLine = 0

    while (-not $proc.HasExited -and [DateTime]::Now -lt $deadline) {
        Start-Sleep -Milliseconds 500

        if (Test-Path $logFile) {
            $lines = Get-Content $logFile -ErrorAction SilentlyContinue
            if ($lines -and $lines.Count -gt $lastLine) {
                for ($i = $lastLine; $i -lt $lines.Count; $i++) {
                    Write-Host "  $($lines[$i])"
                    if ($lines[$i] -match "Done \(") {
                        $found = $true
                    }
                }
                $lastLine = $lines.Count
            }
        }

        if ($found) {
            Write-Host "[2/4] Server ready - sending stop via stdin"
            $proc.StandardInput.WriteLine("stop")
            $proc.StandardInput.Flush()
            break
        }
    }

    if (-not $found) {
        Write-Host "[2/4] Timeout - killing server"
        $proc.Kill()
        exit 1
    }

    Write-Host "[2/4] Waiting for server to exit..."
    $proc.WaitForExit(60000) | Out-Null

    if (Test-Path $levelDat) {
        $sz = (Get-Item $levelDat).Length
        Write-Host "[2/4] level.dat created ($sz bytes)"
    } else {
        Write-Host "ERROR: level.dat not found. Check logs\latest.log."
        exit 1
    }
}

# Step 3: Copy WorldPainter region files to tap_flag\region\
$srcRegion = "..\Map\tap_flag\dimensions\minecraft\overworld\region"
$dstRegion = "tap_flag\region"
if (-not (Test-Path $dstRegion)) { New-Item -ItemType Directory -Force $dstRegion | Out-Null }
$mcaFiles = Get-ChildItem $srcRegion -Filter "*.mca"
Write-Host "[3/4] Copying $($mcaFiles.Count) region files to $dstRegion"
foreach ($f in $mcaFiles) { Copy-Item $f.FullName $dstRegion -Force }
Write-Host "[3/4] Done"

# Step 4: Copy entities folder if present
$srcEnt = "..\Map\tap_flag\dimensions\minecraft\overworld\entities"
if (Test-Path $srcEnt) {
    $dstEnt = "tap_flag\entities"
    if (-not (Test-Path $dstEnt)) { New-Item -ItemType Directory -Force $dstEnt | Out-Null }
    Copy-Item "$srcEnt\*" $dstEnt -Recurse -Force
    Write-Host "[4/4] entities copied"
} else {
    Write-Host "[4/4] No entities folder (skipped)"
}

Write-Host ""
Write-Host "=== Setup complete ==="
Write-Host "Run start-server.ps1 to start the server."
