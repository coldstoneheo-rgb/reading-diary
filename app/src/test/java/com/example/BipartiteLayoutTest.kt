package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.BipartiteGraph
import com.example.data.knowledge.BipartiteLayout
import com.example.data.knowledge.GraphEdge
import com.example.data.knowledge.GraphHit
import com.example.data.knowledge.GraphSide
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.SharedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** STAGE2-PLAN ① 이분 그래프 좌표 순수 함수. 입력은 KeywordLinks.build 결과(이미 정렬됨)라고 가정한다. */
class BipartiteLayoutTest {

  private val w = 400f
  private val h = 300f
  private val pad = 24f

  private fun word(text: String, vararg bookIds: Int, tagged: Boolean = false) =
    SharedWord(word = text, userTagged = tagged, bookIds = bookIds.toSet(), quotes = emptyList())

  private val titles = mapOf(1 to "데미안", 2 to "사피엔스", 3 to "가시고기")

  @Test
  fun emptyInput_givesEmptyGraph_notTruncated() {
    val g = BipartiteLayout.layout(emptyList(), titles, w, h)
    assertEquals(BipartiteGraph.EMPTY, g)
    assertFalse(g.truncated)
    assertNull(BipartiteLayout.hitTest(g, 10f, 10f, 20f, 8f))
  }

  @Test
  fun columns_bookAtPadding_wordAtWidthMinusPadding() {
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h, pad)
    assertTrue(g.bookNodes.all { it.x == pad && it.side == GraphSide.BOOK })
    assertTrue(g.wordNodes.all { it.x == w - pad && it.side == GraphSide.WORD })
    assertEquals(listOf("1", "2"), g.bookNodes.map { it.key })
    assertEquals(listOf("데미안", "사피엔스"), g.bookNodes.map { it.label })
  }

  @Test
  fun eachColumn_isEvenlySpacedBetweenPaddings_andASingleNodeIsCentered() {
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2, 3), word("투쟁", 1, 2), word("인간", 2, 3)), titles, w, h, pad)
    val ys = g.wordNodes.map { it.y }
    assertEquals(listOf(pad, h / 2f, h - pad), ys)
    assertEquals(ys[1] - ys[0], ys[2] - ys[1], 0.001f)
    assertEquals(pad, g.bookNodes.first().y, 0.001f)
    assertEquals(h - pad, g.bookNodes.last().y, 0.001f)

    val one = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h, pad)
    assertEquals(h / 2f, one.wordNodes.single().y, 0.001f)
  }

  @Test
  fun words_keepInputOrder_booksSortByEdgeCountThenTitle() {
    // 책 2: 엣지 3개, 책 1·3: 2개씩 → 2가 먼저, 동률은 제목 오름차순(가시고기 < 데미안)
    val g = BipartiteLayout.layout(listOf(word("투쟁", 1, 2, tagged = true), word("세계", 2, 3), word("인간", 1, 2, 3)), titles, w, h)
    assertEquals(listOf("투쟁", "세계", "인간"), g.wordNodes.map { it.key })
    assertEquals(listOf("사피엔스", "가시고기", "데미안"), g.bookNodes.map { it.label })
    assertEquals(
      listOf(GraphEdge(1, "투쟁"), GraphEdge(2, "투쟁"), GraphEdge(2, "세계"), GraphEdge(3, "세계"), GraphEdge(1, "인간"), GraphEdge(2, "인간"), GraphEdge(3, "인간")),
      g.edges
    )
    assertFalse(g.truncated)
  }

  @Test
  fun overLimits_13words_11books_41edges_becomes_12_10_40_truncated() {
    val bookTitles = (1..11).associateWith { "B%02d".format(it) }
    val all11 = IntArray(11) { it + 1 }
    val words = buildList {
      add(word("w00", *all11))                      // 책 10권에 남는 엣지 10개(책 11은 잘림)
      for (i in 1..9) add(word("w%02d".format(i), 1, 2)) // 18개
      add(word("w10", 1, 2, 3))                     // 3개
      add(word("w11", *all11))                      // 10개 → 합계 41
      add(word("w12", 1, 11))                       // 13번째 단어: 상한 밖
    }
    val g = BipartiteLayout.layout(words, bookTitles, w, h)
    assertEquals(BipartiteLayout.MAX_WORDS, g.wordNodes.size)
    assertEquals(BipartiteLayout.MAX_BOOKS, g.bookNodes.size)
    assertEquals(BipartiteLayout.MAX_EDGES, g.edges.size)
    assertTrue(g.truncated)
    assertFalse("엣지 수 동률이면 제목순 → B11이 잘린다", g.bookNodes.any { it.key == "11" })
    assertFalse(g.wordNodes.any { it.key == "w12" })
    assertEquals("엣지는 단어 순서대로 잘린다 — w11의 마지막(책 10) 엣지가 빠진다", GraphEdge(9, "w11"), g.edges.last())
  }

  @Test
  fun withinLimits_isNotTruncated() {
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h)
    assertFalse(g.truncated)
    assertEquals(2, g.edges.size)
  }

  @Test
  fun deterministic_sameInputSameCoordinates_regardlessOfSetIterationOrder() {
    val a = listOf(word("세계", 3, 1, 2), word("인간", 2, 3))
    val b = listOf(word("세계", 2, 3, 1), word("인간", 3, 2))
    val g1 = BipartiteLayout.layout(a, titles, w, h)
    assertEquals(g1, BipartiteLayout.layout(a, titles, w, h))
    assertEquals(g1, BipartiteLayout.layout(b, titles, w, h))
  }

  @Test
  fun booksMissingFromTitles_areNotDrawn() {
    // 책 9는 지워진 책. 세계는 책 1과의 엣지 하나만 남는다.
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 9)), titles, w, h)
    assertEquals(listOf("1"), g.bookNodes.map { it.key })
    assertEquals(listOf(GraphEdge(1, "세계")), g.edges)
    // 지워진 책 때문에 빠진 것은 상한 고지 대상이 아니다
    assertFalse(g.truncated)
    val gone = BipartiteLayout.layout(listOf(word("세계", 1, 2), word("투쟁", 8, 9)), titles, w, h)
    assertEquals(listOf("세계"), gone.wordNodes.map { it.key })
    assertFalse(gone.truncated)
  }

  @Test
  fun hitTest_wordNodeWinsOverItsOwnEdgeEnds() {
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h, pad)
    val node = g.wordNodes.single()
    assertEquals(GraphHit.Word("세계"), BipartiteLayout.hitTest(g, node.x - 3f, node.y + 2f, nodeRadius = 20f, edgeTolerance = 12f))
  }

  @Test
  fun hitTest_edgeMidpoint_returnsEdge_andEmptySpace_returnsNull() {
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h, pad)
    val book1 = g.bookNodes.first { it.key == "1" }
    val world = g.wordNodes.single()
    val mx = (book1.x + world.x) / 2f
    val my = (book1.y + world.y) / 2f
    assertEquals(GraphHit.Edge(1, "세계"), BipartiteLayout.hitTest(g, mx, my + 2f, nodeRadius = 20f, edgeTolerance = 8f))
    // 위쪽 가운데(두 엣지보다 위): 노드에서도 선분에서도 멀다
    assertNull(BipartiteLayout.hitTest(g, w / 2f, 2f, nodeRadius = 20f, edgeTolerance = 8f))
  }

  @Test
  fun hitTest_bookNodeAloneIsNotAHit_butItsEdgeEndIs() {
    // 책 노드는 결과가 아니다. 책 노드 중심은 엣지의 끝점이기도 하므로 거리 0인 엣지가 잡힌다.
    val g = BipartiteLayout.layout(listOf(word("세계", 1, 2)), titles, w, h, pad)
    val book1 = g.bookNodes.first { it.key == "1" }
    assertEquals(GraphHit.Edge(1, "세계"), BipartiteLayout.hitTest(g, book1.x, book1.y, nodeRadius = 20f, edgeTolerance = 8f))
  }

  @Test
  fun inputPremise_sharedWordsAlwaysSpanTwoOrMoreBooks() {
    // KeywordLinks.build(minBooks = 2)가 같은 책 안 반복을 걸러 준다 — 그래프는 그 결과만 그린다(ADR-003 Q1).
    val books = listOf(
      Book(id = 1, title = "데미안", author = "", totalPages = 1, bookcaseId = 1, status = "READING"),
      Book(id = 2, title = "사피엔스", author = "", totalPages = 1, bookcaseId = 1, status = "READING")
    )
    val diaries = listOf(
      Diary(id = 1, bookId = 1, page = 1, selectedText = "세계 세계 투쟁 투쟁", notes = ""),
      Diary(id = 2, bookId = 1, page = 2, selectedText = "투쟁", notes = ""),
      Diary(id = 3, bookId = 2, page = 3, selectedText = "세계", notes = "")
    )
    val links = KeywordLinks.build(books, diaries)
    assertTrue(links.all { it.bookIds.size >= 2 })
    val g = BipartiteLayout.layout(links, books.associate { it.id to it.title }, w, h)
    assertEquals(listOf("세계"), g.wordNodes.map { it.key })
  }
}
