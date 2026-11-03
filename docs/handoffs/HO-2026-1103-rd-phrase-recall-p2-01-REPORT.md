# HO-2026-1103-rd-phrase-recall-p2-01 — REPORT

- 발주: LC «문구 회상» P2 사진 저장소(스키마 0) · 정본 `docs/PHRASE-RECALL-SPEC-2026-11.md` §3.3(:146-172)
- 창: 로컬 메인 체크아웃, 브랜치 `feat/diary-photo-store`, base `ee543d7`(worktree 새로 만들지 않음 — 발주 전제)
- `/effort` 1회: «Set effort level to medium (saved as your default for new sessions)» — 세션 medium, 올림 0. critic은 정의 frontmatter `effort: high`(#35).
- 전제 확인: `git log origin/main -3` = `ee543d7`(#38) · `bdbcbf3`(#37) · `9377fec`(#36). 발주서의 `6241e61`은 #37 브랜치 HEAD(인계서 표)이고 squash 머지 SHA는 `bdbcbf3` — 둘 다 같은 #37이라 진행.

## 체크리스트(완료 조건 번호)

- [x] 1. `data/photo/DiaryPhotoStore.kt`(interface + `InternalDiaryPhotoStore` + `CameraTempFiles`) · `DiaryPhotoStoreTest` 12건(실행 11 + 실측 프로브 1, 평소 건너뜀) — 저장→존재→삭제→부재 · sweep 참조 밖만(루트 밖·하위 폴더 보존) · 4100px → 1025px(≤2048) · 회전 · 뒤집기 · 루트 = `noBackupFilesDir/diary_photos` · 경로 탈출(`../`·절대·`.`·하위 폴더) 거부 · 파일 부재 null · 디코드 실패 null(잔재 0) · 임시 파일 정리. `GeminiKeyPolicyTest` 5/5 불변
- [x] 2. 스키마 0 — `RoomSchemaExportTest` 4/4(v2), 엔티티·DAO 변경 0
- [x] 3. HEAD `534b142`: `gradle :app:testDebugUnitTest` exit 0, 29 클래스 185(통과 184 · 건너뜀 1 = 프로브) · `gradle :app:verifyRoborazziDebug` exit 0, 185(통과 184 · 건너뜀 1), 기준 이미지 git 변경 0
- [x] 4. AGENT_STATUS(as_of 19:53 실측 · next_actions 선두 P3 · metrics 173→185 · 29 클래스 · gradle_runs 14) · WORKLOG 블록 1
- [x] 5. critic 1차 8/10 **PASS**(경미 8) → 1·2·3·4·5·6(일부) 반영 → PR · «[DONE] PR #n» 뒤 대기
- [x] 6. PR 본문에 :167 · :171 명시(:167은 P4 완료 조건으로 이관 — LC 11-03 판정)
- [x] 7. AGENT_STATUS gradle_runs «#36: 8»(합계 10 → P2 4회 더해 14) · 인계서 :18 «기록당 장수 상한은 P4 PR에서 근거 1줄과 함께 정한다 — 명세 :83» · 인계서 :20 :167·:171 보완

## 한 일

- `InternalDiaryPhotoStore.save`: `BitmapDecoding.decodeDownsampled`(EXIF·긴 변 ≤2048) → 뒤집기 → 회전(`decodeForOcr`와 같은 순서) → 크롭 없음 → `UUID.jpg` JPEG 85. 실패(디코드·OOM·IO)는 null이고 쓰다 만 파일은 지움.
- `fileFor`/`delete`는 `resolve`를 거침(정규화한 부모 == 루트일 때만 허용). `sweep`은 루트 최상위 파일만 보고, 정규화 실패는 건너뜀. KDoc에 «save와 동시에 부르지 않는다» 계약을 적음.
- 촬영 임시 파일: `OcrDiaryScreen` onDispose에서 `CameraTempFiles.deleteAll(cacheDir)`, 단 `isChangingConfigurations`(회전 재생성)이면 건너뜀. ADR-002:30에 «해소» 표기, CLAUDE.md 구조도에 1줄 추가.
- 실행 명령(전부 `--no-daemon --max-workers=1 -Xmx1536m`, JBR 21): `testDebugUnitTest` 1차 184 중 1 실패 → 테스트 수정 → `--tests DiaryPhotoStoreTest` 통과 → critic 대응 → HEAD `534b142` `testDebugUnitTest` · `verifyRoborazziDebug` 둘 다 exit 0.

## 발견

- **Windows 호스트 테스트:** Robolectric(NATIVE)에서 `BitmapFactory.decodeFile`로 읽은 뒤 같은 파일의 `delete()`가 false를 냈다(`save_thenExists_thenDelete_thenAbsent` 1차 실패). 테스트 디코드를 `inputStream().use { decodeStream }`로 바꿔 해결. 제품 코드에는 해당 없음(저장소는 읽기 스트림을 남기지 않음).
- **critic 결함 1(반영):** onDispose 삭제가 회전에도 돌면, 재생성을 견디도록 만든 정밀 분석(`enterPreciseScope`)이 아직 읽는 임시 파일을 지울 수 있었음 → `isChangingConfigurations` 가드로 막음.
- **P4 선행 조건(next_actions 등록):** P4에서 사진 `save`를 ViewModel 스코프에서 돌리면 화면 onDispose(메인 스레드)가 먼저 임시 파일을 지워 사진이 조용히 빠질 수 있음 → P4는 «save 완료 뒤 삭제»로 순서를 옮겨야 함. 앱 시작 sweep은 save와 동시에 돌리지 않을 것.
- **후속 소형(next_actions 등록):** 뒤집기·회전 행렬 3중 복사 공통화 · compress 실패/IO 예외 정리 경로 테스트 · onDispose 연결 Compose 테스트.
- 인계서(전 창 문서) 2줄 수정은 발주 완료 조건 7이 이 PR에 포함하라고 지정한 것(critic 경미 8 — 1 PR = 1 의도 지적, 발주 지시로 유지).

## LC·사용자에게 필요한 것

- [ASK] 명세 :167 «기기 1장 실측»: P2에는 화면 연결이 없어 기기에서 `save`를 부르는 경로가 없고, adb에 기기가 잡히지 않음. 오너 폰으로 찍은 책 페이지 원본 JPEG 1장을 PC에 두고 경로를 알려 주면 `DIARY_PHOTO_PROBE=<경로>`로 `probe_realPhotoSize`를 돌려 «원본 B → 저장 B, W×H»를 PR에 추가함. 대안: 실측을 P4(첫 기기 저장 경로)로 넘김 — LC 판정.
- 사용자: PR 검토 · LC 게이트 PASS 뒤 머지(이 창은 게이트 전 머지 0).

## LC 게이트(11-03)

- PR #39(head `e199c19`) LC 게이트 PASS(차단 0 · 관찰 9). :167은 P4 완료 조건으로 이관하고 프로브는 지금 돌리지 않음. save/sweep 경쟁과 «save 뒤 임시 파일 삭제»는 P4 선행 조건.
- 소형 docs 반영: PR 본문 :167 줄 · AGENT_STATUS `head`/`last_commit` = `ee543d7`, waiting_on에서 잔재 정리 제거. CLAUDE.md 줄 위치 이동은 다른 세션 요청만으로 CLAUDE.md를 고치지 않는 규칙 때문에 사용자 확인 대기.
