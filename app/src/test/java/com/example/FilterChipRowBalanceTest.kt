package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ui.screens.FilterChipRow
import com.example.ui.screens.chooseChipLayout
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 서재 필터 캡슐 줄(11-28 사용자 실기기: «[읽은 책]이 화면 오른쪽 경계에 딱 닿아 있다 — 좌우 밸런스»).
 * 기준: 360 · 393 · 411dp × 글꼴 1.0 · 1.3 · 2.0에서 네 캡슐이 모두 화면 안 · 첫 캡슐 왼쪽 여백 = 마지막 캡슐 오른쪽 여백 ·
 * 낭독 이름 = 글 하나 · 선택 상태가 읽힌다. 각 조합의 단계(아이콘 · 안쪽 여백 · 줄 수)를 출력한다(PR 표).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterChipRowBalanceTest {

  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val tags = listOf("ALL", "READING", "TO_READ", "COMPLETED")
  private val labels = listOf("전체 책", "읽는 중", "읽을 책", "읽은 책")

  private fun show(fontScale: Float, themeId: Int = RecordScreenHarness.LIGHT) {
    rule.setContent {
      RecordScreenHarness.Frame(themeId = themeId, fontScale = fontScale) {
        var filter by remember { mutableStateOf("ALL") }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).testTag("chip_frame")) {
          Spacer(Modifier.height(8.dp))
          FilterChipRow(currentFilter = filter, onFilterSelected = { filter = it })
          Spacer(Modifier.height(8.dp))
        }
      }
    }
    rule.waitForIdle()
  }

  private fun chip(tag: String): SemanticsNode = rule.onNodeWithTag("filter_chip_$tag").fetchSemanticsNode()
  private val density get() = rule.activity.resources.displayMetrics.density

  private fun checkBalanced(where: String, fontScale: Float) {
    show(fontScale)
    val screenW = rule.onRoot().fetchSemanticsNode().size.width
    val nodes = tags.map { chip(it) }
    val lefts = nodes.map { it.boundsInRoot.left }
    val rights = nodes.map { it.boundsInRoot.right }
    for ((i, n) in nodes.withIndex()) {
      val b = n.boundsInRoot
      assertTrue("$where: ${labels[i]} 왼쪽 ${b.left} < 0", b.left >= 0f)
      assertTrue("$where: ${labels[i]} 오른쪽 ${b.right} > 화면 $screenW", b.right <= screenW)
    }
    val leftGap = lefts.min() / density
    val rightGap = (screenW - rights.max()) / density
    val rows = nodes.map { it.boundsInRoot.top }.distinct().size
    val icons = rule.onAllNodesWithTag("filter_chip_icon", useUnmergedTree = true).fetchSemanticsNodes().size
    println("캡슐 $where: 왼쪽 여백 ${"%.1f".format(leftGap)}dp · 오른쪽 여백 ${"%.1f".format(rightGap)}dp · 줄 $rows · 아이콘 ${if (icons > 0) "있음" else "없음"} · 캡슐 폭 ${"%.1f".format(nodes[0].size.width / density)}dp")
    assertEquals("$where: 왼쪽 = 오른쪽 여백", leftGap, rightGap, 0.5f)
    assertEquals("$where: 바깥 여백 16dp", 16f, leftGap, 0.5f)
    // 낭독 이름 = 글 하나(아이콘 이름이 겹쳐 «전체 책, 전체 책»으로 읽히지 않는다) · 잘림 없는 글.
    for ((i, n) in nodes.withIndex()) {
      val merged = rule.onNodeWithTag("filter_chip_${tags[i]}").fetchSemanticsNode()
      val text = merged.config.getOrNull(SemanticsProperties.Text)?.joinToString(transform = AnnotatedString::text)
      val desc = merged.config.getOrNull(SemanticsProperties.ContentDescription)
      assertEquals("$where: ${labels[i]} 글", labels[i], text)
      assertTrue("$where: ${labels[i]} 아이콘 이름이 남았다 $desc", desc.isNullOrEmpty())
    }
  }

  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun w360_font100() = checkBalanced("360dp × 1.0", 1f)
  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun w360_font130() = checkBalanced("360dp × 1.3", 1.3f)
  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun w360_font200() = checkBalanced("360dp × 2.0", 2f)
  @Test @Config(qualifiers = "w393dp-h851dp-xxhdpi") fun w393_font100() = checkBalanced("393dp × 1.0", 1f)
  @Test @Config(qualifiers = "w393dp-h851dp-xxhdpi") fun w393_font130() = checkBalanced("393dp × 1.3", 1.3f)
  @Test @Config(qualifiers = "w393dp-h851dp-xxhdpi") fun w393_font200() = checkBalanced("393dp × 2.0", 2f)
  @Test @Config(qualifiers = "w411dp-h914dp-xxhdpi") fun w411_font100() = checkBalanced("411dp × 1.0", 1f)
  @Test @Config(qualifiers = "w411dp-h914dp-xxhdpi") fun w411_font130() = checkBalanced("411dp × 1.3", 1.3f)
  @Test @Config(qualifiers = "w411dp-h914dp-xxhdpi") fun w411_font200() = checkBalanced("411dp × 2.0", 2f)

  /** 1.0 · 1.3은 가장 좁은 360dp에서도 한 줄(2.0은 두 줄이어도 좌우 같은 여백 — 위 시험이 본다). */
  private fun oneRow(scale: Float) {
    show(scale)
    val tops = tags.map { chip(it).boundsInRoot.top }.distinct()
    assertEquals("360dp × $scale 한 줄", 1, tops.size)
  }

  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun font100_staysOneRow_at360() = oneRow(1f)
  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun font130_staysOneRow_at360() = oneRow(1.3f)

  @Test
  @Config(qualifiers = "w360dp-h800dp-xxhdpi")
  fun selectedState_isReadAndMoves() {
    show(1f)
    fun selected(tag: String) = chip(tag).config.getOrNull(SemanticsProperties.Selected)
    assertEquals(true, selected("ALL"))
    assertEquals(false, selected("COMPLETED"))
    rule.onNodeWithTag("filter_chip_COMPLETED").performClick()
    rule.waitForIdle()
    assertEquals(false, selected("ALL"))
    assertEquals(true, selected("COMPLETED"))
  }

  @Test
  fun chooser_fallsBackInOrder() {
    // 넉넉함 → 아이콘 + 12dp · 네 칸
    assertEquals(4, chooseChipLayout(400f, listOf(40f, 40f, 40f, 40f)).second)
    assertTrue(chooseChipLayout(400f, listOf(40f, 40f, 40f, 40f)).first.showIcon)
    // 매우 좁음 → 두 칸
    assertEquals(2, chooseChipLayout(200f, listOf(60f, 60f, 60f, 60f)).second)
  }

  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
  fun light_360_screenshot() {
    show(1f, RecordScreenHarness.LIGHT)
    rule.onNodeWithTag("chip_frame").captureRoboImage(filePath = "src/test/screenshots/filter_chips_360_light.png")
  }

  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
  fun dark_360_screenshot() {
    show(1f, RecordScreenHarness.DARK)
    rule.onNodeWithTag("chip_frame").captureRoboImage(filePath = "src/test/screenshots/filter_chips_360_dark.png")
  }

  @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
  fun light_360_font200_screenshot() {
    show(2f, RecordScreenHarness.LIGHT)
    rule.onNodeWithTag("chip_frame").captureRoboImage(filePath = "src/test/screenshots/filter_chips_360_font200.png")
  }
}
