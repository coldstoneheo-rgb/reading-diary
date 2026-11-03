# ADR-006: «문구 회상»의 사진 보관과 Room 스키마 동결의 충돌

- 상태: **결정됨(2026-11-03, 오너)** — A-선행 + `diary_photos` 테이블(일기 1 : 사진 N). 결정 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`(이 저장소 밖). 이 문서는 코드 0
- 초안: 2026-11-03(PR #33, `4edd80f`) · 결정 반영: 같은 날(이 PR)
- 상위·관련: [ADR-002](ADR-002-on-device-ocr-rollout.md)(사진 비전송) · [ADR-004](ADR-004-reading-knowledge-base-architecture.md)(3단계 ⑧ `Migration(2,3)`) ·
  [ADR-005](ADR-005-internal-test-and-stage2-boundary.md) §2(스키마 동결)·§3(2단계 경계)·§6 ADR-004-4 · [STAGE2-PLAN 공통 DoD](../STAGE2-PLAN-2026-11.md)(:8-13)
- 명세: [PHRASE-RECALL-SPEC-2026-11](../PHRASE-RECALL-SPEC-2026-11.md)

## 맥락

오너 요청(2026-11-03 10:45): «떠오른 문구·단어 → 언제 어느 책에서 읽었는지 + **당시 촬영 사진**과 함께 핵심 문구 복기».

- 지금 앱은 사진을 기록과 연결해 보관하지 않는다. `Diary`에 사진 필드가 없고(`Entities.kt:57-64`, `2.json` diaries 6열), OCR 파이프라인은 비트맵을 추출 후 회수한다(`ReadingViewModel.kt:540-557`).
  카메라 원본은 `cacheDir` 임시 파일로만 남고(`OcrDiaryScreen.kt:110-118`, 미삭제 — ADR-002:30) 갤러리 사진은 일시 URI뿐이다(`OcrDiaryScreen.kt:153-165`).
- 사진을 «당시 사진»으로 다시 보여 주려면 **① 사진 파일을 앱 내부 저장소에 보관**하고 **② 어느 일기의 사진인지 연결**해야 한다.
  ②의 정석은 Room 스키마 변경 = **Room `version = 3` + `Migration(2,3)`**(초안은 `diaries` 경로 열, 결정은 `diary_photos` 테이블 — 아래 «결정»).

## 충돌

| 규칙 | 위치 | 내용 |
|------|------|------|
| 2단계 공통 DoD | `docs/STAGE2-PLAN-2026-11.md:10` | «**Room 스키마 변경 0** — `version = 2`·엔티티 필드 그대로» |
| 내부 테스트 스키마 동결 | ADR-005 §2(`ADR-005…md:39`) | «내부 테스트 기간 동안 Room 스키마 변경 금지 … 이 금지는 2단계 모든 PR의 DoD에 들어간다» |
| 마이그레이션 시점 | ADR-005 §3(`:54`)·§6 ADR-004-4(`:100`) | `Migration(2,3)`은 **3단계 ⑧**, `fallbackToDestructiveMigration` 제거와 **같은 PR** |
| 현재 빌더 | `AppDatabase.kt:38` | `fallbackToDestructiveMigration()` — 마이그레이션 없이 버전만 올리면 **기록 전체가 조용히 삭제** |

동결의 목적은 **테스터 기록 보호**다(ADR-005 §2 «테스터 기록이 조용히 전부 지워진다»). 따라서 위험의 크기는 «언제 바꾸나»에 달렸다:
첫 AAB 업로드 전이면 새 applicationId(`com.woodpeckerai.readingdiary`)로 깔린 기기는 오너의 디버그 설치뿐이고, 업로드 뒤면 테스터 ≤5명의 실제 기록이 걸린다.
첫 업로드 여부는 레포로 확인할 수 없다 — `AGENT_STATUS.json` `waiting_on`에 «U1~U8 사용자 손»(업로드 = U8)이 열려 있음만 확인(2026-11-03 09:35 기준).

## 선택지

### A. Room v3 + `Migration(2,3)` (정석)

(초안 기본안 — **결정은 `diary_photos` 테이블 변형, 아래 «결정»**) `diaries.photoPath TEXT` nullable 1열 추가, `fallbackToDestructiveMigration` 제거, `3.json` 커밋, 마이그레이션 테스트 M1~M7(명세 §3.4). 사진 파일 이름은 UUID.

- **A-선행:** 첫 AAB 업로드 **전**에 머지. 2단계 DoD에 «ADR-006 예외 1건»을 적고 ADR-005 §2 동결 시작을 «첫 업로드 시점부터»로 명확히 한다.
- **A-후행:** 2단계에는 사진 없는 회상(명세 P1, 스키마 0)만 넣고, 사진 보관(P2~P4)과 v3는 **내부 테스트가 끝난 뒤**. 2단계 DoD는 그대로.
  (초안 1차의 «C. 미룸»은 A-후행과 결과가 같아 critic 지적으로 합쳤다.)

| 리스크 | A-선행 | A-후행 |
|--------|--------|--------|
| 마이그레이션 버그 → 기록 손실 | 오너 디버그 설치만 노출. 단, 파괴 폴백을 지우므로 **실패가 «조용한 삭제» 대신 «앱이 열리지 않음»**으로 바뀐다(되돌림 = 새 빌드) | 테스터 ≤5명 기록 노출. 같은 성질 |
| **되돌릴 수 없는 손실** | 없음 — OCR에 쓴 사진은 첫 업로드부터 남는다(Q-a 결정 = 항상 보관, 체크박스 없음) | **테스트 기간에 찍은 사진은 보관되지 않아, 나중에 기능이 생겨도 그 기록에는 사진을 붙일 수 없다** |
| 요구 충족 | 원문 «당시 촬영 사진» 첫 업로드에 포함, 부제 후보 2·3 사용 가능 | 테스트 기간엔 사진 없음, 부제 후보 2·3 사용 불가 |
| 일정 | 첫 업로드가 P2~P4(초안 추정 9~12h)+검증만큼 늦어짐 — 결정 반영 후 P2~P5(명세 §3.1: P2 2~3h · P3 4~5h · P4 4~5h · P5 5~7h) | 업로드 영향 = P1(3~4h)뿐 |
| 문서 정합 | 개정 대상: ADR-005 §2(`:39`)·STAGE2-PLAN DoD(`:10`) + `Migration(2,3)`을 3단계 ⑧로 둔 ADR-004 실행 단계 ⑧(`:53`, «필요 시 `Migration(2,3)`»)·ADR-005 §3(`:54`, «태그 정식 컬럼·`Migration(2,3)` — 3단계 ⑧»)·§6 ADR-004-4(`:100`) — **2026-11-03 결정 반영 PR에서 개정 완료** | 사진 열이 3단계 ⑧ 묶음에 합류 — ⑧ 서술만 «태그 + 사진 경로»로 개정 |
| 새 의존성 | `androidx.room:room-testing`(테스트 전용, Room 2.7.0 재사용) 1개 — 양쪽 같음. **실제(P3): 0** — Robolectric에서 MigrationTestHelper가 테스트 assets의 스키마 JSON을 찾지 못해(실측) 명세 §3.4 :206 수동 대안으로 갔다 | 〃 |
| 편집 회귀(F6) | `saveDiary`가 `REPLACE` insert(`Daos.kt:63-64`) → 편집 시 경로를 안 넘기면 null로 덮임(열 안). **결정된 테이블 안**에서는 `REPLACE`의 삭제 단계가 CASCADE로 사진 행을 지움 → 선행 `@Update` + `PhotoEdit` Unchanged/Set(명세 §3.4) + M5 테스트로 고정 | 〃 |

### B. 스키마 변경 없이 파일 이름 규약으로 연결

`noBackupFilesDir/diary_photos/{diaryId}-{createdAt}.jpg`. DB는 그대로(`version = 2`), 화면이 일기의 `id`·`createdAt`로 파일을 찾는다.

| 리스크 | 내용 |
|--------|------|
| **엉뚱한 사진 표시** | 파괴 마이그레이션·DB 재생성·일부 복원으로 id가 1부터 다시 매겨지면 남은 파일이 다른 일기에 붙을 수 있다(STAGE2-PLAN ③ 예시 책 표식과 같은 문제, `AppDatabase.kt:54-58`) → `createdAt`을 이름에 넣어 2중 대조로 낮추지만 0은 아님 |
| 저장 순서·코드 비용 | 새 일기는 insert 전에 id가 없다 → `insertDiary`(반환 `Long`) 뒤에 파일을 써야 하고, `saveDiary`가 id를 돌려주도록 바꾸며(`ReadingViewModel.kt:463-477`), 저장소 `save`에 `diaryId`·`createdAt` 인자를 더한다. **사용자가 날짜·시각을 고치면**(`OcrDiaryScreen.kt:209-226`·`:228-243`) 파일 이름을 바꿔야 한다 — 파일 쓰기 성공·DB 실패(또는 반대)의 중간 상태가 A보다 많다 |
| 고아 파일 | CASCADE가 파일을 모른다 → 삭제 연동 + 시작 시 sweep 필수(A도 같음, B는 판정이 더 약함) |
| 이중 정본 | 연결 정보가 DB 밖에 있어 내보내기·마이그레이션·3단계 ⑧ 때 **어차피 열로 옮기는 2차 작업**이 생긴다 |
| 장점 | 2단계 DoD·동결과 충돌 0, 보관을 고른 사진은 첫 업로드부터 남음, 되돌림 = 폴더 삭제 |

## 공통 조건(어느 선택지든)

1. 사진은 **백업에서 자동으로 빠지는 `noBackupFilesDir/diary_photos/`**에 둔다(부속 Q-b 결정: 자동 백업·기기 간 자동 이전 제외 유지). 클라우드 백업에 들어가면 사진이 Google 드라이브로 자동으로 나가 ADR-002와 충돌하고, 자동 백업 용량 한도 **앱당 25MB**를 넘겨 **일기 DB 백업까지 멈춘다.** 저장 루트를 테스트로 고정.
   근거(2026-11-03 확인): Android 공식 문서 [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup) «limited to 25 MB per app» · «If the amount of data is over 25 MB, the system calls `onQuotaExceeded()` and doesn't back up data to the cloud»(`curl` 실측) + 오너 실측: 레퍼런스 앱 본인 백업 zip 50~58MB(5건) — 사진 중심 기록은 한도를 쉽게 넘는다.
2. **자동** 외부 전송 경로 추가 0(`GeminiKeyPolicyTest` 불변). 보관 사진을 자동으로 정밀 분석·공유·백업하지 않는다. 사진이 기기 밖으로 나가는 새 경로는 사용자가 실행하는 P5 zip 백업(SAF)뿐(Q-b 결정).
3. 처리방침 `privacy-policy.html:41`, 데이터 안전 표(INTERNAL-TEST §5)를 **사진 보관 PR과 같은 PR**에서 고친다.
4. 카메라 임시 파일은 보관 복사 후 삭제(ADR-002:30 해소).
5. DB에 경로(`diary_photos` 행)가 있는데 파일이 없으면(자동 백업 복원 — DB는 백업되고 사진은 빠짐) 화면은 «사진 없음(이 기기에 없음)». 깨진 이미지 0, 테스트 1건(명세 §3.5).

## 결정

**결정(2026-11-03, 오너): A-선행, 변형 = `diary_photos` 테이블.** 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`.

- **전제 사실:** 첫 AAB 업로드(U8) 0(오너 확인 2026-11-03) → A-선행 성립(재검토 조건 1 해당 없음).
- **스키마:** `diaries`에 `photoPath` 열 1개가 아니라 새 테이블 **`diary_photos`**(`id` PK · `diaryId` FK → `diaries.id` `ON DELETE CASCADE` · `path` · `sortOrder` · `createdAt`), **일기 1 : 사진 N**.
  Room `version = 3` + `Migration(2,3)`(테이블 `CREATE TABLE` + `diaryId` 인덱스) · `fallbackToDestructiveMigration()` 제거 · `3.json` 커밋 · 마이그레이션 테스트 M1~M7(명세 §3.4).
- **선행 작업:** `saveDiary`의 `REPLACE` insert(`Daos.kt:63-64`) → **`@Update`**(F6). `REPLACE`의 삭제 단계가 `ON DELETE CASCADE`로 사진 행을 지우기 때문(아래 재검토 조건 3이 미리 적어 둔 조건).
- **근거:** 오너 실사용(레퍼런스 앱에서 한 기록에 사진 3장) · 두 번째 마이그레이션 회피(열로 시작하면 N장 요구 때 다시 v4) · 테스터 기록 0인 지금이 가장 싼 시점.
- **기각:**
  - `diaries.photoPath` 열 1개 안 — **기각, 사유: 1기록 N사진 요구 · 2차 마이그레이션 회피.**
  - A-후행 — 테스트 기간 사진 손실(되돌릴 수 없음). B — 이중 정본(3단계에서 열로 옮기는 2차 작업)과 엉뚱한 사진 표시 위험.
- **ADR-005와의 관계:** ADR-005 §2 스키마 동결은 **첫 AAB 업로드 시점부터** 적용되고, 이 ADR이 2단계 DoD «Room 스키마 변경 0»의 **유일한 예외 1건**(`diary_photos`, v3)이다(ADR-005 §2·STAGE2-PLAN 공통 DoD 개정).

**부속 질문 답(2026-11-03, 오너):**
- **Q-a — 체크박스 없음, 항상 보관.** OCR에 쓴 사진은 일기 저장 시 항상 보관한다. 지우려면 편집에서 «사진 지우기».
- **Q-b — 둘로 나눈다.**
  - **자동 백업·기기 간 자동 이전: 제외 유지.** 저장 위치 `noBackupFilesDir/diary_photos/`, 백업 XML(`backup_rules.xml`·`data_extraction_rules.xml`) 변경 0.
  - **사용자가 직접 실행하는 내보내기 zip(DB + 사진) + 가져오기(복원): 사진 포함.** 2단계 ② SAF 마크다운 내보내기(책당 1파일 + 인덱스 노트)는 그대로 두고, «DB+사진 zip 백업 + 가져오기(복원)»를 **P5로 추가**(대체 아님, LC 결정 11-03). 저장 위치는 사용자가 SAF로 고른다(Google 드라이브 포함) → 앱 서버 0·자동 전송 0 유지.
  - 따라서 ADR-002의 «사진은 기기 밖으로 나가지 않는다»는 **«자동으로는 나가지 않는다. 사용자가 직접 내보낼 때만»**으로 1줄 개정(ADR-002 Q1 근거 칸).

**실행 순서(명세 §4 (다)):** P1 회상(스키마 0) → P2 사진 저장소 → P3 Room v3 + `diary_photos` + `Migration(2,3)` + M1~M7 → P4 일기↔사진 연결·카드·편집(유지/교체/삭제) → P5 SAF zip 내보내기 + 가져오기(사진 포함) → 첫 AAB 업로드. STAGE2-PLAN ② 마크다운 내보내기는 P4 뒤·P5 앞(확정 — LC 11-03 판정). 1 PR = 1 의도. 코드는 로컬 창(그래들), 문서 개정은 클라우드.

## 재검토 조건

- ~~첫 AAB 업로드가 이미 끝났다면 A-선행은 성립하지 않는다~~ → 2026-11-03 오너 확인: 업로드 0, 성립. **단, P3 머지 전에 첫 업로드가 먼저 일어나면** 이 결정을 다시 연다(테스터 기록이 v2 파괴 폴백에 노출됨).
- 내부 테스터가 사진 보관으로 저장 공간 문제를 보고하면 → 해상도(긴 변 ≤ 2048)·JPEG 품질·기록당 장수 상한 재검토.
- ~~일기 1건에 사진 여러 장 요구가 생기면 → 열 대신 `diary_photos` 테이블~~ → 결정에 흡수(처음부터 테이블). F6 선행(`saveDiary` → `@Update`)은 P3 완료 조건.
- P5 zip 가져오기에서 스키마 버전이 다른 zip(예: 미래 v4 zip을 v3 앱에)을 받는 요구가 생기면 → 가져오기 호환 규칙 재검토(기본: 앱보다 높은 버전은 거부).
- 사용자가 자동 백업에 사진을 넣어 달라고 요구하면 → 25MB 한도(위 공통 조건 1) 때문에 DB 백업이 멈추므로, 자동 백업이 아니라 P5 zip으로 안내.
