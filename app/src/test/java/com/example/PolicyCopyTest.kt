package com.example

import com.example.ui.screens.PRIVACY_POLICY_URL
import com.example.ui.screens.PRIVACY_SUMMARY_LINES
import com.example.ui.screens.TERMS_SUMMARY
import com.example.ui.screens.privacySummaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 설정 화면의 처리방침 요약이 docs/privacy/privacy-policy.html의 전송 경로를 빠뜨리지 않게 고정한다(발견 D8).
 * 예전 문구는 "기기 바깥으로 … 일체 전송하지 않는" 단정으로 검색어 전송(N1)·자동 백업(N7)을 숨겼다.
 */
class PolicyCopyTest {

  @Test
  fun privacySummary_namesEveryOutboundPath() {
    assertEquals(5, PRIVACY_SUMMARY_LINES.size)
    val text = privacySummaryText(url = "")
    listOf("검색어", "네이버", "표지 이미지", "자동 백업", "Google Gemini", "기기 안에서").forEach {
      assertTrue("privacy summary must mention \"$it\"", text.contains(it))
    }
    assertFalse("no absolute 'never leaves the device' claim", text.contains("일체"))
  }

  @Test
  fun privacySummary_showsUrlLineOnlyWhenSet() {
    assertFalse(privacySummaryText(url = "").contains("전문:"))
    assertTrue(privacySummaryText(url = "https://example.org/privacy-policy.html").endsWith("전문: https://example.org/privacy-policy.html"))
  }

  @Test
  fun privacyPolicyUrl_isBlankOrHttps() {
    // U4(처리방침 배포) 전에는 비워 둔다. 채울 때는 https만.
    assertTrue(PRIVACY_POLICY_URL.isBlank() || PRIVACY_POLICY_URL.startsWith("https://"))
  }

  @Test
  fun terms_useCurrentAppNameAndMentionSearchTransfer() {
    assertTrue(TERMS_SUMMARY.contains("'독서 다이어리'"))
    assertTrue(TERMS_SUMMARY.contains("검색어"))
    // 처리방침 4항(자동 백업)·7항 단서(Google 구성요소의 진단 통계)를 약관에서도 빼지 않는다
    assertTrue(TERMS_SUMMARY.contains("자동 백업"))
    assertTrue(TERMS_SUMMARY.contains("ML Kit"))
    assertTrue(TERMS_SUMMARY.contains("진단"))
  }
}
