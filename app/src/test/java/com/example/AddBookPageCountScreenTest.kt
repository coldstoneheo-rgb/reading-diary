package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.api.BookSearchClient
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.AddEditBookScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 신고 ②: 검색 결과를 고르면 쪽수가 250으로 채워졌다. 가짜 인터셉터가 응답하므로 실제 네트워크 호출은 0이다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddBookPageCountScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private object NoCallExtractor : TextExtractor {
    override suspend fun extract(bitmap: Bitmap): OcrOutcome = error("not used")
  }

  private val isbn = "9788937460449"
  private val kakaoHit = """{"documents":[{"title":"녹색절벽","authors":["김작가"],"isbn":"8937460440 $isbn","thumbnail":""}]}"""
  private val kakaoNoIsbn = """{"documents":[{"title":"녹색절벽","authors":["김작가"],"isbn":"","thumbnail":""}]}"""
  private val noHits = """{"totalItems":0}"""
  private val requests = CopyOnWriteArrayList<String>()

  /** 쪽수 조회 응답을 잡아 두어, 응답 전에 사용자가 움직이는 경우를 만든다. */
  private var holdLookup: CountDownLatch? = null

  private fun client(kakao: String, google: String) = BookSearchClient(
    OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
      val request = chain.request()
      val isKakao = request.url.host == "dapi.kakao.com"
      requests.add(if (isKakao) "kakao" else "google ${request.url.queryParameter("q")}")
      if (!isKakao) holdLookup?.await(15, TimeUnit.SECONDS)
      Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("x")
        .body((if (isKakao) kakao else google).toResponseBody("application/json".toMediaType())).build()
    }).build()
  )

  private fun pickResult(kakao: String, google: String) {
    val vm = ReadingViewModel(ApplicationProvider.getApplicationContext<Application>(), NoCallExtractor, NoCallExtractor, FakeDiaryPhotoStore()) { false }
    composeTestRule.setContent {
      MyApplicationTheme {
        AddEditBookScreen(
          viewModel = vm,
          startWithSearch = true,
          bookSearchClient = client(kakao, google),
          kakaoKeyProvider = { "test-key" }
        )
      }
    }
    composeTestRule.onNodeWithTag("search_book_input").performTextReplacement("녹색절벽")
    awaitText("저자 / 작가: 김작가")
    assertTrue("«쪽 분량» 표기를 지웠다", composeTestRule.onAllNodesWithText("쪽 분량", substring = true).fetchSemanticsNodes().isEmpty())
    composeTestRule.onNodeWithText("저자 / 작가: 김작가").performClick()
  }

  private fun awaitText(text: String) = composeTestRule.waitUntil(15_000) {
    shadowOf(Looper.getMainLooper()).idle()
    composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun assertNoFabricatedPages() =
    assertTrue(composeTestRule.onAllNodesWithText("250").fetchSemanticsNodes().isEmpty())

  @Test
  fun kakaoPick_fillsPagesFromGoogleByIsbn_withOneLookup() {
    pickResult(kakaoHit, """{"items":[{"volumeInfo":{"title":"녹색절벽","pageCount":274}}]}""")

    awaitText("274")

    assertEquals(listOf("kakao", "google isbn:$isbn"), requests.toList())
    assertNoFabricatedPages()
  }

  @Test
  fun kakaoPick_lookupFindsNothing_leavesPagesEmpty_andAsksUser() {
    pickResult(kakaoHit, noHits)

    awaitText("쪽수를 가져오지 못했습니다. 직접 입력해 주세요.")

    assertNoFabricatedPages()
  }

  @Test
  fun kakaoPick_withoutIsbn_sendsNothingToGoogle() {
    pickResult(kakaoNoIsbn, noHits)

    awaitText("쪽수를 가져오지 못했습니다. 직접 입력해 주세요.")

    assertEquals(listOf("kakao"), requests.toList())
    assertNoFabricatedPages()
  }

  /** 잡아 둔 조회가 테스트 뒤까지 남아 메인 디스패처로 돌아오지 않게 한다. */
  @After
  fun releaseHeldLookup() {
    holdLookup?.countDown()
  }

  private val found274 = """{"items":[{"volumeInfo":{"title":"녹색절벽","pageCount":274}}]}"""
  private val kakaoHitWithCover = """{"documents":[{"title":"녹색절벽","authors":["김작가"],"isbn":"8937460440 $isbn","thumbnail":"https://img.example/a.jpg"}]}"""

  /** 잡아 둔 응답을 놓고, 보조 조회 코루틴이 끝날 때까지 돌린다. */
  private fun releaseLookupAndSettle(latch: CountDownLatch) {
    latch.countDown()
    repeat(5) {
      Thread.sleep(100)
      shadowOf(Looper.getMainLooper()).idle()
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun lateLookup_doesNotOverwriteWhatUserTyped() {
    val latch = CountDownLatch(1).also { holdLookup = it }
    pickResult(kakaoHit, found274)
    composeTestRule.onNodeWithTag("book_total_pages_input").performTextReplacement("384")

    releaseLookupAndSettle(latch)

    assertTrue(composeTestRule.onAllNodesWithText("384").fetchSemanticsNodes().isNotEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("274").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun choosingAnotherBook_cancelsPendingLookup() {
    val latch = CountDownLatch(1).also { holdLookup = it }
    pickResult(kakaoHitWithCover, found274)
    composeTestRule.onNodeWithText("다른 도서 검색 ➔").performClick()
    composeTestRule.onNodeWithText("검색 없이 직접 입력", substring = true).performClick()

    releaseLookupAndSettle(latch)

    assertTrue(composeTestRule.onAllNodesWithText("274").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun blankPages_blocksSave_andStoresNothing() {
    pickResult(kakaoHit, noHits)
    awaitText("쪽수를 가져오지 못했습니다. 직접 입력해 주세요.")
    val db = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext<Application>())
    val before = runBlocking { db.bookDao().getAllBooks().first() }.size

    composeTestRule.onNodeWithTag("submit_book_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals("올바른 전체 페이지 수를 기재해주세요.", ShadowToast.getTextOfLatestToast())
    assertEquals(before, runBlocking { db.bookDao().getAllBooks().first() }.size)
    assertFalse(composeTestRule.onAllNodesWithText("250").fetchSemanticsNodes().isNotEmpty())
  }
}
