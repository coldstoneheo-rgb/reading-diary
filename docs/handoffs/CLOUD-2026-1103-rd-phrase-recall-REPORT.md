# CLOUD-2026-1103 reading-diary «문구 회상» 명세·계획·ADR-006 초안 — REPORT

- 발주: 클라우드 20건째(정본 `life-coordinator/handoffs/CLOUD-2026-1103-wave5-19-22.md` §20 — 이 세션은 읽지 못함, 프롬프트 인라인이 전부)
- 브랜치: `cloud/rd-phrase-recall-spec` (분기점 origin/main `c0bccd2`, 2026-11-03 09:37:06 +09:00, PR #32)
- 시작: 2026-11-03T13:02:20+09:00 (`date` 실측 04:02:20 UTC) · 코드 변경 0 · 그래들 0 · 외부 호스트 호출 0 · effort: 세션 기본값(올리지 않음)
- 중복 확인(세션 시작 직후, 13:02~13:04 +09:00): `AGENT_STATUS.json` as_of `2026-11-03T09:35:07+09:00`·next_actions에 «문구 회상» 없음 ·
  `git log origin/main -8` 최신 `c0bccd2`(#32 기록 PR) · `grep -rn "회상\|PhraseRecall"` → `OcrDiaryScreen.kt:1143`·`:1488` 공유 카드 팔레트 이름 «장미빛 회상»뿐(기능 아님) → **미착수 확인**

## 체크리스트 (완료 조건 번호)

- [x] 1. 사실 확인 3건 표(파일:줄) — `docs/PHRASE-RECALL-SPEC-2026-11.md` §0 F1~F3 (+ 새 전제 F4~F7)
- [x] 2. «문구 회상» 기능 명세 — 같은 파일 §1 원문 인용 · §2.2 흐름 · §2.3 검색 규칙(BookComparison 부분 일치 + KeywordExtractor 정규형 + KeywordLinks 칩 + 책·기간·쪽 필터) · §2.4 사진 썸네일·복기
- [x] 3. 구현 계획 — §3 P1~P4(파일·완료 조건), 사진 = `noBackupFilesDir/diary_photos/`, P3 Room v3 + `Migration(2,3)` + 테스트 M1~M7
- [x] 4. ADR-006 초안 — `docs/adr/ADR-006-phrase-recall-photo-storage.md` (충돌 표 · 선택지 A-선행/A-후행/B + 리스크 · 결정 [ASK] · 부속 Q-a/Q-b [ASK])
- [x] 5. SAF 내보내기와의 순서 — 명세 §4 (가)/(나)/(다) 장단 1줄씩 [ASK]
- [x] 6. 스토어 부제 후보 3개 — 명세 §5 [ASK] (글자 수 `python3 len()` 실측 40/31/40)
- [x] 7. `git diff --stat origin/main -- app` → 출력 0줄
- [x] 8. `AGENT_STATUS.json`(as_of `date` 실측, next_actions 맨 앞 [ASK] 4건) · `WORKLOG.md` 블록 1개 추가
- [x] 9. PR([cloud]) · 본문 = 이 REPORT

## 한 일

| 파일 | 내용 |
|------|------|
| `docs/PHRASE-RECALL-SPEC-2026-11.md` | 신규. 사실 확인 F1~F7 · 원문 · 흐름 · 검색 규칙 · 사진 규칙(보관·삭제·dangling·편집) · 하지 않는 것 · P1~P4 계획(파일·시그니처·테스트·완료 조건) · 마이그레이션 테스트 M1~M7 · SAF 순서 [ASK] · 부제 [ASK] |
| `docs/adr/ADR-006-phrase-recall-photo-storage.md` | 신규. STAGE2 DoD(`:10`)·ADR-005 §2(`:39`)·§3(`:54`)·§6(`:100`)과의 충돌 표 · 선택지 3개 리스크 표 · 공통 조건 5 · 결정 [ASK] · 부속 Q-a/Q-b [ASK] · 재검토 조건 |
| `AGENT_STATUS.json` | as_of·phase·current_task·waiting_on·next_actions([ASK] 4건 앞에) |
| `WORKLOG.md` | 블록 1개 추가(위 블록 수정 0) |
| 이 REPORT | |

검증 명령과 결과:
- `git diff --stat origin/main -- app | wc -l` → `0`
- `git diff --name-only origin/main | grep -v '^docs/\|\.md$\|^AGENT_STATUS.json$' | wc -l` → `0` (docs·md·상태 파일만)
- `python3 -c "import json;json.load(open('AGENT_STATUS.json'))"` → OK
- `python3 json.load(app/schemas/…/2.json)` → `version 2`, diaries 열 `id, bookId, page, selectedText, notes, createdAt`
- 부제 글자 수 `python3 len()` → 기존 20 · 후보 1 40 · 2 31 · 3 40 (첫 초안의 손셈 37/30/39는 틀려서 실측값으로 교체)
- **Critic(독립 서브에이전트) 2회:** 1차 6/10 NEEDS_REVISION(심각 0·중요 6·경미 8) → 14건 전부 반영 → 2차 **8/10 PASS**(새 경미 5) → 5건도 반영.
  1차 중요 6: A-후행·C 중복(→ 합침, 손실 행 추가) · 부제 후보 1 «읽었는지» 과장(→ «남긴 밑줄») · 사진 프라이버시 기본값이 [ASK]에서 빠짐(→ Q-a/Q-b, 보수적 기본값) · 편집 시 null 이중 의미(→ `PhotoEdit` Keep/Replace/Remove + `DiaryDao` 조회) · B가 시그니처로 구현 불가(→ 저장 순서·비용 명시) · dangling 경로 누락(→ «사진 없음(이 기기에 없음)»)
- 그래들: 실행 안 함(코드 0, 상시 규약 6)

## 발견

- **D1 (F2 차이)** «OCR 사진 비저장»은 «기록과 연결해 보관하지 않음»이 정확한 표현이다. 카메라 원본은 `cacheDir`에 남아 지워지지 않는다(`OcrDiaryScreen.kt:110-118`, ADR-002:30). 계획 P2에서 해소.
- **D2 (F3 차이)** ADR-002 «사진은 기기 밖으로 안 나감»(`:12`)은 기본 경로 한정이다. 정밀 분석은 동의를 받고 사진을 Google로 보낸다(`:14`·`:21-22`). 문구 회상은 새 전송 0.
- **D3 Auto Backup** 사진을 `filesDir`에 두면 자동 백업으로 기기 밖에 나간다(`backup_rules.xml`은 키 저장소만 제외). 용량 한도를 넘기면 일기 DB 백업까지 멈출 수 있다(25MB는 기억이고 [확인 필요]) → 기본안 `noBackupFilesDir`.
- **D4 `REPLACE` 편집** `DiaryDao.insert`가 `REPLACE`(`Daos.kt:63-64`)이고 편집도 insert라서 사진 경로가 조용히 null이 될 수 있다. 별도 테이블 + CASCADE면 사진 행이 지워진다 → `PhotoEdit` + M5 테스트.
- **D5 검색 엔진** `KeywordLinks.build`는 책 2권 이상에 나온 단어, 상위 30개만 돌려준다(`:45-46`). 그래서 한 책 안의 문구를 찾는 회상의 주 엔진은 `BookComparison.matchKind`(부분 일치)다. 발주의 «KeywordLinks 단어·구절 검색»은 칩과 정규형 일치로 재사용한다.
- **D6 처리방침** `privacy-policy.html:41` «사진 자체는 일기에 저장되지 않습니다»는 사진 보관 PR(P4)과 같은 PR에서 고쳐야 한다.
- **D7** 첫 AAB 업로드 여부는 레포로 확인할 수 없다(`AGENT_STATUS` waiting_on U1~U8 열림). A-선행이 가능한지가 이 사실에 달려 있다.

## LC·사용자에게 필요한 것

- [ASK] ADR-006: **A-선행**(첫 업로드 전 v3) / **A-후행**(2단계는 P1만, 사진은 테스트 뒤 — 그동안 찍은 사진 손실) / **B**(스키마 0, 파일 이름 규약)
- [ASK] ADR-006 부속 **Q-a** «이 사진을 기록과 함께 보관» 기본값(답 전 = 끔) · **Q-b** 기기 간 이전에 사진 포함(답 전 = 제외)
- [ASK] SAF 순서: (가) SAF 먼저 / (나) 회상 P1 → SAF → 사진 / (다) 회상 전체 → SAF
- [ASK] 스토어 부제: 기존 «밑줄이 책과 책을 잇는 독서 다이어리» / 후보 1·2·3(2·3은 P4 머지 뒤에만 쓸 수 있음)
- 확인 요청: 첫 AAB 업로드(U8)를 했는지(D7)
- 이 PR 머지: 사용자 «머지» 메시지 → 상시 규약 8(CI 없음 → diff docs 전용 확인 뒤 squash)

경과 13분(`date` 실측 13:02:20 → 13:15:34 +09:00) · 사용 모델: 정책상 미기재
