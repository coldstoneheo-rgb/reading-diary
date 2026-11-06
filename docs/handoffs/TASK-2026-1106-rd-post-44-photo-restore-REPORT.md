# TASK-2026-1106-rd-post-44-photo-restore — REPORT

- 발주: LC, 창 reading-diary-eb. PR #44 같은 브랜치에서 이어 작업(새 PR 0).
- 메인 체크아웃은 시작 때 이미 `fix/camera-state-pagecount`, head `fc5e79d`, 작업 트리 clean이었다(전환·stash·reset 0).
- 수리 코드 head: `2af984c`. 그 뒤 커밋은 문서만이다.
- 시간: 시작 10:28, 전체 묶음 종료 12:29. 예산 90분을 약 30분 넘겼다(LC 수용). 코드 변경 뒤 첫 그래들이 5~8분씩 걸렸다.

## 체크리스트(완료 조건 번호)

- [x] 1. ① 테스트(옛 코드 red) + 수리. New·Replace·화면 사진을 모두 단언했다.
- [x] 2. ② 거짓 실패 제거 + 테스트 1(옛 코드 red).
- [x] 3. ③ 단언 3건(effect 키·실패 문구·버튼 재활성) + 판정 쿼리 예외 처리 + 테스트 1.
- [x] 4. ④ commitFailure는 변이 red. rotation은 «현 동작 고정 테스트»로 명시. ⑤ import 중복 0.
- [x] 5. 묶음별 verify 실패 0, 스키마 diff 0(아래 표).
- [x] 6. critic high 8/10 PASS → 이 REPORT·AGENT_STATUS·WORKLOG·PR 본문 → [DONE].
- [x] 7. APK `D:\사진\reading-diary\reading-diary-debug-20261106-1255-c6b83f3.apk`(main `c6b83f3`, 21,615,525바이트, 서명 SHA-256 `aba8e5e3…`, `:app:assembleDebug` exit 0 142초, `apksigner verify` exit 0). 사용자 재테스트는 아직 없다.
- [x] 로컬 브랜치 정리: `worktree-final-build` 삭제. `feat/book-search-kakao`(`8defb6a`)는 `git diff --stat feat/book-search-kakao 4cef4dc` 0줄 확인 뒤 LC 허용으로 `git branch -D`(로컬만, 원격 0).

## ① 원인과 수리

LC 코드 추적을 Robolectric으로 재현해 확정했다. 옛 코드에서 커밋 뒤 복원·재저장·원본 읽기 실패는 다음 결과를 낸다.

| 슬롯 | 커밋된 상태 | 재저장 뒤 |
|---|---|---|
| Replace(p1) | `[p3]` | `[]` — 원본·교체본 모두 소실 |
| New | `[kept, p2]` | p2 행 유실 |
| 화면에 띄운 사진(LC 추적 밖에서 추가 발견) | `[kept, p2]` | p2 행 유실 |

원본을 읽을 수 있어도 커밋본을 지우고 같은 사진을 새로 썼다(New·화면 사진 모두).

**수리(나):** 초안이 출발한 DB 사진 id(`draftBaseIds`)를 번들에 함께 저장한다(`draftsInitialized`를 대체). 복원 때 DB id 목록이 기준과 다르면 이 초안은 이미 커밋된 것이므로 슬롯을 DB 기준으로 다시 만들고 화면의 사진도 비운다.

(가)를 고르지 않은 이유: 어떤 행이 실패한 슬롯의 커밋본인지 구분할 표식이 없어, 사용자가 일부러 지운 사진까지 막게 된다.

판별 근거: 사진 행을 바꾸는 곳은 `applyPhotos` 하나이고 `DiaryPhoto` id는 AUTOINCREMENT라 재사용되지 않는다. DB가 기준과 같으면 초안을 그대로 둔다(커밋 전 사망 보존, LC가 요청한 반대 케이스).

**알려진 한계:** 저장을 누른 뒤 커밋 전에 사진을 더 고치고 그 상태로 프로세스가 죽으면, 복원 때 그 추가분은 재동기화로 사라진다. 저장 중에 사진 입력이 잠기지 않기 때문이다(후속).

## ②③⑤ 수리

- ② `ReadingRepository.saveDiary`: 커밋 뒤 파일 삭제 예외를 파일마다 잡는다. 저장은 끝난 것으로 알리고 남은 파일은 앱 시작 sweep이 걷는다. 취소 예외는 다시 던진다.
- ③ 판정 쿼리 예외를 잡아 토스트로 알리고 표식을 푼다. **판단:** 버튼을 계속 막으면 초안을 저장할 길이 없어지므로, 드문 중복 가능성을 알린 채 저장을 연다(반박 가능).
- critic 결함 2: 편집의 `loadPhotos`도 이제 커밋 판정이므로 예외를 잡는다. 이쪽은 저장을 막아 둔다(`photosLoaded=false`). 판정 없이 저장을 열면 ① 경로가 다시 열리기 때문이다.
- ⑤ `ReadingViewModel.kt` import 중복 제거.

## 옛 코드 red와 변이(실측)

명령: `:app:testDebugUnitTest --tests …`. 옛 코드는 main 소스 파일 `OcrDiaryScreen.kt`·`ReadingRepository.kt`를 `fc5e79d` 내용으로 되돌려 실행하고 복구했다.

옛 코드 실행 **8건 중 7건 실패**, 499초:

| 테스트 | 결과 |
|---|---|
| `addedPhoto_…sourceUnreadable…` | 실패(행 불일치) |
| `replacedPhoto_…sourceUnreadable…` | 실패(`but was:<[]>`) |
| `shownPhoto_…sourceUnreadable…` | 실패(행 불일치) |
| `addedPhoto_…sourceReadable…` | 실패(행 불일치) |
| `shownPhoto_…sourceReadable…` | 실패(행 불일치) |
| `checkQueryFailure…` | 실패(`SQLiteException: no such table`) |
| `commitThenFileDeleteFails…` | 실패(onFailure 호출) |
| `addedPhoto_processDiesBeforeCommit…` | **통과(기대대로, 반대 케이스)** |

`editPhotoLoadFailure…`는 그 뒤 추가해 `loadPhotos` 수리 전에 따로 실행했다: 1건 실패(`SQLiteException`), 333초.

변이(수리 코드에서 해당 줄만 바꿈):

| 변이 | 테스트 | 결과 |
|---|---|---|
| effect 키에서 `currentScreen` 제거 | `returningToForm…` | 실패(테스트 보정 뒤, 아래 참고) |
| 실패 문구 분기를 항상 «그대로 있다»로 | `saveFailure_afterLeaving…` | 실패 |
| `!alreadySaved`에서 표식 안 지움 | `saveFailure_onForm…` | 실패 |
| 저장 catch 제거 | `commitFailure…` | 실패 |
| 판정의 위치 확인 2곳 제거 | `rotationWhileSaving…` | **통과 → «현 동작 고정 테스트»로 명시** |

## 검증(묶음별, head `2af984c`)

공통 `:app:verifyRoborazziDebug --tests … --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`, 직렬, 각 묶음 전 가용 RAM ≥3GB 확인(4.9~6.6GB).

| 묶음 | 클래스 | exit | 건수 | 실패 | 소요초 |
|---|---|---|---|---|---|
| N1 | EditPhotoRestore·DiaryFormSubmitScreen | 0 | 11 | 0 | 452 |
| N2 | DiaryDraftRestore·NavigationRestore·OcrDiaryEditSave | 0 | 17 | 0 | 168 |
| A1 | CameraProcessDeath·AddBookPageCount | 0 | 18 | 0 | 189 |
| A2 | OcrDiaryPhotoScreen·BookSearchField·KnowledgeDrawerGraph | 0 | 17 | 0 | 185 |
| A3 | 스크린샷 6 | 0 | 21 | 0 | 156 |
| B1 | 단위 10 | 0 | 74 | 0 | 138 |
| B2 | 단위 10 | 0 | 62(건너뜀 1) | 0 | 132 |
| B3c | 단위 9 | 0 | 81 | 0 | 99 |
| PhotoSave 단독 | 1 | 0 | 15 | 0 | 93 |

- 합 **316건 실패 0 건너뜀 1**, 소스의 `@Test` 수 316과 같다.
- 묶음 단위 통과다. **한 프로세스 전체 통과는 확인하지 않았다.** 테스트 순서 의존은 그대로 남아 있다(B3c와 PhotoSave 분리).
- 이번 변경에 포함된 앞선 head(`3a1b456`)의 일부 verify 2회는 `loadPhotos` 수리로 head가 바뀌어 근거에서 뺐다.
- `git diff --stat origin/main -- app/schemas` 0줄 · `git diff origin/main --stat | grep -ciE 'migration|schema'` 0건.

## 발견

- **`feat/book-search-kakao`:** `git branch -d`가 «not fully merged»로 거부했다(#43이 스쿼시 머지라서). 내용은 `4cef4dc`와 차이 0으로 main과 같다. 발주서의 `-d`를 넘는 강제 삭제라 LC에 물었고, 허용을 받아 `-D`로 지웠다. `git worktree list`는 메인 1개뿐이다.
- **내 테스트가 처음엔 틀렸다(3건, 앱 결함 아님):**
  - `returningToForm`이 effect 키 제거 변이에서 통과했다. 돌아오기 전에 프레임이 돌지 않아 «건너뛴 판정»이라는 전제가 안 만들어졌다. 변이가 잡아 냈고 프레임 진행을 넣어 고쳤다.
  - 파일 단언이 클래스 첫 테스트 이후 실패했다. 기본 ViewModel 팩토리가 정적이라 ViewModel의 Application이 테스트의 것과 달라 다른 폴더를 본 것으로 추정한다. ViewModel 기준 폴더로 바꾸자 통과했다(원인은 이 결과와 일치한다는 데까지 확인).
  - `pump`가 Compose 프레임을 돌리지 않아 위치 변화 효과가 실행되지 않았다.
- **새 토스트 결함을 테스트가 잡았다:** `loadPhotos` 예외 뒤 코루틴이 메인 스레드가 아니어서 토스트가 터졌다(`2af984c`에서 수정). 실기기에서 같은 스레드에 놓일지는 확인하지 않았다.
- `ReadingRepository.deleteWithPhotos`의 커밋 뒤 삭제도 같은 모양이다. 예외 시 처리되지 않을 수 있으나 실제 저장소는 거의 던지지 않는다. 범위 밖이라 그대로 뒀다.
- 실제 카메라·실제 프로세스 사망·갤러리 URI 권한 소멸은 재현하지 않았다. 전부 Robolectric 번들 왕복이고 «원본 읽기 실패»는 원본 파일 삭제로 만들었다. 실기기 판정은 머지 뒤 APK 재테스트다.

## critic

- 1차 `3a1b456`(①~⑤ + #44 전체, high): 7/10 PASS, 심각 0. 실행 증거 없음 조건부. 결함 2(`loadPhotos` 예외)는 `6a853a0`·`2af984c`에서 처리.
- 재확인 `2af984c`: **8/10 PASS**, 심각 0, 이전 중요 결함 2건 닫힘. 후속으로 넘기는 판단도 합당하다고 봤다. 새 경미 2건은 후속으로 넘겼다: 읽기 실패 뒤에도 저장 버튼 토스트가 «불러오는 중»으로 나오는 문구 · 화면 사진 주석이 저장 실패·10장 초과 때는 정확하지 않음.
- critic은 Read/Grep 판독만 했다. 실행 수치는 내가 보고한 값이고 재실행하지 않았다. 신고 증상의 실제 해결은 증명이 아니라고 명시했다.

## 후속(이번 PR 밖)

- 저장 중 사진 입력 잠금(위 «알려진 한계», critic 결함 3). 시나리오: 저장을 누른 뒤 촬영하거나 사진 칸을 고침 → 백그라운드 커밋 → 프로세스 사망 → 복원 때 그 입력이 말없이 사라진다. 커밋된 사진은 잃지 않는다.
- `loadPhotos` 실패 상태를 저장 토스트에서 구분(지금은 «불러오는 중»으로 나온다) · 화면 사진 주석 보정.
- 판정 실패·파일 삭제 실패의 원인 로그. USB 불가로 사용자 로그를 받을 수 없어 지금 이득이 없다.
- 테스트 공백(첫 항목 권장 = 편집 저장 중 회전. 그 경로는 뮤텍스 순서만 지킨다): 편집 저장 중 회전 · 이중 뒤로가기 회귀 · 커밋 전 사망의 Replace·화면 사진 초안 · 카메라 임시 파일 원본 복원 · MainActivity로 프로세스 복원 + 판정 실패.
- 편집 저장 뒤 전환 중 재탭(기존 동작, 유실 없음) · `deleteWithPhotos` 삭제 예외.

## LC 재게이트 2차(head `da5995d`)

**PASS-관찰**(차단 0, 관찰 10). 후속 TASK 1번부터 다룰 관찰:

1. `loadPhotos` 실패 상태에서 저장을 누르면 «불러오는 중» 문구가 틀리다(그 문구를 고정한 테스트도 함께 고친다).
2. 커밋되지 않은 입력이 재동기화 때 말없이 사라지는 3경로: 저장 뒤 수정 · 10장 초과 화면 사진 · 일부 슬롯 저장 실패 → «저장 중 사진 입력 잠금» 후속.
3. 재동기화를 사용자에게 표시하지 않는다.
4. `deleteWithPhotos`의 커밋 뒤 삭제에 예외 처리가 없다(같은 모양).
5. 반대 케이스 테스트가 New 1건뿐이다. Replace·화면 사진·편집 저장 중 회전 테스트가 없다.
6. 판정 쿼리 실패 테스트가 중복 경로를 단언하지 않는다.
7. CI 0건, 묶음 단위 통과(단일 프로세스 미확인).

판정 쿼리 실패 시 저장 허용은 LC가 관찰로 확정했다. 위 7건은 LC가 후속으로 지정한 항목이고, 관찰 10건 중 나머지는 전달받지 않았다.

## LC·사용자에게 필요한 것

- LC: 재게이트. 판정 쿼리 실패 시 표식 해제·중복 알림·저장 허용 판단은 LC 수용(11-06).
- 사용자: 게이트 PASS 뒤 «머지». 머지 뒤 APK 재테스트(촬영 → 확인 → 자르기 화면 · 쪽수 274/384 · 편집에서 사진 교체 저장).
