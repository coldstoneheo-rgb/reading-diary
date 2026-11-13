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

  /** 보정 전 띠·문턱(0.08H 여유 · 0.3Δ) — 띠 경계 규칙 자체를 고정하는 테스트용. 기본값은 K1a(아래 bandEdges 테스트). */
  private val legacy = UnderlineDetector.Params(darkRatio = 0.3, bandTopRatio = null, nextLinePadRatio = null)

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
    // 줄 간격을 좁혀(줄 2 위 = 110) «다음 줄 위 − 0.08H»가 «줄 아래 + 1.0H»(=120)보다 먼저 띠를 막게 한다.
    // 띠 = 80+3 ~ min(110−3=107, 120). 107~108의 가로선은 띠 밖 → 줄 1의 밑줄이 아니다.
    val img = canvas()
    val tightLine2Words = line2Words.map { Box(it.left, 110, it.right, 150) }
    (line1Words + tightLine2Words).forEach { glyphWord(img, it) }
    fill(img, Box(50, 107, 390, 109), 40)
    val tightLine2 = Box(50, 110, 390, 150)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, tightLine2), legacy).none { it.lineIndex == 0 })
    // 대조: 다음 줄을 주지 않으면 띠가 줄 아래 + 1.0H(=120)까지 열려 무언가(이 선 · 줄 2 윗획)가 잡힌다 — 위 결과가 nextTop 분기 덕임을 보인다.
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1), legacy).any { it.lineIndex == 0 })
  }

  @Test
  fun bandEdges_canBeMovedOnlyWhenAsked() {
    // 줄 상자 아래 끝 바로 밑(81~83행) 밑줄: 기본 띠(83행부터)에는 안 들고, 띠 시작을 줄 아래 끝(+0H)으로 당기면 든다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 81, 390, 84), 60)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), legacy).none { it.lineIndex == 0 })
    val top0 = legacy.copy(bandTopRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), top0).count { it.lineIndex == 0 })
    // 기본값(K1a: −0.02H — H=40이면 0px)도 잡는다.
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2)).count { it.lineIndex == 0 })

    // 다음 줄 바로 위(107~108행) 밑줄: 기본 띠 끝(110−3)에는 안 들고, 띠 끝을 다음 줄 위 끝(−0H)까지 늘리면 든다.
    val img2 = canvas()
    val tightLine2Words = line2Words.map { Box(it.left, 110, it.right, 150) }
    (line1Words + tightLine2Words).forEach { glyphWord(img2, it) }
    fill(img2, Box(50, 107, 390, 109), 40)
    val tightLine2 = Box(50, 110, 390, 150)
    assertTrue(UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), legacy).none { it.lineIndex == 0 })
    val pad0 = legacy.copy(nextLinePadRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), pad0).count { it.lineIndex == 0 })
    // 기본값(K1a: 다음 줄 위 +0.025H — H=40이면 +1px)도 잡는다.
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2)).count { it.lineIndex == 0 })
  }

  @Test
  fun tiltedUnderline_isFollowedOnlyWithSlope() {
    // 기운 밑줄: x=50에서 95행 → x=390에서 129행(기울기 0.1). 수평 띠(84~119행)는 x≥300에서 선을 놓친다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    for (x in 50 until 390) { val y = 95 + (x - 50) / 10; for (t in 0 until 3) img[(y + t) * w + x] = 60 }
    val flat = UnderlineDetector.detect(img, w, h, listOf(line1, line2), legacy).filter { it.lineIndex == 0 }
    assertTrue("flat=$flat", flat.none { it.right >= 380 })
    val tilted = UnderlineDetector.detect(img, w, h, listOf(line1, line2), legacy, doubleArrayOf(0.1, 0.0))
      .filter { it.lineIndex == 0 }
    assertTrue("tilted=$tilted", tilted.any { it.left <= 60 && it.right >= 380 })
  }

  @Test
  fun slopeMovingTheBandOutsideThePhoto_readsPaperNotOtherRows() {
    // 사진 위 가장자리에 붙은 줄(위 0, 아래 10) + 큰 음의 기울기: 띠 오른쪽 끝이 사진 위로 나간다(y < 1).
    // 나간 행은 종이(255)로 보아 예외 없이 끝나고 «밑줄»을 만들지 않는다(가드가 없으면 음수 인덱스로 예외).
    val img = canvas()
    val top = Box(50, 0, 390, 10)
    fill(img, Box(50, 0, 70, 10), 30) // 글자 잉크(Δ_text 확보용) — 줄 왼쪽 끝에만, 띠가 기울어도 닿지 않게
    val runs = UnderlineDetector.detect(img, w, h, listOf(top), slopes = doubleArrayOf(-0.14))
    assertTrue("runs=$runs", runs.isEmpty())

    // 대칭: 사진 아래 가장자리 가까운 줄 + 큰 양의 기울기 → 띠 오른쪽 끝이 사진 아래로 나간다(y > 높이 − 2).
    val img2 = canvas()
    val bottom = Box(50, h - 30, 390, h - 20)
    fill(img2, Box(50, h - 30, 70, h - 20), 30)
    val runs2 = UnderlineDetector.detect(img2, w, h, listOf(bottom), slopes = doubleArrayOf(0.14))
    assertTrue("runs2=$runs2", runs2.isEmpty())
  }

  @Test
  fun lineSlope_fitsWordBottoms() {
    val words = listOf(Box(50, 40, 150, 80), Box(150, 45, 250, 85), Box(250, 50, 350, 90))
    assertEquals(0.05, UnderlineDetector.lineSlope(words), 1e-9)
    assertEquals(0.0, UnderlineDetector.lineSlope(words.take(2)), 0.0) // 어절 3개 미만
    val steep = listOf(Box(0, 0, 10, 10), Box(10, 20, 20, 30), Box(20, 40, 30, 50)) // 기울기 2 — 줄 묶기 실수로 본다
    assertEquals(0.0, UnderlineDetector.lineSlope(steep), 0.0)
  }

  @Test
  fun bandEdges_canBeMovedOnlyWhenAsked() {
    // 줄 상자 아래 끝 바로 밑(81~83행) 밑줄: 기본 띠(83행부터)에는 안 들고, 띠 시작을 줄 아래 끝(+0H)으로 당기면 든다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 81, 390, 84), 60)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2)).none { it.lineIndex == 0 })
    val top0 = UnderlineDetector.Params(bandTopRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), top0).count { it.lineIndex == 0 })

    // 다음 줄 바로 위(107~108행) 밑줄: 기본 띠 끝(110−3)에는 안 들고, 띠 끝을 다음 줄 위 끝(−0H)까지 늘리면 든다.
    val img2 = canvas()
    val tightLine2Words = line2Words.map { Box(it.left, 110, it.right, 150) }
    (line1Words + tightLine2Words).forEach { glyphWord(img2, it) }
    fill(img2, Box(50, 107, 390, 109), 40)
    val tightLine2 = Box(50, 110, 390, 150)
    assertTrue(UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2)).none { it.lineIndex == 0 })
    val pad0 = UnderlineDetector.Params(nextLinePadRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), pad0).count { it.lineIndex == 0 })
  }

  @Test
  fun tiltedUnderline_isFollowedOnlyWithSlope() {
    // 기운 밑줄: x=50에서 95행 → x=390에서 129행(기울기 0.1). 수평 띠(84~119행)는 x≥300에서 선을 놓친다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    for (x in 50 until 390) { val y = 95 + (x - 50) / 10; for (t in 0 until 3) img[(y + t) * w + x] = 60 }
    val flat = UnderlineDetector.detect(img, w, h, listOf(line1, line2)).filter { it.lineIndex == 0 }
    assertTrue("flat=$flat", flat.none { it.right >= 380 })
    val tilted = UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = doubleArrayOf(0.1, 0.0))
      .filter { it.lineIndex == 0 }
    assertTrue("tilted=$tilted", tilted.any { it.left <= 60 && it.right >= 380 })
  }

  @Test
  fun slopeMovingTheBandOutsideThePhoto_readsPaperNotOtherRows() {
    // 사진 위 가장자리에 붙은 줄(위 0, 아래 10) + 큰 음의 기울기: 띠 오른쪽 끝이 사진 위로 나간다(y < 1).
    // 나간 행은 종이(255)로 보아 예외 없이 끝나고, 사진 안 다른 행(맨 아래 행 등)을 감아 읽어 «밑줄»을 만들지 않는다.
    val img = canvas()
    val top = Box(50, 0, 390, 10)
    fill(img, Box(50, 0, 70, 10), 30) // 글자 잉크(Δ_text 확보용) — 줄 왼쪽 끝에만, 띠가 기울어도 닿지 않게
    fill(img, Box(0, h - 3, w, h), 30) // 사진 맨 아래 3행을 검게 — 인덱스가 감기면 여기를 읽는다
    val runs = UnderlineDetector.detect(img, w, h, listOf(top), slopes = doubleArrayOf(-0.14))
    assertTrue("runs=$runs", runs.isEmpty())
  }

  @Test
  fun lineSlope_fitsWordBottoms() {
    val words = listOf(Box(50, 40, 150, 80), Box(150, 45, 250, 85), Box(250, 50, 350, 90))
    assertEquals(0.05, UnderlineDetector.lineSlope(words), 1e-9)
    assertEquals(0.0, UnderlineDetector.lineSlope(words.take(2)), 0.0) // 어절 3개 미만
    val steep = listOf(Box(0, 0, 10, 10), Box(10, 20, 20, 30), Box(20, 40, 30, 50)) // 기울기 2 — 줄 묶기 실수로 본다
    assertEquals(0.0, UnderlineDetector.lineSlope(steep), 0.0)
  }

  @Test
  fun lineBesideTheBand_doesNotCutIt() {
    // 띠 가로 범위(30..410) 밖 오른쪽 여백의 짧은 줄(여백 메모 등)은 «다음 줄»이 아니다 → 띠가 잘리지 않고 밑줄을 잡는다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    val margin = Box(450, 86, 560, 96)
    glyphWord(img, margin)
    fill(img, Box(170, 98, 390, 101), 60)
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2, margin))
    assertEquals(1, runs.count { it.lineIndex == 0 })
  }

  @Test
  fun lineWithoutTextContrast_isSkipped() {
    // 글자가 없는 빈 상자(Δ_text ≈ 0) 아래의 옅은 얼룩(종이보다 10 어두움)을 밑줄로 잡지 않는다(minDeltaText).
    val img = canvas()
    fill(img, Box(50, 92, 390, 95), paper - 10)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1)).isEmpty())
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

  /**
   * 붉은 인쇄는 «덮인 비율»이 문턱(40%) 아래라서만 통과한다(획 25%). 굵은 붉은 큰 글씨가 상자의 40% 이상을 덮으면
   * 형광펜으로 잘못 본다 — 실사진(형광펜 0장)에서 아직 재지 못한 한계.
   */
  @Test
  fun highlighter_isBrightAndSaturated_redPrintPassesOnlyByLowCoverage() {
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
      UnderlineDetector.highlightedWords(argb, w, h, line1Words)
    )
  }

  @Test
  fun highlighter_boxOutsideTheImage_countsOnlyTheInsidePart() {
    val argb = IntArray(w * h) { 0xFFF0F0F0.toInt() }
    val yellow = 0xFFFFEE55.toInt()
    for (y in 300 until h) for (x in 560 until w) argb[y * w + x] = yellow
    val words = listOf(
      Box(560, 300, 700, 400), // 오른쪽·아래로 삐져나감 — 안쪽(560..600 × 300..320)은 전부 노랑
      Box(-50, -20, 20, 10),   // 왼쪽·위로 삐져나감 — 안쪽은 종이
      Box(700, 400, 800, 500)  // 완전히 밖 — 셀 픽셀 0
    )
    assertArrayEquals(booleanArrayOf(true, false, false), UnderlineDetector.highlightedWords(argb, w, h, words))
  }
}
