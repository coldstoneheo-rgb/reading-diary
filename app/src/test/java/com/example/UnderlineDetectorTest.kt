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
  private val legacy = UnderlineDetector.Params(darkRatio = 0.3, bandTopRatio = null, nextLinePadRatio = null, smoothMinHeight = 0, seedFraction = 0.0)

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

    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null)
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
    // 뒷장 비침: 종이보다 25 어두운 선(글자 획 Δ=170의 0.15배) — 낮은 문턱(0.23) 아래.
    for (x in 50 until 390) for (t in 0 until 4) img[(95 + t) * w + x] = paper - 25
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).isEmpty())
  }

  @Test
  fun thickInkInBand_isNotAnUnderline() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    // 띠 안의 두꺼운 잉크(상자를 못 받은 글자·자필 글씨 덩어리, 높이 20 = 0.5×H) — 가는 획이 아니다.
    fill(img, Box(170, 90, 300, 110), 30)
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null)
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
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, tightLine2), legacy, slopes = null).none { it.lineIndex == 0 })
    // 대조: 다음 줄을 주지 않으면 띠가 줄 아래 + 1.0H(=120)까지 열려 무언가(이 선 · 줄 2 윗획)가 잡힌다 — 위 결과가 nextTop 분기 덕임을 보인다.
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1), legacy, slopes = null).any { it.lineIndex == 0 })
  }

  @Test
  fun bandEdges_canBeMovedOnlyWhenAsked() {
    // 줄 상자 아래 끝 바로 밑(81~83행) 밑줄: 기본 띠(83행부터)에는 안 들고, 띠 시작을 줄 아래 끝(+0H)으로 당기면 든다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 81, 390, 84), 60)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), legacy, slopes = null).none { it.lineIndex == 0 })
    val top0 = legacy.copy(bandTopRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), top0, slopes = null).count { it.lineIndex == 0 })
    // 기본값(K1a: −0.02H — H=40이면 0px)도 잡는다.
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).count { it.lineIndex == 0 })

    // 다음 줄 바로 위(107~108행) 밑줄: 기본 띠 끝(110−3)에는 안 들고, 띠 끝을 다음 줄 위 끝(−0H)까지 늘리면 든다.
    val img2 = canvas()
    val tightLine2Words = line2Words.map { Box(it.left, 110, it.right, 150) }
    (line1Words + tightLine2Words).forEach { glyphWord(img2, it) }
    fill(img2, Box(50, 107, 390, 109), 40)
    val tightLine2 = Box(50, 110, 390, 150)
    assertTrue(UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), legacy, slopes = null).none { it.lineIndex == 0 })
    val pad0 = legacy.copy(nextLinePadRatio = 0.0)
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), pad0, slopes = null).count { it.lineIndex == 0 })
    // 기본값(K1a: 다음 줄 위 +0.025H — H=40이면 +1px)도 잡는다. 주의: 이 띠는 다음 줄 윗획(110행)까지 읽어
    // 밑줄이 없어도 어절마다 짧은 구간이 생긴다(아래 nextLineTopStroke 테스트) — 여기서 1개인 것은 밑줄이 그 사이를 이어 붙였기 때문.
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, tightLine2), slopes = null).count { it.lineIndex == 0 })
  }

  @Test
  fun nextLineTopStroke_isReachedByTheAveragingWindowWhenBoxesHugTheInk() {
    // K1a 기본 띠의 알려진 한계: 상자가 잉크에 딱 붙은 촘촘한 줄이면 띠 끝(다음 줄 위 +1px)이 다음 줄 윗획을 읽어
    // 밑줄이 없어도 구간이 생긴다(세로 3픽셀 평균을 쓰는 줄에서). 기기 ML Kit 상자는 잉크보다 위아래로 여유가 있어 실측에선 기울기와 함께 정밀도 96.5%(한 단계 문턱 0.28 때).
    val img = canvas()
    val tightLine2Words = line2Words.map { Box(it.left, 110, it.right, 150) }
    (line1Words + tightLine2Words).forEach { glyphWord(img, it) }
    val tightLine2 = Box(50, 110, 390, 150)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, tightLine2), legacy, slopes = null).none { it.lineIndex == 0 })
    // 띠 [y0, y1) 안의 마지막 행(y1 − 1 = 다음 줄 윗획)은 루프가 직접 읽지 않고 그 위 행의 3픽셀 평균 창으로만 닿는다 —
    // 평균을 쓰는 줄(H ≥ smoothMinHeight)에서 남는 한계. 1픽셀 모드에서도 다음 줄 잉크가 상자 위 끝보다 1px 위에서 시작하면 읽힌다(사라진 게 아니라 1px 옮겨짐).
    val alwaysSmooth = UnderlineDetector.Params(smoothMinHeight = 0)
    assertEquals(3, UnderlineDetector.detect(img, w, h, listOf(line1, tightLine2), alwaysSmooth, slopes = null).count { it.lineIndex == 0 })
    // 기본값: 이 줄(H = 40 < 60)은 1픽셀로 읽어 마지막 행에 닿지 않는다.
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, tightLine2), slopes = null).none { it.lineIndex == 0 })
  }

  @Test
  fun evenlyFaintLine_needsDistinctColumns() {
    // 종이보다 49 어두운 선 = 글자 획 Δ(170)의 0.288배: 뚜렷한 열 문턱 0.28은 잡고 보정 전 한 단계 0.3은 놓친다(같은 K1a 띠).
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 94, 390, 97), paper - 49)
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).count { it.lineIndex == 0 })
    val k1a03 = UnderlineDetector.Params(darkRatio = 0.3, seedFraction = 0.0)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), k1a03, slopes = null).none { it.lineIndex == 0 })

    // 아래쪽 경계: 46 어두운 선 = 0.27배 — 낮은 문턱(0.23)은 넘지만 뚜렷한 열(0.28)이 0이라 기본값은 놓치고, 한 단계 0.25는 잡는다.
    val img2 = canvas()
    (line1Words + line2Words).forEach { glyphWord(img2, it) }
    fill(img2, Box(170, 94, 390, 97), paper - 46)
    assertTrue(UnderlineDetector.detect(img2, w, h, listOf(line1, line2), slopes = null).none { it.lineIndex == 0 })
    val k1a025 = UnderlineDetector.Params(darkRatio = 0.25, seedFraction = 0.0)
    assertEquals(1, UnderlineDetector.detect(img2, w, h, listOf(line1, line2), k1a025, slopes = null).count { it.lineIndex == 0 })
  }

  /** 낮은 문턱(0.25배 = 42 어두움)의 밑줄 220px 가운데 왼쪽 [strong]px만 뚜렷하다(0.35배 = 60 어두움). */
  private fun faintLineWithStrongPart(strong: Int): List<UnderlineDetector.Run> {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 94, 390, 97), paper - 42)
    if (strong > 0) fill(img, Box(170, 94, 170 + strong, 97), paper - 60)
    return UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).filter { it.lineIndex == 0 }
  }

  @Test
  fun faintUnderline_isAcceptedWholeOnlyWhenEnoughOfItIsDistinct() {
    // 두 단계 문턱: 구간 220px의 20%(44px) 이상이 뚜렷해야 받아들이고, 받아들이면 옅은 부분까지 한 구간이다.
    assertEquals(listOf(UnderlineDetector.Run(0, 170, 390)), faintLineWithStrongPart(50))
    assertTrue(faintLineWithStrongPart(40).isEmpty())
    assertTrue("고르게 옅은 선(비침 · 얼룩)은 낮은 문턱을 넘어도 밑줄이 아니다", faintLineWithStrongPart(0).isEmpty())
  }

  @Test
  fun lowerThreshold_decidesHowFarAnAcceptedLineExtends() {
    // 뚜렷한 50px(60 어두움) 뒤에 이어지는 부분이 낮은 문턱 바로 위(40 = 0.235Δ)면 한 구간, 바로 아래(38 = 0.224Δ)면 뚜렷한 곳에서 끝난다.
    fun runs(faint: Int): List<UnderlineDetector.Run> {
      val img = canvas()
      (line1Words + line2Words).forEach { glyphWord(img, it) }
      fill(img, Box(170, 94, 390, 97), paper - faint)
      fill(img, Box(170, 94, 220, 97), paper - 60)
      return UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).filter { it.lineIndex == 0 }
    }
    assertEquals(listOf(UnderlineDetector.Run(0, 170, 390)), runs(40))
    assertEquals(listOf(UnderlineDetector.Run(0, 170, 220)), runs(38))
  }

  @Test
  fun thickStrokesInsideAFaintLine_areNotDistinctColumns() {
    // 고르게 옅은 선(0.247Δ)을 가로지르는 두꺼운 세로획(빈틈 10px ≤ 0.3H마다, 합쳐 20% 넘음)은 «뚜렷한 열»로 세지 않는다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 94, 390, 97), paper - 42)
    for (x in 180 until 390 step 30) fill(img, Box(x, 88, x + 10, 112), 30)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).none { it.lineIndex == 0 })
  }

  @Test
  fun onePixelLine_onShortLines_isReadWithoutVerticalAveraging() {
    // 앱 해상도(1/2)에서 연필 선은 1픽셀 남짓. 줄 높이 40 < 60(smoothMinHeight)이면 1픽셀을 그대로 읽어 잡고,
    // 늘 3픽셀 평균(smoothMinHeight = 0)이면 (200 + 60 + 200) / 3 = 153 → 종이와 차 47 < 0.28 × 170으로 놓친다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(170, 95, 390, 96), 60)
    assertEquals(1, UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).count { it.lineIndex == 0 })
    val alwaysSmooth = UnderlineDetector.Params(smoothMinHeight = 0)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), alwaysSmooth, slopes = null).none { it.lineIndex == 0 })
  }

  @Test
  fun smoothMinHeight_boundary_59ReadsOnePixel_60Averages() {
    // 기본값(60)과 비교식(≥)을 함께 고정: 1픽셀 연필 선은 H 59 줄에서는 잡고(1픽셀 읽기), H 60 줄에서는 놓친다(3픽셀 평균).
    fun runsFor(lineH: Int): Int {
      val img = canvas()
      val words = listOf(Box(50, 40, 150, 40 + lineH), Box(170, 40, 270, 40 + lineH), Box(290, 40, 390, 40 + lineH))
      // 아래 획 없는 글자 모양(위 · 가운데 가로획 + 세로획) — 띠 첫 행의 평균이 글자 아래 획을 끌어오지 않게.
      for (b in words) {
        for (yy in listOf(b.top, b.top + b.height / 2)) fill(img, Box(b.left, yy, b.right, yy + 4), 30)
        fill(img, Box(b.left + b.width / 2, b.top, b.left + b.width / 2 + 5, b.bottom), 30)
      }
      val y = 40 + lineH + 12
      fill(img, Box(170, y, 390, y + 1), 60)
      val line = Box(50, 40, 390, 40 + lineH)
      val next = Box(50, 270, 390, 310)
      return UnderlineDetector.detect(img, w, h, listOf(line, next), slopes = null).count { it.lineIndex == 0 }
    }
    assertEquals(1, runsFor(59))
    assertEquals(0, runsFor(60))
  }

  @Test
  fun tiltedUnderline_isFollowedOnlyWithSlope() {
    // 기운 밑줄: x=50에서 95행 → x=390에서 129행(기울기 0.1). 수평 띠(84~119행)는 x≥300에서 선을 놓친다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    for (x in 50 until 390) { val y = 95 + (x - 50) / 10; for (t in 0 until 3) img[(y + t) * w + x] = 60 }
    val flat = UnderlineDetector.detect(img, w, h, listOf(line1, line2), legacy, slopes = null).filter { it.lineIndex == 0 }
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
  fun lineBesideTheBand_doesNotCutIt() {
    // 띠 가로 범위(30..410) 밖 오른쪽 여백의 짧은 줄(여백 메모 등)은 «다음 줄»이 아니다 → 띠가 잘리지 않고 밑줄을 잡는다.
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    val margin = Box(450, 86, 560, 96)
    glyphWord(img, margin)
    fill(img, Box(170, 98, 390, 101), 60)
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2, margin), slopes = null)
    assertEquals(1, runs.count { it.lineIndex == 0 })
  }

  @Test
  fun lineWithoutTextContrast_isSkipped() {
    // 글자가 없는 빈 상자(Δ_text ≈ 0) 아래의 옅은 얼룩(종이보다 10 어두움)을 밑줄로 잡지 않는다(minDeltaText).
    val img = canvas()
    fill(img, Box(50, 92, 390, 95), paper - 10)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1), slopes = null).isEmpty())
  }

  @Test
  fun shortMarks_belowMinimumLength_areIgnored() {
    val img = canvas()
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    fill(img, Box(200, 95, 230, 98), 40) // 글자 0.75개 폭의 짧은 획(쉼표·점 등)
    assertTrue(UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null).isEmpty())
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
    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null)
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

  // ---------- 열별 종이 밝기(HO-2026-1127 검출기 수리) ----------

  /**
   * 지어낸 합성 견본: 종이 밝기가 왼쪽 230 → 오른쪽 110으로 기운 사진(조명 기울기) + 띠 전체에 옅은 자국(그 자리 종이보다 18 어두움 — 수리 뒤 문턱까지 여유를 두려고 옅게 —
   * 뒷장 비침 · 종이 결) + 첫 어절에만 진짜 펜 밑줄. 띠 전체 중앙값 하나로 재면 어두운 오른쪽에서 옅은 자국이 밑줄 문턱을 넘어
   * 줄 끝 어절이 켜진다(수리 전 동작 = localPaperRadiusRatio 0). 열별 종이로 재면 첫 어절만 켜진다.
   */
  @Test
  fun tiltedLighting_doesNotLightTheLineEndWords() {
    fun shade(x: Int) = 230 - 120 * x / w
    val img = IntArray(w * h) { shade(it % w) }
    (line1Words + line2Words).forEach { glyphWord(img, it) }
    for (x in 50 until 390) for (t in 0 until 3) img[(95 + t) * w + x] = shade(x) - 18
    for (x in 50 until 150) for (t in 0 until 3) img[(88 + t) * w + x] = 60
    val words = line1Words + line2Words
    val lineOf = listOf(0, 0, 0, 1, 1, 1)

    val before = UnderlineDetector.Params(localPaperRadiusRatio = 0.0)
    val old = UnderlineDetector.underlinedWords(words, lineOf, UnderlineDetector.detect(img, w, h, listOf(line1, line2), before, slopes = null))
    assertTrue("수리 전에는 줄 끝 어절이 켜진다(이 견본이 고장을 재현하는지): ${old.toList()}", old[2])

    val runs = UnderlineDetector.detect(img, w, h, listOf(line1, line2), slopes = null)
    assertArrayEquals(
      "runs=$runs",
      booleanArrayOf(true, false, false, false, false, false),
      UnderlineDetector.underlinedWords(words, lineOf, runs)
    )
  }

  /**
   * 둘레 표시(HO-2026-1127 추기 4): 아랫줄 어절에 친 동그라미의 윗변이 윗줄 아래 띠에 들어와도 윗줄 어절은 켜지지 않는다.
   * 동그라미 아래 변(아랫줄 띠)은 켜진다 · 같은 띠의 진짜 밑줄도 켜진다. 거르기를 끄면(#91 동작) 윗줄 어절이 켜진다.
   */
  @Test
  fun circleAroundALowerWord_doesNotLightTheLineAbove() {
    val img = canvas()
    val upper = listOf(Box(50, 40, 150, 80), Box(170, 40, 270, 80), Box(290, 40, 390, 80))
    val lower = listOf(Box(50, 112, 150, 152), Box(170, 112, 270, 152), Box(290, 112, 390, 152))
    (upper + lower).forEach { glyphWord(img, it) }
    // 동그라미: 아랫줄 둘째 어절 둘레(가운데 220,132 · 가로 반지름 60 · 세로 40 · 굵기 3 · 회색 90).
    for (t in 0 until 3600) {
      val a = Math.toRadians(t / 10.0)
      val cx = (220 + 60 * Math.cos(a)).toInt()
      val cy = (132 + 40 * Math.sin(a)).toInt()
      for (dx in -1..1) for (dy in -1..1) img[(cy + dy) * w + cx + dx] = 90
    }
    // 진짜 밑줄: 윗줄 셋째 어절 아래(양 끝이 띠 안에서 끝난다).
    for (x in 288 until 392) for (t in 0 until 3) img[(88 + t) * w + x] = 60
    val lines = listOf(Box(50, 40, 390, 80), Box(50, 112, 390, 152))
    val words = upper + lower
    val lineOf = listOf(0, 0, 0, 1, 1, 1)
    fun lit(p: UnderlineDetector.Params) = UnderlineDetector.underlinedWords(words, lineOf, UnderlineDetector.detect(img, w, h, lines, p, slopes = null), p)

    assertArrayEquals(booleanArrayOf(false, false, true, false, true, false), lit(UnderlineDetector.Params()))
    assertArrayEquals(booleanArrayOf(false, true, true, false, true, false), lit(UnderlineDetector.Params(enclosureEndRatio = 0.0)))
  }
}
