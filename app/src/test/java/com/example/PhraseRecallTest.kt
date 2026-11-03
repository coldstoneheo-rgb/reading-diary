package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.MatchKind
import com.example.data.knowledge.PhraseRecall
import com.example.data.knowledge.RecallFilter
import com.example.data.knowledge.RecallPeriod
import com.example.data.knowledge.RecallPeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** 문구 회상 검색 순수 함수(PHRASE-RECALL-SPEC §3.2 P1). */
class PhraseRecallTest {

  private val seoul: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

  private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0, ms: Int = 0): Long =
    Calendar.getInstance(seoul).apply { clear(); set(y, m - 1, d, h, min, s); set(Calendar.MILLISECOND, ms) }.timeInMillis

  private val stars = Book(id = 1, title = "별 헤는 밤", author = "윤동주", totalPages = 120, bookcaseId = 1, status = "READING")
  private val demian = Book(id = 2, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING")
  private val books = listOf(stars, demian)

  private val d1 = Diary(id = 1, bookId = 1, page = 12, selectedText = "별 하나에 추억과 별 하나에 사랑과", notes = "그때 내 생각", createdAt = at(2026, 10, 21))
  private val d2 = Diary(id = 2, bookId = 1, page = 40, selectedText = "어머님, 나는 그리움이 많은 사람입니다", notes = "", createdAt = at(2026, 9, 1))
  private val d3 = Diary(id = 3, bookId = 2, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다.", notes = "데미안을 다시 읽다 — 추억 하나", createdAt = at(2026, 10, 25))
  private val d4 = Diary(id = 4, bookId = 2, page = 150, selectedText = "운명은 그리움을 닮았다", notes = "", createdAt = at(2025, 5, 3))
  private val diaries = listOf(d1, d2, d3, d4)

  private fun ids(hits: List<com.example.data.knowledge.RecallHit>) = hits.map { it.diaryId }

  // --- 기본 ---

  @Test
  fun emptyQueryAndEmptyFilter_returnsEverything_newestFirst() {
    val hits = PhraseRecall.search("", RecallFilter(), books, diaries)
    assertEquals(listOf(3, 1, 2, 4), ids(hits))
    assertTrue(hits.all { it.matchKind == null && it.highlight == null })
  }

  @Test
  fun blankQuery_isTreatedAsEmpty() {
    assertEquals(listOf(3, 1, 2, 4), ids(PhraseRecall.search("   ", RecallFilter(), books, diaries)))
  }

  @Test
  fun emptyInputs_returnEmpty() {
    assertTrue(PhraseRecall.search("별", RecallFilter(), emptyList(), diaries).isEmpty())
    assertTrue(PhraseRecall.search("별", RecallFilter(), books, emptyList()).isEmpty())
  }

  // --- 부분 일치 라벨 ---

  @Test
  fun substringInText_labelledText_withHighlight() {
    val hit = PhraseRecall.search("사랑과", RecallFilter(), books, diaries).single()
    assertEquals(1, hit.diaryId)
    assertEquals(MatchKind.TEXT, hit.matchKind)
    assertEquals("사랑과", hit.excerpt.substring(hit.highlight!!))
  }

  @Test
  fun substringInOneRecord_andStemInAnother_bothReturned_once() {
    // «추억과»: d1 구절에 그대로, d3 메모에는 정규형 «추억»으로만.
    val hits = PhraseRecall.search("추억과", RecallFilter(), books, diaries)
    assertEquals(listOf(3, 1), ids(hits))
    assertEquals(MatchKind.NOTES, hits[0].matchKind)
    assertEquals(MatchKind.TEXT, hits[1].matchKind)
  }

  @Test
  fun substringInNotesOnly_labelledNotes_noHighlight() {
    val hit = PhraseRecall.search("내 생각", RecallFilter(), books, diaries).single()
    assertEquals(MatchKind.NOTES, hit.matchKind)
    assertNull(hit.highlight)
    assertEquals("그때 내 생각", hit.note)
  }

  @Test
  fun substringInTitleOnly_labelledTitle() {
    val hits = PhraseRecall.search("헤는", RecallFilter(), books, diaries)
    assertEquals(listOf(1, 2), ids(hits))
    assertTrue(hits.all { it.matchKind == MatchKind.TITLE && it.highlight == null })
  }

  @Test
  fun substringInTitleAndText_labelledTitleAndText() {
    val hit = PhraseRecall.search("별", RecallFilter(), books, diaries).first { it.diaryId == 1 }
    assertEquals(MatchKind.TITLE_AND_TEXT, hit.matchKind)
    assertEquals("별", hit.excerpt.substring(hit.highlight!!))
  }

  @Test
  fun caseInsensitive_andTrimmed() {
    val en = listOf(Diary(id = 9, bookId = 1, page = 1, selectedText = "Stars Fall Softly", notes = "", createdAt = 0))
    val hit = PhraseRecall.search("  fall ", RecallFilter(), books, en).single()
    assertEquals("Fall", hit.excerpt.substring(hit.highlight!!))
  }

  // --- 조사 차이 ---

  @Test
  fun particleDifference_matchesSameStem_bothWays() {
    // «그리움을»은 d4에 그대로, d2에는 «그리움이»로만 있다.
    val hits = PhraseRecall.search("그리움을", RecallFilter(), books, diaries)
    assertEquals(listOf(2, 4), ids(hits))
    val d2Hit = hits.first { it.diaryId == 2 }
    assertEquals(MatchKind.TEXT, d2Hit.matchKind)
    assertNull("정규형 일치는 강조하지 않는다", d2Hit.highlight)
    assertTrue(d2Hit.excerpt.contains("그리움이"))
    val d4Hit = hits.first { it.diaryId == 4 }
    assertEquals("그리움을", d4Hit.excerpt.substring(d4Hit.highlight!!))

    assertEquals(listOf(2, 4), ids(PhraseRecall.search("그리움이", RecallFilter(), books, diaries)))
  }

  @Test
  fun particleDifference_inNotes_labelledNotes() {
    val notes = listOf(Diary(id = 7, bookId = 1, page = 3, selectedText = "본문", notes = "고향의 그리움은 끝이 없다", createdAt = 0))
    assertEquals(MatchKind.NOTES, PhraseRecall.search("그리움을", RecallFilter(), books, notes).single().matchKind)
  }

  @Test
  fun hashTagInNotes_matchesWordQueryWithParticle() {
    // 태그 본문은 extract(notes)의 토큰으로 정규화돼 맞는다(별도 tags 분기 없음).
    val tagged = listOf(Diary(id = 8, bookId = 1, page = 3, selectedText = "본문", notes = "#그리움", createdAt = 0))
    assertEquals(MatchKind.NOTES, PhraseRecall.search("그리움을", RecallFilter(), books, tagged).single().matchKind)
  }

  @Test
  fun particleQuery_matchesHashTagWhoseStemGoesOneStepFurther() {
    // LC 게이트 반례: stem(«제주도를»)=«제주도», extract(«#제주도»)=«제주» — 양쪽을 끝까지 정규화해야 만난다.
    val tagged = listOf(Diary(id = 50, bookId = 1, page = 1, selectedText = "본문", notes = "#제주도", createdAt = 0))
    val hit = PhraseRecall.search("제주도를", RecallFilter(), books, tagged).single()
    assertEquals(MatchKind.NOTES, hit.matchKind)
  }

  @Test
  fun particleQuery_matchesTextWordWhoseStemGoesOneStepFurther() {
    val text = listOf(Diary(id = 51, bookId = 1, page = 1, selectedText = "그해 여름 제주도", notes = "", createdAt = 0))
    val hit = PhraseRecall.search("제주도를", RecallFilter(), books, text).single()
    assertEquals(MatchKind.TEXT, hit.matchKind)
    assertNull(hit.highlight)
    assertTrue(hit.excerpt.contains("제주도"))
  }

  @Test
  fun settle_stopsAtTwoCharacterFloor() {
    assertEquals("학교", PhraseRecall.settle("학교를"))
  }

  @Test
  fun settle_convergesThroughStackedParticles() {
    assertEquals("학생", PhraseRecall.settle("학생들에게"))
  }

  @Test
  fun stemMatch_excerptLocatesSettledFormWhenFirstStemIsAbsent() {
    // «제주도를»의 1회 정규형 «제주도»는 본문에 없고 «제주에서»만 있다 → 정규화 값 «제주»로 위치를 잡아야 한다.
    val text = "가".repeat(100) + " 제주에서 보낸 여름"
    val hit = PhraseRecall.search("제주도를", RecallFilter(), books,
      listOf(Diary(id = 52, bookId = 1, page = 1, selectedText = text, notes = "", createdAt = 0))).single()
    assertEquals(MatchKind.TEXT, hit.matchKind)
    assertNull(hit.highlight)
    assertTrue("발췌에 일치 단어가 보여야 한다: ${hit.excerpt}", hit.excerpt.contains("제주에서"))
    assertTrue(hit.excerpt.startsWith("…"))
  }

  @Test
  fun multiWordQuery_doesNotUseStemMatching() {
    assertTrue(PhraseRecall.search("그리움을 닮은", RecallFilter(), books, diaries).isEmpty())
  }

  @Test
  fun noDuplicates_whenSubstringAndStemAndNotesAllMatch() {
    val both = listOf(Diary(id = 5, bookId = 1, page = 1, selectedText = "그리움이 그리움을 부른다", notes = "그리움", createdAt = 0))
    assertEquals(listOf(5), ids(PhraseRecall.search("그리움을", RecallFilter(), books, both)))
    assertEquals(listOf(5), ids(PhraseRecall.search("그리움", RecallFilter(), books, both)))
  }

  @Test
  fun noMatch_returnsEmpty() {
    assertTrue(PhraseRecall.search("고양이", RecallFilter(), books, diaries).isEmpty())
  }

  // --- 필터 ---

  @Test
  fun bookFilter_limitsToSelectedBooks() {
    assertEquals(listOf(3, 4), ids(PhraseRecall.search("", RecallFilter(bookIds = setOf(2)), books, diaries)))
    assertEquals(listOf(3, 1, 2, 4), ids(PhraseRecall.search("", RecallFilter(bookIds = setOf(1, 2)), books, diaries)))
  }

  @Test
  fun periodFilter_inclusiveBounds() {
    val f = RecallFilter(fromMillis = d2.createdAt, toMillis = d1.createdAt)
    assertEquals(listOf(1, 2), ids(PhraseRecall.search("", f, books, diaries)))
  }

  @Test
  fun pageFilter_appliesWhenExactlyOneBook() {
    val f = RecallFilter(bookIds = setOf(1), pageRange = 10..20)
    assertEquals(listOf(1), ids(PhraseRecall.search("", f, books, diaries)))
  }

  @Test
  fun pageFilter_ignoredWithTwoOrMoreBooks_orNoBook() {
    assertEquals(listOf(3, 1, 2, 4), ids(PhraseRecall.search("", RecallFilter(bookIds = setOf(1, 2), pageRange = 10..20), books, diaries)))
    assertEquals(listOf(3, 1, 2, 4), ids(PhraseRecall.search("", RecallFilter(pageRange = 10..20), books, diaries)))
  }

  @Test
  fun filtersCombineWithQuery() {
    // «그리움»: d2(책1, 9월)·d4(책2, 2025). 책2 + 2026년 이후 → 0, 책1 + 30~50쪽 + 2026년 → d2
    val y2026 = at(2026, 1, 1, 0)
    assertTrue(PhraseRecall.search("그리움", RecallFilter(bookIds = setOf(2), fromMillis = y2026), books, diaries).isEmpty())
    assertEquals(listOf(2), ids(PhraseRecall.search("그리움", RecallFilter(bookIds = setOf(1), fromMillis = y2026, pageRange = 30..50), books, diaries)))
  }

  // --- 정렬·정합 ---

  @Test
  fun sortIsDeterministic_sameCreatedAtFallsBackToDiaryIdDesc() {
    val same = listOf(
      Diary(id = 20, bookId = 1, page = 1, selectedText = "a", notes = "", createdAt = 100),
      Diary(id = 22, bookId = 2, page = 1, selectedText = "b", notes = "", createdAt = 100),
      Diary(id = 21, bookId = 1, page = 1, selectedText = "c", notes = "", createdAt = 100),
      Diary(id = 19, bookId = 1, page = 1, selectedText = "d", notes = "", createdAt = 200)
    )
    val expected = listOf(19, 22, 21, 20)
    assertEquals(expected, ids(PhraseRecall.search("", RecallFilter(), books, same)))
    assertEquals(expected, ids(PhraseRecall.search("", RecallFilter(), books, same.reversed())))
  }

  @Test
  fun diariesOfDeletedBooks_areExcluded() {
    val orphan = Diary(id = 99, bookId = 404, page = 1, selectedText = "별 하나에", notes = "", createdAt = at(2026, 11, 1))
    assertFalse(99 in ids(PhraseRecall.search("별", RecallFilter(), books, diaries + orphan)))
    assertFalse(99 in ids(PhraseRecall.search("", RecallFilter(), books, diaries + orphan)))
  }

  @Test
  fun hitCarriesBookPageAndDate() {
    val hit = PhraseRecall.search("투쟁", RecallFilter(), books, diaries).single()
    assertEquals("데미안", hit.bookTitle)
    assertEquals("헤르만 헤세", hit.author)
    assertEquals(92, hit.page)
    assertEquals(d3.createdAt, hit.createdAt)
    assertEquals(d3.selectedText, hit.selectedText)
  }

  // --- excerpt 경계 ---

  private val long = "가".repeat(60) + "별" + "나".repeat(60)

  @Test
  fun excerpt_cutsBothSides_withEllipsis() {
    val (ex, hl) = PhraseRecall.excerpt(long, "별")
    assertEquals("…" + "가".repeat(40) + "별" + "나".repeat(40) + "…", ex)
    assertEquals("별", ex.substring(hl!!))
  }

  @Test
  fun excerpt_queryNearStart_noLeadingEllipsis() {
    val (ex, hl) = PhraseRecall.excerpt("별" + "나".repeat(60), "별")
    assertEquals("별" + "나".repeat(40) + "…", ex)
    assertEquals(0..0, hl)
  }

  @Test
  fun excerpt_queryNearEnd_noTrailingEllipsis() {
    val (ex, hl) = PhraseRecall.excerpt("가".repeat(60) + "별", "별")
    assertEquals("…" + "가".repeat(40) + "별", ex)
    assertEquals("별", ex.substring(hl!!))
  }

  @Test
  fun excerpt_shortText_unchanged() {
    assertEquals("별 하나에" to (0..0), PhraseRecall.excerpt("별 하나에", "별"))
  }

  @Test
  fun excerpt_queryAbsentOrEmpty_takesHead_noHighlight() {
    val text = "가".repeat(100)
    assertEquals("가".repeat(80) + "…" to null, PhraseRecall.excerpt(text, "없음"))
    assertEquals("가".repeat(80) + "…" to null, PhraseRecall.excerpt(text, ""))
    assertEquals("" to null, PhraseRecall.excerpt("", "별"))
  }

  @Test
  fun excerpt_graphemeBoundary_neverSplitsSurrogatePairs() {
    val emoji = "😀" // 서로게이트 쌍 2 char = 1 글자
    val text = emoji.repeat(50) + "별" + emoji.repeat(50)
    val (ex, hl) = PhraseRecall.excerpt(text, "별")
    // char 단위로 잘랐다면 이모지 20개 + 반쪽이 남는다. grapheme 단위면 앞뒤 정확히 40글자.
    assertEquals("…" + emoji.repeat(40) + "별" + emoji.repeat(40) + "…", ex)
    assertEquals("별", ex.substring(hl!!))
    ex.forEachIndexed { i, c ->
      if (Character.isHighSurrogate(c)) assertTrue("lone high surrogate at $i", i + 1 < ex.length && Character.isLowSurrogate(ex[i + 1]))
      if (Character.isLowSurrogate(c)) assertTrue("lone low surrogate at $i", i > 0 && Character.isHighSurrogate(ex[i - 1]))
    }
    val (head, _) = PhraseRecall.excerpt(emoji.repeat(100), "")
    assertEquals(emoji.repeat(80) + "…", head)
  }

  // --- 기간 경계(기기 로컬 시간대) ---

  @Test
  fun period_startDayIncludesMidnight() {
    val from = RecallPeriods.dayStart(at(2026, 10, 21, 15, 30), seoul)
    assertEquals(at(2026, 10, 21, 0, 0, 0, 0), from)
    val atMidnight = Diary(id = 30, bookId = 1, page = 1, selectedText = "자정", notes = "", createdAt = at(2026, 10, 21, 0, 0, 0, 0))
    val justBefore = Diary(id = 31, bookId = 1, page = 1, selectedText = "전날", notes = "", createdAt = at(2026, 10, 20, 23, 59, 59, 999))
    assertEquals(listOf(30), ids(PhraseRecall.search("", RecallFilter(fromMillis = from), books, listOf(atMidnight, justBefore))))
  }

  @Test
  fun period_endDayIncludesLastMillisecond() {
    val to = RecallPeriods.dayEnd(at(2026, 10, 25, 9), seoul)
    assertEquals(at(2026, 10, 25, 23, 59, 59, 999), to)
    val last = Diary(id = 40, bookId = 1, page = 1, selectedText = "마지막", notes = "", createdAt = at(2026, 10, 25, 23, 59, 59, 999))
    val nextDay = Diary(id = 41, bookId = 1, page = 1, selectedText = "다음 날", notes = "", createdAt = at(2026, 10, 26, 0, 0, 0, 0))
    assertEquals(listOf(40), ids(PhraseRecall.search("", RecallFilter(toMillis = to), books, listOf(last, nextDay))))
  }

  @Test
  fun period_presets() {
    val now = at(2026, 11, 3, 14)
    assertEquals(at(2026, 10, 3, 0), RecallPeriods.presetFrom(RecallPeriod.LAST_MONTH, now, seoul))
    assertEquals(at(2025, 11, 3, 0), RecallPeriods.presetFrom(RecallPeriod.LAST_YEAR, now, seoul))
    assertNull(RecallPeriods.presetFrom(RecallPeriod.ALL, now, seoul))
    assertNull(RecallPeriods.presetFrom(RecallPeriod.CUSTOM, now, seoul))
  }
}
