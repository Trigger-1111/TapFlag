@echo off
REM Paper 서버를 JDWP 디버그 모드로 시작
REM VSCode에서 "Attach to Paper Server (JDWP 5005)" 로 연결

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 ^
     -Xms1G -Xmx2G ^
     -jar paper-1.21.4.jar --nogui
