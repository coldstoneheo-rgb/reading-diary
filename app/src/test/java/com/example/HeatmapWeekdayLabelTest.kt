package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 독서 잔디 왼쪽 요일 글자 «일 · 화 · 목 · 토»가 1 · 3 · 5 · 7번째 칸 줄 가운데에 온다
 * (2026-11-28 r8Debug 캡처: 글자 줄이 13dp로 고정돼 «토»가 금요일 줄 옆에 왔다).
 * 사용자 폰과 같은 411dp 폭.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class HeatmapWeekdayLabelTest {

  @get:Rule val composeTestRule = createComposeRule()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  @Test
  fun weekdayLabels_sitOnTheMiddleOfRows_1_3_5_7() {
    val vm = ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.setContent { MyApplicationTheme { StatisticsScreen(vm) } }
    composeTestRule.waitForIdle()
    val density = ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
    val grid = composeTestRule.onNodeWithTag("reading_activity_grid").fetchSemanticsNode().boundsInRoot
    val gap = 4f * density
    val cell = (grid.height - 6 * gap) / 7
    for ((row, label) in listOf(0 to "일", 2 to "화", 4 to "목", 6 to "토")) {
      val text = composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
      val rowCenter = grid.top + row * (cell + gap) + cell / 2
      assertEquals("«$label» 글자 가운데(dp)", rowCenter / density, text.center.y / density, 1.5f)
    }
  }
}
