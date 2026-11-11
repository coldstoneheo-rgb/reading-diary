package com.example.debugtools

import com.example.debugtools.OcrBoxDumpTsv.Block
import com.example.debugtools.OcrBoxDumpTsv.Element
import com.example.debugtools.OcrBoxDumpTsv.Input
import com.example.debugtools.OcrBoxDumpTsv.Line
import com.example.debugtools.OcrBoxDumpTsv.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 상자 덤프 TSV(디버그 전용)의 열 · 좌표 되돌리기를 고정한다. 합성 글만 쓴다. */
class OcrBoxDumpTsvTest {

  private val oneWord = listOf(
    Block(Rect(10, 20, 110, 60), listOf(Line("하나 둘", Rect(10, 20, 110, 60), listOf(
      Element("하나", Rect(10, 20, 50, 60)),
      Element("둘", Rect(60, 20, 110, 60)),
      Element("상자없음", null)
    ))))
  )

  private fun rows(tsv: String) = tsv.lines().filter { it.isNotBlank() && !it.startsWith("#") }

  @Test
  fun fullFrame_scalesBitmapCoordsBackToSourcePixels() {
    // 원본 4000×3000을 다운샘플(÷2)한 2000×1500 비트맵, 자르기 없음 → 배율 2.
    val tsv = OcrBoxDumpTsv.format(oneWord, Input(4000, 3000, 0f, false, 0f, 0f, 1f, 1f, 2000, 1500), "t", "mlkit")
    val r = rows(tsv)
    assertEquals(OcrBoxDumpTsv.COLUMNS, r[0])
    assertEquals("2\t1\t1\t1\t0\t0\t20\t40\t200\t80\t-1\t", r[1])
    assertEquals("4\t1\t1\t1\t1\t0\t20\t40\t200\t80\t-1\t", r[2])
    assertEquals("5\t1\t1\t1\t1\t1\t20\t40\t80\t80\t-1\t하나", r[3])
    assertEquals("5\t1\t1\t1\t1\t2\t120\t40\t100\t80\t-1\t둘", r[4])
    assertEquals("상자 없는 어절은 건너뛴다", 5, r.size)
    assertTrue(tsv.contains("# coords: upright source pixels 4000x3000\n"))
  }

  @Test
  fun crop_addsTheOffsetBeforeScaling() {
    // 다운샘플 2000×1500에서 왼쪽 위 25%·20%를 잘라낸 비트맵(1500×1200) → 오프셋 500,300 · 배율 2.
    val m = OcrBoxDumpTsv.mapping(Input(4000, 3000, 0f, false, 0.25f, 0.2f, 1f, 1f, 1500, 1200))
    assertEquals(500, m.offsetX)
    assertEquals(300, m.offsetY)
    assertEquals(Rect(1020, 640, 1220, 720), m.map(Rect(10, 20, 110, 60)))
    assertTrue(m.exact)
  }

  @Test
  fun userQuarterTurn_swapsTheUprightSize() {
    // 원본 4000×3000을 앱에서 90° 돌림 → 세운 크기 3000×4000, 비트맵 1500×2000 → 배율 2.
    val m = OcrBoxDumpTsv.mapping(Input(4000, 3000, 90f, false, 0f, 0f, 1f, 1f, 1500, 2000))
    assertEquals(3000, m.uprightWidth)
    assertEquals(4000, m.uprightHeight)
    assertEquals(2.0, m.scaleX, 1e-9)
    assertTrue(m.exact)
  }

  @Test
  fun flipOrOddAngle_isMarkedNotExact() {
    val flipped = OcrBoxDumpTsv.format(oneWord, Input(4000, 3000, 0f, true, 0f, 0f, 1f, 1f, 2000, 1500), "t", "mlkit")
    assertTrue(flipped.contains("NOT EXACT"))
    assertFalse(OcrBoxDumpTsv.mapping(Input(4000, 3000, 45f, false, 0f, 0f, 1f, 1f, 2000, 1500)).exact)
  }

  @Test
  fun tabsAndNewlinesInText_doNotBreakColumns() {
    val blocks = listOf(Block(null, listOf(Line("x", null, listOf(Element("가\t나\n다", Rect(0, 0, 10, 10)))))))
    val r = rows(OcrBoxDumpTsv.format(blocks, Input(10, 10, 0f, false, 0f, 0f, 1f, 1f, 10, 10), "t", "mlkit"))
    assertEquals(12, r[1].split('\t').size)
    assertEquals("가 나 다", r[1].split('\t')[11])
  }
}
