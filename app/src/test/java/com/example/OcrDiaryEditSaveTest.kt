package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-003-2 회귀: 편집 모드는 예전에 스톡 사진 URL을 "찍은 사진"처럼 띄워 두고 열렸다. 그 URL을 지운 뒤에도
 * 기존 일기를 열어 문장을 고치고 저장하는 흐름이 그대로 동작해야 한다(사진 없이).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrDiaryEditSaveTest {

  @get:Rule val composeTestRule = createComposeRule()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("편집 저장 흐름에서 추출을 부르면 안 된다")
  }

  @Test
  fun editMode_opensWithoutPhoto_andSavesEditedText() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val (bookId, diaryId) = runBlocking {
      val caseId = db.bookcaseDao().insert(Bookcase(name = "편집 테스트")).toInt()
      val b = db.bookDao().insert(
        Book(title = "편집 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")
      ).toInt()
      val d = db.diaryDao().insert(Diary(bookId = b, page = 10, selectedText = "고치기 전 문장", notes = "메모")).toInt()
      b to d
    }
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor) { false }

    composeTestRule.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = diaryId) } }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithText("고치기 전 문장").fetchSemanticsNodes().isNotEmpty()
    }

    // 사진 없이 열린다: 인식 버튼(사진을 자른 뒤에만 보임)이 없다
    composeTestRule.onNodeWithTag("ocr_button").assertDoesNotExist()

    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("고친 문장")
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().assertIsDisplayed().performClick()

    composeTestRule.waitUntil(10_000) {
      runBlocking { db.diaryDao().getAllDiaries().first() }.any { it.id == diaryId && it.selectedText == "고친 문장" }
    }
    val saved = runBlocking { db.diaryDao().getAllDiaries().first() }.single { it.id == diaryId }
    assertEquals(10, saved.page)
    assertEquals("메모", saved.notes)
  }
}
