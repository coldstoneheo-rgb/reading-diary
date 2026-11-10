# TASK-2026-1110 rd ② 마크다운 내보내기 → P5 백업·복원 — REPORT (야간 무인)

발주: life-coordinator/handoffs/TASK-2026-1110-rd-stage2-saf-export-backup.md (LC d3, 11-10 22:1x)
작업 창: reading-diary-3f · 기준 main `2db66ac` → 마감 main `91986a6`

## 완료 조건 체크리스트

- [x] 0. 미커밋 2파일 정체 확인(`git diff`) — 머지 위임 개정분(`.claude/settings.json` allow 6줄: `git branch -d`·`git ls-remote`·`gh api -X DELETE …/git/refs/heads/*`의 Bash/PowerShell 쌍 + 끝 개행, `CLAUDE.md` 위임 조항 1줄 교체). 다른 내용 0.
      권한 설정을 피어 요청으로 커밋하지 않음 → LC 수용(설정 PR 철회), 아침 손 목록으로. AGENT_STATUS head 불일치(c6b83f3 vs main)는 이 기록 PR에서 `91986a6`으로 바로잡음.
- [x] 1. ② PR #47 — critic 1차 6.5 NEEDS_REVISION → 2차 8/10 PASS · LC PASS-관찰(차단 0·관찰 5) · `gh pr merge 47 --squash --match-head-commit 6fd7250…` → main `91986a6`. 원격 가지 `feat/markdown-export` 남김(main 조항: 원격 삭제는 사용자 손).
- [x] 2. P5 PR #48 — 테스트 7항 + 회귀 6항 · 설정 메뉴 2개(`settings_backup_create`·`settings_backup_restore`) · 확인 다이얼로그 · 진행 표시 · 문구 정합 · critic 사전 6/10 → 8/10 PASS(최종 head 12a98d1 재확인 PASS) · [ASK] 게이트 보냄 → **머지 0(오너 기기 확인 전, [HOLD-머지대기])**
- [x] 3. P5 head debug APK `D:\사진\reading-diary\reading-diary-debug-20261111-0320-12a98d1.apk` · 서명 SHA-256 `aba8e5e3…54c0bc` = 앞 APK(c6b83f3)와 동일(`apksigner verify --print-certs` 두 파일)
- [x] 4. 전체 단위 + Roborazzi verify 결과(아래) · 순서 의존 재현·우회 기록
- [x] 5. REPORT · AGENT_STATUS · WORKLOG → 이 기록 PR #49
- AGENT_STATUS waiting_on 정리: «원격 브랜치 fix/camera-state-pagecount 삭제» — 메인 체크아웃 `refs/remotes/origin`에 없음(삭제 시점·주체는 기록에 없음) · «e0094b0 덮어 설치로 서명 확인» — 같은 인증서(`aba8e5e3…`)의 12a98d1 APK 덮어 설치(아침 손 1)가 같은 확인을 대신하므로 그 항목에 합침

## 한 일

### ② 마크다운 내보내기 — #47 머지
- 기억 서랍 버튼 → `OpenDocumentTree` → 하위 폴더 «독서 다이어리» → 책당 1파일 + «독서 다이어리 목록.md». 같은 이름은 건너뛰고 «만든 파일 N · 건너뛴 파일 M(목록)». 덮어쓰기·병합·스텁 노트·사진 0.
- `DocumentTree`에 기존 문서를 여는 API 없음, `SafDocumentTree`는 `createDocument` + 새 URI의 `openOutputStream`만(소스 grep 테스트로 고정). HonestCopyTest 옵시디언 조건부 허용 + 조건 판정 테스트 + 금지어 «마크다운 백업».
- 계획과 다른 점 5(PR 본문): `[ ] # ^` 제거 · 120자 + **UTF-8 240바이트** 컷 · 대소문자 무시·인덱스 이름 회피 · `createAndWrite`가 실제 이름 반환 · `exportedDate` 인자.

### P5 zip 백업·복원 — #48 OPEN(HOLD)
- zip = `manifest.json` + `db/reading_diary.db`(같은 스키마 스냅숏 DB) + `photos/`. 키 저장소·설정 0(받지도 않음).
- 복원 = 캐시에 풀며 검증(경로 탈출·모르는 항목·manifest·형식/Room 버전·크기 1GB) → SQLite 머리말 → 읽기 전용·삭제 안 하는 처리기로 `quick_check` → 스냅숏을 같은 마이그레이션으로 열어 행·사진 수 대조 → NonCancellable 안에서 사진 들여오기 + 한 트랜잭션 교체 → 예시 표식 삭제 → 고아 사진 sweep. 실패하면 기존 기록 그대로·들여온 사진만 삭제.
- 만들기 실패 시 SAF 문서 삭제(discard) · `"wt"` 실패 시 `"w"` · I/O 오류는 «백업 파일 아님»과 구분 · 잔여 스냅숏·임시 폴더 시작 정리.
- 문구: 처리방침 2·4·6·7항 · 앱 약관 제3조 · 요약 2·4번 · 데이터 카드 · INTERNAL-TEST :55·:63-64·N8(줄 목록은 PR 본문 «문구 정합»).

## 검증(실행한 명령과 결과)

공통: `gradle --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m :app:<task> --tests …`, 묶음마다 직전 여유 RAM 측정(3.7~5.4GB).

**#47(② head 6fd7250, base 기준):**
- a17d0ad `verifyRoborazziDebug` 스크린샷 9클래스+3: BUILD SUCCESSFUL, 골든 변경 0
- 9805ee3 `testDebugUnitTest` A~M 30클래스: 197건(통과 196·건너뜀 1·실패 0)
- 6fd7250 N·O·Ph·Po: 84건 중 OcrDiary 2클래스 10건 실패(Compose waitUntil 10초) → 두 클래스 단독 11건 통과 = 순서 의존
- 6fd7250 RVM Export/Ocr/SampleBooks·RoomSchema·S*: 43건 실패 0 · PhotoSave 단독 15건 실패 0
- 합 339건(통과 338·건너뜀 1·실패 0)

**#48(P5 head 12a98d1):**
- `verifyRoborazziDebug` A~M + Ph·Po 39클래스: 278건(통과 277·건너뜀 1·실패 0), 골든 변경 0
- `verifyRoborazziDebug` N·RVM(Backup/Export/Ocr/SampleBooks)·RoomSchema·S*: 52건 실패 0 (수정 전 1회는 RVMBackupTest 2건이 «Running»에서 멈춤 → 아래 «발견 2»)
- `testDebugUnitTest` N·O*·PhotoSave 한 묶음: 36건 실패 0
- 합 359건(통과 358·건너뜀 1·실패 0)
- `assembleDebug`(메인 체크아웃 detach 12a98d1, 5m29s) BUILD SUCCESSFUL

**순서 의존(완료 조건 4):**
- N~S 한 묶음(②): `ReadingViewModelPhotoSaveTest.sweep_removesOnlyUnreferencedFiles`(:257)에서 01:15부터 CPU 증가 0 — `jstack` 확인 → `taskkill /T /F` → 단독 실행 15건 통과로 우회.
- N·O·Ph·Po(②): OcrDiary 2클래스 시간 초과 → 단독 재실행으로 우회.
- 메모리 보호가 백그라운드 그래들 1회 회수(01:3x, 명령 실패 아님) → LC «가» 뒤 포그라운드로만 실행.

## 발견

1. **복원에서 프레임워크 SQLite 오류 처리기가 위험하다.** 머리말만 SQLite인 깨진 DB를 기본 처리기가 지우고 빈 DB로 다시 열어, manifest가 0이면 «빈 기록으로 교체»가 가능했다(테스트가 BAD_MANIFEST로 실제 재현). 읽기 전용 + 던지는 처리기로 `quick_check`를 먼저 해 BAD_DATABASE로 막음.
2. **순서 의존의 유력한 원인.** 앱 시작 sweep이 프로세스 단위 `photoIoMutex`를 Main에서 잡고 IO에서 돌아와 Main 재개로 푼다 → 앞 테스트의 Main 루퍼가 버려지면 잠금이 영원히 안 풀린다. #48에서 시작 정리 전체를 IO에서 잠그도록 옮긴 뒤: 수정 전 «Running»에 멈추던 N·RVM·S* 묶음이 같은 구성 그대로 52건 통과(전후 비교 1회), N·O·PhotoSave 한 묶음 36건 통과. PhotoSave가 원래 멈춘 N~S 한 묶음 자체는 다시 돌리지 않았다 — 확정 아님. critic 경미: PhotoSave를 단독으로 돌리면 시작 sweep이 테스트의 sweep과 경쟁해 개수 단언이 흔들릴 수 있음 → 시작 sweep 테스트 훅 후속.
3. 계획의 «120자 컷»은 한국어에서 255바이트 한도를 넘는다(critic 1차) — 계획 문서 갱신 후보.

## 후속

- ② (LC #47 관찰, 고치지 않음): ① 제목 «. .x» → «.x.md» 숨김 파일 가능성(MarkdownExport.kt:40, 추론 — 테스트로 먼저 판정) ② C1·양방향 제어문자(U+202E)·CON/NUL 잔존(:32) ③ frontmatter C0 제어문자 미이스케이프·제목 줄바꿈 시 «# » 갈림 ④ 유니코드 정규화 없음(NFD 기존 파일이면 사본 1개 더·«독서 다이어리 (1)» 폴더), 사용자가 «독서 다이어리» 폴더 자체를 고르면 중첩 ⑤ SafDocumentTree 테스트 0 · Error 상태에 건너뛴 목록·남은 파일 수 미표시. critic 잔여: `deleteDocument` 대상 grep 고정 없음.
- P5: v2 백업 마이그레이션 왕복 미검증(v2 zip은 실재하지 않음) · 시작 sweep 테스트 훅 · init 주석 «순서»→«겹치지 않게» · 실제 SAF 프로바이더(드라이브) 호출 0 — 오너 기기 확인이 덮는다.
- 전체 스위트 한 프로세스 실행 1회(순서 의존 해소 확정용).

## 효과 수준(effort)
세션 medium 유지. critic 서브에이전트는 정의 frontmatter 그대로(변경 0).

## 아침 손 목록

1. **#48 오너 기기 확인(머지 조건):** `D:\사진\reading-diary\reading-diary-debug-20261111-0320-12a98d1.apk` 덮어 설치(서명 동일 — 거부되면 앱을 지우지 말고 멈춤) → 기억 서랍 «마크다운 파일로 내보내기»로 2차 사본 확보 → 설정 «백업 파일 만들기»로 드라이브에 저장 → **드라이브에서 zip이 있고 크기가 0이 아닌지 확인 — 만들기가 실패했거나 확인이 안 되면 앱 데이터를 지우지 않는다**(지우면 기기의 유일한 원본이 사라진다) → 기기 설정에서 앱 데이터 삭제 → 앱 열고 설정 «백업 파일에서 복원»으로 그 zip 선택·«복원하기» → 책·기록·사진 수가 전과 같은지. 통과하면 #48 머지 지시(LC 게이트 결과도 필요). 최우선 손 작업은 이것 하나다.
2. 기억 서랍 «마크다운 파일로 내보내기»를 같은 폴더에 두 번 → 두 번째는 전부 «건너뜀» · 드라이브 폴더를 골랐을 때 사본(« (1)»)이 생기는지.
3. 메인 체크아웃 미커밋 2파일(`.claude/settings.json` allow 6줄 · `CLAUDE.md` 위임 개정)을 직접 커밋할지 결정.
4. 원격 가지 삭제: `feat/markdown-export`(#47 머지됨). `feat/zip-backup-restore`는 #48 머지 뒤.
