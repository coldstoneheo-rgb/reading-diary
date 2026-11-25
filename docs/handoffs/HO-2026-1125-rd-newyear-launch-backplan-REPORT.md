# HO-2026-1125 reading-diary — 새해 공개 역산 · 비공개 테스트 준비 · 완성도 REPORT

발주: LC `handoffs/HO-2026-1125-rd-newyear-launch-backplan.md` + LC REPLY(2026-11-25 13:33 — 일정 차이 수용 · 준비물을 이번 발주로 · 첫 제출 목표 11-27). 시작 main `df88c87`, 2026-11-25 13:30.

## 체크리스트(발주서 완료 조건)

- [x] 1. Play 요건 사실표 → `docs/store-listing/CLOSED-TEST-2026-11.md` §1
- [x] 2. 역산 일정표 → 같은 문서 §2(LC 어림에 없던 «비공개 첫 검토» 반영)
- [x] 3. 1.0 범위표 · 사용자가 고를 것 3 → §3
- [x] 4. 등록 준비물 → §4 답안지 · 그림(아이콘 · 1024×500 · 스크린샷 4장(눈으로 확인)) · 처리방침 게시 전 원본(게시일 자리만 비어 있음 · 문의 메일 반영) · 호스팅 실측 §5
- [x] 5. 서명 빌드 → §6 + `FIRST-UPLOAD-2026-11.md`(사용자 단계 10개). 키스토어 없음 · 콘솔 앱 없음(사용자 답). `bundleRelease`는 권한 규칙에 막혀 **하지 않음**(우회 0)
- [x] 6. 테스터 모집 글 → §7(사용자 지시로 예고 글 없이 링크와 함께 1번)
- [x] 7. #67 확인 자료 → LC로 보냄(2026-11-25)
- [ ] 8. PR별 critic → LC 게이트 → 머지
- [x] 9. 검출기 보정 → PR #69(놓침 81 → 66)
- [x] 10. 처음 쓰는 사람의 한 바퀴 → `FirstRunSafetyTest.wholeLoop_…`(새 설치 → 등록 → 인식 → 고쳐 저장 → 낱말로 찾기) + 코드 점검 목록(아래)

## 완성도 1 — 검출기

PR #69 본문 · SPIKE §1-0-5. 앱 해상도 · 앱 어절 판정 190/5/81 → **205/3/66**(97.4 → 98.6% · 70.1 → 75.6% · 비침 0 · 본문 밖 선택 3 → 2 · 정답 밖 3 → 3 · 장별 하락 0). 옅은 물결(3 · 9번) 56 → 47, **9 되찾음**. 한 장 빼기 교차 검증에서는 «정답 밖 선택»이 5로 상한 3을 넘을 수 있고, 표본은 11장 · 한 권 · 연필뿐이다. 중간에 LC로 보낸 209/3/62 · 13은 뒤에 뺀 규칙이 든 값이다(정정).

## 완성도 2 — 한 바퀴

실기기 · 에뮬레이터 0(AVD 0 · 연결 기기 0). 카메라 · ML Kit · Play 서비스는 가짜로 바꿔 끼운 Robolectric 시험이라 **실제 촬영 · 모델 내려받기 · 키보드 가림 · 상태바 색은 확인하지 못했다.** 칩(#67)은 그 가지의 `UnderlinePreselectPanelTest`가 따로 덮는다(한 바퀴 시험에는 들어 있지 않다).

점검 = 읽기 전용 서브에이전트 1개(파일:줄은 main `df88c87` 기준) + 워커 직독 확인.

**출시 전 반드시**

«고침» = 가지에서 고침(머지는 아래 «PR» 표).

| # | 결함 | 근거 | 처리 |
|---|---|---|---|
| 1 | 책장 휴지통이 확인 없이 책 · 기록 · 사진까지 지움 | `MainActivity.kt:212-215` · `Entities.kt:18-23` | 고침(`fix/first-run-data-safety`) |
| 2 | 책 A의 구독이 남아 B 화면을 빼앗음 → 엉뚱한 책에 저장 · 삭제 | `ReadingViewModel.kt:533-543` · `:882` | 고침 |
| 3 | «이미지 저장» 버튼에 저장 코드가 없음 | `OcrDiaryScreen.kt:1525-1528` | 고침(`fix/store-readiness`) |
| 4 | 새 설치에서 기본 책장이 두 벌 생길 수 있음 | `AppDatabase.kt:60-69` · `ReadingViewModel.kt:168-175` | 고침 |
| 5 | 글자 인식 모델을 첫 인식 때야 내려받음 · 실패 이유가 토스트 한 번 | 매니페스트 · `OcrDiaryScreen.kt:336-339` | 고침 |
| 6 | 앱 아이콘이 기본 로봇 | `res/drawable/ic_launcher_*` | 고침 |
| 7 | 앱 안 처리방침 주소 빈 값(Play 필수) | `SettingsScreen.kt:40` | 주소가 생긴 뒤 1줄 PR |
| 8 | 뒤로 한 번에 쓰던 기록이 사라짐 | `MainActivity.kt:89-91` | 고침 |
| 9 | 책 등록 연타로 두 권 | `AddEditBookScreen.kt:468-498` | 고침 |
| 10 | 완독한 책의 제목 · 책장만 바꿔도 완독일이 오늘로 바뀜 | `ReadingViewModel.kt:646` | 고침 |
| 11 | `versionCode` 1 고정 · 카카오 키 없이도 경고 없이 빌드됨 | `app/build.gradle.kts:17` | 사용자 확인(올린 적 있는지 · 빌드 뒤 검색 1회) |

**테스트 중:** 띄어쓰기 · 줄 끝에서 끊긴 낱말 검색(`BookComparison.kt:20-23`) · 쪽수 필수(`AddEditBookScreen.kt:478`) · 키보드 가림 의심(imePadding 0) · 테마가 저장되지 않음(`ReadingViewModel.kt:220`) · 상태바 아이콘 색(`MainActivity.kt:56`) · 등록 폼 회전 시 입력 소실 · 재인식이 편집창 글을 덮음(`OcrDiaryScreen.kt:334`) · 책 상세에 기록 사진 없음 · 카메라 권한 영구 거부 막다른 길 · 검색 실패 문구가 개발자용 · 대시보드 · 통계 숫자 · 마지막 쪽 저장이 자동 완독 · 진도/별점 동시 갱신 경쟁(읽고-고쳐-쓰기) · 편집 중 DB 재방출이 입력을 덮음(`AddEditBookScreen.kt:132-144`) · 칩 바꾸기 되돌리기 · 영어 설명 2곳.
**1.0 뒤:** 글 없는 사진 기록 · 이미 두 벌인 기본 책장 정리 · 시작일 빈 책의 상태 문구 · minify.

## PR · 검증

| PR | head | critic | 시험(`verifyRoborazziDebug` 7묶음, 묶음마다 따로) |
|---|---|---|---|
| #69 검출기 | `813cdbd` | 7 → 8/10 PASS | 432 · 실패 0 · 건너뜀 1 |
| #70 첫 사용 기록 지키기 | `ea86898` | 6.5 NEEDS_REVISION → 7.5 → 8/10 PASS | 437 · 실패 0 · 건너뜀 1 · 변이 2건은 시험이 잡음(`2e6087f`) |
| #71 스토어 준비 | `62f5f2b` | 6.5 NEEDS_REVISION → 7.5 → 8/10 PASS | 437 · 1차에 **1건 실패** → 같은 head 재실행 3회 통과 · 건너뜀 1 |
| 문서(이 PR) | 이 PR head | 6 NEEDS_REVISION(심각 1) → 8 → 7.5 → 8.5/10 PASS | `BookSearchProviderPolicyTest` · `HonestCopyTest` · `PolicyCopyTest` 종료 0 |

- #71의 실패 = `OcrDiaryPhotoScreenTest.edit_recreation_keepsTextAndPhotoDrafts`(:228). 2026-11-05 REPORT에 간헐로 기록된 시험. critic 코드 판단: 사진 구역은 Room 조회 뒤에 그려지는데 시험은 메모 글만 기다린다 — 이 PR 때문일 가능성은 낮다. 재실행 3회만으로는 증거가 약하다(우연히 약 51%). 시험 고침은 다음 test PR.
- #69 · #70 · #71의 첫 묶음 목록에는 `src/testDebug`의 `debugtools` 2클래스가 빠져 있었다 — #70 댓글에 «전체»라고 잘못 적었다가 고치고, 세 가지 모두 8건씩 더 돌렸다.
- 하지 않은 것: 한 프로세스 전체 실행 · `assembleDebug`(서명 키 권한 규칙) · 실기기.
- #67과 #71은 `OcrDiaryScreen.kt` `clearShownPhoto` 1곳이 충돌한다(두 줄을 모두 두면 풀림). 처음 «충돌 0» 보고는 #71을 고치기 전 값이다.

순서: `fix/store-readiness`가 처리방침 게시 · AAB 빌드보다 먼저다(처리방침의 «글로 공유하기» · «설치할 때 모델 내려받기»는 그 PR이 들어가야 사실이다).

## effort(REF §4)

`.claude/agents/critic.md` frontmatter `effort: high` 있음(표의 critic = high와 같다). 레포의 서브에이전트 정의는 이 1개뿐이고 스킬에는 effort 항목이 없다 — 더할 것 0. 세션 medium, 올린 곳 없음.

## 막힌 것(권한)

- `my-upload-key.jks` · `.env` · `debug.keystore`를 묶은 조회 1회 거부, `debug.keystore` 워크트리 복사 1회 거부(`.claude/settings.json` deny `Read(./debug.keystore)` 등). 우회 0 — 그래서 **새 검출기 · 수리가 든 디버그 APK는 만들지 못했다.** `CLAUDE.md`는 복사를 지시하고 권한 규칙은 막는다(어긋남 — 사용자가 정할 것).
- 자동 검토가 판정을 내지 못한 명령 1건은 안내대로 같은 내용으로 1회 다시 보내 통과했다.

## PUBLIC 점검

네 가지 모두 추가 줄 ↔ 정답 글(본문 · 메모 절) 6글자 겹침 = 형식 낱말(`underline` · `stroke`)뿐 · 사진 이름 0 · 메일 = 공개하기로 한 문의 메일 1개만(처리방침 · 답안지). #69 가지는 이 점검보다 먼저 올렸다(점검 결과 0 — 다음부터 점검이 먼저).

## 사용자가 할 것

`docs/store-listing/CLOSED-TEST-2026-11.md` §8(날짜순 · 분) · `docs/store-listing/FIRST-UPLOAD-2026-11.md`(단계별).
