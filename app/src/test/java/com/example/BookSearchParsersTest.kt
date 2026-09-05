package com.example

import com.example.data.api.BookSearchParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 도서 검색 응답 파싱의 오프라인 회귀 테스트. 네트워크·자격증명 없이 항상 같은 결과를 낸다.
 */
class BookSearchParsersTest {

  @Test
  fun naver_stripsHtmlTagsEditionSuffixAndAuthorRoles() {
    val body = """
      {"items":[
        {"title":"<b>토비의</b> 스프링 3.1 (양장본)","author":"<b>저자</b>: 이일민 지음","image":"https://img.example/1.jpg"},
        {"title":"객체지향의 사실과 오해","author":"조영호^김철수|박영희","image":""},
        {"title":"코스모스 [양장본]","author":"칼 세이건 저"},
        {"title":"수학의 정석 (제3판)","author":"옮김: 홍성대"}
      ]}
    """.trimIndent()

    val result = BookSearchParsers.parseNaverBooks(body)

    assertEquals(4, result.size)
    // HTML 제거가 역할어 접두 제거보다 먼저 적용되어야 "^저자:" 앵커가 매치된다
    assertEquals("토비의 스프링 3.1", result[0].title)
    assertEquals("이일민", result[0].author)
    assertEquals("https://img.example/1.jpg", result[0].coverUrl)
    assertEquals(250, result[0].pageCount)
    assertEquals("조영호, 김철수, 박영희", result[1].author)
    assertEquals("코스모스", result[2].title)
    assertEquals("칼 세이건", result[2].author)
    assertEquals("수학의 정석", result[3].title)
    assertEquals("홍성대", result[3].author)
  }

  @Test
  fun naver_missingItemsOrFieldsFallsBackSafely() {
    assertTrue(BookSearchParsers.parseNaverBooks("""{"total":0}""").isEmpty())

    val result = BookSearchParsers.parseNaverBooks("""{"items":[{}]}""")
    assertEquals(1, result.size)
    assertEquals("알 수 없는 제목", result[0].title)
    assertEquals("지은이 미상", result[0].author)
    assertEquals("", result[0].coverUrl)
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
}
