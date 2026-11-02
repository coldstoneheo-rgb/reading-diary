# CLOUD-2026-1102 reading-diary 2단계 착수 문서 — REPORT

- 브랜치: `cloud/rd-stage2-docs` (분기점 origin/main `47900e1`, 2026-09-07 03:40 +09:00 PR #23)
- 시작: 2026-11-02T15:23+09:00 · 코드 변경 0 · effort: 세션 기본값(올리지 않음)

## 체크리스트

- [x] 1. ADR-005 — `docs/adr/ADR-005-internal-test-and-stage2-boundary.md` (§1 패키지 · §2 내부 테스트 · §3 경계 · §4 네이버 키 · §5 모델 상수 드리프트 · §6 처분 12행)
- [x] 2. ADR-003·004 역참조 — 각 파일 말미 1줄 추가(`git diff --stat`: 각 +2줄, 원문 수정 0)
- [x] 3. store-listing 초안 — `docs/store-listing/INTERNAL-TEST-2026-11.md` (§1 기본 · §2 문안([ASK] 부제) · §3 애셋·AI 선언 · §5 데이터 안전 N1~N7 파일:줄 · §6 테스터 안내 · §7 오너 손 U1~U8)
- [x] 4. privacy-policy.html — `docs/privacy/privacy-policy.html` (`grep -c "<script"` = 0, HTMLParser 파싱 OK, 외부 리소스 0)
- [x] 5. STAGE2-PLAN — `docs/STAGE2-PLAN-2026-11.md` (①~⑤ 각 파일·시그니처·테스트·완료 조건·추정, 부록 A·B, 합계 14~21h)
- [x] 6. AGENT_STATUS.json + WORKLOG.md — 루트. `json.load` OK
- [x] 7. app/ 변경 0 · PR — `git diff --stat origin/main -- app/` 출력 없음(0) · PR 제목 `[cloud]` 접두 · 머지 안 함
- [x] 8. 이 REPORT — 마지막 줄 경과·모델

## 한 일

| 파일 | 내용 |
|------|------|
| `docs/adr/ADR-005-internal-test-and-stage2-boundary.md` | 신규. applicationId·내부 테스트(≤5·«제공됨»·스키마 동결)·2단계 경계(병합 없음)·네이버 키 재검토 조건 4개·모델 상수 드리프트 표·보류 처분 12행 |
| `docs/adr/ADR-003-…md`, `docs/adr/ADR-004-…md` | 말미 역참조 1줄씩(원문 질문 수정 0) |
| `docs/store-listing/INTERNAL-TEST-2026-11.md` | 신규. 등록정보·문안·애셋 규격·AI 선언·정책 항목·데이터 안전(경로 N1~N7 파일:줄)·테스터 안내·오너 손 U1~U8 |
| `docs/privacy/privacy-policy.html` | 신규. 정적, 스크립트 0, 다크 모드, 시행일·연락처 자리 |
| `docs/STAGE2-PLAN-2026-11.md` | 신규. ①캔버스 ②SAF ③예시 2권 ④Unsplash ⑤applicationId + 부록 A(주석)·B(앱 안 약관 문구) |
| `AGENT_STATUS.json`, `WORKLOG.md` | 신규(루트) |

검증 명령과 결과:
- `git diff --stat origin/main -- app/` → 출력 없음(app/ 변경 0)
- `grep -rn "unsplash" app/src --include=*.kt | wc -l` → 20 (4파일: Entities 5 · AddEditBookScreen 9 · AppDatabase 3 · OcrDiaryScreen 3)
- `find app/src/test -name "*.kt" | wc -l` → 16, 스크린샷 기준 6장(`app/src/test/screenshots/*.png`)
- `grep -c "<script" docs/privacy/privacy-policy.html` → 0, `python3 html.parser` 파싱 OK
- `python3 -c "json.load(open('AGENT_STATUS.json'))"` → OK
- 그래들 빌드: 실행 안 함(코드 0, 발주 규약 6)
- **Critic(독립 서브에이전트): PASS 7/10, 심각 0 · 중요 2 · 경미 8 → 10건 전부 반영**(커밋 「docs: apply critic findings」). 중요 2건: ④ 완료 조건 모순(필터 상수 vs «unsplash 0회» → «`images.unsplash.com/photo-` 리터럴 0회 + 호스트 상수 1곳»으로 정정), ④ Coil 호출부 누락(`AddEditBookScreen.kt:574-577` 표지 미리보기가 `cleanCoverUrl`을 거치지 않음 → 추가). 경미: ⑤ grep 범위, 네이버 폴백 조건(무효 키는 폴백 안 함), https 재작성 범위(Google만), 한도 추정 근거, 처분 행 12 필드, 번호 체계 주석, 글자 수 34자, N6·버튼 줄 인용

## 발견

- **D1 처분 행 수.** ADR-003 보류 6이 질문 두 개(스토어 부제 + `[[위키링크]]` 유지)를 담고 있어 원문 기준 11문항 = 처분 12행(6a·6b). 인라인 집계 5+3+3+1=12와 일치.
- **D2 ADR-003-1 문항 차이.** 원문은 «빈 상태 vs 예시 샘플 1권» 양자택일. 처분은 둘의 결합(빈 상태 + 버튼으로 **2권**). 2권이어야 «서로 다른 책» 연결이 보인다 — ADR-005 §6 근거란에 기록.
- **D3 Unsplash가 DB에 저장돼 있다.** 수동 등록이 폴백 URL을 그대로 `saveBook(coverUrl = activeCoverUrl)`로 저장(`AddEditBookScreen.kt:770`), `cleanCoverUrl`은 `http`면 그대로 반환(`Entities.kt:43`). 코드 폴백만 지우면 기존 설치는 계속 Unsplash를 부른다 → STAGE2-PLAN ④에 표시 시점 필터 포함.
- **D4 «내보내기 = 사용자 주도 백업»의 표현.** SAF 내보내기는 가져오기·복원이 없어 화면에서 «백업»이라 부르면 ADR-003 Q7(가짜 백업 금지)과 충돌 → 문구는 «내보내기», 문서에서만 «사본»으로.
- **D5 모델 상수 드리프트.** `GeminiApiClient.kt:39` 주석(미확인) · ADR-001:82-83(GA 확인됨) · ADR-004:61(미검증)이 서로 다름 → ADR-005 §5에서 ADR-001을 정본으로 정정, 주석은 STAGE2-PLAN 부록 A.
- **D6 Google Books 경로.** 네이버 키가 없으면 검색어가 Google Books로 간다(`AddEditBookScreen.kt:219-223`). 데이터 안전·처리방침에 N2로 포함.
- **D7 날짜.** 발주문 «09-06 이후 커밋 0» — 실제 마지막 커밋은 `47900e1` 2026-09-07 03:40 +09:00(PR #23). 결론(이후 커밋 0, 56일)은 같다.
- **D8 앱 안 약관·개인정보 문구.** `SettingsScreen.kt:574`·`:610`이 옛 이름 «나만의 서재 다이어리»를 쓰고 네이버 검색어 전송·자동 백업을 빠뜨린 채 «전송하지 않는다»고 단정 → STAGE2-PLAN 부록 B(내부 테스트 전 권장).
- **D9 androidTest 패키지 단언.** `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt:20`이 `"com.example"`을 단언 → ⑤에서 함께 수정.
- **D10 시드 구절 저작권.** 현재 시드(`AppDatabase.kt:106-127`)는 보호기간 내 작품의 번역 인용. 공개 배포 예시로는 부적합 → ③ 예시는 보호기간 만료 국내 작품 또는 자작 문장.
- **D11 예시 표식 오인 위험.** 스키마 없이 prefs에 id만 저장하면 DB 재생성 후 id 재사용으로 진짜 책이 «예시»로 지워질 수 있음 → ③에 id+제목 대조 + `onCreate`에서 키 삭제.
- **D12 «제공됨» 해석.** 내부 테스트 트랙 출시 상태 «내부 테스터에게 제공됨»으로 해석해 ADR-005 §2 완료 정의에 적었다. 다르면 LC가 정정.
- **D13 로그.** `GeminiApiClient.kt:48`이 `Log.d`로 책 제목을 남긴다(`isMinifyEnabled=false`라 릴리스에도 남음). 비밀은 아니지만 개인 독서 정보 — 범위 밖, 후속 후보.
- **D14 촬영 캐시.** `cacheDir`의 `camera_photo_*.jpg` 삭제 코드 없음(`OcrDiaryScreen.kt:134-136`, ADR-002 후속 발견 그대로) → 처리방침 2항에 사실대로 «임시 캐시에 남을 수 있음».
- **D16 네이버 무효 키 폴백 없음.** 키가 401/403/429거나 헤더 불가 문자면 Google로 폴백하지 않고 오류만 보인다(`AddEditBookScreen.kt:177-217`) — 키가 APK에 박힌 내부 테스트에서 키가 폐기되면 검색이 통째로 막힌다. ADR-005 §4 재검토 조건과 연결(Critic 지적에서 파생).
- **D15 SDK 데이터 공개 미확인.** ML Kit·코드 스캐너의 SDK 자체 수집 항목은 이 VM에서 확인하지 않았다 → 데이터 안전 §5에 [사용자 확인].

## LC·사용자에게 필요한 것

- **[ASK] 스토어 부제** — 후보 «밑줄이 책과 책을 잇는 독서 다이어리»(대안: ADR-003 Q9 문장). 작업을 막지 않는다.
- 데이터 안전 [사용자 확인] 3건: 공유 여부(N1·N6 «아니요» 초안), 삭제 요청 항목 답, Google SDK 몫.
- 사용자 손 U1~U8(키스토어·콘솔 앱 생성·아이콘·처리방침 배포·테스터 5·폼 제출·부제·`bundleRelease`).
- LC 판단: STAGE2-PLAN 부록 B(앱 안 약관 문구)를 내부 테스트 전 필수로 넣을지.
- 머지: LC 게이트 뒤 사용자 1줄.

총 경과: 약 16분(벽시계 2026-11-02T06:23Z→06:39Z, `date -Iseconds` 기준) · 사용 모델: claude-opus-5-5(세션 설정값, 서빙 모델은 다를 수 있음)
