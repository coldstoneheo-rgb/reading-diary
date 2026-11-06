package com.example

import com.example.ui.screens.TERMS_SUMMARY
import com.example.ui.screens.privacySummaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 네이버 책 검색 API 종료(2026-07-31) 뒤의 공급자 정책을 고정한다.
 * 소스 grep 방식은 GeminiKeyPolicyTest와 같다.
 */
class BookSearchProviderPolicyTest {

  private fun mainFiles() = File("src/main").walkTopDown()
    .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()

  @Test
  fun noNaverCallPathInProductionSources() {
    // 옛 호스트는 나눠 쓴다: 저장소 전체 grep이 이 줄에 걸리지 않게.
    val forbidden = listOf("openapi.naver" + ".com", "X-Naver-Client", "parseNaverBooks", "NAVER_CLIENT")
    val offenders = mainFiles().flatMap { file ->
      val text = file.readText()
      forbidden.filter { text.contains(it) }.map { "${file.path}: $it" }
    }
    assertTrue("naver path found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
  }

  @Test
  fun naverKeysAreNotBakedIntoBuildConfig() {
    val names = BuildConfig::class.java.fields.map { it.name }
    assertEquals(emptyList<String>(), names.filter { it.contains("NAVER") })
  }

  @Test
  fun kakaoKeyHasBuildSourcesOfCredentialChain() {
    val names = BuildConfig::class.java.fields.map { it.name }
    assertTrue("env source", "ENV_KAKAO_REST_API_KEY" in names)
    assertTrue(".env source", "KAKAO_REST_API_KEY" in names)
  }

  @Test
  fun kakaoKeyIsSentOnlyByBookSearchClient() {
    val users = mainFiles().filter { it.extension == "kt" && it.readText().contains("KakaoAK") }.map { it.name }
    assertEquals(listOf("BookSearchClient.kt"), users)
  }

  @Test
  fun noKeyEntryScreenForBookSearch() {
    // ADR-005 §4: 스토어 공개 앱의 사용자에게는 키가 없다. 도서 검색 키 입력 화면을 만들지 않는다.
    val settings = File("src/main/java/com/example/ui/screens/SettingsScreen.kt")
    assertNotNull(settings.takeIf { it.isFile })
    val text = settings.readText()
    assertTrue(!text.contains("saveKakaoRestKey") && !text.contains("kakao_key_input"))
  }

  @Test
  fun privacyPolicyNamesCurrentProviders() {
    val policy = File("../docs/privacy/privacy-policy.html").readText()
    assertTrue(policy.contains("dapi.kakao.com"))
    assertTrue(policy.contains("www.googleapis.com"))
    assertTrue(!policy.contains("네이버"))
  }

  @Test
  fun publicCopyStatesIsbnLookupToGoogle() {
    // 카카오가 성공해도 고른 책의 ISBN은 Google로 간다. 처리방침 9항: 새 경로는 먼저 공개 문구에 반영한다.
    assertTrue(File("../docs/privacy/privacy-policy.html").readText().contains("그 책의 ISBN"))
    assertTrue(privacySummaryText(url = "").contains("그 책의 ISBN"))
    assertTrue(TERMS_SUMMARY.contains("그 책의 ISBN"))
  }

  @Test
  fun publicCopyStatesEveryConditionForSendingQueryToGoogle() {
    // 카카오 오류 때도 검색어가 Google로 간다. 처리방침과 앱 요약이 이 조건을 빠뜨리면 사실과 어긋난다.
    val condition = "카카오에서 찾지 못하거나 오류가 나면"
    assertTrue(File("../docs/privacy/privacy-policy.html").readText().contains(condition))
    assertTrue(privacySummaryText(url = "").contains(condition))
  }
}
