# HO-2026-1103-rd-phrase-recall-p3-01 — REPORT

- 발주: LC «문구 회상» P3 🔴 Room v3 + `diary_photos` + `Migration(2,3)` + M1~M7 + saveDiary REPLACE→@Update 선행 · 정본 `docs/PHRASE-RECALL-SPEC-2026-11.md` §3.4(:173-231)
- 창: 로컬 메인 체크아웃, 브랜치 `feat/room-v3-diary-photos`, base `1e368a3`(#39)
- effort: 세션 medium(올림 0). critic 정의 `effort: high`.

## 체크리스트(완료 조건 번호)

- [x] 1. `DiaryPhoto` + `version = 3` + entities 등록 + `MIGRATION_2_3`(`data/Migrations.kt`, SQL = 3.json createSql과 글자 대조 일치) + 파괴 폴백 제거 + `3.json` 커밋. diaries createSql v2 = v3(`RoomSchemaExportTest.v2SchemaStaysCommitted_andV3KeepsDiariesUnchanged`), 2.json 변경 0
- [x] 2. M1~M7 — `MigrationTest` 7건(픽스처 확인 1 + M1·M2·M3·M4·M5·M7) + M6 = `RoomSchemaExportTest.migrationDebtIsVisible…`(2.json·3.json 두 파일로 자동 활성). 방식 = 명세 :206 수동 대안(아래 발견 1)
- [x] 3. saveDiary @Update 전환(첫 커밋 `7f8b0dc`) + 편집 회귀 M5(1) · `OcrDiaryEditSaveTest` 1/1 통과. **M5 red 증거:** 저장을 REPLACE insert로 바꾸면 `expected:<[a, b, c]> but was:<[]>`로 실패(변형 되돌림)
- [x] 4. `DiaryPhotoDao`(photosFor·insertAll·deleteByIds·allPaths) + `DiaryPhotoDaoTest` 2건(in-memory)
- [x] 5. HEAD `2e53c9c`: `gradle :app:testDebugUnitTest` exit 0, 31 클래스 195(통과 194 · 건너뜀 1 = P2 프로브) · `gradle :app:verifyRoborazziDebug` exit 0, 195(통과 194 · 건너뜀 1) · 기준 이미지 10장 git 변경 0 · `RoomSchemaExportTest` 5/5 · `GeminiKeyPolicyTest` 5/5
- [x] 6. ADR-005:39 · STAGE2:10 집행 표기 + v2 시절 사실 과거형/행 번호 정정 · ADR-006 리스크 표(새 의존성 실제 0) · 명세 §3.4 «실제(P3)» 1줄 · AGENT_STATUS · WORKLOG
- [x] 7. critic 1차 8/10 조건부 PASS → 조건 2건(그래들 통과 · M5 red 증거) 충족 + 반영 → PR → [DONE] → LC 게이트 대기

## 한 일

- 선행: `DiaryDao.update`(@Update) · `ReadingRepository.saveDiary` = 편집이면 update(0행이면 같은 id로 insert), 새 기록이면 insert.
- 스키마: `DiaryPhoto`(FK CASCADE · diaryId 인덱스) · `MIGRATION_2_3`/`ALL_MIGRATIONS` · `AppDatabase.builder(context, name)`로 빌더 설정 일원화(M4가 같은 설정으로 연다) · 죽은 `onDestructiveMigration` 콜백 삭제.
- 저장 경로: `PhotoEdit`(Unchanged/Set) · `PhotoSlot`(Existing/New) = `data/PhotoEdit.kt`. Set은 한 트랜잭션에서 기존 행 삭제 → 목록 순서로 재삽입(Existing은 id·path·createdAt 유지), 빠진 파일은 커밋 뒤 `DiaryPhotoStore.delete`. VM은 `photoStore` 생성자 기본값 `InternalDiaryPhotoStore`(Set의 «파일 삭제» 요구 때문, 명세 :215). 화면 변경 0.

## 발견

1. **MigrationTestHelper + test assets는 Robolectric에서 동작하지 않았다**(실측 `FileNotFoundException … Missing file: com.example.data.AppDatabase/2.json`, `sourceSets["test"].assets` 추가 상태). 명세 :206 대안(2.json 직접 읽기 3단계)으로 바꿨고, `room-testing`·assets 설정을 되돌려 **새 의존성 0**. ADR-006 리스크 표·명세에 «실제» 표기.
2. critic 1차 8/10 조건부 PASS — 중요 3(ADR-005:39 거짓 문장 · 행 번호 낡음 · 검증 증거) · 경미 6. 반영: 문서 정정, M5 red 증거, 같은 경로 New 시 파일 보존, update 0행 폴백 테스트, KDoc 과장 정정. 남김: v1 DB 설치는 이제 열리지 않음(ADR-006이 수용, 기기 확인 때 user_version 2 확인 권장).
3. 레포 루트에 0바이트 `f.extension`이 23:16에 생김(커밋 전 발견, 커밋 0, 삭제) — 규약 7의 훅 사고와 같은 증상.

## LC·사용자에게 필요한 것

- 사용자(P4 착수 전, LC 판정 3): 오너 기기에서 v2 디버그 설치 위에 v3 디버그를 덮어 설치 → 기록 수 동일(같은 `debug.keystore`). 메인 체크아웃에 `debug.keystore`가 보이지 않아(Glob 0) 이 창은 APK를 만들지 않았다.
- LC 게이트 PASS 뒤 머지.
