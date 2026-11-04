package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.SampleBooks
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.viewmodel.ReadingViewModel
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * ADR-003-1 «예시 2권»: 신규 설치는 책 0권 · 넣기는 2권(중복 0) · 지우기는 예시만 · DB가 새로 만들어지면 표식 삭제.
 * AppDatabase는 프로세스 싱글턴이라 테스트마다 닫고 파일을 지워 "신규 설치"를 만든다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingViewModelSampleBooksTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private fun resetDatabase() {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    (field.get(null) as AppDatabase?)?.close()
    field.set(null, null)
    app.deleteDatabase("reading_diary_db")
  }

  @Before fun setUp() = resetDatabase()
  @After fun tearDown() {
    collectors.forEach { it.cancel() }
    resetDatabase()
  }

  private fun prefs() = app.getSharedPreferences(SampleBooks.PREFS_NAME, Context.MODE_PRIVATE)

  private val collectors = mutableListOf<Job>()

  /** sampleBookIds는 WhileSubscribed라 화면처럼 구독자가 있어야 값이 흐른다. */
  private fun viewModel() = ReadingViewModel(app, NoCallExtractor, NoCallExtractor) { false }.also { vm ->
    collectors += CoroutineScope(Dispatchers.Main).launch { vm.sampleBookIds.collect { } }
  }

  /** viewModelScope(Main) 작업과 Room IO가 끝날 때까지 메인 루퍼를 돌린다. */
  private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within ${timeoutMs}ms", condition())
  }

  private fun allBooks(): List<Book> = runBlocking { AppDatabase.getDatabase(app).bookDao().getAllBooks().first() }
  private fun allDiaryBookIds(): List<Int> = runBlocking { AppDatabase.getDatabase(app).diaryDao().getAllDiaries().first() }.map { it.bookId }

  @Test
  fun freshInstall_hasNoBooks_andBookcasesOnly() {
    runBlocking { AppDatabase.getDatabase(app).bookcaseDao().getCount() } // onCreate 실행
    awaitUntil { runBlocking { AppDatabase.getDatabase(app).bookcaseDao().getCount() } == 5 }
    assertEquals(emptyList<Book>(), allBooks())
  }

  @Test
  fun insertSamples_addsTwoBooks_andSecondInsertAddsNothing() {
    val vm = viewModel()
    var first = -1
    vm.insertSampleBooks { first = it }
    awaitUntil { first >= 0 }
    assertEquals(2, first)
    assertEquals(SampleBooks.BOOKS.map { it.title }.toSet(), allBooks().map { it.title }.toSet())
    assertEquals(SampleBooks.BOOKS.sumOf { it.diaries.size }, allDiaryBookIds().size)
    awaitUntil { vm.sampleBookIds.value.size == 2 }

    var second = -1
    vm.insertSampleBooks { second = it }
    awaitUntil { second >= 0 }
    assertEquals(0, second)
    assertEquals(2, allBooks().size)
  }

  @Test
  fun deleteSamples_removesOnlySamples_andTheirDiaries() {
    val vm = viewModel()
    var userBookId = -1
    vm.saveBook(title = "내 책", author = "나", totalPages = 50, currentPage = 0, coverUrl = "", bookcaseId = 1, status = "READING") {}
    awaitUntil { allBooks().any { it.title == "내 책" } }
    userBookId = allBooks().single { it.title == "내 책" }.id
    var added = -1
    vm.insertSampleBooks { added = it }
    awaitUntil { added >= 0 }
    assertEquals(3, allBooks().size)

    var removed = -1
    vm.deleteSampleBooks { r, _ -> removed = r }
    awaitUntil { removed >= 0 }
    assertEquals(2, removed)
    assertEquals(listOf(userBookId), allBooks().map { it.id })
    assertTrue("sample diaries must be gone with their books (CASCADE)", allDiaryBookIds().all { it == userBookId })
    assertFalse(prefs().contains(SampleBooks.KEY) && prefs().getStringSet(SampleBooks.KEY, emptySet())!!.isNotEmpty())
  }

  @Test
  fun deleteSamples_keepsASampleBookTheUserWroteIn() {
    val vm = viewModel()
    var added = -1
    vm.insertSampleBooks { added = it }
    awaitUntil { added >= 0 }
    val touched = allBooks().first()
    vm.saveDiary(bookId = touched.id, page = 3, selectedText = "내가 직접 옮긴 문장", notes = "내 메모") {}
    awaitUntil { allDiaryBookIds().size == SampleBooks.BOOKS.sumOf { it.diaries.size } + 1 }

    var removed = -1; var kept = -1
    vm.deleteSampleBooks { r, k -> removed = r; kept = k }
    awaitUntil { removed >= 0 }
    assertEquals(1, removed)
    assertEquals(1, kept)
    assertEquals(listOf(touched.id), allBooks().map { it.id })
    // 남긴 책은 더 이상 예시가 아니다(배지·통계 제외 해제)
    awaitUntil { vm.sampleBookIds.value.isEmpty() }
  }

  @Test
  fun insertSamples_tappedTwiceQuickly_addsOnlyOnce() {
    val vm = viewModel()
    val results = mutableListOf<Int>()
    vm.insertSampleBooks { results += it }
    vm.insertSampleBooks { results += it }
    awaitUntil { results.size == 2 }
    assertEquals(listOf(2, 0), results.sorted().reversed())
    assertEquals(2, allBooks().size)
    awaitUntil { vm.sampleBookIds.value.size == 2 }
  }

  @Test
  fun destructiveMigrationCallback_clearsStaleSampleMarks() {
    prefs().edit().putStringSet(SampleBooks.KEY, setOf("1|진달래꽃")).commit()
    AppDatabase.clearSampleMarks(app) // onCreate가 쓰는 정리 함수
    assertFalse(prefs().contains(SampleBooks.KEY))
  }

  @Test
  fun databaseOnCreate_clearsStaleSampleMarks() {
    prefs().edit().putStringSet(SampleBooks.KEY, setOf("1|진달래꽃")).commit()
    runBlocking { AppDatabase.getDatabase(app).bookcaseDao().getCount() } // 새 DB 열기 → onCreate
    assertFalse(prefs().contains(SampleBooks.KEY))
  }
}
