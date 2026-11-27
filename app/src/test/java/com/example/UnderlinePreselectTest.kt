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

  /** 높이 40 어절 8개가 오른쪽으로 갈수록 8px씩 올라가는 줄(양 끝 차 56px > 글자 높이) — 휜 쪽 아래쪽 줄의 꼴. */
  private fun risingLine(baseTop: Int) = List(8) { i -> Box(40 + i * 70, baseTop - i * 8, 100 + i * 70, baseTop - i * 8 + 40) }

  @Test
  fun curvedPageLine_staysOneLine_andDoesNotSplitIntoFragments() {
    // 옛 묶기(줄 전체 세로 중심 중앙값과 비교)는 이 줄을 5 + 3 조각으로 갈랐다. 조각의 위 끝이 윗줄 밑줄 띠의 아래 한계가 돼
    // 띠가 몇 px로 줄고, 밑줄 줄 어절을 통째로 놓쳤다(사용자 실제 틀 측정 2/5/4의 원인).
    val upper = risingLine(200)
    val lower = risingLine(290)
    val lines = UnderlinePreselect.clusterLines(upper + lower)
    assertEquals(2, lines.size)
    assertEquals((0..7).toList(), lines[0].words)
    assertEquals((8..15).toList(), lines[1].words)
  }

  @Test
  fun tallMarginBox_andSmallMarkBeforeTheLine_doNotPullOrSplitBodyLines() {
    // 기준 사진 g02의 꼴: 줄 머리 왼쪽 작은 표시(높이 25)와 여백의 키 큰 손글씨(높이 90)가 본문 줄을 가르거나 끌어오지 않는다.
    val body = List(5) { i -> Box(100 + i * 70, 200 - i * 3, 160 + i * 70, 240 - i * 3) }
    val mark = Box(10, 230, 40, 255)
    val margin = Box(10, 340, 40, 430) // 세로 중심이 다음 줄보다 35px 아래(글자 높이 40의 절반 넘게)
    val next = List(5) { i -> Box(100 + i * 70, 330, 160 + i * 70, 370) }
    val words = body + mark + margin + next
    val lines = UnderlinePreselect.clusterLines(words)
    val bodyLine = lines.single { 0 in it.words }
    assertTrue(bodyLine.words.containsAll((0..4).toList()))
    val nextLine = lines.single { 7 in it.words }
    assertEquals((7..11).toList(), nextLine.words)
    assertTrue(lines.single { 6 in it.words }.words == listOf(6))
  }

  @Test
  fun smallWordInsideAFlatLine_doesNotBecomeItsOwnLine() {
    // critic 반례 A: 본문 줄 사이에 키 작은 상자(구두점 · 라틴 소문자). 혼자 줄이 되면 그 위 끝이 본문 줄의 띠를 막는다.
    val body = List(4) { i -> Box(40 + i * 70, 200, 100 + i * 70, 240) }
    val small = Box(104, 224, 108, 240)
    val lines = UnderlinePreselect.clusterLines(body + small)
    assertEquals(1, lines.size)
    // 줄 머리에 먼저 오는 키 작은 상자(여는 따옴표)도 같은 줄.
    val quote = Box(20, 200, 30, 216)
    assertEquals(1, UnderlinePreselect.clusterLines(body + quote).size)
  }

  @Test
  fun smallWordInsideARisingLine_doesNotSplitIt() {
    // critic 반례 B: 8px씩 올라가는 줄 가운데 키 작은 상자 하나가 그 줄의 마지막 어절이 되어도 다음 어절이 끊기지 않는다.
    val line = risingLine(200)
    val small = Box(175, 206, 195, 228)
    val lines = UnderlinePreselect.clusterLines(line + small)
    assertEquals(1, lines.size)
  }

  @Test
  fun nearerLineByDistance_winsOverAStaleFragmentThatIsVerticallyCloser() {
    // 가로 거리 먼저: 아래로 기우는 본문 줄 어절이 멀리 왼쪽에 남은 조각(세로로 더 가까움)과 본문 줄 둘 다의 허용폭에 들 때 본문 줄로.
    val stale = Box(10, 216, 40, 256)
    val body = List(6) { k -> Box(100 + 70 * k, 180 + 8 * k, 160 + 70 * k, 220 + 8 * k) }
    val lines = UnderlinePreselect.clusterLines(listOf(stale) + body)
    assertEquals((1..6).toList(), lines.single { 1 in it.words }.words)
    assertEquals(listOf(0), lines.single { 0 in it.words }.words)
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
    // ② 앱 코드(src/main)에서 검출기 detect를 부르는(또는 함수 참조로 넘기는) 파일은 UnderlinePreselect.kt 하나뿐이고,
    // 거기서는 slopes를 넘긴다. UnderlineDetector.kt 안에서는 정의 말고 detect 호출이 없어야 한다(우회 도우미 금지).
    // 테스트 작업 디렉터리 = app 모듈 루트(GeminiKeyPolicyTest와 같은 관례).
    val mainSrc = File("src/main/java")
    assertTrue("expected app/src/main/java to exist", mainSrc.isDirectory)
    // 이름 «detect»가 코드에 나오는 파일 자체를 센다(주석 제외) — import 뒤 맨 이름 호출 · with(UnderlineDetector) · import 별칭도 걸린다.
    val ref = Regex("""\bdetect\b""")
    fun code(f: File) = f.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")
    val callers = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "UnderlineDetector.kt" }
      .filter { ref.containsMatchIn(code(it)) }.map { it.name }.toList()
    assertEquals(listOf("UnderlinePreselect.kt"), callers)
    // detect의 slopes에는 기본값이 없다(생략 = 컴파일 오류). 앱 경로의 호출은 null을 넘기지 않는다(아래 ③).
    val detectorSrc = File(mainSrc, "com/example/data/ocr/UnderlineDetector.kt").readText()
    val inner = Regex("""(?<!fun )\bdetect\s*\(|::\s*detect\b""").findAll(detectorSrc).count()
    assertEquals("UnderlineDetector.kt 안의 detect 호출", 0, inner)
    // ③ UnderlinePreselect.kt 코드 안 «detect»는 그 한 번의 호출뿐이고, 마지막 인자는 slopes(null 리터럴 0).
    val src = code(File(mainSrc, "com/example/data/ocr/UnderlinePreselect.kt"))
    assertEquals(1, ref.findAll(src).count())
    val calls = Regex("""UnderlineDetector\.detect\(([^)]*)\)""").findAll(src).map { it.groupValues[1] }.toList()
    assertEquals(1, calls.size)
    assertTrue("call=${calls.single()}", calls.single().trim().endsWith("slopes") && !calls.single().contains("null"))
  }

  @Test
  fun slopes_cannotBeMadeNullable() {
    // #64 재게이트 관찰 ②: slopes를 nullable로 바꿔 «기울기 없이» 부르는 우회를 막는다.
    // 컴파일 단언: Result.slopes가 DoubleArray?가 되면 이 줄이 컴파일되지 않는다.
    val slopes: DoubleArray = UnderlinePreselect.select(IntArray(4), 2, 2, emptyList()).slopes
    assertEquals(0, slopes.size)
    // 소스 단언: 검출기에 넘기는 지역 slopes는 DoubleArray(...)로 만들고, 파일 어디에도 nullable DoubleArray 타입이 없다.
    val src = File("src/main/java/com/example/data/ocr/UnderlinePreselect.kt").readText()
    assertTrue(Regex("""val slopes\s*=\s*DoubleArray\(""").containsMatchIn(src))
    assertFalse("nullable DoubleArray in UnderlinePreselect.kt", Regex("""DoubleArray\s*\?""").containsMatchIn(src))
    assertTrue(Regex("""val slopes:\s*DoubleArray\s*[,)]""").containsMatchIn(src))
  }
}
