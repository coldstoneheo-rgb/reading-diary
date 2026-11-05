package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.api.SearchResultBook
import com.example.data.api.kakaoHttpErrorText
import com.example.ui.screens.BookSearchContent
import com.example.ui.screens.NAVER_SHUTDOWN_NOTICE
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 도서 검색 화면 기준 이미지. 표지 주소를 비워 이미지 요청도 0이다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class BookSearchScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val books = listOf(
    SearchResultBook("녹색절벽", "김작가, 이작가", 250, ""),
    SearchResultBook("녹색절벽 2", "김작가", 250, "")
  )

  private fun show(
    results: List<SearchResultBook> = emptyList(),
    error: String? = null,
    notice: String? = null,
    searchedWithKakao: Boolean? = true,
    onManualEntry: () -> Unit = {},
    onPick: (SearchResultBook) -> Unit = {}
  ) {
    composeTestRule.setContent {
      MyApplicationTheme {
        BookSearchContent(
          query = "녹색절벽", onQueryChange = {}, onScanClick = {}, onSearchAction = {},
          onManualEntry = onManualEntry, onPick = onPick, isSearching = false,
          results = results, error = error, notice = notice, searchedWithKakao = searchedWithKakao
        )
      }
    }
  }

  @Test
  fun results_screenshot() {
    var picked: SearchResultBook? = null
    show(results = books, onPick = { picked = it })
    composeTestRule.onNodeWithTag("search_fallback_notice").assertDoesNotExist()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/book_search_results.png")
    composeTestRule.onNodeWithText("저자 / 작가: 김작가, 이작가").performClick()
    assertEquals(books[0], picked)
  }

  @Test
  fun googleFallbackWithCause_screenshot() {
    show(results = books, notice = kakaoHttpErrorText(401) + " 이번 결과는 Google 도서에서 가져왔습니다.")
    composeTestRule.onNodeWithTag("search_fallback_notice").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/book_search_fallback_notice.png")
  }

  @Test
  fun errorCard_showsCauseAndNaverNotice_screenshot() {
    var manual = 0
    show(error = kakaoHttpErrorText(403), onManualEntry = { manual++ })
    composeTestRule.onNodeWithText(NAVER_SHUTDOWN_NOTICE, substring = true).assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/book_search_error.png")
    composeTestRule.onNodeWithTag("search_manual_entry_button").performClick()
    assertEquals(1, manual)
  }
}
