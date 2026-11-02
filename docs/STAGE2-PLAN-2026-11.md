# 2단계 코드 작업 계획 (2026-11) — 기억 서랍 2단계 + Play 내부 테스트 선행 정리

- 상위 결정: [ADR-005](adr/ADR-005-internal-test-and-stage2-boundary.md) §3(묶음 경계) · §6(보류 처분)
- 상태: **계획(코드 0).** 이 문서가 다음 클라우드/로컬 발주의 입력이다. 항목마다 1 PR(CLAUDE.md «1 PR = 1 의도»).
- 근거 파일:줄은 2026-11-02 origin/main `47900e1` 기준.
- **번호 체계:** ①~⑤는 이 문서 내부 번호다(⑤ = applicationId). «3단계 ⑥~⑧»은 ADR-004 «실행 단계» 표의 번호다(ADR-004의 ④⑤는 이 문서의 ①②에 해당).

## 공통 DoD(모든 항목)

- **Room 스키마 변경 0** — `AppDatabase` `version = 2`·엔티티 필드 그대로(`AppDatabase.kt:21`, `Entities.kt`). `RoomSchemaExportTest` 통과로 확인.
- **사진 외부 전송 경로 추가 0** — `GeminiKeyPolicyTest` 통과(참조 범위 고정 유지).
- **유료·광고 코드 0.** 새 네트워크 호스트 0(④는 오히려 하나를 지운다).
- `gradle :app:testDebugUnitTest` 통과. Compose UI를 바꾸는 항목(①②③④)은 `gradle :app:verifyRoborazziDebug`까지, 기준 이미지 갱신 시 PR에 이유.
- Critic 서브에이전트 PASS(HARNESS.md).

## 권장 순서

| 순서 | 항목 | 이유 | 추정 |
|------|------|------|------|
| 1 | ⑤ applicationId | 가장 작고 독립적. 첫 업로드 전에만 가능 | 0.5~1h |
| 2 | ④ Unsplash 폴백 제거 | ③의 예시 책이 쓸 로컬 플레이스홀더를 먼저 만든다 | 2~3h |
| 3 | ③ «예시 2권» | 시드 제거가 ④와 같은 `AppDatabase.kt`를 건드림 → ④ 뒤 | 2~3h |
| 4 | ① 캔버스 보조 뷰 | ③의 예시 2권이 그래프 첫 화면 데이터가 된다 | 4~6h |
| 5 | ② SAF 내보내기 | 가장 위험(파일 쓰기). 마지막에, AAB 직전 | 5~7h |
| — | 부록 A·B | 문구 정정. ⑤ 또는 별도 소형 PR | 0.5~1h |

합계 추정 14~21h(검증·Critic 루프 포함, 빌드 시간 제외).

---

## ① 캔버스 보조 뷰 — 이분 그래프(ADR-003 Q3 2단계)

**범위:** 기억 서랍 «연결 발견» 섹션에 «목록 ｜ 그림» 전환을 두고, «그림»이면 책 열(왼쪽) ｜ 공유 단어 열(오른쪽) 이분 그래프를 Compose Canvas로 그린다.
목록은 기본값으로 남는다(대체 아님). 단어/엣지 탭 → 그 단어의 원문 구절 카드(두 책 나란히).

**좌표 순수 함수(새 파일 제안):** `app/src/main/java/com/example/data/knowledge/BipartiteLayout.kt`

```kotlin
enum class GraphSide { BOOK, WORD }

data class GraphNode(val key: String, val label: String, val side: GraphSide, val x: Float, val y: Float)
data class GraphEdge(val bookKey: String, val wordKey: String)

data class BipartiteGraph(
    val bookNodes: List<GraphNode>,
    val wordNodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    /** 상한 때문에 잘린 단어·책·엣지가 있으면 true. 화면이 "일부만 표시" 한 줄을 띄운다(숨김을 숨기지 않는다). */
    val truncated: Boolean
)

sealed interface GraphHit {
    data class Word(val word: String) : GraphHit
    data class Edge(val bookId: Int, val word: String) : GraphHit
}

object BipartiteLayout {
    const val MAX_BOOKS = 10
    const val MAX_WORDS = 12
    const val MAX_EDGES = 40

    /** 결정적: 같은 입력이면 같은 좌표. 힘 기반·난수 0(Roborazzi 안정). */
    fun layout(
        sharedWords: List<SharedWord>,      // KeywordLinks.build 결과(이미 정렬됨: 태그 우선 → 책 수 → 구절 수 → 사전순)
        titleById: Map<Int, String>,
        width: Float,
        height: Float,
        padding: Float = 24f
    ): BipartiteGraph

    /** 탭 좌표 → 단어 노드(반지름 안) 우선, 아니면 선분까지 거리 ≤ tolerance 인 엣지. 없으면 null. */
    fun hitTest(graph: BipartiteGraph, x: Float, y: Float, nodeRadius: Float, edgeTolerance: Float): GraphHit?
}
```

규칙: 단어는 `sharedWords` 순서로 앞 12개 → 그 단어들이 걸린 책을 **엣지 수 내림차순 → 제목 오름차순**으로 앞 10권 → 엣지는 단어 순서대로 40개까지.
각 열은 `padding..height-padding`에 균등 간격, 책 열 x = `padding`, 단어 열 x = `width - padding`. 입력은 `SharedWord.bookIds`만 쓴다(같은 책 안 반복은 애초에 엣지가 아님 — ADR-003 Q1).

**건드릴 파일:**
- 새 `data/knowledge/BipartiteLayout.kt`(위)
- 새 `ui/screens/KnowledgeGraphCanvas.kt` — `@Composable fun KnowledgeGraphCanvas(graph: BipartiteGraph, onHit: (GraphHit) -> Unit, modifier: Modifier)`
  (`Canvas` + `pointerInput { detectTapGestures }`, 라벨은 `rememberTextMeasurer`). testTag `knowledgedrawer_graph`.
- `ui/screens/KnowledgeDrawerScreen.kt` — 연결 섹션(`:256-287`)에 전환 버튼(testTag `knowledgedrawer_view_toggle`), 탭 결과 카드(기존 `ConnectionCard`(`:492`) 재사용: `GraphHit.Word` → 그 `SharedWord`, `GraphHit.Edge` → 같은 카드에서 해당 책 구절 강조).
  `KnowledgeDrawerContent`에 테스트용 초기값 파라미터 `initialConnectionView` 추가(기존 `initialQuery` 관례, `:77`).
- ViewModel 변경 없음(그래프는 `sharedWords`에서 화면 크기를 알고 나서 `remember(sharedWords, size)`로 계산 — 순수·저비용).

**테스트:**
- 새 `app/src/test/java/com/example/BipartiteLayoutTest.kt`(JVM): 빈 입력 → 빈 그래프 · 상한(단어 13·책 11·엣지 41 입력 → 12/10/40, `truncated = true`) ·
  결정성(두 번 호출 동일) · 균등 간격 · 열 x 좌표 · `hitTest` 노드/엣지/빈 곳 · 책 1권만 걸린 단어는 입력에 없다는 전제 확인.
- `KnowledgeDrawerScreenshotTest`에 **1장** 추가: `knowledge_drawer_graph.png`(기존 `books`/`diaries` 픽스처 + 그래프 뷰). `recordRoborazziDebug`로 기준 생성, PR에 이유.
- 기존 기준 5장(`knowledge_drawer_*.png`)은 기본 뷰가 목록이므로 바뀌지 않아야 한다 — `verifyRoborazziDebug` 통과가 그 증거.

**완료 조건:** 위 테스트 통과 · 기본 뷰 = 목록(기준 5장 불변) · 그래프에서 단어 탭 → 두 책 원문 카드 · 상한 초과 시 «일부만 표시» 문구 · 힘 기반·난수 0.
**추정:** 4~6h.

---

## ② SAF 마크다운 내보내기 — 새 파일 생성만(ADR-004 Q6·Q7)

**범위:** 기억 서랍 상단 메뉴 «마크다운 내보내기» → `ActivityResultContracts.OpenDocumentTree()`로 사용자가 폴더 선택 →
그 아래 하위 폴더 **«독서 다이어리»**(없으면 생성, 있으면 재사용) → **책당 1파일** + 인덱스 노트 1장(`독서 다이어리 목록.md`).
같은 이름 파일이 이미 있으면 **쓰지 않고 건너뛰며** 결과 다이얼로그에 «만든 파일 N · 건너뛴 파일 M(목록)»을 보인다. 덮어쓰기·병합 0(ADR-005 §3). 키워드 스텁 노트 0(ADR-004 Q6).

**순수 함수(새 파일 제안):** `app/src/main/java/com/example/data/export/MarkdownExport.kt`

```kotlin
data class ExportFile(val fileName: String, val content: String)
data class ExportPlan(val toCreate: List<ExportFile>, val skipped: List<String>)

object MarkdownExport {
    /** 파일 이름: 제목에서 / \ : * ? " < > | 와 제어문자 제거, 앞뒤 공백·점 제거, 120자 컷, 빈 값이면 "제목 없음". 같은 이름의 다른 책은 id 오름차순으로 " (2)", " (3)". */
    fun fileNames(books: List<Book>): Map<Int, String>
    /** YAML frontmatter(title, author, status, rating, pages, exported) + 기록(쪽·발췌 인용·메모) + 이 책에 걸린 공유 단어 [[단어]] 목록. */
    fun bookNote(book: Book, diaries: List<Diary>, linkedWords: List<String>): String
    fun indexNote(books: List<Book>, fileNames: Map<Int, String>): String   // [[파일 이름]] 목록
    /** 이미 있는 이름은 skipped로. 인덱스 노트도 예외 없음(있으면 건너뜀 — 알려진 한계, 테스터 보고 대상). */
    fun plan(books: List<Book>, diaries: List<Diary>, sharedWords: List<SharedWord>, existingNames: Set<String>): ExportPlan
}
```

**쓰기 경계(새 파일 제안):** `app/src/main/java/com/example/data/export/DocumentTree.kt`

```kotlin
interface DocumentTree {
    fun existingNames(): Set<String>
    /** 새 문서를 만들고 거기에만 쓴다. 이미 있는 문서를 여는 API는 이 인터페이스에 없다. 실패 시 false. */
    fun createAndWrite(fileName: String, mimeType: String, content: String): Boolean
}
class SafDocumentTree(context: Context, treeUri: Uri) : DocumentTree   // DocumentsContract.createDocument + openOutputStream(새 URI)만
```

새 의존성 0을 기본안으로 한다(`DocumentsContract`·`ContentResolver` 직접 사용). `androidx.documentfile`은 카탈로그에 없음(`gradle/libs.versions.toml` grep 0) — 쓰려면 PR에 1줄 근거.
ViewModel: `fun exportMarkdown(tree: DocumentTree)` → `exportState: StateFlow<ExportState>`(Idle/Running/Done(created, skipped)/Error). 쓰기는 `Dispatchers.IO`.
SAF가 `createDocument`에서 이름을 바꿔 «이름 (1).md»로 만드는 프로바이더가 있으므로, `existingNames()`로 **먼저** 거르고 그래도 반환 이름이 다르면 그 사실을 결과에 표시한다.

**건드릴 파일:** 새 `data/export/MarkdownExport.kt`, `data/export/DocumentTree.kt` · `ui/viewmodel/ReadingViewModel.kt`(내보내기 상태·함수) ·
`ui/screens/KnowledgeDrawerScreen.kt`(메뉴 버튼 testTag `knowledgedrawer_export_markdown`, 결과 다이얼로그 testTag `knowledgedrawer_export_result`) ·
`app/src/test/java/com/example/HonestCopyTest.kt`(조건화, 아래).

**HonestCopyTest 조건화(ADR-004 Q8):** `noOverstatedFeatureVocabularyInProductionSources`(`HonestCopyTest.kt:58-70`)에서
`Obsidian`/`옵시디언`만 **조건부**로 뺀다 — 조건: `src/main/java/com/example/data/export/DocumentTree.kt`가 존재하고 `DocumentsContract.createDocument`를 포함할 때.
조건을 못 맞추면 지금처럼 금지. `RAG`·`Logseq`·`뇌세포` 등 나머지는 무조건 금지 그대로. 조건 판정 자체를 테스트 1개로 고정(파일을 지우면 다시 금지되는지).
화면 문구에는 «백업»을 쓰지 않는다(복원 없음, ADR-005 §6 ADR-003-5) — 금지어 목록에 `"마크다운 백업"` 1개 추가 제안.

**테스트:**
- 새 `MarkdownExportTest.kt`(JVM, `testImplementation(libs.json)` 불필요): 파일 이름 정제·중복 접미 · frontmatter 형식 · `[[단어]]`는 그 책에 걸린 공유 단어만 ·
  **이미 있는 이름은 toCreate에 절대 없음** · 인덱스 노트 건너뜀 · 빈 책(기록 0) 처리.
- 새 `ReadingViewModelExportTest.kt`(Robolectric): `FakeDocumentTree`로 created/skipped 집계, `createAndWrite` 실패 시 Error, **기존 이름으로 `createAndWrite`가 한 번도 호출되지 않음**.
- `HonestCopyTest` 조건 테스트.
- Roborazzi: 결과 다이얼로그는 상태 기반 stateless 컴포저블로 빼서 1장(선택). 기억 서랍 기준 5장 불변.

**완료 조건:** 위 테스트 통과 · 같은 폴더에 두 번 내보내면 두 번째는 전부 «건너뜀»(실기기 수동 확인 1회, 오너 기기) · 덮어쓰기 경로 0(`DocumentTree`에 열기 API 없음) ·
스토어 문안 «■ 마크다운 파일로 내보내기» 교체(INTERNAL-TEST-2026-11 §2) · 처리방침 6항 «(제공 시)» 삭제.
**추정:** 5~7h.

---

## ③ ADR-003-1 «예시 2권 넣기»

**범위:** 신규 설치 시드 책·일기 제거(책장 5개는 유지) + 설정 «예시 2권 넣기» / «예시 지우기» 버튼. 예시는 «예시» 배지, 통계 제외, 일괄 삭제. **기존 기기의 시드는 건드리지 않는다.**

**스키마 없이 표식하는 방법(제안):** `diary_general_settings` SharedPreferences(`ReadingViewModel.kt:104`, Auto Backup 포함 — DB와 같은 운명)에
`sample_books` = `"id|title"` 집합을 저장. 판정은 **id와 제목이 모두 일치**할 때만.
위험: 파괴적 마이그레이션·앱 데이터 일부 복원으로 DB가 새로 만들어지면 id가 1부터 다시 매겨져 사용자의 진짜 책이 «예시»로 오인되고 일괄 삭제될 수 있다 →
① 제목까지 대조 ② `AppDatabase.DatabaseCallback.onCreate`(`AppDatabase.kt:49`)에서 `sample_books` 키를 지운다. 두 방어를 테스트로 고정.

**예시 내용:** 연결이 첫 화면에 보이도록 서로 다른 책 2권 + 일기 각 1~2건 + 두 책 메모에 같은 `#태그`(자동 추출 품질에 기대지 않고 결정적으로 연결).
현재 시드 구절(`AppDatabase.kt:106-127`, 데미안·돈의 속성·사피엔스)은 저작권이 살아 있는 작품의 번역 인용이다 → 예시는 **저작권 보호기간이 끝난 국내 작품**(예: 윤동주·김소월) 또는 직접 쓴 문장으로 교체(발견 D10). 표지는 ④의 플레이스홀더(`coverUrl = ""`).

**건드릴 파일:** `data/AppDatabase.kt`(onCreate 시드 책·일기 삽입 삭제, `sample_books` 키 정리) · 새 `data/SampleBooks.kt`(예시 데이터 상수 + `isSample(book, marks)` 순수 함수) ·
`ui/viewmodel/ReadingViewModel.kt`(`insertSampleBooks()`, `deleteSampleBooks()`, `sampleBookIds: StateFlow<Set<Int>>`) ·
`ui/screens/SettingsScreen.kt`(SECTION 2 데이터 저장 카드 `:438` 근처에 버튼 2개, testTag `settings_insert_samples`/`settings_delete_samples`) ·
`ui/screens/DashboardScreen.kt`·`BookDetailScreen.kt`(«예시» 배지) · `ui/screens/StatisticsScreen.kt`(`:44-45`의 books/diaries에서 예시 제외).

**테스트:** 새 `SampleBooksTest.kt`(JVM): `isSample` id·제목 일치/불일치 · 예시 2권이 `KeywordLinks.build`로 최소 1개 공유 단어(태그)를 만든다 · 예시 구절 문자열이 현재 시드 문구를 포함하지 않는다.
Robolectric: 넣기 → 2권, 다시 누르면 중복 안 들어감 · 지우기 → 예시만 삭제(진짜 책 남음, CASCADE로 일기도) · `onCreate`가 키를 지움.
통계 화면 스크린샷 기준이 없으므로 통계 집계 순수 함수로 빼서 JVM 테스트(선택).

**완료 조건:** 신규 설치 첫 화면 책 0권 · 버튼 1회 → 예시 2권 + 기억 서랍 연결 ≥1 · 통계 수치에 예시 미포함 · 지우기 후 사용자 책 불변 · 스키마 변경 0.
**추정:** 2~3h.

---

## ④ ADR-003-2 Unsplash 표지 폴백 제거 → 로컬 플레이스홀더

**실측:** `grep -rn "unsplash" app/src --include=*.kt | wc -l` = **20**줄, 4파일, 논리 지점 4곳(ADR-003 보류 2의 «4곳»과 일치).

| 파일 | 줄 | 지점 | 처리 |
|------|----|------|------|
| `app/src/main/java/com/example/data/Entities.kt` | 45-49 (5줄) | `Book.cleanCoverUrl` 기본 표지 목록 | 목록 삭제. `coverUrl`이 비었거나 Unsplash면 `""` 반환 → 화면이 플레이스홀더 |
| `app/src/main/java/com/example/ui/screens/AddEditBookScreen.kt` | 88-91, 94-98 (9줄) | 수동 등록 `activeCoverUrl` 책장별/해시 폴백 | 폴백 삭제. `activeCoverUrl = coverUrl`(검색 결과 표지만) |
| `app/src/main/java/com/example/data/AppDatabase.kt` | 71, 84, 98 (3줄) | 시드 책 `coverUrl` | ③에서 시드 삭제 시 함께 사라짐. ④를 먼저 하면 `""`로 |
| `app/src/main/java/com/example/ui/screens/OcrDiaryScreen.kt` | 95, 101, 107 (3줄) | `UnderlinePreset` 예시 이미지 URL(편집 모드 `:199`에서 `activeImageUrl` 초기값) | 프리셋 URL 삭제. 편집 모드는 이미지 없이 «이미지 없음» 상태로 진입하도록 `:199` 정리(편집 저장 흐름 회귀 테스트 필요) |

**이미 저장된 값(중요, 발견 D3):** 수동 등록 시 `activeCoverUrl`(Unsplash 폴백 포함)이 그대로 `saveBook(coverUrl = activeCoverUrl)`로 **Room에 저장**된다(`AddEditBookScreen.kt:770`).
기존 설치에는 Unsplash URL이 DB에 들어 있고, `cleanCoverUrl`은 `http`로 시작하면 그대로 돌려주므로(`Entities.kt:43`) 코드 폴백만 지워서는 계속 Unsplash를 부른다.
→ 표시 시점 필터 `isStockCover(url) = url.contains("images.unsplash.com")`이면 플레이스홀더. **DB 값을 고쳐 쓰지 않는다**(데이터 마이그레이션 회피, 되돌리기 쉬움).

**플레이스홀더:** 새 `ui/components/CoverPlaceholder.kt` — `@Composable fun CoverPlaceholder(title: String, modifier: Modifier)`: 제목 첫 글자 + 제목 해시로 고른 테마 색(네트워크 0).
색 선택은 순수 함수 `placeholderColorIndex(title: String, paletteSize: Int): Int`(JVM 테스트).
Coil 호출부: `DashboardScreen.kt:436`, `BookDetailScreen.kt:246`(둘 다 `cleanCoverUrl` 경유), `AddEditBookScreen.kt:445`(검색 결과 썸네일), **`AddEditBookScreen.kt:574-577`(표지 미리보기 — `activeCoverUrl`을 직접 로드, `cleanCoverUrl`을 거치지 않음. 편집 진입 시 `:156` `coverUrl = it.coverUrl`로 저장된 Unsplash URL이 들어오므로 여기에도 `isStockCover` 필터 필수)**, `OcrDiaryScreen.kt:550`(`AsyncImage(activeImageUrl)` — `:199` 정리로 해소되는지 확인) — URL이 비었거나 스톡이면 `AsyncImage` 대신 플레이스홀더.

**테스트:** 새 `CoverFallbackTest.kt`(JVM): `cleanCoverUrl`이 빈 값·Unsplash 값에 `""` · 네이버/Google 표지 URL은 그대로 · `placeholderColorIndex` 결정성.
`HonestCopyTest` 또는 새 테스트에 **`app/src/main`에서 `images.unsplash.com/photo-` URL 리터럴 0회** grep 고정(판정용 호스트 상수 `"images.unsplash.com"` 1곳은 허용, `app/src/test`는 검색 범위 밖). Roborazzi: 플레이스홀더 1장(선택) — 기존 기준 이미지는 표지를 그리지 않으므로 불변 예상.

**완료 조건:** `grep -rn "images.unsplash.com/photo-" app/src/main --include=*.kt | wc -l` = 0 · 호스트 판정 상수는 1곳(`isStockCover`) · 기존 DB에 남은 Unsplash URL도 화면에서 플레이스홀더 · 데이터 안전 폼 N3에서 Unsplash 삭제.
**추정:** 2~3h.

---

## ⑤ applicationId 변경(ADR-005 §1)

**변경:** `app/build.gradle.kts:14` `applicationId = "com.example"` → `"com.woodpeckerai.readingdiary"`. `namespace`(`:10`)·코드 패키지 그대로.
**함께 고칠 것:** `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt:20` `assertEquals("com.example", appContext.packageName)` → 새 id.
**자동으로 따라가는 것(확인만):** `AndroidManifest.xml:28` `${applicationId}.fileprovider`, `OcrDiaryScreen.kt:140` `"${context.packageName}.fileprovider"`.
**영향:** 기기에서 다른 앱이 된다 — 오너 기기의 기존 `com.example` 설치본 기록은 이어지지 않는다(ADR-005 §1). 디버그 서명(`debug.keystore`)은 그대로라 새 id의 디버그 설치끼리는 갱신 가능.
Robolectric은 병합 매니페스트의 패키지를 따르므로 단위 테스트 영향 없음 예상 — `testDebugUnitTest`로 확인.

**테스트:** 새 테스트 1개 제안 — `BuildConfig.APPLICATION_ID == "com.woodpeckerai.readingdiary"`(되돌림 방지). `testDebugUnitTest` 통과. `assembleDebug` 1회(로컬, 키스토어 복사 후).
**완료 조건:** 위 통과 · `grep -rn '"com.example"' app/` 결과가 `app/build.gradle.kts:10`(`namespace`) 1곳뿐.
**추정:** 0.5~1h.

---

## 부록 A — `GeminiApiClient.kt:39` 낡은 주석(ADR-005 §5)

«2단계 착수 시 이 모델 ID가 실재하는지 최우선 확인» → «ADR-001:82 — 2026-09 GA 확인. 프로덕션 승급 전 재확인». 동작 변화 0. ⑤ PR에 같이 넣지 말고(의도 분리) 문서·주석 소형 PR로.

## 부록 B — 설정 화면의 앱 안 약관·개인정보 문구 정정(내부 테스트 전 권장)

`SettingsScreen.kt:574`(이용약관)·`:610`(개인정보) 문구가 ① 옛 이름 «나만의 서재 다이어리»를 쓰고 ② «기기 바깥으로 식별 명세를 일체 요구하거나 비밀 수집·전송하지 않는»처럼
**네이버 검색어 전송(N1)·자동 백업(N7)을 빠뜨린 채 단정**한다 — 처리방침 초안([privacy-policy.html](privacy/privacy-policy.html))과 어긋나 데이터 안전 폼 심사·테스터 신뢰에 걸린다(발견 D8).
제안: 두 다이얼로그 본문을 처리방침 요약 5줄 + 처리방침 URL로 교체. `HonestCopyTest`에 `"나만의 서재 다이어리"` 금지 1개 추가. 1 PR, 0.5h. 착수 여부는 LC 판단.
