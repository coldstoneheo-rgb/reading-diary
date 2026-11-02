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
