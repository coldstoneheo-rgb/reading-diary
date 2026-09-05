package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.screens.BookSearchField
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 검색창 동작(ADR-003 실행 8): 스캔 버튼은 항상 보이고, 지우기는 검색어가 있을 때만. 스캐너 실행 자체는 실기기에서 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BookSearchFieldTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun scanButtonAlwaysVisible_clearOnlyWhenQueryPresent_andScanClickIsReported() {
    var scanClicks = 0
    composeTestRule.setContent {
      var query by androidx.compose.runtime.remember { mutableStateOf("") }
      MyApplicationTheme {
        BookSearchField(query = query, onQueryChange = { query = it }, onScanClick = { scanClicks++ }, onSearchAction = {})
      }
    }

    composeTestRule.onNodeWithTag("search_isbn_scan_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_clear_button").assertDoesNotExist()

    composeTestRule.onNodeWithTag("search_book_input").performTextInput("데미안")
    composeTestRule.onNodeWithTag("search_clear_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_isbn_scan_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_isbn_scan_button").performClick()
    assertEquals(1, scanClicks)

    composeTestRule.onNodeWithTag("search_clear_button").performClick()
    composeTestRule.onNodeWithTag("search_clear_button").assertDoesNotExist()
  }
}
