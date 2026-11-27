package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Book
import com.example.data.Bookcase
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.OcrDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 새 사진을 고르면 앞 사진의 반전·자르기 틀(·회전)이 이어지지 않고 기본값(틀 = 사진 전체)으로 돌아간다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrNewPhotoResetTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val app: Application get() = ApplicationProvider.getApplicationContext()
  private val db: AppDatabase get() = AppDatabase.getDatabase(app)

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private var pickCount = 0

  /** 갤러리를 띄우지 않고 고를 때마다 다른 사진을 돌려준다. */
  private val registryOwner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        pickCount++
        dispatchResult(requestCode, Uri.parse("content://photos/picked$pickCount"))
      }
    }
  }

  private var bookId = 0

  @Before
  fun setUp() = runBlocking {
    val caseId = db.bookcaseDao().insert(Bookcase(name = "화면 테스트")).toInt()
    bookId = db.bookDao().insert(Book(title = "화면 테스트 책", author = "", totalPages = 100, bookcaseId = caseId, status = "READING")).toInt()
  }

  private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

  @Test
  fun pickingANewPhoto_resetsFlipAndCropFrame() {
    val vm = ReadingViewModel(app, NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = null) }
      }
    }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("save_diary_submit_button").fetchSemanticsNodes().isNotEmpty()
    }

    // 첫 사진: 반전을 켜고 왼쪽 끝을 30%로 옮긴다.
    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    composeTestRule.onNodeWithContentDescription("좌우 반전").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("crop_flip_button").assertIsOn()
    // 슬라이더는 접힌 «틀 조정» 안(R2) — 한 번 눌러 연다.
    composeTestRule.onNodeWithTag("crop_adjust_toggle").performScrollTo().performClick()
    composeTestRule.onAllNodes(slider).onFirst().performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
    composeTestRule.onNodeWithText("좌측 끝: 30%").assertExists()

    // 새 사진: 반전 꺼짐 · 틀 = 사진 전체.
    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("crop_flip_button").performScrollTo().assertIsOff()
    composeTestRule.onNodeWithText("좌측 끝: 0%").assertExists()
    composeTestRule.onNodeWithText("우측 끝: 100%").assertExists()
  }

  private object FailingExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = OcrOutcome.Failed("모델을 준비하는 중이에요")
  }

  /** 실패 안내는 버튼 아래에 남되, 아직 읽지 않은 새 사진 아래에 앞 사진의 실패가 남아 있으면 안 된다. */
  @Test
  fun ocrFailure_staysUnderTheButton_andIsClearedByANewPhoto() {
    val vm = ReadingViewModel(app, FailingExtractor, FailingExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        MyApplicationTheme { OcrDiaryScreen(viewModel = vm, bookId = bookId, diaryId = null) }
      }
    }
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodesWithTag("save_diary_submit_button").fetchSemanticsNodes().isNotEmpty()
    }
    fun errors() = composeTestRule.onAllNodesWithTag("ocr_error").fetchSemanticsNodes().size

    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    composeTestRule.onNodeWithTag("crop_confirm_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    check(errors() == 0)
    composeTestRule.runOnUiThread { vm.processUnderlineOcr(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)) }
    composeTestRule.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); errors() == 1 }
    composeTestRule.onNodeWithText("모델을 준비하는 중이에요").assertExists()

    composeTestRule.onNodeWithContentDescription("사진 선택").performClick()
    composeTestRule.waitForIdle()
    check(errors() == 0) { "새 사진을 고른 즉시 지워져야 한다(자르기 확정이 대신 지우면 안 된다)" }
    composeTestRule.onNodeWithTag("crop_confirm_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ocr_button").assertExists()
    check(errors() == 0) { "앞 사진의 실패 안내가 새 사진 아래에 남았다" }
  }
}
