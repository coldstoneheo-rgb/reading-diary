# 비공개 테스트 → 2027-01-01 공개: 사실 · 일정 · 콘솔 답안지 (2026-11-25)

- 앞 문서: [INTERNAL-TEST-2026-11.md](INTERNAL-TEST-2026-11.md)(등록정보 문안 · 데이터 흐름 N1~N8의 코드 근거). 이 문서는 **콘솔에 옮겨 적을 답**과 날짜만 둔다.
- 콘솔 쓰기 · 키스토어 · 테스터 연락은 사용자 손이다.

## 1. Play 요건 사실표

출처 = Play Console 도움말 원문(`support.google.com/googleplay/android-developer/answer/<번호>?hl=en`, 조회 2026-11-25 13:30~14:20 KST).

| 물음 | 원문 | 출처 |
|---|---|---|
| **이 요건이 우리 계정에 적용되나** | «personal accounts created after November 13, 2023» · «do not apply to developers with personal accounts created before November 13, 2023» → **적용된다고 보고 진행한다**(같은 계정의 다른 앱이 2026-09에 이 절차를 거침 — LC 확인 2026-11-25 · 계정 만든 날은 미대조) | 14151465 · 9859152 |
| 프로덕션 접근 요건 | «At least 12 testers must be opted in to your closed test when you apply for production access, and they must have been opted in continuously for the preceding 14 days.» | 14151465 |
| 나갔다 들어온 사람 | «Testers who opt in, test for fewer than 14 days, and then opt out do not count … the 14 days must be consecutive» | 14151465 |
| 수만 채우면 되나 | «Reasons for required continued testing include having fewer than 12 opted-in testers or insufficient tester engagement during the testing period.» | 14151465 |
| 비공개 테스트 선행 조건 | «You can start a closed test after completing your app setup.» | 14151465 |
| 내부 테스트를 건너뛰어도 되나 | «Internal testing is optional, but recommended as a starting point.» → 된다 | 14151465 |
| 내부 테스트 참여자 | «A user who opts into your app's internal test is no longer eligible to receive an open or closed test.» | 9845334 |
| 접근 신청 검토 | «Review usually takes seven days or less, but can occasionally take longer.» | 14151465 |
| 비공개 트랙 첫 검토 기간 | 비공개 트랙만의 말은 **없음**. 일반: «Processing can take a few hours or up to seven days (or longer in exceptional cases)» | 9859654 |
| 프로덕션 첫 검토 기간 | 프로덕션만의 말은 **없음**. «For certain developer accounts … review times of up to seven days or longer in exceptional cases.» | 9859751 |
| 구글 그룹을 쓰면 | «For closed tests using a Google Group, users must join the group before opting into your test.» · 주소 형식 «yourgroupname@googlegroups.com» | 9845334 |
| 옵트인 링크가 생기는 때 | «The opt-in link displays only when an app status is 'Published'.» → 첫 검토가 끝나야 12명을 들일 수 있다 | 9845334 |
| 링크가 퍼지는 시간 | «the test link can take several hours to become available to testers» | 9845334 |
| 새 빌드를 올리면 14일을 다시 세나 | **말이 없음**(세는 대상은 옵트인). «Updating your app in closed testing before releasing to production helps minimize low ratings» | 14151465 |
| 검토 중 변경을 더 보내면 | «your app may be pushed to the back of the app review queue» | 9859654 |
| 공개 날짜 고정 | 관리형 게시: «push an app update live at a specific time» | 9859654 |
| 관리형 게시의 제약 | «Your app must already be available to use Managed publishing. You can't use it when publishing an app for the first time.» · «we strongly recommend publishing your app to a closed testing track first» → 첫 비공개 제출 때는 쓸 수 없다. 비공개 게시 뒤 프로덕션에서 되는지는 콘솔에서 확인(확정 문장 없음) | 9859654 |
| 여유 권고 | «include a buffer period of at least a week between submitting your app and going live» | 9859654 |
| 처리방침 | «You must link to a privacy policy on your store listing **and within your app**» · 공개 URL · 유효한 SSL · 편집 불가 웹페이지(PDF 아님) · 지역 제한 없음 | 9859455 |
| 데이터 보안 폼 | 비공개 트랙도 필수(내부 트랙만 면제) | 10787469 |
| 연말 검토 지연 | **말이 없음**(조회한 문서 안) | — |

## 2. 역산 일정표(검토마다 7일 가정 — 문서는 «더 걸릴 수 있다»고 하므로 최악이 아니다)

| 늦어도 | 단계 | 누가 | 근거 |
|---|---|---|---|
| **11-26 18:00** | 콘솔 답안지 · 그림 · 처리방침 준비 끝 | 워커 | LC 2026-11-25 |
| **11-27** | 앱 설정 전부 + 첫 비공개 빌드를 «검토로 보내기» | 사용자 | 12-04에서 7일 앞 |
| 12-04 | 12명 이상 옵트인 끝(15~18명 모집 권고) | 사용자 | 12-18에서 14일 앞 |
| 12-18 | 프로덕션 접근 신청(신청서에 테스트 요약 · 고친 것을 적는다) | 사용자(초안 워커) | 검토 ≤ 7일 |
| 12-25 | 접근 승인 → 프로덕션 출시를 검토로 보냄(관리형 게시 켬) | 사용자 | 검토 ≤ 7일 |
| **01-01** | «게시» | 사용자 | |

- LC 어림과 다른 곳: 비공개 트랙 **첫 검토**가 어림에 없었다. 11-30에 보내고 검토가 7일 걸리면 옵트인 12-07 → 신청 12-21 → 승인 12-28 → 검토마다 7일이면 공개 01-04.
- 여유가 0인 표다(반려 후 재제출 · 추가 테스트 요구 · 링크가 퍼지는 몇 시간은 0으로 잡았다). 검토가 실제로 1~3일이면 그만큼 여유가 된다(보장은 없다).
- 관리형 게시가 프로덕션 첫 출시에서 안 되면: 검토를 통과한 날이 곧 공개일이다(그때는 12-25보다 늦게 보내 날짜를 맞추지 않는다 — 일찍 공개되는 편이 낫다).
- 01-01을 놓치면: 접근 승인 뒤 바로 프로덕션 검토를 보내고 통과하는 날 공개한다(그 사이 비공개 테스트는 계속). 날짜를 앞당길 수 있는 손잡이는 «첫 제출을 하루라도 빨리»뿐이다.
- 가장 먼저 밀리는 단계 = 앱 설정 · 첫 제출. 그때 버릴 것 = 첫 빌드의 기능(칩 화면 · 검출기 보정은 테스트 중 올려도 된다). 14일은 줄일 수 없다.
- 첫 검토가 도는 동안, 그리고 12-25 ~ 01-01 프로덕션 검토가 도는 동안 새 빌드나 등록정보 변경을 보내지 않는다(줄 뒤로 밀릴 수 있다).

## 3. 1.0 범위표

| 항목 | 상태 | 언제 |
|---|---|---|
| 책 등록 · 기록 · 사진 보관 · 기억 서랍 · 문구 회상 · 백업/복원 · 마크다운 내보내기 | main | 지금 |
| 기록이 사라지거나 섞이는 결함 수리(책장 삭제 확인 · 책 섞임 · 기본 책장 두 벌 · 뒤로 확인 · 연타 · 완독일) | PR `fix/first-run-data-safety` | **비공개 테스트 전** |
| 앱 아이콘 · 가짜 «이미지 저장» 제거 · 글자 인식 모델 미리 받기 | PR `fix/store-readiness` | **비공개 테스트 전** |
| 앱 안 처리방침 링크(누르는 링크는 `fix/store-readiness` · 주소 `PRIVACY_POLICY_URL`은 게시 뒤 1줄 PR) | 주소 대기 | **비공개 테스트 전** |
| 밑줄 검출기 두 단계 문턱(놓침 81 → 66) | PR `fix/underline-faint-hysteresis` | 전(되면) · 아니면 테스트 중 |
| #67 칩 화면 | draft | 테스트 중(사용자 눈 확인 뒤 · 되면 전) |
| 칩 후속(모든 줄 펼치기 · 바꾸기 되돌리기 · 줄바꿈) | 없음 | 테스트 중 |
| 띄어쓰기 · 줄 끝에서 끊긴 낱말 검색 · 쪽수 «모름» 허용 · 키보드 가림 · 상태바 색 · 테마 저장 · 재인식이 글을 덮음 · 진도/별점 동시 갱신 경쟁 | 점검에서 나옴 | 테스트 중 |
| 글 없는 사진만 기록 · 코드 줄이기(minify) · 이미 두 벌인 기본 책장 정리 | 점검에서 나옴 | 1.0 뒤 |

**사용자가 고를 것(3) + 필수 확인(1):**
1. **형광펜** — 제품 목적지 문장에는 있지만 앱에 연결돼 있지 않고 기준 사진도 0장이다. 권고: 1.0에서는 «밑줄»만 말하고 형광펜은 1.0 뒤.
2. **첫 빌드에 #67을 넣을까** — 권고: 11-26에 눈 확인이 되면 넣고, 아니면 테스트 중 2번째 빌드로.
3. **타겟 연령** — 답안지는 «18세 이상만». 청소년 독자도 받으려면 13~15 · 16~17도 고르는데, 9867159는 이 나이도 나라에 따라 아동으로 볼 수 있다고 한다. 권고: 첫 제출은 18세 이상, 공개 뒤 넓히기.
- **필수(ADR-005 §4 «비공개 테스트로 넓힐 때 다시 결정»): 카카오 REST 키를 앱에 넣은 채 12~18명에게 줄지.** 확인 3건 = ① 약관이 앱 안 내장 키를 허용하는가 ② 이 키가 책 검색 말고 다른 API에 쓰이는가 ③ 한도 초과 응답 코드. 권고: 릴리스 전용 키를 새로 만들어 쓰고(11-11 REPORT «키»), 키가 막혀도 Google Books로 넘어가는 지금 동작을 유지. 확인 없이 가려면 사용자가 «그대로»라고 정한다.

## 4. 콘솔 답안지(그대로 옮겨 적기)

근거 열의 N번호 = INTERNAL-TEST §5-1 표(경로 설명은 유효, 줄 번호는 2026-11-02 기준이라 낡았다). N9 = 글로 공유(`OcrDiaryScreen.kt` `diaryShareText`).

### 4-1. 앱 만들기 · 기본 등록정보
| 칸 | 답 | 근거 |
|---|---|---|
| 앱 이름 | 독서 다이어리 | `app/src/main/res/values/strings.xml` `app_name` |
| 패키지 이름 | com.woodpeckerai.readingdiary | `app/build.gradle.kts:14` — 첫 업로드 뒤 못 바꾼다 |
| 기본 언어 · 종류 · 가격 | 한국어(ko-KR) · 앱 · 무료 | INTERNAL-TEST §1 |
| 짧은 설명 | 밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리 | 같은 문서 §2(오너 결정) |
| 전체 설명 | INTERNAL-TEST §2의 코드 블록 전체를 그대로 붙여 넣기(«읽은 책의 밑줄이 …»로 시작) | |
| 카테고리 | 도서/참고자료 | |
| 연락 메일 | **사용자가 정함**(공개된다) | |
| «앱 만들기» 선언 | 개발자 프로그램 정책 · 미국 수출법 확인 + Play 앱 서명 약관 동의 | 9859152 |
| 테스터(비공개 트랙 › 테스터) | «Google 그룹스» 선택 → `(그룹 주소)@googlegroups.com` · 의견 받을 곳 `(주소)` | 9845334 · 그룹 방식은 LC 가정(2026-11-25, 사용자 통보) |
| 첫 출시의 앱 서명 | «Google이 만든 키 사용»(기본) | 9859348 |
| 처리방침 URL | §5의 주소 | |

### 4-2. 앱 콘텐츠
| 항목 | 답 | 근거 |
|---|---|---|
| 로그인 정보(앱 액세스) | 모든 기능을 로그인 없이 쓸 수 있음 | 계정 코드 0. 정밀 분석은 본인 키가 있을 때만 나타나는 선택 기능 |
| 광고 | 아니요 | 광고 SDK 0(`app/build.gradle.kts` dependencies) |
| 콘텐츠 등급 설문 | 이메일 = 연락 메일 · 카테고리 «유틸리티, 생산성, 커뮤니케이션 또는 기타» · 폭력 · 공포 · 성 · 도박 · 욕설 · 약물 · 사용자 간 소통 · 위치 공유 · 디지털 구매 모두 «아니요» | 사용자끼리 주고받는 기능 0 |
| 타겟 연령 | 18세 이상만 선택 · «미성년자 접근 제한»은 켜지 않음 | 9867159. 아동 대상이 아님 · Families 정책 밖 |
| 아동의 관심을 끌 수 있나 | 아니요 | |
| 뉴스 · 정부 · 금융 · 건강 | 아니요 | |
| 광고 ID 사용 | 아니요 | 디버그 빌드의 병합 매니페스트에 `AD_ID` 0건(2026-11-25) — 서명 빌드에서 1회 더 확인 |

### 4-3. 데이터 보안
| 질문 | 답 | 근거 |
|---|---|---|
| 필수 사용자 데이터를 수집 · 공유하나 | 예 | N1 · N2 · N6 · ML Kit |
| 전송 중 암호화 | 예 | N1 · N2 · N6 HTTPS · ML Kit «encrypts the data in transit using HTTPS»(developers.google.com/ml-kit/android-data-disclosure) |
| 계정 생성 | 없음 | |
| 데이터 삭제 요청 방법 | 제공하지 않음(앱 서버 0 · 기기 안 기록은 앱에서 지움) | |

| 데이터 유형 | 수집 | 공유 | 일시 처리 | 필수/선택 | 목적 | 근거 |
|---|---|---|---|---|---|---|
| 앱 활동 › 앱 내 검색 기록 | 예 | 아니요(사용자가 시작한 전송 — 10787469 «specific user-initiated action») | **아니요** | 선택 | 앱 기능 | N1 · N2 · N2-1. 받는 쪽(카카오 · Google)이 저장하지 않는다는 근거가 없다 |
| 사진 및 동영상 › 사진 | 예 | 아니요(사진마다 확인 = 사용자가 시작 + 앱 안 고지 · 동의) | **아니요** | 선택 | 앱 기능 | N6. 보관은 Gemini API 약관을 따른다(처리방침 5항) |
| 앱 정보 및 성능 › 진단 · 기타 성능 데이터 | 예 | 아니요(ML Kit 문서 «does not transfer this data to third-parties» · Google을 SDK 제공자로 봄) | 아니요 | 필수 | 분석 | ML Kit SDK: 기기 정보 · 앱 버전 · 지연 · 오류 코드 |
| 기기 또는 기타 ID | 예 | 아니요 | 아니요 | 필수 | 분석 | ML Kit «For thin features: Device identifier … Per-installation identifiers» — 이 앱은 thin(`play-services-mlkit-*`) |
| 그 밖 전부 | 아니요 | | | | | N9(글로 공유) · N7-1(마크다운) · N8(zip)은 사용자가 고른 앱 · 위치로 나가는 사용자 시작 전송이라 공유로 적지 않는다. 앱이 선언한 권한 `INTERNET` · `CAMERA`(병합 매니페스트에는 의존성이 더한 `ACCESS_NETWORK_STATE`도 있다) |

N2-1(카카오에서 고른 책의 ISBN이 Google로 감)은 «사용자가 예상하는 전송»이 다른 것보다 약하다 — 보수적으로 가려면 검색 기록의 공유를 «예»로 해도 사실과 어긋나지 않는다(사용자 선택).

INTERNAL-TEST §5-2는 ML Kit 몫을 «[사용자 확인]»으로 비워 두었다 — 2026-11-25에 Google 공개 문서를 읽어 위 두 줄로 채웠다(«기기 ID 아니요»는 틀린 답이 된다). 바코드 스캐너 SDK만의 공개 문서는 따로 찾지 않았다(같은 ML Kit 계열로 보고 한 줄로 묶음).

### 4-4. 그림
| 그림 | 규격(9866151) | 파일(사용자 PC `D:\사진\reading-diary\store-20261125\`) |
|---|---|---|
| 앱 아이콘 | 512×512 32비트 PNG(알파) ≤ 1024KB | `icon-512.png` |
| 그래픽 이미지 | 1024×500 JPEG 또는 24비트 PNG(투명 없음) | `feature-1024x500.png` |
| 휴대전화 스크린샷 | 2~8장 · 9:16 · 한 변 320~3840px · 장당 8MB · PNG는 24비트(투명 없음) | (SHOTS) |

그림은 코드로 그린 도형이다(생성형 그림 모델 0). 문안 · 그림 배치를 AI 도구가 도왔다는 사실은 콘솔이 물을 때만 적는다.

## 5. 처리방침 호스팅

- 실측(2026-11-25 14:11): `https://coldstoneheo-rgb.github.io/` 404 · `…/reading-diary/privacy-policy.html` 404 · 레포 `has_pages=false` · PUBLIC. 공개 레포의 GitHub Pages는 무료다.
- 길: `gh-pages` 가지에 `privacy-policy.html` 한 파일만 둔다(`docs/` 전체를 사이트로 내지 않으려고) → 레포 Settings › Pages › Branch `gh-pages` / root → 주소 `https://coldstoneheo-rgb.github.io/reading-diary/privacy-policy.html`.
- 켜는 것은 공개 게시라 사용자 확인 1회 뒤에 한다. 먼저 필요한 것 = 공개할 문의 메일 주소(원본의 `[연락처 이메일 입력]` 2곳).
- 주소가 200을 돌려준 뒤 `PRIVACY_POLICY_URL` 1줄 PR(누르는 링크 자체는 `fix/store-readiness`의 `PrivacyPolicyBody`).

## 6. 서명 빌드

| 확인 | 결과 |
|---|---|
| `signingConfigs.release` | `KEYSTORE_PATH`(없으면 루트 `my-upload-key.jks`) · `STORE_PASSWORD` · `KEY_PASSWORD` · alias `upload`(`app/build.gradle.kts:26-33`) |
| 키스토어 파일이 있는가 | **확인 못 함** — 에이전트 권한 규칙이 이 파일과 `.env`를 막는다(우회 0). 사용자가 답한다 |
| `bundleRelease` | **돌리지 않음** — 권한 규칙 + «릴리스 서명은 사용자만» |

사용자 절차(**Git Bash 창에서 직접** — 이 대화의 `!`로는 비밀번호를 물을 수 없다. 아래는 워커가 실행해 보지 않았다):

```
cd ~/reading-diary && git checkout main && git pull --ff-only && git log -1 --oneline
```

```
unset KAKAO_REST_API_KEY; IFS= read -rsp "키스토어 비밀번호: " STORE_PASSWORD && echo && export STORE_PASSWORD KEY_PASSWORD="$STORE_PASSWORD" JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle :app:bundleRelease --no-daemon --no-configuration-cache; unset STORE_PASSWORD KEY_PASSWORD
```

- **한 블록씩** 붙여 넣는다(둘을 함께 붙이면 다음 줄이 비밀번호로 들어갈 수 있다). 첫 블록이 실패하거나 마지막 커밋이 기대와 다르면 멈춘다.
- `unset KAKAO_REST_API_KEY`: 셸에 값이 있으면 `.env`보다 먼저 앱에 박힌다.

- 키 비밀번호 = 키스토어 비밀번호(아래 방식으로 만들면 따로 없다). 키를 레포 밖에 두었으면 둘째 블록 앞에 `export KEYSTORE_PATH="/c/…/my-upload-key.jks"`.
- `--no-daemon --no-configuration-cache`는 비밀번호가 상주 프로세스나 `.gradle` 캐시에 남지 않게 하려는 것이다.
- 결과 = `app/build/outputs/bundle/release/app-release.aab`. 비공개 트랙에서 설치한 뒤 첫 확인으로 책 검색 1회(카카오 키가 빈 채로도 경고 없이 빌드된다 — 그때는 Google Books로만 검색된다).

키가 없으면 먼저 **PowerShell**에서(Git Bash 창은 keytool 비밀번호를 화면에 보일 수 있다):

```
cd ~\reading-diary; & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

그 뒤 워커: 병합 매니페스트 확인(광고 ID · 권한) · `versionCode` +1 PR(두 번째 업로드부터).

출시 노트(첫 빌드, 그대로 붙이기):

```
<ko-KR>
첫 비공개 테스트 버전입니다.
- 책을 등록하고, 책 페이지를 찍거나 골라 문장을 남길 수 있어요.
- 남긴 문장은 한 낱말로 다시 찾을 수 있어요.
</ko-KR>
```

## 7. 테스터 안내문(발신은 사용자)

첫 글(그룹 가입 — 검토를 보내기 전):

```
독서 다이어리 앱을 함께 써 보실 분을 찾아요. 안드로이드 휴대폰만 돼요(아이폰은 안 돼요).
참여하실 분은 휴대폰 Play 스토어에 로그인된 구글 계정으로 아래 그룹에 먼저 가입해 주세요. 메일 주소를 저에게 알려 주지 않으셔도 돼요.
   (그룹 가입 링크)
며칠 안에 설치 링크를 다시 올릴게요.
```

둘째 글(참여 링크가 생긴 뒤):

```
[독서 다이어리 비공개 테스트에 와 주셔서 고맙습니다]

참여 3단계
1) 아직이면 그룹에 먼저 가입합니다. (그룹 가입 링크)
2) 가입한 그 구글 계정으로 아래 링크를 열고 «테스터 되기»를 누릅니다.
   (참여 링크)
3) 같은 화면의 «Google Play에서 다운로드»로 설치합니다. (링크가 열리기까지 몇 시간 걸릴 수 있어요)

14일 동안 테스트에서도 그룹에서도 나가지 말아 주세요. 중간에 나가면 14일이 처음부터 다시 시작돼요.

부탁드리는 한 가지
 - 이틀에 한 번, 읽는 책에서 마음에 든 문장 하나를 남겨 주세요. 다른 기능은 편하게 눌러 보시고, 불편한 점을 알려 주세요.

알아 두실 것
 - 글자 인식은 휴대폰 안에서 해요. 처음에는 인식 모델을 받느라 인터넷이 필요할 수 있어요.
 - 사진은 저절로 밖으로 나가지 않아요. 설정에서 본인 Gemini 키를 넣고 «정밀 분석»을 직접 누른 사진만 Google로 가요.
 - 책을 검색하면 검색어가 카카오(안 되면 Google)로 가요.
 - 기록이 걱정되면 설정의 «백업 파일 만들기»로 저장해 두세요.

이상한 점은 (의견 받을 곳)으로 화면 캡처와 함께 보내 주세요. 작은 불편도 좋습니다.
```

예전에 이 앱의 **내부 테스트**에 들어간 사람은 먼저 나가야 비공개 테스트를 받을 수 있다(9845334).

## 8. 사용자가 할 것(날짜순)

| 날짜 | 할 일 | 분 | 먼저 있어야 하는 것 |
|---|---|---|---|
| 11-26 | 답 4개: 키스토어 파일 유무 · 콘솔에 앱이 이미 있는지(버전 코드 1을 올린 적이 있는지) · 공개 문의 메일 · 의견 받을 곳 | 8 | — |
| 11-26 | **필수** 카카오 키 결정(§3) | 5 | — |
| 11-26 | 구글 그룹 1개 만들기(groups.google.com · 누구나 가입 요청 없이 가입 · 구성원 목록은 소유자만 보기) | 5 | — |
| 11-26 | 채팅방에 첫 글(§7) — 15~18명이 그룹에 가입하게 | 5 | 그룹 |
| 11-26 | 키가 없으면 만들기 + 레포 밖 두 곳에 사본 | 10 | §6 명령 |
| 11-26 | 처리방침 게시 확인 1회(GitHub Pages) | 3 | 문의 메일 |
| 11-26 | #67 칩 화면 눈 확인 | 10 | APK |
| 11-26 | 콘솔 «앱 만들기» | 10 | §4-1 |
| 11-26 | 카카오 릴리스 전용 키 만들기(위 결정이 «새 키»일 때) | 5 | 11-11 REPORT «키» |
| 11-27 | 앱 설정(등록정보 · 그림 · 앱 콘텐츠 · 데이터 보안) | 40 | §4 · §5 주소 |
| 11-27 | `bundleRelease` | 15 | 수리 PR 머지 · 앱 안 링크 PR 머지 |
| 11-27 | 비공개 트랙: AAB · 출시 노트(§6) · 테스터에 그룹 주소 · 국가(한국) · 의견 받을 곳 · «검토로 보내기» | 15 | 위 전부 |
| 검토 통과 뒤 | 참여 링크를 둘째 글(§7)에 붙여 채팅방에 올리기 | 5 | 상태 «게시됨» |
| 12-04 | 옵트인 12명 이상인지 확인 | 2 | |
| 12-18 | 프로덕션 접근 신청 | 20 | 워커의 신청서 초안 |
| 12-25 | 프로덕션 출시 → 검토(관리형 게시 켬) | 15 | 접근 승인 |
| 01-01 | 게시 | 2 | 검토 통과 |

기기의 지금 디버그 앱은 서명이 달라 Play 앱을 덮어 설치할 수 없다 → 먼저 «백업 파일 만들기», Play 앱 설치 뒤 복원(11-11 REPORT «첫 AAB» 0번).
