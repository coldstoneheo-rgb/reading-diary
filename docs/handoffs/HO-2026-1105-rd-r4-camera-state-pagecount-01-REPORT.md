# HO-2026-1105-rd-r4-camera-state-pagecount-01 — REPORT

- 발주: LC «R4: 사진 촬영 뒤 초기 화면 튕김 수리 + 검색 등록 쪽수 250 고정 제거». 발주서가 ①②를 1 PR로 지정했다.
- 창: 로컬, 메인 체크아웃 브랜치 `fix/camera-state-pagecount`, base `4cef4dc`
- 앱 코드 head: `4e40a36`(APK 기준). 뒤 커밋은 테스트·문서만 바꾼다.

## 체크리스트(완료 조건 번호)

- [ ] 1. ① 재현 + 수리 + 테스트(옛 코드 실패 확인). **실기기 재실행은 미완**: 연결된 기기·AVD가 없어 Robolectric으로만 재현했다.
- [x] 2. ② 250 제거 + Google 보조 조회 + 빈 값 처리 + 파서 테스트 + «쪽 분량» 삭제.
- [x] 3. 묶음 분할 통과(아래 표 그대로) · 한 프로세스 전체 통과는 확인하지 않음 · B3 순서에서 PhotoSave 9건 실패 재현, 단독 통과.
- [x] 4. 디버그 APK `D:\사진\reading-diary\reading-diary-debug-20261105-2309-4e40a36.apk`(21,114,269바이트, 서명 `aba8e5e3…`) + PNG `camera_restored_crop_step.png`·`book_search_results.png`.
- [ ] 5. critic high 결과는 아래 «critic» 절에 쓴다.

## ① 재현과 원인

재현 수단: 실제 `MainActivity`를 띄워 기록 화면에서 «사진 촬영»을 누르고, 저장 번들을 Parcel로 왕복시킨 뒤 새 Activity(새 ViewModelStore)를 만들어 그 사이에 카메라 결과를 전달한다. 실제 카메라·실제 프로세스 사망은 아니다.

수리 전 코드 관찰(`CameraProcessDeathTest`, 7건 중 5건 실패):

| 카메라 결과 | 복원 뒤 화면 | 재진입 토스트 | 자르기 단계 |
|---|---|---|---|
| RESULT_CANCELED | Dashboard | «사진 촬영이 취소되었습니다.» | 없음 |
| RESULT_OK | Dashboard | 없음 | 없음 |

함께 관찰: Activity가 재생성용으로 부서질 때 촬영 임시 파일이 지워졌다.

**원인 1문장(가설 포함):** 화면 위치가 ViewModel 메모리에만 있어 Activity·ViewModel이 재생성되면 초기 화면으로 돌아가고, 기록 화면이 다시 구성될 때에야 지연된 카메라 결과가 전달된다(Robolectric 실측). 실기기에서 프로세스가 죽었는지와 카메라가 RESULT_OK가 아닌 값을 돌려줬는지는 **가설**이다. 근거는 사용자가 본 «취소» 토스트가 RESULT_CANCELED 쪽 관찰과만 맞는다는 것뿐이다.

## 한 일

**① 촬영 복귀**
- 현재 화면과 백스택을 Activity 저장 상태에 문자열로 보존한다(`ScreenCodec`, `navigationSnapshot`, `restoreNavigation`). 새 ViewModel에 한 번만 적용하므로 회전 때는 현재 위치를 유지한다. 문구 회상의 필터는 보존하지 않고 검색어만 남긴다.
- 폼 저장 키를 `ocr_form/<bookId>/<diaryId>`로 바꿨다. 옛 테스트 `newProcess_doesNotRestoreOldForm`은 의도가 뒤집혀 `newProcess_keepsSameForm_butNotOtherBooksForm`으로 교체했다.
- 시스템이 재생성하려고 Activity를 부술 때는 임시 파일을 지우지 않는다. 화면을 떠날 때와 새 시작 때 지운다.
- `success=false`여도 임시 파일 크기 > 0이고 수정 시각 ≥ 촬영 시작이면 촬영으로 처리한다. 두 카메라 경로에 같게 적용했다. **초 단위 내림 1건을 더했다**(파일 시각이 초로 잘리는 저장소 대비, LC 수용). 그래서 같은 초 안의 옛 파일은 촬영으로 본다.
- 복원 직후 책 목록을 받기 전에는 «해당 책을 일치시킬 수 없습니다» 대신 진행 표시를 보인다. 정말 없는 책이면 안내와 «돌아가기»를 보인다.

**② 쪽수**
- `SearchResultBook.pageCount`를 `Int?`로, `isbn`을 추가. 파서의 기본값 250을 없앴다. Google의 0쪽도 없음으로 본다.
- 결과를 고를 때 쪽수가 없고 ISBN이 있으면 **그 1권의 ISBN만** Google Books로 조회한다. 없거나 실패하면 빈 칸 + «쪽수를 가져오지 못했습니다. 직접 입력해 주세요.» 저장은 기존 규칙대로 막는다. 늦은 응답은 사용자 입력을 덮어쓰지 않는다.
- 결과 목록의 «쪽 분량» 줄을 지웠다.

**공개 문구(LC가 «처리방침 변경 0»을 이 건에 한해 해제, 9항 «새 경로는 먼저 반영» 우선)**
- 처리방침 3·7항, 앱 요약 3번, 앱 약관 3조, INTERNAL-TEST N2-1·폼·유형표·안내문.

## 검증(명령 + 결과)

공통 옵션 `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. 표의 묶음은 모두 head `4e40a36`의 `:app:verifyRoborazziDebug --tests …`이고, «통과»는 묶음 단위로만 쓴다.

| 묶음 | 클래스 | exit | 실행 | 실패 | 소요초 |
|---|---|---|---|---|---|
| A | 화면 12 | 초과 종료 → 분할 | — | — | 540 |
| A1 | CameraProcessDeath·AddBookPageCount | 0 | 18 | 0 | 418 |
| A2 | OcrDiaryPhotoScreen·OcrDiaryEditSave·BookSearchField·KnowledgeDrawerGraph | 0 | 18 | 0 | 213 |
| A3 | 스크린샷 6 | 0 | 21 | 0 | 169 |
| B | 단위 31 | 초과 종료 → 분할 | — | — | 540 |
| B1 | 11 | 0 | 86(건너뜀 1) | 0 | 173 |
| B2 | 10 | 0 | 56 | 0 | 153 |
| B3 | 10 | 초과 종료, PhotoSave 9건 실패 표시 | — | — | 540 |
| B3a | B3 − ReadingViewModelOcrTest | 초과 종료, 같은 실패 | — | — | 540 |
| PhotoSave 단독 | 1 | 0 | 14 | 0 | 139 |
| B3c | B3 − PhotoSave | 0 | 81 | 0 | 136 |

- 묶음별 실행 수의 합: A1 18 + A2 18 + A3 21 + B1 86 + B2 56 + PhotoSave 14 + B3c 81 = **294**.
- **한 프로세스 전체 통과는 확인하지 않았다.**
- `:app:assembleDebug` exit 0, 108초. `apksigner verify --print-certs` SHA-256 `aba8e5e3…`.
- `recordRoborazziDebug`: `86a8583` 직전 작업본에서 exit 0.
- critic 2차 뒤 테스트·문서만 고친 커밋: `compileDebugUnitTestKotlin` exit 0, `verifyRoborazziDebug --tests *CameraProcessDeathTest --tests *NavigationRestoreTest` exit 0, 19건 실패 0, 172초. 이 2개 클래스 외에는 재실행하지 않았다. 테스트 수는 이제 295.
- 그 밖의 실행: 수리 전 `baeed00`의 전체 verify 284건 실패 0은 지금 head의 근거가 아니어서 쓰지 않는다. `86a8583` 전체 verify는 상한으로 끊겼고 그 전에 ReadingViewModelOcrTest 2건이 `Dispatchers.setMain` 동시 사용 오류로 실패했다. `4e40a36`에서 Activity 정리를 넣은 뒤 같은 클래스는 `testDebugUnitTest`(30건)와 B3c에서 실패 0이었다. 원인이 정말 그 정리였는지는 재현으로 확인하지 않았다.

## 발견

- **규약 위반 2건:** 그래들 실행이 668초·679초까지 가며 도구가 백그라운드로 옮겼다(포그라운드 상한 10분). `taskkill /T`로 끊는 스크립트로 전환한 뒤 재발 없음.
- **Windows에서 `timeout -k`는 그래들을 끊지 못한다**(실측: 상한 뒤에도 그래들이 끝까지 돌았다). 프로세스 트리를 끊어야 한다.
- **테스트 순서 의존:** B3 순서에서 `ReadingViewModelPhotoSaveTest`가 9건 실패 후 멈춘다. 단독은 14건 통과. `ReadingViewModelOcrTest`의 `setMain`이 원인이라는 첫 가설은 B3a로 틀렸다. 현재 가설은 `photoIoMutex`(ReadingViewModel.kt:831)를 쥔 정리 코루틴이 메인 루퍼 소실로 멈추는 것이고 미검증이다. base 재현 미확인. 새 테스트 `NavigationRestoreTest` 등이 같은 패턴이라 악화시켰을 가능성이 남는다.
- **기준 이미지 1건을 잘못 기록했다가 고쳤다:** `camera_restored_crop_step.png`의 첫 기록은 그래픽 모드를 지정하지 않아 semantics 노드 덤프였고, 그 상태로 verify도 통과했다. critic이 지적해 실제 렌더링으로 다시 기록했다. 사진 칸은 테스트용 회색이다.
- 테스트 환경에서 FileProvider가 Windows 경로의 캐시 폴더를 못 찾아 테스트만 shadow로 URI를 만든다.
- 이미 250으로 저장된 책은 자동으로 고치지 않는다(스키마 변경 0). 실제 쪽 번호가 250을 넘으면 기록 저장이 막히므로 사용자가 책 정보에서 고쳐야 한다.
- 카카오 실호출: 사용자 실기기에서 검색 성공(11-05 18:2x 사용자 보고). 다만 쪽수 보조 조회의 실제 Google 응답은 아직 호출한 적이 없다.

## critic

- 1차 `baeed00`: 6/10 NEEDS_REVISION. 필수 게이트 미실행, 복원 중 오류 문구 스침, 노드 덤프 기준 이미지, photoCamera·임시 파일·쪽수 경합 테스트 공백, 문구 2건을 `86a8583`에서 보정.
- 2차 `4e40a36`: 7/10 PASS(심각 0, 임계에 걸림). critic은 이 PASS가 코드 평가이고 신고 ① 해결의 증명이 아니라고 명시했고, «전체 실행 미확인»을 LC가 수용하길 권했다.
  - 이번 PR에서 보정: 복원 직후 오류 문구 테스트의 공허한 앞부분(받기 전 상태 단언으로 교체) · 가설을 단정한 테스트 주석 · INTERNAL-TEST의 낡은 줄 번호.
  - critic 추론: 뮤텍스 가설을 뒷받침하는 수치(PhotoSave 14건 중 뮤텍스 사용 10건, 9건 대기 실패, 1건 `runBlocking` 무한 대기). 증명은 아니다. 새 `NavigationRestoreTest`가 악화 요인일 공산이 크다는 판단이다.

## LC·사용자에게 필요한 것

- 사용자: 위 APK 설치 → «기록하기 → 사진 촬영 → 확인 → 자르기 화면» 확인 + 책 검색 등록 시 쪽수 274/384 확인. 이미 250으로 등록된 두 권은 직접 고친다.
- 사용자: USB 디버깅이 가능하면 재현 뒤 `adb logcat -d -b main,system,crash` 일부를 확인해 프로세스 사망 여부를 가린다. 로그에 앱·카메라 활동 정보가 들어가니 내용을 보고 전달한다.
- 사용자: 원격 브랜치 삭제.

## 후속

- 테스트 순서 의존(위 «발견», 다음 RD TASK 1번으로 LC 발주).
- 저장·뒤로 가기 직후 전환 250ms 사이에 앱이 내려가면 옛 초안이 되살아날 가능성(critic 추론, 미재현).
- 등록 폼 복원: 위치는 복원되지만 제목·쪽수 입력은 복원되지 않는다.
- BookDetail로 복원됐는데 책이 없으면 진행 표시가 멈추지 않는다(드문 경로).
- 조회 실패와 «쪽수 없음»을 구분하지 않고 같은 문구를 쓴다.
- 새 기록 저장 직후 홈으로 나간 뒤 프로세스가 죽으면 옛 초안이 복원되어 다시 저장 시 2건이 될 수 있다(critic 추론, 복원 도입으로 생긴 경로, 미재현).
- 테스트 정리: rotation이 재생성 자체를 단언하지 않음 · 쪽수 경합 테스트의 고정 500ms 대기 · `choosingAnotherBook`의 Coil 실제 로더(`.example` 도메인 DNS 시도 가능, 클래스 설명의 «네트워크 0»과 어긋날 수 있음).
