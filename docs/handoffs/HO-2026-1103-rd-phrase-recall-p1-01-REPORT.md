# HO-2026-1103-rd-phrase-recall-p1-01 — REPORT

- 발주: LC «문구 회상» P1(사진 없는 회상: 검색 순수 함수 + 화면 + 내비, 스키마 0) · 정본 `docs/PHRASE-RECALL-SPEC-2026-11.md` §3.2
- 창: 로컬(worktree `.claude/worktrees/phrase-recall-p1`, 브랜치 `feat/phrase-recall-p1`, base `a809d1c`)
- 같은 창 선행 작업: effort 정본 적용 PR #35(설정 파일만) — 사용자 위임 «effort 설정 PR 진행, 게이트 PASS 뒤 머지까지»로 머지 `a809d1c`
- effort: 세션 medium(기본, 올림 0). critic 정의 `effort: high`(#35).

## 체크리스트(완료 조건 번호)

- [x] 1. 새 파일 2(`data/knowledge/PhraseRecall.kt` · `ui/screens/PhraseRecallScreen.kt`) · 수정 파일 4(`MainActivity.kt` · `ReadingViewModel.kt` · `DashboardScreen.kt` · `KnowledgeDrawerScreen.kt`) · testTag 5 · 스키마 변경 0(`RoomSchemaExportTest` 4/4)
- [x] 2. `PhraseRecallTest.kt` 32건 — §3.2 항목 전부 + grapheme 경계 1(`excerpt_graphemeBoundary_neverSplitsSurrogatePairs`) + 기간 경계 2(`period_startDayIncludesMidnight` · `period_endDayIncludesLastMillisecond`) + #태그 1
- [x] 3. Roborazzi 2장(`phrase_recall_results.png` 117,647B · `phrase_recall_empty.png` 49,830B) record·커밋 · `ScreenshotBaselineIntegrityTest` 1/1 · `screenshot_baselines` 8 → 10(기존 8장 git 변경 0)
- [x] 4. `verifyRoborazziDebug`(= 전체 단위 테스트 포함) 28 클래스 168 테스트 실패 0 · `GeminiKeyPolicyTest` 5/5 · 새 네트워크 호스트 0
- [x] 5. 대시보드 상단 아이콘(`dashboard_phraserecall_button`, 설명 «문구 회상») + 내비 서랍 버튼(`drawer_phraserecall_button`) + 기억 서랍 검색 결과 링크(`knowledgedrawer_phrase_recall_link`). 대시보드 기준 이미지 없음 → 오너 기기 수동 확인 1줄
- [x] 6. docs: 표기 제거(명세 :4·:265 · ADR-006:93 · STAGE2:31 · AGENT_STATUS next_actions) · 명세 상태 줄 · AGENT_STATUS(as_of·next_actions 선두 P2·metrics 누적) · WORKLOG 새 블록 1
- [x] 7. critic 1차 6/10 NEEDS_REVISION(심각 1: 기준 이미지·게이트 미실행 · 중요 3: 회전 소실·책 보기 복귀 소실·AGENT_STATUS 표기) → 대응 → 2차 8/10 **PASS**(경미 6: REPORT 채움 · WORKLOG 처리 발견 기록 · 태그 분기 중복 → 분기 삭제 · 기간 한계 PR 명시 · 회전 테스트 후속 · critic 칸) → PR

## 한 일

- 검색 순수 함수 `PhraseRecall.search/excerpt` + `RecallPeriods.dayStart/dayEnd/presetFrom`(minSdk 24라 `Calendar`). 일치 규칙은 기억 서랍의 `matchKind` · `KeywordExtractor.stem/extract/tags` 재사용, 정렬 `createdAt`↓ → `diaryId`↓.
- 화면 `PhraseRecallContent`(stateless): 검색창 · 공유 단어 칩 · 필터 칩 [책(다중)] [기간(전체/1개월/1년/직접 지정 — `DatePickerDialog` 2회)] [쪽(책 1권일 때만)] · 결과 카드(발췌 강조 · 책·저자 · 기록한 날·쪽 · 일치 라벨 · 메모) · 카드 탭 = 펼침(구절·메모 전문 + «이 책 보기»).
- 내비: `Screen.PhraseRecall(initialQuery, initialFilter)`(Screen sealed는 `ReadingViewModel.kt`에 있음 — 명세의 «MainActivity.kt»는 `when` 분기 쪽), `openPhraseRecall` · `openBookFromPhraseRecall`(떠나기 전 현재 화면에 검색어·필터를 실어 뒤로 오면 복원).
- 실행 명령(전부 `--no-daemon --max-workers=1 -Xmx1536m`): `testDebugUnitTest --tests PhraseRecallTest` 31/31 → `testDebugUnitTest` 165/165 → critic 1차 대응 → `recordRoborazziDebug --tests PhraseRecallScreenshotTest` → `verifyRoborazziDebug` 1차 168 중 1 실패 → 수정 → 2차 168/168.
- 잔재 worktree `remove-stray-files`·`effort-settings` 제거(둘 다 reparse point 0 확인 뒤, `--force` 없이. 전자는 장경로로 폴더가 남아 `\\?\` 경로로 삭제, 메인 `debug.keystore` 2666B 그대로).

## 발견

- **테스트 누수:** DB를 tearDown에서 닫는 VM 테스트는 VM 초기화 코루틴(IO)이 닫힌 DB를 다시 열며 낸 예외를 다음 클래스의 `runTest`로 흘린다(`ReadingViewModelOcrTest` `UncaughtExceptionsBeforeTest`). DB를 쓰지 않는 내비 테스트는 리셋을 하지 않게 고쳤다.
- **WORKLOG 규칙 충돌:** 발주는 WORKLOG의 «해석 — 오너 확인 대상» 표기 제거를 요구했지만 `WORKLOG.md:3`은 «위 블록은 고치지 않는다». 옛 블록은 원문 그대로 두고 새 블록에 «확정»을 적어 갈음했다. LC가 다르게 원하면 1줄 수정.
- **명세 :109**에는 해당 표기가 없었다(실측 grep) — 실제 제거는 명세 2곳 + ADR-006 + STAGE2 + AGENT_STATUS = 5곳, WORKLOG는 위처럼 갈음.
- **알려진 한계:** 미리 정한 기간(최근 1개월·1년)을 고른 채 «이 책 보기» → 뒤로 오면 칩이 그 시점의 날짜 범위(«직접 지정»)로 보인다. 결과 집합은 같다.
- `stemMatch`의 `tags` 분기는 도달 불가(불용어·숫자 태그는 검색어 정규형부터 null, 나머지는 extract(notes)가 같은 토큰을 냄) → critic 2차 지적으로 삭제.
- 기억 서랍 링크는 nullable 콜백이라 기존 `knowledge_drawer_cross_book_search.png` 기준은 그대로다(스크린샷 테스트는 콜백 미전달).
- 후속(AGENT_STATUS next_actions 등록): 펼친 카드 검색어 강조 · 검색 계산 백그라운드화(기록 수천 건 대비, 지금은 키 입력마다 메인 스레드) · 기억 서랍도 «책 보기 → 뒤로» 상태 보존.

## LC·사용자에게 필요한 것

- 사용자: PR 검토 · 머지 위임 1줄(이 창은 위임 없음 — «[DONE] PR #n» 대기).
- 사용자: 머지 뒤 디버그 APK로 대시보드 상단 «문구 회상» 아이콘 · 내비 서랍 버튼 진입 1회 확인.
- 사용자: 새 세션에서 `/effort` 상태 1회 확인(#35).
