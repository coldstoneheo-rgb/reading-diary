# TASK-2026-1111 rd 기본 틀 · 기준 자료 · 밑줄 검출 스파이크 · 확인용 APK — REPORT

발주서: `life-coordinator/handoffs/TASK-2026-1111-rd-default-crop-golden-underline-spike.md`(+ 추가분 10~13 · 14~16) · 창: reading-diary-63 · 시작 origin/main `9ebc60d`
레포가 공개라 이 문서에는 사진 내용(책 제목 · 본문 · 메모 글 · 원본 사진 파일 이름)을 쓰지 않는다 — «기준 사진 n번»(golden 폴더 `gNN`)과 수치만.

## 체크리스트(완료 조건)

- [x] 1. PR A = **#53** → main **`9e58138`**(head `588e273`). 단위 39건 · 스크린샷 7클래스(골든 1장 의도 변경) 실패 0 · critic 8/10 PASS · LC 1차 조건부(공개 레포에 사진 내용 조각 — 이전 REPORT 6곳) → 익명화 1커밋 → 재게이트 PASS-관찰 → 머지 · 정리(원격 DELETE · 워크트리 제거 · 로컬 -D) · `ls-remote` = main 1개
- [x] 2. 이전 REPORT 정정(5조합 ±2px) + 익명화 → #53으로 원격에. 메인의 미추적 사본은 «사본에만 있는 10줄 = 정정·익명화한 줄의 옛 판» 확인 뒤 삭제 → ff-only `9e58138`
- [x] 3. 기준 자료 규격 1쪽 — `docs/quality/GOLDEN-SET.md`(#54)
- [x] 4. 기준 사진 1번 정답 초안 — golden 폴더 `g01.golden.txt`(+ 사진 사본 · Tesseract 상자 · Tesseract 본문 출력)
- [x] 5. 측정 진입점 `GoldenMeasureTest`(`RD_GOLDEN_DIR`) — 빈 폴더 «자료 0장» 테스트 포함, 11장 실측 통과
- [x] 6. 스파이크 `UnderlineDetector.kt` — 합성 13건 실패 0 · 11장 실측(상자 = Tesseract): 정밀도 96.1% · 재현율 46.3% · 상자 줄이 대응된 줄만 73.4% · 비침·장식 오검출 0 → **PR #54**
- [x] 7. 판정 재료 1쪽 = `docs/quality/SPIKE-UNDERLINE-2026-11.md`(수치 · 실패 유형 · 원인 분리 · D 전제 허용 의견 · ML Kit 덤프 길 · 제품화 PR 4개) · 캡처 `D:\사진\reading-diary\spike\g01~g11.*.overlay.png`
- [ ] 8. 확인용 APK — LC 결정: ML Kit 상자 덤프 PR(14) 머지 뒤 1번만 빌드 → **새 창 몫**(15)
- [x] 10. 새 사진 10장 정답 초안 — 서브에이전트 2개가 사진을 직접 읽어 작성(golden `g02~g11`, 같은 책 한 권, 연필 밑줄만 · 형광펜 0). 미확정 글자 {?} 5개(4번 3 · 10번 2). 11장 형식 검사 OK. id 대응표는 레포 밖 `golden/ID-MAP.txt`
- [x] 11. 11장 측정 — 사진별 표 · 실패 유형별 개수는 SPIKE 문서 §1
- [x] 12. 갤럭시 [T] 같은 쪽(기준 사진 7번): 본문 글자 오류율 0.5% · 받은 글 전체 24.7% · Tesseract 68.2% · [T] 자필 53.8% · ML Kit 미측정
- [x] 13. 조사 1쪽 — `docs/quality/RESEARCH-SAMSUNG-T-SHARE-BLOCKS-2026-11.md`. ⓐ 공개 SDK·인텐트 없음(직접 연 출처 6) ⓑ 공유로 받기 PR 2개 ⓒ 블록 규칙 4개 + 측정 길
- [ ] 14. ML Kit 상자 덤프 PR(디버그 전용) — **새 창 몫**(컨텍스트 60% 근접으로 미착수, LC 지시)
- [ ] 15. 덤프 머지 뒤 확인용 APK 1개 + 사용자 손 한 벌 — **새 창 몫**
- [x] 16. 덤프 측정 명령 1줄 — 아래 «새 창에 넘김»
- [x] 9. REPORT(이 파일)

## 마감 상태(2026-11-11 22:1x)

> **새 창 첫 규칙:** 이 레포는 PUBLIC이다. 첫 커밋 전부터 파일 이름·id·테스트 문자열·커밋 메시지에 책 이름·쪽·본문·메모 글 0 — 지운 커밋으로는 이력에서 사라지지 않는다.

- **#54 이력 처리(LC 판정 22:2x):** «고쳐서 앞으로» — force-push 0 · PR 닫기 0. 남은 것은 출판된 책의 이름·쪽 표기·본문 조각 1개(자필 메모 글 0). 사용자가 지우기를 원하면 사용자 손으로 GitHub 지원 요청. 새 창은 #54를 연 채 같은 가지(`feat/underline-spike-golden`, 워크트리 `<레포>\.claude\worktrees\underline-spike` — 남겨 둠)에 수정 커밋을 얹는다.
- **기준 자료 id 규칙:** `g01`~`g11`(레포 문서엔 «기준 사진 n번»만). 미확정 글자 {?} 5개 = 4번 3 · 10번 2. 대응표는 레포 밖 `golden/ID-MAP.txt`.
- **스파이크 수치(상자 = Tesseract):** 정밀도 96.1%(124/129) · 재현율 46.3%(124/268) · 상자 줄이 대응된 줄만 73.4%(124/169) · 미검출 144 = 상자 실패 99 + 검출기 45 · 비침·장식 오검출 0 · 본문 어절 오검출 5 · 본문 밖 구간 5 · 형광펜 0장(미측정). 사진별 표 = `docs/quality/SPIKE-UNDERLINE-2026-11.md` §1.
- **갤럭시 [T] 조사 결론:** ① 다른 앱이 부를 공개 SDK·인텐트 없음(삼성 개발자 SDK 목록·지원 문서 직접 확인) ② 사용자가 [T]에서 «공유»하면 공유 시트로 나감 — `text/plain` 형식은 추정(사용자 1분 확인) ③ 기대할 길 = «공유로 받기»(PR 2개) · 기대하면 안 될 길 = 삼성 내부 서비스 직접 호출.

- main **`9e58138`**(#52 `21771cc` · #53). 메인 체크아웃 = main, 미추적 = 이 REPORT 1개.
- **PR #54**(`feat/underline-spike-golden`, 워크트리 `.claude/worktrees/underline-spike`, head `42ec678`) — **critic 6/10 NEEDS_REVISION**, [GATE-REQ] 안 보냄.
  - 🔴 **이력 노출(사용자 결정):** 이미 push된 앞 커밋(첫 push `2214739`의 조상 `07f436e` · `3de3ea6` 등)에 책 이름이 든 id · 쪽 표기 · 본문 조각 1개가 있다(`git log -p origin/main..origin/feat/underline-spike-golden` grep으로 확인, 최종 diff는 0). 길: (가) 깨끗한 단일 커밋으로 force-push — 발주서 멈출 곳 (f) 금지라 사용자 결정 (나) #54 닫고 새 가지·새 PR — 닫힌 PR의 `refs/pull/54/head`로 옛 커밋은 계속 보이므로 완전 삭제는 GitHub 지원 요청(사용자). 에이전트 실행 0.
  - 수정 목록(새 창): ① `highlightedWords` 경계 자르기 + `height` 인자 + 가장자리 상자 테스트 ② `band_stopsAboveTheNextLine`이 nextTop 분기를 실제로 타게(줄 2를 바짝 붙이거나 `bandMaxRatio` 키움) + 주석 숫자 ③ Δ_text 하한(예: 40 미만 줄 건너뜀) ④ 다음 줄을 x 범위가 겹치는 줄로 ⑤ 측정 상세(본문 어절·OCR 줄 글)를 `_results/details.md`로 분리하고 «PR에는 표만» 명시 ⑥ RESEARCH 문서 ⓑⓒ를 별도 docs PR로 나누거나 함께 두는 이유 ⑦ 전체 `testDebugUnitTest` 1줄 + «UI 변경 0이라 Roborazzi 대상 없음» ⑧ 로컬 경로·기종 자리표시자 ⑨ 붉은 인쇄 테스트 이름에 «덮인 비율로 통과» 한계 ⑩ 측정 편향(줄 상자에 여백 메모가 합쳐져 어절 범위가 밀림 · 같은 어절 두 번 · greedy 매칭 · `#`/`[`로 시작하는 본문 줄) 주석 또는 수정.
- 레포 밖: `D:\사진\reading-diary\golden\` g01~g11(정답 초안 · Tesseract 상자 · 엔진 출력 · `ID-MAP.txt` · `_results/measure.md`) · `D:\사진\reading-diary\spike\` 오버레이 11장.

### 새 창에 넘김(14 · 15 · 16)

- **14 덤프 PR:** 설계 = SPIKE 문서 §1-1. LC 조건: 디버그 전용(릴리스 변형 컴파일 또는 소스셋 분리로 증명) · 줄·어절 상자 + 인식 글 TSV(Tesseract 열, 머리에 OCR 입력 비트맵 크기·자르기·회전) · 앱 전용 저장소 · **adb 없이 꺼내는 길을 기본**(디버그 전용 «덤프 공유» → 공유 시트) · 네트워크 0 · 처리방침·데이터 안전 폼 무관 1줄 · critic → [GATE-REQ]. 그래들 전 [ASK].
- **15 APK:** 14 머지 뒤 **메인 체크아웃**에서 `assembleDebug`(키스토어·`.env`는 메인에만 — 워크트리로 복사하는 `cp`는 이 세션 deny) → `D:\사진\reading-diary\`에 날짜·SHA 이름으로. 카카오 키 실측 = `app/build/generated/source/buildConfig/debug/com/example/BuildConfig.java`의 `KAKAO_REST_API_KEY` **길이만**(11-05 빌드 = 32 · `ENV_KAKAO_REST_API_KEY` = 0). 사용자 손 한 벌(약 15분): ① 덮어 설치(서명 같음 — 거부되면 앱을 지우지 말고 멈춤) ② 기준 사진 1번 쪽 재촬영 → 틀이 사진 전체로 열리는지 → 인식 → 화면 2장 캡처 ③ 기준 사진 11장 각각 갤러리에서 고름 → 자르기 확정(기본 전체) → 인식(장당 약 30초) ④ «덤프 공유» → PC `D:\사진\reading-diary\golden\boxes\mlkit-<날짜>\`.
- **16 측정 1줄**(덤프 파일 이름을 `g01.tsv`~`g11.tsv`로 맞춘 뒤, #54가 들어간 워크트리에서; 셸에 `JAVA_HOME`=Android Studio JBR):
  `ANDROID_HOME='<Android SDK 경로>' RD_GOLDEN_DIR='D:\사진\reading-diary\golden' RD_SPIKE_OUT='D:\사진\reading-diary\spike' gradle :app:testDebugUnitTest --tests com.example.golden.GoldenMeasureTest --rerun --no-daemon --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g`
  → `golden\_results\measure.md`에 상자 출처 `mlkit-…` 행이 더해진다.

### 후속 큐(막지 않음)

1. 새 사진을 고를 때 앞 사진의 틀·회전이 이어짐(`OcrDiaryScreen.kt:154-166 · 185-196`, 이전부터) — 후속 1순위(LC)
2. `INTERNAL-TEST-2026-11.md:103` 표 항목명 «N5 밑줄 글자 인식(기본)»
3. 틀이 가장자리에 붙으면 점선·손잡이 바깥 절반이 잘림(critic·LC) · 회전·플립 + 전체 틀 테스트 1건(critic)
4. 공유로 받기 PR 2개(RESEARCH ⓑ) · 블록 규칙으로 본문만(ⓒ) · 띠를 줄 기울기에 맞춰 돌리기(SPIKE §5)
5. 사용자 확인: 4번·10번 손글씨 {?} 5자 · 9번 옅은 밑줄 범위 · 손 별표(★)를 메모로 둘지 · 2번 토막 밑줄 합침 · 사용자 기기 [T] 공유 시트(1분)

## 사용자 지시 처리

- «미커밋 2파일(CLAUDE.md · .claude/settings.json)을 chore PR로 커밋해» → 변경분만 패치로 떠서 origin/main 위에 적용(충돌 0, 내용 변경 0) → **PR #52** head `a1400a3` → LC 게이트 PASS-관찰 → `gh pr merge 52 --squash --match-head-commit a1400a3…` → main **`21771cc`**. 원격 가지 DELETE · 로컬 워크트리 제거 · 로컬 가지 -D(스쿼시, origin/main과 diff 0 확인) → `git ls-remote --heads origin` = main 1개.
- «2파일 되돌리고 ff-only 가» → 대조(작업 사본 vs origin/main 차이 = #50의 CropGeometry 구조 목록 1줄뿐, 사라지는 사용자 변경 0) → `git checkout -- CLAUDE.md .claude/settings.json` → `git merge --ff-only origin/main` **3ddee89 → 21771cc**.

## 그래들 기록

모두 `--no-daemon --offline --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g`, 묶음 전 여유 RAM 기록.
- PR A: 1차 20:26 `ANDROID_HOME` 없음(설정 단계 실패, 테스트 0) · 2차 20:27:59(5.66GB) 컴파일 뒤 테스트 진입에서 Claude Code 메모리 부족 회수(결과 0, 고아 데몬은 스스로 종료 — `gradle --stop`은 분류기 거부, 우회 0) · «가» 뒤 작은 묶음 3+2+1+1(5.6~6.7GB) 전부 통과.
- 스파이크: 21:31 합성 13건(7m23s, 첫 컴파일 포함) · 21:39 측정 11장(3m18s) · 21:44 측정 재실행(대응된 줄 칸 추가, 4m36s). 회수 0.

## 발견

- 이 PC에 로컬 Tesseract 5.3.1 + `kor`(`C:\Tesseract-OCR`) → 실사진 어절 상자를 JVM 측정에 씀(LC 수용, 상자 출처 칸 = `boxes/<출처>/`). 다만 기울고 휜 페이지에서 본문 줄을 대부분 못 만듦(11장 합계 52/86줄) — 미검출 144 중 99가 이 탓.
- 원본 사진 대부분이 EXIF 6(가로 저장). Tesseract·측정 코드는 EXIF를 안 읽음 → 정답 머리 `rotate:`로 세움.
- `debug.keystore`를 워크트리로 복사하는 `cp`가 이 세션 deny → 확인용 APK는 메인 체크아웃에서만.
- 워크트리 그래들은 `ANDROID_HOME`(메인 `local.properties`의 `sdk.dir`)을 셸에 넣어야 한다.
- 이 도구 환경은 쓰기 때 `\uXXXX` 이스케이프를 실제 글자로 바꿔 저장 → Kotlin 소스에 BOM·U+FFFD·`'가'..'힣'`가 글자 그대로(의미 동일). 파이썬 heredoc의 `\\`도 바뀌어 경로 치환이 깨짐 — 경로가 든 문서 편집은 Edit/Write로.
- 카카오 키: 11-05 디버그 빌드 `BuildConfig` — `KAKAO_REST_API_KEY` 길이 32 · `ENV_KAKAO_REST_API_KEY` 길이 0(값 출력 0). 새 APK 뒤 다시(15).

## 효과 수준(effort)

세션 medium 유지. 서브에이전트: critic 2회(정의 기본) · 정답 초안 2 · 조사 1(general-purpose 기본). 올린 곳 없음.
