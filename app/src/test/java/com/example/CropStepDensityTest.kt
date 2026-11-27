package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
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
import java.io.File

/**
 * 재설계 R2(자르기 단계)의 통과 기준(REDESIGN §2-3 · §7-1 보정 1 · 3 · 4 · 5 · 6):
 * 사진 위 도구 줄(아이콘만 · 48dp · 한국어 낭독 이름) · 사진 지우기 확인 · 자르기 처음대로 되돌리기 ·
 * 접힌 «틀 조정»에서도 틀이 보임 · 첫 화면에 사진 + 확정/읽기 버튼 · 글꼴 1.3 · 2.0배 잘림 0 · 겹침 0 · 12sp 이하 글자 0.
 * 화면 = 411×891dp(사용자 폰과 같은 dp). 사진은 지어낸 견본 그림(회색 바탕 + 글줄 막대)이다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class CropStepDensityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

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

  /** 견본 쪽 그림(세로 3:4, 회색 바탕에 글줄 막대). 실제 책 사진이 아니다. */
  private fun pagePhoto(): Uri {
    val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(225, 225, 220))
    val p = Paint().apply { color = Color.rgb(60, 60, 60) }
    for (i in 0 until 22) c.drawRect(80f, 120f + i * 46f, if (i % 5 == 4) 520f else 820f, 140f + i * 46f, p)
    val f = File(app.cacheDir, "crop-density-page.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(f)
  }

  /** 새 기록 화면을 열고 상단 바 «사진 선택»으로 견본 사진을 골라 자르기 단계까지. */
  private fun openCropStep(themeId: Int = RecordScreenHarness.LIGHT, fontScale: Float = 1f) {
    val id = runBlocking {
      val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
      db.bookDao().insert(Book(title = "지어낸 책", author = "지은이", totalPages = 320, currentPage = 96, bookcaseId = caseId, status = "READING", startDate = "2026-11-20 09:00")).toInt()
    }
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    val uri = pagePhoto()
    val registryOwner = object : ActivityResultRegistryOwner {
      override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
          dispatchResult(requestCode, uri)
        }
      }
    }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        RecordScreenHarness.Frame(themeId, fontScale) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null) }
      }
    }
    pump { has("diary_page_input") }
    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    pump { has("camera_crop_card") }
    idle()
  }

  private fun SemanticsNode.rect(): Rect = Rect(positionInRoot, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
  private fun Rect.overlaps(o: Rect) = left < o.right - 0.5f && o.left < right - 0.5f && top < o.bottom - 0.5f && o.top < bottom - 0.5f
  private fun Rect.inside(o: Rect) = left >= o.left - 0.5f && top >= o.top - 0.5f && right <= o.right + 0.5f && bottom <= o.bottom + 0.5f

  private fun byTag(tag: String) = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
  private fun byLabel(label: String) = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode()
  private fun frameText(): String = byTag("crop_frame").config[SemanticsProperties.ContentDescription].joinToString()

  private fun layout(node: SemanticsNode): TextLayoutResult? {
    val out = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
    return out.firstOrNull()
  }

  /** 자르기 카드 안의 글 노드. */
  private fun cardTextNodes(): List<SemanticsNode> {
    val card = byTag("camera_crop_card").rect()
    return composeTestRule.onAllNodes(SemanticsMatcher("text") { SemanticsActions.GetTextLayoutResult in it.config }, useUnmergedTree = true)
      .fetchSemanticsNodes().filter { it.rect().overlaps(card) }
  }

  private fun noTextOverflows(where: String) {
    for (n in cardTextNodes()) {
      val l = layout(n) ?: continue
      val text = l.layoutInput.text.text
      assertTrue("$where: 글 높이가 넘친다 «$text»", !l.didOverflowHeight)
      for (line in 0 until l.lineCount) {
        assertTrue("$where: 글이 잘렸다 «$text» 줄 $line 끝 ${l.getLineRight(line)} > 폭 ${l.size.width}", l.getLineRight(line) <= l.size.width + 1f)
        assertTrue("$where: 말줄임 «$text»", !l.isLineEllipsized(line))
      }
      // 글이 자기 버튼(병합 노드) 밖으로 나가지 않는다.
    }
  }

  private fun assertNoOverlap(where: String, nodes: Map<String, SemanticsNode>) {
    val list = nodes.entries.toList()
    for (i in list.indices) for (j in i + 1 until list.size) {
      assertTrue("$where: «${list[i].key}» ${list[i].value.rect()} 와 «${list[j].key}» ${list[j].value.rect()}가 겹친다", !list[i].value.rect().overlaps(list[j].value.rect()))
    }
  }

  private fun assertAtLeast48(where: String, n: SemanticsNode) {
    assertTrue("$where 폭 ${n.size.width / density}dp < 48", n.size.width / density >= 47.5f)
    assertTrue("$where 높이 ${n.size.height / density}dp < 48", n.size.height / density >= 47.5f)
  }

  private val toolLabels = listOf("사진 지우기", "90도 회전", "좌우 반전", "자르기 처음대로")

  private fun openAdjust() {
    composeTestRule.onNodeWithTag("crop_adjust_toggle").performScrollTo().performClick()
    pump { has("crop_adjust_panel") }
  }

  private fun moveLeftEdge(to: Float) {
    composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).onFirst()
      .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(to) }
    idle()
  }

  // ── 보정 4: 사진 위 도구 줄 = 아이콘만 · 48dp · 한국어 낭독 이름 ──

  @Test
  fun toolRow_iconOnly_48dp_koreanNames() {
    openCropStep()
    val row = byTag("crop_tool_row").rect()
    val photo = byTag("crop_photo_box").rect()
    for (label in toolLabels) {
      val n = byLabel(label)
      assertAtLeast48(label, n)
      assertTrue("$label 이 도구 줄 밖", n.rect().inside(row))
    }
    assertTrue("도구 줄이 사진 상자 바로 위가 아니다", row.bottom <= photo.top + 0.5f && photo.top - row.bottom < 1f)
    assertNoOverlap("도구 줄", toolLabels.associateWith { byLabel(it) })
    // 병기(아이콘 + 글자) 버튼이 남지 않았다.
    for (old in listOf("회전 (90°)", "좌우 반전", "자르기 리셋", "사진 전체 삭제", "자르기 확정하기")) {
      assertTrue("«$old» 글자가 남았다", composeTestRule.onAllNodesWithText(old).fetchSemanticsNodes().isEmpty())
    }
  }

  // ── 사진 지우기 = 확인 다이얼로그(취소면 남는다) ──

  @Test
  fun clearPhoto_asksFirst_andCancelKeepsThePhoto() {
    openCropStep()
    composeTestRule.onNodeWithContentDescription("사진 지우기").performClick()
    pump { has("photo_clear_confirm") }
    composeTestRule.onNodeWithTag("photo_clear_cancel").performClick()
    idle()
    assertTrue("취소했는데 사진이 사라졌다", has("camera_crop_card"))
    assertTrue(!has("photo_clear_confirm"))

    composeTestRule.onNodeWithContentDescription("사진 지우기").performClick()
    pump { has("photo_clear_confirm") }
    composeTestRule.onNodeWithTag("photo_clear_confirm").performClick()
    pump { !has("camera_crop_card") }
    assertTrue("사진을 지우면 빈 사진 자리로", has("photo_slot"))
  }

  // ── 자르기 처음대로 = 되돌리기 스낵바 ──

  @Test
  fun resetCrop_canBeUndone_fromTheSnackbar() {
    openCropStep()
    openAdjust()
    moveLeftEdge(0.3f)
    assertTrue(frameText(), frameText().startsWith("자르기 틀 왼쪽 30%"))
    composeTestRule.onNodeWithContentDescription("자르기 처음대로").performScrollTo().performClick()
    pump { frameText().startsWith("자르기 틀 왼쪽 0% · 위 0% · 오른쪽 100% · 아래 100%") }
    pump { composeTestRule.onAllNodesWithText("되돌리기").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithText("되돌리기").performClick()
    pump { frameText().startsWith("자르기 틀 왼쪽 30%") }
    composeTestRule.onNodeWithText("좌측 끝: 30%").assertExists()
  }

  // ── 보정 3: 접힌 «틀 조정»에서도 지금 틀이 사진 위에 보이고, 한 번 눌러 열린다 · 기본 틀 = 사진 전체 ──

  @Test
  fun adjustCollapsed_stillShowsTheFrame_andOpensWithOneTap() {
    openCropStep()
    assertTrue("틀 조정은 접힌 채로 시작", !has("crop_adjust_panel"))
    assertEquals("접힘", byTag("crop_adjust_toggle").config[SemanticsProperties.StateDescription])
    val frame = byTag("crop_frame")
    assertTrue("틀이 사진 상자 위에 있지 않다", frame.rect().inside(byTag("crop_photo_box").rect()))
    assertEquals("자르기 틀 왼쪽 0% · 위 0% · 오른쪽 100% · 아래 100%", frameText())
    composeTestRule.onNodeWithTag("crop_adjust_toggle").performClick()
    pump { has("crop_adjust_panel") }
    assertEquals("펼침", byTag("crop_adjust_toggle").config[SemanticsProperties.StateDescription])
    assertTrue(has("crop_frame"))
  }

  // ── 보정 6: 첫 화면(411×891dp)에 사진 + 확정 · 읽기 버튼이 스크롤 없이 · 사진 상자 크기 ──

  @Test
  fun firstScreen_showsPhotoAndConfirmThenRead_withoutScrolling() {
    openCropStep()
    val screenH = composeTestRule.onRoot().fetchSemanticsNode().size.height
    val screenW = composeTestRule.onRoot().fetchSemanticsNode().size.width
    val photo = byTag("crop_photo_box").rect()
    val card = byTag("camera_crop_card").rect()
    println("사진 상자 ${photo.width / density}×${photo.height / density}dp (화면 ${screenW / density}×${screenH / density}dp)")
    assertEquals("사진 상자 폭 = 카드 폭", card.width, photo.width, 1f)
    assertEquals("카드 = 화면 폭 - 좌우 12dp", screenW - 24 * density, card.width, 2f)
    // 견본 사진 3:4 → 높이 = 폭 × 4/3(최대 화면 높이 60% 이하)
    val expectedH = minOf(photo.width * 4f / 3f, screenH * 0.6f)
    assertEquals("사진 상자 높이 = 사진 비(최대 화면 60%)", expectedH, photo.height, 2f)

    fun onScreen(name: String, n: SemanticsNode) {
      val r = n.rect()
      println("첫 화면 $name 아래 끝 = ${r.bottom / density}dp")
      assertTrue("$name 위 끝이 화면 밖", r.top >= 0f)
      assertTrue("$name 아래 끝 ${r.bottom} > 화면 높이 $screenH", r.bottom <= screenH)
    }
    onScreen("사진 상자", byTag("crop_photo_box"))
    onScreen("자르기 확정", byTag("crop_confirm_button"))
    assertAtLeast48("자르기 확정", byTag("crop_confirm_button"))
    assertAtLeast48("틀 조정", byTag("crop_adjust_toggle"))

    composeTestRule.onNodeWithTag("crop_confirm_button").performClick()
    pump { has("ocr_button") }
    onScreen("사진 상자(확정 뒤)", byTag("crop_photo_box"))
    onScreen("틀 안 글자 읽기", byTag("ocr_button"))
    assertAtLeast48("틀 안 글자 읽기", byTag("ocr_button"))
    assertAtLeast48("틀 다시 조정", byTag("crop_readjust_button"))
    assertTrue(frameText().endsWith("고정됨"))
  }

  // ── 12sp 이하 글자 0(자르기 카드 · 틀 조정 열림 · 확정 뒤) ──

  @Test
  fun noTextAt12spOrBelow_inTheCropCard() {
    openCropStep()
    openAdjust()
    fun check(where: String) {
      val nodes = cardTextNodes()
      assertTrue("$where: 글 없음", nodes.isNotEmpty())
      for (n in nodes) {
        val l = layout(n) ?: continue
        val sp = l.layoutInput.style.fontSize.value
        assertTrue("$where «${l.layoutInput.text.take(20)}» ${sp}sp < 13sp", sp >= 13f)
      }
    }
    check("틀 조정 열림")
    composeTestRule.onNodeWithTag("crop_confirm_button").performScrollTo().performClick()
    pump { has("ocr_button") }
    check("확정 뒤")
  }

  // ── 보정 1: 글꼴 1.3 · 2.0배에서 잘림 0 · 겹침 0 ──

  private fun fontScale_noClipNoOverlap(scale: Float, shot: String) {
    openCropStep(fontScale = scale)
    openAdjust()
    composeTestRule.onNodeWithTag("camera_crop_card").performScrollTo()
    idle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$shot.png")
    val where = "글꼴 ${scale}배"
    val card = byTag("camera_crop_card").rect()
    val parts = toolLabels.associateWith { byLabel(it) } +
      mapOf("틀 조정" to byTag("crop_adjust_toggle"), "자르기 확정" to byTag("crop_confirm_button"), "틀 조정 판" to byTag("crop_adjust_panel"), "사진" to byTag("crop_photo_box"))
    for ((name, n) in parts) assertTrue("$where: $name ${n.rect()}이 카드 $card 밖", n.rect().inside(card))
    assertNoOverlap(where, parts)
    noTextOverflows(where)

    composeTestRule.onNodeWithTag("crop_confirm_button").performScrollTo().performClick()
    pump { has("ocr_button") }
    val after = mapOf("틀 다시 조정" to byTag("crop_readjust_button"), "틀 안 글자 읽기" to byTag("ocr_button"), "사진" to byTag("crop_photo_box"))
    for ((name, n) in after) assertTrue("$where 확정 뒤: $name 이 카드 밖", n.rect().inside(byTag("camera_crop_card").rect()))
    assertNoOverlap("$where 확정 뒤", after)
    noTextOverflows("$where 확정 뒤")
  }

  @Test fun fontScale130_cropStep() = fontScale_noClipNoOverlap(1.3f, "crop_step_font130")
  @Test fun fontScale200_cropStep() = fontScale_noClipNoOverlap(2f, "crop_step_font200")

  // ── 보정 5: 밝은(1) + 어두운(Dark 9) 기준 이미지 ──

  @Test
  fun light_screenshot() {
    openCropStep()
    // 자르기 카드만 찍는다 — 화면 위 «시각» 줄은 지금 시각이라 실행마다 달라진다(화면 전체는 RedesignCaptureTest 캡처).
    composeTestRule.onNodeWithTag("camera_crop_card").captureRoboImage(filePath = "src/test/screenshots/crop_step_light.png")
  }

  @Test
  fun dark_screenshot() {
    openCropStep(themeId = RecordScreenHarness.DARK)
    // 자르기 카드만 찍는다 — 화면 위 «시각» 줄은 지금 시각이라 실행마다 달라진다(화면 전체는 RedesignCaptureTest 캡처).
    composeTestRule.onNodeWithTag("camera_crop_card").captureRoboImage(filePath = "src/test/screenshots/crop_step_dark.png")
  }
}
