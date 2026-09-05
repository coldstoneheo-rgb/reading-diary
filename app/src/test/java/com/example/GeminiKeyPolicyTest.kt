package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecureKeyManager
import com.example.data.api.GeminiApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-001 불변식: Gemini API 키는 앱 바이너리에 존재하지 않으며, 키가 등록되지 않은 기기에서는
 * 네트워크 호출 없이 즉시 예외로 끝난다(가짜 문장 폴백 없음, ADR-002).
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
  fun withoutRegisteredKey_throwsBeforeAnyNetworkCall_noFakeText() {
    // 키가 없으면 isKeyValid=false로 OkHttp 블록을 건너뛰고 즉시 예외. 가짜 문장(ADR-002 Q2)은 더 이상 없다.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    val error = assertThrows(IllegalStateException::class.java) {
      runBlocking { GeminiApiClient.extractUnderlinedText(context, bitmap, "데미안") }
    }

    assertTrue(error.message!!.contains("등록되지 않았"))
    assertFalse(error.message!!.contains("아브락사스"))
  }

  @Test
  fun geminiClient_isNotReferencedByAnyProductionPath() {
    // ADR-002 Q3: 사진이 기기 밖으로 나가는 유일한 코드(GeminiApiClient)는 명시 동의 UI 없이는 어디서도 호출되면 안 된다.
    // 테스트 작업 디렉터리는 app 모듈 루트다(GreetingScreenshotTest의 상대 경로 관례와 동일).
    val mainSrc = java.io.File("src/main/java")
    assertTrue("expected app/src/main/java to exist", mainSrc.isDirectory)
    val offenders = mainSrc.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { !it.path.replace('\\', '/').contains("/data/api/GeminiApiClient.kt") }
      .filter { it.readText().contains("GeminiApiClient") }
      .map { it.path }
      .toList()
    assertTrue("GeminiApiClient referenced from: $offenders", offenders.isEmpty())
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
