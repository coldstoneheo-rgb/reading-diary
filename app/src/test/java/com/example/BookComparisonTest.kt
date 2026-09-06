package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.BookComparison
import com.example.data.knowledge.MatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-004: 검색 결과를 책별로 묶어 "같은 키워드가 다른 책에서도 나왔다"를 보이게 한다.
 * 오너 비전: A 책을 떠올리며 검색했는데 B 책에서도 그때 쓴 내 메모가 나오는 경험.
 */
class BookComparisonTest {

  private val demian = Book(id = 1, title = "데미안", author = "헤르만 헤세", totalPages = 240, bookcaseId = 1, status = "READING")
  private val sapiens = Book(id = 2, title = "사피엔스", author = "유발 하라리", totalPages = 630, bookcaseId = 1, status = "TO_READ")
  private val money = Book(id = 3, title = "돈의 속성", author = "김승호", totalPages = 390, bookcaseId = 2, status = "COMPLETED")
  private val books = listOf(demian, sapiens, money)

  private fun diary(id: Int, bookId: Int, page: Int, text: String, notes: String = "") =
    Diary(id = id, bookId = bookId, page = page, selectedText = text, notes = notes, createdAt = 1_780_000_000_000L)

  @Test
  fun sameKeywordAcrossBooks_isGroupedByBook() {
    val diaries = listOf(
      diary(10, 1, 92, "새는 알에서 나오려고 투쟁한다. 알은 세계이다.", "내 세계를 깨뜨리는 일"),
      diary(11, 2, 31, "인간은 상상 속의 세계를 공유함으로써 협동한다."),
      diary(12, 3, 112, "돈은 인격체다.", "돈을 대하는 태도")
    )

    val matches = BookComparison.compare("세계", books, diaries)

    // 데미안 1건 + 사피엔스 1건. 돈의 속성은 "세계"가 없다.
    assertEquals(listOf("데미안", "사피엔스"), matches.map { it.bookTitle })
    assertEquals(2, BookComparison.bookCount(matches))
    assertEquals(2, BookComparison.recordCount(matches))
    assertEquals("헤르만 헤세", matches[0].author)
    assertEquals(92, matches[0].records[0].page)
  }

  @Test
  fun recordsMatchedInMyNote_comeFirstWithinABook() {
    val diaries = listOf(
      diary(10, 1, 10, "성장에 관한 문장"),                    // 구절 일치
      diary(11, 1, 200, "다른 문장", "성장이란 무엇인가"),      // 메모 일치 — 페이지는 뒤지만 먼저 와야 한다
      diary(12, 1, 5, "성장하는 사람")                         // 구절 일치, 더 앞 페이지
    )

    val records = BookComparison.compare("성장", books, diaries).single().records

    assertEquals(listOf(11, 12, 10), records.map { it.diaryId })
    assertTrue(records[0].matchedInNote)
    assertEquals(MatchKind.NOTES, records[0].matchKind)
    assertFalse(records[1].matchedInNote)
  }

  @Test
  fun booksAreOrderedByRecordCountThenTitle_deterministically() {
    val diaries = listOf(
      diary(1, 1, 1, "기록"),
      diary(2, 2, 1, "기록"), diary(3, 2, 2, "기록"),
      diary(4, 3, 1, "기록")
    )

    val matches = BookComparison.compare("기록", books, diaries)

    // 사피엔스 2건이 먼저, 나머지 1건짜리는 제목 사전순(데미안 < 돈의 속성)
    assertEquals(listOf("사피엔스", "데미안", "돈의 속성"), matches.map { it.bookTitle })
  }

  @Test
  fun blankQueryOrNoMatch_returnsEmpty() {
    val diaries = listOf(diary(1, 1, 1, "세계"))
    assertTrue(BookComparison.compare("", books, diaries).isEmpty())
    assertTrue(BookComparison.compare("   ", books, diaries).isEmpty())
    assertTrue(BookComparison.compare("없는말", books, diaries).isEmpty())
    assertTrue(BookComparison.compare("세계", emptyList(), diaries).isEmpty())
    assertTrue(BookComparison.compare("세계", books, emptyList()).isEmpty())
  }

  @Test
  fun diaryOfDeletedBook_isExcluded() {
    val diaries = listOf(diary(1, 1, 1, "세계"), diary(2, 99, 1, "세계"))
    val matches = BookComparison.compare("세계", books, diaries)
    assertEquals(1, matches.size)
    assertEquals(listOf(1), matches[0].records.map { it.diaryId })
  }

  @Test
  fun bookTitleMatch_bringsTheWholeBookIn_evenWithoutTextMatch() {
    val diaries = listOf(diary(1, 1, 7, "아무 문장", "아무 메모"))
    val matches = BookComparison.compare("데미안", books, diaries)
    assertEquals(1, matches.size)
    assertEquals(MatchKind.TITLE, matches[0].records[0].matchKind)
  }
}
