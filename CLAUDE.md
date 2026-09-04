# CLAUDE.md

독서 다이어리 — Android(Kotlin + Jetpack Compose + Room) 단일 모듈 앱.
책/책장/독서일기(밑줄 구절)를 온디바이스에 저장하고, 네이버 책 검색 API로 서지를 채운다.
이 파일은 **작업 원칙**만 담는다. 운영 메커니즘은 [.claude/HARNESS.md](.claude/HARNESS.md),
작업 루프는 [.claude/skills/standard-workflow/SKILL.md](.claude/skills/standard-workflow/SKILL.md).

## 구조 한눈에

```
app/src/main/java/com/example/
  MainActivity.kt          # 단일 Activity. Screen(sealed) 기반 자체 백스택 내비게이션
  data/Entities.kt         # Room 엔티티: Bookcase → Book → Diary (CASCADE)
  data/Daos.kt, AppDatabase.kt, ReadingRepository.kt
  data/api/GeminiApiClient.kt   # 이름과 달리 100% 오프라인 스텁(제목 기반 고정 문구). 실제 OCR/LLM 없음
  data/SecureKeyManager.kt # EncryptedSharedPreferences에 네이버 키 저장(실패 시 평문 prefs 폴백)
  ui/viewmodel/ReadingViewModel.kt  # 상태·내비·CRUD 전부 여기 (AndroidViewModel)
  ui/screens/*Screen.kt    # Dashboard/BookDetail/AddEditBook/OcrDiary/Settings/Statistics/KnowledgeDrawer
  ui/screens/AddEditBookScreen.kt   # 네이버 책 검색 호출(OkHttp 직접, openapi.naver.com)이 화면 코드 안에 있음
  ui/theme/                # 테마 id로 전환하는 다중 컬러스킴
```

## 작업 원칙 (Karpathy)

1. **Think Before Coding** — 코딩 전에 가정을 명시한다. 모호하면 해석을 나열하고 먼저 확인한다.
2. **Simplicity First** — 요청 이상을 만들지 않는다. 과도한 추상화 금지.
3. **Surgical Changes** — 필요한 것만 건드린다. 무관한 코드를 "개선"하지 않는다. 기존 스타일을 따른다.
4. **Goal-Driven Execution** — "검증 추가" → "실패하는 테스트 작성 → 통과시키기".

## 루프 하네스 (Loop Engineering)

- **생성 ↔ 평가 분리.** 만든 주체(Maker)가 스스로 합격 판정하지 않는다.
  독립 `critic` 서브에이전트가 채점하고, 7/10 미만이거나 심각 결함이 있으면 보정 루프를 돈다.
- **상태파일 체인** `spec → plan → draft → qa_report → final`. 임시 산출물은 `scratch/`(gitignore).
- **코드 탐색은 `codebase-memory-mcp` 그래프 우선** (`search_graph`/`trace_path`/`get_code_snippet`).
  세션 시작 시 `list_projects`로 인덱스 확인, 없으면 `index_repository`.
- **하네스 모드(`/harness`)**: worktree → commit → push → PR → 리뷰검증 → squash 머지 → main 동기화까지
  중간 보고 없이 직행하고 완료 시 1회만 표로 보고한다.

## PR 규칙

- **1 PR = 1 의도.** 문서 / UI / 데이터(Room 스키마) / 테스트 / 설정을 한 PR에 섞지 않는다.
- `main`에서 직접 작업하지 않는다. `.claude/worktrees/<name>`에서 의도가 드러나는 브랜치로.
- 커밋 메시지: `feat:`/`fix:`/`docs:`/`chore:` + 한 줄 의도.

## 핵심 명령어

레포에 Gradle 래퍼(`gradlew`)가 **없다.** 로컬 Gradle 9.x 배포판(`~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle`)
또는 Android Studio를 쓴다. JDK는 Android Studio 동봉 JBR 21.

```bash
gradle :app:assembleDebug            # 디버그 빌드 (debug.keystore 필요 — README 참조)
gradle :app:testDebugUnitTest        # Robolectric + Roborazzi 단위/스크린샷 테스트
gradle :app:recordRoborazziDebug     # 스크린샷 기준 이미지 갱신(의도된 UI 변경 때만)
```

## 안전 규칙

- **릴리스 서명은 사용자만.** `STORE_PASSWORD`/`KEY_PASSWORD`/`KEYSTORE_PATH`가 필요한 `assembleRelease`/`bundleRelease`는
  에이전트가 실행하지 않고 사용자가 `!`로 직접 실행한다.
- `.env`(NAVER_CLIENT_ID/SECRET)와 `debug.keystore`는 gitignore. 키를 코드·로그·PR 본문에 쓰지 않는다.
- Room 스키마 변경은 🔴 고위험: 마이그레이션 전략 없이 엔티티 필드를 바꾸지 않는다(기존 설치 데이터 소실).
- `GeminiApiClient`는 스텁이다. "AI 연동" 작업 전에 실제 백엔드/키 정책부터 합의한다.

---
**Last Updated:** 2026-09-05 · **Stack:** Kotlin 2.2 · AGP 9.1 · Compose BOM · Room · OkHttp(Retrofit/Moshi 의존성은 있으나 미사용) · Roborazzi
