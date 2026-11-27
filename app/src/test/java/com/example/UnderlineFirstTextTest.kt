package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.ocr.OcrLine
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.OcrWord
import com.example.data.ocr.TextExtractor
import com.example.data.ocr.UnderlineDetector
import com.example.data.ocr.UnderlinePreselect
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 추출 기본값(HO-2026-1127 조건 3): 밑줄 어절이 있으면 편집창은 **그 어절만**으로 시작하고, 틀 안 전체 글은 접힌 «원문 전체»로 내려간다.
 * 밑줄 어절이 0개면 지금처럼 전체 글이 편집창에 남는다. 글은 지어낸 견본이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UnderlineFirstTextTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private val full = "바람이 부는 언덕에 나무가 서 있다"

  private class FixedExtractor(private val text: String) : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = OcrOutcome.Text(
      text,
      listOf(OcrLine(text, null, text.split(" ").mapIndexed { i, w -> OcrWord(w, UnderlineDetector.Box(i * 10, 0, i * 10 + 8, 8)) }))
    )
  }

  private var bookId = 0

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "추출 기본값")).toInt()
    bookId = db.bookDao().insert(Book(title = "견본 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
  }

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      composeTestRule.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within 10s", condition())
  }

  private fun fieldText(): String = composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes()
    .firstOrNull()?.config?.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.EditableText) { null }?.text.orEmpty()

  /** 새 기록 화면에서 인식을 돌린다. [selected] = 어절 순번마다 미리 선택 값. */
  private fun recognize(selected: (Int) -> Boolean): ReadingViewModel {
    val vm = ReadingViewModel(app, FixedExtractor(full), FixedExtractor(full), FakeDiaryPhotoStore()) { false }
    vm.preselectRunner = { _, lines -> lines.single().words.mapIndexed { i, w -> UnderlinePreselect.Word(w.text, 0, selected(i)) } }
    composeTestRule.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId) } }
    pump { composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.runOnUiThread { vm.processUnderlineOcr(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)) }
    return vm
  }

  @Test
  fun underlinedWords_fillTheEditorFirst_andTheFullTextIsFoldedBelow() {
    recognize { it == 1 || it == 2 }
    pump { fieldText() == "부는 언덕에" }
    // 전체 글은 접혀 있다 — 펼치면 보이고, 편집창을 전체 글로 되돌릴 수 있다.
    composeTestRule.onAllNodesWithTag("ocr_full_text").assertCountEquals(0)
    composeTestRule.onNodeWithTag("ocr_full_text_toggle").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("ocr_full_text").performScrollTo().assertTextEquals(full)
    composeTestRule.onNodeWithTag("ocr_full_text_apply").performScrollTo().performClick()
    pump { fieldText() == full }
  }

  @Test
  fun noUnderlinedWord_keepsTheFullText_andShowsNoFoldedSection() {
    val vm = recognize { false }
    pump { vm.underlinePreselect.value != null && fieldText() == full }
    repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
    assertTrue(fieldText() == full)
    composeTestRule.onAllNodesWithTag("ocr_full_text_toggle").assertCountEquals(0)
  }
}
