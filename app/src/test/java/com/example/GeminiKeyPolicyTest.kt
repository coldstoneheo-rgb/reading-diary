package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
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
  fun withoutRegisteredKey_fallsBackToSimulationWithoutNetwork() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    val result = GeminiApiClient.extractUnderlinedText(context, bitmap, "데미안")

    assertTrue(result.contains("아브락사스"))
    assertTrue(result.contains("시뮬레이션"))
    assertFalse(result.contains("GEMINI_API_KEY"))
  }
}
