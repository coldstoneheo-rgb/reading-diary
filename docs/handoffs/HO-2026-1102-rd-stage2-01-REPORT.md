# HO-2026-1102-rd-stage2-01 REPORT — reading-diary 2단계 코드 1회차

- 발주: `life-coordinator/handoffs/HO-2026-1102-rd-stage2-01.md` · 작업 창: 로컬 워커(Opus 5.5) · 2026-11-02 16:4x~
- 사용자 지시(작업 중 수신): «CRITIC+LC 게이트 PASS면 알아서 머지해» → critic PASS + LC 게이트 PASS인 PR은 이 창이 squash 머지했다.

## 체크리스트 (완료 조건 번호)

- [x] 1. ⑤ PR #25 — applicationId · ExampleInstrumentedTest · ApplicationIdTest · 부록 A 주석 · D13 로그 1줄 삭제 · `"com.example"` grep = namespace 1곳 · 88/17/0
- [x] 2. ④ PR #26 — 폴백 4곳 삭제 · `isStockCover` 상수 1곳 · 미리보기 필터 · OcrDiary 편집 모드 정리 + 회귀 테스트 · CoverPlaceholder + JVM 테스트 · 리터럴 0 grep 테스트 · 기존 6장 diff 0 + 1장 record · N3 Unsplash 삭제 · 96/19/0
- [x] 3. 부록 B PR #27 — 두 다이얼로그 교체 · URL 상수 1곳 · HonestCopyTest 금지 1건 · 대조표(PR 본문) · 91/17/0
- [ ] 4. ③ PR #28 — 코드 완료(구현·critic 1차 반영·origin/main 병합, HEAD `0547826`). 최종 verify가 메모리 부족으로 2회 중단(1회차: 셸 회수, 2회차: 셸 회수 뒤 계속 돌던 데몬을 내 `gradle --stop`이 끊음) → **사용자 포그라운드 실행 대기 · 머지 보류**
- [x] 5. #25~#27: critic(high) → LC 게이트 → `gh pr checks`(워크플로 없음: «no checks reported») 완료. #28은 재채점·게이트 대기(체크리스트 4)
- [x] 6. 기록 — 이 REPORT · AGENT_STATUS · WORKLOG 블록 (기록 PR)
- [ ] 7. 미완: `assembleDebug` 1회 — 워크트리로 `debug.keystore` 복사가 이 세션 권한 deny 규칙에 막힘(우회 0). 사용자 손

## PR 표

| 순서 | PR | 브랜치 | critic | LC 게이트 | 머지 |
|---|---|---|---|---|---|
| ⑤ | #25 | `fix/application-id` | PASS 8/10 | PASS | `157c303` |
| ④ | #26 | `fix/cover-placeholder` | 6 → PASS 8/10 | PASS | `f5fce2b` |
| 부록 B | #27 | `docs/settings-policy-copy` | 6 → PASS 8/10 | PASS | `36f66ab` |
| ③ | #28 | `feat/sample-books` | 6 → 재채점 대기(verify 뒤) | 대기 | **보류** — 코드 완료, verify 미완 |

`gh pr checks`: 네 PR 모두 «no checks reported»(저장소에 CI 워크플로 없음).

## 검증 수치 (모두 이 창에서 실행, 동시 그래들 1개)

| 브랜치 | 명령 | 결과 |
|---|---|---|
| origin/main `ae6751c`(베이스라인) | `gradle :app:testDebugUnitTest` | BUILD SUCCESSFUL 11m55s · 87 tests / 16 classes / 0 fail |
| ⑤ | `testDebugUnitTest` | 88 / 17 / 0 |
| ④ 1차 | `recordRoborazziDebug` | 95 / 19 / 0 · 기존 6장 git diff 0 · `cover_placeholder.png` 신규 |
| ④ 수정 후 | `verifyRoborazziDebug` | 96 / 19 / 0 · 7장 verify 통과 |
| 부록 B 1차·수정 후 | `verifyRoborazziDebug` | 91 / 17 / 0 (두 번) |
| ③ 1차 | `verifyRoborazziDebug` | 94 / 18 / 0 |
| ③ 수정+병합 뒤 | `verifyRoborazziDebug` | 112 / 23 / 2 fail(테스트 구독자 누락) → 테스트 수정(0547826) |
| ③ 재실행 ×2 | `verifyRoborazziDebug` | 메모리 부족 회수로 미완(결과 없음) |

첫 베이스라인 시도는 `ANDROID_HOME` 미설정으로 0건 실행 실패 → SDK 경로 지정 후 재실행(위 수치).

## critic 이력 (effort high — LC 규약 §6: critic·법적 서술)

| PR | 1차 | 고친 것 | 2차 |
|---|---|---|---|
| #25 | PASS 8/10 | — (중요 1 = assembleDebug 사용자 손) | — |
| #26 | NEEDS_REVISION 6/10 | 수동 등록 문구가 «명화 표지 자동 매칭»을 계속 약속 → 문구·조건 수정 + 금지 테스트, N3 근거 심볼화, verify 실행 | PASS 8/10 |
| #27 | NEEDS_REVISION 6/10 | 약관에 자동 백업 단서·Google 구성요소 진단 통계 단서 누락 → 추가 + 테스트 단언, 표지 요청을 검색과 분리 | PASS 8/10 |
| #28 | NEEDS_REVISION 6/10 | 예시 지우기가 사용자 일기까지 CASCADE·확인 없음 → 확인 다이얼로그 + 사용자 기록 있는 책은 «내 책»으로 남김 · 파괴적 마이그레이션 시 표식 잔존 → `onDestructiveMigration` 정리 · 넣기 연타 중복 → Mutex + withTransaction · Eagerly → WhileSubscribed | 대기 |

## 완료 조건 3 — 처리방침 html ↔ 앱 문구 문장 대조 (#27 최종본 e70c883)

| 앱 문구 | 처리방침 `docs/privacy/privacy-policy.html` 근거 |
|---|---|
| 요약 1. 책·책장·독서 일기·설정은 이 기기에 저장, 개발자 서버 없음 | 서두 «계정과 서버가 없는» · 1항 첫 문장 |
| 요약 2. 밑줄 인식은 기기 안, 사진 외부 전송 없음(5번 예외) | 2항 첫 항목(1·2문장) + 셋째 항목(예외) |
| 요약 3. 검색어 → 네이버(키 없는 빌드는 Google Books) · 표지 표시 때 이미지 서버 요청 | 3항 1·3문장(html은 «검색 결과의 표지»로 좁게 씀 — 후속) |
| 요약 4. 자동 백업 시 책·일기·일반 설정이 본인 Google 백업에 포함 가능(API 키 제외) | 4항 1·2문장 |
| 요약 5. 정밀 분석은 키 등록·동의·사진마다 확인을 모두 거친 경우만 Gemini 전송 | 5항 세 조건·전송 문장 |
| 약관 제2조 삭제 문장 + «(자동 백업이 켜져 있으면 … 사본이 남을 수 있습니다)» | 1항 3문장 + 4항 |
| 약관 제2조 «광고·분석·추적 SDK를 쓰지 않으며 … Google 구성요소(ML Kit, 코드 스캐너)는 진단·사용 통계를 처리할 수 있습니다» | 7항 표 아래 문단 전체 |
| 약관 제3조 검색어 전송 · 정밀 분석 조건 | 3항 · 5항 |
| 약관 제2조 «기록을 판매하지 않습니다» | html에 명시 문장 없음 — 서두 «서버를 운영하지 않습니다»에서 함의. html에 한 문장 추가는 후속 |
| 요약에 없는 것 | 6항(내보내기·복사), 8~10항 → U4 뒤 `PRIVACY_POLICY_URL` 줄로 전문 안내 |

LC 게이트 실측: «나만의 서재» `app/src/main` 0 · «일체» 0 · 문장 대조 모순 0 · URL 상수 1곳.

## 충돌 위험 표

| 쌍 | 파일 | 내용 | 처리 |
|---|---|---|---|
| #26 ↔ #28 | `AppDatabase.kt` | #26은 시드 coverUrl을 `""`로, #28은 시드 블록 삭제 | #26 머지 뒤 `git merge origin/main`(9b49329)에서 충돌 1건 → 삭제 쪽 채택 |
| #27 ↔ #28 | `SettingsScreen.kt` | 다른 위치(상단 상수·약관 vs 데이터 카드) | 자동 병합됨 |
| #26 ↔ #28 | `DashboardScreen.kt`·`BookDetailScreen.kt` | 표지 분기 vs 배지 | 자동 병합됨, 배지·플레이스홀더 공존 grep 확인 |

## 발견

- D-a: 편집 모드가 그동안 스톡 사진을 «찍은 사진»처럼 띄워 Unsplash를 호출했다(프리셋의 유일한 사용처). 제거 + 회귀 테스트.
- D-b: 옛 앱 이름이 설정 외에 드로어 헤더(`MainActivity.kt`)에도 있었다 — #27에서 같이 고침.
- D-c: 처리방침 html 3항이 표지 요청을 «검색 결과의 표지»로 좁혀 쓴다(시드·옛 폴백 표지 포함 못 함). 앱 문구는 고쳤고 html은 후속.
- D-d: `debug.keystore` 복사가 권한 deny — 이 창에서는 APK를 만들 수 없다.

## 다음 회차 (각 1줄)

- `metadata.json:3` 옛 이름 — ADR-001:63에서 의도적으로 유지, 기록만(LC)
- INTERNAL-TEST N3 «미리보기만 경유» 표현 정정(LC)
- `OcrDiaryScreen`의 `http` 가드(정밀 분석 대상·인식 버튼) — 프리셋 삭제로 도달 불가, 정리 후보(LC)

- `GeminiApiClient.extractUnderlinedText(bookTitle)` 매개변수 미사용 → 삭제 + 로그 개인정보 가드 테스트(LC 관찰·critic #25)
- STAGE2-PLAN 부록 A «⑤ PR에 넣지 말고 별도 PR» 문구가 낡음(실제로는 #25에 포함) → 문서 정정
- 처리방침 html: 3항 표지 문구 분리 + «기록을 판매하지 않는다» 명시 문장 · U4 뒤 `PRIVACY_POLICY_URL` 채우기 + 링크화
- `OcrDiaryEditSaveTest`·`ReadingViewModelSampleBooksTest`의 Room 싱글턴 → in-memory DB 주입(critic #26)
- Dashboard/BookDetail 안 플레이스홀더·예시 배지 스크린샷 기준 추가
- STAGE2-PLAN ① 캔버스 · ② SAF 내보내기

## LC·사용자에게 필요한 것

- 사용자 (a) #28 검증 — Claude Code 입력창에서 `!` 모드 1줄(Git Bash 터미널에 직접 붙일 때는 앞의 `! `를 뺀다 — bash에서 `!`는 결과를 뒤집어 gradle이 실행되지 않는다): `! cd <레포>/.claude/worktrees/samples && ANDROID_HOME='<Android SDK 경로>' ~/.gradle/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj/gradle-9.4.1/bin/gradle :app:verifyRoborazziDebug` → 0 fail이면 critic 재채점 → LC 게이트 → 머지

- 사용자: `debug.keystore`가 있는 체크아웃에서 `gradle :app:assembleDebug` 1회 + 패키지명 `com.woodpeckerai.readingdiary` 확인, 신규 설치 실기기 확인(책 0 → 예시 2권 → 기억 서랍 연결). 기존 `com.example` 앱은 지우지 말 것(기록이 이어지지 않음).
- 사용자 손 U1~U8(INTERNAL-TEST-2026-11) · 스토어 부제 [ASK].
