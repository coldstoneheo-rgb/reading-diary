# «문구 회상» 기능 명세 + 구현 계획 (2026-11)

- 상태: **명세·계획(코드 0) — 오너 결정 4건 반영(2026-11-03).** 사진 보관 = [ADR-006](adr/ADR-006-phrase-recall-photo-storage.md) **결정됨**(A-선행 + `diary_photos` 테이블) · 순서 §4 (다) · 부제 §5 후보 3. 결정 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`.
- 구현 순서: P1 → P2 → P3 → P4 → (② SAF 마크다운(② 위치는 결정 3 목록에 없어 (다)의 정의 «회상 전체 → SAF ②»로 둔 **해석 — 오너 확인 대상**)) → P5 → 첫 AAB 업로드. 코드는 로컬 창(그래들), 이 문서 개정은 클라우드.
- 근거 파일:줄은 2026-11-03 origin/main `c0bccd2` 기준(결정 반영 개정은 `4edd80f` 기준 — 앱 코드는 두 커밋 사이 변경 0).
- 관련: [ADR-002](adr/ADR-002-on-device-ocr-rollout.md)(온디바이스 OCR, 사진 비전송) · [ADR-003](adr/ADR-003-memory-drawer-and-honest-features.md)(기억 서랍, 근거 없는 연결 금지) ·
  [ADR-004](adr/ADR-004-reading-knowledge-base-architecture.md)(3단계 ⑧ `Migration(2,3)`) · [ADR-005](adr/ADR-005-internal-test-and-stage2-boundary.md)(§2 스키마 동결) · [STAGE2-PLAN](STAGE2-PLAN-2026-11.md)(공통 DoD · ② SAF)

## 0. 사실 확인 (명세의 전제)

| # | 사실 | 근거(파일:줄) | 레포와의 차이 |
|---|------|---------------|---------------|
| F1 | **Diary 엔티티에 사진 필드가 없다.** 필드는 `id`·`bookId`·`page`·`selectedText`·`notes`·`createdAt` 6개 | `app/src/main/java/com/example/data/Entities.kt:57-64` · 스키마 정본 `app/schemas/com.example.data.AppDatabase/2.json`(diaries 컬럼 6개, `python3 json.load`로 확인) · `AppDatabase.kt:21` `version = 2` | 없음 |
| F2 | **OCR은 Bitmap만 처리하고 사진을 저장하지 않는다.** 엔진 경계는 `extract(bitmap: Bitmap)`뿐이고, 파이프라인은 URI → 디코드 → 추출 → `recycle()`로 끝난다. 저장 함수 `saveDiary`에는 사진 인자가 없다 | `data/ocr/TextExtractor.kt:9-11` · `ui/viewmodel/ReadingViewModel.kt:540-557`(`runOcrPipeline`, `:552` `cropped.recycle()`) · `ReadingViewModel.kt:463-477`(`saveDiary`) · 호출부 `ui/screens/OcrDiaryScreen.kt:1445-1452` | **있음(작음):** «비저장»은 «기록과 연결해 보관하지 않음»이라는 뜻이다. 카메라 원본은 `cacheDir`에 임시 파일로 만들어지고(`OcrDiaryScreen.kt:110-118`) **지워지지 않는다**(ADR-002:30 «후속 발견»). 갤러리 사진은 `GetContent` URI만 화면 상태에 들고 있다(`OcrDiaryScreen.kt:153-165`, 영구 권한 없음 → 나중에 다시 열 수 없음) |
| F3 | **ADR-002: 사진은 기기 밖으로 나가지 않는다.** | `docs/adr/ADR-002-on-device-ocr-rollout.md:12`(«사진은 어느 쪽도 기기 밖으로 나가지 않음» — 2026-11-03 개정 «**자동으로는** 나가지 않음 — 사용자가 직접 내보낼 때만») · 처리방침 `docs/privacy/privacy-policy.html:39` | **있음(범위):** 이 문장은 **기본 경로(ML Kit)** 한정이다. 사용자가 키 등록 + 동의 + 사진마다 확인을 거친 «정밀 분석»은 선택한 사진을 Google로 보낸다(ADR-002:14·:21-22, `privacy-policy.html:42`·`:70`). 문구 회상은 **새 자동 외부 전송 0(사용자 실행 P5 zip만 예외)**으로 이 경계를 그대로 지킨다 |

**새로 드러난 전제(사진을 보관하면 생기는 것):**

- **F4 Auto Backup.** `android:allowBackup="true"`(`AndroidManifest.xml:17`)이고 백업 규칙은 키 저장소만 뺀다(`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).
  사진을 `filesDir`에 그냥 두면 **Google 드라이브 자동 백업으로 기기 밖에 나간다** → F3과 충돌. 자동 백업에는 **앱당 25MB** 한도가 있어 사진이 이를 넘기면 **일기 DB 백업까지 멈춘다**(확인 2026-11-03: [Android 공식 문서](https://developer.android.com/identity/data/autobackup) «over 25 MB … doesn't back up data to the cloud» `curl` 실측 + 오너 실측 레퍼런스 앱 백업 zip 50~58MB).
  → 사진은 백업에서 반드시 빠지는 곳에 둔다(§2.4·§3.3).
- **F5 처리방침 문구.** `privacy-policy.html:41` «사진 자체는 일기에 저장되지 않습니다» — 사진 보관을 켜는 PR이 **같은 PR에서** 고쳐야 한다(`WORKLOG.md:29` learning: «'코드는 바꿨는데 그 코드를 설명하는 문구·단서가 남는' 결함»).
- **F6 일기 저장이 `REPLACE`다.** `Daos.kt:63-64` `@Insert(onConflict = REPLACE)`, 편집도 `saveDiary`가 새 `Diary`를 만들어 insert한다(`ReadingViewModel.kt:465-473`).
  사진 경로를 `Diary` 컬럼에 두면 편집 때 경로를 함께 넘기지 않으면 **null로 덮인다.** 별도 테이블 + `ON DELETE CASCADE`면 REPLACE의 삭제 단계에서 **사진 행이 지워진다.** → 결정(ADR-006)은 별도 테이블이므로 **편집 저장을 `@Update`로 바꾸는 것이 P3의 선행 조건**이다(§3.4, 회귀 테스트 M5).
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

### 2.4 사진 (ADR-006 결정: `diary_photos` 테이블 · 항상 보관)

- **무엇을 보관하나:** OCR에 쓴 **그 사진**(카메라 촬영·갤러리 선택 모두)을, 회전·뒤집기를 적용하고 긴 변 **≤ 2048px**로 줄인 JPEG(`inSampleSize`가 2의 거듭제곱이라 3000px 사진은 1500px가 된다, `BitmapDecoding.kt:23-31`)(`BitmapDecoding.MAX_SIDE`, `BitmapDecoding.kt:17`). 크롭 전 **페이지 전체**를 보관한다(«당시 사진» 복기가 목적 — 크롭 영역만 남기면 앞뒤 맥락이 사라진다).
- **몇 장:** 일기 1건 : 사진 **N장**(`diary_photos` 행 N개, `sortOrder` 순). 새 기록은 OCR에 쓴 사진 1장으로 시작하고, 편집에서 사진을 더하거나 빼거나 바꾼다(§3.4 `PhotoEdit`). 기록당 장수 상한은 P4 PR에서 근거 1줄과 함께 정한다.
- **어디에:** 앱 내부 저장소 `noBackupFilesDir/diary_photos/` — 다른 앱 접근 불가, 앱 삭제 시 함께 삭제, **클라우드 자동 백업·기기 간 자동 이전 모두에서 제외**(API 24+ 전부, 백업 XML 변경 0 — ADR-006 Q-b 결정). 기기 밖으로 사진이 나가는 길은 **사용자가 직접 실행하는 zip 내보내기(P5, §3.6)뿐**이다.
- **언제:** 일기 «저장»을 누를 때 **항상** 보관한다(체크박스 없음 — ADR-006 Q-a 결정). OCR 실행·취소·뒤로가기로는 저장 0.
- **지우기:** 편집에서 «사진 지우기»(사진별). 일기 삭제·책 삭제(CASCADE)·책장 삭제·예시 책 지우기(`deleteSampleBooks`, `ReadingViewModel.kt:173`) 시 해당 사진 파일도 삭제(행은 `ON DELETE CASCADE`로 함께 삭제). 앱 시작 시 고아 파일 정리(sweep) 1회 — 누락된 삭제 경로도 여기서 걷힌다.
- **경로는 있는데 파일이 없을 때(dangling):** Android 자동 백업 복원(DB는 백업되고 사진은 빠짐)·저장 공간 정리 등으로 생긴다. 화면은 **«사진 없음(이 기기에 없음)»**으로 표시하고 깨진 이미지를 그리지 않는다. `diary_photos` 행은 지우지 않는다(P5 zip 가져오기로 파일이 돌아오면 다시 보인다).
- **편집 모드:** 보관된 사진 N장을 보여 주고 사진마다 **유지·교체·삭제**, 그리고 «사진 추가». 저장 시 사진 처리는 «변경 없음»과 «목록 지정»을 구분한다(null 하나로 «유지»와 «삭제»를 겸하지 않는다, §3.4). 지금처럼 «사진 없이 열기»(`OcrDiaryScreen.kt:167-176`)는 사진이 없을 때만.
- **외부 전송 0(자동).** 보관 사진을 정밀 분석·공유 카드로 자동으로 보내는 경로 0. 정밀 분석은 지금처럼 사용자가 그 화면에서 사진마다 확인할 때만(`GeminiKeyPolicyTest` 참조 범위 불변). zip 내보내기는 사용자가 실행하고 저장 위치(SAF)를 고를 때만 — 앱 서버 0.
- **기존 기록:** 이 기능 이전에 남긴 기록에는 사진이 없다(애초에 보관하지 않았으므로 복구 불가) — 카드에 «사진 없음».

### 2.5 하지 않는 것

생성형 요약·추천 0 · 사진 OCR 재실행으로 검색 0(검색 대상은 저장된 텍스트뿐) · 사진 **자동** 업로드·공유 0(자동 백업·기기 간 자동 이전 포함) · 사진 원본 해상도 보관 0 · ② SAF **마크다운** 내보내기에 사진 포함 0(텍스트 노트 그대로).
**사진이 포함되는 것(개정 2026-11-03, ADR-006 Q-b):** 사용자가 직접 실행하는 **P5 zip 백업(DB + 사진) + 가져오기(복원)**(§3.6).

## 3. 구현 계획

### 3.1 단계 (1 PR = 1 의도, CLAUDE.md)

| 단계 | 의도 | 스키마 | ADR-006 의존 | 추정 |
|------|------|--------|--------------|------|
| **P1** | 사진 없는 «문구 회상»: 검색 순수 함수 + 화면 + 내비 | 0 | 없음 | 3~4h |
| **P2** | 사진 저장소 경계 `DiaryPhotoStore`(`noBackupFilesDir`) + 고아 정리 | 0 | 있음(결정: UUID 파일 이름, DB가 경로 보유) | 2~3h |
| **P3** 🔴 | 선행 `saveDiary` `REPLACE` → `@Update`(F6) + Room `version = 3` + **`diary_photos` 테이블** + `Migration(2,3)`(CREATE TABLE + 인덱스) + `fallbackToDestructiveMigration` 제거 + `3.json` + 마이그레이션 테스트 M1~M7 | **변경** | 있음(A-선행 결정, ADR-005 §2 예외 1건) | 4~5h |
| **P4** | OCR 저장 시 사진 항상 보관 + 편집 모드 사진 N장(유지/교체/삭제/추가) + 회상 카드 썸네일·복기 화면 사진 + 삭제 연동 + **처리방침·데이터 안전·스토어 문구** | 0 | 있음 | 4~5h |
| **P5** | **SAF zip 백업(DB + 사진) + 가져오기(복원)** — 사용자가 실행·저장 위치 선택(Google 드라이브 포함). ② 마크다운 내보내기와 별개(대체 아님) | 0(v3 그대로) | 있음(Q-b 결정) | 5~7h |

순서(결정 §4 (다)): **P1 → P2 → P3 → P4 → (STAGE2 ② SAF 마크다운) → P5 → 첫 AAB 업로드.** P2가 P3보다 먼저인 이유: P3의 `PhotoEdit` 파일 삭제가 P2의 `DiaryPhotoStore`를 쓴다. P5는 v3 스키마(P3)와 사진 파일(P4)이 있어야 의미가 있다. 합계 18~24h(검증·critic 포함, 빌드 시간 제외, ② 제외).
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

- 파일 이름: **UUID.jpg**(결정 A — `diary_photos.path`가 경로를 가짐, 저장 순서 = 파일 쓰기 → `saveDiary(photos = PhotoEdit.Set(…New(경로)))`, §3.4). 기각된 B(참고): 
  **`{diaryId}-{createdAt}.jpg`**(DB 없이 대조) — **B는 위 시그니처로 구현할 수 없었다:** 새 일기는 insert 전에 id가 없으므로 순서가 `insertDiary`(반환 `Long`, `ReadingRepository.kt:34`) → id를 받아 파일 쓰기여야 하고,
  `saveDiary`가 id를 돌려주도록 바꾸고(`ReadingViewModel.kt:463-477`는 지금 `onComplete: () -> Unit`), `save(source, …, diaryId, createdAt)`로 인자를 늘리며, 날짜를 고치면 파일 이름도 바꿔야 한다(ADR-006 B 리스크).
- 디코드는 기존 `BitmapDecoding.decodeDownsampled`(`BitmapDecoding.kt:65`) 재사용, JPEG 품질 85(예상 0.3~1MB/장 — 실측 아님, P2 PR에서 기기 1장 실측해 기록).
- 카메라 임시 파일은 보관 복사 후(또는 저장하지 않고 화면을 떠날 때) **삭제** → ADR-002:30 후속 발견 해소.
- 백업 제외: `noBackupFilesDir`이라 XML 변경 0(ADR-006 Q-b 결정 — 자동 백업·기기 간 자동 이전 제외 유지). 기기를 옮길 때 사진은 P5 zip으로 사용자가 직접 옮긴다.
- **테스트:** 새 `DiaryPhotoStoreTest.kt`(Robolectric) — 저장 후 파일 존재·긴 변 ≤ 2048 · 저장 루트가 `noBackupFilesDir` 아래 · 경로 탈출 거부 · 파일 부재 시 `fileFor` null · 삭제 · sweep이 참조된 파일을 지우지 않음 · 디코드 실패 시 null.
- **완료 조건:** 위 통과 · 사진이 클라우드 백업에 들어가는 경로 0 · 새 권한 0 · 새 의존성 0.

### 3.4 P3 🔴 — 선행 `@Update` + Room version 3 + `diary_photos` + 마이그레이션 (ADR-006 결정)

**선행(같은 PR의 첫 커밋, F6):** `DiaryDao`에 `@Update suspend fun update(diary: Diary)`를 두고, `saveDiary`가 **편집(`id != 0`)이면 `update`, 새 기록이면 `insert`**를 부른다.
`REPLACE`의 삭제 단계가 `diary_photos`의 `ON DELETE CASCADE`를 일으켜 편집만 해도 사진 행이 사라지는 회귀를 막는다(M5).

**스키마 변경(결정 — 새 테이블 1개, `diaries` 열 변경 0):**

```kotlin
@Entity(
    tableName = "diary_photos",
    foreignKeys = [ForeignKey(entity = Diary::class, parentColumns = ["id"], childColumns = ["diaryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("diaryId")]
)
data class DiaryPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val diaryId: Int,
    val path: String,        // noBackupFilesDir/diary_photos/ 기준 상대 경로(UUID.jpg)
    val sortOrder: Int,      // 0부터, 화면 표시 순서
    val createdAt: Long = System.currentTimeMillis()
)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 문자열은 컴파일이 만든 3.json의 createSql과 같아야 한다(정본 = 3.json, M1이 검증)
        db.execSQL("CREATE TABLE IF NOT EXISTS `diary_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `diaryId` INTEGER NOT NULL, `path` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`diaryId`) REFERENCES `diaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_photos_diaryId` ON `diary_photos` (`diaryId`)")
    }
}
```

`diaries.photoPath` 열 1개 안은 **기각**(ADR-006 결정 — 1기록 N사진 · 2차 마이그레이션 회피).

**첫 단계(실패하는 테스트 먼저):** `MigrationTest`가 `2.json`을 읽어 v2 DB를 만드는 것만 하는 테스트를 먼저 올려, Robolectric 단위 테스트에서 `MigrationTestHelper`가 스키마 JSON을 찾는지 확인한다.
`sourceSets["test"].assets.srcDir(...)`이 단위 테스트 병합 assets에 들어가는지는 **미검증**(`isIncludeAndroidResources = true`는 이미 있음, `app/build.gradle.kts:63`). 안 되면 대안 = 테스트가 `app/schemas/…/2.json`을 파일로 직접 읽어 v2 DB를 손수 만든 뒤 `Room.databaseBuilder(...).addMigrations(MIGRATION_2_3)`로 연다 — 필수 3단계: ① `createSql`의 `${TABLE_NAME}` 자리표시자를 실제 테이블 이름으로 치환 ② `setupQueries`대로 `room_master_table`을 만들고 v2 `identityHash` 삽입 ③ `PRAGMA user_version = 2`. 하나라도 빠지면 Room이 v2로 인식하지 못해 엉뚱한 경로를 탄다.
`debug`/`main` 소스셋 assets에 스키마를 넣는 방법은 **금지**(APK에 들어간다).

**건드릴 파일:** `data/Entities.kt`(`DiaryPhoto` 추가, `Diary` 그대로) · `data/AppDatabase.kt`(`entities`에 `DiaryPhoto`, `version = 3`, `.addMigrations(MIGRATION_2_3)`, **`fallbackToDestructiveMigration()` 제거**(`:38`), `:12-20` 주석 갱신) · 새 `data/Migrations.kt` ·
`app/schemas/com.example.data.AppDatabase/3.json`(컴파일로 생성 후 커밋) · `app/build.gradle.kts`(테스트가 스키마 JSON을 읽도록 `sourceSets["test"].assets.srcDir("$projectDir/schemas")`, `testImplementation(libs.androidx.room.testing)`) ·
`gradle/libs.versions.toml`(`androidx-room-testing`, 버전 = `roomRuntime` `2.7.0` 재사용 — **새 의존성 1개**, 현재 카탈로그 grep 0) ·
`data/Daos.kt`(`DiaryDao.update` · 새 `DiaryPhotoDao` — `photosFor(diaryId)`·`insertAll`·`deleteByIds`·`allPaths()`(sweep용)) · `data/ReadingRepository.kt`(노출) ·
`ui/viewmodel/ReadingViewModel.kt`(`saveDiary`에 `photos: PhotoEdit = PhotoEdit.Unchanged` 인자 —
`sealed interface PhotoEdit { object Unchanged; data class Set(val slots: List<PhotoSlot>) }` · `sealed interface PhotoSlot { data class Existing(val photoId: Int); data class New(val path: String) }`.
`Unchanged`면 사진 행을 건드리지 않는다. `Set`이면 **목록 순서 = `sortOrder`**, 목록에 없는 기존 행은 삭제 + 파일 삭제, `New`는 행 추가. 사진별 «유지 = `Existing`», «삭제 = 목록에서 뺌», «교체 = 그 자리의 `Existing`을 `New`로», 전부 지우기 = `Set(emptyList())` — 유지와 삭제를 null 하나로 겸하지 않는다) ·
`app/src/test/java/com/example/RoomSchemaExportTest.kt`(identityHash·테이블·컬럼 단언을 v3로).

**마이그레이션 테스트 계획(새 `app/src/test/java/com/example/MigrationTest.kt`, Robolectric + `MigrationTestHelper`):**

| # | 시나리오 | 단언 |
|---|----------|------|
| M1 | v2 DB를 `2.json`으로 만들고 책장 2·책 3·일기 5(한글·이모지·빈 메모·`createdAt` 과거값 포함) 삽입 → `runMigrationsAndValidate(3, MIGRATION_2_3)` | 검증 통과(스키마 = `3.json`, `diary_photos` 테이블·인덱스 존재) |
| M2 | M1 뒤 행 대조 | 책장·책·일기 행이 **바이트 동일**(일기 `id·bookId·page·selectedText·notes·createdAt`), `diaries` 컬럼 6개 그대로, `diary_photos` 0행 |
| M3 | 외래키 | `PRAGMA foreign_key_check` 결과 0행 · 책 삭제 → 그 책 일기 CASCADE 삭제 → **그 일기의 `diary_photos` 행도 CASCADE 삭제** · `ON DELETE CASCADE` 3개(diaries→books, books→bookcases, diary_photos→diaries) |
| M4 | 실제 빌더 경로 | M1 파일을 `AppDatabase.getDatabase`와 같은 빌더 설정(마이그레이션 등록)으로 열어 `diaryDao().getAllDiaries().first()` 5건 |
| M5 | 편집 회귀(F6) | 사진 3장(`a`,`b`,`c`)인 일기를 텍스트만 고쳐 `saveDiary(id = 그 id, photos = Unchanged)` → **사진 3행 그대로**(`@Update` — `REPLACE`면 0행이 되는 회귀) · `Set([Existing(b), New("x")])` → 행 `b`(0)·`x`(1), `a`·`c` 행·파일 삭제 · `Set(emptyList())` → 0행 (세 경우가 서로 구분됨) |
| M6 | 파괴 폴백 부재 | 기존 `migrationDebtIsVisible_whenMoreThanOneSchemaVersionExists`(`RoomSchemaExportTest.kt:81-91`)가 `2.json`+`3.json` 두 파일로 **자동 활성화** → `fallbackToDestructiveMigration` 문자열 0 |
| M7 | 빈 DB | v2 빈 DB(책장만) → 3 마이그레이션 통과 |

**완료 조건:** M1~M7 통과 · `testDebugUnitTest` 전체 통과 · `verifyRoborazziDebug` 기준 불변(UI 0) · **오너 기기 수동 확인 1회**(v2 디버그 설치 위에 v3 디버그 덮어 설치 → 기록 수 동일, 같은 `debug.keystore`) · critic PASS · **첫 AAB 업로드 전 머지**(ADR-006 재검토 조건 1).
문서(ADR-005 §2·§3·§6 ADR-004-4 · ADR-004 실행 단계 ⑧ · STAGE2-PLAN 공통 DoD)는 2026-11-03 결정 반영 PR에서 이미 개정됨 — P3 PR은 사실이 바뀐 줄(예: `version = 3` 행 번호)만 고친다.

### 3.5 P4 — 화면 연결

**건드릴 파일:** `ui/screens/OcrDiaryScreen.kt`(체크박스 **없음** — 저장하면 OCR에 쓴 사진을 항상 보관(ADR-006 Q-a). 편집 모드 사진 N장 표시·사진별 바꾸기·지우기·«사진 추가» — 화면은 의도만 넘긴다) ·
`ui/viewmodel/ReadingViewModel.kt`(**사진 쓰기는 ViewModel 스코프에서**: `saveDiaryWithPhotos(…, photos: PhotoInput)` — `sealed interface PhotoInput { object Unchanged; data class Slots(val slots: List<PhotoInputSlot>) }` · `sealed interface PhotoInputSlot { data class Existing(val photoId: Int); data class New(val source: Uri, val rotation: Float, val flipped: Boolean) }`(null·불리언 조합으로 상태를 겸하지 않는다) → `New`마다 `DiaryPhotoStore.save` 후 `PhotoSlot.New(경로)`로 바꿔 `saveDiary(photos = PhotoEdit.Set(…))`, `Unchanged`는 그대로 — 디코드를 ViewModel에 두는 기존 관례(`ReadingViewModel.kt:506-509`)와 같아 회전·뒤로가기에도 끊기지 않는다 · `deleteDiary`·`deleteBook`·`deleteBookcase`·`deleteSampleBooks`에서 사진 파일 삭제 — CASCADE 전에 해당 일기들의 `diary_photos.path`를 먼저 조회, 앱 시작 시 `sweep` 1회) ·
`ui/screens/PhraseRecallScreen.kt`(썸네일 = `sortOrder` 0번 사진 — Coil `AsyncImage(model = File)`, 로컬 파일만 · 복기 화면은 N장 넘겨 보기·확대) ·
`docs/privacy/privacy-policy.html:41`(«사진 자체는 일기에 저장되지 않습니다» → «글자 인식에 쓴 사진은 기록과 함께 앱 내부 저장소에 보관되며, 자동 백업·기기 간 자동 이전에서 제외됩니다. 사용자가 직접 실행하는 백업 파일(zip)에만 포함되고, 기록·책을 지우면 함께 지워집니다») ·
`docs/store-listing/INTERNAL-TEST-2026-11.md`(§5 N8 행은 2026-11-03 결정 반영 PR에서 미리 들어감 — P4에서 코드 근거 칸 채우기 · §2 문안 «사진은 기기 밖으로 나가지 않습니다»(:41) · §6 테스터 안내(:149) 같은 문구 → «자동으로는 나가지 않습니다» 계열로) · 앱 안 처리방침 요약(`PolicyCopyTest` 대상 문구가 사진을 언급하면 함께).
**테스트:** `OcrDiaryEditSaveTest` 확장(편집 저장 시 사진 유지·교체·삭제·추가, `Unchanged`로 텍스트만 고쳐도 사진 N장 유지) · 새 기록 저장 시 사진 1장이 체크 없이 보관됨 · Robolectric 삭제 연동(일기·책·책장·예시 책 삭제 후 파일 0, 다른 일기 파일 유지) · **dangling**(행은 있고 파일은 없는 사진 → 카드·복기 화면이 «사진 없음(이 기기에 없음)», 크래시 0) · `HonestCopyTest`에 «사진 자체는 일기에 저장되지 않습니다» 문구가 `app/src/main`에 없음 · Roborazzi 회상 카드 썸네일 1장(테스트 리소스 이미지).
**완료 조건:** 위 통과 · dangling 표시 규칙 적용 · 사진 없이 연 기록(«사진 없이 열기»)은 지금과 같은 흐름 · 자동 외부 전송 경로 추가 0(`GeminiKeyPolicyTest` 불변) · 오너 기기에서 촬영 → 저장 → 회상 카드 사진 확인 1회.

### 3.6 P5 — SAF zip 백업(DB + 사진) + 가져오기(복원) (ADR-006 Q-b 결정, 2026-11-03 추가)

**범위:** 설정 «백업 파일 만들기» → `ActivityResultContracts.CreateDocument("application/zip")`로 사용자가 저장 위치·이름을 고른다(Google 드라이브 등 SAF 프로바이더 포함) → zip 1개.
설정 «백업 파일에서 복원» → `OpenDocument`로 zip 선택 → 확인 다이얼로그(«지금 기록이 백업 파일 내용으로 바뀝니다») → 복원.
STAGE2-PLAN ② 마크다운 내보내기(책당 1파일 + 인덱스 노트, 덮어쓰기 0)는 **그대로** 두고 별개 메뉴다(대체 아님, LC 결정 11-03). 복원이 있으므로 이 기능만 «백업»이라 부른다(ADR-005 §6 ADR-003-5 개정).

**zip 구성(기본안 — P5 PR에서 확정):** `manifest.json`(형식 버전 · Room `version` · 만든 시각 · 기록·사진 수) + `db/`(Room DB 사본 — WAL 체크포인트 뒤 복사) + `photos/`(`diary_photos.path`와 같은 이름의 JPEG). 키 저장소(`SecureKeyManager`의 EncryptedSharedPreferences)·설정은 **넣지 않는다**(키는 기기 밖으로 내보내지 않음, CLAUDE.md 안전 규칙).

**규칙:**
- 앱 서버 0 · 자동 실행 0(예약·주기 백업 없음) · 새 네트워크 호스트 0. 전송은 사용자가 고른 SAF 프로바이더 몫.
- 가져오기는 **교체**(병합 0). 실패하면 기존 기록을 그대로 둔다(임시 위치에 풀어 검증 → 통과 시에만 교체).
- 검증: `manifest.json` 필수 · zip 항목 경로 탈출(`../`, 절대 경로) 거부 · Room 버전이 앱보다 높으면 거부 · 낮으면 등록된 `Migration`으로 올림 · 사진 파일이 없는 `diary_photos` 행은 dangling 규칙(§2.4)대로 «사진 없음».
- 크기: 사진 포함이라 수십 MB가 정상(오너 실측 레퍼런스 앱 50~58MB) — 쓰기·읽기는 `Dispatchers.IO` 스트리밍, 진행 표시.

**건드릴 파일(제안):** 새 `data/backup/BackupArchive.kt`(zip 쓰기·읽기·검증 순수 로직, `InputStream`/`OutputStream` 경계) · 새 `data/backup/BackupManifest.kt` · `ui/viewmodel/ReadingViewModel.kt`(백업·복원 상태) · `ui/screens/SettingsScreen.kt`(메뉴 2개, testTag `settings_backup_create`·`settings_backup_restore`) · 처리방침·데이터 안전 문구(N8 «사용자 내보내기 zip에만 포함»과 일치 확인) · `INTERNAL-TEST-2026-11.md:63` «백업»이라 부르지 않는다 → «마크다운 내보내기만» 한정으로.
**테스트:** 새 `BackupArchiveTest.kt`(JVM/Robolectric) — 왕복(백업 → 빈 DB에 복원 → 책장·책·일기·사진 행·파일 바이트 동일) · 경로 탈출 항목 거부 · `manifest.json` 없음/손상 거부 · 앱보다 높은 Room 버전 거부 · 복원 중 실패 시 기존 기록 불변 · 키 저장소 파일이 zip에 없음 · 사진 파일 빠진 zip → dangling 표시. Roborazzi: 확인 다이얼로그 1장(선택).
**완료 조건:** 위 통과 · `GeminiKeyPolicyTest`·`HonestCopyTest`·`RoomSchemaExportTest` 통과 · 새 권한 0 · 새 네트워크 호스트 0 · **오너 기기 수동 확인 1회**(백업 zip을 드라이브에 저장 → 앱 데이터 삭제 → 복원 → 기록·사진 수 동일) · critic PASS.

## 4. 2단계 ② SAF 내보내기와의 순서 — 결정됨 (다)

**결정(2026-11-03, 오너): (다) 회상 전체(사진 포함) 먼저 → SAF.** 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`.
순서 = **P1 회상(스키마 0) → P2 사진 저장소 → P3 Room v3 + `diary_photos` + `Migration(2,3)` + M1~M7 → P4 일기↔사진 연결·카드·편집(유지/교체/삭제) → P5 SAF zip 내보내기 + 가져오기(사진 포함) → 첫 AAB 업로드.**
STAGE2-PLAN ② 마크다운 내보내기는 유지되며 P4 뒤, P5 앞에 둔다(② 위치는 결정 3 목록에 없어 (다)의 정의 «회상 전체 → SAF ②»로 둔 **해석 — 오너 확인 대상**)(STAGE2-PLAN «문구 회상과의 순서»). 1 PR = 1 의도.

검토했던 선택지(기록):

| 선택지 | 장점(1줄) | 단점(1줄) |
|--------|-----------|-----------|
| (가) **SAF ② 먼저** → 문구 회상 P1~P4 | 2단계 계획(STAGE2-PLAN 권장 순서 5번, AAB 직전)과 스키마 동결을 그대로 지켜 첫 업로드가 가장 빠르다 | 오너가 방금 요청한 기능이 가장 늦고, 내보내기에 사진 포함 여부를 나중에 다시 설계해야 할 수 있다 |
| (나) **회상 P1(사진 없음) 먼저** → SAF ② → 사진(P2~P4) | 스키마 0인 절반을 바로 쓰게 하면서 SAF 일정도 지킨다 | 사진 없는 회상은 원문 요구의 절반이고, 그동안 찍은 사진은 보관되지 않아 나중에 붙일 수 없다 |
| **(다) 회상 전체(사진 포함) 먼저** → SAF ② — **채택** | 첫 업로드에 핵심 기능이 들어가고 SAF가 처음부터 사진 포함/제외를 정해 설계된다 | 가장 위험한 변경 두 개(마이그레이션·파일 쓰기)가 연달아 오고, ADR-006 A-선행 선택과 ADR-005 §2 개정이 전제다(둘 다 2026-11-03 충족) |

## 5. 스토어 부제 — 결정됨 (후보 3)

**결정(2026-11-03, 오너): «밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리»**(40자, 공백 포함 `python3 len()` 실측). 정본: life-coordinator `DEC-2026-1103-rd-phrase-recall.md`.
반영: `docs/store-listing/INTERNAL-TEST-2026-11.md:26` · ADR-005 §6 ADR-003-6b.

검토했던 후보(기록) — 이전 후보(ADR-005 §6 ADR-003-6b): «밑줄이 책과 책을 잇는 독서 다이어리»(20자).

| # | 후보 | 글자 수(공백 포함) | 강조점 |
|---|------|--------------------|--------|
| 1 | 떠오른 한 문장, 언제 어느 책에 남긴 밑줄인지 찾아 주는 독서 다이어리 | 40 | 원문 요구(회상) — «읽었는지»가 아니라 «남긴 밑줄»: 앱은 읽은 날을 저장하지 않는다(§2.3) |
| 2 | 밑줄과 그날의 페이지 사진으로 다시 만나는 독서 다이어리 | 31 | 사진 복기 |
| **3** | **밑줄이 책과 책을 잇고, 떠오른 문장을 사진과 함께 되찾는 독서 다이어리 — 채택** | 40 | 기존 후보(연결) + 회상 |

- 짧은 설명 한도 80자 이내(`INTERNAL-TEST-2026-11.md:24`) — 충족.
- **정직 조건 충족:** ① 후보 3은 사진 연결(P4)이 머지된 빌드에서만 쓸 수 있다 → 결정된 순서에서 첫 업로드는 P5 뒤(P4 뒤). ② «읽은 날»을 약속하지 않는다 — 앱이 아는 것은 기록한 날(`createdAt`)뿐.

## 6. 질문 처분 요약

- ADR-006 선택 → **결정됨: A-선행 + `diary_photos` 테이블** → `DEC-2026-1103-rd-phrase-recall.md`
- ADR-006 부속 Q-a·Q-b → **결정됨: Q-a 항상 보관(체크박스 없음) · Q-b 자동 백업·이전 제외 + 사용자 zip 내보내기·가져오기에 포함(P5)** → `DEC-2026-1103-rd-phrase-recall.md`
- §4 SAF 순서 → **결정됨: (다)** → `DEC-2026-1103-rd-phrase-recall.md`
- §5 부제 → **결정됨: 후보 3** → `DEC-2026-1103-rd-phrase-recall.md`
- 막지 않는 기본값(프라이버시와 무관한 것만): 진입점 = 대시보드 버튼 + 기억 서랍 링크

## 7. 레퍼런스 앱 (오너 제시, 2026-11-03 — 사실만)

[«독서 다이어리» `net.donxu.reading_diary`](https://play.google.com/store/apps/details?id=net.donxu.reading_diary&hl=ko) — 아래는 오너가 제시한 사실을 옮긴 것이다(이 세션이 Play 페이지를 다시 확인하지 않음).

| 항목 | 내용 |
|------|------|
| 개발자 · 규모 | 문동수(개인) · 다운로드 10만+ · 평점 4.7 · 리뷰 7,832 |
| 출시 · 갱신 | 2011-02-27 출시 · 2026-09-28 갱신 |
| 광고 · 데이터 안전 | 광고 있음 · «앱 활동·기기 ID 공유» |
| 기록 방식 | 사진 중심(촬영 → 자르기 → 텍스트와 함께 저장, **OCR 없음**) · 1기록 N사진 · 쪽수 · 태그 · 책별 타임라인 · 책장 · 달력 |
| 백업 · 복원 | 사용자가 «백업하기» → zip을 **본인 Google 드라이브**에(Android 자동 백업 아님) · 복원 = 설정 실험실 «웹에서 데이터 복원 / 사진 복원» |
| 오너 실측 | 본인 백업 zip 50~58MB(2023-10~2026-09, 5건) → 자동 백업 25MB 한도(F4)를 넘는 규모 |

이 앱에서 가져온 것: 1기록 N사진(ADR-006 `diary_photos`) · 사용자 실행 zip 백업·복원(P5).

**우리 차별점(3줄):**
1. **OCR 텍스트** — 사진에서 밑줄 구절을 온디바이스로 글자로 뽑아 검색 가능한 기록으로 남긴다(ADR-002).
2. **책 사이 연결 + 회상** — 서로 다른 책에 함께 나온 단어로 책을 잇고(ADR-003), 떠오른 문구 하나로 그때의 책·날짜·사진을 되찾는다(이 명세).
3. **광고 0 · 자동 외부 전송 0** — 사진은 사용자가 직접 내보낼 때만 기기 밖으로 나간다(ADR-006 Q-b).
