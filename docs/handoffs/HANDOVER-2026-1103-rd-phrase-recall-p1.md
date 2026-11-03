# HANDOVER — 2026-11-03 로컬 창(문구 회상 P1·P1-b) → 다음 창(P2)

## 결과(SHA)

| PR | 의도 | 머지 SHA | 검증 |
|----|------|----------|------|
| #35 | effort 정본 적용(`.claude/settings.json` modelSettings medium · critic `effort: high`) | `a809d1c` | 설정 파일만, JSON 파싱 OK |
| #36 | 문구 회상 P1 — 검색 순수 함수 + 화면 + 내비(스키마 0) | `9377fec` | HEAD `c9718d5` testDebugUnitTest 170/170 · verifyRoborazziDebug 170/170 · critic 6→8 PASS · LC PASS-관찰 |
| #37 | P1-b — settle 테스트 2 + 발췌 위치 settle 폴백 | `bdbcbf3` | HEAD `6241e61` testDebugUnitTest 173/173 · verifyRoborazziDebug 173/173 · critic 8 PASS · LC PASS |

보고서: [HO-2026-1103-rd-phrase-recall-p1-01-REPORT.md](HO-2026-1103-rd-phrase-recall-p1-01-REPORT.md).

## 다음 = P2 사진 저장소(스키마 0)

정본 `docs/PHRASE-RECALL-SPEC-2026-11.md` §3.3 · LC 발주 `HO-2026-1103-rd-phrase-recall-p2-01`(LC 레포). 요지:
- 새 `data/photo/DiaryPhotoStore.kt`: `save(source: Uri, rotationDegrees: Float, flipped: Boolean): String?` · `fileFor(rel): File?`(경로 탈출·파일 부재 null) · `delete` · `sweep(referenced): Int`.
- `InternalDiaryPhotoStore(context)`: 루트 `noBackupFilesDir/diary_photos/`, 이름 `UUID.jpg`, JPEG 85. 디코드는 `BitmapDecoding.decodeDownsampled`(EXIF 포함, 긴 변 ≤ `MAX_SIDE` 2048) → 사용자 플립 후 회전(`decodeForOcr`와 같은 순서) → 크롭 없음(페이지 전체).
- 카메라 임시 파일 삭제(`OcrDiaryScreen.kt` cacheDir, ADR-002:30 후속 발견) 포함. 화면·DB 연결 0(P3·P4). 기록당 사진 상한 가정 0(기록당 장수 상한은 P4 PR에서 근거 1줄과 함께 정한다 — 명세 :83).
- `DiaryPhotoStoreTest`(Robolectric) — 명세 §3.3 목록 + 루트가 noBackupFilesDir 아래임을 고정. `GeminiKeyPolicyTest`·`RoomSchemaExportTest` 불변.
- 명세 §3.3 완료 조건 2건을 PR 본문에 명시: :167 JPEG 85 저장 1장 크기 실측(예상 0.3~1MB/장은 실측 아님) · :171 새 권한 0 · 새 의존성 0 · 클라우드 백업 경로 0.
- 게이트: critic PASS → PR → «[DONE] PR #n» → **LC 게이트 PASS 전 머지 0(위임 있어도)**.

## 후속 소형(P2 이후, next_actions에 등록)

critic 경미(#36·#37):
1. `settle`의 «버릴 단어가 되면 직전 값» 분기 테스트 0.
2. `stemExcerpt` 마지막 앞부분 대체는 TEXT 일치에서 사실상 도달 불가(방어 코드, 테스트 0).
3. 2글자 정규형이 다른 단어 안에서 먼저 잡힐 수 있음(예: 본문 «제주항공 … 제주에서» → 발췌가 «제주항공» 근처). 첫 위치 방식의 기존 한계, 강조 없음.
4. 과매칭 범위 부정 테스트 0(보호 명사 + 조사 · 4글자 일반 명사).

그 밖: 펼친 카드 검색어 강조 · 검색 계산 백그라운드화(기록 수천 건) · 회전 복원 테스트(StateRestorationTester) · 기억 서랍 «책 보기 → 뒤로» 상태 보존.

## 운영 메모

- 그래들: `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m`, JBR 21, 워크트리에 `local.properties`(sdk.dir) 필요. 1회 4~8분.
- Robolectric VM 테스트에서 tearDown에 DB를 닫으면 초기화 코루틴 예외가 다음 클래스로 샌다(`UncaughtExceptionsBeforeTest`) — DB 안 쓰는 VM 테스트는 리셋하지 않는다.
- 그래들을 돌린 워크트리는 `git worktree remove`가 장경로로 폴더를 못 지운다(등록만 해제). 에이전트의 폴더 삭제는 보호 규칙·권한에 막혔다 → 사용자 손.

## 사용자 손

- 잔재 폴더 `.claude/worktrees/phrase-recall-p1`·`.claude/worktrees/p1b-recall-settle` 삭제, 로컬 브랜치 `feat/phrase-recall-p1`·`chore/effort-settings`·`fix/phrase-recall-settle-excerpt`·`docs/handover-phrase-recall-p1` 삭제.
- 디버그 APK로 대시보드 «문구 회상» 아이콘·내비 서랍 버튼 진입 확인.
- 새 세션에서 `/effort` 상태 1회 확인(#35).
