# 조사 1쪽 — 갤럭시 [T] 글자 추출 · «공유로 받기» · 블록으로 본문만 남기기 (2026-11-11, 구현 0)

계기: 사용자(11-11 21:0x)가 갤럭시 S24 카메라 [T]로 기준 사진 7번과 같은 쪽을 추출해 공유 → 본문은 거의 맞고 자필 메모만 틀림. «추출된 텍스트를 수정하면 되겠지» = 자필 메모는 **미리 뽑고 사용자가 고친다**가 기대 동작(설계 D와 같은 방향).
같은 쪽 비교 수치는 `SPIKE-UNDERLINE-2026-11.md` §1(엔진 표, 대상 page · memo).

## ⓐ 다른 앱이 갤럭시 [T]를 직접 부를 수 있나

**결론: 공개 SDK·API·인텐트 없음 — 사용자가 [T]에서 «공유/복사»로 넘기는 길만 현실적이다.** (11-11 웹 문서 조회, 사진·글 전송 0)

| 질문 | 결론 | 근거(직접 연 페이지) |
|---|---|---|
| 삼성 개발자 SDK에 텍스트 인식이 있나 | 없다. Galaxy SDK 목록 = IAP·Wallet·Health·S Pen Remote·DeX 등. [T] 엔진은 삼성 R&D 우크라이나의 온디바이스 엔진으로 카메라·갤러리·Smart Select에 내장 — 외부 공개 언급 없음 | https://developer.samsung.com/galaxy · https://research.samsung.com/news/The-One-UI-Interface-Uses-Text-and-Image-Recognition-Developed-by-Samsung-R-D-Institute-Ukraine |
| 다른 앱이 부르는 문서화된 인텐트 | 없다. 지원 문서는 «(T) 누름 → 강조 → 복사·붙여넣기» 사용자 조작만. `com.samsung.android.*` 내부 서비스는 **비공개 — 의존 금지**(One UI 업데이트로 깨짐 · 갤럭시 밖엔 없음) | https://www.samsung.com/ae/support/mobile-devices/how-to-extract-text-from-a-photo |
| [T]의 «공유»가 표준 공유인가 | 공유 메뉴(Copy/Translate/Select all/Share → 공유 시트)는 공식 확인. `ACTION_SEND text/plain` 형식은 **추정**(문서에 형식 없음) — 사용자 손 1회로 확정 | https://www.samsung.com/us/support/answer/ANS10002535/ · 받는 쪽 표준 https://developer.android.com/training/sharing/receive |
| 대안 | ML Kit v2 한국어 = 공개 API·온디바이스(지금 엔진, 언번들). 시스템 «이미지 속 글자 선택»·서클 투 서치 = 앱용 공개 API 없음(추정, 기사 요약) | https://developers.google.com/ml-kit/vision/text-recognition/v2/android |

미확인: Knox SDK·Samsung SDS AICR(검색 요약만 — AICR은 클라우드 과금이라 «사진은 기기 밖으로 안 나감» 원칙과 어긋나 후보 아님) · 렌즈 공유 형식.
사용자 손(1분): S24 [T] → 글자 선택 → «공유» → 공유 시트에 메모·메시지 같은 일반 글 대상이 뜨는지 본다(= text/plain 확정).

## ⓑ «공유로 받기» — 갤럭시 [T] · Google 렌즈에서 공유한 글(과 사진)을 기록 초안으로

지금: `AndroidManifest.xml`의 액티비티 1개, `intent-filter`는 MAIN/LAUNCHER뿐(:40-44) — 다른 앱의 공유 목록에 안 뜬다.

| 단계 | 바뀌는 것 |
|---|---|
| 매니페스트 | `MainActivity`에 `ACTION_SEND` + `text/plain`(글) · `image/*`(사진) 필터. `ACTION_SEND_MULTIPLE`은 뒤로 |
| 받기 | `MainActivity.onCreate/onNewIntent`에서 `EXTRA_TEXT` · `EXTRA_STREAM`(content URI, 읽기 권한은 그 순간뿐) → 사진은 받자마자 `DiaryPhotoStore`로 앱 안에 복사 |
| 화면 | 자체 백스택에 «받은 글 초안» 화면 1개: 책 고르기(최근 책 먼저) → 글 편집창(미리 채움, 줄 고르기 D와 같은 UI 재사용 가능) → 저장 = 기존 일기 저장 경로 |
| 테스트 | 인텐트 → 초안 화면 Robolectric 1 · 스크린샷 1 · 사진 URI 권한 만료 뒤에도 사본이 남는지 1 |

PR 수: **2** — ① 글만(`text/plain`, 사진 0 — 처리방침 영향 0) ② 사진 함께(`image/*` + 사본 보관). 각 1 의도.
처리방침: 받은 글·사진은 기기 안에만 저장(지금 방침 «사진은 자동으로 기기 밖으로 나가지 않음»과 같음). 다만 **[T]·렌즈 쪽 처리는 그 앱의 방침**이고 우리 앱이 통제하지 않는다 — 안내 문구 1줄(«다른 앱에서 받은 글은 그 앱이 만든 것입니다»). 렌즈는 클라우드 처리일 수 있어 «사진이 밖으로 안 나간다»는 우리 약속과 별개임을 문구로 분리.
한계: 받은 글에는 밑줄 정보가 없다(전체 글) → 밑줄만 고르기는 D(줄 고르기)로 사용자 손.

## ⓒ ML Kit 블록 정보로 «본문만 남기기» 흉내

지금 쓰는 정보: `MlKitTextExtractor.kt:27-33`이 블록마다 `boundingBox` + 줄 글자를 받고, `OcrTextAssembler.assemble`(:23-26)은 위→왼쪽 정렬해 전부 잇는다(블록 구분 = 빈 줄). 블록·줄 상자는 있지만 «머리글/본문» 표지는 없다(ML Kit v2 Text API에 문서 구조 분류 없음).

규칙 후보(순수 함수, 블록 상자·글자만으로):
1. **본문 블록** = 줄 수 ≥ 2 이고 줄 높이가 페이지 블록 줄 높이 중앙값의 0.8~1.25배, 왼쪽 끝이 본문 왼쪽 정렬선 ±0.5자.
2. **머리글·쪽번호** = 페이지 위·아래 8% 안의 짧은 블록(≤ 6자) 또는 숫자만인 블록 · 세로로 긴 상자(세로 글).
3. **제목** = 본문보다 줄 높이 1.3배 이상.
4. **자필 메모** = 위 어디에도 안 맞고 신뢰도가 낮은 블록(ML Kit은 줄·요소 `confidence` 제공) — 버리지 않고 «메모 후보»로 따로 보여 사용자가 고친다(④·사용자 기대 동작).

측정 길: 기준 자료 `[page]`가 이미 «본문만»이라, 엔진 출력 `page` 대상의 **어절 정밀도**가 곧 «본문만 남기기» 성능이다(머리글·쪽번호를 끌어오면 정밀도가 떨어진다). 규칙을 넣기 전후를 같은 표로 비교 — 상자는 Tesseract 블록(`block_num`)으로 먼저 흉내 내고, 기기 ML Kit 덤프(스파이크 문서 §4의 PR 1)가 생기면 같은 자료로 다시 잰다.
쓰임: ② 문단(틀 안 본문만) · ③ 페이지 요약의 입력 정리(머리글·쪽번호가 요약에 섞이지 않게).
