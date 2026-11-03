# «문구 회상» 기능 명세 + 구현 계획 (2026-11)

- 상태: **명세·계획(코드 0).** 사진 보관 방식은 [ADR-006](adr/ADR-006-phrase-recall-photo-storage.md)에서 **[ASK]** — 선택 전에는 §3.2 P1(사진 없는 회상)만 착수 가능.
- 근거 파일:줄은 2026-11-03 origin/main `c0bccd2` 기준.
- 관련: [ADR-002](adr/ADR-002-on-device-ocr-rollout.md)(온디바이스 OCR, 사진 비전송) · [ADR-003](adr/ADR-003-memory-drawer-and-honest-features.md)(기억 서랍, 근거 없는 연결 금지) ·
  [ADR-004](adr/ADR-004-reading-knowledge-base-architecture.md)(3단계 ⑧ `Migration(2,3)`) · [ADR-005](adr/ADR-005-internal-test-and-stage2-boundary.md)(§2 스키마 동결) · [STAGE2-PLAN](STAGE2-PLAN-2026-11.md)(공통 DoD · ② SAF)

## 0. 사실 확인 (명세의 전제)

| # | 사실 | 근거(파일:줄) | 레포와의 차이 |
|---|------|---------------|---------------|
| F1 | **Diary 엔티티에 사진 필드가 없다.** 필드는 `id`·`bookId`·`page`·`selectedText`·`notes`·`createdAt` 6개 | `app/src/main/java/com/example/data/Entities.kt:57-64` · 스키마 정본 `app/schemas/com.example.data.AppDatabase/2.json`(diaries 컬럼 6개, `python3 json.load`로 확인) · `AppDatabase.kt:21` `version = 2` | 없음 |
| F2 | **OCR은 Bitmap만 처리하고 사진을 저장하지 않는다.** 엔진 경계는 `extract(bitmap: Bitmap)`뿐이고, 파이프라인은 URI → 디코드 → 추출 → `recycle()`로 끝난다. 저장 함수 `saveDiary`에는 사진 인자가 없다 | `data/ocr/TextExtractor.kt:9-11` · `ui/viewmodel/ReadingViewModel.kt:540-557`(`runOcrPipeline`, `:552` `cropped.recycle()`) · `ReadingViewModel.kt:463-477`(`saveDiary`) · 호출부 `ui/screens/OcrDiaryScreen.kt:1445-1452` | **있음(작음):** «비저장»은 «기록과 연결해 보관하지 않음»이라는 뜻이다. 카메라 원본은 `cacheDir`에 임시 파일로 만들어지고(`OcrDiaryScreen.kt:110-118`) **지워지지 않는다**(ADR-002:30 «후속 발견»). 갤러리 사진은 `GetContent` URI만 화면 상태에 들고 있다(`OcrDiaryScreen.kt:153-165`, 영구 권한 없음 → 나중에 다시 열 수 없음) |
| F3 | **ADR-002: 사진은 기기 밖으로 나가지 않는다.** | `docs/adr/ADR-002-on-device-ocr-rollout.md:12`(«사진은 어느 쪽도 기기 밖으로 나가지 않음») · 처리방침 `docs/privacy/privacy-policy.html:39` | **있음(범위):** 이 문장은 **기본 경로(ML Kit)** 한정이다. 사용자가 키 등록 + 동의 + 사진마다 확인을 거친 «정밀 분석»은 선택한 사진을 Google로 보낸다(ADR-002:14·:21-22, `privacy-policy.html:42`·`:70`). 문구 회상은 **새 외부 전송 0**으로 이 경계를 그대로 지킨다 |

**새로 드러난 전제(사진을 보관하면 생기는 것):**

- **F4 Auto Backup.** `android:allowBackup="true"`(`AndroidManifest.xml:17`)이고 백업 규칙은 키 저장소만 뺀다(`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).
  사진을 `filesDir`에 그냥 두면 **Google 드라이브 자동 백업으로 기기 밖에 나간다** → F3과 충돌. 자동 백업에는 앱당 용량 한도(25MB로 알고 있음 — **[확인 필요: 기억, 이 세션은 외부 문서를 열지 않음]**)가 있어 사진이 이를 넘기면 **일기 DB 백업까지 멈출 수 있다.**
  → 사진은 백업에서 반드시 빠지는 곳에 둔다(§2.4·§3.3).
- **F5 처리방침 문구.** `privacy-policy.html:41` «사진 자체는 일기에 저장되지 않습니다» — 사진 보관을 켜는 PR이 **같은 PR에서** 고쳐야 한다(`WORKLOG.md:29` learning: «'코드는 바꿨는데 그 코드를 설명하는 문구·단서가 남는' 결함»).
- **F6 일기 저장이 `REPLACE`다.** `Daos.kt:63-64` `@Insert(onConflict = REPLACE)`, 편집도 `saveDiary`가 새 `Diary`를 만들어 insert한다(`ReadingViewModel.kt:465-473`).
  사진 경로를 `Diary` 컬럼에 두면 편집 때 경로를 함께 넘기지 않으면 **null로 덮인다.** 별도 테이블 + `ON DELETE CASCADE`면 REPLACE의 삭제 단계에서 **사진 행이 지워진다.** 어느 설계든 회귀 테스트 필수(§3.4 M5·§3.5).
- **F7 검색 엔진은 이미 둘 있다.** `KeywordLinks.build`(`data/knowledge/KeywordLinks.kt:42-88`)는 **서로 다른 책 2권 이상**에 나온 단어만, 상위 30개(`:45-46`)를 돌려준다 → 한 책에만 있는 문구를 찾는 «회상»에는 단독으로 부족하다.
  자유 문자열 검색은 `BookComparison`(`data/knowledge/BookComparison.kt:18-31` `matchKind`, `:73-106` `compare`)이 구절·메모·제목 부분 일치로 이미 한다(기억 서랍 검색창 `KnowledgeDrawerScreen.kt:94-103`).

## 1. 사용자 원문

> «떠오른 문구·단어 → 언제 어느 책에서 읽었는지 + 당시 촬영 사진과 함께 핵심 문구 복기»
> — 오너, 2026-11-03 10:45 (발주 인라인 인용)

부제 재작성은 이 명세 뒤(§5).

## 2. 기능 명세

### 2.1 한 줄 정의

머릿속에 떠오른 문구·단어 하나로 **내가 남긴 기록**을 찾아, 그 기록이 **어느 책의 몇 쪽**, **언제** 남긴 것인지와 **그때 찍은 페이지 사진**을 함께 보여 준다. 새로 만들어 내는 문장 0(생성 없음, ADR-003 Q2·ADR-004).

### 2.2 사용 흐름

```
[진입] 대시보드 «문구 회상» 버튼  또는  기억 서랍 검색 결과의 «사진과 함께 보기»
   │
   ▼
[검색창] 떠오른 문구·단어 입력 (예: "그리움", "별 하나에")
   │      아래 칩: 공유 단어(KeywordLinks.build 상위) — 탭하면 검색어로
   │      필터 줄: [책 ▾] [기간 ▾] [쪽 ▾]   (입력 없이 필터만으로도 둘러보기 가능)
   ▼
[결과 카드 목록]  최신 기록 순
   ┌──────────────────────────────────────────┐
   │ [사진 썸네일 64dp]  «…별 하나에 [추억]과…»   │ ← 핵심 문구(검색어 강조, 앞뒤 발췌)
   │                    별 헤는 밤 · 윤동주      │ ← 어느 책
   │                    2026.10.21 · p.12       │ ← 언제 · 몇 쪽
   │                    메모: 그때 내 생각 1줄…   │
   └──────────────────────────────────────────┘
   │  (사진이 없는 기록은 썸네일 자리에 «사진 없음» 표시 — 숨기지 않는다)
   ▼
[복기 화면] 카드 탭
   - 위: 그때 찍은 페이지 사진(핀치 확대) · 사진 없으면 이 영역 없음
   - 아래: 구절 전문 · 메모 전문 · 책·저자·날짜·쪽 · «이 기록 편집» · «이 책 보기»
```

### 2.3 검색 규칙 (기존 함수 재사용)

| 구분 | 규칙 | 재사용 |
|------|------|--------|
| 구절·문구 검색 | 검색어(앞뒤 공백 제거, 소문자 비교)가 `selectedText` 또는 `notes`에 **부분 문자열**로 있으면 일치. 책 제목만 맞은 기록도 포함하되 라벨로 구분 | `matchKind`(`BookComparison.kt:18-31`) 그대로 — 라벨 «제목·구절 일치/구절 일치/메모 일치/제목 일치»(`BookComparison.kt:10-15`) |
| 단어 검색(조사 차이 흡수) | 검색어가 공백 없는 한 단어면, 기록의 `KeywordExtractor.extract(…)` 토큰과 **같은 정규형**이면 일치(예: «그리움을» ↔ «그리움이»). 부분 문자열 일치가 이미 있으면 중복 표시 0 | `KeywordExtractor.extract`(`KeywordExtractor.kt:87`) · `#태그`는 `tags`(`:98`) |
| 단어 칩 | 검색창 아래에 `KeywordLinks.build` 결과 상위 N개(기억 서랍과 같은 목록) | `ReadingViewModel.sharedWords`(`:281-282`) |
| 책 필터 | 책 다중 선택. 비우면 전체 | `books` StateFlow |
| 기간 필터 | 전체 · 최근 1개월 · 최근 1년 · 직접 지정(시작~끝 날짜). 기준 = `Diary.createdAt`(사용자가 저장 화면에서 날짜·시각을 고칠 수 있는 값, 날짜 `OcrDiaryScreen.kt:209-226`·시각 `:228-243`) | — |
| 쪽 필터 | `from..to` 정수 범위(책 필터가 1권일 때만 노출 — 여러 책의 쪽 범위는 의미가 없다) | — |
| 정렬 | **`createdAt` 내림차순 → `diaryId` 내림차순**(결정적). 고정 점수·관련도 점수 0(ADR-003 Q5) | — |
| 빈 결과 | «일치하는 기록 0개» + 검색어가 있으면 «다른 표현으로 찾아보세요» 1줄. 거짓 제안 0 | — |

«언제 읽었는지»는 **기록을 남긴 시각**(`createdAt`)이다. 앱은 «읽은 날»을 따로 저장하지 않으므로 화면 라벨은 «기록한 날»로 쓴다(정직 원칙, ADR-003 Q7).

### 2.4 사진 (ADR-006 선택 후)

- **무엇을 보관하나:** OCR에 쓴 **그 사진**(카메라 촬영·갤러리 선택 모두)을, 회전·뒤집기를 적용하고 긴 변 **≤ 2048px**로 줄인 JPEG 1장(`inSampleSize`가 2의 거듭제곱이라 3000px 사진은 1500px가 된다, `BitmapDecoding.kt:23-31`)(`BitmapDecoding.MAX_SIDE`, `BitmapDecoding.kt:17`). 크롭 전 **페이지 전체**를 보관한다(«당시 사진» 복기가 목적 — 크롭 영역만 남기면 앞뒤 맥락이 사라진다).
- **어디에(보수적 기본값):** 앱 내부 저장소 `noBackupFilesDir/diary_photos/` — 다른 앱 접근 불가, 앱 삭제 시 함께 삭제, **클라우드 백업·기기 간 이전 모두에서 자동 제외**(API 24+ 전부, 백업 XML 변경 0). 기기 간 이전에 사진을 넣고 싶다면 `filesDir` + 백업 규칙 제외로 바꿔야 하며, API 24~30은 `backup_rules.xml`이 클라우드·이전을 구분하지 못한다(`minSdk = 24`, `app/build.gradle.kts:15`) — **ADR-006 부속 [ASK] Q-b**.
- **언제:** 일기 «저장»을 누를 때만. OCR 실행·취소·뒤로가기로는 저장 0. 저장 화면에 체크박스 «이 사진을 기록과 함께 보관» — 오너 답 전 기본값 **끔**(opt-in, **ADR-006 부속 [ASK] Q-a**).
- **지우기:** 일기 삭제·책 삭제(CASCADE)·책장 삭제·예시 책 지우기(`deleteSampleBooks`, `ReadingViewModel.kt:173`) 시 해당 사진 파일도 삭제. 앱 시작 시 고아 파일 정리(sweep) 1회 — 누락된 삭제 경로도 여기서 걷힌다.
- **경로는 있는데 파일이 없을 때(dangling):** 백업 복원(DB는 백업되고 사진은 빠짐)·저장 공간 정리 등으로 생긴다. 화면은 **«사진 없음(이 기기에 없음)»**으로 표시하고 깨진 이미지를 그리지 않는다. DB 경로는 지우지 않는다(되돌리기 쉬움).
- **편집 모드:** 보관된 사진이 있으면 보여 주고 «사진 바꾸기 / 사진 지우기». 저장 시 사진 처리는 **유지·교체·삭제 3가지**로 구분한다(null 하나로 «유지»와 «삭제»를 겸하지 않는다, §3.4). 지금처럼 «사진 없이 열기»(`OcrDiaryScreen.kt:167-176`)는 사진이 없을 때만.
- **외부 전송 0.** 보관 사진을 정밀 분석·공유 카드로 자동으로 보내는 경로 0. 정밀 분석은 지금처럼 사용자가 그 화면에서 사진마다 확인할 때만(`GeminiKeyPolicyTest` 참조 범위 불변).
- **기존 기록:** 이 기능 이전에 남긴 기록에는 사진이 없다(애초에 보관하지 않았으므로 복구 불가) — 카드에 «사진 없음».

### 2.5 하지 않는 것

생성형 요약·추천 0 · 사진 OCR 재실행으로 검색 0(검색 대상은 저장된 텍스트뿐) · 사진 외부 업로드·공유 0 · 사진 원본 해상도 보관 0 · 내보내기(SAF)에 사진 포함 0(§5 순서 결정 뒤 별도 판단).

## 3. 구현 계획

### 3.1 단계 (1 PR = 1 의도, CLAUDE.md)

| 단계 | 의도 | 스키마 | ADR-006 의존 | 추정 |
|------|------|--------|--------------|------|
| **P1** | 사진 없는 «문구 회상»: 검색 순수 함수 + 화면 + 내비 | 0 | 없음 — 2단계 DoD 안에서 착수 가능 | 3~4h |
| **P2** | 사진 저장소 경계 `DiaryPhotoStore`(`noBackupFilesDir`) + 고아 정리 | 0 | 있음(파일 이름 규약·저장 순서가 선택지마다 다름) | 2~3h |
| **P3** 🔴 | Room `version = 3` + `Migration(2,3)` + `fallbackToDestructiveMigration` 제거 + `3.json` + 마이그레이션 테스트 | **변경** | 선택지 A(선행·후행)만 | 3~4h |
| **P4** | OCR 저장 화면 연결 + 편집 모드 사진 + 회상 카드 썸네일·복기 화면 사진 + 삭제 연동 + **처리방침·데이터 안전·스토어 문구** | 0 | 있음 | 4~5h |

순서: P1 → (ADR-006 선택) → A-선행이면 바로 P2 → P3 → P4 · A-후행이면 2단계는 P1까지, P2 → P3 → P4는 내부 테스트 뒤(P3의 `PhotoEdit` 파일 삭제가 P2의 `DiaryPhotoStore`를 쓰므로 P2가 먼저) · B면 P2 → P4(B 전용 저장 순서, §3.3). 합계 12~16h(검증·critic 포함, 빌드 시간 제외).
P3는 UI와 섞지 않는다(CLAUDE.md «UI + Room 스키마는 PR을 나눈다»).

### 3.2 P1 — 사진 없는 문구 회상

**새 순수 함수:** `app/src/main/java/com/example/data/knowledge/PhraseRecall.kt`

```kotlin
data class RecallFilter(
    val bookIds: Set<Int> = emptySet(),     // 비면 전체
    val fromMillis: Long? = null,           // createdAt 하한(포함)
    val toMillis: Long? = null,             // createdAt 상한(포함)
    val pageRange: IntRange? = null         // bookIds.size == 1 일 때만 적용
)

data class RecallHit(
    val diaryId: Int, val bookId: Int, val bookTitle: String, val author: String,
    val page: Int, val createdAt: Long,
    val excerpt: String,          // 검색어 주변 발췌(앞뒤 각 40자, 잘리면 …)
    val highlight: IntRange?,     // excerpt 안 검색어 위치(없으면 null — 단어 정규형 일치·제목 일치)
    val note: String,
    val matchKind: MatchKind?     // 검색어가 비면 null(필터만으로 둘러보기)
)

object PhraseRecall {
    fun search(query: String, filter: RecallFilter, books: List<Book>, diaries: List<Diary>): List<RecallHit>
    fun excerpt(text: String, query: String, radius: Int = 40): Pair<String, IntRange?>
}
```

**건드릴 파일:** 새 `data/knowledge/PhraseRecall.kt` · 새 `ui/screens/PhraseRecallScreen.kt`(stateless `PhraseRecallContent(books, diaries, sharedWords, initialQuery, initialFilter, onOpen)` — `KnowledgeDrawerContent` 관례, testTag `phraserecall_search_field`·`phraserecall_filter_book`·`phraserecall_filter_period`·`phraserecall_filter_page`·`phraserecall_result`) ·
`MainActivity.kt`(`Screen` sealed에 `PhraseRecall` 추가) · `ui/viewmodel/ReadingViewModel.kt`(내비 함수 1개, 상태 추가 0 — 계산은 화면 `remember`) · `ui/screens/DashboardScreen.kt`(진입 버튼) · 선택: `KnowledgeDrawerScreen.kt` 검색 결과에서 같은 검색어로 이동 링크.

**테스트:** 새 `PhraseRecallTest.kt`(JVM) — 빈 검색어+빈 필터 = 전체 최신순 · 부분 일치 구절/메모/제목 라벨 · 조사 차이(«그리움을»↔«그리움이») 일치 · 중복 0 · 책/기간/쪽 필터 각각과 조합 · 쪽 필터는 책 2권 이상이면 무시 · 정렬 결정성 · 책이 지워진 일기 제외 · `excerpt` 경계(앞·뒤 잘림, 검색어 없음).
Roborazzi 2장: `phrase_recall_results.png`, `phrase_recall_empty.png`(기존 기준 불변 — `verifyRoborazziDebug`).
**완료 조건:** 위 테스트 통과 · `RoomSchemaExportTest`·`GeminiKeyPolicyTest`·`HonestCopyTest` 통과 · 새 네트워크 호스트 0 · critic PASS.

### 3.3 P2 — 사진 저장소 경계

**새 파일:** `app/src/main/java/com/example/data/photo/DiaryPhotoStore.kt`

```kotlin
interface DiaryPhotoStore {
    /** 디코드·회전·축소한 JPEG를 앱 내부 저장소에 쓰고 상대 경로를 돌려준다. 실패 시 null(일기 저장은 계속). */
    suspend fun save(source: Uri, rotationDegrees: Float, flipped: Boolean): String?
    fun fileFor(relativePath: String): File?   // 저장소 밖 경로(../ 등)는 null
    suspend fun delete(relativePath: String)
    /** 참조되지 않는 파일 삭제. 반환 = 지운 개수. */
    suspend fun sweep(referenced: Set<String>): Int
}
class InternalDiaryPhotoStore(context: Context) : DiaryPhotoStore   // noBackupFilesDir/diary_photos/
```

- `fileFor`는 경로 탈출(`../` 등)**과 파일 부재** 모두 null — 화면은 null이면 «사진 없음»(dangling 처리, §2.4).

- 파일 이름: **UUID.jpg**(선택지 A — DB가 경로를 가짐, 저장 순서 = 파일 쓰기 → `saveDiary(photo = Replace(경로))`) /
  **`{diaryId}-{createdAt}.jpg`**(선택지 B — DB 없이 대조). **B는 위 시그니처로 구현할 수 없다:** 새 일기는 insert 전에 id가 없으므로 순서가 `insertDiary`(반환 `Long`, `ReadingRepository.kt:34`) → id를 받아 파일 쓰기여야 하고,
  `saveDiary`가 id를 돌려주도록 바꾸고(`ReadingViewModel.kt:463-477`는 지금 `onComplete: () -> Unit`), `save(source, …, diaryId, createdAt)`로 인자를 늘리며, 날짜를 고치면 파일 이름도 바꿔야 한다(ADR-006 B 리스크).
- 디코드는 기존 `BitmapDecoding.decodeDownsampled`(`BitmapDecoding.kt:65`) 재사용, JPEG 품질 85(예상 0.3~1MB/장 — 실측 아님, P2 PR에서 기기 1장 실측해 기록).
- 카메라 임시 파일은 보관 복사 후(또는 보관하지 않기로 했을 때) **삭제** → ADR-002:30 후속 발견 해소.
- 백업 제외: 기본값 `noBackupFilesDir`이면 XML 변경 0. ADR-006 Q-b에서 «기기 간 이전 포함»을 고르면 `filesDir/diary_photos/` + `backup_rules.xml` `<exclude domain="file" path="diary_photos/"/>` + `data_extraction_rules.xml` `<cloud-backup>` 같은 줄 — API 24~30에서는 이전에서도 빠진다(구분 불가).
- **테스트:** 새 `DiaryPhotoStoreTest.kt`(Robolectric) — 저장 후 파일 존재·긴 변 ≤ 2048 · 저장 루트가 `noBackupFilesDir` 아래 · 경로 탈출 거부 · 파일 부재 시 `fileFor` null · 삭제 · sweep이 참조된 파일을 지우지 않음 · 디코드 실패 시 null.
  Q-b로 `filesDir`을 고르면 `GeminiKeyPolicyTest.backupRules_excludeSecureKeyPrefs` 옆에 **`backupRules_excludeDiaryPhotos`**(두 XML 파싱)를 추가.
- **완료 조건:** 위 통과 · 사진이 클라우드 백업에 들어가는 경로 0 · 새 권한 0 · 새 의존성 0.

### 3.4 P3 🔴 — Room version 3 + 마이그레이션 (선택지 A일 때만)

**스키마 변경(제안 — 열 1개 추가):** `diaries`에 `photoPath TEXT` **nullable**, 기본값 없음(null = 사진 없음).

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `diaries` ADD COLUMN `photoPath` TEXT")
    }
}
```

별도 테이블(`diary_photos`, 일기 1건에 사진 여러 장)은 F6 때문에 `saveDiary`를 `@Update`로 바꾸는 선행 작업이 필요하고, 원문 요구(«당시 촬영 사진»)는 1장이면 충분하다 → 열 1개를 기본안으로 하고 ADR-006에 대안으로만 적는다.

**첫 단계(실패하는 테스트 먼저):** `MigrationTest`가 `2.json`을 읽어 v2 DB를 만드는 것만 하는 테스트를 먼저 올려, Robolectric 단위 테스트에서 `MigrationTestHelper`가 스키마 JSON을 찾는지 확인한다.
`sourceSets["test"].assets.srcDir(...)`이 단위 테스트 병합 assets에 들어가는지는 **미검증**(`isIncludeAndroidResources = true`는 이미 있음, `app/build.gradle.kts:63`). 안 되면 대안 = 테스트가 `app/schemas/…/2.json`을 파일로 직접 읽어 v2 DB를 손수 만든 뒤 `Room.databaseBuilder(...).addMigrations(MIGRATION_2_3)`로 연다 — 필수 3단계: ① `createSql`의 `${TABLE_NAME}` 자리표시자를 실제 테이블 이름으로 치환 ② `setupQueries`대로 `room_master_table`을 만들고 v2 `identityHash` 삽입 ③ `PRAGMA user_version = 2`. 하나라도 빠지면 Room이 v2로 인식하지 못해 엉뚱한 경로를 탄다.
`debug`/`main` 소스셋 assets에 스키마를 넣는 방법은 **금지**(APK에 들어간다).

**건드릴 파일:** `data/Entities.kt`(`Diary.photoPath: String? = null`) · `data/AppDatabase.kt`(`version = 3`, `.addMigrations(MIGRATION_2_3)`, **`fallbackToDestructiveMigration()` 제거**(`:38`), `:12-20` 주석 갱신) · 새 `data/Migrations.kt` ·
`app/schemas/com.example.data.AppDatabase/3.json`(컴파일로 생성 후 커밋) · `app/build.gradle.kts`(테스트가 스키마 JSON을 읽도록 `sourceSets["test"].assets.srcDir("$projectDir/schemas")`, `testImplementation(libs.androidx.room.testing)`) ·
`gradle/libs.versions.toml`(`androidx-room-testing`, 버전 = `roomRuntime` `2.7.0` 재사용 — **새 의존성 1개**, 현재 카탈로그 grep 0) ·
`data/Daos.kt`(`DiaryDao`에 id 한 건 조회 `getByIdOneShot` — 지금 없음, `Daos.kt:55-68`) · `data/ReadingRepository.kt`(그 조회 노출) ·
`ui/viewmodel/ReadingViewModel.kt`(`saveDiary`에 `photo: PhotoEdit = PhotoEdit.Keep` 인자 — `sealed interface PhotoEdit { object Keep; data class Replace(val path: String); object Remove }`. `Keep`이면 기존 행을 조회해 그 `photoPath`를 그대로 넣는다(F6 REPLACE 대응), `Remove`면 null + 파일 삭제, `Replace`면 새 경로 + 옛 파일 삭제) ·
`app/src/test/java/com/example/RoomSchemaExportTest.kt`(identityHash·컬럼 단언을 v3로).

**마이그레이션 테스트 계획(새 `app/src/test/java/com/example/MigrationTest.kt`, Robolectric + `MigrationTestHelper`):**

| # | 시나리오 | 단언 |
|---|----------|------|
| M1 | v2 DB를 `2.json`으로 만들고 책장 2·책 3·일기 5(한글·이모지·빈 메모·`createdAt` 과거값 포함) 삽입 → `runMigrationsAndValidate(3, MIGRATION_2_3)` | 검증 통과(스키마 = `3.json`) |
| M2 | M1 뒤 행 대조 | 일기 5행의 `id·bookId·page·selectedText·notes·createdAt` **바이트 동일**, `photoPath` 전부 null |
| M3 | 외래키 | `PRAGMA foreign_key_check` 결과 0행 · 책 삭제 → 그 책 일기 CASCADE 삭제 유지 · `ON DELETE CASCADE` 2개 유지 |
| M4 | 실제 빌더 경로 | M1 파일을 `AppDatabase.getDatabase`와 같은 빌더 설정(마이그레이션 등록)으로 열어 `diaryDao().getAllDiaries().first()` 5건 |
| M5 | 편집 회귀(F6) | `photoPath = "a.jpg"`인 일기를 `saveDiary(id = 그 id, photo = Keep)`로 편집 → `photoPath` 그대로 · `Remove` → null · `Replace("b.jpg")` → `"b.jpg"` (세 경우가 서로 구분됨) |
| M6 | 파괴 폴백 부재 | 기존 `migrationDebtIsVisible_whenMoreThanOneSchemaVersionExists`(`RoomSchemaExportTest.kt:81-91`)가 `2.json`+`3.json` 두 파일로 **자동 활성화** → `fallbackToDestructiveMigration` 문자열 0 |
| M7 | 빈 DB | v2 빈 DB(책장만) → 3 마이그레이션 통과 |

**완료 조건:** M1~M7 통과 · `testDebugUnitTest` 전체 통과 · `verifyRoborazziDebug` 기준 불변(UI 0) · **오너 기기 수동 확인 1회**(v2 디버그 설치 위에 v3 디버그 덮어 설치 → 기록 수 동일, 같은 `debug.keystore`) · critic PASS · ADR-006 결정대로 같은 PR에서 문서 개정 — ADR-005 §2(`:39`)·STAGE2-PLAN 공통 DoD(`:10`), 그리고 `Migration(2,3)`을 «3단계 태그 컬럼»으로 적은 ADR-004 «실행 단계» ⑧(`:53`)·ADR-005 §3(`:54`)·§6 ADR-004-4(`:100`)(태그 컬럼은 이후 `Migration(3,4)`).

### 3.5 P4 — 화면 연결

**건드릴 파일:** `ui/screens/OcrDiaryScreen.kt`(체크박스 testTag `ocrdiary_keep_photo`, 편집 모드 사진 표시·바꾸기·지우기 — 화면은 의도만 넘긴다) ·
`ui/viewmodel/ReadingViewModel.kt`(**사진 쓰기는 ViewModel 스코프에서**: `saveDiaryWithPhoto(…, photo: PhotoInput)` — `sealed interface PhotoInput { object Keep; object Remove; data class New(val source: Uri, val rotation: Float, val flipped: Boolean) }`(null·불리언 조합으로 상태를 겸하지 않는다) → `New`면 `DiaryPhotoStore.save` 후 `PhotoEdit.Replace(경로)`, 나머지는 그대로 `saveDiary(photo = …)` — 디코드를 ViewModel에 두는 기존 관례(`ReadingViewModel.kt:506-509`)와 같아 회전·뒤로가기에도 끊기지 않는다 · `deleteDiary`·`deleteBook`·`deleteBookcase`·`deleteSampleBooks`에서 사진 삭제 — 책·책장은 CASCADE 전에 해당 일기들의 경로를 먼저 조회, 앱 시작 시 `sweep` 1회) ·
`ui/screens/PhraseRecallScreen.kt`(썸네일 — Coil `AsyncImage(model = File)`, 로컬 파일만 · 복기 화면 확대) ·
`docs/privacy/privacy-policy.html:41`(«사진 자체는 일기에 저장되지 않습니다» → «보관을 선택한 사진은 앱 내부 저장소에만 남고 자동 백업에서 제외되며, 기록·책을 지우면 함께 지워집니다») ·
`docs/store-listing/INTERNAL-TEST-2026-11.md`(§5 데이터 안전 표에 «N8 사진 보관(기기 안) — 전송 0» 행, §2 문안) · 앱 안 처리방침 요약(`PolicyCopyTest` 대상 문구가 사진을 언급하면 함께).
**테스트:** `OcrDiaryEditSaveTest` 확장(편집 저장 시 사진 유지·교체·지우기) · Robolectric 삭제 연동(일기·책·책장·예시 책 삭제 후 파일 0, 다른 일기 파일 유지) · **dangling**(경로는 있고 파일은 없는 일기 → 카드·복기 화면이 «사진 없음(이 기기에 없음)», 크래시 0) · `HonestCopyTest`에 «사진 자체는 일기에 저장되지 않습니다» 문구가 `app/src/main`에 없음 · Roborazzi 회상 카드 썸네일 1장(테스트 리소스 이미지).
**완료 조건:** 위 통과 · dangling 표시 규칙 적용 · 사진을 보관하지 않은 기록은 지금과 같은 흐름 · 외부 전송 경로 추가 0(`GeminiKeyPolicyTest` 불변) · 오너 기기에서 촬영 → 저장 → 회상 카드 사진 확인 1회.

## 4. 2단계 ② SAF 내보내기와의 순서 — [ASK]

| 선택지 | 장점(1줄) | 단점(1줄) |
|--------|-----------|-----------|
| (가) **SAF ② 먼저** → 문구 회상 P1~P4 | 2단계 계획(STAGE2-PLAN 권장 순서 5번, AAB 직전)과 스키마 동결을 그대로 지켜 첫 업로드가 가장 빠르다 | 오너가 방금 요청한 기능이 가장 늦고, 내보내기에 사진 포함 여부를 나중에 다시 설계해야 할 수 있다 |
| (나) **회상 P1(사진 없음) 먼저** → SAF ② → 사진(P2~P4) | 스키마 0인 절반을 바로 쓰게 하면서 SAF 일정도 지킨다 | 사진 없는 회상은 원문 요구의 절반이고, 그동안 찍은 사진은 보관되지 않아 나중에 붙일 수 없다 |
| (다) **회상 전체(사진 포함) 먼저** → SAF ② | 첫 업로드에 핵심 기능이 들어가고 SAF가 처음부터 사진 포함/제외를 정해 설계된다 | 가장 위험한 변경 두 개(마이그레이션·파일 쓰기)가 연달아 오고, ADR-006 A-선행(또는 B) 선택과 ADR-005 §2 개정이 전제다 |

## 5. 스토어 부제 후보(새 기능 포함) — [ASK]

현재 후보(ADR-005 §6 ADR-003-6b, `INTERNAL-TEST-2026-11.md:26`): «밑줄이 책과 책을 잇는 독서 다이어리»(20자).

| # | 후보 | 글자 수(공백 포함) | 강조점 |
|---|------|--------------------|--------|
| 1 | 떠오른 한 문장, 언제 어느 책에 남긴 밑줄인지 찾아 주는 독서 다이어리 | 40 | 원문 요구(회상) — «읽었는지»가 아니라 «남긴 밑줄»: 앱은 읽은 날을 저장하지 않는다(§2.3) |
| 2 | 밑줄과 그날의 페이지 사진으로 다시 만나는 독서 다이어리 | 31 | 사진 복기 |
| 3 | 밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리 | 40 | 기존 후보(연결) + 회상 |

- 짧은 설명 한도 80자 이내(`INTERNAL-TEST-2026-11.md:24`) — 셋 다 충족(글자 수는 `python3 len()` 실측).
- **정직 조건:** ① 2·3은 사진 보관(P4)이 머지된 빌드에서만 쓸 수 있다. P4 전 업로드라면 1(사진 언급 없음) 또는 기존 후보. ② 어느 후보도 «읽은 날»을 약속하지 않는다 — 앱이 아는 것은 기록한 날(`createdAt`)뿐.

## 6. 남은 질문 요약

- [ASK] ADR-006 선택(A-선행 / A-후행 / B) — [ADR-006](adr/ADR-006-phrase-recall-photo-storage.md)
- [ASK] ADR-006 부속 Q-a «이 사진을 기록과 함께 보관» 기본값(답 전 = 끔) · Q-b 기기 간 이전에 사진 포함(답 전 = 제외, `noBackupFilesDir`)
- [ASK] §4 SAF 순서 (가)/(나)/(다)
- [ASK] §5 부제 1/2/3/기존
- 막지 않는 기본값(프라이버시와 무관한 것만): 진입점 = 대시보드 버튼 + 기억 서랍 링크
