# TASK-2026-1111 rd #48 머지 · 정리 · 밑줄 추출 품질 · 키 · 첫 AAB — REPORT

발주서: `life-coordinator/handoffs/TASK-2026-1111-rd-48-merge-first-aab.md` · 창: reading-diary-ee · 시작 main `91986a6`

## 체크리스트(완료 조건)

> 아래 9 · 12-PR · 10 · 14 줄은 작업 중 기록이다. 결과는 «마감 상태»가 우선한다(#50 `e6790eb` · #51 `9ebc60d` 머지, 10은 새 창으로 이월).

- [x] 1. 로컬 main ff-only `91986a6 → c1e987a` (`git merge --ff-only origin/main`), 미커밋 2파일 보존(`git status`: ` M .claude/settings.json` · ` M CLAUDE.md`)
- [x] 2. #48 머지: 원격 head `12a98d19…` 확인 → `gh pr merge 48 --squash --match-head-commit 12a98d19d998…` → main **`3ddee89`** (`gh pr view 48` state MERGED)
- [x] 3. 가지·워크트리 정리 — 아래 «정리 근거»
- [x] 4. 첫 AAB 업로드 손 단계 — 아래 «첫 AAB»
- [x] 5. 미커밋 2파일 처분 안 — 아래
- [x] 6. #47 실기기 확인 2건 — **남아 있다**(아래)
- [x] 7. 카카오 키 — 아래 «키»
- [ ] 9. 자르기 어긋남 재현·수리 — 원인 코드·수치 재현 완료, 수리 `crop-fix` `1f8c00d`→`dc5ede9`(critic 1차 6/10 NEEDS_REVISION: 게이트 미실행·화면 연결 미고정·EXIF 중복 → 반영). 단위 4클래스 1회차(16:56, 여유 3.5GB)는 10분 초과 후 Claude Code 메모리 회수로 결과 0 · 고아 java 정리는 taskkill 분류기 거부 → 사용자 손. 2회차(«가» 17:2x): `compileDebugUnitTestKotlin` 1차 570초 timeout(EXIT 124, 첫 KSP+kotlinc, 여유 1.8GB까지 하락) → 2차 2m29s 성공(재실행 = KSP 2개뿐). «가» 18:0x: 단위 4클래스 32건 실패 0(5m02s) · `verifyRoborazziDebug` 스크린샷 7클래스 33건 실패 0, 골든 변경 0(4m18s). 모두 `--no-daemon --offline --max-workers=1 -Xmx2g`, 시작 전 여유 5.8GB. critic 2차 7/10 NEEDS_REVISION(게이트 범위 누락: `DiaryFormSubmitScreenTest`가 사진 선택 뒤 새 코드를 지남 · 사진 교체 시 이전 비율 순간 표시) → `871cc71`(`value = null` 1줄) + DiaryFormSubmit 5 · OcrDiaryEditSave 1 · PhraseRecallNavigation 2 · CropGeometry 5 = 13건 실패 0(8m52s) → **critic 3차 7/10 PASS(심각 0)**. **PR #50** head `871cc71`, LC 게이트 대기. 후속(PR 본문): 화면 배치 자체를 고정하는 테스트(실제 PNG + `getBoundsInRoot`).
- [ ] 12-PR. 문구 **PR #51** head `2a50af4`(문자열 10개 + 테스터 안내 1단어) — 스크린샷 7클래스 33건 실패 0·골든 변경 0 · 화면 4클래스 24건 · PolicyCopyTest 4건 실패 0 · critic 8/10 PASS. 병합 순서 #50 다음. LC 게이트 대기
- [ ] 10. 수리 뒤 원문 사진 재추출 일치율 — ML Kit은 JVM/Robolectric에서 돌지 않고 adb 기기 0대·에뮬레이터 금지(RAM) → **LC 판정: 수리 PR 머지 뒤 확인용 APK + 사용자 손 3줄로 갈음**(아래 «10. 사용자 확인»)
- [x] 11. 기기 안 밑줄 선택 설계 1쪽 — 아래
- [x] 12. 문구/동작 차이 닫는 안 — 아래
- [x] 13. Gemini 길 표 — 아래
- [ ] 14. REPORT 최종(이 파일) — 9·10 결과 뒤 확정

## 마감 상태(2026-11-11 저녁, 창 reading-diary-ee)

- **머지:** #50 head `871cc71` 확인 → `gh pr merge 50 --squash --match-head-commit 871cc71…` → `e6790eb`. #51 head `2a50af4` 확인 + `git merge-tree` 충돌 0 → `gh pr merge 51 --squash --match-head-commit 2a50af4…` → 원격 main **`9ebc60d`**. LC 게이트 둘 다 PASS-관찰.
- **정리:** 원격 가지 2개 `gh api -X DELETE` → `git ls-remote --heads origin` = main 1개. 로컬 가지 2개 `-D`(스쿼시). 워크트리 2개 제거(reparse 0 · status 0줄 확인 뒤, 긴 경로라 `\\?\` 삭제). `.claude/worktrees/crop-fix` **빈 폴더 1개**가 이 세션 셸이 잡고 있어 남음 — 창을 닫은 뒤 지우면 된다.
- **[BLOCKED] 로컬 main 동기화(멈출 곳 (c)):** 로컬 `3ddee89` · 원격 `9ebc60d`. `git merge --ff-only origin/main` → «Your local changes to … CLAUDE.md would be overwritten» 중단. 원인 = 사용자 미커밋 `CLAUDE.md`(위임 조항 개정)와 #50의 `CLAUDE.md` 구조 목록 +1줄. stash·덮어쓰기 0. `git status`: ` M .claude/settings.json` · ` M CLAUDE.md` · `?? 이 REPORT`. 해소 = 사용자가 2파일을 커밋(완료 조건 5 안)하거나 직접 결정 → 그 뒤 ff-only.
- **10번:** 확인용 APK 빌드는 후속 PR(아래 A) 머지 뒤 새 창에서(LC 지시). 사용자 손 3줄은 아래 «10. 사용자 확인» 그대로, 단 틀은 «밑줄 두 문장»에 맞춘다.
- **후속 PR(새 창, LC 판정):** A) 기본 틀을 사진 전체(0·1·0·1)로 — 실제 잘림은 수리 전후 모두 «가운데 80%»였고, 꽉 채워 찍으면 기본값에서 한쪽 여유가 0.5자뿐(LC 게이트 계산). `OcrDiaryScreen.kt` 초기값·초기화 지점(:118-137 부근)·슬라이더·테스트 기대값. B) 남은 «밑줄» 약속: `OcrDiaryScreen.kt:1206` · `INTERNAL-TEST-2026-11.md:39` 제목 · 이 REPORT의 «4가지 조합 ±1px» → «5조합 ±2px»(반영함 — §9). C) 큐만: 화면 연결 고정 테스트 · 사진 선택 직후 폴백 틀 순간 표시 · EXIF PNG 미리보기 · `privacy-policy.html:39` 단어.
- ~~이 REPORT는 아직 미커밋~~ → 새 창(reading-diary-63)이 기본 틀 PR(`fix/default-crop-full-photo`)에 실어 올림.

## 정리 근거(완료 조건 3)

스쿼시 머지라 `git merge-base --is-ancestor`는 6개 모두 no. 대신 `git diff $(git merge-base main B) B | git patch-id --stable`를 스쿼시 커밋 패치 ID와 비교했다 — 6/6 SAME.

| 가지 | 끝 | PR | 스쿼시 | 패치 ID |
|---|---|---|---|---|
| feat/zip-backup-restore | 12a98d1 | #48 | 3ddee89 | 32b637eb SAME |
| docs/close-stage2-export-backup | 34fe6d0 | #49 | c1e987a | a2ccf572 SAME |
| feat/markdown-export | 6fd7250 | #47 | 91986a6 | 0f1cd526 SAME |
| chore/standing-merge-delegation | b8359a8 | #46 | 2db66ac | 69831cfc SAME |
| docs/close-r4-apk | f2a913d | #45 | b2cb1f1 | 36049764 SAME |
| fix/camera-state-pagecount | 0df43b1 | #44 | c6b83f3 | 9a374a6a SAME |

- 원격: 앞 3개를 `gh api -X DELETE …/git/refs/heads/<b>` 단독 한 줄씩 → `git ls-remote --heads origin` = `main` 1개.
- 로컬: 6개 `git branch -D`(스쿼시라 `-d`가 거부 — 위 패치 ID 동일이 근거).
- 워크트리 3: 제거 전 reparse point 수 md-export 0 · record 0 · zip-backup 0. `git worktree remove`가 md-export·zip-backup에서 «Filename too long»으로 중도 실패(등록은 풀림) → 잔여 폴더(추적 파일·무시 파일만, 직전 `status --porcelain` 0줄)를 `Remove-Item -LiteralPath \\?\…`로 삭제 → `git worktree prune`. 남은 워크트리 = 메인 + 이번 `crop-fix`.

## 첫 AAB 업로드 — 사용자 손 단계(완료 조건 4)

근거: `docs/store-listing/INTERNAL-TEST-2026-11.md:159-171`(U1~U9) · `app/build.gradle.kts:13-18,26-33,42-48,74-82`.

0. **먼저 백업.** 기기의 지금 앱은 디버그 키로 서명됐고 `applicationId`가 같다(`com.woodpeckerai.readingdiary`, 디버그 접미사 없음, `build.gradle.kts:14`). Play에서 받은 앱은 Play 앱 서명 키라 **덮어 설치가 안 되고, 디버그 앱을 지워야 설치된다 → 기록이 지워진다.** 설정 «백업 파일 만들기»(#48)로 zip을 두 곳에 두고 → 디버그 앱 삭제 → Play 앱 설치 → «백업 파일에서 복원».
1. (U1) 업로드 키스토어 생성·보관: alias `upload`, 파일은 레포 루트 `my-upload-key.jks` 또는 `KEYSTORE_PATH`(`build.gradle.kts:28`). 비밀번호는 대화·레포에 쓰지 않는다. 분실 대비 사본을 레포 밖 두 곳에.
2. (U2) Play Console에서 앱 만들기(독서 다이어리 · 앱 · 무료).
3. 버전 코드: 지금 `versionCode = 1`, `versionName = "1.0"`(`build.gradle.kts:17-18`). 첫 업로드는 그대로 된다. **두 번째 업로드부터는 매번 +1 PR이 먼저 필요**(같은 코드는 콘솔이 거부).
4. (U8) 빌드 — **메인 체크아웃에서**(워크트리에는 `.env`가 없어 카카오 키 없이 구워진다): 셸에 `KAKAO_REST_API_KEY`가 export돼 있지 않은지 확인(있으면 그 값이 `.env`보다 먼저 박힘, U8 비고) → `KEYSTORE_PATH`·`STORE_PASSWORD`·`KEY_PASSWORD`를 셸에 넣고 `! gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
5. 콘솔: 테스트 및 출시 → 테스트 → **내부 테스트** → 새 버전 만들기 → Play 앱 서명 사용(기본, 신규 앱은 AAB 필수) → AAB 업로드 → 출시 노트 → 저장 → 검토.
6. (U4·U6) 출시 전에 콘솔이 요구하는 앱 콘텐츠: 개인정보처리방침 URL(U4) · 데이터 보안 폼(§5) · 콘텐츠 등급 · 타겟 연령. 막히면 그 항목부터.
7. (U5) 테스터 목록(≤5) 등록 → 옵트인 링크 배포(§6) → «출시» → 상태 «제공됨» 확인.
8. (U9) 내보내기 전 확인 — 아래 «키» 결론의 «릴리스 전용 카카오 키» 1건을 먼저.

에이전트 실행 0: 키스토어 생성·`bundleRelease`·콘솔 업로드(멈출 곳 (b)).

## 미커밋 2파일 처분(완료 조건 5)

안: **커밋한다 — 단, 사용자가 직접 커밋하거나 사용자가 이 창에 직접 지시한 뒤 chore PR.** 이유: `CLAUDE.md` 작업 사본은 이미 세션 규칙으로 읽히고 있다(이번 원격 가지 삭제도 이 개정 조항을 따랐다) — 커밋 안 하면 레포 정본(«원격 브랜치 삭제는 위임 밖»)과 실제 행동이 어긋나고, 새 워크트리는 옛 문장을 본다. `settings.json` 6줄은 그 조항을 실행하는 allow(`git branch -d` · `git ls-remote` · 이 레포 refs DELETE 한정)라 짝이다. 권한 설정이라 피어(LC) 요청으로 에이전트가 커밋하지 않는다(11-10 결정 유지).

## #47 실기기 확인(완료 조건 6)

남아 있다(2건) — `TASK-2026-1110-…-REPORT.md:77` 아침 손 2: ① 같은 폴더로 마크다운 내보내기 두 번 → 두 번째는 전부 «건너뜀» ② 드라이브 폴더를 골랐을 때 « (1)» 사본이 생기는지. 사용자 11-11 원문은 백업·복원만 언급.

## 키(완료 조건 7)

- **주입 경로:** `secrets` 플러그인(`build.gradle.kts:74-82`)이 `.env`의 `KAKAO_REST_API_KEY`를 `BuildConfig.KAKAO_REST_API_KEY`로, `defaultConfig`(:22-23)가 셸 환경변수를 `BuildConfig.ENV_KAKAO_REST_API_KEY`로 굽는다. 둘 다 `defaultConfig` 수준이라 **release AAB에도 똑같이 들어간다**(`isMinifyEnabled = false`, :45 — 난독화가 있어도 문자열은 남는다). 고르는 순서 `BookSearchClient.kt:165-169`: 기기 저장소 → 셸 env → `.env`.
- **앱 데이터 삭제 뒤 검색이 된 까닭:** 기기 저장소 키는 지워졌지만(백업 zip에도 키는 없음 — `BackupSection.kt:43` «API 키와 앱 설정은 넣지 않습니다»는 사실대로다) APK에 구운 키가 남아 카카오로 검색한 것으로 본다. 근거: 사용자 캡처 1의 결과(국내판 도서 목록)와 캡처 2의 «쪽수를 가져오지 못했습니다»(카카오 응답에는 쪽수가 없어 Google ISBN 조회로 넘어간 흔적, `BookSearchClient.kt:63-68`). 키가 없었다면 Google 경로로 갔을 것이다(:39-41). **[미확인]** APK 바이너리에 키가 있는지 직접 대조는 하지 않았다 — `.env` 읽기가 권한 분류기에 막혔고 우회하지 않았다.
- **문구 어긋남:** 없음. 다만 사용자가 «키를 다시 안 넣었는데 된다»를 «백업에 키가 들어갔다»로 오해할 여지 → 설정의 카카오 안내에 «이 앱에는 검색 키가 들어 있어 따로 넣지 않아도 됩니다» 1줄 후보(후속, 막지 않음).
- **LC 가정 «카카오 키 내장 + Google 폴백 유지» — 수용(조건 1개).** 카카오 공식 문서: 쿼터 [getting-started/quota](https://developers.kakao.com/docs/ko/getting-started/quota) 책 검색 일 30,000건 · Daum 검색 일 50,000건 · 추가 쿼터 요금표에 Daum 검색 없음 → 유출 피해 = 과금이 아니라 한도 소진(그때도 Google 폴백). [보안 가이드](https://developers.kakao.com/docs/ko/getting-started/security-guideline) §2.1·§5.1: 노출 시 «키를 추가로 발급하고 교체한 후, 기존 키를 삭제», 삭제는 복구 불가. REST 키를 클라이언트에 넣지 말라는 직접 금지 문구는 없음(어드민 키·클라이언트 시크릿만 «백엔드에서만»). [앱 키 개편](https://developers.kakao.com/docs/ko/getting-started/app-key-migration)(2025-12): 앱 키별 5개 등록·삭제 가능, 허용 IP는 키별 — 모바일 클라이언트는 IP가 고정이 아니라 쓸 수 없다.
- **첫 AAB 전에 정할 것(1줄):** 카카오 콘솔에서 **릴리스 전용 REST 키를 새로 추가**해 `.env`에 넣고 빌드(개발 키와 분리 → 유출 시 그 키만 삭제·교체, 디버그 빌드는 무영향), IP 제한은 설정하지 않는다. — **LC 판정 16:5x: 수용, 키 생성은 사용자 손.**
- **릴리스 키 만들기 — 사용자 손(카카오 앱 키 개편 문서 기준, 화면 이름은 콘솔에서 다를 수 있음):**
  1. developers.kakao.com → 내 애플리케이션 → 이 앱 선택.
  2. [앱] → [앱 키] → REST API 키 항목에서 «키 추가»(앱 키별 최대 5개) → 이름 예: `release-2026-11`.
  3. 새 키의 허용 IP는 **비워 둔다**(모바일 기기 IP는 고정이 아님). 제품 설정에서 Daum 검색이 이 앱에 켜져 있는지 확인.
  4. 메인 체크아웃 `.env`의 `KAKAO_REST_API_KEY` 값을 새 키로 바꾼다(값을 대화·커밋에 쓰지 않는다). 디버그 빌드도 같은 `.env`를 읽으므로, 개발용 키를 계속 쓰려면 릴리스 빌드 직전에만 바꾸고 빌드 뒤 되돌리거나, 빌드 셸에서 `KAKAO_REST_API_KEY`를 export(셸 값이 `.env`보다 앞섬, `BookSearchClient.kt:165-169`).
  5. 유출이 보이면: 키 하나 더 추가 → `.env` 교체 → 새 버전 업로드 → 옛 키 삭제(복구 불가, 보안 가이드 §5.1).

## 9. 자르기 어긋남 — 재현(코드·수치) · 수리

**원인(확정, 코드):** 미리보기는 `Box(fillMaxWidth, 320.dp)` 안 `AsyncImage(ContentScale.Fit)`인데 틀은 **상자** 기준 비율로 그린다(`OcrDiaryScreen.kt:678-681`, 수정 전). 실제 자르기는 **비트맵** 기준 비율(`BitmapDecoding.cropRect`, :34-40). 세로 사진은 상자 안에서 좌우에 검은 띠가 생겨 두 좌표계가 갈린다. 세로 방향은 사진 높이 = 상자 높이라 어긋남 0 — LC 대조(줄 머리·끝만 빠지고 줄 가운데는 맞음)와 같은 모양.

**수치 재현(384dp 폭 기기 가정 — 1080px 화면·밀도 2.8125, 상자 = 384 − 16×2(`:417`) − 14×2(`:625`) = 324×320dp; 사진 3000×4000):** 사진은 240×320dp로 그려지고 좌우 띠 42dp. 기본 틀 좌 0.1 → 화면 틀 x = 32.4dp(띠 안), 실제 잘림 x = 42 + 0.1×240 = **66dp**. 사용자가 틀을 본문 왼쪽 끝(사진 가로 비율 a)에 맞추면 실제 잘림은 사진 폭의 `(g/B)(1−2a)`만큼 안쪽 — a = 0.1이면 0.104 ≈ **사진 폭 10.4% = 312px/3000px**(양쪽 각각). 본문 한 줄 ≈ 25자 · 사진 폭 76%이므로 한쪽 ≈ 3자 — 틀을 본문보다 약간 바깥에 두면 관측된 1~2자와 맞는다. 411dp 기기면 상자 351dp · 띠 55.5dp로 더 크다. **[미확인]** 실기기 화면 픽셀 실측은 하지 않았다(그래들·에뮬레이터 보류).

**수리(워크트리 `crop-fix`, 가지 `fix/crop-frame-matches-image`):**
- `data/ocr/CropGeometry.kt`(신규, 순수): 사진이 실제로 그려진 사각형(`displayedImageRect`, 90°/270°면 가로세로 교환) + 그 안의 틀(`cropFrame`).
- `BitmapDecoding.orientedSize`: 경계만 읽어 EXIF 반영 크기 — `decodeDownsampled`와 같은 좌표계.
- `OcrDiaryScreen`: 틀을 사진 사각형 기준으로 그림. 회전 시 이미지를 회전 전 크기로 잡고 돌려 사각형과 정확히 겹치게 함(이전에는 가로 사진을 90° 돌리면 상자를 넘쳐 위아래가 잘려 보였다). 크기를 모르면 예전처럼 상자 전체(스크린샷 골든 무영향 예상).
- 테스트 `CropGeometryTest`: 324×320/3000×4000에서 틀 left 66dp 고정 · 회전 교환 · 5조합(회전 0/90/180/270 · 플립 2건)에서 화면 틀 모서리가 가리키는 원본 픽셀 = `decodeForOcr`가 실제로 잘라 낸 첫 픽셀(±2px) — *정정 11-11 저녁: 초안의 «4가지 ±1px»는 테스트 코드(`CropGeometryTest.frameCornerOnScreenPointsAtFirstCroppedPixel`, 5건 · 허용 2f)와 달랐다* · `orientedSize` EXIF 90°.
- 기본 틀 값(가운데 80%): **그대로 둔다.** *(뒤집힘 11-11 저녁: LC 게이트가 «실제 잘림은 수리 전후 모두 가운데 80%»를 계산 → 후속 PR `fix/default-crop-full-photo`에서 사진 전체(0·1·0·1)로. 아래 이 줄의 근거는 낡았다.)* 이제 틀이 사진 위에 정확히 보이므로 사용자가 보고 맞출 수 있다. 바꿀 근거(사진별 여백 분포)가 없다.

## 10. 사용자 확인(수리 APK 설치 뒤, 3줄)

1. `D:\사진\reading-diary\`의 수리 APK를 덮어 설치(서명 같음 — 거부되면 앱을 지우지 말고 멈춤) → 기준 사진 1번과 같은 쪽을 세로로 다시 찍는다.
2. 노란 틀의 좌우를 본문 첫 글자·끝 글자 바로 바깥에, 위아래를 본문 첫 줄부터 마지막 줄까지 맞춘다 → 틀이 사진 위(검은 띠가 아닌 곳)에 그려지는지 본다 → 인식.
3. 틀이 보이는 화면 1장 + 나온 글 화면 1장을 캡처해 `D:\사진\reading-diary\`에 둔다 → 워커가 원문 9줄과 대조(빠진 글자·잘못 읽은 글자 수). 기대: 줄 머리·끝 누락 0, 남는 오류는 ML Kit 인식 자체(밑줄 연필선 겹침 등).

## 11. 기기 안에서 밑줄 친 줄만 고르기 — 설계 1쪽(구현 0)

**입력:** 자른 비트맵(긴 변 ≤2048) + ML Kit `Text.TextBlock.lines[].elements[]`의 상자(이미 받고 있음, `OcrTextAssembler`가 쓰는 것과 같은 객체).

**후보**
| | 방법 | 장점 | 약점 |
|---|---|---|---|
| A | **줄 아래 띠 검사.** 각 줄 상자 아래, 다음 줄 상자 위까지(줄 사이 간격의 0~70%) 띠를 회색조로 잘라 열마다 «종이보다 어두운 픽셀이 있는가»를 본다 → 어두운 열이 이어진 구간(가로 연속 길이 ≥ 글자 2개 폭, 세로 흔들림 허용 위해 띠 전체 높이에서 찾음) → 그 구간과 겹치는 `element`(어절)만 고름 | 순수 Kotlin(OpenCV 0) · 줄 일부 밑줄(줄 끝 한 어절 + 다음 줄)을 어절 단위로 처리 · JVM 테스트 쉬움 | 줄 간격이 좁은 책에서 윗줄 글자 아랫부분이 띠에 들어옴 → 줄 상자 하단 여유 2px 제외 |
| B | **긴 가로 획 추출 후 줄에 붙이기.** 적응형 이진화 → 가로 형태학 열림(커널 ≈ 글자 3개 폭 × 1px) → 남은 획의 연결 성분 중 폭 ≫ 높이인 것 → 각 획을 «바로 위 줄»에 배정 | 휜 연필선·여러 줄 걸친 밑줄도 한 번에 | 직접 구현 시 코드량 큼(OpenCV면 APK +10MB대) · 표 괘선·장식선 오검출 |
| C | (보조) 형광펜: HSV 채도가 종이보다 높은 픽셀이 줄 상자 **안**을 덮은 비율 ≥ 40% | 형광펜은 A·B로 못 잡음 | 컬러 인쇄 책 오검출 |
| D | **줄 고르기 UI(A·C로 미리 선택).** 인식된 줄을 목록/사진 위 상자로 보여 주고 사용자가 눌러 넣고 뺌 | 오검출을 사용자가 바로잡음 — 자동 정확도에 기대지 않음 | 한 단계 더 누름 |

**권고: A + D(+ 형광펜 쓰는 사용자가 있으면 C).** 자동 판정을 «미리 선택»으로만 쓰고 최종은 사용자 손.

**뒷장 비침을 밑줄로 잡지 않는 기준(같은 사진 안에서 상대값):**
1. 대비: 종이 중앙값 대비 획의 어둡기 Δ_line ≥ **0.4 × Δ_text**(Δ_text = 같은 줄 글자 획의 어둡기). 기준 사진 1번에서 본문 2번째·4번째 줄 아래의 비침선은 연필 밑줄보다 눈에 띄게 옅다 — 이 비율로 가른다(문턱은 실사진 3~5장으로 보정, 사진은 레포 밖).
2. 날카로움: 획 가장자리 세로 기울기(gradient) 크기 — 비침은 종이를 지나며 번져 경계가 무르다. 연필선의 70% 미만이면 기각.
3. 위치: 비침선은 뒷면 줄 간격에 맞춰 있어 앞면 줄 사이 «정중앙 띠»를 벗어나는 경우가 많다 — 보조 점수만(단독 기각 금지).

**예상 오검출:** 장 머리 붉은 장식 막대(본문 1번째 줄 왼쪽), 쪽번호 옆 가로줄, 손글씨 메모의 가로획(쪽 아래 자필 메모), 진한 비침(얇은 종이 + 진한 볼펜), 밑줄을 다음 줄 바로 위에 그은 경우(윗줄/아랫줄 배정 혼동) — 마지막은 «가까운 쪽, 동률이면 윗줄».
**예상 미검출:** 아주 옅은 연필(H심), 글자 위를 지나는 밑줄(가운데 줄 긋기).

**구현 범위(별도 발주 시):** ① `UnderlineDetector`(순수: 회색조 배열 + 줄/어절 상자 → 고른 어절 목록) + 합성 이미지 JVM 테스트 ② `TextExtractor`가 줄·어절 상자를 돌려주도록 `OcrOutcome.Text` 확장(지금은 문자열만) ③ 결과 화면에 줄 고르기(D) + 스크린샷 테스트. PR 3개 예상. 실사진 정확도 측정은 레포 밖 로컬 사진으로 워커가 수치만 보고.

## 12. «밑줄 구절 추출» 약속 ↔ 기본 경로가 영역 전체를 읽는 차이(안 1개)

**안: 지금은 문구를 고치고(작은 PR), 동작(11의 A+D)은 설계 판정 뒤.** 이유: 동작 수리는 PR 3개 규모인데 문구 거짓은 오늘부터 사용자를 헷갈리게 한다(이번 «밑줄 외 텍스트까지 뽑는다» 보고가 그 결과). 바꿀 곳: `OcrDiaryScreen.kt:608` «밑줄 부분을 촬영…» → «밑줄 친 부분만 틀 안에 들어오게 잘라 주세요. 틀 안의 글자를 모두 읽어 옵니다.» · `:1014` «AI 밑줄 인식 시작» → «틀 안 글자 읽기» · `:895` 토스트 · `:1048` «밑줄친 문장이 이곳에…» → «읽은 글자가 이곳에 보이고 고칠 수 있습니다.» · `SettingsScreen.kt:50,405,654` «밑줄 구절 인식» → «책 페이지 글자 인식». 스크린샷 골든 갱신 필요(문구 PR의 이유로 명시).

## 13. 스토어 사용자가 Gemini «정밀 분석»을 쓰는 길(결정은 사용자 — 과금)

현행 근거: 키는 사용자가 설정에 등록(`SecureKeyManager`, fail-closed), `BuildConfig`에 Gemini 키 없음(`build.gradle.kts:78`, ADR-001).

| 길 | 필요한 변경 | 비용이 닿는 곳 | 위험·정책 |
|---|---|---|---|
| ① 현행(본인 키) | 0. 설정 안내에 «Google AI Studio에서 무료 키 발급» 절차 링크 정도 | 사용자 본인 Google 계정(무료 등급 한도 안이면 0) | 일반 사용자는 거의 못 씀 · 데이터 안전 폼에 «사용자 선택 시 사진 전송» 유지 |
| ② 릴리스에서 숨김 | `BuildConfig` 플래그 1개로 설정 카드·정밀 분석 버튼 숨김 + 스크린샷 · 데이터 안전 폼에서 사진 전송 항목 삭제 가능 | 0 | 가장 단순. 기능 1개 감소 |
| ③ 게이트웨이 경유(오너 키) | 서버 함수(예: Cloud Functions/Workers) + Play Integrity/App Check + 기기별 일일 한도 + 앱 클라이언트 교체 + 처리방침·데이터 안전 폼 갱신(사진이 오너 서버 경유) + 키 회전 | **오너 결제 계정**(호출당 이미지 입력 과금) + 서버 운영 | ADR-001 재검토 대상 · 남용 시 과금 폭주 → 한도·인증 필수 · 최소 PR 4~5개 |

LC 판정 16:5x: ① 수용 · 12번 문구 수정은 crop-fix PR과 분리한 별도 PR · 5번 커밋은 사용자 손.

권고(워커): 첫 AAB는 **① 현행 그대로**(코드 변경 0, 이미 동의·확인 다이얼로그 있음), 11번의 기기 안 밑줄 선택이 나온 뒤 정밀 분석의 필요가 남는지 보고 ②/③을 정한다.

## 효과 수준(effort)
세션 medium 유지. 올린 곳 없음.

## 막힌 것
- `.env`의 카카오 키 존재 여부·APK 내장 대조(값은 출력하지 않고 예/아니오만 보는 스크립트) — 자동 모드 분류기 거부. 우회하지 않음. 사용자 확인 방법(1분): 비행기 모드로 도서 검색 → 오류 안내가 «공용 Google 도서 검색(이 앱에는 카카오 REST 키가 등록되어 있지 않습니다)…»이면 키 없음, «도서 검색…»이면 키 내장(`AddEditBookScreen.kt:559-562`).
