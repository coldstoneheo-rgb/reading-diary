# 개발 워크플로 하네스 (Loop Engineering)

핵심 원칙은 루트 [`../CLAUDE.md`](../CLAUDE.md), 자율 파이프라인은 글로벌 `harness-loop-engine` 스킬
(`~/.claude/skills/harness-loop-engine/SKILL.md`, `/harness` 명령)이 정의한다.
이 문서는 **이 프로젝트에만 적용되는 델타**만 담는다. 파이프라인 단계 자체를 여기에 복제하지 않는다.

## 핵심 원칙: 생성 ↔ 평가 분리

- **Maker** = 메인 세션. 코드/문서를 생성한다. **스스로 합격 판정하지 않는다.**
- **Critic** = 독립 서브에이전트([`agents/critic.md`](agents/critic.md)). 합격 임계는 그 파일이 단독 소유한다.
- Maker는 Critic이 PASS를 낼 때까지 보정 루프를 돈다.
- Critic의 `qa_report`는 **PR 코멘트로 남긴다**(`gh pr comment`). `scratch/`는 워크트리와 함께 사라지므로 기록 장소가 아니다.
- PR에 외부 리뷰 코멘트가 없으면 `code-review` 스킬 자체검증 결과도 PR 코멘트로 남긴다.

## 6부품 매핑

| 부품 | 이 프로젝트에서 |
|------|----------------|
| 오토메이션 | `gradle :app:testDebugUnitTest`, UI 변경 시 `gradle :app:verifyRoborazziDebug` |
| 스킬 | [`skills/standard-workflow/SKILL.md`](skills/standard-workflow/SKILL.md), 글로벌 `harness-loop-engine`, `codebase-memory` |
| 커넥터(MCP) | `codebase-memory-mcp` — 프로젝트명 `reading-diary`, 루트는 메인 체크아웃(워크트리는 미포함) |
| 서브에이전트 | [`agents/critic.md`](agents/critic.md) — 독립 평가자 |
| 상태파일 | `spec`/`plan`은 PR 본문(Summary/Plan), `qa_report`는 PR 코멘트. 임시 초안만 `scratch/` |
| Critic | 최종 합격 판정 |

## 이 프로젝트의 파이프라인 델타

- 워크트리 진입은 `EnterWorktree(name)` (브랜치 `worktree-<name>`). 진입 직후 메인 체크아웃의 `debug.keystore`를 복사한다.
- 검증 게이트: 코드 변경이면 `testDebugUnitTest`, Compose UI 변경이면 `verifyRoborazziDebug`까지.
- `gh pr merge --squash` 후 `ExitWorktree(keep)` → 메인에서 `git pull --ff-only origin main` → 워크트리·브랜치 정리.

## 멈춰서 1회만 묻는 예외

- 릴리스 서명(`assembleRelease`/`bundleRelease`): 키스토어 비밀번호 필요 → 사용자가 `!`로 실행.
- Room 스키마 변경, 기존 사용자 데이터에 영향 주는 마이그레이션, 브랜치 대량 삭제.
- force-push는 `settings.json`이 **전면 거부**한다(`--force-with-lease` 포함). 필요하면 사용자가 `!`로 실행한다.
- 요구가 진짜 모호해 방향이 갈리는 지점. 이때는 질문을 하나로 묶어 1회만 묻는다.

## 위험등급별 승인

| 등급 | 대상 | 게이트 |
|------|------|--------|
| 🟢 저위험 | 문서, `.claude`, 설정, 테스트 *추가* | 자율 진행 + Critic 게이트 |
| 🟡 중위험 | 화면/ViewModel 로직, 새 Composable, 네이버·Gemini 호출 경로 | 계획 제시 → 이의 없으면 진행 |
| 🔴 고위험 | Room 엔티티/스키마, 키 저장(SecureKeyManager)·키 로깅, 백업/복원, 릴리스 서명 | 명시 승인 + 롤백 전략 |
