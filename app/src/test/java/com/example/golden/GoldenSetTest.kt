package com.example.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 기준 자료 형식·일치율·측정 흐름을 합성 자료로 고정한다(사용자 사진 0). */
class GoldenSetTest {

  @get:Rule val tmp = TemporaryFolder()

  private val sample = """
    # 합성 예시
    photo: s1.png
    rotate: 0
    whole_page: no

    [page]
    하나 둘 셋 넷
    다섯 여섯 일곱 여덟

    [marks]
    1	underline	셋 넷
    2	underline	다섯
    2	highlight	일곱

    [memo]
    bottom	생각 메모{?}

    [decoys]
    bleed	2
  """.trimIndent()

  @Test
  fun parse_readsSectionsAndMarks() {
    val g = GoldenSet.parse("s1", sample)
    assertEquals(2, g.page.size)
    assertEquals("셋 넷 다섯", g.marksText("underline"))
    assertEquals("일곱", g.marksText("highlight"))
    assertEquals(setOf(1 to 2, 1 to 3, 2 to 0), GoldenSet.markedTokens(g, "underline"))
    assertEquals(listOf(GoldenSet.Decoy("bleed", 2)), g.decoys)
  }

  @Test
  fun parse_backslashEscapesPageLinesThatLookLikeCommentsOrSections() {
    // raw 문자열 — 백슬래시가 그대로 파일 글자다.
    val g = GoldenSet.parse("e", listOf("[page]", """\# 하나 둘""", """\[셋] 넷""", "다섯", "[marks]", "2\tunderline\t넷").joinToString("\n"))
    assertEquals(listOf("# 하나 둘", "[셋] 넷", "다섯"), g.page)
    assertEquals(setOf(2 to 1), GoldenSet.markedTokens(g, "underline"))
  }

  @Test(expected = IllegalStateException::class)
  fun parse_rejectsMarkNotOnItsLine() {
    GoldenSet.markedTokens(GoldenSet.parse("x", "[page]\n가 나\n[marks]\n1\tunderline\t다"))
  }

  @Test
  fun score_wordPrecisionRecallAndCharErrorRate() {
    // 정답 4어절, 엔진 3어절 중 2개 맞음(문장부호는 어절 끝에서 무시). 글자: «가나다라마바사아» vs «가나다라마바차».
    val s = GoldenSet.score("가나, 다라. 마바차", "가나 다라 마바 사아")
    assertEquals(2, s.tp)
    assertEquals(2.0 / 3, s.precision!!, 1e-9)
    assertEquals(0.5, s.recall!!, 1e-9)
    // 편집 거리: «가나,다라.마바차» → «가나다라마바사아» = 쉼표·마침표 삭제 2 + 차→사 1 + 아 삽입 1 = 4, 정답 8자
    assertEquals(4.0 / 8, s.cer!!, 1e-9)
  }

  @Test
  fun score_unsureCharMatchesAnything() {
    val s = GoldenSet.score("생각 메모들", "생각 메모{?}")
    assertEquals(2, s.tp)
    assertEquals(0.0, s.cer!!, 1e-9)
  }

  @Test
  fun emptyFolder_isZeroItemsNotAFailure() {
    assertTrue(GoldenMeasure.run(tmp.newFolder("empty")).table.contains("자료 0장"))
  }

  /** 사진 + Tesseract 꼴 상자 + 엔진 출력이 있는 합성 폴더 한 벌을 끝까지 잰다. */
  @Test
  fun fullFolder_scoresEngineOutputAndSpike() {
    val dir = tmp.newFolder("golden")
    File(dir, "s1.golden.txt").writeText(sample)
    // 600×300 흰 종이, 줄 1(y 40~80) 어절 4개, 줄 2(y 140~180) 어절 4개. 어절 폭은 글자 수에 비례(2글자 = 100px, 공백 37px).
    val w = 600; val h = 300
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, 0xC8C8C8)
    fun ink(l: Int, t: Int, r: Int, b: Int, v: Int) { for (y in t until b) for (x in l until r) img.setRGB(x, y, (v shl 16) or (v shl 8) or v) }
    val tsv = StringBuilder("level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n")
    val line1 = listOf("하나", "둘", "셋", "넷")
    val line2 = listOf("다섯", "여섯", "일곱", "여덟")
    fun layout(words: List<String>, top: Int, ln: Int) {
      var x = 20
      for ((i, word) in words.withIndex()) {
        val ww = word.length * 50
        // 글자 획: 가로 3줄 + 세로 1줄
        for (yy in listOf(top, top + 18, top + 36)) ink(x, yy, x + ww, yy + 4, 30)
        ink(x + ww / 2, top, x + ww / 2 + 4, top + 40, 30)
        tsv.append("5\t1\t1\t1\t$ln\t${i + 1}\t$x\t$top\t$ww\t40\t90\t$word\n")
        x += ww + 37
      }
    }
    layout(line1, 40, 1)
    layout(line2, 140, 2)
    // 밑줄: 줄 1 «셋 넷»(x 244~381) 아래, 줄 2 «다섯»(x 20~120) 아래
    ink(240, 92, 385, 95, 60)
    ink(18, 192, 124, 195, 60)
    ImageIO.write(img, "png", File(dir, "s1.png"))
    File(dir, "boxes/synthetic").mkdirs()
    File(dir, "boxes/synthetic/s1.tsv").writeText(tsv.toString())
    File(dir, "outputs/engineA").mkdirs()
    File(dir, "outputs/engineA/s1.underline.txt").writeText("셋 넷 다섯 여섯")

    val r = GoldenMeasure.run(dir, File(dir, "spike"))
    val report = r.table
    assertTrue(report, report.contains("| s1 | engineA | underline | 75.0% | 100.0% |"))
    assertTrue(report, report.contains("| s1 | synthetic | 100.0% | 100.0% | 100.0% | 3/0/0(0) | 2/2 | bleed 0 | 0 |"))
    // 본문 어절이 든 상세는 표와 갈라져 details에만 있다.
    assertTrue(r.details, r.details.contains("### s1 · synthetic"))
    assertTrue(report, !report.contains("미검출 어절") && !report.contains("###"))
    // 표는 PR에 옮길 수 있으므로 로컬 폴더 경로를 싣지 않는다.
    assertTrue(report, !report.contains(dir.path) && !report.contains(dir.name))
    assertTrue(File(dir, "spike/s1.synthetic.overlay.png").isFile)
  }
}
