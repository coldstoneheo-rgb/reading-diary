# HO-2026-1127 — 실기기 지적 4건 → 2번째 빌드 REPORT (중간 · 다음 창 인계)

발주: LC `life-coordinator-ae` 2026-11-27 · 수신 `reading-diary-46` · 시작 main `cc3c7c2`

> 이 레포는 PUBLIC이다. 사용자 사진 · 영상 · 그 안의 글 · 원본 파일 이름은 이 문서에 쓰지 않는다. 사용자 사진으로 잰 값은 개수로만.

## LC가 사용자에게 올릴 것

1. **미리보기 APK로 같은 사진 한 번 + 상자 덤프 공유**(조건 3 측정의 유일한 길 — 에뮬레이터 이미지 설치는 자동 분류기가 막았다). APK는 Play 앱과 나란히 깔리고(`…readingdiary.preview` · «문장서랍 미리보기») 새 동작(밑줄 어절로 시작하는 칸 · 접힌 원문 · 6~12줄 편집창 · 읽기 시작)을 바로 보여 준다. 단계는 LC에 보낸 6줄 메시지 그대로.
2. **R8 번들 크기 재기**: 에이전트의 릴리스 번들 빌드는 서명 없는 측정 빌드까지 분류기가 막는다. 사용자 1줄(PR `chore/r8-release-check` 머지 뒤): `! gradle :app:bundleReleaseR8Check --no-configuration-cache` → `app/build/outputs/bundle/releaseR8Check/*.aab` 바이트 수.
3. `/effort status` 1줄(슬래시 명령 — 에이전트가 실행할 수 없다).
4. 저장 버튼 자리 = LC 판정 «상단 바 글자 버튼 저장»(계획서 §7) — 사용자가 뒤집을 수 있다.

## 체크리스트(완료 조건)

- [x] 0. effort 정본 §4 — 이미 적용: `.claude/settings.json:33-37` medium · `.claude/agents/critic.md:6` high · 수집 정의 파일 없음. `/effort status`는 사용자 손.
- [~] 1. 사실표 — 코드 값 · 캡처(스크린샷 시험 411×891dp, LC 10:xx 지시로 에뮬레이터 대신) 끝 → 계획서 §1. 사용자 사진: 570자 · 켜진 12 · 맞음 2 · 과선택 ≥3 · 나머지 미확정(상자 덤프 대기).
- [x] 2. 재설계 계획서(LC «계획 가» 418f30a · 보정 6 = 계획서 §7-1) `docs/design/REDESIGN-2026-11-density.md` — critic 1차 6/10 → 지적 반영 · 2차 채점 전. before 7장 · after(조건 3·4·5) 7장 = 레포 밖 열람 폴더.
- [~] 3. 추출 기본값 — 브랜치 `fix/underline-words-first`(로컬). 271 기준 **205/3/66 · 정밀도 98.6% 그대로**(측정 표 diff 0 — 검출기 무변경). 과선택 · 놓침 수는 상자 덤프 뒤. critic 6/10.
- [~] 4. 읽기 시작 — 브랜치 `feat/start-reading`(로컬 · Room 스키마 무변경 v3). critic 6/10.
- [~] 5. 편집창 — 브랜치 `fix/editor-grows`(로컬). 500자에서 **4 → 12줄**(`ExtractedTextEditorSizeTest`), 짧은 글 6줄. 키보드 캡처는 에뮬레이터 없이 불가 → 미리보기 APK 실기기 확인으로. critic 6/10.
- [ ] 6. 재설계 구현 — LC «계획 가» 뒤.
- [~] 7. 브랜치 `chore/r8-release-check`(로컬): R8 켜기 · 서명 없는 측정 빌드형 · `scripts/sign-release.sh`. 번들 바이트는 사용자 손(위 2). critic 5/10(심각 1).
- [ ] 8. 2번째 빌드 구성표 · 출시 노트.
- [~] 9. 이 문서(중간). WORKLOG · AGENT_STATUS는 마감 때.

## 검증(이번 창에서 실행)

| 명령 | 대상 | 결과 |
|---|---|---|
| `testDebugUnitTest` 대상 9클래스 | 조건 3+5+4를 합친 로컬 커밋 | 54 · 실패 0 |
| `GoldenMeasureTest`(기기 덤프 11장) | 같은 커밋 | 205/3/66 · 98.6% · 이전 표와 diff 0 |
| `verifyRoborazziDebug` 전체 | 같은 커밋 | 470 · 실패 3 · 건너뜀 1 — 실패 3은 모두 기준 이미지(새 `extracted_editor_500` 없음 · `preselect_after_panel` · `preselect_before_none` 바뀜 = 편집창 높이). 기준 이미지 다시 찍기 전 |
| `assembleDebug`(미리보기 브랜치) | `build/preview-20261127` 3f5e621 | 성공 · 21,183,760 바이트 |
| `:app:tasks --all` | `chore/r8-release-check` | 구성 통과(releaseR8Check 태스크 나열) |

막힌 것(우회 0): 에뮬레이터 이미지 설치 · debug.keystore 복사 · 릴리스형 번들 빌드(서명 · 무서명 모두) — 자동 분류기 거부.

## 다음 창 인계

**에뮬레이터가 있다**(LC가 사용자 지시로 설치 · 13:5x 부팅 확인): AVD `lc_pixel8_api36`(API 36 · Play 서비스 · 1080×2400 · 데이터 `D:\Android\avd\`). 띄우기 `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd lc_pixel8_api36 -no-boot-anim -no-audio -gpu swiftshader_indirect`(`-gpu auto`는 멈춘다) · 부팅 약 100초 · 2코어라 gradle과 같이 돌리지 않는다. 미리보기 APK 3f5e621이 깔려 있다.
→ 상자 덤프는 다음 창이 에뮬레이터에서 직접 뜬다: 사용자 사진을 `adb push`(레포 밖 원본 · 레포에는 개수만) → 미리보기 앱에서 덤프 → 레포 밖 폴더에 `user-20261127` 자료로 넣고 `GoldenMeasureTest`로 맞음/과선택/놓침 개수. 사용자 폰 덤프는 «있으면 좋음». 키보드 올라온 편집창 캡처(조건 5)도 에뮬레이터에서.
재설계 구현(조건 6)은 «계획 가»를 받았다 — 계획서 §7-1 보정 6이 통과 기준. 순서: 조건 3 → 5 → 4 → R8 PR 먼저 연 뒤 R1 → R2 → R3.

브랜치 · 워크트리(모두 로컬 · 푸시 0):
| 브랜치 | 워크트리 | 남은 일(critic 1차 지적) |
|---|---|---|
| `fix/underline-words-first` | `second-build` | ① «원문 전체» 표시 조건이 `preselect`에 묶여 회전 뒤 사라짐 → `ocrFullText.isNotEmpty() && extractedText != ocrFullText`로 + 회전 시험 ② «미리 선택 전에 사용자가 고친 글은 덮지 않음» 시험(느린 preselectRunner) ③ 기준 이미지 2장 다시 찍기 |
| `fix/editor-grows` | `editor-grows` | 기준 이미지 record(`extracted_editor_500` 새로 · 바뀐 2장) · 크기 단언과 스크린샷 분리 |
| `feat/start-reading` | `start-reading` | ① 기록 Flow가 늦게 와 시작 카드가 깜빡임 → 기록 로드 신호 뒤에만 그리기 ② 시작 시각 고치기가 11sp 글자 클릭뿐 → ✎ 아이콘 48dp + Role · 미래/완독일 뒤 막기 ③ 시작 카드 스크린샷 시험 |
| `chore/r8-release-check` | `r8-release` | 고침 2dd1802: 심각(구성 캐시 비밀번호) · KAKAO 셸 변수 검사 · 빌드 앞 키 확인 · `IFS=`. 남음: JBR 경로 ④ versionCode 1 → 2 ⑤ Tink용 `-dontwarn` 은 missing_rules.txt가 요구하는 것만 |
| `docs/redesign-plan` | `redesign-plan` | 계획서 critic 2차 → PR → 계획 REPLY |
| `build/preview-20261127` · `combo/test-20261127` | `preview-apk` | 미리보기 전용 · PR 0 · 덤프 받은 뒤 지워도 됨 |

머지 순서: 조건 3 → 5 → 4(모두 `OcrDiaryScreen.kt` · `FirstRunSafetyTest.kt` 근처를 건드린다) → R8 → 계획서.
2번째 빌드 후보: 위 넷 + #78(draft — 취소 때 비트맵 회수 위험이 남아 **넣지 않기 권고**, 근본 수리 뒤).

## effort

- 세션: medium(레포 설정). critic 서브에이전트: high(정의 파일) — 3회(조건 3·4·5 · 계획서 + R8).
