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
순서는 회상 → 사진 저장소 → v3 → 화면 연결 → zip → 첫 업로드(② 마크다운 내보내기는 P4 뒤·zip 앞 — 11-03 LC 판정으로 확정), 부제는 «밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리». 이 결정을 ADR·계획·명세·스토어 문안에 모순 없이 옮겼다(코드 0).
