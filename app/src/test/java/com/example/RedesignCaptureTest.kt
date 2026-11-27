package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.data.ocr.UnderlinePreselect
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * 재설계 계획용 화면 캡처 도구(HO-2026-1127 조건 1 · 2). 기준 이미지가 아니다 — 비교하지 않고 PNG만 쓴다.
 *
 *   RD_CAPTURE_OUT='<레포 밖 폴더>' gradle :app:recordRoborazziDebug --tests com.example.RedesignCaptureTest --rerun
 *
 * (Roborazzi 캡처는 record/verify 태스크에서만 파일을 쓴다. 경로가 레포 밖이라 기준 이미지가 생기지 않는다.)
 *
 * 화면 크기 = 411×891dp · xxhdpi(≈1080×2340px, 사용자 폰과 같은 비). 한 장 = 스크롤 없이 보이는 한 화면.
 * 글 · 책은 지어낸 견본이다. RD_CAPTURE_OUT이 없으면 아무것도 하지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class RedesignCaptureTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private val out: File? = System.getenv("RD_CAPTURE_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun vm() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }

  private fun idle(n: Int = 15) = repeat(n) {
    composeTestRule.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idle()
    Thread.sleep(20)
  }

  private fun shot(name: String) {
    idle()
    val f = File(out!!, "$name.png")
    composeTestRule.onRoot().captureRoboImage(filePath = f.path)
    println("캡처 $name ${f.length()}바이트")
  }

  private fun has(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private val sampleBody = "바람이 부는 언덕에 나무가 서 있고 그 아래로 작은 길이 이어진다. 길 끝에는 오래된 집이 한 채 있다. "

  private fun book(status: String, page: Int, start: String): Int = runBlocking {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    val caseId = db.bookcaseDao().insert(Bookcase(name = "견본 책장")).toInt()
    db.bookDao().insert(Book(title = "지어낸 책 제목", author = "지은이", totalPages = 320, currentPage = page, bookcaseId = caseId, status = status, startDate = start)).toInt()
  }

  @Test
  fun bookDetail_newBook() {
    if (out == null) return
    val id = book("TO_READ", 0, "")
    val vm = vm()
    composeTestRule.runOnUiThread { vm.navigateTo(Screen.BookDetail(id)) }
    composeTestRule.setContent { MyApplicationTheme { BookDetailScreen(viewModel = vm) } }
    composeTestRule.waitUntil(10_000) { has("book_detail_title") }
    shot("detail-new")
  }

  @Test
  fun bookDetail_withRecords() {
    if (out == null) return
    val id = book("READING", 96, "2026-11-20")
    runBlocking {
      db.diaryDao().insert(Diary(bookId = id, page = 45, selectedText = "바람이 부는 언덕에 나무가 서 있다", notes = "짧은 메모 한 줄", createdAt = 1_795_700_000_000L))
      db.diaryDao().insert(Diary(bookId = id, page = 96, selectedText = "길 끝에는 오래된 집이 한 채 있다", notes = "", createdAt = 1_795_748_760_000L))
    }
    val vm = vm()
    composeTestRule.runOnUiThread { vm.navigateTo(Screen.BookDetail(id)) }
    composeTestRule.setContent { MyApplicationTheme { BookDetailScreen(viewModel = vm) } }
    composeTestRule.waitUntil(10_000) { has("book_detail_title") }
    shot("detail-records-1")
    composeTestRule.onAllNodesWithTag("add_diary_button").fetchSemanticsNodes().firstOrNull()?.let {
      composeTestRule.onNodeWithTag("add_diary_button").performScrollTo()
    }
    shot("detail-records-2")
  }

  /** 견본 쪽 그림(회색 바탕에 글줄 막대) — 자르기 단계용. */
  private fun pagePhoto(): Uri {
    val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(225, 225, 220))
    val p = Paint().apply { color = Color.rgb(60, 60, 60) }
    for (i in 0 until 22) c.drawRect(80f, 120f + i * 46f, if (i % 5 == 4) 520f else 820f, 140f + i * 46f, p)
    val f = File(app.cacheDir, "redesign-page.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(f)
  }

  @Test
  fun write_newRecord_andCropStep() {
    if (out == null) return
    val id = book("READING", 96, "2026-11-20")
    val vm = vm()
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
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null) }
      }
    }
    composeTestRule.waitUntil(10_000) { has("save_diary_submit_button") }
    shot("write-1-top")
    // 갤러리 버튼: 지금 화면은 글자 «사진 선택», 재설계 뒤에는 같은 이름의 아이콘(contentDescription).
    composeTestRule.onNode(androidx.compose.ui.test.hasText("사진 선택").or(androidx.compose.ui.test.hasContentDescription("사진 선택")), useUnmergedTree = false)
      .performClick()
    composeTestRule.waitUntil(10_000) { has("camera_crop_card") }
    composeTestRule.onNodeWithTag("camera_crop_card").performScrollTo()
    shot("write-2-crop")
  }

  // ── R1 장면(before/after 짝): 첫 화면(밝은 · Dark · 글꼴 1.3 · 2.0) · 인식 뒤 편집창(밝은 · Dark) · 키보드 ──

  /** 새 기록의 첫 화면. */
  private fun r1Top(name: String, themeId: Int, fontScale: Float) {
    val id = book("READING", 96, "2026-11-20")
    val vm = vm()
    composeTestRule.setContent { RecordScreenHarness.Frame(themeId, fontScale) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null) } }
    composeTestRule.waitUntil(10_000) { has("diary_page_input") }
    shot(name)
  }

  @Test fun r1_top_light() { if (out != null) r1Top("r1-top-light", RecordScreenHarness.LIGHT, 1f) }
  @Test fun r1_top_dark() { if (out != null) r1Top("r1-top-dark", RecordScreenHarness.DARK, 1f) }
  @Test fun r1_top_font130() { if (out != null) r1Top("r1-top-font130", RecordScreenHarness.LIGHT, 1.3f) }
  @Test fun r1_top_font200() { if (out != null) r1Top("r1-top-font200", RecordScreenHarness.LIGHT, 2f) }

  /** 인식 뒤(밑줄 어절 칩 · 편집창). 고치기 화면에 미리 선택을 넣어 만든다. */
  private fun r1Text(name: String, themeId: Int, fontScale: Float) {
    val id = book("READING", 96, "2026-11-20")
    val full = sampleBody.repeat(6).take(570)
    val diaryId = runBlocking { db.diaryDao().insert(Diary(bookId = id, page = 47, selectedText = full, notes = "", createdAt = 1_795_748_760_000L)).toInt() }
    val vm = vm()
    composeTestRule.setContent { RecordScreenHarness.Frame(themeId, fontScale) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = diaryId) } }
    composeTestRule.waitUntil(10_000) { has("diary_extracted_text_input") }
    idle()
    vm.underlinePreselect.value = full.split(" ").filter { it.isNotBlank() }.take(24).mapIndexed { i, w -> UnderlinePreselect.Word(w, i / 8, i in 2..4 || i in 17..19) }
    idle()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo()
    shot(name)
  }

  @Test fun r1_text_light() { if (out != null) r1Text("r1-text-light", RecordScreenHarness.LIGHT, 1f) }
  @Test fun r1_text_dark() { if (out != null) r1Text("r1-text-dark", RecordScreenHarness.DARK, 1f) }
  @Test fun r1_text_font200() { if (out != null) r1Text("r1-text-font200", RecordScreenHarness.LIGHT, 2f) }

  /** 메모 칸에 쓰는 중 키보드가 올라온 장면(회색 판 = 키보드 자리). */
  @Test
  fun r1_keyboard() {
    if (out == null) return
    val id = book("READING", 96, "2026-11-20")
    val vm = vm()
    var view: android.view.View? = null
    composeTestRule.setContent {
      RecordScreenHarness.Frame(imeDp = RecordScreenHarness.IME_DP, onView = { view = it }) { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = null) }
    }
    composeTestRule.waitUntil(10_000) { has("diary_page_input") }
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("47")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("바람이 부는 언덕에 나무가 서 있다")
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo().performClick()
    val px = (RecordScreenHarness.IME_DP * app.resources.displayMetrics.density).toInt()
    composeTestRule.runOnUiThread { RecordScreenHarness.sendIme(view!!, px) }
    idle()
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo()
    shot("r1-keyboard")
  }

  @Test
  fun write_afterRecognition_withPreselect() {
    if (out == null) return
    val id = book("READING", 96, "2026-11-20")
    val full = sampleBody.repeat(6).take(570)
    val diaryId = runBlocking { db.diaryDao().insert(Diary(bookId = id, page = 47, selectedText = full, notes = "", createdAt = 1_795_748_760_000L)).toInt() }
    val vm = vm()
    composeTestRule.setContent { MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = id, diaryId = diaryId) } }
    composeTestRule.waitUntil(10_000) { has("diary_extracted_text_input") }
    idle()
    val words = full.split(" ").filter { it.isNotBlank() }.take(24).mapIndexed { i, w -> UnderlinePreselect.Word(w, i / 8, i in 2..4 || i in 17..19) }
    vm.underlinePreselect.value = words
    idle()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo()
    shot("write-3-text")
    // 저장이 상단 바로 옮겨 간 뒤에도 같은 장면(화면 맨 아래 = 메모 칸)을 찍는다.
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo()
    shot("write-4-bottom")
  }
}
