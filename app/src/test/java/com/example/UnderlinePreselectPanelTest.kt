package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsProperties
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.data.ocr.UnderlinePreselect.Word
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.screens.UnderlinePreselectPanel
import com.example.ui.screens.defaultGlued
import com.example.ui.screens.lineJoins
import com.example.ui.screens.preselectedText
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import com.example.data.Diary
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 밑줄 미리 선택 패널(SPIKE §4 PR 3). 글은 지어낸 견본이다(실제 책 쪽 아님).
 * 미리 선택 0개 = 기존 화면 그대로 · 편집창 글은 버튼을 눌렀을 때만 바뀐다 · 사용자가 쓴 글을 미리 선택이 덮지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class UnderlinePreselectPanelTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  /** 견본: 줄 0 «작은 새가 아침마다 창가에 온다» 중 «아침마다 창가에» · 줄 1 «바람이 분다» 미리 선택 0 · 줄 2 «나는 그 소리를 듣는다» 중 «소리를». */
  private val sample = listOf(
    Word("작은", 0, false), Word("새가", 0, false), Word("아침마다", 0, true), Word("창가에", 0, true), Word("온다.", 0, false),
    Word("바람이", 1, false), Word("분다.", 1, false),
    Word("나는", 2, false), Word("그", 2, false), Word("소리를", 2, true), Word("듣는다.", 2, false)
  )

  private var bookId = 0
  /** 기록 시각이 고정된 기존 기록(스크린샷이 실행 시각에 흔들리지 않게). 글 = 사용자가 이미 쓴 글. */
  private var diaryId = 0
  private lateinit var savedTimeZone: java.util.TimeZone

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "화면 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "화면 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
    diaryId = db.diaryDao().insert(Diary(bookId = bookId, page = 12, selectedText = "내가 쓴 글", notes = "", createdAt = 1_700_000_000_000L)).toInt()
    savedTimeZone = java.util.TimeZone.getDefault()
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
  }

  @After
  fun tearDown() { java.util.TimeZone.setDefault(savedTimeZone) }

  @Test
  fun noPreselect_drawsNothing() {
    // setContent는 테스트마다 한 번 — 값만 바꿔 가며 본다.
    var words by mutableStateOf<List<Word>?>(null)
    composeTestRule.setContent {
      MyApplicationTheme { UnderlinePreselectPanel(words, words?.map { it.selected } ?: emptyList(), {}, {}) }
    }
    for (w in listOf(null, emptyList(), sample.map { it.copy(selected = false) })) {
      words = w
      composeTestRule.waitForIdle()
      composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(0)
    }
    // 판별력 확인: 하나라도 선택되면 그린다.
    words = sample
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(1)
  }

  @Test
  fun preselectedText_joinsChosenWordsInEngineOrder() {
    assertEquals("아침마다 창가에 소리를", preselectedText(sample, sample.map { it.selected }))
    assertEquals("", preselectedText(sample, sample.map { false }))
  }

  /** 기존 기록 편집 화면(시각 고정). 편집창에 그 기록의 글이 들어온 뒤 돌려준다. */
  private fun screen(vm: ReadingViewModel) {
    composeTestRule.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = diaryId) } }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes()
        .any { androidx.compose.ui.semantics.SemanticsProperties.EditableText in it.config && it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == "내가 쓴 글" }
    }
  }

  @Test
  fun screen_zeroPreselect_isTheSameAsBefore() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    screen(vm)
    val input = composeTestRule.onNodeWithTag("diary_extracted_text_input")
    input.performScrollTo()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/preselect_before_none.png")
    // 편집창 아래 요소의 위치가 그대로인지로 «아무것도 더해지지 않음»을 본다.
    // 화면 밖 노드의 boundsInRoot는 잘려 0이 되므로(빈 단언) 스크롤해 보이게 한 뒤 잘리지 않는 위치로 견준다.
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo()
    fun saveButtonBounds() = composeTestRule.onNodeWithTag("diary_notes_input").fetchSemanticsNode().positionInRoot
    val before = saveButtonBounds()
    org.junit.Assert.assertTrue("$before", before.y > 0f)
    // 미리 선택이 와도 고른 어절이 0개면 화면에 아무것도 더해지지 않는다(패널 노드 0 · 아래 요소 위치 그대로).
    vm.underlinePreselect.value = sample.map { it.copy(selected = false) }
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(0)
    assertEquals(before, saveButtonBounds())
    input.assertTextEquals("내가 쓴 글", includeEditableText = true)
    // 판별력: 하나라도 선택되면 패널이 생긴다.
    vm.underlinePreselect.value = sample
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(1)
  }

  @Test
  fun screen_recreation_dropsThePanelWithTheRecognitionResult_andKeepsTheText() {
    // 화면 재생성(회전 등)은 기존처럼 인식 상태를 비운다(DisposableEffect → resetOcrState → 미리 선택 null).
    // 패널은 사라지고, 편집창 글(rememberSaveable)은 남는다 — 낡은 칩이 새 화면에 남지 않는다.
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    val restorer = StateRestorationTester(composeTestRule)
    restorer.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = null) } }
    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithTag("save_diary_submit_button").fetchSemanticsNodes().isNotEmpty() }
    vm.underlinePreselect.value = sample
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preselect_apply_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").assertTextEquals("아침마다 창가에 소리를", includeEditableText = true)
    restorer.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(0)
    composeTestRule.onNodeWithTag("diary_extracted_text_input").assertTextEquals("아침마다 창가에 소리를", includeEditableText = true)
  }

  @Test
  fun screen_pickingAnotherPhoto_clearsThePanel() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    val registryOwner = object : ActivityResultRegistryOwner {
      override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
          dispatchResult(requestCode, Uri.parse("content://photos/another"))
        }
      }
    }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = null) }
      }
    }
    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithTag("save_diary_submit_button").fetchSemanticsNodes().isNotEmpty() }
    vm.underlinePreselect.value = sample
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(1)
    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("underline_preselect_panel").assertCountEquals(0)
  }

  @Test
  fun screen_preselectShownAndEditable_textChangesOnlyOnApply() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    screen(vm)
    val input = composeTestRule.onNodeWithTag("diary_extracted_text_input")
    input.performScrollTo()

    vm.underlinePreselect.value = sample
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("underline_preselect_panel").performScrollTo()
    // 사용자가 쓴 글은 미리 선택이 와도 그대로다(자동 반영 없음).
    input.assertTextEquals("내가 쓴 글", includeEditableText = true)
    composeTestRule.onNodeWithTag("preselect_word_2").assertIsSelected()
    composeTestRule.onNodeWithTag("preselect_word_0").assertIsNotSelected()
    // 미리 선택이 없는 줄(줄 1)은 칩을 그리지 않는다.
    composeTestRule.onAllNodesWithTag("preselect_word_5").assertCountEquals(0)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/preselect_after_panel.png")

    // 고치기: «소리를» 끄고 «작은» 켜기 → 아직 글은 그대로.
    composeTestRule.onNodeWithTag("preselect_word_9").performClick()
    composeTestRule.onNodeWithTag("preselect_word_0").performClick()
    composeTestRule.onNodeWithTag("preselect_word_9").assertIsNotSelected()
    input.assertTextEquals("내가 쓴 글", includeEditableText = true)

    composeTestRule.onNodeWithTag("preselect_apply_button").performScrollTo().performClick()
    input.assertTextEquals("작은 아침마다 창가에", includeEditableText = true)
  }

  // ── 줄 이음(ADR-002 Q4 개정 · 2026-11-28): 기본 붙임 · 앞 줄 끝이 문장부호면 띄움 · 자리마다 사람이 바꾼다 ──

  /** 지어낸 견본: 줄 0 «푸른 하늘» / 줄 1 «을 보았다,» / 줄 2 «바람이 분다.» / 줄 3 «나는» — 모두 켜짐. */
  private val joined = listOf(
    Word("푸른", 0, true), Word("하늘", 0, true), Word("을", 1, true), Word("보았다,", 1, true),
    Word("바람이", 2, true), Word("분다.", 2, true), Word("나는", 3, true)
  )

  @Test
  fun lineJoin_defaultGlues_unlessPrevEndsWithPunctuation() {
    val all = joined.map { true }
    assertEquals(listOf(2, 4, 6), lineJoins(joined, all))
    assertEquals("푸른 하늘을 보았다, 바람이 분다. 나는", preselectedText(joined, all))
    // 사람이 바꾼 자리만 뒤집힌다.
    assertEquals("푸른 하늘 을 보았다, 바람이 분다. 나는", preselectedText(joined, all, setOf(2)))
    assertEquals("푸른 하늘을 보았다,바람이 분다. 나는", preselectedText(joined, all, setOf(4)))
    // 앞 줄 끝 어절이 꺼져 있으면 이음 자리가 아니다(떨어진 조각 = 공백).
    val gap = all.toMutableList().also { it[1] = false }
    assertEquals(listOf(4, 6), lineJoins(joined, gap))
    assertEquals("푸른 을 보았다, 바람이 분다. 나는", preselectedText(joined, gap))
    // 닫는 따옴표 · 괄호 뒤의 문장부호도 띄움.
    assertTrue(!defaultGlued("말했다.”"))
    assertTrue(!defaultGlued("(그렇다)."))
    assertTrue(defaultGlued("하늘"))
    // 닫는 괄호 · 곧은 따옴표가 마침표 뒤에 올 때도 띄움.
    assertTrue(!defaultGlued("그렇다.)"))
    assertTrue(!defaultGlued("그렇다.\""))
    // 전각 부호 · 말줄임도 문장부호.
    listOf("정말？", "정말！", "하나，", "그렇다．", "그런데⋯", "그런데‥").forEach { assertTrue(it, !defaultGlued(it)) }
    // 가운뎃점은 앞뒤를 붙여 쓰는 부호 → 줄을 넘어도 붙임.
    assertTrue(defaultGlued("봄·"))
  }

  @Test
  fun screen_lineJoin_visibleTogglable_andSavedTextIsThePreview() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    screen(vm)
    vm.underlinePreselect.value = joined
    composeTestRule.waitForIdle()
    val join = composeTestRule.onNodeWithTag("preselect_join_2", useUnmergedTree = false)
    join.performScrollTo()
    val node = join.fetchSemanticsNode()
    assertEquals("붙임", node.config[SemanticsProperties.StateDescription])
    assertEquals("줄 이음: 앞 줄 끝 «하늘», 다음 줄 «을»", node.config[SemanticsProperties.ContentDescription].joinToString())
    val d = app.resources.displayMetrics.density
    assertTrue("이음 표시 누름 높이 ${node.touchBoundsInRoot.height / d}dp < 48", node.touchBoundsInRoot.height / d >= 47.5f)
    composeTestRule.onNodeWithTag("preselect_join_4").assertExists()
    assertEquals("띄움", composeTestRule.onNodeWithTag("preselect_join_4").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    composeTestRule.onNodeWithTag("preselect_preview").assertTextEquals("푸른 하늘을 보았다, 바람이 분다. 나는")
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/preselect_line_join.png")

    join.performClick()
    composeTestRule.waitForIdle()
    assertEquals("띄움", composeTestRule.onNodeWithTag("preselect_join_2").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    val preview = "푸른 하늘 을 보았다, 바람이 분다. 나는"
    composeTestRule.onNodeWithTag("preselect_preview").assertTextEquals(preview)

    composeTestRule.onNodeWithTag("preselect_apply_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").assertTextEquals(preview, includeEditableText = true)
    composeTestRule.onNodeWithTag("save_diary_submit_button").performClick()
    composeTestRule.waitUntil(10_000) {
      runBlocking { db.diaryDao().getAllDiaries().first() }.any { it.id == diaryId && it.selectedText == preview }
    }
  }

  @Test
  fun screen_newPreselectValue_resetsFlippedJoins() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    screen(vm)
    vm.underlinePreselect.value = joined
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preselect_join_2").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("띄움", composeTestRule.onNodeWithTag("preselect_join_2").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    // 다른 인식 결과가 왔다가 같은 견본이 다시 오면(새 인식) 바꾼 자리는 기본값으로 돌아간다.
    vm.underlinePreselect.value = sample
    composeTestRule.waitForIdle()
    vm.underlinePreselect.value = joined.map { it.copy() }
    composeTestRule.waitForIdle()
    assertEquals("붙임", composeTestRule.onNodeWithTag("preselect_join_2").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
  }
}
