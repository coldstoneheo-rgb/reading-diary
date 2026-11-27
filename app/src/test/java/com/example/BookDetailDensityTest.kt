package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.BookDetailScreen
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * 재설계 R3(도서 상세 + 기록 목록)의 통과 기준(REDESIGN §2-1 · §6 · §7-1 보정 1 · 2 · 4 · 5):
 * 첫 화면 기록 카드 3개 · 카드 아이콘 48dp + 한국어 이름 · 쪽 숫자 직접 넣기 · 시작 전엔 FAB · 진척도 없음 ·
 * 12sp 이하 글자 0 · 글꼴 1.3 · 2.0배 잘림 0 · 겹침 0 · 밝은 · 어두운 테마 기준 이미지.
 * 화면 = 411×891dp(사용자 폰과 같은 dp). 글 · 책은 지어낸 견본이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class BookDetailDensityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private val density get() = app.resources.displayMetrics.density

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  // 기록 카드의 날짜 · 시각이 기준 이미지에 찍히므로 시간대를 고정한다.
  private var savedZone: TimeZone? = null
  @Before fun fixZone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul")) }
  @After fun restoreZone() { savedZone?.let { TimeZone.setDefault(it) } }

  private fun idle(n: Int = 15) = repeat(n) {
    composeTestRule.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idle()
    Thread.sleep(20)
  }

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) idle(1)
    assertTrue("condition not met within 10s", condition())
  }

  private fun has(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  private fun stored(id: Int) = runBlocking { db.bookDao().getBookByIdOneShot(id)!! }

  /** 지어낸 견본 기록 3건(인용 2줄 이상 · 메모 1줄 이상 — 카드가 가장 커지는 모양). */
  private val samples = listOf(
    Triple(45, "바람이 부는 언덕에 나무가 서 있고 그 아래로 작은 길이 이어진다. 길 끝에는 오래된 집이 한 채 있다.", "길의 끝을 상상해 본다. 집 안에는 누가 살았을까."),
    Triple(72, "창문 너머로 들어온 빛이 책상 위의 종이를 천천히 데웠다. 종이에는 아무것도 적혀 있지 않았다.", "빈 종이가 주는 느낌을 적어 두고 싶었다."),
    Triple(96, "강물은 늘 같은 자리에 있는 것처럼 보이지만 한 번도 같은 물이었던 적이 없다.", "")
  )

  private fun startedBook(withRecords: Boolean = true): Int = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
    val id = db.bookDao().insert(Book(title = "지어낸 책 제목", author = "지은이", totalPages = 320, currentPage = 96, bookcaseId = caseId, status = "READING", startDate = "2026-11-20 09:00")).toInt()
    if (withRecords) samples.forEachIndexed { i, (page, text, notes) ->
      db.diaryDao().insert(Diary(bookId = id, page = page, selectedText = text, notes = notes, createdAt = 1_795_700_000_000L + i * 3_600_000L))
    }
    id
  }

  private fun newBook(): Int = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
    db.bookDao().insert(Book(title = "지어낸 새 책", author = "지은이", totalPages = 320, currentPage = 0, bookcaseId = caseId, status = "TO_READ", startDate = "")).toInt()
  }

  private fun open(id: Int, themeId: Int = RecordScreenHarness.LIGHT, fontScale: Float = 1f, cards: Int = 3): ReadingViewModel {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.runOnUiThread { vm.navigateTo(Screen.BookDetail(id)) }
    composeTestRule.setContent { RecordScreenHarness.Frame(themeId, fontScale) { BookDetailScreen(viewModel = vm) } }
    pump { has("reading_start_text") && diaryCards().size >= cards }
    idle()
    return vm
  }

  private fun diaryCards(): List<SemanticsNode> = composeTestRule.onAllNodes(
    SemanticsMatcher("diary card") { it.config.getOrNull(SemanticsProperties.TestTag)?.matches(Regex("diary_item_\\d+")) == true }
  ).fetchSemanticsNodes()

  private fun SemanticsNode.rect(): Rect = Rect(positionInRoot, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
  private fun Rect.overlaps(o: Rect) = left < o.right - 0.5f && o.left < right - 0.5f && top < o.bottom - 0.5f && o.top < bottom - 0.5f

  private fun byTag(tag: String) = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
  private fun byLabel(label: String) = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode()
  private fun tagOf(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag).orEmpty()

  private fun layout(node: SemanticsNode): TextLayoutResult? {
    val out = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
    return out.firstOrNull()
  }

  private fun textNodes(): List<SemanticsNode> =
    composeTestRule.onAllNodes(SemanticsMatcher("text") { SemanticsActions.GetTextLayoutResult in it.config }, useUnmergedTree = true).fetchSemanticsNodes()

  /** 잘림 0. 기록 카드의 인용(2줄) · 메모(1줄)는 일부러 줄여 보이는 글이라 말줄임만 허용한다(나머지는 고치기 화면에서 본다). */
  private fun noTextOverflows(where: String) {
    for (n in textNodes()) {
      val l = layout(n) ?: continue
      val text = l.layoutInput.text.text
      val shortened = tagOf(n).startsWith("diary_quote_") || tagOf(n).startsWith("diary_note_")
      if (!shortened) assertTrue("$where: 글 높이가 넘친다 «$text»", !l.didOverflowHeight)
      for (line in 0 until l.lineCount) {
        assertTrue("$where: 글이 잘렸다 «$text» 줄 $line 끝 ${l.getLineRight(line)} > 폭 ${l.size.width}", l.getLineRight(line) <= l.size.width + 1f)
        if (!shortened) assertTrue("$where: 말줄임 «$text»", !l.isLineEllipsized(line))
      }
    }
  }

  private fun assertNoOverlap(where: String, nodes: Map<String, SemanticsNode>) {
    val list = nodes.entries.toList()
    for (i in list.indices) for (j in i + 1 until list.size) {
      assertTrue("$where: «${list[i].key}» ${list[i].value.rect()} 와 «${list[j].key}» ${list[j].value.rect()}가 겹친다", !list[i].value.rect().overlaps(list[j].value.rect()))
    }
  }

  /** 누를 수 있는 모든 대상(OnClick)의 폭 · 높이. 48dp 미만 목록을 돌려준다. */
  private fun smallTargets(): List<String> =
    composeTestRule.onAllNodes(SemanticsMatcher("clickable") { SemanticsActions.OnClick in it.config }).fetchSemanticsNodes()
      .filter { it.size.width / density < 47.5f || it.size.height / density < 47.5f }
      .map { "${tagOf(it).ifEmpty { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }} ${it.size.width / density}×${it.size.height / density}dp" }

  // ── §6 목표: 첫 화면(스크롤 없이) 기록 카드 3개 이상 ──

  @Test
  fun firstScreen_showsThreeRecordCards_withoutScrolling() {
    open(startedBook())
    val screenH = composeTestRule.onRoot().fetchSemanticsNode().size.height
    val fabTop = byTag("add_diary_button").rect().top
    val cards = diaryCards()
    assertEquals(3, cards.size)
    for (c in cards) {
      val r = c.rect()
      println("첫 화면 ${tagOf(c)} 아래 끝 = ${r.bottom / density}dp (FAB 위 끝 ${fabTop / density}dp · 화면 ${screenH / density}dp)")
      assertTrue("${tagOf(c)} 위 끝이 화면 밖", r.top >= 0f)
      assertTrue("${tagOf(c)} 아래 끝 ${r.bottom} > 화면 높이 $screenH(스크롤해야 보인다)", r.bottom <= screenH)
      assertTrue("${tagOf(c)} 아래 끝 ${r.bottom}이 FAB(${fabTop}) 뒤로 들어간다", r.bottom <= fabTop)
    }
    // 상단 바 제목 = 책 제목 · 기록 머리 = «기록 N»
    assertEquals("지어낸 책 제목", byTag("book_detail_title").config[SemanticsProperties.Text].joinToString { it.text })
    assertEquals("기록 3", byTag("diary_list_header").config[SemanticsProperties.Text].joinToString { it.text })
    assertTrue(composeTestRule.onAllNodesWithText("도서 상세 정보").fetchSemanticsNodes().isEmpty())
    // 진척도 한 줄: 슬라이더와 «96 / 320 쪽»이 한 줄(같은 높이 띠)에 있다.
    val slider = byTag("progress_slider").rect()
    val pageText = byTag("progress_page_text").rect()
    assertEquals("96 / 320 쪽", byTag("progress_page_text").config[SemanticsProperties.Text].joinToString { it.text })
    assertTrue("슬라이더 $slider · 쪽 $pageText 가 한 줄이 아니다", slider.top < pageText.bottom && pageText.top < slider.bottom)
    // FAB는 아이콘 + 글자 «기록 쓰기»(확장형) 하나.
    assertTrue(composeTestRule.onAllNodesWithText("기록 쓰기", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
  }

  // ── 보정 4: 카드 아이콘 48dp 대상 + 한국어 낭독 이름 · 48dp 미만 누르는 대상 0 ──

  @Test
  fun recordCardIcons_are48dpTargets_withKoreanNames() {
    open(startedBook())
    for (label in listOf("기록 고치기", "기록 지우기")) {
      val nodes = composeTestRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes()
      assertEquals("$label 카드마다 하나", 3, nodes.size)
      for (n in nodes) {
        assertTrue("$label 폭 ${n.size.width / density}dp < 48", n.size.width / density >= 47.5f)
        assertTrue("$label 높이 ${n.size.height / density}dp < 48", n.size.height / density >= 47.5f)
      }
    }
    assertTrue(composeTestRule.onAllNodesWithContentDescription("Edit entry").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithContentDescription("Delete entry").fetchSemanticsNodes().isEmpty())
    assertTrue("시작 시각 고치기 이름", composeTestRule.onAllNodesWithContentDescription("시작 시각 고치기").fetchSemanticsNodes().isNotEmpty())
    assertTrue("더 보기 이름", composeTestRule.onAllNodesWithContentDescription("더 보기").fetchSemanticsNodes().isNotEmpty())
    val small = smallTargets()
    println("48dp 미만 누르는 대상: ${small.size} $small")
    assertTrue("48dp 미만 누르는 대상: $small", small.isEmpty())

    // 지우기는 확인 다이얼로그를 거친다.
    composeTestRule.onAllNodesWithContentDescription("기록 지우기").fetchSemanticsNodes().first().let { n ->
      composeTestRule.onNode(SemanticsMatcher("first delete") { it.id == n.id }).performClick()
    }
    idle()
    assertTrue(composeTestRule.onAllNodesWithText("기록 삭제").fetchSemanticsNodes().isNotEmpty())
    assertEquals("확인 전에는 지우지 않는다", 3, diaryCards().size)
  }

  // ── 보정 2: «96 / 320 쪽»을 누르면 쪽 수를 직접 넣는다 ──

  @Test
  fun tappingThePageNumber_opensAnInput_andSavesThePage() {
    val id = startedBook()
    open(id)
    composeTestRule.onNodeWithTag("progress_page_text").performClick()
    pump { has("progress_page_input") }
    composeTestRule.onNodeWithTag("progress_page_input").performTextReplacement("400")
    idle()
    composeTestRule.onNodeWithTag("progress_page_save").assertIsNotEnabledCompat()
    composeTestRule.onNodeWithTag("progress_page_input").performTextReplacement("123")
    idle()
    composeTestRule.onNodeWithTag("progress_page_save").performClick()
    pump { stored(id).currentPage == 123 }
    pump { !has("progress_page_input") }
    pump { byTag("progress_page_text").config[SemanticsProperties.Text].joinToString { it.text } == "123 / 320 쪽" }
    assertEquals("READING", stored(id).status)
  }

  @Test
  fun diag_pageDialog_cancel() {
    val id = startedBook()
    open(id)
    composeTestRule.onNodeWithTag("progress_page_text").performClick()
    pump { has("progress_page_input") }
    composeTestRule.onNodeWithText("취소").performClick()
    pump { !has("progress_page_input") }
  }

  @Test
  fun diag_pageDialog_saveWithoutTyping() {
    val id = startedBook()
    open(id)
    composeTestRule.onNodeWithTag("progress_page_text").performClick()
    pump { has("progress_page_input") }
    composeTestRule.onNodeWithTag("progress_page_save").performClick()
    pump { !has("progress_page_input") }
  }

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledCompat() {
    val n = fetchSemanticsNode()
    assertTrue("저장이 켜져 있다(범위 밖 쪽)", SemanticsProperties.Disabled in n.config)
  }

  // ── 시작 전: «읽기 시작» 카드만, FAB · 진척도 없음 ──

  @Test
  fun notStartedBook_hasNoFabAndNoProgress() {
    open(newBook(), cards = 0)
    pump { has("start_reading_button") }
    assertFalse("시작 전에는 FAB(기록 쓰기)가 없다", has("add_diary_button"))
    assertTrue(composeTestRule.onAllNodesWithText("기록 쓰기", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    assertFalse("시작 전에는 진척도가 없다", has("progress_slider"))
    assertFalse(has("progress_page_text"))
    assertTrue("48dp 미만: ${smallTargets()}", smallTargets().isEmpty())
    noSmallText("시작 전")
  }

  // ── 12sp 이하 글자 0(최소 13sp) ──

  private fun noSmallText(where: String) {
    for (n in textNodes()) {
      val l = layout(n) ?: continue
      val sp = l.layoutInput.style.fontSize.value
      assertTrue("$where: «${l.layoutInput.text.take(20)}» ${sp}sp < 13sp", sp >= 13f)
    }
  }

  @Test
  fun noTextAt12spOrBelow() {
    open(startedBook())
    noSmallText("기록 3건")
  }

  // ── 보정 1: 글꼴 1.3 · 2.0배 잘림 0 · 겹침 0 ──

  private fun fontScale_noClipNoOverlap(scale: Float, shot: String) {
    open(startedBook(), fontScale = scale, cards = 1)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$shot.png")
    val where = "글꼴 ${scale}배"
    val screenW = composeTestRule.onRoot().fetchSemanticsNode().size.width
    assertNoOverlap("$where 상단 바", mapOf("뒤로" to byLabel("뒤로가기"), "제목" to byTag("book_detail_title"), "더 보기" to byTag("more_options_button")))
    assertNoOverlap("$where 머리", mapOf("시작" to byTag("reading_start_text"), "✎" to byTag("reading_start_edit"), "저자" to byTag("book_detail_author")))
    println("$where 슬라이더 노드 ${byTag("progress_slider").rect()} · 칸 ${byTag("progress_slider_area").rect()} · 쪽 ${byTag("progress_page_text").rect()}")
    assertNoOverlap("$where 진척도", mapOf("슬라이더 칸" to byTag("progress_slider_area"), "쪽" to byTag("progress_page_text")))
    assertTrue("$where: 진척도 쪽 글이 화면 밖", byTag("progress_page_text").rect().right <= screenW + 0.5f)
    assertTrue("$where: 슬라이더 칸 폭 ${byTag("progress_slider_area").size.width / density}dp < 48", byTag("progress_slider_area").size.width / density >= 48f)
    // 보이는 첫 카드의 머리 줄: 날짜 · 쪽 글과 아이콘 둘이 겹치지 않는다.
    val first = tagOf(diaryCards().first()).removePrefix("diary_item_")
    assertNoOverlap("$where 카드 머리", mapOf("날짜" to byTag("diary_meta_$first"), "고치기" to byTag("diary_edit_$first"), "지우기" to byTag("diary_delete_$first")))
    noTextOverflows(where)
    assertTrue(smallTargets().isEmpty())
  }

  @Test fun fontScale130() = fontScale_noClipNoOverlap(1.3f, "book_detail_font130")
  @Test fun fontScale200() = fontScale_noClipNoOverlap(2f, "book_detail_font200")

  // ── 보정 5: 밝은(1) + 어두운(Dark 9) 테마 기준 이미지 ──

  @Test
  fun light_screenshot() {
    open(startedBook())
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/book_detail_light.png")
  }

  @Test
  fun dark_screenshot() {
    open(startedBook(), themeId = RecordScreenHarness.DARK)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/book_detail_dark.png")
  }
}
