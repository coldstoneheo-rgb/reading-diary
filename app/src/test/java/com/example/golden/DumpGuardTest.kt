package com.example.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 기기 덤프 거르기(ⓐ NOT EXACT ⓑ 방향 ⓒ 짝)를 합성 자료로 고정한다(사용자 사진 0). */
class DumpGuardTest {

  @get:Rule val tmp = TemporaryFolder()

  private val w = 600
  private val h = 400

  private fun golden(id: String, l1: String, l2: String, rotate: Int = 0) = """
    photo: $id.png
    rotate: $rotate
    [page]
    $l1
    $l2
    [marks]
    1	underline	${l1.split(" ").last()}
  """.trimIndent()

  /** 위쪽 절반에만 글줄 2개(y 40~80 · 140~180) — 180° 돌리면 상자 자리가 빈 종이가 된다. */
  private fun page(): BufferedImage {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, 0xC8C8C8)
    fun ink(l: Int, t: Int, r: Int, b: Int) { for (y in t until b) for (x in l until r) img.setRGB(x, y, 0x1E1E1E) }
    for (top in listOf(40, 140)) {
      var x = 20
      repeat(4) {
        for (yy in listOf(top, top + 18, top + 36)) ink(x, yy, x + 100, yy + 4)
        ink(x + 50, top, x + 54, top + 40)
        x += 137
      }
    }
    return img
  }

  /** #55 덤프 꼴: 머리 `#` 줄 + 열 이름 + 어절(level 5) 행. */
  private fun dump(words1: List<String>, words2: List<String>, rotate: Int = 0, notExact: Boolean = false): String {
    val sb = StringBuilder("# source: mlkit\n")
    sb.append("# coords: upright source pixels ${w}x$h${if (notExact) " (NOT EXACT: flip)" else ""}\n")
    sb.append("# expects golden rotate: $rotate\n")
    sb.append("level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n")
    for ((ln, pair) in listOf(40 to words1, 140 to words2).withIndex()) {
      var x = 20
      for ((i, word) in pair.second.withIndex()) {
        sb.append("5\t1\t1\t1\t${ln + 1}\t${i + 1}\t$x\t${pair.first}\t100\t40\t-1\t$word\n")
        x += 137
      }
    }
    return sb.toString()
  }

  private val a1 = listOf("하나", "둘", "셋", "넷")
  private val a2 = listOf("다섯", "여섯", "일곱", "여덟")
  private val b1 = listOf("가을", "바람", "나무", "그늘")
  private val b2 = listOf("저녁", "노을", "강물", "소리")

  @Test
  fun notExact_isReadFromTheHeader() {
    assertTrue(DumpGuard.notExact(dump(a1, a2, notExact = true)))
    assertFalse(DumpGuard.notExact(dump(a1, a2)))
    assertTrue(DumpGuard.isDeviceDump(dump(a1, a2)))
    assertFalse(DumpGuard.isDeviceDump("level\tpage_num\n5\t1"))
  }

  @Test
  fun pairing_followsTheText_notTheFileName_andLaterDumpWins() {
    val ga = GoldenSet.parse("ga", golden("ga", a1.joinToString(" "), a2.joinToString(" ")))
    val gb = GoldenSet.parse("gb", golden("gb", b1.joinToString(" "), b2.joinToString(" ")))
    val dir = tmp.newFolder("dumps")
    // 사람이 고른 순서와 반대로 저장된 덤프 + 같은 사진을 두 번 인식한 덤프 + 아무 짝도 없는 덤프.
    File(dir, "ocr-dump-001.tsv").writeText(dump(b1, b2))
    File(dir, "ocr-dump-002.tsv").writeText(dump(a1, a2))
    File(dir, "ocr-dump-003.tsv").writeText(dump(a1, a2))
    File(dir, "ocr-dump-004.tsv").writeText(dump(listOf("전혀", "다른", "글", "줄"), listOf("상관", "없는", "덤프", "파일")))
    val p = DumpGuard.pair(listOf(ga, gb), dir.listFiles()!!.sortedBy { it.name })
    assertEquals("ocr-dump-003.tsv", p["ga"]!!.file.name)
    assertEquals(2, p["ga"]!!.candidates)
    assertEquals("ocr-dump-001.tsv", p["gb"]!!.file.name)
    assertEquals(2, p.size)
  }

  @Test
  fun alignment_isHigherWhenBoxesSitOnTheTextLines() {
    val img = page()
    val gray = IntArray(w * h) { img.getRGB(it % w, it / w) and 0xFF }
    val lines = GoldenSet.clusterLines(GoldenSet.readWords(dump(a1, a2)))
    val own = DumpGuard.alignment(gray, w, h, lines)!!
    val flipped = IntArray(w * h) { gray[w * h - 1 - it] } // 180°
    val alt = DumpGuard.alignment(flipped, w, h, lines)!!
    assertTrue("own=$own alt=$alt", own > 20 && own > alt + 20)
    assertNull(DumpGuard.alignment(gray, w, h, lines.take(1)))
  }

  /** 측정 흐름 끝까지: 이름 안 바꾼 덤프가 자동으로 짝지어지고, 틀린 입력은 «⚠제외» 행으로만 나온다. */
  @Test
  fun measure_autoPairsDumps_andExcludesSilentlyWrongOnes() {
    val dir = tmp.newFolder("golden")
    File(dir, "ga.golden.txt").writeText(golden("ga", a1.joinToString(" "), a2.joinToString(" ")))
    File(dir, "gb.golden.txt").writeText(golden("gb", b1.joinToString(" "), b2.joinToString(" ")))
    ImageIO.write(page(), "png", File(dir, "ga.png"))
    ImageIO.write(page(), "png", File(dir, "gb.png"))
    File(dir, "boxes/mlkit-ok").mkdirs()
    File(dir, "boxes/mlkit-ok/ocr-dump-001.tsv").writeText(dump(a1, a2))
    File(dir, "boxes/mlkit-bad").mkdirs()
    File(dir, "boxes/mlkit-bad/ocr-dump-001.tsv").writeText(dump(a1, a2, notExact = true))
    File(dir, "boxes/mlkit-bad/ocr-dump-002.tsv").writeText(dump(b1, b2, rotate = 180))

    val table = GoldenMeasure.run(dir).table
    assertTrue(table, table.contains("| ga | mlkit-ok (짝 자동 ocr-dump-001.tsv 글 일치 100.0%) |"))
    assertTrue(table, table.contains("| ga | ⚠제외 mlkit-bad (짝 자동 ocr-dump-001.tsv 글 일치 100.0%) — NOT EXACT"))
    assertTrue(table, Regex("\\| gb \\| ⚠제외 mlkit-bad .*방향 의심").containsMatchIn(table))
  }
}
