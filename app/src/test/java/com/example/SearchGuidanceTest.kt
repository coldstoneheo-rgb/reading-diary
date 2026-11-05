package com.example

import com.example.data.api.connectionFailureText
import com.example.data.api.pickKakaoRestKey
import com.example.ui.screens.NAVER_SHUTDOWN_NOTICE
import com.example.ui.screens.searchGuidanceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-003 실행 7: 도서 검색 안내는 원인을 사실대로 말하고, 존재하지 않는 키 입력 화면으로 보내지 않는다. */
class SearchGuidanceTest {

  @Test
  fun noResults_saysNoMatch_andOffersManualEntry() {
    val text = searchGuidanceText(query = " 청춘 ", error = null, searchedWithKakao = true)
    assertTrue(text.contains("'청춘'"))
    assertTrue(text.contains("직접 입력"))
    assertFalse(text.contains("설정"))
  }

  @Test
  fun errorWithoutKakaoKey_namesGoogleRoute_andExplainsMissingKey() {
    val text = searchGuidanceText(query = "청춘", error = "공용 Google 도서 검색의 호출 한도를 넘었습니다. (HTTP 429)", searchedWithKakao = false)
    assertTrue(text.contains("Google"))
    assertTrue(text.contains("카카오 REST 키가 등록되어 있지 않습니다"))
    assertTrue(text.contains("HTTP 429"))
    assertFalse(text.contains("[설정]"))
    assertFalse(text.contains("API 설정하기"))
  }

  @Test
  fun errorWithKakaoKey_namesKakaoRoute() {
    val text = searchGuidanceText(query = "청춘", error = "카카오 REST 키를 확인해 주세요. (HTTP 401)", searchedWithKakao = true)
    assertTrue(text.startsWith("카카오 도서 검색"))
    assertTrue(text.contains("HTTP 401"))
  }

  @Test
  fun bothCardStates_carryOneNaverShutdownLine() {
    assertTrue(NAVER_SHUTDOWN_NOTICE.contains("네이버 책 검색 API는 2026-07-31 종료"))
    listOf(
      searchGuidanceText(query = "청춘", error = null, searchedWithKakao = true),
      searchGuidanceText(query = "청춘", error = "오류", searchedWithKakao = true)
    ).forEach { text ->
      assertEquals(1, text.lines().count { it == NAVER_SHUTDOWN_NOTICE })
    }
  }

  // ---- pickKakaoRestKey ----

  @Test
  fun kakaoKey_isCleanedOfQuotesAndSpaces() {
    assertEquals("abc123", pickKakaoRestKey(" \"abc123\" "))
    assertEquals("abc123", pickKakaoRestKey("'abc123' "))
  }

  @Test
  fun kakaoKey_blankOrPlaceholder_meansNoKey() {
    assertNull(pickKakaoRestKey("", "   ", "\" \""))
    assertNull(pickKakaoRestKey("KAKAO_REST_API_KEY_PLACEHOLDER"))
    assertNull(pickKakaoRestKey("KAKAO_REST_API_KEY"))
    assertNull(pickKakaoRestKey("MY_KAKAO_REST_API_KEY"))
  }

  @Test
  fun kakaoKey_respectsSourcePriority_andSkipsUnusableSources() {
    assertEquals("device", pickKakaoRestKey("device", "env", "dotenv"))
    assertEquals("env", pickKakaoRestKey("", "env", "dotenv"))
    assertEquals("dotenv", pickKakaoRestKey("", "KAKAO_REST_API_KEY_PLACEHOLDER", "dotenv"))
  }

  @Test
  fun connectionFailure_isTranslatedWithoutExceptionMessage() {
    val secret = "abcdefghij"
    val text = connectionFailureText(java.net.UnknownHostException("dapi.kakao.com header=$secret"))
    assertTrue(text.contains("인터넷 연결"))
    assertFalse(text.contains(secret))
    assertTrue(connectionFailureText(java.net.SocketTimeoutException("t")).contains("늦어"))
    assertTrue(connectionFailureText(IllegalStateException("x")).contains("다시 시도"))
  }

  @Test
  fun errorBeforeAnySearch_usesNeutralRoute() {
    val text = searchGuidanceText(query = "청춘", error = "네트워크 오류", searchedWithKakao = null)
    assertTrue(text.startsWith("온라인 검색"))
  }
}
