package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.export.MarkdownExport
import com.example.data.knowledge.SharedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STAGE2-PLAN ②: 파일 이름 규칙 · 같은 이름 처리 · 건너뛰기 · 인덱스 노트 · frontmatter · 빈 책. */
class MarkdownExportTest {

  private fun book(id: Int, title: String, author: String = "저자", status: String = "READING") =
    Book(id = id, title = title, author = author, totalPages = 300, currentPage = 10, bookcaseId = 1, status = status, rating = 4)

  private fun diary(id: Int, bookId: Int, page: Int, text: String, notes: String = "") =
    Diary(id = id, bookId = bookId, page = page, selectedText = text, notes = notes, createdAt = 1_000L * id)

  private fun word(w: String, vararg bookIds: Int) = SharedWord(w, false, bookIds.toSet(), emptyList())

  @Test
  fun sanitize_removesForbiddenAndControlCharsAndTrims() {
    assertEquals("ab cd", MarkdownExport.sanitize(" a/b\\: *?\"<>|c\td "))
    assertEquals("링크 깨짐", MarkdownExport.sanitize("[링크] #깨짐^"))
    assertEquals("앞뒤 점", MarkdownExport.sanitize("..앞뒤 점.."))
    assertEquals("제목 없음", MarkdownExport.sanitize("  ///  "))
    assertEquals("제목 없음", MarkdownExport.sanitize(""))
  }

  @Test
  fun sanitize_cutsAt120CodePoints() {
    val long = "가".repeat(130)
    assertEquals(120, MarkdownExport.sanitize(long).length)
    // 서로게이트 쌍을 반으로 자르지 않는다.
    val emoji = "😀".repeat(130)
    val cut = MarkdownExport.sanitize(emoji)
    assertEquals(120, cut.codePointCount(0, cut.length))
  }

  @Test
  fun fileNames_sameTitleGetsSuffixInIdOrder_caseInsensitive_andAvoidsIndexName() {
    val names = MarkdownExport.fileNames(
      listOf(book(5, "같은 책"), book(2, "같은 책"), book(9, "같은 책"), book(3, "Book"), book(4, "book"), book(7, MarkdownExport.INDEX_NAME))
    )
    assertEquals("같은 책", names[2])
    assertEquals("같은 책 (2)", names[5])
    assertEquals("같은 책 (3)", names[9])
    assertEquals("Book", names[3])
    assertEquals("book (2)", names[4])
    assertEquals(MarkdownExport.INDEX_NAME + " (2)", names[7])
  }

  @Test
  fun bookNote_hasFrontmatterRecordsAndOnlyGivenWords() {
    val note = MarkdownExport.bookNote(
      book(1, "제목 \"따옴표\"", status = "COMPLETED"),
      listOf(diary(2, 1, 30, "둘째 줄\n이어짐", "메모"), diary(1, 1, 12, "첫 구절")),
      listOf("고독"),
      "2026-11-11"
    )
    assertTrue(note, note.startsWith("---\ntitle: \"제목 \\\"따옴표\\\"\"\nauthor: \"저자\"\nstatus: \"다 읽음\"\nrating: 4\npages: 300\nexported: 2026-11-11\n---\n"))
    assertTrue(note.indexOf("### p.12") < note.indexOf("### p.30"))
    assertTrue(note.contains("> 둘째 줄\n> 이어짐\n"))
    assertTrue(note.contains("\n메모\n"))
    assertTrue(note.contains("[[고독]]"))
  }

  @Test
  fun bookNote_emptyBookHasNoRecordsLine() {
    val note = MarkdownExport.bookNote(book(1, "빈 책"), emptyList(), emptyList(), "2026-11-11")
    assertTrue(note.contains("아직 남긴 기록이 없습니다."))
    assertFalse(note.contains("[["))
    assertFalse(note.contains("함께 나온 단어"))
  }

  @Test
  fun plan_linksOnlyWordsSharedByThatBook() {
    val plan = MarkdownExport.plan(
      listOf(book(1, "가"), book(2, "나"), book(3, "다")),
      listOf(diary(1, 1, 1, "x"), diary(2, 2, 1, "y"), diary(3, 3, 1, "z")),
      listOf(word("바다", 1, 2), word("숲", 2, 3)),
      emptySet(),
      "2026-11-11"
    )
    val byName = plan.toCreate.associate { it.fileName to it.content }
    assertTrue(byName.getValue("가.md").contains("[[바다]]"))
    assertFalse(byName.getValue("가.md").contains("[[숲]]"))
    assertTrue(byName.getValue("나.md").contains("[[바다]] [[숲]]"))
    assertFalse(byName.getValue("다.md").contains("[[바다]]"))
  }

  @Test
  fun plan_existingNamesAreNeverInToCreate_includingIndex() {
    val books = listOf(book(1, "가"), book(2, "나"), book(3, "다"))
    val existing = setOf("가.md", "다.md", MarkdownExport.INDEX_NAME + ".md", "무관한 노트.md")
    val plan = MarkdownExport.plan(books, emptyList(), emptyList(), existing, "2026-11-11")
    val created = plan.toCreate.map { it.fileName }
    assertEquals(listOf("나.md"), created)
    assertEquals(listOf("가.md", "다.md", MarkdownExport.INDEX_NAME + ".md"), plan.skipped)
  }

  @Test
  fun plan_existingNameMatchIsCaseInsensitive() {
    val plan = MarkdownExport.plan(listOf(book(1, "Book")), emptyList(), emptyList(), setOf("BOOK.MD"), "2026-11-11")
    assertTrue(plan.toCreate.none { it.fileName == "Book.md" })
    assertEquals(listOf("Book.md"), plan.skipped)
  }

  @Test
  fun plan_secondRunInSameFolderSkipsEverything() {
    val books = listOf(book(1, "가"), book(2, "가"), book(3, "나"))
    val diaries = listOf(diary(1, 1, 3, "구절"))
    val first = MarkdownExport.plan(books, diaries, emptyList(), emptySet(), "2026-11-11")
    assertEquals(4, first.toCreate.size)
    assertTrue(first.skipped.isEmpty())
    val second = MarkdownExport.plan(books, diaries, emptyList(), first.toCreate.map { it.fileName }.toSet(), "2026-11-12")
    assertTrue(second.toCreate.isEmpty())
    assertEquals(first.toCreate.map { it.fileName }, second.skipped)
  }

  @Test
  fun indexNote_linksEveryBookFileName() {
    val books = listOf(book(1, "가", author = "김"), book(2, "가", author = ""))
    val names = MarkdownExport.fileNames(books)
    val index = MarkdownExport.indexNote(books, names)
    assertEquals("# ${MarkdownExport.INDEX_NAME}\n\n- [[가]] — 김\n- [[가 (2)]]\n", index)
  }
}
