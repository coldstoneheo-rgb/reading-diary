package com.example

import com.example.data.ocr.UnderlineDetector
import com.example.data.ocr.UnderlineDetector.Box
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 합성 이미지로 밑줄·형광펜 검출 스파이크의 규칙을 고정한다(순수 JVM). */
class UnderlineDetectorTest {

  private val w = 600
  private val h = 320
  private val paper = 200

  private fun canvas() = IntArray(w * h) { paper }

  private fun fill(img: IntArray, b: Box, v: Int) {
    for (y in b.top until b.bottom) for (x in b.left until b.right) img[y * w + x] = v
  }

  /** 글자 대신 «획 모양» 블록: 가로획 3개 + 세로획 1개(글자처럼 위아래로 두껍다). */
  private fun glyphWord(img: IntArray, b: Box) {
    for (yy in listOf(b.top, b.top + b.height / 2, b.bottom - 4)) fill(img, Box(b.left, yy, b.right, yy + 4), 30)
    fill(img, Box(b.left + b.width / 2, b.top, b.left + b.width / 2 + 5, b.bottom), 30)
  }

  // 줄 높이 40, 줄 간격 100. 줄 1: 어절 3개, 줄 2: 어절 3개.
  private val line1Words = listOf(Box(50, 40, 150, 80), Box(170, 40, 270, 80), Box(290, 40, 390, 80))
  private val line2Words = listOf(Box(50, 140, 150, 180), Box(170, 140, 270, 180), Box(290, 140, 390, 180))
  private val line1 = Box(50, 40, 390, 80)
  private val line2 = Box(50, 140, 390, 180)

  @Test
  fun pencilUnderline_marksOnlyTheWordsAboveIt() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    // 연필 밑줄(회색 60, 3px): 줄 1의 둘째·셋째 어절 아래. 약간 기울어 1px씩 내려간다.
    for (x in 165 until 395) { val y = 92 + (x - 165) / 120; for (t in 0 until 3) img[(y + t) * w + x] = 60 }

    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2))
    assertEquals(1, runs.size)
    assertEquals(0, runs[0].lineIndex)

    val words = line1Words + line2Words
    val lineOf = listOf(0, 0, 0, 1, 1, 1)
    assertArrayEquals(
      booleanArrayOf(false, true, true, false, false, false),
      UnderlineDetector.underlinedWords(words, lineOf, runs)
    )
  }

  @Test
  fun bleedThrough_isTooFaintToCount() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    // 뒷장 비침: 종이보다 25 어두운 선(글자 획 Δ=170의 0.15배) — 문턱 0.3 아래.
    for (x in 50 until 390) for (t in 0 until 4) img[(95 + t) * w + x] = paper - 25
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2)).isEmpty())
  }

  @Test
  fun thickInkInBand_isNotAnUnderline() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    // 띠 안의 두꺼운 잉크(상자를 못 받은 글자·자필 글씨 덩어리, 높이 20 = 0.5×H) — 가는 획이 아니다.
    fill(img, Box(170, 90, 300, 110), 30)
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2))
    assertTrue("runs=$runs", runs.isEmpty())
  }

  @Test
  fun band_stopsAboveTheNextLine() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    // 줄 2 바로 위(띠 끝 137 아래, 138~140)의 가로선은 줄 1의 밑줄이 아니다.
    fill(img, Box(50, 138, 390, 140), 40)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2)).none { it.lineIndex == 0 })
  }

  @Test
  fun shortMarks_belowMinimumLength_areIgnored() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(200, 95, 230, 98), 40) // 글자 0.75개 폭의 짧은 획(쉼표·점 등)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2)).isEmpty())
  }

  @Test
  fun wavyUnderlineWithSmallGaps_isOneRun() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    for (x in 50 until 390) {
      if (x % 37 in 0..5) continue // 연필이 종이를 덜 탄 빈틈(6px < 0.3×H=12)
      val y = 92 + if ((x / 20) % 2 == 0) 0 else 3
      for (t in 0 until 3) img[(y + t) * w + x] = 70
    }
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2))
    assertEquals(1, runs.size)
    assertTrue(runs[0].left <= 52 && runs[0].right >= 388)
  }

  @Test
  fun highlighter_isBrightAndSaturated_redPrintIsNot() {
    val argb = IntArray(w * h) { 0xFFF0F0F0.toInt() }
    fun paint(b: Box, c: Int) { for (y in b.top until b.bottom) for (x in b.left until b.right) argb[y * w + x] = c }
    val yellow = 0xFFFFEE55.toInt()
    val red = 0xFFB42828.toInt()
    val black = 0xFF1E1E1E.toInt()
    // 어절 1: 노란 형광펜 위 검은 글자(획 20%)
    paint(line1Words[0], yellow); paint(Box(60, 50, 140, 58), black)
    // 어절 2: 흰 종이 위 붉은 인쇄 글자(획 25%) — 장 머리 장식 글씨 같은 경우
    paint(Box(170, 40, 270, 50), red)
    // 어절 3: 흰 종이 위 검은 글자
    paint(Box(290, 40, 390, 50), black)
    assertArrayEquals(
      booleanArrayOf(true, false, false),
      UnderlineDetector.highlightedWords(argb, w, line1Words)
    )
  }
}
