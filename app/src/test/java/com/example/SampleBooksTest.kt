package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.SampleBooks
import com.example.data.knowledge.KeywordLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-003-1 «예시 2권»의 순수 규칙. */
class SampleBooksTest {

  private fun book(id: Int, title: String) =
    Book(id = id, title = title, author = "", totalPages = 100, bookcaseId = 1, status = "READING")

  @Test
  fun isSample_requiresBothIdAndTitle() {
    val marks = setOf("1|진달래꽃", "2|하늘과 바람과 별과 시")
    assertTrue(SampleBooks.isSample(book(1, "진달래꽃"), marks))
    // DB가 새로 만들어져 id 1이 사용자의 진짜 책이 된 경우: 제목이 다르면 예시가 아니다
    assertFalse(SampleBooks.isSample(book(1, "내 진짜 책"), marks))
    // 같은 제목이라도 id가 다르면(사용자가 직접 넣은 같은 이름의 책) 예시가 아니다
    assertFalse(SampleBooks.isSample(book(7, "진달래꽃"), marks))
    assertFalse(SampleBooks.isSample(book(1, "진달래꽃"), emptySet()))
    assertEquals(setOf(1), SampleBooks.sampleIds(listOf(book(1, "진달래꽃"), book(3, "다른 책")), marks))
  }

  @Test
  fun samples_produceASharedTagConnection() {
    val books = SampleBooks.BOOKS.mapIndexed { i, s -> book(i + 1, s.title) }
    val diaries = SampleBooks.BOOKS.flatMapIndexed { i, s ->
      s.diaries.map { Diary(bookId = i + 1, page = it.page, selectedText = it.selectedText, notes = it.notes) }
    }
    assertEquals(2, books.size)
    val shared = KeywordLinks.build(books, diaries)
    val tag = shared.firstOrNull { it.word == SampleBooks.SHARED_TAG }
    assertTrue("expected #${SampleBooks.SHARED_TAG} to link both sample books: $shared", tag != null)
    assertTrue(tag!!.userTagged)
    assertEquals(setOf(1, 2), tag.bookIds.toSet())
  }

  @Test
  fun samples_doNotQuoteTheOldCopyrightedSeed() {
    val old = listOf("데미안", "돈의 속성", "사피엔스", "헤르만 헤세", "김승호", "유발 하라리",
      "알에서 나오려고 투쟁", "돈은 인격체", "솟아 나오려는 것")
    val text = SampleBooks.BOOKS.joinToString("\n") { b ->
      b.title + b.author + b.diaries.joinToString { it.selectedText + it.notes }
    }
    old.forEach { assertFalse("sample text must not contain \"$it\"", text.contains(it)) }
  }
}
