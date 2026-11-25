package com.example

import com.example.data.ocr.BitmapDecoding
import com.example.data.ocr.OcrBlock
import com.example.data.ocr.OcrTextAssembler
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextAssemblerTest {

  @Test
  fun blocksAreOrderedTopThenLeft_linesJoinedBySpace_blocksByBlankLine() {
    val blocks = listOf(
      OcrBlock(lines = listOf("두 번째 문단의", "첫 줄"), top = 400, left = 10),
      OcrBlock(lines = listOf("첫 번째 문단은", "여기서 시작해서", "이렇게 끝난다."), top = 100, left = 10),
      OcrBlock(lines = listOf("같은 높이 오른쪽 칼럼"), top = 100, left = 600)
    )

    val result = OcrTextAssembler.assemble(blocks)

    assertEquals(
      "첫 번째 문단은 여기서 시작해서 이렇게 끝난다.\n\n같은 높이 오른쪽 칼럼\n\n두 번째 문단의 첫 줄",
      result
    )
  }

  /** 칩(어절 상자)의 블록 순서 = 편집창 글의 블록 순서. 아래 블록을 먼저 준 엔진 결과에서도 같아야 한다. */
  @Test
  fun boxesUseTheSameBlockOrderAsTheText() {
    val blocks = listOf(
      OcrBlock(lines = listOf("아래"), top = 400, left = 10),
      OcrBlock(lines = listOf("오른쪽"), top = 100, left = 600),
      OcrBlock(lines = listOf("왼쪽"), top = 100, left = 10)
    )
    val ordered = OcrTextAssembler.inReadingOrder(blocks, { it.top }, { it.left }).map { it.lines.single() }
    assertEquals(listOf("왼쪽", "오른쪽", "아래"), ordered)
    assertEquals(ordered.joinToString("\n\n"), OcrTextAssembler.assemble(blocks))
    // 실제 추출기가 어절 상자에도 이 순서를 쓰는지(ML Kit은 JVM에서 돌지 않아 소스로 고정한다).
    val extractor = java.io.File("src/main/java/com/example/data/ocr/MlKitTextExtractor.kt").readText()
    assertEquals(1, Regex("val boxed = OcrTextAssembler\\.inReadingOrder\\(").findAll(extractor).count())
  }

  @Test
  fun blankLinesAndEmptyBlocksAreDropped_noSpacingCorrection() {
    val blocks = listOf(
      OcrBlock(lines = listOf("  ", ""), top = 0, left = 0),
      OcrBlock(lines = listOf(" 띄어쓰기는  그대로 ", "\t"), top = 50, left = 0)
    )

    assertEquals("띄어쓰기는  그대로", OcrTextAssembler.assemble(blocks))
    assertEquals("", OcrTextAssembler.assemble(emptyList()))
  }

  @Test
  fun inSampleSize_keepsLongestSideWithinLimit() {
    assertEquals(1, BitmapDecoding.inSampleSizeFor(1024, 768))
    assertEquals(1, BitmapDecoding.inSampleSizeFor(2048, 1536))
    assertEquals(2, BitmapDecoding.inSampleSizeFor(4000, 3000))
    assertEquals(4, BitmapDecoding.inSampleSizeFor(3000, 8000))
    assertEquals(1, BitmapDecoding.inSampleSizeFor(0, 0))
  }
}
