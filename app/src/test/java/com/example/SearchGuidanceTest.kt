package com.example

import com.example.ui.screens.searchGuidanceText
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
    assertTrue(text.contains("네이버 검색 키가 들어 있지 않습니다"))
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

  @Test
  fun errorBeforeAnySearch_usesNeutralRoute() {
    val text = searchGuidanceText(query = "청춘", error = "네트워크 오류", searchedWithNaver = null)
    assertTrue(text.startsWith("온라인 검색"))
  }
}
