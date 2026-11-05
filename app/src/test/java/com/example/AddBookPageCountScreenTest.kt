package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.BookSearchClient
import com.example.data.ocr.OcrOutcome
import com.example.data.ocr.TextExtractor
import com.example.ui.screens.AddEditBookScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReadingViewModel
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

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

  private fun client(kakao: String, google: String) = BookSearchClient(
    OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
      val request = chain.request()
      val isKakao = request.url.host == "dapi.kakao.com"
      requests.add(if (isKakao) "kakao" else "google ${request.url.queryParameter("q")}")
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

    awaitText("검색 결과에 쪽수가 없습니다. 직접 입력해 주세요.")

    assertNoFabricatedPages()
  }

  @Test
  fun kakaoPick_withoutIsbn_sendsNothingToGoogle() {
    pickResult(kakaoNoIsbn, noHits)

    awaitText("검색 결과에 쪽수가 없습니다. 직접 입력해 주세요.")

    assertEquals(listOf("kakao"), requests.toList())
    assertNoFabricatedPages()
  }
}
