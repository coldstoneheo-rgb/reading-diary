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
    // ADR-002 Q3: 사진이 기기 밖으로 나가는 유일한 코드(GeminiApiClient)는 GeminiTextExtractor 어댑터를 통해서만,
    // 그 어댑터는 ReadingViewModel.processPreciseAnalysis(키 등록 + 사진 전송 동의 + 사진마다 확인)에서만 쓰인다.
    // 테스트 작업 디렉터리는 app 모듈 루트다(GreetingScreenshotTest의 상대 경로 관례와 동일).
    val mainSrc = java.io.File("src/main/java")
    assertTrue("expected app/src/main/java to exist", mainSrc.isDirectory)
    val ktFiles = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    fun offenders(symbol: String, allowed: List<String>) = ktFiles
      .filter { f -> allowed.none { f.path.replace('\\', '/').endsWith(it) } }
      .filter { it.readText().contains(symbol) }
      .map { it.path }
    listOf(
      "GeminiApiClient" to listOf("/data/api/GeminiApiClient.kt", "/data/ocr/GeminiTextExtractor.kt"),
      // GeminiApiClient.kt는 KDoc에서 어댑터를 언급할 뿐 호출하지 않는다
      "GeminiTextExtractor" to listOf("/data/ocr/GeminiTextExtractor.kt", "/ui/viewmodel/ReadingViewModel.kt", "/data/api/GeminiApiClient.kt")
    ).forEach { (symbol, allowed) ->
      val bad = offenders(symbol, allowed)
      assertTrue("$symbol referenced from: $bad", bad.isEmpty())
    }
  }

  /** 컴파일된 XML 리소스를 파싱해 (섹션, 태그, domain, path) 목록으로. 주석·속성 순서에 영향받지 않는다. */
  private fun backupRuleEntries(resId: Int): List<List<String>> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val parser = context.resources.getXml(resId)
    val entries = mutableListOf<List<String>>()
    var section = "root"
    var event = parser.eventType
    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
      if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
        when (parser.name) {
          "cloud-backup", "device-transfer", "full-backup-content" -> section = parser.name
          "include", "exclude" -> entries.add(
            listOf(section, parser.name, parser.getAttributeValue(null, "domain") ?: "", parser.getAttributeValue(null, "path") ?: "")
          )
        }
      }
      event = parser.next()
    }
    return entries
  }

  @Test
  fun backupRules_excludeSecureKeyPrefs() {
    // ADR-001: 키 저장소(및 SharedPreferences가 남길 수 있는 .bak 사본)는 클라우드 백업·기기 이전에서 제외.
    // 리소스를 파싱하므로 주석 처리된 규칙은 통과하지 못하고, 파일명은 SecureKeyManager와 같은 상수에서 온다.
    val prefsXml = "${SecureKeyManager.PREFS_FILE}.xml"
    val required = listOf(prefsXml, "$prefsXml.bak")

    val legacy = backupRuleEntries(R.xml.backup_rules)
    val modern = backupRuleEntries(R.xml.data_extraction_rules)
    for (path in required) {
      assertTrue("backup_rules must exclude $path: $legacy", legacy.contains(listOf("full-backup-content", "exclude", "sharedpref", path)))
      assertTrue("cloud-backup must exclude $path: $modern", modern.contains(listOf("cloud-backup", "exclude", "sharedpref", path)))
      assertTrue("device-transfer must exclude $path: $modern", modern.contains(listOf("device-transfer", "exclude", "sharedpref", path)))
    }

    // 매니페스트 배선이 빠지면 규칙 파일이 있어도 전부 백업된다 (ApplicationInfo의 해당 필드는 @hide라 원문을 본다)
    val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
    assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
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
