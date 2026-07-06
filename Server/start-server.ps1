$java = 'C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe'
$serverDir = Split-Path $MyInvocation.MyCommand.Path

Set-Location $serverDir

Write-Host '=== TapFlag Server ===' -ForegroundColor Cyan
Write-Host 'Ctrl+C 로 정지 | 접속: localhost:25565 | RCON: 25575' -ForegroundColor Yellow

& $java `
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 `
    -Xms1G -Xmx2G `
    -jar purpur-1.21.4.jar --nogui
