param(
    [string]$ServerDir = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server"
)

$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:PATH = "C:\tools\apache-maven-3.9.9\bin;$env:JAVA_HOME\bin;$env:PATH"

$artifact = "TapFlag-1.0-SNAPSHOT.jar"
$targetJar = "target\$artifact"

Write-Host ">>> Building TapFlag..." -ForegroundColor Cyan
mvn clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host ">>> Build OK: $targetJar" -ForegroundColor Green

$pluginsDir = Join-Path $ServerDir "plugins"
if (Test-Path $pluginsDir) {
    $dest = Join-Path $pluginsDir $artifact
    Copy-Item -Path $targetJar -Destination $dest -Force
    Write-Host ">>> Deployed: $dest" -ForegroundColor Green
} else {
    Write-Host "Server plugins dir not found: $pluginsDir" -ForegroundColor Yellow
}
