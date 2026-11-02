package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.CoverPlaceholder
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ADR-003-2: 표지 없는 책의 로컬 플레이스홀더(네트워크 0) 기준 이미지. 제목마다 색이 갈리는지 눈으로 확인할 수 있게 4권. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class CoverPlaceholderScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun coverPlaceholder_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          listOf("진달래꽃", "하늘과 바람과 별과 시", "Demian", "").forEach {
            CoverPlaceholder(title = it, modifier = Modifier.size(width = 80.dp, height = 115.dp))
          }
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/cover_placeholder.png")
  }
}
