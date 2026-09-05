# CLAUDE.md

독서 다이어리 — Android(Kotlin + Jetpack Compose + Room) 단일 모듈 앱.
책/책장/독서일기(밑줄 구절)를 온디바이스에 저장하고, 네이버 책 검색으로 서지를 채우며,
촬영한 책 페이지에서 밑줄 구절을 추출한다(사용자가 자기 Gemini 키를 등록한 경우에만 실제 분석, 아니면 예시 문장).
이 파일은 **작업 원칙**만 담는다. 운영 메커니즘은 [.claude/HARNESS.md](.claude/HARNESS.md),
작업 루프는 [.claude/skills/standard-workflow/SKILL.md](.claude/skills/standard-workflow/SKILL.md),
아키텍처 결정은 [docs/adr/](docs/adr/).

## 구조 한눈에

```
app/src/main/java/com/example/
  MainActivity.kt          # 단일 Activity. Screen(sealed) 기반 자체 백스택 내비게이션
  data/Entities.kt         # Room 엔티티: Bookcase → Book → Diary (CASCADE)
  data/Daos.kt, AppDatabase.kt, ReadingRepository.kt
  data/api/GeminiApiClient.kt   # SecureKeyManager에 사용자 Gemini 키가 있으면 generativelanguage.googleapis.com에
                                #   페이지 이미지를 POST(키는 x-goog-api-key 헤더). 키가 없으면 제목 기반 시뮬레이션으로 폴백
  data/SecureKeyManager.kt # EncryptedSharedPreferences에 네이버·Gemini 키 저장(실패 시 평문 prefs 폴백)
  ui/viewmodel/ReadingViewModel.kt  # 상태·내비·CRUD 전부 여기 (AndroidViewModel)
  ui/screens/*Screen.kt    # Dashboard/BookDetail/AddEditBook/OcrDiary/Settings/Statistics/KnowledgeDrawer
  ui/screens/AddEditBookScreen.kt   # 네이버 책 검색(OkHttp 직접, openapi.naver.com)이 화면 코드 안에 있음
  ui/screens/OcrDiaryScreen.kt      # 카메라/갤러리 → 크롭 → viewModel.processUnderlineOcr → GeminiApiClient
  ui/theme/                # 테마 id로 전환하는 다중 컬러스킴
```

## 작업 원칙 (Karpathy)

1. **Think Before Coding** — 코딩 전에 가정을 명시한다. 모호하면 해석을 나열하고 먼저 확인한다.
2. **Simplicity First** — 요청 이상을 만들지 않는다. 과도한 추상화 금지.
3. **Surgical Changes** — 필요한 것만 건드린다. 무관한 코드를 "개선"하지 않는다. 기존 스타일을 따른다.
4. **Goal-Driven Execution** — "검증 추가" → "실패하는 테스트 작성 → 통과시키기".

## 루프 하네스 (Loop Engineering)

- **생성 ↔ 평가 분리.** 만든 주체(Maker)가 스스로 합격 판정하지 않는다.
  독립 `critic` 서브에이전트가 채점한다. 합격 임계는 [.claude/agents/critic.md](.claude/agents/critic.md)가 단독으로 정의한다.
- **코드 탐색은 `codebase-memory-mcp` 그래프 우선** (`search_graph`/`trace_path`/`get_code_snippet`).
  세션 시작 시 `list_projects`로 인덱스 확인, 없으면 `index_repository`. 인덱스는 **메인 체크아웃** 기준이므로
  워크트리에서 바꾼 파일은 그래프가 아니라 Read/Grep으로 본다.
- **하네스 모드(`/harness`)**: 글로벌 `harness-loop-engine` 스킬이 파이프라인을 정의한다. 프로젝트 고유 규칙은 HARNESS.md.

## PR 규칙

- **1 PR = 1 의도.** 하나의 PR은 하나의 목적만 가진다. 그 목적에 필요한 코드·테스트·문서는 함께 간다.
  목적이 다른 변경(예: 기능 + 무관한 리팩터, UI + Room 스키마)은 PR을 나눈다.
- `main`에서 직접 작업하지 않는다. `.claude/worktrees/<name>`에서 의도가 드러나는 브랜치로.
- 커밋 메시지: `feat:`/`fix:`/`docs:`/`chore:` + 한 줄 의도.

## 핵심 명령어

레포에 Gradle 래퍼(`gradlew`)가 **없다.** 로컬 Gradle 9.x 배포판(`~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle`)
또는 Android Studio를 쓴다. JDK는 Android Studio 동봉 JBR 21.

```bash
gradle :app:testDebugUnitTest        # JUnit + Robolectric 단위 테스트 (스크린샷 비교는 하지 않음)
gradle :app:verifyRoborazziDebug     # 스크린샷 회귀 검증 — UI 변경 PR의 필수 게이트
gradle :app:recordRoborazziDebug     # 기준 이미지 갱신(의도된 UI 변경 때만, PR에 이유 명시)
gradle :app:assembleDebug            # 디버그 빌드 — 루트에 debug.keystore 필요(아래)
```

`captureRoboImage`는 `record`/`verify`/`compare` 태스크로 실행할 때만 동작한다. `testDebugUnitTest`만 통과했다고
스크린샷이 검증된 것이 아니다.

`debug.keystore`는 gitignore되어 새 워크트리에는 없다. 메인 체크아웃의 `debug.keystore`를 워크트리 루트로 복사한다
(`cp ../../../debug.keystore .`). **새로 만들지 않는다** — 서명이 달라지면 기기의 기존 설치가 갱신 불가가 되어 Room 데이터가 날아간다.

## 안전 규칙

- **유료 API 키는 앱 바이너리에 절대 넣지 않는다** ([ADR-001](docs/adr/ADR-001-gemini-key-and-ocr-cost-model.md)).
  `GEMINI_API_KEY`는 secrets 플러그인 `ignoreList`로 `BuildConfig`에서 제외되며, 유일한 출처는 사용자가 등록한
  `SecureKeyManager`다. `BuildConfig.GEMINI_API_KEY`를 다시 참조하는 코드는 리뷰에서 거부한다.
- **비밀 3종**: Gemini 키(SecureKeyManager 전용, 평문 폴백 없음), `NAVER_CLIENT_ID/SECRET`, 릴리스 키스토어
  (`STORE_PASSWORD`/`KEY_PASSWORD`/`KEYSTORE_PATH`). 코드·로그·PR 본문·테스트 픽스처에 쓰지 않는다.
  네이버 키의 출처와 우선순위: ① SecureKeyManager → ② 빌드 셸 환경변수(`BuildConfig.ENV_NAVER_*`, `app/build.gradle.kts`가 굽는다)
  → ③ `.env`→`BuildConfig.NAVER_*`. **셸에 `NAVER_CLIENT_SECRET`을 export한 채 빌드하면 APK에 박힌다.**
  Gemini 키는 URL 쿼리가 아니라 `x-goog-api-key` 헤더로 보낸다. 헤더·예외 메시지를 통째로 로그에 남기지 않는다.
  `logging-interceptor`는 버전 카탈로그 항목까지 제거했다. 재도입하려면 `libs.versions.toml`부터 복원해야 하며,
  그때 `redactHeader("x-goog-api-key")`가 필수다.
- **릴리스 서명은 사용자만.** `assembleRelease`/`bundleRelease`는 에이전트가 실행하지 않고 사용자가 `!`로 직접 실행한다.
- 기기에 실제 Gemini 키가 등록돼 있으면 OCR 화면 조작이 **유료 외부 호출**을 일으킨다. 자동 테스트는 키 없이 돌린다.
- Room 스키마 변경은 🔴 고위험: 마이그레이션 전략 없이 엔티티 필드를 바꾸지 않는다(기존 설치 데이터 소실).

---
**Last Updated:** 2026-09-05 · **Stack:** Kotlin 2.2 · AGP 9.1 · Compose BOM · Room · OkHttp + org.json(직접 사용, Retrofit/Moshi 제거됨) · Roborazzi
