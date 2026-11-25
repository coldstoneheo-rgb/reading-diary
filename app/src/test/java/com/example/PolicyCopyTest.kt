package com.example

import com.example.ui.screens.PRIVACY_POLICY_URL
import com.example.ui.screens.PRIVACY_SUMMARY_LINES
import com.example.ui.screens.TERMS_SUMMARY
import com.example.ui.screens.privacySummaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 설정 화면의 처리방침 요약이 docs/privacy/privacy-policy.html의 전송 경로를 빠뜨리지 않게 고정한다(발견 D8).
 * 예전 문구는 "기기 바깥으로 … 일체 전송하지 않는" 단정으로 검색어 전송(N1)·자동 백업(N7)을 숨겼다.
 */
class PolicyCopyTest {

  @Test
  fun privacySummary_namesEveryOutboundPath() {
    assertEquals(5, PRIVACY_SUMMARY_LINES.size)
    val text = privacySummaryText(url = "")
    listOf("검색어", "카카오", "Google Books", "표지 이미지", "자동 백업", "Google Gemini", "기기 안에서").forEach {
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
  fun privacyPolicyUrl_isThePublishedPage() {
    // 콘솔에 적는 주소와 같아야 한다. 바꾸려면 gh-pages 게시 주소부터 바꾼다.
    assertEquals("https://coldstoneheo-rgb.github.io/reading-diary/privacy-policy.html", PRIVACY_POLICY_URL)
  }

  /** 게시 사본(gh-pages)은 이 원본을 그대로 복사한 것이다 — 채우지 않은 `[ ]` 자리가 남으면 안 된다. */
  @Test
  fun publishedPolicySource_hasNoPlaceholder() {
    val policy = File("../docs/privacy/privacy-policy.html").readText()
    assertFalse("placeholder left in the policy", policy.contains("["))
    assertTrue(Regex("시행일: <strong>[0-9]{4}-[0-9]{2}-[0-9]{2}<").containsMatchIn(policy))
    assertTrue(policy.contains("<h1>문장서랍 개인정보처리방침</h1>"))
    assertFalse("old app name left in the policy", Regex("독서 ?다이어리").containsMatchIn(policy))
    assertTrue(policy.contains("운영: <strong>딱따구리AI(WoodpeckerAI)</strong>"))
  }

  /** 공개된 처리방침이 사실보다 좁거나 센 말로 돌아가지 않게 한다(표지 요청은 검색 화면만이 아니고, 검색어는 화면 복원을 위해 잠시 남을 수 있다). */
  @Test
  fun publishedPolicy_doesNotNarrowCoverRequestsOrOverclaimSearchTerms() {
    val policy = File("../docs/privacy/privacy-policy.html").readText()
    assertFalse(policy.contains("검색어를 기기에도 기록하지 않습니다"))
    assertFalse(policy.contains("검색 결과의 표지"))
    assertTrue(policy.contains("앱은 검색어를 기록으로 저장하지 않습니다. 다만"))
    assertTrue(policy.contains("되살린 검색어로 다시 검색될 수 있습니다"))
    assertTrue(policy.contains("검색 결과·책 추가와 수정·책 목록·책 상세"))
  }

  @Test
  fun terms_useCurrentAppNameAndMentionSearchTransfer() {
    assertTrue(TERMS_SUMMARY.contains("'문장서랍'"))
    assertTrue(TERMS_SUMMARY.contains("검색어"))
    // 처리방침 4항(자동 백업)·7항 단서(Google 구성요소의 진단 통계)를 약관에서도 빼지 않는다
    assertTrue(TERMS_SUMMARY.contains("자동 백업"))
    assertTrue(TERMS_SUMMARY.contains("ML Kit"))
    assertTrue(TERMS_SUMMARY.contains("진단"))
    // P5: 사용자가 실행하는 백업 파일은 사진이 기기 밖(고른 위치)으로 나가는 경로다 — 약관·요약에서 빼지 않는다
    assertTrue(TERMS_SUMMARY.contains("백업 파일 만들기"))
    assertTrue(privacySummaryText(url = "").contains("백업 파일 만들기"))
  }
}
