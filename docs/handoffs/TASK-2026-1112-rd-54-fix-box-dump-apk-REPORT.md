# TASK-2026-1112 rd #54 수정 · 기기 상자 덤프 PR · 확인용 APK — REPORT

발주서: `life-coordinator/handoffs/TASK-2026-1112-rd-54-fix-box-dump-apk.md` · 시작 main `9e58138` · #54 head `42ec678`
레포가 공개라 이 문서에는 사진 내용(책 제목 · 쪽 · 본문 · 메모 글 · 원본 사진 파일 이름)을 쓰지 않는다 — «기준 사진 n번» · g01~g11과 수치만.

LC 수용(03:1x): 덤프 꺼내는 길 = 디버그 전용 «덤프 공유»(공유 시트) + adb 보조 · 5는 별도 소형 fix PR · 순서 #54 → 덤프 PR → 5 PR.

## 체크리스트(완료 조건)

- [x] 1. #54 → main **`16789183`** (head `661edb4`). 1차 수정 10건 + 2차 후속 4건 = `333817f` · 덤프 머리 읽기(dumpRotate/dumpSize) = `661edb4`. critic 2차 8/10 PASS · 전체 56클래스 382건 실패 0 · head 2클래스 18건 실패 0 · LC 게이트 PASS-관찰 → 머지 · 원격 DELETE · 워크트리 제거 · 로컬 -D
- [x] 2. 덤프 PR **#55** → main **`f796e1e`** (head `a114e40`). critic 1차 7/10 PASS → 반영(a94201e · a114e40) → 2차 8/10 PASS · 테스트 23건(1차 실패 1 = Windows FileProvider → 셰도) · compileReleaseKotlin · 스크린샷 21건 · LC PASS-관찰 → 머지 · 원격 DELETE · 워크트리 제거 · 로컬 -D
- [x] 3. APK = `<작업 폴더>\reading-diary-debug-20261112-59e2677.apk`(21,669,894 B, main `59e2677` = #54·#55·#56, assembleDebug 05:15~05:20). BuildConfig 실측(값 출력 0): `KAKAO_REST_API_KEY` 길이 32 · `ENV_KAKAO_REST_API_KEY` 0 · NAVER·GEMINI 필드 없음 · 셸 NAVER/KAKAO 환경변수 미설정. #57은 테스트 소스만이라 APK 무관(LC 판정). 사용자 손 한 벌 = 아래 4단계 약 15분
- [x] 4. 측정 명령 1줄 = 아래 «측정 명령». 덤프가 이 창에 들어오지 않음(새벽 무인) → 11장 재측정은 아침 덤프 뒤
- [x] 5. 새 사진에서 틀·회전 초기화 **#56** → main **`59e2677`** (head `b7053bd`, main merge 2회 포함). critic 8/10 PASS · OcrNewPhotoResetTest 1 · OcrDiaryPhotoScreenTest 10 · 스크린샷 21건 verify · main 합친 뒤 22건(#54 2클래스 포함) 실패 0 · LC PASS-관찰 → 머지 · 정리
- [x] 4+. (LC 보강) **#57** → main **`544a5f9`** (head `d4807b6`). critic 1차 5/10 → 실측 재설계 → 2차 6/10(실행 증거만) → 3차 7/10 PASS · DumpGuardTest 5 · GoldenSetTest 8 · 11장 회귀 diff 0 · LC PASS-관찰 → 머지 · 정리
- [x] 6. 미확정 글자 5개 위치 — `<기준 자료 폴더>\g04.golden.txt` 37번째 줄 2개 · 38번째 줄 1개, `g10.golden.txt` 33번째 줄 2개(모두 `[memo]` 절, 메모장 Ctrl+G로 줄 이동 → `{?}`를 사진 보고 고침). 각 파일 3번째 줄의 `{?}`는 설명 주석이라 세지 않음
- [x] 7. REPORT(이 파일)

## 아침 사용자 손 한 벌 · 4단계 · 약 15분

APK = `<작업 폴더>\reading-diary-debug-20261112-59e2677.apk`(main `59e2677`).

1. **덮어 설치(약 3분)** — APK를 폰으로 옮겨(USB 복사 또는 드라이브) 설치. 서명이 같아 기존 기록이 유지된다. «업데이트할 수 없음/충돌»이 뜨면 **앱을 지우지 말고 멈춘다**(지우면 기록이 사라짐).
2. **기준 사진 1번 쪽 재촬영·틀 확인(약 3분)** — 책 한 권 기록 → «사진 촬영» → 틀이 사진 전체로 열리는지 → 자르기 확정 → 인식 → 화면 2장 캡처(틀 화면 · 결과 화면). 이어서 «사진 선택»으로 다른 사진을 고르면 회전·반전·틀이 처음 상태로 돌아오는지 한 번 본다.
3. **상자 덤프 11장(약 7분)** — 설정 → 맨 아래 «개발용 · 글자 상자 덤프» → «모두 지우기» → 스위치 켜기. 기록 화면에서 기준 사진 **1번부터 11번까지 순서대로** «사진 선택» → (회전·반전 누르지 않음) → 자르기 확정(기본 = 전체) → 인식(장당 약 30초). 1번 원본이 폰에 없으면 PC의 사본을 폰으로 먼저 복사.
4. **덤프 옮기기(약 2분)** — 설정 → «덤프 공유»(11개) → 퀵 셰어/드라이브 등으로 PC → `<기준 자료 폴더>\boxes\mlkit-20261112\`에 이름 그대로 둔다. 끝나면 스위치 끄기 → **«모두 지우기»**(공유 사본에도 인식된 본문이 남으므로). 짝짓기 규칙: 이름을 바꾸지 않는다 — 측정이 글자 겹침으로 기준 사진과 자동으로 짝짓는다(같은 사진을 다시 인식했으면 나중 덤프를 쓴다).

## 측정 명령(덤프가 들어오면 — 워커가 실행)

사전 대조(03:5x): 정답 머리 `rotate:` = 9장 90 · 2장(5번 · 9번) 0. 레포 밖 사진 사본에서는 EXIF를 읽지 못함(사본에 태그 없음 추정) → 덤프 머리 `# expects golden rotate:`·세운 크기로 장마다 확인한다.
#57 머지 뒤: 이름 바꾸기 · NOT EXACT 수작업 거르기 **없음** — 측정이 덤프를 글 포함률로 g01~g11에 자동으로 짝짓고, NOT EXACT · 크기 불일치 · 글 일치 < 40% · 방향 의심/판정 불가 · 짝 없음을 «⚠» 행(사유 포함)으로만 낸다. 합산 때 «⚠» 행은 빼고 뺀 장 수를 적는다. (#57 머지 전이면 `ocr-dump-0NN` → `gNN.tsv` 수동 짝 + `NOT EXACT` grep.)
측정 1줄(`JAVA_HOME`=Android Studio JBR, main에서):
   `ANDROID_HOME='<Android SDK 경로>' RD_GOLDEN_DIR='<기준 자료 폴더>' RD_SPIKE_OUT='<오버레이 폴더>' gradle :app:testDebugUnitTest --tests com.example.golden.GoldenMeasureTest --rerun --no-daemon --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g`
   → 표의 g01~g11 행(수치 행 + «⚠» 행)이 11개인지 센다 — 모자라면 그 장은 덤프가 없거나 짝을 못 지은 것(«⚠짝 없음» 행 확인).
   → `golden\_results\measure.md` 스파이크 표에 상자 출처 `mlkit-20261112` 행이 `tesseract-5.3.1-kor` 행과 나란히 생긴다(줄 대응 수 · 정밀도 · 재현율).

## 그래들 기록

모두 `--no-daemon --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g`, 묶음 전 여유 RAM 기록.
- ① 03:16:45~03:23:31 #54 `UnderlineDetectorTest` 10 · `GoldenSetTest` 7 실패 0(6.77GB)
- ② 03:24:50~03:27:17 #54 `GoldenMeasureTest` 11장 재측정 통과 — 바뀐 곳 3번 1어절
- ③ 03:27:59~03:35:11 #54 전체 56클래스 382건 실패 0 · 건너뜀 1
- A 03:43:43~03:49:52 #54 head 661edb4 2클래스 18건 실패 0(6.05GB)
- B 03:49:55~03:57:38 덤프 23건 중 1 실패(`OcrBoxDumpShareTest` — 상세는 D가 결과 폴더를 덮어써 소실. 추정 원인 = Windows Robolectric FileProvider 경로 문제, 기존 `ShadowCacheFileProvider` 적용 a114e40, 재실행 대기)
- C 03:57:44~04:01:57 덤프 `compileReleaseKotlin` 통과(4.32GB) — 릴리스 변형 컴파일 증명
- D 04:02:00~04:04:51 덤프 스크린샷 6클래스 21건 verify 통과(5.29GB)
- E 04:04:55 시작 → Claude Code가 «시스템 메모리 부족»으로 중단(LC 보고 → «가» 뒤 재시작)
- B' 04:16:48~04:21:18 `OcrBoxDumpShareTest` 2 실패 0(6.16GB) — 원인 확정(Windows FileProvider)
- E1 04:21:43~04:24:57 5 fix `OcrNewPhotoResetTest` 1 · `OcrDiaryPhotoScreenTest` 10 실패 0(6.24GB)
- E2 04:25:23~04:28:40 5 fix 스크린샷 6클래스 21건 verify 통과(6.14GB)
- F 04:35:23~04:43:15 5 fix + main(#54·#55) 22건 실패 0(6.05GB)
- G 04:57:19~05:06:47 #57 DumpGuardTest 5 · GoldenSetTest 8 · GoldenMeasureTest(11장) 실패 0 · Tesseract 행 diff 0(6.04GB)

## 후속 큐(막지 않음)

1. (#54 게이트 관찰 4) `GoldenSet.kt` `markedTokens` 오류 메시지가 정답 밑줄 글을 그대로 넣음(로컬 콘솔·그래들 보고서 한정) → 줄 번호만
2. (관찰 4) `GoldenSet.kt` 48행 BOM 문자 · 92행 U+FFFD 문자가 소스에 글자 그대로 → 유니코드 이스케이프로(도구가 쓰기 때 이스케이프를 글자로 바꾸는 문제 — Edit로)
3. (관찰 4) `dumpRotate`가 90 배수 아닌 값을 조용히 0회전으로 받음 → 거부·경고
4. (관찰 4) `GoldenSetTest` 경로 단언 `dir.name`이 약함 → 절대 경로 조각 여러 개로
5. ~~(#55 critic 2차 경미 1) 측정이 덤프 머리 `NOT EXACT`를 읽지 않음~~ → #57로 처리
5c. (#57 critic 3차 경미) 짝 상대 정답에 사진이 없거나 `<id>.tsv`가 우선할 때 자동 짝 덤프가 표에 안 나옴(수치 오염 없음) → «⚠짝 없음» 행
5d. (#57 critic 관찰) 덤프 거르기는 합성 테스트로만 검증 — 첫 기기 덤프 측정 때 «⚠» 행 수·사유 확인
5e. (#56 게이트 관찰) 테스트가 회전·카메라 경로를 직접 단언하지 않음
6a. (#55 게이트 관찰 4) `OcrBoxDump.pending`이 프로세스 전역 object 상태 · 정책 테스트가 병합 매니페스트는 보지 않음 · 좌표 되돌리기 ±(배율×1)px 오차
6. (#55 critic) `mapping` ↔ `BitmapDecoding.decodeForOcr` 왕복 Robolectric 테스트
7. (#54 게이트 관찰 1, 아침 사용자 보고 1줄) #54 이력에 기종 코드 · 로컬 사진 폴더 경로도 남아 있음 — head에서는 지움, LC 판정 «고쳐서 앞으로»에 포함
5f. (#57 게이트 관찰 1) 테스트 빈칸: 크기 불일치 → 제외 · 측정 흐름의 «방향 판정 불가» · FIRST 구제(주석 달고 측정)
5g. (#57 게이트 관찰 2) 정답에 덤프 0개일 때 «덤프 없음» 행을 코드로

## 마감 상태(2026-11-12 05:2x)

- main **`544a5f9`** = #54 `1678918` · #55 `f796e1e` · #56 `59e2677` · #57 `544a5f9`. 원격 가지 = main 1개. 워크트리 = 메인 1개. 메인 체크아웃 미추적 = REPORT 2개.
- critic 기록: #54 1차 6/10 → 2차 8/10 · #55 1차 7/10 → 2차 8/10 · #56 8/10 · #57 1차 5/10 → 2차 6/10 → **3차 7/10 PASS**(PR 본문엔 2차까지 — 여기가 정본).
- 아침 사용자 손: 위 4단계(약 15분) + 미확정 글자 5개(완료 조건 6 줄) + #54 이력 메모(큐 7, 보고만).
- 아침 워커 손: 덤프가 `<기준 자료 폴더>\boxes\mlkit-20261112\`에 들어오면 측정 1줄 → 11행 세기 → Tesseract 행과 나란히(줄 대응 수 · 정밀도 · 재현율) → SPIKE 문서 §1에 mlkit 행.

## 효과 수준(effort)

세션 medium 유지. 서브에이전트: critic 9회(정의 기본). 올린 곳 없음.
