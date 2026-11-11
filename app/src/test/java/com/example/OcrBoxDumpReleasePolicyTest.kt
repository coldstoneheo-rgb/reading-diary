package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 상자 덤프는 디버그 빌드 전용이다. 릴리스 변형이 컴파일하는 src/release 스텁에 파일 쓰기 · 공유 · 네트워크 · 화면 요소가
 * 들어오지 않게, 그리고 덤프가 매니페스트(권한 · provider)를 늘리지 않게 소스를 읽어 고정한다.
 */
class OcrBoxDumpReleasePolicyTest {

  private val stub = File("src/release/java/com/example/debugtools/OcrBoxDump.kt")

  @Test
  fun releaseStub_hasNoIoShareNetworkOrUi() {
    assertTrue(stub.path, stub.isFile)
    val code = stub.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") || it.trimStart().startsWith("//") }
      .joinToString("\n")
    for (banned in listOf("java.io", "File(", "Intent", "FileProvider", "okhttp", "URL(", "Card", "Text(", "Button", "writeText", "Log.")) {
      assertFalse("릴리스 스텁에 «$banned»", code.contains(banned))
    }
    // 함수 본문은 모두 비어 있다.
    assertEquals(4, Regex("\\)\\s*\\{\\s*\\}").findAll(code).count())
  }

  @Test
  fun noVariantManifestOrNewProvider() {
    // 덤프 공유는 main의 기존 FileProvider(cache-path)만 쓴다 — 변형별 매니페스트를 두지 않는다.
    assertFalse(File("src/debug/AndroidManifest.xml").exists())
    assertFalse(File("src/release/AndroidManifest.xml").exists())
    val main = File("src/main/AndroidManifest.xml").readText()
    assertEquals(1, Regex("<provider").findAll(main).count())
  }

  @Test
  fun dumpCode_livesOnlyInTheDebugSourceSet() {
    val mainHits = File("src/main/java").walkTopDown().filter { it.isFile && it.readText().contains("ocr-dump") }.toList()
    assertTrue("main에 덤프 경로 문자열: $mainHits", mainHits.isEmpty())
    assertTrue(File("src/debug/java/com/example/debugtools/OcrBoxDump.kt").isFile)
  }
}
