# CLOUD-2026-1103 reading-diary «문구 회상» 오너 결정 4건 문서 반영 — REPORT

- 발주: 클라우드 23건째(정본 `life-coordinator/handoffs/CLOUD-2026-1103-rd-phrase-recall-decisions-23.md` — 이 세션은 읽지 못함, 프롬프트 인라인이 전부)
- 결정 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`(사용자 원문 11-03: «AAB 업로드 한적 없어. 추천 답으로 진행하자.»)
- 브랜치: `cloud/rd-phrase-recall-decisions` (분기점 origin/main `4edd80f`, PR #33 머지 커밋)
- 시작: 2026-11-03T06:54:53Z (`date` 실측) · 코드 변경 0 · 그래들 0 · effort: 세션 기본값(올리지 않음)
- 전제 확인(06:54:53Z): `git ls-tree origin/main` → `docs/PHRASE-RECALL-SPEC-2026-11.md`·`docs/adr/ADR-006-phrase-recall-photo-storage.md` 존재 → #33 머지됨, 진행.
- 중복 확인(같은 시각): `git log origin/main -5` 최신 `4edd80f`(#33) · `AGENT_STATUS.json` next_actions 맨 앞이 [ASK] 4건 그대로 → 결정 미반영 확인.

## 체크리스트 (완료 조건 번호)

- [x] 1. ADR-006 결정됨 · A-선행 + diary_photos · 열 1개 안 기각 · Q-a·Q-b · 재검토 조건
- [x] 2. ADR-005 §2·§3·§6 · ADR-004 ⑧ · STAGE2-PLAN DoD·P5 행 · ADR-002 1줄
- [x] 3. 명세 §2.4·§2.5·§3.1·§3.4·§4·§5·§6 · 25MB [확인 필요] 닫기
- [x] 4. 레퍼런스 앱 절
- [x] 5. INTERNAL-TEST :26 부제 · §5 사진 보관 행
- [x] 6. WORKLOG:62 · AGENT_STATUS next_actions·metrics · ADR-004:53 인용 정정
- [x] 7. 코드·그래들 diff 0
- [x] 8. critic PASS — 1차 6/10 NEEDS_REVISION(중요 2·경미 5) → 전부 반영 → 2차 **8/10 PASS**(새 경미 3) → 3건도 반영
- [x] 9. PR([cloud]) · 본문 = 이 REPORT

## 한 일

| 파일 | 내용 |
|------|------|
| `docs/adr/ADR-006-phrase-recall-photo-storage.md` | 상태 «결정됨(2026-11-03, 오너)» · 결정 = A-선행 + `diary_photos` 테이블(열 1개 안 «기각 — 1기록 N사진·2차 마이그레이션 회피») · 선행 `@Update` · Q-a 항상 보관 · Q-b 자동 백업 제외 + 사용자 zip(P5) 포함 · 공통 조건 1 25MB 근거로 닫음 · 공통 조건 2 «자동» 전송 0 · 재검토 조건 갱신 · 실행 순서 |
| `docs/PHRASE-RECALL-SPEC-2026-11.md` | 머리 · F3·F4·F6 · §2.4(테이블·N장·항상 보관·dangling 유지) · §2.5(마크다운엔 사진 0, P5 zip엔 포함) · §3.1 단계표(P3 테이블, P5 추가, 순서) · §3.3 P2(UUID 결정, Q-b 분기 삭제) · §3.4 재작성(`DiaryPhoto` 엔티티·MIGRATION SQL·`PhotoEdit` Unchanged/Set·M1~M7 N장 기준) · §3.5 P4(체크박스 삭제) · **§3.6 P5 신설**(범위·zip 구성·규칙·파일·테스트·완료 조건) · §4 (다) · §5 후보 3 · §6 결정됨 → DEC 포인터 · **§7 레퍼런스 앱** |
| `docs/adr/ADR-005-…md` | 행 번호 불변 개정: :9 개정 표시 · :27 가져오기 · :39 동결 «첫 AAB 업로드부터» + ADR-006 예외 · :54 «태그 + diary_photos» · :55 자동 전송 · :94 ADR-003-5 «백업» 명칭 · :96 부제 결정 · :100 ADR-004-4 · :103 · :109 |
| `docs/adr/ADR-004-…md:53` | ⑧ 서술 «태그 + diary_photos» |
| `docs/adr/ADR-002-…md:12` | «자동으로는 나가지 않음 — 사용자가 직접 내보낼 때만» |
| `docs/STAGE2-PLAN-2026-11.md` | :10 DoD «ADR-006 예외(diary_photos, v3) 외 0» · :11 자동 전송 · :24 ② «P5 직전» · :25 P5 행 · 문구 회상과의 순서 단락 |
| `docs/store-listing/INTERNAL-TEST-2026-11.md` | :24·:26 부제 후보 3 · §5-1 N8 행 · U7 |
| `WORKLOG.md` | :62 «네 갈래» → 세 갈래 · 블록 1개 추가 |
| `AGENT_STATUS.json` | as_of · waiting_on · next_actions P1~P5(«로컬 창», «P1 무관 착수» 문구 삭제) · metrics |

검증 명령과 결과:
- `git ls-tree origin/main docs/PHRASE-RECALL-SPEC-2026-11.md docs/adr/ADR-006-…` → 두 파일 존재(#33 머지)
- `curl -sL "https://developer.android.com/identity/data/autobackup?hl=en"` → «limited to 25 MB per app» · «If the amount of data is over 25 MB, the system calls onQuotaExceeded() and doesn't back up data to the cloud»
- `python3 len("밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리")` → 40
- `python3 json.load(2.json)` diaries `createSql` 형식 → 명세 §3.4 SQL 형식과 일치(critic 확인)
- `git diff --stat origin/main -- app gradle '*.kts' '*.toml' '*.gradle' | wc -l` → `0`
- `git diff --name-only origin/main | grep -v '^docs/\|\.md$\|^AGENT_STATUS.json$' | wc -l` → `0`
- `grep -rn "\[ASK\]" docs/adr docs/PHRASE-RECALL-SPEC-2026-11.md docs/STAGE2-PLAN-2026-11.md docs/store-listing AGENT_STATUS.json` → 0건
- ADR-005 `git diff -U0` 헝크 전부 `-N +N`(행 번호 불변 — ADR-006·명세의 :39/:54/:100 인용 유지)
- 그래들: 실행 안 함(상시 규약 6)

## 발견

- **D1 ② 위치(해석).** 결정 3 목록(P1→…→P5→업로드)에 STAGE2 ② 마크다운 내보내기가 없다. (다)의 정의 «회상 전체 → SAF ②»대로 P4 뒤·P5 앞에 두고 모든 순서 서술에 «해석 — 오너 확인 대상»으로 표시.
- **D2 «Migration(2,3) = 태그 + diary_photos».** 결정 1이 `Migration(2,3)` = CREATE TABLE + 인덱스만이라, ADR-004/005 서술은 «마이그레이션 서술 = 태그 + diary_photos — diary_photos는 Migration(2,3)(첫 업로드 전), 태그 컬럼은 3단계 ⑧ Migration(3,4)»로 썼다(결정과 모순 없게 나눔).
- **D3 ADR-004:53 인용 오기.** ADR-006·명세 초안이 «Migration(2,3)을 3단계 태그 컬럼으로 적은 ADR-004 ⑧(:53)»이라 했으나 ADR-004:53은 «필요 시 Migration(2,3)»뿐, «태그 컬럼»은 ADR-005:54. 인용 정정.
- **D4 «백업» 명칭.** ADR-005 §6 ADR-003-5는 «복원 없는 사본이라 백업이라 부르지 않는다». P5는 복원이 있으므로 P5만 «백업», 마크다운은 계속 «내보내기»로 개정. `INTERNAL-TEST:63`·`:41`·`:149` 문구 갱신은 P4/P5 PR 몫으로 명세에 적음(지금 바꾸면 미구현 기능 약속).
- **D5 처리방침 `privacy-policy.html:41`** 은 P4 PR에서 같이 고친다(명세 §3.5에 새 문안). 이 PR에서는 건드리지 않음.
- **D6 `com.example` 설치본** 기록은 P5 이전 빌드라 zip으로 옮길 수 없다(ADR-005 §1 :27에 명시).
- **D7 metrics `critic_first_pass_rate`** 이번 세션 0/1(1차 NEEDS_REVISION).
- 반대 의견: 없음. 결정 재해석 0.

## LC·사용자에게 필요한 것

- 확인: STAGE2 ② 마크다운 내보내기를 P4 뒤·P5 앞에 두는 해석(D1)이 맞는지.
- 이 PR 머지: 사용자 «머지» 메시지 → 상시 규약 8(CI 없음 → diff docs 전용 확인 뒤 squash).
- 다음 코드 작업: 로컬 RD 창 P1(명세 §3.2).

경과 9분(`date` 실측 06:54:53 → 07:04:33 UTC) · 사용 모델: 정책상 미기재
