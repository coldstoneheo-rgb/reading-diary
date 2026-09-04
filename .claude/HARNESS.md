# 개발 워크플로 하네스 (Loop Engineering)

이 프로젝트의 작업은 **루프 엔지니어링 하네스** 위에서 진행한다.
핵심 원칙은 루트 [`../CLAUDE.md`](../CLAUDE.md)에 있고, 이 문서는 그 운영 메커니즘을 정의한다.

## 핵심 원칙: 생성 ↔ 평가 분리

- **Maker** = 메인 세션. 코드/문서를 생성한다. **스스로 합격 판정하지 않는다.**
- **Critic** = 독립 서브에이전트([`agents/critic.md`](agents/critic.md)). draft를 채점하고 결함을 보고한다.
- Maker는 Critic의 `qa_report`가 임계(7/10, 심각 결함 0)를 넘을 때까지 **보정 루프**를 돈다.
- PR에 외부 리뷰 코멘트가 없으면 `code-review` 스킬 자체검증 결과를 `gh pr comment`로 남긴다.

## 6부품 매핑

| 부품 | 이 프로젝트에서 |
|------|----------------|
| 오토메이션 | `gradle :app:testDebugUnitTest`(Robolectric/Roborazzi), `assembleDebug` |
| 스킬 | [`skills/standard-workflow/SKILL.md`](skills/standard-workflow/SKILL.md), 글로벌 `harness-loop-engine`, `codebase-memory` |
| 커넥터(MCP) | `codebase-memory-mcp` — 코드 그래프 인덱스(프로젝트명 `reading-diary`) |
| 서브에이전트 | [`agents/critic.md`](agents/critic.md) — 독립 평가자 |
| 상태파일 체인 | `spec → plan → draft → qa_report → final`, 임시본은 `scratch/` |
| Critic | 최종 합격 판정 |

## 자율 파이프라인 (`/harness`)

```
1. EnterWorktree(name)                  → .claude/worktrees/<name>, 브랜치 worktree-<name>
2. 구현 → 타깃 테스트 → commit → git push -u origin <branch>
3. gh pr create --base main
4. gh pr view N --json reviews,comments + gh api .../pulls/N/comments
     코멘트 있음 → 타당성 검증 후 좁게 수정·해명·resolve
     코멘트 없음 → code-review 스킬 자체검증 → gh pr comment
5. gh pr merge N --squash   (worktree 안에서는 --delete-branch 금지)
6. ExitWorktree(keep) → git pull --ff-only origin main
   → git worktree remove --force .claude/worktrees/<name> → git branch -D → git push origin --delete
```

## 멈춰서 1회만 묻는 예외

- 릴리스 서명(`assembleRelease`/`bundleRelease`): 키스토어 비밀번호 필요 → 사용자가 `!`로 실행.
- Room 스키마 변경, 기존 사용자 데이터에 영향 주는 마이그레이션, force-push, 브랜치 대량 삭제.
- 요구가 진짜 모호해 방향이 갈리는 지점. 이때는 질문을 하나로 묶어 1회만 묻는다.

## 위험등급별 승인

| 등급 | 대상 | 게이트 |
|------|------|--------|
| 🟢 저위험 | 문서, `.claude`, 설정, 테스트 *추가* | 자율 진행 + Critic 게이트 |
| 🟡 중위험 | 화면/ViewModel 로직, 새 Composable, 네이버 API 호출 | 계획 제시 → 이의 없으면 진행 |
| 🔴 고위험 | Room 엔티티/스키마, 키 저장(SecureKeyManager), 백업/복원, 릴리스 서명 | 명시 승인 + 롤백 전략 |
