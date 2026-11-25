package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Diary
import com.example.data.ocr.OcrLine
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.OcrWord
import com.example.data.ocr.TextExtractor
import com.example.data.ocr.UnderlineDetector
import com.example.data.ocr.UnderlinePreselect
import com.example.ui.screens.AddEditBookScreen
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 처음 쓰는 사람의 기록이 사라지거나 섞이지 않게 막는 동작. 마지막 테스트는 새 설치에서 «등록 → 인식 → 고쳐 저장 → 낱말로 다시 찾기» 한 바퀴. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class FirstRunSafetyTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private class FixedExtractor(private val text: String) : TextExtractor {
    /** 엔진처럼 한 줄의 어절 상자도 준다(밑줄 미리 선택의 입력). */
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = OcrOutcome.Text(
      text,
      listOf(OcrLine(text, null, text.split(" ").mapIndexed { i, w -> OcrWord(w, UnderlineDetector.Box(i * 10, 0, i * 10 + 8, 8)) }))
    )
  }

  private fun viewModel(text: String = "쓰지 않음") = ReadingViewModel(app, FixedExtractor(text), FixedExtractor(text), FakeDiaryPhotoStore()) { false }

  private val collectors = mutableListOf<Job>()

  @After
  fun tearDown() {
    collectors.forEach { it.cancel() }
    newInstall()
  }

  @Before
  fun newInstall() {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    (field.get(null) as AppDatabase?)?.close()
    field.set(null, null)
    app.deleteDatabase(AppDatabase.DB_NAME)
  }

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      composeTestRule.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within 10s", condition())
  }

  /** 조건이 참이 되지 않는지 잠시 지켜본다(늦은 방출이 올 시간을 준다). */
  private fun settle() = repeat(30) {
    shadowOf(Looper.getMainLooper()).idle()
    Thread.sleep(20)
  }

  private fun insertBook(title: String): Int = runBlocking {
    db.bookDao().insert(Book(title = title, author = "지은이", totalPages = 100, bookcaseId = 1, status = "READING")).toInt()
  }

  private fun shown(text: String) = composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun newInstall_hasEachDefaultBookcaseOnce_andBasicComesFirst() {
    // 여는 즉시(첫 조회) 5개가 있다 — 뒤늦은 별도 코루틴이 넣으면 ViewModel의 채우기와 겹쳐 두 벌이 된다.
    assertEquals(AppDatabase.DEFAULT_BOOKCASES, runBlocking { db.bookcaseDao().getAllBookcasesOneShot() }.map { it.name })
    newInstall()
    val vm = viewModel() // DB를 처음 여는 것이 ViewModel인 실제 순서
    settle()
    val names = runBlocking { db.bookcaseDao().getAllBookcasesOneShot() }.map { it.name }
    assertEquals(AppDatabase.DEFAULT_BOOKCASES, names)
    assertEquals("기본 책장", names.first())
    assertEquals(Screen.Dashboard, vm.currentScreenState.value)
  }

  @Test
  fun openingAnotherBook_stopsFollowingTheFirstOne() {
    val vm = viewModel()
    val a = insertBook("가 책")
    val b = insertBook("나 책")
    vm.navigateTo(Screen.BookDetail(a))
    pump { vm.activeBook.value?.id == a }
    vm.navigateBack()
    vm.navigateTo(Screen.BookDetail(b))
    pump { vm.activeBook.value?.id == b }
    // 마지막 값만 보면 뒤이은 «나 책» 방출에 가려진다 — 스쳐 간 값까지 모두 적는다.
    val shownBooks = mutableListOf<Int?>()
    val shownDiaryBooks = mutableListOf<Int>()
    collectors += CoroutineScope(Dispatchers.Unconfined).launch { vm.activeBook.collect { shownBooks += it?.id } }
    collectors += CoroutineScope(Dispatchers.Unconfined).launch { vm.activeBookDiaries.collect { list -> shownDiaryBooks += list.map { it.bookId } } }
    // 책 · 기록 표가 바뀌면 Room은 구독 중인 조회를 모두 다시 내보낸다. 끊지 않은 «가 책» 구독이 화면을 빼앗으면 안 된다.
    runBlocking {
      db.bookDao().update(db.bookDao().getBookByIdOneShot(a)!!.copy(rating = 5))
      db.diaryDao().insert(Diary(bookId = a, page = 1, selectedText = "가 책 문장", notes = ""))
    }
    settle()
    // 앞 책의 늦은 진도 저장 · 별점도 지금 연 책을 바꾸지 않는다.
    vm.updateBookProgress(a, 10)
    pump { runBlocking { db.bookDao().getBookByIdOneShot(a) }!!.currentPage == 10 }
    vm.updateBookRating(a, 3)
    pump { runBlocking { db.bookDao().getBookByIdOneShot(a) }!!.rating == 3 }
    settle()
    assertEquals(setOf<Int?>(b), shownBooks.toSet())
    assertTrue("$shownDiaryBooks", shownDiaryBooks.isEmpty())
  }

  @Test
  fun editingACompletedBook_keepsItsCompletionDate() {
    val vm = viewModel()
    val id = runBlocking {
      db.bookDao().insert(Book(title = "다 읽은 책", author = "지은이", totalPages = 100, currentPage = 100, bookcaseId = 1, status = "COMPLETED", endDate = "2026-01-05")).toInt()
    }
    var done = false
    vm.saveBook(id = id, title = "고친 제목", author = "지은이", totalPages = 100, currentPage = 100, coverUrl = "", bookcaseId = 2, status = "COMPLETED") { done = true }
    pump { done }
    assertEquals("2026-01-05", runBlocking { db.bookDao().getBookByIdOneShot(id) }!!.endDate)

    // 끝 쪽에 다시 놓아도 그대로, 다시 읽는 중으로 갔다가 완독하면 새 날짜다.
    var moved = false
    vm.updateBookProgress(id, 100) { moved = true }
    pump { moved }
    assertEquals("2026-01-05", runBlocking { db.bookDao().getBookByIdOneShot(id) }!!.endDate)
    moved = false
    vm.updateBookProgress(id, 50) { moved = true }
    pump { moved }
    done = false
    vm.saveBook(id = id, title = "고친 제목", author = "지은이", totalPages = 100, currentPage = 100, coverUrl = "", bookcaseId = 2, status = "COMPLETED") { done = true }
    pump { done }
    assertFalse("2026-01-05" == runBlocking { db.bookDao().getBookByIdOneShot(id) }!!.endDate)
  }

  @Test
  fun deletingADiary_refreshesTheOpenBook() {
    val vm = viewModel()
    val a = insertBook("가 책")
    val d = runBlocking { db.diaryDao().insert(Diary(bookId = a, page = 1, selectedText = "지울 문장", notes = "")) }.toInt()
    vm.navigateTo(Screen.BookDetail(a))
    pump { vm.activeBookDiaries.value.size == 1 }
    vm.deleteDiary(d)
    pump { vm.activeBookDiaries.value.isEmpty() }
  }

  @Test
  fun deleteBookcaseDialog_saysWhatIsLost_andDeletesOnlyOnConfirm() {
    var confirmed = 0
    var dismissed = 0
    composeTestRule.setContent {
      MyApplicationTheme { DeleteBookcaseDialog("여행", books = 2, diaries = 7, onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
    }
    assertTrue(shown("책 2권과 독서 기록 7개"))
    composeTestRule.onNodeWithText("취소").performClick()
    assertEquals(0 to 1, confirmed to dismissed)
    composeTestRule.onNodeWithTag("delete_bookcase_confirm").performClick()
    assertEquals(1, confirmed)
  }

  @Test
  fun leavingWithUnsavedInput_asksFirst_andBlankFormLeavesAtOnce() {
    val vm = viewModel()
    val a = insertBook("가 책")
    lateinit var back: OnBackPressedDispatcher
    vm.navigateTo(Screen.BookDetail(a))
    vm.navigateTo(Screen.AddDiary(a))
    composeTestRule.setContent {
      back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = a) }
    }
    pump { composeTestRule.onAllNodesWithTag("diary_extracted_text_input").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("아직 저장 안 한 문장")
    composeTestRule.waitForIdle()

    composeTestRule.runOnUiThread { back.onBackPressed() }
    composeTestRule.waitForIdle()
    assertTrue(shown("저장하지 않고 나갈까요?"))
    assertEquals(Screen.AddDiary(a), vm.currentScreenState.value)
    composeTestRule.onNodeWithText("계속 쓰기").performClick()
    assertTrue(shown("아직 저장 안 한 문장"))

    composeTestRule.onNodeWithContentDescription("뒤로가기").performClick()
    composeTestRule.onNodeWithTag("discard_diary_confirm").performClick()
    composeTestRule.waitForIdle()
    assertEquals(Screen.BookDetail(a), vm.currentScreenState.value)

    // 비운 폼은 묻지 않는다.
    vm.navigateTo(Screen.AddDiary(a))
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("뒤로가기").performClick()
    composeTestRule.waitForIdle()
    assertFalse(shown("저장하지 않고 나갈까요?"))
    assertEquals(Screen.BookDetail(a), vm.currentScreenState.value)
  }

  @Test
  fun submittingABookTwice_registersItOnce() {
    val vm = viewModel()
    composeTestRule.setContent { MyApplicationTheme { AddEditBookScreen(viewModel = vm, startWithSearch = false) } }
    composeTestRule.onNodeWithTag("book_title_input").performScrollTo().performTextReplacement("연타 책")
    composeTestRule.onNodeWithTag("book_author_input").performScrollTo().performTextReplacement("지은이")
    composeTestRule.onNodeWithTag("book_total_pages_input").performScrollTo().performTextReplacement("120")
    composeTestRule.onNodeWithTag("submit_book_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("submit_book_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("submit_book_button").performClick()
    settle()
    assertEquals(1, runBlocking { db.bookDao().getAllBooks().first() }.count { it.title == "연타 책" })
  }

  @Test
  fun wholeLoop_newInstall_toFindingTheSentenceAgainByOneWord() {
    val vm = viewModel(text = "바람이 부는 언덕에 나무가 서 있다")
    // 검출기 자체는 UnderlineDetectorTest · 기준 자료 측정이 본다. 여기서는 앞 세 어절에 밑줄이 있는 결과를 흉내 낸다.
    vm.preselectRunner = { _, lines -> lines.single().words.mapIndexed { i, w -> UnderlinePreselect.Word(w.text, 0, i < 3) } }
    composeTestRule.setContent { MyApplicationTheme { MainAppEntry(vm) } }

    // 1) 책 등록
    composeTestRule.runOnUiThread { vm.navigateTo(Screen.AddEditBook(startWithSearch = false)) }
    pump { composeTestRule.onAllNodesWithTag("book_title_input").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("book_title_input").performScrollTo().performTextReplacement("지어낸 책")
    composeTestRule.onNodeWithTag("book_author_input").performScrollTo().performTextReplacement("지은이")
    composeTestRule.onNodeWithTag("book_total_pages_input").performScrollTo().performTextReplacement("120")
    composeTestRule.onNodeWithTag("submit_book_button").performScrollTo().performClick()

    // 2) 서재에 뜬 책 → 기록하기
    pump { composeTestRule.onAllNodesWithTag("books_grid").fetchSemanticsNodes().isNotEmpty() && vm.books.value.size == 1 }
    val bookId = vm.books.value.single().id
    pump { composeTestRule.onAllNodesWithTag("book_card_$bookId").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("book_card_$bookId").performClick()
    pump { composeTestRule.onAllNodesWithTag("add_diary_button").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onAllNodesWithTag("add_diary_button").onFirst().performClick()
    pump { composeTestRule.onAllNodesWithTag("diary_page_input").fetchSemanticsNodes().isNotEmpty() }

    // 3) 인식(사진 · ML Kit만 가짜) → 결과가 편집창에 들어온다 → 고친다
    composeTestRule.runOnUiThread { vm.processUnderlineOcr(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)) }
    pump { shown("바람이 부는 언덕에 나무가 서 있다") }
    // 밑줄 어절 칩: 미리 켜져 있지만 버튼을 눌러야 문장이 바뀐다. 잘못 켜진 «부는»은 끄고 놓친 «나무가»는 켠다.
    pump { composeTestRule.onAllNodesWithTag("underline_preselect_panel").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("preselect_word_0").assertIsSelected()
    composeTestRule.onNodeWithTag("preselect_word_3").assertIsNotSelected()
    composeTestRule.onNodeWithTag("preselect_word_1").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("preselect_word_3").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(shown("바람이 부는 언덕에 나무가 서 있다"))
    composeTestRule.onNodeWithTag("preselect_apply_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").assertTextContains("바람이 언덕에 나무가")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("바람이 부는 언덕에")
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("7")
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().performClick()
    pump { vm.currentScreenState.value == Screen.BookDetail(bookId) }
    assertEquals(listOf("바람이 부는 언덕에"), runBlocking { db.diaryDao().getAllDiaries().first() }.map { it.selectedText })

    // 4) 낱말 하나로 다시 찾기
    composeTestRule.runOnUiThread { vm.openPhraseRecall() }
    pump { composeTestRule.onAllNodesWithTag("phraserecall_search_field").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("phraserecall_search_field").performTextReplacement("언덕")
    pump { composeTestRule.onAllNodesWithTag("phraserecall_result").fetchSemanticsNodes().size == 1 }
    composeTestRule.onNodeWithTag("phraserecall_search_field").performTextReplacement("바다")
    pump { composeTestRule.onAllNodesWithTag("phraserecall_result").fetchSemanticsNodes().isEmpty() }
  }
}
