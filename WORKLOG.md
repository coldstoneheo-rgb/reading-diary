# WORKLOG

에이전트 작업 블록 로그. 새 블록은 아래에 추가한다(위 블록은 고치지 않는다).

---
date: 2026-11-02
agent: cloud (CLOUD-2026-1102 rd-stage2-docs)
scope: docs only — ADR-005, docs/store-listing/INTERNAL-TEST-2026-11.md, docs/privacy/privacy-policy.html, docs/STAGE2-PLAN-2026-11.md, AGENT_STATUS.json, WORKLOG.md, ADR-003·004 역참조
signals:
  synergy: "power-of-habit의 applicationId 선례(코드 패키지 유지·applicationId만 변경)를 그대로 재사용. 데이터 안전 폼 표(경로 × 파일:줄)는 다른 온디바이스 앱에도 쓸 수 있는 틀"
  monetization: "0 — 유료·광고 코드 없음. 네이버 무료 한도 키 APK 포함은 비용이 아니라 한도 위험(ADR-005 §4)"
  learning: "코드 폴백을 지워도 이미 DB에 저장된 값은 남는다(Unsplash URL이 수동 등록 시 Room에 저장됨). 처분 '제거'는 저장된 데이터까지 세어야 완료"
  idea: "SAF 내보내기의 '건너뜀 목록'을 3단계 병합의 착수 신호로 쓴다 — 기능이 스스로 다음 기능의 수요를 측정"
---

**의미.** 9월 7일 이후 두 달 가까이 멈춰 있던 저장소에 다시 방향을 적었다. 이번 블록은 코드를 한 줄도 바꾸지 않았지만,
ADR-003·004에 쌓여 있던 오너 보류 11건이 처음으로 전부 «종결·기본값·미룸·사용자 값» 중 하나로 정리됐고,
2단계가 어디서 끝나는지(병합 없음, 스키마 동결)가 문서로 고정됐다. 다음 발주는 질문 없이 STAGE2-PLAN의 ⑤부터 시작할 수 있다.
출시 쪽에서는 데이터 안전 폼을 «실제 코드가 무엇을 내보내는가»에서 거꾸로 작성했고, 그 과정에서 앱 안 약관 문구가
네이버 검색 전송을 빠뜨린 채 «전송하지 않는다»고 단정하는 것을 찾았다 — 정직한 기능 원칙(ADR-003)이 출시 문서에도 그대로 적용돼야 한다.

---
date: 2026-11-02
agent: local worker (HO-2026-1102-rd-stage2-01, Opus 5.5)
scope: code — STAGE2-PLAN ⑤ applicationId(#25) · ④ Unsplash 폴백 제거(#26) · 부록 B 약관·처리방침 문구(#27) · ③ 예시 2권(#28)
signals:
  synergy: "④의 CoverPlaceholder가 ③ 예시 책의 표지가 됐다 — 계획의 순서(④→③)가 실제 의존을 정확히 짚었다"
  monetization: "0 — 유료·광고 코드 없음, 네트워크 호스트 1개(Unsplash) 감소"
  learning: "critic 4건 중 3건이 1차 NEEDS_REVISION. 공통점은 '코드는 바꿨는데 그 코드를 설명하는 문구·단서가 남는' 결함(명화 표지 약속, 약관의 자동 백업 단서 누락) — 기능 삭제 PR은 문구 grep을 DoD에 넣을 것"
  idea: "예시 책에 사용자가 기록을 쓰면 '내 책'으로 승격 — 일괄 삭제를 안전하게 만드는 동시에 온보딩이 자연스럽게 실사용으로 넘어간다"
---

**의미.** 56일 만의 첫 코드 회차. Play 첫 업로드 전에만 가능한 applicationId를 확정했고, 앱이 사용자 몰래 부르던 외부 호스트(Unsplash)를
저장된 값까지 포함해 화면에서 걷어냈으며, 앱 안 약관이 처리방침과 같은 사실을 말하게 했다. 신규 설치는 이제 빈 서재로 시작하고,
예시는 저작권이 끝난 김소월·윤동주 구절로만 원할 때 들어간다. 사용자 지시로 critic+LC 게이트 통과 PR(#25~#27)은 이 창이 머지했다. #28은 코드 완료 상태로, 메모리 부족으로 끊긴 최종 검증을 사용자 포그라운드 실행으로 넘겼다.

---
date: 2026-11-03
agent: local worker (HO-2026-1103-rd-stage2-02, Opus 5.5)
scope: code — STAGE2-PLAN ① 캔버스 보조 뷰: BipartiteLayout(#30) · 그림 보기·전환·탭 카드(#31)
signals:
  synergy: "③ 예시 2권의 #그리움 태그가 그대로 그래프 첫 화면 데이터가 된다 — 계획 순서(③→①)대로"
  monetization: "0 — 유료·광고 코드 없음, 새 네트워크 호스트 0"
  learning: "critic 정적 채점이 그래들 전에 컴파일 오류 2건을 정확히 예측. 그래들 1회가 비싼 환경에서는 critic 1차를 «가» 요청 전에 돌린다"
  idea: "그림에서 단어를 누르면 목록과 같은 원문 카드가 열린다 — 보조 뷰가 근거(원문)에서 떨어지지 않게 하는 규칙(ADR-003 Q2)을 화면 구조로 강제"
---

**의미.** 기억 서랍의 «연결 발견»에 «목록 ｜ 그림» 전환을 달았다. 그림은 책 ↔ 함께 나온 단어 이분 그래프이고, 좌표는 난수 없는 순수 함수라 스크린샷이 흔들리지 않는다. 상한(단어 12·책 10·선 40)에 걸리면 «일부만 표시»를 숨기지 않고 말한다. 목록이 기본값으로 남는다. 두 PR 모두 critic 8/10 PASS·verify 131/0이지만, 이 세션에서 squash 머지가 분류기에 막혀 사용자가 직접 머지했다 — #30 `e4a6456` → #31 `8ed683a`(main).

---
date: 2026-11-03
agent: cloud (CLOUD-2026-1103 rd-phrase-recall)
scope: docs only — docs/PHRASE-RECALL-SPEC-2026-11.md(신규), docs/adr/ADR-006-phrase-recall-photo-storage.md(신규), docs/handoffs/CLOUD-2026-1103-rd-phrase-recall-REPORT.md, AGENT_STATUS.json, WORKLOG.md
signals:
  synergy: "검색 엔진을 새로 만들지 않는다 — 기억 서랍의 BookComparison.matchKind(부분 일치)와 KeywordExtractor(조사 흡수)·KeywordLinks(단어 칩)를 그대로 엮는다"
  monetization: "0 — 유료·광고 코드 없음, 새 네트워크 호스트 0"
  learning: "사진을 '기기 안에 보관'하는 순간 Auto Backup이 그 사진을 기기 밖(드라이브)으로 보낸다. 보관 기능은 백업 규칙과 한 묶음으로 설계해야 ADR-002가 지켜진다"
  idea: "미루는 동안 찍은 사진은 영영 붙일 수 없다 — '되돌릴 수 없는 손실'을 선택지 리스크 표에 따로 세는 습관"
---

**의미.** 오너가 새로 꺼낸 «떠오른 문구로 그때의 책·날짜·사진을 다시 만나기»를 코드 0으로 명세했다. 검색·필터·카드(P1)는 스키마를 건드리지 않아 2단계 안에서 바로 할 수 있고,
사진 보관은 Room v3 마이그레이션이 정석이라 내부 테스트 스키마 동결과 부딪힌다 — 그 충돌을 ADR-006에 세 갈래(첫 업로드 전 v3 / 테스트 뒤 v3 / 파일 이름 규약 — 초안 1차의 «미룸»은 «테스트 뒤 v3»와 결과가 같아 합침)로 펼쳐 오너 선택으로 남겼다.
사실 확인 중 두 가지가 드러났다: 사진을 내부 저장소에 두면 자동 백업이 기기 밖으로 내보낸다는 것, 일기 편집이 `REPLACE` insert라 사진 경로가 조용히 지워질 수 있다는 것. 둘 다 계획의 테스트 항목이 됐다.

---
date: 2026-11-03
agent: cloud (CLOUD-2026-1103 rd-phrase-recall-decisions, 23건째)
scope: docs only — ADR-006 결정됨 · ADR-002/004/005 개정 · STAGE2-PLAN · PHRASE-RECALL-SPEC · INTERNAL-TEST · AGENT_STATUS · 이 블록 · REPORT
signals:
  synergy: "레퍼런스 앱의 사용자 실행 zip 백업을 P5로 들여와, 자동 백업 25MB 한도와 ADR-002 '자동 전송 0'을 둘 다 지킨다"
  monetization: "0 — 유료·광고 코드 없음, 앱 서버 0"
  learning: "'기억으로 25MB'를 공식 문서 curl 1줄로 닫았다 — [확인 필요]는 열 수 있으면 바로 연다"
  idea: "결정 반영 PR이 뒤 코드 PR(P3)의 문서 개정 의무를 미리 갚아 두면, 코드 PR은 사실이 바뀐 줄만 고치면 된다"
---

**의미.** 오너가 «문구 회상» 결정 4건을 내렸다: 사진은 첫 업로드 전에 `diary_photos` 테이블(일기 1 : 사진 N)로 정식 보관하고(ADR-006 A-선행), OCR에 쓴 사진은 체크 없이 항상 남기되 자동 백업에는 넣지 않으며 사용자가 직접 만드는 zip 백업·복원(P5)에만 담는다.
순서는 회상 → 사진 저장소 → v3 → 화면 연결 → zip → 첫 업로드(② 마크다운 내보내기는 P4 뒤·zip 앞 — 해석, 오너 확인 대상), 부제는 «밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리». 이 결정을 ADR·계획·명세·스토어 문안에 모순 없이 옮겼다(코드 0).

---
date: 2026-11-03
agent: local (HO-2026-1103-rd-phrase-recall-p1-01)
scope: 문구 회상 P1 — PhraseRecall.kt(검색 순수 함수) · PhraseRecallScreen.kt · 내비(Screen.PhraseRecall) · 대시보드·내비 서랍 진입 · 기억 서랍 링크 · 테스트 3파일 · 기준 이미지 2장 · docs 판정 반영
signals:
  synergy: "기억 서랍의 matchKind·KeywordExtractor를 그대로 재사용해 «책 사이 비교»와 «떠오른 문구 회상»이 같은 일치 규칙을 쓴다"
  monetization: "0 — 유료·광고 코드 없음, 새 네트워크 호스트 0, 사진 전송 0"
  learning: "DB를 tearDown에서 닫는 ViewModel 테스트는 초기화 코루틴 예외를 다음 클래스의 runTest로 흘린다(UncaughtExceptionsBeforeTest) — DB를 안 쓰는 VM 테스트는 리셋하지 않는다"
  idea: "화면 상태를 백스택 Screen 인자로 실어 두면 SaveableStateHolder 없이도 «책 보기 → 뒤로»에서 검색 맥락이 돌아온다"
---

**의미.** 머릿속에 떠오른 문구 하나로 내가 남긴 기록을 찾아 «어느 책 몇 쪽, 언제 기록했는지»를 보여 주는 화면이 생겼다(사진은 P4).
조사가 달라도(«그리움을»↔«그리움이») 찾고, 책·기간·쪽으로 좁힐 수 있으며, 결과는 기록한 날 최신순이다(점수 0). 스키마는 v2 그대로다.
11-03 LC 판정으로 STAGE2 ② 마크다운 내보내기 위치(P4 뒤·P5 앞)가 확정돼 명세·ADR-006·STAGE2·AGENT_STATUS의 «해석 — 오너 확인 대상» 표기를 걷었다(위 11-03 블록의 같은 표기는 append-only 규칙에 따라 그대로 두고 이 줄로 갈음).

---
date: 2026-11-03
agent: local (HO-2026-1103-rd-phrase-recall-p1 마감 · P1-b)
scope: PR #37 P1-b(settle 테스트 + 발췌 위치 settle 폴백) 머지 · 인계서 docs/handoffs/HANDOVER-2026-1103-rd-phrase-recall-p1.md · AGENT_STATUS
signals:
  synergy: "LC 사후 재게이트 관찰을 소형 PR 1개로 닫고 바로 다음 단계로 — 게이트 관찰이 다음 발주의 선행 조건이 된다"
  monetization: "0"
  learning: "정규화를 한쪽만 1회 더 하는 비대칭은 일치뿐 아니라 발췌 위치에도 숨어 있다 — 일치 키와 위치 키를 같은 함수로"
  idea: "인계서에 critic 경미를 번호로 모아 두면 다음 창의 후속 소형 PR 발주서가 그대로 된다"
---

**의미.** 문구 회상 P1이 게이트 관찰까지 닫혔다(#36·#37). «제주도를»처럼 조사가 붙은 검색어로 «제주에서»만 있는 기록을 찾으면, 이제 카드 발췌에도 그 단어가 보인다.
다음은 사진을 앱 안(백업 제외 폴더)에 보관하는 저장소(P2)이고, 새 창에서 시작한다.


---
date: 2026-11-03
agent: local (HO-2026-1103-rd-phrase-recall-p2-01)
scope: 문구 회상 P2 — data/photo/DiaryPhotoStore.kt(저장소 계층) · DiaryPhotoStoreTest · OcrDiaryScreen 촬영 임시 파일 삭제 · ADR-002:30 해소 표기 · CLAUDE.md 구조도 · 전 창 인계서 보정 2줄
signals:
  synergy: "OCR 디코드(BitmapDecoding.decodeDownsampled)를 그대로 재사용해 보관 사진과 OCR 입력이 같은 EXIF·축소 규칙을 쓴다"
  monetization: "0 — 유료·광고 코드 없음, 새 네트워크 호스트 0, 새 권한·의존성 0"
  learning: "Windows 호스트의 Robolectric에서 BitmapFactory.decodeFile 뒤 같은 파일 delete가 실패했다 — 테스트 디코드는 스트림을 직접 닫는다"
  idea: "onDispose 정리는 isChangingConfigurations로 회전 재생성을 거른다 — 재생성을 견디게 만든 ViewModel 작업과 화면 정리가 서로 밟지 않게"
---

**의미.** OCR에 쓴 페이지 사진을 앱 안 백업 제외 폴더(`noBackupFilesDir/diary_photos/`)에 남길 수 있는 저장소가 생겼다(아직 화면·DB와 연결하지 않음, P3·P4).
경로 탈출·고아 파일 정리(sweep)·디코드 실패를 테스트로 고정했고, 촬영할 때마다 캐시에 쌓이던 원본 사진은 이제 화면을 떠나면 지워진다(ADR-002 후속 발견 해소). 스키마는 v2 그대로다.

---
date: 2026-11-03
agent: local (HO-2026-1103-rd-phrase-recall-p3-01)
scope: 문구 회상 P3 🔴 — saveDiary REPLACE→@Update 선행 · Room v3 diary_photos + MIGRATION_2_3 + 파괴 폴백 제거 + 3.json · DiaryPhotoDao · PhotoEdit(Unchanged/Set) 저장 경로 · MigrationTest M1~M7 · DiaryPhotoDaoTest · RoomSchemaExportTest v3 · ADR-005/006·STAGE2 집행 표기
signals:
  synergy: "P2 DiaryPhotoStore.delete를 PhotoEdit.Set의 파일 삭제에 그대로 써서, 행(DB)과 파일(저장소)의 책임이 한 줄로 갈렸다"
  monetization: "0 — 유료·광고 코드 없음, 새 네트워크 호스트 0, 새 의존성 0(room-testing도 결국 불필요)"
  learning: "Robolectric 단위 테스트에서 test 소스셋 assets는 MigrationTestHelper가 보는 assets에 들어가지 않았다 — 명세가 미리 적어 둔 수동 대안(2.json 직접 읽기 3단계)이 실제로 필요했다"
  idea: "앱 빌더 설정을 AppDatabase.builder 하나로 모으면 마이그레이션 테스트가 «실제로 앱이 여는 방식»을 그대로 검증한다"
---

**의미.** 첫 업로드 전 단 한 번 허용된 스키마 변경을 집행했다. 일기마다 사진 N장을 담을 `diary_photos` 테이블이 생기고(v3), 기존 v2 기록은 바이트 그대로 옮겨진다(M1~M7).
이제 마이그레이션이 없거나 실패하면 기록이 조용히 지워지는 대신 앱이 열리지 않는다(파괴 폴백 제거). 편집 저장이 REPLACE에서 @Update로 바뀌어, 문장만 고쳐도 사진 행이 CASCADE로 사라지는 회귀(F6)가 막혔다. 화면 연결은 P4.

---
date: 2026-11-05
agent: local (HO-2026-1105-rd-p3-p4-01)
scope: P3 #40 머지(e5145db) · P4 사진 항상 보관 · 편집 N장 · 회상 카드·복기 · 삭제 연동 · 처리방침·데이터 안전 문구
signals:
  synergy: "StateRestorationTester + ViewModel 토큰 저장 키는 다른 Compose 앱의 프로세스 재시작 폼 복원 버그에도 그대로 쓸 수 있다"
  monetization: "0 — 유료·광고 코드 없음"
  learning: "rememberSaveable inputs는 복원 때 검증되지 않는다(문서 명시). 복원을 막으려면 key를 바꿔야 한다. Robolectric의 composeTestRule.waitUntil은 메인 루퍼를 돌리지 않는다"
  idea: "사진이 있는 기록을 예시 지우기에서 보호한 것처럼, 삭제 판정에는 새 자식 테이블을 함께 넣는 체크를 스키마 변경 체크리스트에 추가"
---

**의미.** 문구 회상이 처음으로 사진과 이어졌다. 저장한 사진을 편집에서 고치고, 회상 카드에서 넘겨 보고 확대할 수 있다.
critic이 유실 경로를 먼저 잡았다: 예시 기록에 붙인 사용자 사진이 «예시 지우기»로 사라지는 경로, 저장 중 뒤로 가기, 회전 시 글 소실, 프로세스 재시작 뒤 옛 입력 복원.
기존 디버그 키는 찾지 못했다. 이번 APK는 덮어 설치할 수 없으니 드라이브에 올리지 않았다.

---
date: 2026-11-05
agent: local (HO-2026-1105-rd-p3-p4-01 마감)
scope: docs only — AGENT_STATUS.json, WORKLOG.md, REPORT
signals:
  synergy: "메모리가 빠듯한 머신에서 여러 창이 그래들을 나눠 쓸 때 LC가 슬롯을 주는 방식이 강제 종료를 멈췄다"
  monetization: "0"
  learning: "worktree에는 gitignore된 서명 키가 없다 — 빌드 전 storeFile 존재를 확인한다. 검색 도구는 gitignore된 파일을 건너뛸 수 있어 부재 판단은 ls로 한다. 백그라운드 그래들은 세션이 쉬는 동안 메모리 가드에 종료될 수 있다"
  idea: "debug.keystore를 잃으면 기기 데이터가 묶인다. 키 백업 위치를 문서에 정하는 항목을 STAGE2 후속 후보로"
---

**마감.** #40(`e5145db`)·#41(`e0094b0`) 머지. 문구 회상은 사진까지 이어졌지만 실기기에서는 아직 한 번도 돌지 않았다.
이전 블록의 «기존 디버그 키는 찾지 못했다»는 틀렸다. 키는 메인 체크아웃 루트에 있었고, 그 키로 다시 빌드한 APK를 드라이브에 올렸다. 다음은 오너 기기 덮어 설치와 기록 수 확인이다.

---
date: 2026-11-05
agent: local (HO-2026-1105-rd-book-search-provider-01)
scope: 네이버 책 검색 종료 대응 — 카카오 책 검색 1차 + Google Books 폴백 · BookSearchClient 분리 · stateless BookSearchContent · 처리방침·ADR-005 §4·데이터 안전 문구
signals:
  synergy: "호출을 화면에서 빼니 가짜 인터셉터로 분기 전부를 네트워크 없이 고정할 수 있었다. 공급자가 또 바뀌어도 클라이언트 한 파일이다"
  monetization: "0 — 유료·광고 코드 없음. 카카오 공식 쿼터 문서상 책 검색 일 30,000건, 추가 쿼터 요금 항목에 Daum 검색 없음"
  learning: "Robolectric 런너에서는 Android의 org.json이 쓰여 optString(null)이 \"null\"을 돌려준다. 순수 JVM 테스트는 이 경로를 못 잡는다"
  idea: "외부 API 하나가 끊기면 등록 체인 전체가 멈춘다. 공급자 생존을 주기적으로 확인하는 점검을 후보로"
---

**의미.** 책 검색이 404로 막혀 있던 것을 코드에서 복구했다. 키 설정 실수나 카카오 장애가 있어도 Google로 재시도하고 원인을 보여 주므로, 등록이 한 공급자에 묶이지 않는다.
**아직 아닌 것.** 카카오 실호출은 0회다. 픽스처와 공식 문서로만 맞췄으니, 복구 판정은 오너 기기에서 «녹색절벽» 검색이 된 뒤에 한다.

---
date: 2026-11-05
agent: local (HO-2026-1105-rd-r4-camera-state-pagecount-01)
scope: 촬영 뒤 초기 화면 튕김 수리(화면·백스택 복원, 임시 파일 유지) · 검색 등록 쪽수 250 제거(고른 1권 ISBN만 Google 보조 조회) · 처리방침·앱 요약·INTERNAL-TEST 반영
signals:
  synergy: "실제 MainActivity를 띄운 재생성 테스트가 RESULT_OK와 RESULT_CANCELED의 증상 차이를 보여 줬다. 사용자 토스트 한 줄이 원인 가설을 좁혔다"
  monetization: "0 — 유료·광고 코드 없음. Google 보조 조회는 고른 1권만 불러 공용 한도 소진을 줄인다"
  learning: "그래픽 모드 없는 Roborazzi 캡처는 화면이 아니라 노드 덤프를 기록하고 verify도 통과한다. 기준 이미지는 기록 뒤 눈으로 본다. Windows에서 timeout -k는 그래들을 못 끊는다"
  idea: "전체 스위트가 10분을 넘겼다. 순서 의존을 고친 뒤 묶음 구성을 표준 명령으로 고정하는 안"
---

**의미.** 앱의 핵심 루프인 «촬영 → 자르기 → 추출»을 가로막던 튕김을 Robolectric에서 재현해 고쳤고, 지어낸 쪽수 250을 없앴다.
**아직 아닌 것.** 실기기·실제 카메라·실제 프로세스 사망에서는 확인하지 않았다. 카메라가 실제로 RESULT_OK가 아닌 값을 돌려주는지는 가설이다. 전체 검증은 묶음 분할 통과이고 한 프로세스 전체 통과가 아니며, B3 순서에서 PhotoSave가 멈추는 순서 의존이 남아 있다.

---
date: 2026-11-06
agent: local (HO-2026-1105-rd-r4-camera-state-pagecount-01 재게이트 마감)
scope: 복원 초안 중복 보정(7b102d6·d4d608a·b219ca6) · 재검증 · PR #44 처분
signals:
  synergy: "LC 코드 추적이 critic 전체 PASS 뒤에 남은 회귀를 찾았고, critic은 그 수리가 새로 만든 결함을 찾았다. 게이트 둘이 서로 다른 것을 잡았다"
  monetization: "0"
  learning: "상태를 복원하면 '이미 끝난 일'도 함께 복원된다. 복원 기능은 저장 경로 전체와 함께 검토해야 한다. 데이터 손실 수리는 저장 경로마다(새 기록·편집·사진) 따로 시험한다"
  idea: "복원·재시도 안전성을 저장 함수의 멱등 키로 해결하는 안(스키마 변경이 필요하므로 별도 검토)"
---

**의미.** 화면 복원이 만든 기록 중복을 Robolectric에서 재현해 막았다. 묶음별 verify 실패 0, 합 304건. LC 재게이트 PASS-관찰.
**머지하지 않았다.** LC가 이 PR이 연 편집 사진 소실 경로(미재현)를 관찰 ①로 남겼고, 마지막 코드 `b219ca6`은 critic 채점을 받지 않았다. ① 전에는 APK를 내지 않으니 머지해도 사용자에게 돌아가는 것이 없다. 위임 조건이 충족됐다는 LC 통보를 받았지만 위 이유로 처분을 미뤘고, 사용자가 원하면 바로 뒤집을 수 있다.
**아직 아닌 것.** 실기기 검증 0회. 사용자 `4e40a36` 재테스트 결과 대기. 그 APK에는 초안 중복 보정이 없다.

---
date: 2026-11-06
agent: local reading-diary-eb (TASK-2026-1106-rd-post-44-photo-restore)
scope: 편집 복원 뒤 재저장 사진 유실 경로 폐쇄 · 커밋 뒤 거짓 실패 제거 · 판정 쿼리 예외 처리 · 무테스트 변경 단언 · PR #44 재게이트 2차 준비
signals:
  synergy: "변이 실행이 내 새 테스트의 공허한 통과를 잡았고, critic은 수리로 판정 쿼리가 된 loadPhotos의 예외 공백을 잡았다. 옛 코드 red와 변이는 서로 다른 것을 검증한다"
  monetization: "0"
  learning: "복원 초안에는 '어떤 DB 상태에서 출발했는가'를 함께 저장해야 커밋 여부를 가릴 수 있다. 정적 ViewModel 팩토리 탓에 Robolectric에서 VM의 Application이 테스트의 것과 다를 수 있다(결과로 추정)"
  idea: "복원 대상 폼은 기준 상태를 함께 저장하는 규칙을 다음 저장 경로(P5 가져오기 등) 점검 항목으로"
---

**의미.** 편집 화면의 저장 중 이탈 뒤 복원·재저장에서 커밋된 사진이 지워지는 경로를 재현하고 막았다. 옛 코드 8건 중 7건 실패(Replace는 행 `[]`로 원본·교체본 모두 소실), 수리 뒤 9묶음 316건 실패 0.
**아직 아닌 것.** 실기기·실제 프로세스 사망·갤러리 권한 소멸 미확인. 한 프로세스 전체 통과 미확인. 머지·APK 전이다.
