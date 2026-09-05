package com.example

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roborazzi 기준 이미지가 실제 PNG인지 확인한다.
 * 과거 greeting.png가 텍스트 도구로 써져(시그니처 0x89가 UTF-8 `c2 89`로, CRLF가 LF로) 커밋된 탓에
 * verifyRoborazziDebug가 "read(...) must not be null"로 항상 실패했다. UI를 건드리지 않는 PR에서도 이 테스트가 재발을 잡는다.
 */
class ScreenshotBaselineIntegrityTest {

  private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

  @Test
  fun everyBaselineScreenshotIsARealPng() {
    val dir = File("src/test/screenshots")
    assertTrue("expected app/src/test/screenshots to exist", dir.isDirectory)
    val pngs = dir.listFiles { f -> f.isFile && f.extension == "png" }.orEmpty()
    assertTrue("no baseline screenshots found in ${dir.path}", pngs.isNotEmpty())
    for (png in pngs) {
      val head = png.inputStream().use { it.readNBytes(8) }
      assertArrayEquals("${png.name} is not a valid PNG (was it written by a text tool?)", pngSignature, head)
    }
  }
}
