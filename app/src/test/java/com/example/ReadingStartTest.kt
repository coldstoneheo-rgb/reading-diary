package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.ReadingStart
import com.example.ui.screens.BookDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

/** «읽기 시작»(HO-2026-1127 조건 4): 시작 전에는 진척도 · 기록 쓰기가 닫혀 있고, 누르면 날짜 · 시각이 저장돼 보인다. 스키마는 그대로(v3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReadingStartTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private lateinit var savedTimeZone: TimeZone
  private var caseId = 0

  @Before
  fun setUp() = runBlocking {
    savedTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    caseId = db.bookcaseDao().insert(Bookcase(name = "읽기 시작")).toInt()
  }

  @After
  fun tearDown() { TimeZone.setDefault(savedTimeZone) }

  private fun book(status: String = "TO_READ", page: Int = 0, start: String = "") =
    Book(title = "견본", author = "", totalPages = 100, currentPage = page, bookcaseId = caseId, status = status, startDate = start)

  // ---------- 순수 규칙 ----------

  @Test
  fun isStarted_migrationRule_forExistingBooks() {
    assertFalse("새 책(읽을 책 · 0쪽 · 기록 0)", ReadingStart.isStarted(book(), 0))
    assertFalse("«읽고 있는 책»으로 등록만 하고 손대지 않은 책도 시작 전", ReadingStart.isStarted(book(status = "READING"), 0))
    assertTrue("옛 앱이 적은 날짜만 있는 값", ReadingStart.isStarted(book(start = "2026-11-20"), 0))
    assertTrue("읽은 쪽이 있음", ReadingStart.isStarted(book(page = 3), 0))
    assertTrue("완독", ReadingStart.isStarted(book(status = "COMPLETED"), 0))
    assertTrue("기록이 있음", ReadingStart.isStarted(book(), 1))
  }

  @Test
  fun formatAndParse_roundTripToTheMinute_andReadOldDateOnlyValues() {
    val at = 1_795_748_760_000L // 2026-11-27 12:06 KST
    val s = ReadingStart.format(at)
    assertEquals("2026-11-27 12:06", s)
    assertEquals(at - at % 60_000, ReadingStart.parse(s))
    assertTrue(ReadingStart.hasTime(s))
    assertEquals(ReadingStart.parse("2026-11-27 00:00"), ReadingStart.parse("2026-11-27"))
    assertFalse(ReadingStart.hasTime("2026-11-27"))
    assertNull(ReadingStart.parse(""))
    assertNull(ReadingStart.parse("어제"))
    assertNull(ReadingStart.parse("2026-11-27 12:06 뒤에 글"))
  }

  // ---------- 화면 + ViewModel ----------

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      composeTestRule.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within 10s", condition())
  }

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: android.graphics.Bitmap): OcrOutcome = error("not used")
  }

  private fun has(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  private fun stored(id: Int) = runBlocking { db.bookDao().getBookByIdOneShot(id)!! }

  private fun detail(id: Int): ReadingViewModel {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.runOnUiThread { vm.navigateTo(Screen.BookDetail(id)) }
    composeTestRule.setContent { MyApplicationTheme { BookDetailScreen(viewModel = vm) } }
    pump { has("reading_start_text") }
    return vm
  }

  @Test
  fun newBook_opensProgressAndRecordOnlyAfterStart_andShowsTheStoredDateTime() {
    val id = runBlocking { db.bookDao().insert(book()).toInt() }
    val vm = detail(id)
    assertTrue(has("start_reading_button"))
    assertFalse("시작 전에는 기록 쓰기가 닫혀 있다", has("add_diary_button"))
    assertFalse("시작 전에는 진척도가 닫혀 있다", has("progress_slider"))

    val before = System.currentTimeMillis()
    composeTestRule.onNodeWithTag("start_reading_button").performScrollTo().performClick()
    pump { has("add_diary_button") }
    val saved = stored(id)
    assertEquals("READING", saved.status)
    assertTrue(saved.startDate, ReadingStart.hasTime(saved.startDate))
    assertTrue(ReadingStart.parse(saved.startDate)!! >= before - before % 60_000)
    assertTrue(has("progress_slider"))
    assertFalse(has("start_reading_button"))
    composeTestRule.onNodeWithTag("reading_start_text").assertTextContains("독서 시작: ${saved.startDate}")

    // 고치기: 시작 날짜 · 시각만 바뀌고 상태 · 쪽은 그대로.
    composeTestRule.runOnUiThread { vm.updateReadingStart(id, 1_795_700_000_000L) }
    pump { stored(id).startDate == ReadingStart.format(1_795_700_000_000L) }
    assertEquals("READING", stored(id).status)
    composeTestRule.onNodeWithTag("reading_start_text").assertTextContains("독서 시작: ${ReadingStart.format(1_795_700_000_000L)}")

    // 다시 «읽기 시작»을 불러도 저장된 시작 값을 덮지 않는다.
    composeTestRule.runOnUiThread { vm.startReading(id, 1_900_000_000_000L) }
    repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
    assertEquals(ReadingStart.format(1_795_700_000_000L), stored(id).startDate)
  }

  @Test
  fun existingBook_withOldDateOnlyStart_isAlreadyStarted() {
    val id = runBlocking { db.bookDao().insert(book(status = "READING", page = 12, start = "2026-11-20")).toInt() }
    detail(id)
    assertFalse(has("start_reading_button"))
    assertTrue(has("add_diary_button"))
    composeTestRule.onNodeWithTag("reading_start_text").assertTextContains("독서 시작: 2026-11-20")
  }

  @Test
  fun existingBook_withRecordsButNoStart_isAlreadyStarted() {
    val id = runBlocking { db.bookDao().insert(book()).toInt() }
    runBlocking { db.diaryDao().insert(Diary(bookId = id, page = 0, selectedText = "글", notes = "", createdAt = 1L)) }
    detail(id)
    pump { has("add_diary_button") }
    assertFalse(has("start_reading_button"))
  }
}
