package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.KeywordExtractor
import com.example.data.knowledge.KeywordLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-003 Q1·Q2. 형태소 분석기 없는 휴리스틱이므로 "무엇을 잘 못하는지"도 테스트로 고정한다.
 * 이 테스트들이 깨지면 규칙이 바뀐 것이므로 ADR-003을 같이 고친다.
 */
class KeywordLinksTest {

  // ---- KeywordExtractor ----

  @Test
  fun extract_stripsOneParticle_keepsTwoCharMinimum_dropsStopwords() {
    val words = KeywordExtractor.extract("새는 알에서 나오려고 투쟁한다. 알은 세계이다. 태어나려는 자는 하나의 세계를 깨뜨려야 한다.")
    // "새는"·"알에서"는 접미를 떼면 1글자가 되므로 떼지 않고 그대로 남는다. 즉 단음절 명사 "새", "알"은 연결 단어가 되지 못한다 — 알려진 한계(ADR-003 회의록).
    assertFalse(words.contains("새"))
    assertFalse(words.contains("알"))
    assertTrue(words.contains("새는"))
    // "세계이다"/"세계를" → "세계" 하나로 합쳐진다.
    assertEquals(1, words.count { it == "세계" })
    assertTrue(words.contains("세계"))
    // 불용어 "하나의"→"하나"는 버려진다.
    assertFalse(words.contains("하나"))
  }

  @Test
  fun stem_doesNotOverCutShortNouns() {
    // "종이"에서 "이"를 떼면 1글자가 되므로 떼지 않는다.
    assertEquals("종이", KeywordExtractor.stem("종이"))
    assertEquals("나이", KeywordExtractor.stem("나이"))
    // 긴 접미가 짧은 접미보다 먼저 적용된다.
    assertEquals("도서관", KeywordExtractor.stem("도서관에서는"))
    // 복수 접미 "들은"은 한 덩어리로 뗀다. 한 번만 떼므로 "친구에게는"은 "친구"에서 멈추고 더 줄지 않는다.
    assertEquals("학생", KeywordExtractor.stem("학생들은"))
    assertEquals("친구", KeywordExtractor.stem("친구에게는"))
  }

  @Test
  fun stem_dropsDigitsSingleCharsAndLowercasesLatin() {
    assertNull(KeywordExtractor.stem("2026"))
    assertNull(KeywordExtractor.stem("a"))
    assertNull(KeywordExtractor.stem("돈"))
    assertEquals("demian", KeywordExtractor.stem("Demian"))
  }

  @Test
  fun tags_parsesHashtagsFromNotes_inOrder_withoutDuplicates() {
    assertEquals(listOf("자존", "돈", "자존감_회복"), KeywordExtractor.tags("#자존 과 #돈 에 대해 #자존 다시 #자존감_회복"))
    assertEquals(emptyList<String>(), KeywordExtractor.tags("태그 없음"))
  }

  // ---- KeywordLinks ----

  private val demian = Book(id = 1, title = "데미안", author = "헤세", totalPages = 240, bookcaseId = 1, status = "READING")
  private val money = Book(id = 2, title = "돈의 속성", author = "김승호", totalPages = 390, bookcaseId = 2, status = "COMPLETED")
  private val sapiens = Book(id = 3, title = "사피엔스", author = "하라리", totalPages = 630, bookcaseId = 3, status = "TO_READ")

  @Test
  fun build_linksOnlyWordsSharedAcrossDifferentBooks() {
    val diaries = listOf(
      Diary(id = 10, bookId = 1, page = 45, selectedText = "내 안에서 솟아 나오려는 것, 바로 그것을 살아보려고 했다.", notes = "세계를 깨뜨리는 용기"),
      Diary(id = 11, bookId = 1, page = 92, selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다.", notes = ""),
      Diary(id = 12, bookId = 3, page = 7, selectedText = "인간은 상상 속의 세계를 공유함으로써 협동한다.", notes = ""),
      Diary(id = 13, bookId = 2, page = 112, selectedText = "돈은 인격체다. 자기를 소중히 대하는 사람에게 머문다.", notes = "")
    )

    val links = KeywordLinks.build(listOf(demian, money, sapiens), diaries)

    val world = links.single { it.word == "세계" }
    assertEquals(setOf(1, 3), world.bookIds)          // 데미안 ↔ 사피엔스
    assertEquals(3, world.quotes.size)                // 데미안 2건 + 사피엔스 1건, 같은 일기는 한 번만
    assertFalse(world.userTagged)
    // "인격체"는 돈의 속성 한 권에만 나오므로 연결이 아니다.
    assertTrue(links.none { it.word == "인격체" })
  }

  @Test
  fun build_userTagsRankFirst_andIgnoreStopwordFilter() {
    val diaries = listOf(
      Diary(id = 1, bookId = 1, page = 1, selectedText = "자유에 관한 문장", notes = "#자기 성찰"),
      Diary(id = 2, bookId = 2, page = 2, selectedText = "자유와 돈", notes = "#자기 관리"),
      Diary(id = 3, bookId = 3, page = 3, selectedText = "자유의 역사", notes = "")
    )

    val links = KeywordLinks.build(listOf(demian, money, sapiens), diaries)

    // "자유"는 3권에서 나와 책 수는 더 많지만, 사용자 태그 "자기"(불용어지만 태그라 살아남음)가 먼저 온다.
    assertEquals(listOf("자기", "자유"), links.map { it.word })
    assertTrue(links[0].userTagged)
    assertEquals(3, links[1].bookCount)
  }

  @Test
  fun build_returnsEmptyForSingleBookOrNoDiaries_andSkipsOrphanDiaries() {
    val oneBook = listOf(Diary(id = 1, bookId = 1, page = 1, selectedText = "세계 세계", notes = ""), Diary(id = 2, bookId = 1, page = 2, selectedText = "세계", notes = ""))
    assertTrue(KeywordLinks.build(listOf(demian), oneBook).isEmpty())
    assertTrue(KeywordLinks.build(listOf(demian, money), emptyList()).isEmpty())
    // bookId 99는 존재하지 않는 책 → 무시된다.
    val orphan = listOf(Diary(id = 1, bookId = 1, page = 1, selectedText = "세계", notes = ""), Diary(id = 2, bookId = 99, page = 2, selectedText = "세계", notes = ""))
    assertTrue(KeywordLinks.build(listOf(demian), orphan).isEmpty())
  }

  @Test
  fun build_respectsLimit() {
    val d1 = Diary(id = 1, bookId = 1, page = 1, selectedText = "가나다 라마바 사아자 차카타", notes = "")
    val d2 = Diary(id = 2, bookId = 2, page = 1, selectedText = "가나다 라마바 사아자 차카타", notes = "")
    assertEquals(2, KeywordLinks.build(listOf(demian, money), listOf(d1, d2), limit = 2).size)
  }
}
