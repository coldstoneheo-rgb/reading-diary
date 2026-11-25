package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.screens.PrivacyPolicyBody
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 앱 안 처리방침은 누를 수 있는 링크다. 주소가 비어 있으면 링크를 그리지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyPolicyLinkTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val opened = mutableListOf<String>()

  private fun show(url: String, open: (String) -> Unit = { opened += it }) = composeTestRule.setContent {
    CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = open(uri) }) {
      MyApplicationTheme { PrivacyPolicyBody(url) }
    }
  }

  @Test
  fun link_opensThePolicyUrl() {
    show("https://example.org/privacy-policy.html")
    composeTestRule.onNodeWithTag("privacy_policy_link").performClick()
    assertEquals(listOf("https://example.org/privacy-policy.html"), opened)
  }

  @Test
  fun deviceWithoutBrowser_doesNotCrash() {
    show("https://example.org/privacy-policy.html") { throw IllegalArgumentException("no browser") }
    composeTestRule.onNodeWithTag("privacy_policy_link").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun blankUrl_drawsNoLink() {
    show("")
    assertEquals(0, composeTestRule.onAllNodesWithTag("privacy_policy_link").fetchSemanticsNodes().size)
  }
}
