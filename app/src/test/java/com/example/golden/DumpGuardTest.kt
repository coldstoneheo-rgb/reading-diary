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

/** 기기 덤프 거르기(ⓐ NOT EXACT ⓑ 방향 ⓒ 짝)를 합성 자료로 고정한다(사용자 사진 0 · 합성 낱말뿐). */
class DumpGuardTest {

  @get:Rule val tmp = TemporaryFolder()

  private val w = 600
  private val h = 400

  private val a1 = listOf("하늘바다구름", "강물나무숲길", "산들바람소리", "새벽이슬방울")
  private val a2 = listOf("저녁노을빛깔", "달빛그림자들", "별빛반짝임과", "겨울눈송이들")
  private val b1 = listOf("기차역대합실", "시계탑종소리", "우체국창구앞", "신문가판대옆")
  private val b2 = listOf("버스정류장의", "택시승강장과", "지하철출구로", "횡단보도건너")
  private val c1 = listOf("컴퓨터자판기", "마우스모니터", "프린터스캐너", "키보드책상위")
  private val c2 = listOf("전화기충전기", "냉장고세탁기", "청소기선풍기", "전자렌지오븐")

  private fun golden(id: String, l1: List<String>, l2: List<String>, rotate: Int = 0) = listOf(
    "photo: $id.png", "rotate: $rotate", "[page]", l1.joinToString(" "), l2.joinToString(" "),
    "[marks]", "1\tunderline\t${l1.last()}"
  ).joinToString("\n")

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
  private fun dump(words1: List<String>, words2: List<String>?, rotate: Int = 0, notExact: Boolean = false, created: String = "2026-11-12T05:00:00"): String {
    val sb = StringBuilder("# source: mlkit\n# created: $created\n")
    sb.append("# coords: upright source pixels ${w}x$h${if (notExact) " (NOT EXACT: flip)" else ""}\n")
    sb.append("# expects golden rotate: $rotate\n")
    sb.append("level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n")
    for ((ln, pair) in listOfNotNull(40 to words1, words2?.let { 140 to it }).withIndex()) {
      var x = 20
      for ((i, word) in pair.second.withIndex()) {
        sb.append("5\t1\t1\t1\t${ln + 1}\t${i + 1}\t$x\t${pair.first}\t100\t40\t-1\t$word\n")
        x += 137
      }
    }
    return sb.toString()
  }

  private val ga get() = GoldenSet.parse("ga", golden("ga", a1, a2))
  private val gb get() = GoldenSet.parse("gb", golden("gb", b1, b2))

  @Test
  fun notExact_andDeviceDump_areReadFromTheHeader() {
    assertTrue(DumpGuard.notExact(dump(a1, a2, notExact = true)))
    assertFalse(DumpGuard.notExact(dump(a1, a2)))
    assertTrue(DumpGuard.isDeviceDump(dump(a1, a2)))
    assertFalse(DumpGuard.isDeviceDump("level\tpage_num\n5\t1"))
  }

  @Test
  fun pairing_followsTheText_highestMatchThenLaterCreated_andReportsTheRest() {
    val dir = tmp.newFolder("dumps")
    File(dir, "ocr-dump-001.tsv").writeText(dump(b1, b2, created = "2026-11-12T05:00:01"))
    File(dir, "ocr-dump-002.tsv").writeText(dump(a1, a2, created = "2026-11-12T05:00:02"))
    // 이름순으로는 앞이지만 나중에 만든(같은 사진을 다시 인식한) 덤프가 이긴다 — 일련번호가 지우기 뒤 001로 돌아가도.
    File(dir, "ocr-dump-000.tsv").writeText(dump(a1, a2, created = "2026-11-12T05:00:09"))
    File(dir, "ocr-dump-004.tsv").writeText(dump(c1, c2))
    val r = DumpGuard.pair(listOf(ga, gb), dir.listFiles()!!.sortedBy { it.name })
    assertEquals("ocr-dump-000.tsv", r.paired["ga"]!!.file.name)
    assertEquals(listOf("ocr-dump-002.tsv"), r.paired["ga"]!!.dropped)
    assertEquals("ocr-dump-001.tsv", r.paired["gb"]!!.file.name)
    assertEquals(listOf("ocr-dump-004.tsv"), r.unpaired.map { it.first.name })
  }

  @Test
  fun pairing_marksAmbiguousAndTooShort_andAcceptsACroppedDump() {
    val twin = GoldenSet.parse("gt", golden("gt", a1, a2)) // 같은 쪽을 두 번 기준 자료로 넣은 경우
    val dir = tmp.newFolder("dumps2")
    File(dir, "ocr-dump-001.tsv").writeText(dump(a1, a2))
    File(dir, "ocr-dump-002.tsv").writeText(dump(listOf("짧은글"), null))
    val r = DumpGuard.pair(listOf(ga, twin), dir.listFiles()!!.sortedBy { it.name })
    assertTrue(r.paired.isEmpty())
    assertTrue(r.unpaired.toString(), r.unpaired.any { it.first.name == "ocr-dump-001.tsv" && it.second.startsWith("짝 모호") })
    assertTrue(r.unpaired.toString(), r.unpaired.any { it.first.name == "ocr-dump-002.tsv" && it.second.contains("판정 불가") })
    // 미리보기에서 첫 줄만 잘라 인식한 덤프도 포함률은 높다.
    val crop = File(tmp.newFolder("dumps3"), "ocr-dump-001.tsv").apply { writeText(dump(a1, null)) }
    assertEquals("ocr-dump-001.tsv", DumpGuard.pair(listOf(ga, gb), listOf(crop)).paired["ga"]!!.file.name)
  }

  @Test
  fun alignment_isHigherWhenBoxesSitOnTheTextLines_andDecideNeedsAClearMargin() {
    val img = page()
    val gray = IntArray(w * h) { img.getRGB(it % w, it / w) and 0xFF }
    val lines = GoldenSet.clusterLines(GoldenSet.readWords(dump(a1, a2)))
    val own = DumpGuard.alignment(gray, w, h, lines)!!
    val flipped = IntArray(w * h) { gray[w * h - 1 - it] } // 180°
    val alt = DumpGuard.alignment(flipped, w, h, lines)!!
    assertEquals("own=$own alt=$alt", DumpGuard.Verdict.FIRST, DumpGuard.decide(own, alt))
    assertNull(DumpGuard.alignment(gray, w, h, lines.take(1)))
    assertEquals(DumpGuard.Verdict.SECOND, DumpGuard.decide(2.0, 30.0))
    assertEquals(DumpGuard.Verdict.UNDECIDED, DumpGuard.decide(30.0, 27.0)) // 줄 간격이 고른 쪽: 비슷하면 판정 안 함
    assertEquals(DumpGuard.Verdict.UNDECIDED, DumpGuard.decide(null, 5.0))
  }

  /** 측정 흐름 끝까지: 이름 안 바꾼 덤프가 자동으로 짝지어지고, 틀린 입력은 «⚠» 행으로만 나온다. */
  @Test
  fun measure_autoPairsDumps_andShowsEveryWrongInputAsAWarningRow() {
    val dir = tmp.newFolder("golden")
    File(dir, "ga.golden.txt").writeText(golden("ga", a1, a2))
    File(dir, "gb.golden.txt").writeText(golden("gb", b1, b2))
    ImageIO.write(page(), "png", File(dir, "ga.png"))
    ImageIO.write(page(), "png", File(dir, "gb.png"))
    File(dir, "boxes/mlkit-ok").mkdirs()
    File(dir, "boxes/mlkit-ok/ocr-dump-001.tsv").writeText(dump(a1, a2))
    File(dir, "boxes/mlkit-bad").mkdirs()
    File(dir, "boxes/mlkit-bad/ocr-dump-001.tsv").writeText(dump(a1, a2, notExact = true))
    File(dir, "boxes/mlkit-bad/ocr-dump-002.tsv").writeText(dump(b1, b2, rotate = 180)) // 정답 rotate 0과 다름
    File(dir, "boxes/mlkit-bad/ocr-dump-003.tsv").writeText(dump(c1, c2))
    // 머리 없는 Tesseract 꼴 파일은 이름이 <id>.tsv가 아니면 자동 짝에 들어가지 않는다.
    File(dir, "boxes/tesseract-x").mkdirs()
    File(dir, "boxes/tesseract-x/ga-old.tsv").writeText(dump(a1, a2).lines().filterNot { it.startsWith("#") }.joinToString("\n"))

    val table = GoldenMeasure.run(dir).table
    assertTrue(table, table.contains("| ga | mlkit-ok (짝 자동 ocr-dump-001.tsv 글 일치 100.0%) |"))
    assertTrue(table, table.contains("| ga | ⚠제외 mlkit-bad (짝 자동 ocr-dump-001.tsv 글 일치 100.0%) — NOT EXACT"))
    assertTrue(table, Regex("\\| gb \\| ⚠제외 mlkit-bad .*방향 의심\\(정답 rotate 0").containsMatchIn(table))
    assertTrue(table, table.contains("| — | ⚠짝 없음 mlkit-bad: ocr-dump-003.tsv — 최고 일치"))
    assertFalse(table, table.contains("tesseract-x"))
  }
}
