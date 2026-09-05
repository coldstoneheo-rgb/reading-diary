package com.example

import com.example.ui.screens.areNaverKeysHeaderSafe
import com.example.ui.screens.connectionFailureText
import com.example.ui.screens.pickNaverCredentials
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
    val text = searchGuidanceText(query = " 청춘 ", error = null, searchedWithNaver = true)
    assertTrue(text.contains("'청춘'"))
    assertTrue(text.contains("직접 입력"))
    assertFalse(text.contains("설정"))
  }

  @Test
  fun errorWithoutNaverKeys_namesGoogleRoute_andExplainsMissingKeys() {
    val text = searchGuidanceText(query = "청춘", error = "공용 Google 도서 검색의 호출 한도를 넘었습니다 (HTTP 429).", searchedWithNaver = false)
    assertTrue(text.contains("Google"))
    assertTrue(text.contains("네이버 검색 키가 등록되어 있지 않습니다"))
    assertTrue(text.contains("HTTP 429"))
    assertFalse(text.contains("[설정]"))
    assertFalse(text.contains("API 설정하기"))
  }

  @Test
  fun errorWithNaverKeys_namesNaverRoute_notGoogleQuota() {
    val text = searchGuidanceText(query = "청춘", error = "네이버 검색 API 인증에 실패했습니다.", searchedWithNaver = true)
    assertTrue(text.startsWith("네이버 도서 검색"))
    assertFalse(text.contains("구글 API 호출 한도"))
  }

  // ---- pickNaverCredentials ----

  @Test
  fun credentials_bothPresent_areCleanedOfQuotesAndSpaces() {
    assertEquals("ABCDEFGHIJKLMNOPQRST" to "abcdefghij", pickNaverCredentials(" \"ABCDEFGHIJKLMNOPQRST\" ", "'abcdefghij' "))
  }

  @Test
  fun credentials_placeholdersOrBlank_meanNoKeys() {
    assertNull(pickNaverCredentials("NAVER_CLIENT_ID_PLACEHOLDER", "abcdefghij"))
    assertNull(pickNaverCredentials("ABCDEFGHIJKLMNOPQRST", "MY_NAVER_CLIENT_SECRET"))
    // 자리표시자 판정은 양쪽 합집합: ID 자리에 Secret 자리표시자가 와도 키 없음으로 본다.
    assertNull(pickNaverCredentials("NAVER_CLIENT_SECRET", "abcdefghij"))
    assertNull(pickNaverCredentials("\" \"", "abcdefghij"))   // 따옴표 안 공백은 키가 아니다
    assertNull(pickNaverCredentials("", "abcdefghij"))
    assertNull(pickNaverCredentials("ABCDEFGHIJKLMNOPQRST", ""))
  }

  @Test
  fun credentials_swappedIdAndSecret_areCorrected() {
    // 네이버 Client ID(20자)가 Secret(10자)보다 길다. 예전 버전이 뒤바꿔 저장한 기기를 보정한다.
    assertEquals("ABCDEFGHIJKLMNOPQRST" to "abcdefghij", pickNaverCredentials("abcdefghij", "ABCDEFGHIJKLMNOPQRST"))
  }

  @Test
  fun credentials_withNonAsciiCharacters_areNotHeaderSafe() {
    // 비ASCII 키를 헤더에 넣으면 OkHttp 예외 메시지에 값이 통째로 실린다(CLAUDE.md). 헤더 투입 전에 걸러야 한다.
    assertTrue(areNaverKeysHeaderSafe("ABCDEFGHIJKLMNOPQRST", "abcdefghij"))
    assertFalse(areNaverKeysHeaderSafe("ABCDEFGHIJKLMNOPQRST", "abcdefghi\u200b"))   // zero-width space
    assertFalse(areNaverKeysHeaderSafe("ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴ", "abcdefghij"))   // 전각 문자
  }

  @Test
  fun connectionFailure_isTranslatedWithoutExceptionMessage() {
    val secret = "abcdefghij"
    val text = connectionFailureText(java.net.UnknownHostException("openapi.naver.com header=$secret"))
    assertTrue(text.contains("인터넷 연결"))
    assertFalse(text.contains(secret))
    assertTrue(connectionFailureText(java.net.SocketTimeoutException("t")).contains("늦어"))
    assertTrue(connectionFailureText(IllegalStateException("x")).contains("다시 시도"))
  }

  @Test
  fun errorBeforeAnySearch_usesNeutralRoute() {
    val text = searchGuidanceText(query = "청춘", error = "네트워크 오류", searchedWithNaver = null)
    assertTrue(text.startsWith("온라인 검색"))
  }
}
