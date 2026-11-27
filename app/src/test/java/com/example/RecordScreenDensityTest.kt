package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.data.ocr.UnderlinePreselect
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.viewmodel.ReadingViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 재설계 R1(기록 쓰기 화면)의 통과 기준(REDESIGN §7-1 보정 6):
 * 첫 화면 단언 · 아이콘 낭독 이름 · 글꼴 1.3 · 2.0배에서 잘림 0 · 겹침 0 · 12sp 이하 글자 0 · 키보드가 떠도 상단 바(저장) 고정 · 되돌리기.
 * 화면 = 411×891dp(사용자 폰과 같은 dp). 글 · 책은 지어낸 견본이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class RecordScreenDensityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun vm() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }

  private val density get() = app.resources.displayMetrics.density

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

  private fun newBook(): Int = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
    db.bookDao().insert(Book(title = "지어낸 책", author = "지은이", totalPages = 320, currentPage = 96, bookcaseId = caseId, status = "READING", startDate = "2026-11-20 09:00")).toInt()
  }

  private val sampleBody = "바람이 부는 언덕에 나무가 서 있고 그 아래로 작은 길이 이어진다. 길 끝에는 오래된 집이 한 채 있다. "

  private fun openNew(themeId: Int = RecordScreenHarness.LIGHT, fontScale: Float = 1f, imeDp: Int = 0, onView: (View) -> Unit = {}): ReadingViewModel {
    val id = newBook()
    val vm = vm()
    composeTestRule.setContent { RecordScreenHarness.Frame(themeId, fontScale, imeDp, onView) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null) } }
    pump { has("diary_page_input") }
    idle()
    return vm
  }

  /** 고치기 화면 + 밑줄 어절 칩(인식 뒤와 같은 모양). */
  private fun openEditWithChips(fontScale: Float = 1f, text: String = sampleBody.repeat(6).take(570), themeId: Int = RecordScreenHarness.LIGHT): ReadingViewModel {
    val id = newBook()
    val diaryId = runBlocking { db.diaryDao().insert(Diary(bookId = id, page = 47, selectedText = text, notes = "", createdAt = 1_795_748_760_000L)).toInt() }
    val vm = vm()
    composeTestRule.setContent { RecordScreenHarness.Frame(themeId = themeId, fontScale = fontScale) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = diaryId) } }
    pump { fieldText() == text }
    return vm
  }

  private fun chips(vm: ReadingViewModel) {
    val words = sampleBody.repeat(2).split(" ").filter { it.isNotBlank() }.take(24)
    composeTestRule.runOnUiThread { vm.underlinePreselect.value = words.mapIndexed { i, w -> UnderlinePreselect.Word(w, i / 8, i in 2..4 || i in 17..19) } }
    pump { has("underline_preselect_panel") }
    idle()
  }

  private fun fieldText(): String = composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes()
    .firstOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

  /** 잘리지 않은 자리(부모 잘림을 적용하지 않은 위치 + 크기). */
  private fun SemanticsNode.rect(): Rect = Rect(positionInRoot, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))

  private fun Rect.overlaps(o: Rect) = left < o.right - 0.5f && o.left < right - 0.5f && top < o.bottom - 0.5f && o.top < bottom - 0.5f
  private fun Rect.inside(o: Rect) = left >= o.left - 0.5f && top >= o.top - 0.5f && right <= o.right + 0.5f && bottom <= o.bottom + 0.5f

  private fun layout(node: SemanticsNode): TextLayoutResult? {
    val out = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
    return out.firstOrNull()
  }

  private fun textNodes(): List<SemanticsNode> =
    composeTestRule.onAllNodes(SemanticsMatcher("text") { SemanticsActions.GetTextLayoutResult in it.config }, useUnmergedTree = true).fetchSemanticsNodes()

  private fun noTextOverflows(where: String) {
    for (n in textNodes()) {
      val l = layout(n) ?: continue
      // didOverflowWidth는 maxLines=1 글이 넓은 제약으로 먼저 재어질 때 실제로 그려진 글 폭과 무관하게 참이 된다 —
      // 그래서 줄마다 실제 글 끝(lineRight)이 글 칸 폭 안인지 · 말줄임이 없는지 · 높이가 넘치지 않는지로 본다.
      val text = l.layoutInput.text.text
      assertTrue("$where: 글 높이가 넘친다 «$text»", !l.didOverflowHeight)
      for (line in 0 until l.lineCount) {
        assertTrue("$where: 글이 잘렸다 «$text» 줄 $line 끝 ${l.getLineRight(line)} > 폭 ${l.size.width}", l.getLineRight(line) <= l.size.width + 1f)
        assertTrue("$where: 말줄임 «$text»", !l.isLineEllipsized(line))
      }
    }
  }

  private fun assertNoOverlap(where: String, nodes: Map<String, SemanticsNode>) {
    val list = nodes.entries.toList()
    for (i in list.indices) for (j in i + 1 until list.size) {
      assertTrue("$where: «${list[i].key}» ${list[i].value.rect()} 와 «${list[j].key}» ${list[j].value.rect()}가 겹친다", !list[i].value.rect().overlaps(list[j].value.rect()))
    }
  }

  private fun byTag(tag: String) = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
  private fun byLabel(label: String) = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode()

  // ── 보정 6: 첫 화면 ──

  @Test
  fun firstScreen_showsDateTimePagePhotoSlotAndPhotoPick_withoutScrolling() {
    openNew()
    val screenH = composeTestRule.onRoot().fetchSemanticsNode().size.height
    val shown = mapOf(
      "날짜" to byTag("diary_date_row"), "시각" to byTag("diary_time_row"), "쪽" to byTag("diary_page_row"),
      "사진 자리" to byTag("photo_slot"), "사진 선택(주 동작)" to byLabel("사진 선택"), "사진 촬영" to byLabel("사진 촬영"), "저장" to byTag("save_diary_submit_button")
    )
    for ((name, n) in shown) {
      val r = n.rect()
      println("첫 화면 $name 아래 끝 = ${r.bottom / density}dp (화면 ${screenH / density}dp)")
      assertTrue("$name 위 끝이 화면 밖", r.top >= 0f)
      assertTrue("$name 아래 끝 ${r.bottom} > 화면 높이 $screenH(스크롤해야 보인다)", r.bottom <= screenH)
    }
    // 안내 카드는 없다(REDESIGN §6).
    assertTrue(composeTestRule.onAllNodesWithText("분석 준비 완료").fetchSemanticsNodes().isEmpty())
    assertEquals("기록 쓰기", composeTestRule.onNodeWithTag("diary_screen_title").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
  }

  // ── 보정 4: 아이콘 낭독 이름(한국어) · 48dp 대상 ──

  @Test
  fun iconActions_haveKoreanNames_and48dpTargets() {
    openEditWithChips(text = "첫 줄\n둘째 줄")
    assertEquals("기록 고치기", composeTestRule.onNodeWithTag("diary_screen_title").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
    for (label in listOf("사진 촬영", "사진 선택", "줄바꿈 해제", "전체 지우기", "더 보기")) {
      val n = byLabel(label)
      assertTrue("$label 폭 ${n.size.width / density}dp < 48", n.size.width / density >= 47.5f)
      assertTrue("$label 높이 ${n.size.height / density}dp < 48", n.size.height / density >= 47.5f)
    }
    // «더 보기» 안: 여백 자동 보정 · 글로 공유하기
    composeTestRule.onNodeWithContentDescription("더 보기").performClick()
    idle()
    assertTrue(has("trim_text_button"))
    assertTrue(has("share_text_button"))
    // 본문에 병기 버튼(아이콘 + 글자)이 남지 않았다.
    assertTrue(composeTestRule.onAllNodesWithText("사진 촬영").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("줄바꿈 해제").fetchSemanticsNodes().isEmpty())
  }

  // ── 되돌리기(줄바꿈 해제 · 전체 지우기) · 저장 비활성 ──

  @Test
  fun unwrapAndClear_canBeUndone_fromTheSnackbar() {
    openEditWithChips(text = "첫 줄\n둘째 줄")
    composeTestRule.onNodeWithContentDescription("줄바꿈 해제").performClick()
    pump { fieldText() == "첫 줄 둘째 줄" }
    pump { composeTestRule.onAllNodesWithText("되돌리기").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithText("되돌리기").performClick()
    pump { fieldText() == "첫 줄\n둘째 줄" }

    composeTestRule.onNodeWithContentDescription("전체 지우기").performClick()
    pump { fieldText() == "" }
    composeTestRule.onNodeWithTag("save_diary_submit_button").assertIsNotEnabled()
    pump { composeTestRule.onAllNodesWithText("되돌리기").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithText("되돌리기").performClick()
    pump { fieldText() == "첫 줄\n둘째 줄" }
  }

  @Test
  fun save_isATopBarTextButton_disabledUntilThereIsText() {
    openNew()
    val save = composeTestRule.onNodeWithTag("save_diary_submit_button")
    save.assertIsNotEnabled()
    assertTrue(composeTestRule.onAllNodesWithText("저장", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("지어낸 문장")
    idle()
    save.assertIsEnabled()
  }

  // ── 12sp 이하 글자 0(최소 13sp · 칩 14sp) ──

  @Test
  fun noTextAt12spOrBelow_andChipsAre14sp() {
    val vm = openEditWithChips()
    chips(vm)
    for (n in textNodes()) {
      val l = layout(n) ?: continue
      val sp = l.layoutInput.style.fontSize.value
      assertTrue("«${l.layoutInput.text.take(20)}» ${sp}sp < 13sp", sp >= 13f)
    }
    val chip = composeTestRule.onAllNodes(SemanticsMatcher("chip text") { SemanticsActions.GetTextLayoutResult in it.config }, useUnmergedTree = true)
      .fetchSemanticsNodes().mapNotNull { layout(it) }.first { it.layoutInput.text.text == "언덕에" }
    assertEquals(14f, chip.layoutInput.style.fontSize.value, 0.01f)
  }

  // ── 보정 1: 글꼴 1.3 · 2.0배에서 48dp 줄 · 상단 바 · 칩의 잘림 0 · 겹침 0 ──

  // 기준 이미지는 시각이 고정된 고치기 화면으로 찍는다(새 기록은 지금 시각을 보여 이미지가 매번 달라진다). 배치는 새 기록과 같고 제목이 더 길다.
  private fun rowsAndTopBar_noClipNoOverlap(scale: Float, shot: String) {
    openEditWithChips(fontScale = scale, text = "지어낸 문장 한 줄")
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$shot.png")
    val where = "글꼴 ${scale}배"
    for (tag in listOf("diary_date_row", "diary_time_row", "diary_page_row", "photo_slot")) {
      val row = byTag(tag)
      assertTrue("$where: $tag 높이 ${row.size.height / density}dp < 48", row.size.height / density >= 47.5f)
      // 줄 안 글(이름표 · 값)은 줄 밖으로 나가지 않고 서로 겹치지 않는다.
      val parts = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().children
        .flatMap { c -> listOf(c) + c.children }.filter { SemanticsActions.GetTextLayoutResult in it.config }
      assertTrue("$where: $tag 안 글 없음", parts.isNotEmpty())
      for (p in parts) assertTrue("$where: $tag 안 글 ${p.rect()}이 줄 ${row.rect()} 밖", p.rect().inside(row.rect()))
      // 이름표(첫 글)는 값 쪽 글과 겹치지 않는다(값 칸 안의 자리표시 글은 값 칸 자신과 겹치므로 이름표하고만 견준다).
      val label = parts.first()
      for (p in parts.drop(1)) assertTrue("$where: $tag 이름표 ${label.rect()}와 값 ${p.rect()}가 겹친다", !label.rect().overlaps(p.rect()))
    }
    assertNoOverlap("$where 입력 줄", listOf("diary_date_row", "diary_time_row", "diary_page_row", "photo_slot").associateWith { byTag(it) })
    assertNoOverlap(
      "$where 상단 바",
      mapOf("뒤로" to byLabel("뒤로가기"), "제목" to byTag("diary_screen_title"), "사진 촬영" to byLabel("사진 촬영"), "사진 선택" to byLabel("사진 선택"), "저장" to byTag("save_diary_submit_button"))
    )
    noTextOverflows(where)
  }

  private fun chips_noClipNoOverlap(scale: Float, shot: String) {
    val vm = openEditWithChips(fontScale = scale)
    chips(vm)
    composeTestRule.onNodeWithTag("underline_preselect_panel").performScrollTo()
    idle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$shot.png")
    val where = "글꼴 ${scale}배 칩"
    val panel = byTag("underline_preselect_panel")
    val chipNodes = composeTestRule.onAllNodes(SemanticsMatcher("chip") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("preselect_word_") == true })
      .fetchSemanticsNodes().associateBy { it.config[SemanticsProperties.TestTag] }
    assertEquals("보이는 칩(고른 어절이 있는 두 줄 × 8)", 16, chipNodes.size)
    for ((tag, c) in chipNodes) assertTrue("$where: $tag ${c.rect()}이 패널 ${panel.rect()} 밖", c.rect().inside(panel.rect()))
    assertNoOverlap(where, chipNodes)
    noTextOverflows(where)
  }

  @Test fun fontScale130_rowsAndTopBar() = rowsAndTopBar_noClipNoOverlap(1.3f, "record_top_font130")
  @Test fun fontScale200_rowsAndTopBar() = rowsAndTopBar_noClipNoOverlap(2f, "record_top_font200")
  @Test fun fontScale130_chips() = chips_noClipNoOverlap(1.3f, "record_chips_font130")
  @Test fun fontScale200_chips() = chips_noClipNoOverlap(2f, "record_chips_font200")

  // ── 보정 5: 밝은 + 어두운(Dark) 테마 기준 이미지 ──

  @Test
  fun light_screenshot() {
    openEditWithChips(text = "지어낸 문장 한 줄")
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/record_top_light.png")
  }

  @Test
  fun dark_screenshot() {
    openEditWithChips(text = "지어낸 문장 한 줄", themeId = RecordScreenHarness.DARK)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/record_top_dark.png")
  }

  // ── (f) 키보드가 떠도 상단 바(저장)는 제자리, 내용만 키보드 위까지 줄어든다 ──

  @Test
  fun keyboardUp_keepsTheTopBar_andShrinksOnlyTheContent() {
    var view: View? = null
    var shown by mutableStateOf(true)
    val id = newBook()
    val vm = vm()
    val original = composeTestRule.activity.window.attributes.softInputMode
    composeTestRule.setContent {
      RecordScreenHarness.Frame(imeDp = RecordScreenHarness.IME_DP, onView = { view = it }) {
        if (shown) OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null)
      }
    }
    pump { has("diary_page_input") }
    val window = composeTestRule.activity.window
    // 창 단위: 이 화면에 있는 동안 adjustResize(밀어 올림 없음).
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)

    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("지어낸 문장")
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo().performClick()
    idle()
    val saveBefore = byTag("save_diary_submit_button").rect()
    val screenH = composeTestRule.onRoot().fetchSemanticsNode().size.height
    val imePx = (RecordScreenHarness.IME_DP * density).toInt()
    composeTestRule.runOnUiThread { RecordScreenHarness.sendIme(view!!, imePx) }
    idle()
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo()
    idle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/record_keyboard.png")

    val save = byTag("save_diary_submit_button")
    assertEquals("키보드가 떠도 저장 자리가 그대로", saveBefore, save.rect())
    assertTrue("저장이 화면 밖", save.rect().top >= 0f)
    composeTestRule.onNodeWithTag("save_diary_submit_button").assertIsEnabled()
    val content = byTag("diary_form_scroll").rect()
    println("키보드 ${RecordScreenHarness.IME_DP}dp: 내용 칸 아래 끝 ${content.bottom / density}dp · 키보드 위 끝 ${(screenH - imePx) / density}dp")
    assertTrue("내용 칸 아래 끝 ${content.bottom}이 키보드 위 끝 ${screenH - imePx} 아래로 들어간다", content.bottom <= screenH - imePx + 1f)
    val notes = byTag("diary_notes_input").rect()
    assertTrue("쓰는 칸(메모) ${notes}이 키보드에 가린다", notes.bottom <= screenH - imePx + 1f && notes.top >= save.rect().bottom)

    // 화면을 떠나면 창 설정을 되돌린다.
    composeTestRule.runOnUiThread { RecordScreenHarness.sendIme(view!!, 0) }
    shown = false
    idle()
    assertEquals("떠난 뒤 창 설정이 처음 값으로", original, window.attributes.softInputMode)
  }
}
