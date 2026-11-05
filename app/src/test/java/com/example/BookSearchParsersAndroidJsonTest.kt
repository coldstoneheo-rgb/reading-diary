package com.example

import com.example.data.api.BookSearchParsers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android의 org.json은 JSON null을 optString에서 문자열 "null"로 돌려준다. 순수 JVM의 org.json:json은 기본값을 돌려주므로
 * 그곳에서는 이 경로를 고정할 수 없다. 첫 테스트가 Android 구현으로 도는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookSearchParsersAndroidJsonTest {

  @Test
  fun runsOnAndroidJsonImplementation() {
    assertEquals("null", JSONObject("""{"a":null}""").optString("a", ""))
  }

  @Test
  fun kakao_jsonNulls_areNotShownAsTheWordNull() {
    val book = BookSearchParsers.parseKakaoBooks("""{"documents":[{"title":null,"authors":[null],"thumbnail":null}]}""").single()
    assertEquals("알 수 없는 제목", book.title)
    assertEquals("지은이 미상", book.author)
    assertEquals("", book.coverUrl)
  }

  @Test
  fun google_jsonNulls_areNotShownAsTheWordNull() {
    val body = """{"items":[{"volumeInfo":{"title":null,"authors":[null],"imageLinks":{"thumbnail":null,"smallThumbnail":null}}}]}"""
    val book = BookSearchParsers.parseGoogleBooks(body).single()
    assertEquals("알 수 없는 제목", book.title)
    assertEquals("지은이 미상", book.author)
    assertEquals("", book.coverUrl)
  }
}
