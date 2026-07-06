# TapFlag 서버 가이드

## 서버 시작

**터미널 1 — 서버 실행**
```powershell
cd C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server
powershell -ExecutionPolicy Bypass -File start-server.ps1
```

**터미널 2 — 자동 배포 (코드 수정 → 자동 반영)**
```powershell
cd C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag
powershell -ExecutionPolicy Bypass -File watch-deploy.ps1
```

**서버 종료**
```powershell
cd C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server
powershell -ExecutionPolicy Bypass -File rcon_stop.ps1
```

**서버 초기화 (맵 리셋)**
```powershell
cd C:\Users\LG\OneDrive\Desktop\Projects\Tap_Flag\Server
powershell -ExecutionPolicy Bypass -File reset-server.ps1
```
> 처음 실행 시 현재 Map 폴더를 `map_backup`으로 자동 저장.  
> 새 맵 파일을 받으면 `map_backup` 폴더를 새 파일로 교체 후 재실행.

**접속**: Minecraft 1.21.4 → 멀티플레이 → `localhost`

---

## 게임 플로우

1. **시작** — `/tapflag start <팀수>` (최소 2)
   - 온라인 전원이 방랑자 상태로 스폰
   - 팀 수 = 깃발 수 (깃발 1 → 빨강팀, 깃발 2 → 파랑팀 …)
   - 깃발 랜덤 배치 (균등 간격), 월드보더 2048 활성화

2. **초반 (러시 페이즈)** — 점령 가능 시간 전까지 모든 플레이어 약화 + 채굴 피로 디버프
   - 방랑자가 깃발을 공격해 HP를 0으로 만들면 해당 팀에 자동 합류
   - 팀장은 `/tapflag team recruit <이름>` 으로 다른 방랑자 영입 가능

3. **점령 페이즈** (게임 마지막 1시간)
   - 디버프 해제, 팀 vs 팀 깃발 쟁탈 시작
   - 적 깃발 HP를 0으로 → 내 팀으로 점령, 소유 깃발 0 → 팀 해체

4. **종료** — 한 팀이 모든 깃발 독점 → 10초 후 자동 종료

---

## 혼자 테스트하는 방법

```
/tapflag playtest setup
```
- 빨강/파랑 팀 생성, 깃발 2개를 주변에 배치 (중립)
- 플레이어는 방랑자 상태로 시작 (실제 게임과 동일)
- 점령 강제 ON, 보더 400
- 깃발 공격 → HP 0 → 팀 합류

```
/tapflag playtest jointeam <팀id>   # 팀 강제 전환 (다른 기능 테스트용)
/tapflag playtest weapon            # 테스트 무기 지급
/tapflag playtest stop              # 테스트 종료
/tapflag playtest skipban           # 대기 차단 해제
```

---

## 전체 명령어

### 게임
| 명령어 | 설명 |
|--------|------|
| `/tapflag start <팀수>` | 게임 시작 (최소 2) |
| `/tapflag stop` | 게임 강제 종료 |
| `/tapflag status` | 현재 상태 요약 |
| `/tapflag reload` | config.yml 재로드 |

### 팀
| 명령어 | 설명 |
|--------|------|
| `/tapflag team list` | 팀 목록 |
| `/tapflag team info <id>` | 팀 상세 |
| `/tapflag team join <id>` | 팀 합류 |
| `/tapflag team disband <id>` | 팀 해체 |
| `/tapflag team recruit <이름>` | 팀장 전용 — 방랑자 영입 |

### 깃발
| 명령어 | 설명 |
|--------|------|
| `/tapflag flag place <id>` | 현위치에 깃발 배치 |
| `/tapflag flag list` | 깃발 목록 |
| `/tapflag flag info <id>` | 깃발 상세 |
| `/tapflag flag hit <id> <데미지>` | 수동 피격 테스트 |
| `/tapflag flag capture <id> <팀>` | 강제 점령 |
| `/tapflag flag reset <id>` | HP 초기화 |
| `/tapflag flag remove <id>` | 깃발 제거 |

### 타이머
| 명령어 | 설명 |
|--------|------|
| `/tapflag timer status` | 남은 시간 / 페이즈 |
| `/tapflag timer capture on/off` | 점령 시간 강제 전환 |
| `/tapflag timer start/stop` | 타이머 수동 제어 |

---

## 게임 규칙

| 항목 | 내용 |
|------|------|
| 게임 시간 | 3시간 |
| 점령 가능 시간 | 마지막 1시간 |
| 점령 불가 시간 | 약화 + 채굴 피로 디버프 |
| 깃발 HP | 300 |
| 깃발 데미지 | 실제 무기 데미지 × 공격 쿨타임 배율 (바닐라와 동일) |
| 월드보더 | 2048 (게임 시작 시 자동 생성) |
| 방랑자 점령 | 중립 깃발 HP 0 → 매핑된 팀 자동 합류 |
| 팀 해체 조건 | 소유 깃발 0개 |
| 승리 조건 | 전체 깃발 독점 → 10초 후 게임 종료 |
| 사망 패널티 | 20분 밴 |
| Y좌표 제한 | Y -60 이하 굴착 금지 / Y 200 이상 건축 금지 |
| 네더/엔드 | 이동 불가 |

---

## 파일 구조

```
Tap_Flag/
├── src/                       플러그인 소스
├── Server/
│   ├── tap_flag/              현재 월드
│   ├── map_backup/            원본 맵 백업 (reset 시 사용)
│   ├── plugins/TapFlag.jar
│   ├── start-server.ps1       서버 시작
│   ├── rcon_stop.ps1          서버 종료
│   ├── reset-server.ps1       맵/플러그인 상태 초기화
│   └── logs/latest.log        서버 로그
├── Map/tap_flag/              최초 WorldPainter 파일
├── deploy.ps1                 수동 빌드+배포
└── watch-deploy.ps1           자동 빌드+배포 감시
```
