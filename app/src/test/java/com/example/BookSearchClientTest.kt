package com.example

import com.example.data.api.BookSearchClient
import com.example.data.api.BookSearchOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** 카카오 1차 + Google 폴백 경로. 가짜 인터셉터가 응답하므로 실제 네트워크 호출은 0이다. */
class BookSearchClientTest {

  private val kakaoHit = """{"meta":{"total_count":1,"is_end":true},"documents":[
    {"title":"녹색절벽","authors":["김작가"],"publisher":"출판사","isbn":"8937460440 9788937460449",
     "thumbnail":"https://search1.kakaocdn.net/thumb/R120x174/?fname=a.jpg","datetime":"2024-01-01T00:00:00.000+09:00"}]}"""
  private val kakaoEmpty = """{"meta":{"total_count":0,"is_end":true},"documents":[]}"""
  private val googleHit = """{"items":[{"volumeInfo":{"title":"Green Cliff","authors":["Kim"],"pageCount":312}}]}"""
  private val googleEmpty = """{"totalItems":0}"""
  private val key = "kakao-test-key"

  private class Reply(val code: Int = 200, val body: String = "", val thrown: Exception? = null)

  private val requests = mutableListOf<Request>()

  private fun client(kakao: Reply?, google: Reply?) = BookSearchClient(
    OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
      val request = chain.request()
      requests.add(request)
      val reply = when (request.url.host) {
        "dapi.kakao.com" -> kakao
        "www.googleapis.com" -> google
        else -> null
      } ?: error("unexpected host ${request.url.host}")
      reply.thrown?.let { throw it }
      Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(reply.code).message("x")
        .body(reply.body.toResponseBody("application/json".toMediaType())).build()
    }).build()
  )

  private fun search(kakao: Reply?, google: Reply?, query: String = "녹색절벽", kakaoKey: String? = key): BookSearchOutcome =
    runBlocking { client(kakao, google).search(query, kakaoKey) }

  private fun hosts() = requests.map { it.url.host }

  @Test
  fun generalQuery_callsKakaoWithoutTarget_withKakaoAkHeader_andStopsThere() {
    val outcome = search(Reply(body = kakaoHit), null)

    assertEquals(listOf("dapi.kakao.com"), hosts())
    val request = requests.single()
    assertEquals("/v3/search/book", request.url.encodedPath)
    assertEquals("녹색절벽", request.url.queryParameter("query"))
    assertNull("일반 검색은 제목·저자를 함께 찾도록 target을 뺀다", request.url.queryParameter("target"))
    assertEquals("KakaoAK $key", request.header("Authorization"))
    assertEquals("녹색절벽", outcome.books.single().title)
    assertNull(outcome.error)
    assertNull(outcome.notice)
    assertTrue(outcome.searchedWithKakao)
  }

  @Test
  fun isbnQuery_usesTargetIsbn_withNormalizedDigits() {
    search(Reply(body = kakaoHit), null, query = "978-89-374-6044-9")

    val url = requests.single().url
    assertEquals("isbn", url.queryParameter("target"))
    assertEquals("9788937460449", url.queryParameter("query"))
  }

  @Test
  fun noKey_goesOnlyToGoogle_withoutAuthorizationHeader() {
    val outcome = search(null, Reply(body = googleHit), kakaoKey = null)

    assertEquals(listOf("www.googleapis.com"), hosts())
    assertNull(requests.single().header("Authorization"))
    assertEquals("녹색절벽", requests.single().url.queryParameter("q"))
    assertEquals("Green Cliff", outcome.books.single().title)
    assertFalse(outcome.searchedWithKakao)
  }

  @Test
  fun noKey_isbnQuery_usesGoogleIsbnPrefix() {
    search(null, Reply(body = googleHit), query = "9788937460449", kakaoKey = null)
    assertEquals("isbn:9788937460449", requests.single().url.queryParameter("q"))
  }

  @Test
  fun noKey_googleFailure_isReportedAsError() {
    val outcome = search(null, Reply(code = 429), kakaoKey = null)
    assertTrue(outcome.books.isEmpty())
    assertTrue(outcome.error!!.contains("HTTP 429"))
    assertFalse(outcome.searchedWithKakao)
  }

  @Test
  fun kakaoZeroHits_fallsBackToGoogle_withoutNotice() {
    val outcome = search(Reply(body = kakaoEmpty), Reply(body = googleHit))

    assertEquals(listOf("dapi.kakao.com", "www.googleapis.com"), hosts())
    assertEquals("Green Cliff", outcome.books.single().title)
    assertNull(outcome.error)
    assertNull(outcome.notice)
  }

  @Test
  fun kakaoZeroHits_googleFailure_isStillNoResultsNotAnError() {
    val outcome = search(Reply(body = kakaoEmpty), Reply(code = 500))
    assertTrue(outcome.books.isEmpty())
    assertNull(outcome.error)
  }

  @Test
  fun kakaoHttpFailures_showTheirOwnCause_aboveGoogleResults() {
    val expected = mapOf(
      401 to "카카오 REST 키를 확인",
      403 to "검색 API가 활성화",
      429 to "일일 한도",
      500 to "HTTP 500"
    )
    expected.forEach { (code, phrase) ->
      val outcome = search(Reply(code = code), Reply(body = googleHit))
      assertEquals("Green Cliff", outcome.books.single().title)
      assertNull(outcome.error)
      assertTrue("$code notice: ${outcome.notice}", outcome.notice!!.contains(phrase))
      assertTrue(outcome.notice!!.contains("Google 도서"))
    }
  }

  @Test
  fun kakaoFailure_googleZeroHits_showsKakaoCauseAsError() {
    val outcome = search(Reply(code = 401), Reply(body = googleEmpty))
    assertTrue(outcome.books.isEmpty())
    assertTrue(outcome.error!!.contains("카카오 REST 키를 확인"))
    assertNull(outcome.notice)
  }

  @Test
  fun bothFail_errorNamesBothServices() {
    val outcome = search(Reply(code = 403), Reply(code = 429))
    assertTrue(outcome.books.isEmpty())
    assertTrue(outcome.error!!.contains("HTTP 403"))
    assertTrue(outcome.error!!.contains("Google"))
    assertTrue(outcome.error!!.contains("HTTP 429"))
  }

  @Test
  fun kakaoBadJson_fallsBackToGoogle_withNotice() {
    val outcome = search(Reply(body = "not json"), Reply(body = googleHit))
    assertEquals("Green Cliff", outcome.books.single().title)
    assertTrue(outcome.notice!!.contains("해석할 수 없습니다"))
  }

  @Test
  fun exceptionMessage_withKeyAndUrl_neverReachesUserText() {
    val leaky = UnknownHostException("dapi.kakao.com Authorization: KakaoAK $key")
    val outcome = search(Reply(thrown = leaky), Reply(thrown = leaky))

    assertTrue(outcome.error!!.contains("인터넷 연결"))
    assertFalse(outcome.error!!.contains(key))
    assertFalse(outcome.error!!.contains("dapi.kakao.com"))
  }

  @Test
  fun keyThatIsNotHeaderSafe_isNeverSent_andItsValueIsNotShown() {
    val badKey = "kakao​key"
    val outcome = search(null, Reply(body = googleHit), kakaoKey = badKey)

    assertEquals("헤더에 넣지 않고 Google만 호출한다", listOf("www.googleapis.com"), hosts())
    assertEquals("Green Cliff", outcome.books.single().title)
    assertTrue(outcome.notice!!.contains("쓸 수 없는 문자"))
    assertFalse(outcome.notice!!.contains(badKey))
  }
}
