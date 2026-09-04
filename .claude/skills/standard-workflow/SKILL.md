---
name: standard-workflow
description: 독서 다이어리(Android) 표준 작업 워크플로우 — 작업 선정부터 머지까지. 루프 하네스(생성·평가 분리) 기반. 새 작업을 시작하거나, PR을 올리거나, 리뷰에 대응하거나, "다음 뭐 할까"를 정할 때 사용한다.
---

# 표준 워크플로우 (Loop Engineering)

원칙은 [`../../../CLAUDE.md`](../../../CLAUDE.md), 프로젝트 델타는 [`../../HARNESS.md`](../../HARNESS.md),
파이프라인 단계는 글로벌 `harness-loop-engine` 스킬.
핵심: **Maker와 Critic을 분리**하고, **승인 강도를 위험에 비례**시킨다.

## 작업 루프 (0→10)

```
0.  컨텍스트 로드 — MEMORY.md 확인, codebase-memory list_projects → 없으면 index_repository(메인 체크아웃)
1.  현 상태 동기화 — git status/원격/worktree 잔존 확인. main은 pull --ff-only
2.  다음 작업 선정 — 리스크 최소화 우선. 자율영역 ↔ 인간필요영역(서명·스키마)으로 분해
3.  계획 + 완료정의(DoD) — 범위/비범위/변경파일/검증 명령. PR 본문 Summary/Plan에 그대로 적는다(= spec/plan)
4.  위험등급 게이트 — HARNESS.md 표. 🔴이면 명시 승인 후 진행
5.  worktree 브랜치 — EnterWorktree(name) → 메인의 debug.keystore 복사. 오래된 브랜치 재사용 금지
6.  구현(Maker) — 승인 계획 내에서만. 범위·위험 커지면 멈추고 재보고
7.  검증 — 코드 변경: `gradle :app:testDebugUnitTest`. Compose UI 변경: `gradle :app:verifyRoborazziDebug`까지.
       결과 PASS/MINOR/BLOCKER + "왜 안전한가"
8.  Critic 게이트 — critic 서브에이전트에 diff·워크트리 경로·검증 결과를 넘겨 채점. NEEDS_REVISION이면 6으로.
       qa_report는 PR 코멘트로 남긴다
9.  commit → push → PR(본문: Summary/Plan/Verification/Notes) → 리뷰 대응(봇 맹신 금지) → squash 머지
10. main 동기화 → worktree/브랜치 정리 → MEMORY.md 갱신(무엇을/왜/다음) → 0으로 회귀
```

## 실패 복구 (같은 프롬프트 재시도 금지)

- **컨텍스트 부족** → 그래프(`trace_path`)·관련 파일·스키마를 더 준다.
- **방향 오류** → spec/DoD로 돌아간다.
- **구조적 충돌** → 접근을 바꾼다. 작게 쪼개 격리 테스트한다.

## 이 프로젝트 특이사항

- `gradlew` 없음 → 로컬 Gradle 9.x 배포판 또는 Android Studio. 빌드 검증은 시간이 걸리므로 변경 부위 타깃 테스트 우선.
- Roborazzi는 `verify`/`record` 태스크로만 실제 캡처·비교한다. 기준 이미지(`app/src/test/screenshots/`)는 커밋됨.
  의도된 UI 변경만 `recordRoborazziDebug`로 갱신하고 PR에 이유를 적는다.
- `GeminiApiClient`는 실제 키가 있으면 유료 외부 호출을 한다. 테스트는 placeholder 키로.
- 릴리스 서명은 사용자 전용(`!`).
