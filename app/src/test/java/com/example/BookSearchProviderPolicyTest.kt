package com.example

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
    val forbidden = listOf("openapi.naver.com", "X-Naver-Client", "parseNaverBooks", "NAVER_CLIENT")
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
}
