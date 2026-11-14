package com.example

import com.example.data.ocr.OcrLine
import com.example.data.ocr.OcrWord
import com.example.data.ocr.UnderlineDetector
import com.example.data.ocr.UnderlineDetector.Box
import com.example.data.ocr.UnderlinePreselect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 앱의 밑줄 «미리 선택» 입구(SPIKE §4-2). 합성 이미지(순수 JVM)로 고정한다:
 * 기운 줄에서 어절 상자로 맞춘 기울기가 검출기에 실제로 넘어가는지, 기울기를 끄는 길이 앱 코드에 없는지.
 */
class UnderlinePreselectTest {

  companion object {
    const val W = 600
    const val H = 320
    const val PAPER = 200

    // 줄 1: 오른쪽으로 내려가는 기운 줄(어절 아래 끝 80 → 90 → 100, 기울기 약 0.087). 줄 2: 수평, 멀리 아래.
    val line1Words = listOf(Box(70, 40, 150, 80), Box(170, 50, 270, 90), Box(290, 60, 390, 100))
    val line2Words = listOf(Box(70, 240, 150, 280), Box(170, 240, 270, 280), Box(290, 240, 390, 280))

    /** 줄 1의 둘째·셋째 어절 아래에 줄 기울기를 따라 그은 연필 밑줄(회색 60, 3px). */
    fun page(): IntArray {
      val img = IntArray(W * H) { PAPER }
      fun fill(b: Box) { for (y in b.top until b.bottom) for (x in b.left until b.right) img[y * W + x] = 30 }
      // 글자 대신 «획 모양» 블록: 가로획 3개 + 세로획 1개(UnderlineDetectorTest와 같은 모양).
      for (b in line1Words + line2Words) {
        for (yy in listOf(b.top, b.top + b.height / 2, b.bottom - 4)) fill(Box(b.left, yy, b.right, yy + 4))
        fill(Box(b.left + b.width / 2, b.top, b.left + b.width / 2 + 5, b.bottom))
      }
      val slope = UnderlineDetector.lineSlope(line1Words)
      for (x in 165 until 395) {
        val y = Math.round(90 + (x - 220) * slope + 6).toInt()
        for (t in 0 until 3) img[(y + t) * W + x] = 60
      }
      return img
    }
  }

  @Test
  fun tiltedLine_selectsOnlyTheUnderlinedWords_withTheFittedSlope() {
    val words = line2Words + line1Words // 엔진 순서는 위→아래가 아닐 수 있다 — 결과는 입력 순번을 따른다
    val r = UnderlinePreselect.select(page(), W, H, words)

    assertEquals(listOf(false, false, false, false, true, true), r.selected.toList())
    assertEquals(2, r.lines.size)
    // 줄마다 그 줄 어절 상자로 맞춘 기울기가 검출기에 넘어간다(기운 줄 ≠ 0).
    for ((i, line) in r.lines.withIndex()) {
      assertEquals(UnderlineDetector.lineSlope(line.words.map { words[it] }), r.slopes[i], 0.0)
    }
    val tilted = r.lines.indexOfFirst { it.words.containsAll(listOf(3, 4, 5)) }
    assertTrue("slope=${r.slopes[tilted]}", r.slopes[tilted] > 0.08)
  }

  @Test
  fun forLines_keepsEngineLineAndWordOrder() {
    val lines = listOf(
      OcrLine("a b c", null, line1Words.mapIndexed { i, b -> OcrWord("w1$i", b) }),
      OcrLine("d e f", null, line2Words.mapIndexed { i, b -> OcrWord("w2$i", b) })
    )
    val out = UnderlinePreselect.forLines(page(), W, H, lines)
    assertEquals(listOf("w10", "w11", "w12", "w20", "w21", "w22"), out.map { it.text })
    assertEquals(listOf(0, 0, 0, 1, 1, 1), out.map { it.lineIndex })
    assertEquals(listOf(false, true, true, false, false, false), out.map { it.selected })
  }

  @Test
  fun noWords_givesNothing() {
    assertTrue(UnderlinePreselect.forLines(page(), W, H, emptyList()).isEmpty())
    assertTrue(UnderlinePreselect.forLines(page(), W, H, listOf(OcrLine("x", null, emptyList()))).isEmpty())
  }

  @Test
  fun outlierHeightWords_areLeftOutAndNeverSelected() {
    // 여러 줄을 합친 큰 상자(높이 > 2.4 × 중앙값)는 줄 묶기에서 빠지고 미리 선택도 false.
    val words = line1Words + line2Words + Box(60, 30, 400, 290)
    assertEquals((0..5).toList(), UnderlinePreselect.keptWords(words))
    val r = UnderlinePreselect.select(page(), W, H, words)
    assertFalse(r.selected[6])
    assertEquals(2, r.lines.size)
  }

  @Test
  fun appPath_cannotCallTheDetectorWithoutSlope() {
    // ① 앱의 입구(select)에는 기울기를 끄거나 바꾸는 인자가 없다(회색조 · 크기 · 어절 상자 · 문턱만).
    val selects = UnderlinePreselect::class.java.methods.filter { it.name == "select" }
    assertTrue(selects.isNotEmpty())
    for (m in selects) {
      val types = m.parameterTypes.toList()
      assertTrue("select($types)", types.none { it == DoubleArray::class.java || it == java.lang.Boolean.TYPE || it == java.lang.Double.TYPE })
    }
    // ② 앱 코드(src/main)에서 검출기 detect를 직접 부르는 파일은 UnderlinePreselect.kt 하나뿐이고, 거기서는 slopes를 넘긴다.
    // 테스트 작업 디렉터리 = app 모듈 루트(GeminiKeyPolicyTest와 같은 관례).
    val mainSrc = File("src/main/java")
    assertTrue("expected app/src/main/java to exist", mainSrc.isDirectory)
    val callers = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }
      .filter { f -> Regex("""\bdetect\s*\(""").containsMatchIn(f.readText()) && !f.name.equals("UnderlineDetector.kt") }
      .map { it.name }.toList()
    assertEquals(listOf("UnderlinePreselect.kt"), callers)
    val src = File(mainSrc, "com/example/data/ocr/UnderlinePreselect.kt").readText()
    val calls = Regex("""UnderlineDetector\.detect\(([^)]*)\)""").findAll(src).map { it.groupValues[1] }.toList()
    assertEquals(1, calls.size)
    assertTrue("call=${calls.single()}", calls.single().trim().endsWith("slopes") && !calls.single().contains("null"))
  }
}
