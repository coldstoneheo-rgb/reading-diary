package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 편집창 크기(HO-2026-1127 조건 5): 500자 글에서 보이는 줄 수가 이전 값(115dp 고정 = 4줄)의 두 배 이상.
 * 줄 수 = (칸 높이 − 위아래 안쪽 여백 32dp) ÷ 본문 줄 높이 20sp(bodyMedium). 글꼴 배율 1.0 전제(선형 환산). 글은 지어낸 견본이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExtractedTextEditorSizeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun visibleLines(text: String, shot: String? = null): Double {
    val (bookId, diaryId) = runBlocking {
      val caseId = db.bookcaseDao().insert(Bookcase(name = "편집창")).toInt()
      val b = db.bookDao().insert(Book(title = "견본", author = "", totalPages = 100, bookcaseId = caseId, status = "READING", startDate = "2026-11-27")).toInt()
      b to db.diaryDao().insert(Diary(bookId = b, page = 1, selectedText = text, notes = "", createdAt = 1_700_000_000_000L)).toInt()
    }
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = diaryId) } }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes()
        .any { androidx.compose.ui.semantics.SemanticsProperties.EditableText in it.config && it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == text }
    }
    val node = composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().fetchSemanticsNode()
    if (shot != null) composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$shot.png")
    val density = node.layoutInfo.density
    val heightDp = node.size.height / density.density
    val lineDp = 20f * density.fontScale
    return ((heightDp - 32f) / lineDp).toDouble()
  }

  private val fiveHundred = "바람이 부는 언덕에 나무가 서 있고 그 아래로 작은 길이 이어진다. ".repeat(14).take(500)

  @Test
  fun fiveHundredChars_showAtLeastTwiceTheOldFourLines() {
    val lines = visibleLines(fiveHundred)
    println("편집창에 보이는 줄(500자) = ${"%.1f".format(lines)}")
    assertTrue("보이는 줄 $lines < 8", lines >= 8.0)
    // 상한: 칸이 글 길이만큼 끝없이 늘지 않는다(500자 ≈ 22줄).
    assertTrue("보이는 줄 $lines > 상한 12", lines <= com.example.ui.screens.EXTRACTED_TEXT_MAX_LINES + 0.5)
  }

  /** 기준 이미지(verifyRoborazziDebug에서만 비교). 크기 단언은 위 시험이 맡는다 — 이미지가 바뀌어도 줄 수 판정은 따로 읽힌다. */
  @Test
  fun fiveHundredChars_screenshot() {
    visibleLines(fiveHundred, shot = "extracted_editor_500")
  }

  @Test
  fun shortText_keepsTheSixLineFloor() {
    val lines = visibleLines("짧은 글")
    println("편집창에 보이는 줄(짧은 글) = ${"%.1f".format(lines)}")
    assertTrue("보이는 줄 $lines < 5.5", lines >= 5.5)
    assertTrue("짧은 글인데 칸이 상한까지 늘었다: $lines", lines < 8.0)
  }
}
