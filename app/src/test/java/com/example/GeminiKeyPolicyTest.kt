package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecureKeyManager
import com.example.data.api.GeminiApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-001 불변식: Gemini API 키는 앱 바이너리에 존재하지 않으며, 키가 등록되지 않은 기기에서는
 * 네트워크 호출 없이 시뮬레이션 문장으로 폴백한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiKeyPolicyTest {

  @Test
  fun buildConfig_hasNoGeminiApiKeyField() {
    // secrets 플러그인 ignoreList가 제거되면 이 필드가 다시 생성되어 실패한다
    val field = BuildConfig::class.java.fields.find { it.name == "GEMINI_API_KEY" }
    assertNull("GEMINI_API_KEY must not be baked into BuildConfig (ADR-001)", field)
  }

  @Test
  fun withoutRegisteredKey_returnsSimulatedText() = runBlocking {
    // 키가 없으면 isKeyValid=false로 OkHttp 블록을 건너뛴다. 네트워크 미발생 자체를 직접 검증하지는 않는다.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    val result = GeminiApiClient.extractUnderlinedText(context, bitmap, "데미안")

    assertTrue(result.contains("아브락사스"))
    assertTrue(result.contains("시뮬레이션"))
    assertFalse(result.contains("GEMINI_API_KEY"))
  }

  @Test
  fun saveGeminiApiKey_rejectsNonHeaderSafeCharacters() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // 제로폭 공백·전각 문자는 헤더에 실을 수 없고, 실으려 하면 예외 메시지에 키가 통째로 남는다
    assertFalse(SecureKeyManager.saveGeminiApiKey(context, "AIza​xyz"))
    assertFalse(SecureKeyManager.saveGeminiApiKey(context, "ＡＩｚａ"))
    assertFalse(SecureKeyManager.saveGeminiApiKey(context, "   "))
    assertFalse(SecureKeyManager.isHeaderSafe("abc​"))
    assertTrue(SecureKeyManager.isHeaderSafe("AIzaSy-abc_123"))
  }
}
