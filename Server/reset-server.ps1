param(
    [string]$MapSource = "C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Map\tap_flag"
)

$ServerDir  = $PSScriptRoot
$WorldDir   = "$ServerDir\tap_flag"
$BackupDir  = "$ServerDir\map_backup"
$JAVA       = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

Set-Location $ServerDir

# 1. 서버 실행 중이면 먼저 종료
$javaProc = Get-Process java -ErrorAction SilentlyContinue
if ($javaProc) {
    Write-Host "[1/4] Server running - sending stop via RCON..."
    & $JAVA RconStop 2>$null
    Start-Sleep -Seconds 5
    $javaProc = Get-Process java -ErrorAction SilentlyContinue
    if ($javaProc) {
        Write-Host "  Server still running - force kill"
        $javaProc | Stop-Process -Force
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "[1/4] Server not running"
}

# 2. MapSource 백업 (최초 1회 또는 -Force 인자 시)
if (-not (Test-Path $BackupDir)) {
    Write-Host "[2/4] Creating map backup..."
    Copy-Item $MapSource $BackupDir -Recurse -Force
    Write-Host "  Backup created: $BackupDir"
} else {
    Write-Host "[2/4] Backup already exists ($BackupDir) - skipping"
    Write-Host "  To force re-backup: Remove-Item '$BackupDir' -Recurse -Force"
}

# 3. 현재 tap_flag 월드 삭제
Write-Host "[3/4] Removing current tap_flag world..."
if (Test-Path $WorldDir) {
    Remove-Item $WorldDir -Recurse -Force
    Write-Host "  Removed: $WorldDir"
}
# nether/end도 삭제
foreach ($dim in @("tap_flag_nether", "tap_flag_the_end")) {
    if (Test-Path "$ServerDir\$dim") {
        Remove-Item "$ServerDir\$dim" -Recurse -Force
        Write-Host "  Removed: $dim"
    }
}

# 4. 서버 기동 → level.dat 생성 → 종료 → 백업 리전 복사
Write-Host "[4/4] Starting server to generate level.dat..."
$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName       = $JAVA
$psi.Arguments      = "-Xms512M -Xmx1G -jar purpur-1.21.4.jar --nogui"
$psi.UseShellExecute = $false
$psi.CreateNoWindow  = $true
$psi.WorkingDirectory = $ServerDir

$proc = [System.Diagnostics.Process]::Start($psi)
Write-Host "  Server PID: $($proc.Id)"

$logFile  = "$ServerDir\logs\latest.log"
$deadline = [DateTime]::Now.AddSeconds(120)
$found    = $false
$lastLine = 0

while ([DateTime]::Now -lt $deadline -and -not $proc.HasExited) {
    Start-Sleep -Milliseconds 600
    if (Test-Path $logFile) {
        $lines = Get-Content $logFile -ErrorAction SilentlyContinue
        if ($lines -and $lines.Count -gt $lastLine) {
            $lastLine = $lines.Count
            if ($lines | Select-String "Done \(") { $found = $true; break }
        }
    }
}

if ($found) {
    Write-Host "  Server ready - stopping via RCON..."
    & $JAVA RconCommand "stop" 2>$null
    $proc.WaitForExit(30000) | Out-Null
} else {
    Write-Host "  Timeout - force killing server"
    $proc.Kill()
}

# 5. 백업 리전 파일 복사
$srcRegion = "$BackupDir\dimensions\minecraft\overworld\region"
$dstRegion = "$WorldDir\region"
if (Test-Path $srcRegion) {
    New-Item -ItemType Directory -Force $dstRegion | Out-Null
    $mca = Get-ChildItem $srcRegion -Filter "*.mca"
    Write-Host "  Copying $($mca.Count) region files..."
    foreach ($f in $mca) { Copy-Item $f.FullName $dstRegion -Force }
}

$srcEnt = "$BackupDir\dimensions\minecraft\overworld\entities"
if (Test-Path $srcEnt) {
    $dstEnt = "$WorldDir\entities"
    New-Item -ItemType Directory -Force $dstEnt | Out-Null
    Copy-Item "$srcEnt\*" $dstEnt -Recurse -Force
}

# 플러그인 데이터 초기화 (깃발, 팀 상태 삭제)
$pluginData = "$ServerDir\plugins\TapFlag"
foreach ($f in @("flags.yml", "teams.yml")) {
    if (Test-Path "$pluginData\$f") {
        Remove-Item "$pluginData\$f" -Force
        Write-Host "  Cleared $f"
    }
}

Write-Host ""
Write-Host "=== Reset complete ==="
Write-Host "Map restored from backup. Run start-server.ps1 to start."
