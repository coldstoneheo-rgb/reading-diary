package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.Diary
import com.example.data.DiaryPhoto
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 명세 §3.5 화면 흐름: 체크 없이 보관 · 편집 N장 유지/교체/삭제/추가 · 텍스트만 고치면 사진 유지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrDiaryPhotoScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private val picked = Uri.parse("content://photos/picked")

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  /** 갤러리를 띄우지 않고 고른 결과를 바로 돌려준다. */
  private val registryOwner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        dispatchResult(requestCode, picked)
      }
    }
  }

  private var bookId = 0
  private lateinit var store: FakeDiaryPhotoStore

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "화면 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "화면 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
    store = FakeDiaryPhotoStore()
  }

  private fun open(diaryId: Int?) {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = diaryId) }
      }
    }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("save_diary_submit_button").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun diaryWithPhotos(vararg paths: String): Int = runBlocking {
    store.seed(*paths)
    val d = db.diaryDao().insert(Diary(bookId = bookId, page = 10, selectedText = "처음 문장", notes = "")).toInt()
    db.diaryPhotoDao().insertAll(paths.mapIndexed { i, p -> DiaryPhoto(diaryId = d, path = p, sortOrder = i) })
    d
  }

  private fun rows(diaryId: Int) = runBlocking { db.diaryPhotoDao().photosFor(diaryId) }

  private fun awaitSection(firstIndexVisible: Boolean = true) {
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("diary_photo_add").fetchSemanticsNodes().isNotEmpty() &&
        (!firstIndexVisible || composeTestRule.onAllNodesWithTag("diary_photo_0").fetchSemanticsNodes().isNotEmpty())
    }
    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("처음 문장").fetchSemanticsNodes().isNotEmpty() }
    // performScrollTo는 가장 가까운 스크롤 부모만 움직인다. 사진 줄은 가로 스크롤 안이라, 세로 스크롤 직속인 «사진 추가»로 구역을 화면에 넣는다.
    composeTestRule.onNodeWithTag("diary_photo_add").performScrollTo()
  }

  private fun saveAndAwait(condition: () -> Boolean) {
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitUntil(10_000, condition)
  }

  /** 파일 삭제는 커밋 뒤 메인 스레드에서 이어진다. Robolectric의 waitUntil은 메인 루퍼를 돌리지 않으므로 직접 돌린다. */
  private fun assertDeleted(vararg paths: String) {
    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      store.deleted.isNotEmpty()
    }
    assertEquals(paths.toList(), store.deleted)
  }

  private fun textOf(diaryId: Int) = runBlocking { db.diaryDao().getAllDiaries().first() }.firstOrNull { it.id == diaryId }?.selectedText

  @Test
  fun newRecord_keepsPickedPhoto_withoutCheckbox() {
    open(diaryId = null)
    composeTestRule.onNodeWithText("사진 선택").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("12")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("직접 쓴 문장")

    saveAndAwait { store.files.isNotEmpty() && runBlocking { db.diaryPhotoDao().allPaths() }.contains("new0.jpg") }

    val diary = runBlocking { db.diaryDao().getAllDiaries().first() }.single { it.bookId == bookId }
    assertEquals(listOf("new0.jpg"), rows(diary.id).map { it.path })
  }

  @Test
  fun newRecord_withoutPhoto_savesNoPhotoRow() {
    open(diaryId = null)
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("12")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("사진 없는 문장")

    saveAndAwait { runBlocking { db.diaryDao().getAllDiaries().first() }.any { it.bookId == bookId } }

    val diary = runBlocking { db.diaryDao().getAllDiaries().first() }.single { it.bookId == bookId }
    assertTrue(rows(diary.id).isEmpty())
    assertTrue(store.files.isEmpty())
  }

  @Test
  fun edit_textOnly_keepsAllPhotos() {
    val d = diaryWithPhotos("a.jpg", "b.jpg", "c.jpg")
    val before = rows(d)
    open(d)
    awaitSection()

    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("고친 문장")
    saveAndAwait { textOf(d) == "고친 문장" }

    assertEquals(before, rows(d))
    assertTrue(store.deleted.isEmpty())
  }

  @Test
  fun edit_remove_deletesThatPhotoOnly() {
    val d = diaryWithPhotos("a.jpg", "b.jpg")
    open(d)
    awaitSection()

    composeTestRule.onNodeWithTag("diary_photo_remove_0").performClick()
    saveAndAwait { rows(d).size == 1 }

    assertEquals(listOf("b.jpg"), rows(d).map { it.path })
    assertEquals(0, rows(d).single().sortOrder)
    assertDeleted("a.jpg")
  }

  @Test
  fun edit_replace_swapsThatSlotInPlace() {
    val d = diaryWithPhotos("a.jpg", "b.jpg")
    open(d)
    awaitSection()

    composeTestRule.onNodeWithTag("diary_photo_replace_0").performClick()
    composeTestRule.onNodeWithTag("diary_photo_from_gallery").performClick()
    saveAndAwait { rows(d).firstOrNull()?.path == "new0.jpg" }

    assertEquals(listOf("new0.jpg", "b.jpg"), rows(d).map { it.path })
    assertDeleted("a.jpg")
  }

  @Test
  fun edit_add_appendsAfterExisting() {
    val d = diaryWithPhotos("a.jpg")
    open(d)
    awaitSection()

    composeTestRule.onNodeWithTag("diary_photo_add").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_photo_from_gallery").performClick()
    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithTag("diary_photo_1").fetchSemanticsNodes().isNotEmpty() }
    saveAndAwait { rows(d).size == 2 }

    assertEquals(listOf("a.jpg", "new0.jpg"), rows(d).map { it.path })
  }

  /** 카메라 왕복·회전으로 Activity가 다시 만들어져도 글과 사진 목록이 함께 남는다. */
  @Test
  fun edit_recreation_keepsTextAndPhotoDrafts() {
    val d = diaryWithPhotos("a.jpg", "b.jpg")
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = d) }
      }
    }
    awaitSection()
    composeTestRule.onNodeWithTag("diary_photo_remove_0").performClick()
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo().performTextReplacement("고치던 메모")

    restoration.emulateSavedInstanceStateRestore()

    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("고치던 메모").fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithTag("diary_photo_1").assertDoesNotExist()
    composeTestRule.onNodeWithText("이 기록의 사진 (1/10)").assertExists()
  }

  /** 프로세스가 새로 떠도(ViewModel 교체) 화면 위치와 함께 복원되므로 같은 폼의 입력은 이어지고, 다른 책의 폼에는 섞이지 않는다. */
  @Test
  fun newProcess_keepsSameForm_butNotOtherBooksForm() {
    val otherBook = runBlocking {
      db.bookDao().insert(Book(title = "다른 책", author = "", totalPages = 50, bookcaseId = db.bookDao().getAllBooks().first().first().bookcaseId, status = "READING")).toInt()
    }
    var vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }
    var shownBook = bookId
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = shownBook, diaryId = null) }
      }
    }
    fun awaitForm() = composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      composeTestRule.onAllNodesWithTag("diary_notes_input").fetchSemanticsNodes().isNotEmpty()
    }
    awaitForm()
    composeTestRule.onNodeWithTag("diary_notes_input").performScrollTo().performTextReplacement("쓰던 메모")

    vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }
    restoration.emulateSavedInstanceStateRestore()
    awaitForm()
    composeTestRule.waitUntil(10_000) { composeTestRule.onAllNodesWithText("쓰던 메모").fetchSemanticsNodes().isNotEmpty() }

    shownBook = otherBook
    restoration.emulateSavedInstanceStateRestore()
    awaitForm()
    assertTrue(composeTestRule.onAllNodesWithText("쓰던 메모").fetchSemanticsNodes().isEmpty())
  }

  /** dangling: 파일이 없어도 크래시 없이 안내를 보이고, 행은 지우지 않는다. */
  @Test
  fun edit_danglingPhoto_showsNotice_keepsRow() {
    val d = diaryWithPhotos("gone.jpg")
    store.files.clear()
    open(d)
    awaitSection()

    composeTestRule.onNodeWithText("사진 없음(이 기기에 없음)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("고친 문장")
    saveAndAwait { textOf(d) == "고친 문장" }

    assertEquals(listOf("gone.jpg"), rows(d).map { it.path })
  }

  @Test
  fun noPhotos_editOpensLikeBefore() {
    val d = diaryWithPhotos()
    open(d)
    awaitSection(firstIndexVisible = false)
    composeTestRule.onNodeWithTag("ocr_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("diary_photo_0").assertDoesNotExist()
  }
}
