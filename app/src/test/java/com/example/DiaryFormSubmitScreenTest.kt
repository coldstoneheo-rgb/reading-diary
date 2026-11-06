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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.shadows.ShadowToast
import java.io.IOException

/**
 * 새 기록 저장 표식(submittedCreatedAt) 뒤의 화면 동작. 화면을 직접 띄워 ViewModel의 위치만 옮기므로
 * «구성은 살아 있는데 위치가 이 화면이 아닌» 상태를 만들 수 있다. 저장은 사진 저장소의 [FakeDiaryPhotoStore.gate]에서 세운다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiaryFormSubmitScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)
  private val form: Screen get() = Screen.AddDiary(bookId)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private val registryOwner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        dispatchResult(requestCode, Uri.parse("content://photos/picked"))
      }
    }
  }

  private var bookId = 0
  private lateinit var gate: CompletableDeferred<Unit>
  private lateinit var store: FakeDiaryPhotoStore
  private lateinit var vm: ReadingViewModel

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "저장 표식 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "저장 표식 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
    gate = CompletableDeferred()
    store = FakeDiaryPhotoStore(gate = gate)
    vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, store) { false }
    ShadowToast.reset()
  }

  private fun records() = runBlocking { db.diaryDao().getAllDiaries().first() }.filter { it.bookId == bookId }

  private fun pump(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      composeTestRule.mainClock.advanceTimeByFrame() // 위치 변화로 효과가 다시 돌려면 재구성 프레임이 필요하다
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue("condition not met within 10s", condition())
  }

  /** 폼을 채우고 사진을 띄운 뒤 저장을 눌러 저장소 앞에 세운다. */
  private fun openAndSubmit() {
    vm.navigateTo(Screen.BookDetail(bookId))
    vm.navigateTo(form)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = null) }
      }
    }
    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      composeTestRule.onAllNodesWithText("사진 선택").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("사진 선택").onFirst().performScrollTo().performClick()
    composeTestRule.onNodeWithTag("diary_page_input").performScrollTo().performTextReplacement("7")
    composeTestRule.onNodeWithTag("diary_extracted_text_input").performScrollTo().performTextReplacement("표식 문장")
    composeTestRule.waitForIdle()
    clickSave()
    pump { vm.diarySaving.value }
  }

  private fun clickSave() =
    composeTestRule.onNodeWithTag("save_diary_submit_button").performScrollTo().performClick()

  private fun awaitToast(text: String) = pump { ShadowToast.getTextOfLatestToast()?.contains(text) == true }

  /** 편집 화면이 사진을 읽지 못하면 앱이 죽지 않고, 커밋 여부를 가릴 수 없으니 저장을 열지 않는다. */
  @Test
  fun editPhotoLoadFailure_doesNotCrash_andKeepsSaveClosed() {
    val photoRows = runBlocking {
      val d = db.diaryDao().insert(Diary(bookId = bookId, page = 7, selectedText = "처음 문장", notes = "")).toInt()
      db.diaryPhotoDao().insertAll(listOf(DiaryPhoto(diaryId = d, path = "kept.jpg", sortOrder = 0)))
      store.seed("kept.jpg")
      d
    }
    val sqlite = db.openHelper.writableDatabase
    sqlite.execSQL("ALTER TABLE diary_photos RENAME TO diary_photos_hidden")
    try {
      vm.navigateTo(Screen.BookDetail(bookId))
      vm.navigateTo(Screen.AddDiary(bookId, photoRows))
      composeTestRule.setContent {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
          MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = photoRows) }
        }
      }
      awaitToast("사진 정보를 불러오지 못했습니다")
      pump { composeTestRule.onAllNodesWithText("처음 문장").fetchSemanticsNodes().isNotEmpty() }
      clickSave()
      awaitToast("사진 정보를 불러오는 중입니다")
      assertTrue(!vm.diarySaving.value)
    } finally {
      sqlite.execSQL("ALTER TABLE diary_photos_hidden RENAME TO diary_photos")
    }
    assertEquals(listOf("kept.jpg"), runBlocking { db.diaryPhotoDao().photosFor(photoRows) }.map { it.path })
    assertTrue(store.deleted.isEmpty())
  }

  /** 저장 실패 뒤 표식이 풀려 버튼이 살아나고, 다시 누르면 저장된다. */
  @Test
  fun saveFailure_onForm_saysInputKept_reenablesButton_andRetrySaves() {
    openAndSubmit()

    gate.completeExceptionally(IOException("disk"))
    awaitToast("입력한 내용은 그대로 있으니")
    pump { !vm.diarySaving.value }
    assertTrue(records().isEmpty())

    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      runCatching { composeTestRule.onNodeWithTag("save_diary_submit_button").assertIsEnabled() }.isSuccess
    }
    store.gate = null
    clickSave()
    pump { records().size == 1 && !vm.diarySaving.value }
    assertEquals(Screen.BookDetail(bookId), vm.currentScreenState.value)
  }

  /** 이미 화면을 떠났으면 입력이 남아 있지 않으니 «그대로 있다»고 말하지 않는다. */
  @Test
  fun saveFailure_afterLeaving_doesNotClaimInputKept() {
    openAndSubmit()

    vm.navigateBack()
    gate.completeExceptionally(IOException("disk"))

    awaitToast("다시 작성해 주세요")
    assertTrue(records().isEmpty())
  }

  /**
   * 떠난 동안 커밋되면 판정이 건너뛰어진다. 돌아오면 위치 변화로 판정을 다시 돌려 초안을 버려야 한다.
   * effect 키를 고정하는 테스트이고 사용자 경로의 재현은 아니다. 실제 앱의 navigateTo는 토큰을 바꿔 새 폼을 만든다.
   */
  @Test
  fun returningToForm_rerunsCheck_andLeavesWhenAlreadySaved() {
    openAndSubmit()

    vm.navigateBack()
    gate.complete(Unit)
    pump { records().size == 1 && !vm.diarySaving.value }
    composeTestRule.waitForIdle() // 저장 종료로 효과가 다시 돌아 «이 화면이 아니라서» 판정을 건너뛰게 한다
    shadowOf(Looper.getMainLooper()).idle()
    composeTestRule.waitForIdle()
    assertEquals("전제: 떠난 화면에서는 위치를 건드리지 않는다", Screen.BookDetail(bookId), vm.currentScreenState.value)

    vm.navigateTo(form)

    awaitToast("이미 저장된 기록입니다.")
    pump { vm.currentScreenState.value == Screen.BookDetail(bookId) }
    assertEquals(1, records().size)
  }

  /** 판정 쿼리가 실패해도 앱이 죽지 않고, 알린 뒤 입력을 남긴 채 버튼을 돌려준다. 테이블 이름을 잠시 바꿔 실제 SQLite 오류를 낸다. */
  @Test
  fun checkQueryFailure_doesNotCrash_warns_andKeepsForm() {
    openAndSubmit()
    vm.navigateBack()
    gate.complete(Unit)
    pump { records().size == 1 && !vm.diarySaving.value }
    composeTestRule.waitForIdle()

    val sqlite = db.openHelper.writableDatabase
    sqlite.execSQL("ALTER TABLE diaries RENAME TO diaries_hidden")
    try {
      vm.navigateTo(form)
      awaitToast("저장 여부를 확인하지 못했습니다")
    } finally {
      sqlite.execSQL("ALTER TABLE diaries_hidden RENAME TO diaries")
    }

    assertEquals(form, vm.currentScreenState.value)
    composeTestRule.waitUntil(10_000) {
      shadowOf(Looper.getMainLooper()).idle()
      runCatching { composeTestRule.onNodeWithTag("save_diary_submit_button").assertIsEnabled() }.isSuccess
    }
    assertEquals(1, records().size)
  }
}
