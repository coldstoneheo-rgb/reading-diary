# 비공개 테스트 → 2027-01-01 공개: 사실 · 일정 · 콘솔 답안지 (2026-11-25)

- 앞 문서: [INTERNAL-TEST-2026-11.md](INTERNAL-TEST-2026-11.md)(등록정보 문안 · 데이터 흐름 N1~N8의 코드 근거). 이 문서는 **콘솔에 옮겨 적을 답**과 날짜만 둔다.
- 콘솔 쓰기 · 키스토어 · 테스터 연락은 사용자 손이다.

## 1. Play 요건 사실표

출처 = Play Console 도움말 원문(`support.google.com/googleplay/android-developer/answer/<번호>?hl=en`, 조회 2026-11-25 13:30~14:20 KST).

| 물음 | 원문 | 출처 |
|---|---|---|
| 프로덕션 접근 요건 | «At least 12 testers must be opted in to your closed test when you apply for production access, and they must have been opted in continuously for the preceding 14 days.» | 14151465 |
| 나갔다 들어온 사람 | «Testers who opt in, test for fewer than 14 days, and then opt out do not count … the 14 days must be consecutive» | 14151465 |
| 수만 채우면 되나 | «Reasons for required continued testing include having fewer than 12 opted-in testers or insufficient tester engagement during the testing period.» | 14151465 |
| 비공개 테스트 선행 조건 | «You can start a closed test after completing your app setup.» | 14151465 |
| 내부 테스트를 건너뛰어도 되나 | «Internal testing is optional, but recommended as a starting point.» → 된다 | 14151465 |
| 내부 테스트 참여자 | «A user who opts into your app's internal test is no longer eligible to receive an open or closed test.» | 9845334 |
| 접근 신청 검토 | «Review usually takes seven days or less, but can occasionally take longer.» | 14151465 |
| 비공개 트랙 첫 검토 기간 | 비공개 트랙만의 말은 **없음**. 일반: «Processing can take a few hours or up to seven days (or longer in exceptional cases)» | 9859654 |
| 프로덕션 첫 검토 기간 | 프로덕션만의 말은 **없음**. «For certain developer accounts … review times of up to seven days or longer in exceptional cases.» | 9859751 |
| 옵트인 링크가 생기는 때 | «The opt-in link displays only when an app status is 'Published'.» → 첫 검토가 끝나야 12명을 들일 수 있다 | 9845334 |
| 링크가 퍼지는 시간 | «the test link can take several hours to become available to testers» | 9845334 |
| 새 빌드를 올리면 14일을 다시 세나 | **말이 없음**(세는 대상은 옵트인). «Updating your app in closed testing before releasing to production helps minimize low ratings» | 14151465 |
| 검토 중 변경을 더 보내면 | «your app may be pushed to the back of the app review queue» | 9859654 |
| 공개 날짜 고정 | 관리형 게시: «push an app update live at a specific time» | 9859654 |
| 처리방침 | «You must link to a privacy policy on your store listing **and within your app**» · 공개 URL · 유효한 SSL · 편집 불가 웹페이지(PDF 아님) · 지역 제한 없음 | 9859455 |
| 데이터 보안 폼 | 비공개 트랙도 필수(내부 트랙만 면제) | 10787469 |
| 연말 검토 지연 | **말이 없음**(조회한 문서 안) | — |

## 2. 역산 일정표(최악 = 검토마다 7일)

| 늦어도 | 단계 | 누가 | 근거 |
|---|---|---|---|
| **11-26 18:00** | 콘솔 답안지 · 그림 · 처리방침 준비 끝 | 워커 | LC 2026-11-25 |
| **11-27** | 앱 설정 전부 + 첫 비공개 빌드를 «검토로 보내기» | 사용자 | 12-04에서 7일 앞 |
| 12-04 | 12명 이상 옵트인 끝(15~18명 모집 권고) | 사용자 | 12-18에서 14일 앞 |
| 12-18 | 프로덕션 접근 신청(신청서에 테스트 요약 · 고친 것을 적는다) | 사용자(초안 워커) | 검토 ≤ 7일 |
| 12-25 | 접근 승인 → 프로덕션 출시를 검토로 보냄(관리형 게시 켬) | 사용자 | 검토 ≤ 7일 |
| **01-01** | «게시» | 사용자 | |

- LC 어림과 다른 곳: 비공개 트랙 **첫 검토**가 어림에 없었다. 11-30에 보내고 검토가 7일 걸리면 옵트인 12-07 → 신청 12-21 → 승인 12-28 → 공개 최악 01-04.
- 여유가 0인 표다. 검토가 실제로 1~3일이면 하루하루가 여유가 된다(보장은 없다).
- 가장 먼저 밀리는 단계 = 앱 설정 · 첫 제출. 그때 버릴 것 = 첫 빌드의 기능(칩 화면 · 검출기 보정은 테스트 중 올려도 된다). 14일은 줄일 수 없다.
- 첫 검토가 도는 동안 새 빌드를 보내지 않는다(줄 뒤로 밀릴 수 있다). 승인 뒤에 올린다.

## 3. 1.0 범위표

| 항목 | 상태 | 언제 |
|---|---|---|
| 책 등록 · 기록 · 사진 보관 · 기억 서랍 · 문구 회상 · 백업/복원 · 마크다운 내보내기 | main | 지금 |
| 기록이 사라지거나 섞이는 결함 수리(책장 삭제 확인 · 책 섞임 · 기본 책장 두 벌 · 뒤로 확인 · 연타 · 완독일) | PR `fix/first-run-data-safety` | **비공개 테스트 전** |
| 앱 아이콘 · 가짜 «이미지 저장» 제거 · 글자 인식 모델 미리 받기 | PR `fix/store-readiness` | **비공개 테스트 전** |
| 앱 안 처리방침 링크(`SettingsScreen.kt` `PRIVACY_POLICY_URL`) | 주소가 생긴 뒤 1줄 PR | **비공개 테스트 전** |
| 밑줄 검출기 두 단계 문턱(놓침 81 → 66) | PR `fix/underline-faint-hysteresis` | 전(되면) · 아니면 테스트 중 |
| #67 칩 화면 | draft | 테스트 중(사용자 눈 확인 뒤 · 되면 전) |
| 칩 후속(모든 줄 펼치기 · 바꾸기 되돌리기 · 줄바꿈) | 없음 | 테스트 중 |
| 띄어쓰기 · 줄 끝에서 끊긴 낱말 검색 · 쪽수 «모름» 허용 · 키보드 가림 · 상태바 색 · 테마 저장 · 재인식이 글을 덮음 · 진도/별점 동시 갱신 경쟁 | 점검에서 나옴 | 테스트 중 |
| 글 없는 사진만 기록 · 코드 줄이기(minify) · 이미 두 벌인 기본 책장 정리 | 점검에서 나옴 | 1.0 뒤 |

**사용자가 고를 것(3):**
1. **형광펜** — 제품 목적지 문장에는 있지만 앱에 연결돼 있지 않고 기준 사진도 0장이다. 권고: 1.0에서는 «밑줄»만 말하고 형광펜은 1.0 뒤.
2. **첫 빌드에 #67을 넣을까** — 권고: 11-26에 눈 확인이 되면 넣고, 아니면 테스트 중 2번째 빌드로.
3. **처리방침 주소 자리** — 권고: 이 레포의 GitHub Pages(무료, §5).

## 4. 콘솔 답안지(그대로 옮겨 적기)

근거 열의 N번호 = INTERNAL-TEST §5-1 표(코드 파일:줄).

### 4-1. 앱 만들기 · 기본 등록정보
| 칸 | 답 | 근거 |
|---|---|---|
| 앱 이름 | 독서 다이어리 | `app/src/main/res/values/strings.xml` `app_name` |
| 패키지 이름 | com.woodpeckerai.readingdiary | `app/build.gradle.kts:14` — 첫 업로드 뒤 못 바꾼다 |
| 기본 언어 · 종류 · 가격 | 한국어(ko-KR) · 앱 · 무료 | INTERNAL-TEST §1 |
| 짧은 설명 | 밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리 | 같은 문서 §2(오너 결정) |
| 전체 설명 | 같은 문서 §2의 초안 전문 | |
| 카테고리 | 도서/참고자료 | |
| 연락 메일 | **사용자가 정함**(공개된다) | |
| 처리방침 URL | §5의 주소 | |

### 4-2. 앱 콘텐츠
| 항목 | 답 | 근거 |
|---|---|---|
| 로그인 정보(앱 액세스) | 모든 기능을 로그인 없이 쓸 수 있음 | 계정 코드 0. 정밀 분석은 본인 키가 있을 때만 나타나는 선택 기능 |
| 광고 | 아니요 | 광고 SDK 0(`app/build.gradle.kts` dependencies) |
| 콘텐츠 등급 설문 | 카테고리 «유틸리티, 생산성, 커뮤니케이션 또는 기타» · 폭력 · 공포 · 성 · 도박 · 욕설 · 약물 · 사용자 간 소통 · 위치 공유 · 디지털 구매 모두 «아니요» | 사용자끼리 주고받는 기능 0 |
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
| 앱 활동 › 앱 내 검색 기록 | 예 | 아니요(사용자가 시작한 전송) | 예 | 선택 | 앱 기능 | N1 · N2 · N2-1 |
| 사진 및 동영상 › 사진 | 예 | 아니요(사진마다 확인) | 예 | 선택 | 앱 기능 | N6 |
| 앱 정보 및 성능 › 진단 · 기타 성능 데이터 | 예 | 아니요 | 아니요 | 필수 | 분석 | ML Kit SDK: 기기 정보 · 앱 버전 · 지연 · 오류 코드 |
| 기기 또는 기타 ID | 예 | 아니요 | 아니요 | 필수 | 분석 | ML Kit «For thin features: Device identifier … Per-installation identifiers» — 이 앱은 thin(`play-services-mlkit-*`) |
| 그 밖 전부 | 아니요 | | | | | 앱이 선언한 권한 `INTERNET` · `CAMERA`(병합 매니페스트에는 의존성이 더한 `ACCESS_NETWORK_STATE`도 있다) |

INTERNAL-TEST §5-2는 ML Kit 몫을 «[사용자 확인]»으로 비워 두었다 — 2026-11-25에 Google 공개 문서를 읽어 위 두 줄로 채웠다(«기기 ID 아니요»는 틀린 답이 된다). 바코드 스캐너 SDK만의 공개 문서는 따로 찾지 않았다(같은 ML Kit 계열로 보고 한 줄로 묶음).

### 4-4. 그림
| 그림 | 규격(9866151) | 파일(사용자 PC) |
|---|---|---|
| 앱 아이콘 | 512×512 32비트 PNG ≤ 1024KB | `D:\사진\reading-diary\store-20261125\icon-512.png` |
| 그래픽 이미지 | 1024×500 JPEG 또는 24비트 PNG(투명 없음) | `…\feature-1024x500.png` |
| 휴대전화 스크린샷 | 2~8장 · 9:16 · 한 변 320~3840px | `…\1-dashboard.png` ~ `4-drawer.png`(1080×1920, 지어낸 책 · 실제 앱 화면 렌더) |

그림은 코드로 그린 도형이다(생성형 그림 모델 0). 문안 · 그림 배치를 AI 도구가 도왔다는 사실은 콘솔이 물을 때만 적는다.

## 5. 처리방침 호스팅

- 실측(2026-11-25 14:11): `https://coldstoneheo-rgb.github.io/` 404 · `…/reading-diary/privacy-policy.html` 404 · 레포 `has_pages=false` · PUBLIC. 공개 레포의 GitHub Pages는 무료다.
- 길: `gh-pages` 가지에 `privacy-policy.html` 한 파일만 둔다(`docs/` 전체를 사이트로 내지 않으려고) → 레포 Settings › Pages › Branch `gh-pages` / root → 주소 `https://coldstoneheo-rgb.github.io/reading-diary/privacy-policy.html`.
- 켜는 것은 공개 게시라 사용자 확인 1회 뒤에 한다. 먼저 필요한 것 = 공개할 문의 메일 주소(원본의 `[연락처 이메일 입력]` 2곳).
- 주소가 200을 돌려준 뒤 `PRIVACY_POLICY_URL` 1줄 PR(앱 안 링크).

## 6. 서명 빌드

| 확인 | 결과 |
|---|---|
| `signingConfigs.release` | `KEYSTORE_PATH`(없으면 루트 `my-upload-key.jks`) · `STORE_PASSWORD` · `KEY_PASSWORD` · alias `upload`(`app/build.gradle.kts:26-33`) |
| 키스토어 파일이 있는가 | **확인 못 함** — 에이전트 권한 규칙이 이 파일과 `.env`를 막는다(우회 0). 사용자가 답한다 |
| `bundleRelease` | **돌리지 않음** — 권한 규칙 + «릴리스 서명은 사용자만» |

사용자 명령 1줄(메인 체크아웃, Git Bash — 비밀번호는 직접 입력되고 화면 · 기록에 남지 않는다):

```
read -s -p "키스토어 비밀번호: " STORE_PASSWORD && read -s -p $'\n키 비밀번호: ' KEY_PASSWORD && export STORE_PASSWORD KEY_PASSWORD JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ~/.gradle/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle :app:bundleRelease; unset STORE_PASSWORD KEY_PASSWORD
```

결과 = `app/build/outputs/bundle/release/app-release.aab`. 키가 없으면 먼저(비밀번호는 묻는 대로 입력):

```
"/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

그 뒤 워커: 병합 매니페스트 확인(광고 ID · 권한) · 출시 노트 · `versionCode` +1 PR(두 번째 업로드부터).

## 7. 테스터 안내문(발신은 사용자)

```
[독서 다이어리 비공개 테스트에 와 주셔서 고맙습니다]

참여 3단계
1) 아래 참여 링크를 누르고 «테스터 되기»를 누릅니다. (휴대폰 Play 스토어에 로그인된 구글 계정이어야 해요)
   (참여 링크: 검토가 끝나면 붙여 넣기)
2) 같은 화면의 «Google Play에서 다운로드»로 설치합니다.
3) 14일 동안 테스트를 나가지 말아 주세요. 중간에 나가면 14일이 처음부터 다시 시작돼요.

딱 한 가지만 부탁드려요
 - 이틀에 한 번, 읽는 책에서 마음에 든 문장 하나를 남겨 주세요. (사진을 찍거나 직접 적어도 돼요)

알아 두실 것
 - 글자 인식은 휴대폰 안에서 합니다. 처음 한 번은 인식 모델을 받느라 인터넷이 필요할 수 있어요.
 - 사진은 저절로 밖으로 나가지 않습니다.
 - 테스트 중 기록이 걱정되면 설정의 «백업 파일 만들기»로 저장해 두세요.

이상한 점은 (의견 받을 곳)으로 화면 캡처와 함께 보내 주세요. 작은 불편도 좋습니다.
```

## 8. 사용자가 할 것(날짜순)

| 날짜 | 할 일 | 분 | 먼저 있어야 하는 것 |
|---|---|---|---|
| 11-26 | 답 3개: 키스토어 파일 유무 · 콘솔에 앱이 이미 있는지 · 공개 문의 메일 | 5 | — |
| 11-26 | 키가 없으면 만들기 + 레포 밖 두 곳에 사본 | 10 | §6 명령 |
| 11-26 | 처리방침 게시 확인 1회(GitHub Pages) | 3 | 문의 메일 |
| 11-26 | #67 칩 화면 눈 확인 | 10 | APK |
| 11-26 | 콘솔 «앱 만들기» | 10 | §4-1 |
| 11-26 | 카카오 릴리스 전용 키(권고) | 5 | 11-11 REPORT «키» |
| 11-27 | 앱 설정(등록정보 · 그림 · 앱 콘텐츠 · 데이터 보안) | 40 | §4 · §5 주소 |
| 11-27 | `bundleRelease` | 15 | 수리 PR 머지 · 앱 안 링크 PR 머지 |
| 11-27 | 비공개 트랙: AAB · 출시 노트 · 테스터 명단 · 국가(한국) · 의견 받을 곳 · «검토로 보내기» | 15 | 위 전부 |
| 검토 통과 뒤 | 참여 링크를 채팅방에 올리기 | 5 | §7 |
| 12-04 | 옵트인 12명 이상인지 확인 | 2 | |
| 12-18 | 프로덕션 접근 신청 | 20 | 워커의 신청서 초안 |
| 12-25 | 프로덕션 출시 → 검토(관리형 게시 켬) | 15 | 접근 승인 |
| 01-01 | 게시 | 2 | 검토 통과 |

기기의 지금 디버그 앱은 서명이 달라 Play 앱을 덮어 설치할 수 없다 → 먼저 «백업 파일 만들기», Play 앱 설치 뒤 복원(11-11 REPORT «첫 AAB» 0번).
