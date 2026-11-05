package com.example

import com.example.data.api.BookSearchParsers
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 도서 검색 응답 파싱의 오프라인 회귀 테스트. 네트워크·자격증명 없이 항상 같은 결과를 낸다.
 *
 * 주의: 이 테스트는 `org.json:json` 레퍼런스 구현으로 실행된다. Android 기기의 `org.json`과는
 * 타입 강제변환 규칙(예: 숫자를 `getString`으로 읽을 때)이 다를 수 있다.
 */
class BookSearchParsersTest {

  @Test
  fun kakao_readsDocumentsAndJoinsAuthors_ignoringOtherFields() {
    val body = """
      {"meta":{"total_count":2,"pageable_count":2,"is_end":true},"documents":[
        {"title":"토비의 스프링 3.1 (양장본)","authors":["이일민"],"translators":["역자"],"publisher":"에이콘","isbn":"8960773433 9788960773431",
         "thumbnail":"https://search1.kakaocdn.net/thumb/R120x174.q85/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fa.jpg",
         "datetime":"2012-09-21T00:00:00.000+09:00","price":37800,"sale_price":34020,"status":"정상"},
        {"title":"객체지향의 사실과 오해","authors":["조영호 저","김철수 옮김","지음"],"isbn":"","thumbnail":""}
      ]}
    """.trimIndent()

    val result = BookSearchParsers.parseKakaoBooks(body)

    assertEquals(2, result.size)
    assertEquals("토비의 스프링 3.1", result[0].title)
    assertEquals("이일민", result[0].author)
    assertEquals("https://search1.kakaocdn.net/thumb/R120x174.q85/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fa.jpg", result[0].coverUrl)
    assertEquals("카카오 응답에는 쪽수가 없어 기본값을 쓴다", 250, result[0].pageCount)
    assertEquals("조영호, 김철수", result[1].author)
    assertEquals("", result[1].coverUrl)
  }

  @Test
  fun kakao_missingDocumentsOrFieldsFallsBackSafely() {
    assertTrue(BookSearchParsers.parseKakaoBooks("""{"meta":{"total_count":0}}""").isEmpty())

    val result = BookSearchParsers.parseKakaoBooks("""{"documents":[{},{"title":"","authors":[]},{"title":null,"authors":[null],"thumbnail":null}]}""")
    assertEquals(3, result.size)
    result.forEach {
      assertEquals("알 수 없는 제목", it.title)
      assertEquals("지은이 미상", it.author)
      assertEquals("", it.coverUrl)
    }
  }

  @Test
  fun kakao_keepsAngleBracketsInTitle_butDropsHighlightTags() {
    val body = """{"documents":[{"title":"<어린 왕자> 깊이 읽기"},{"title":"<b>녹색</b>절벽"}]}"""
    val result = BookSearchParsers.parseKakaoBooks(body)
    assertEquals("<어린 왕자> 깊이 읽기", result[0].title)
    assertEquals("녹색절벽", result[1].title)
  }

  @Test
  fun kakao_httpThumbnailIsUpgraded_onlyAtTheStart() {
    val body = """{"documents":[{"title":"T","thumbnail":"http://img.example/t?fname=http://origin/a.jpg"}]}"""
    assertEquals("https://img.example/t?fname=http://origin/a.jpg", BookSearchParsers.parseKakaoBooks(body)[0].coverUrl)
  }

  @Test
  fun google_readsVolumeInfoAndUpgradesThumbnailToHttps() {
    val body = """
      {"items":[
        {"volumeInfo":{"title":"Kotlin in Action","authors":["Dmitry Jemerov","Svetlana Isakova"],"pageCount":360,
          "imageLinks":{"smallThumbnail":"http://books.google.com/small.jpg","thumbnail":"http://books.google.com/thumb.jpg"}}},
        {"id":"no-volume-info"},
        {"volumeInfo":{"title":"No Authors"}}
      ]}
    """.trimIndent()

    val result = BookSearchParsers.parseGoogleBooks(body)

    assertEquals(2, result.size)
    assertEquals("Kotlin in Action", result[0].title)
    assertEquals("Dmitry Jemerov", result[0].author)
    assertEquals(360, result[0].pageCount)
    assertEquals("https://books.google.com/thumb.jpg", result[0].coverUrl)
    assertEquals("No Authors", result[1].title)
    assertEquals("지은이 미상", result[1].author)
    assertEquals(250, result[1].pageCount)
    assertEquals("", result[1].coverUrl)
  }

  @Test
  fun google_missingItemsReturnsEmpty() {
    assertTrue(BookSearchParsers.parseGoogleBooks("""{"kind":"books#volumes","totalItems":0}""").isEmpty())
  }

  @Test
  fun google_smallThumbnailOnly_isUsedAsFallback() {
    // optString("thumbnail")은 결측 시 ""를 돌려주므로 예전엔 폴백이 죽어 있었다. 이제 smallThumbnail로 내려간다.
    val body = """{"items":[{"volumeInfo":{"title":"T","imageLinks":{"smallThumbnail":"http://x/small.jpg"}}}]}"""
    assertEquals("https://x/small.jpg", BookSearchParsers.parseGoogleBooks(body)[0].coverUrl)
    val noLinks = """{"items":[{"volumeInfo":{"title":"T"}}]}"""
    assertEquals("", BookSearchParsers.parseGoogleBooks(noLinks)[0].coverUrl)
  }

  @Test
  fun invalidJson_propagatesJSONException() {
    // 계약: 파싱 실패는 호출자(BookSearchClient)가 원인 문구로 처리한다
    assertThrows(JSONException::class.java) { BookSearchParsers.parseKakaoBooks("not json") }
    assertThrows(JSONException::class.java) { BookSearchParsers.parseGoogleBooks("not json") }
  }
}
