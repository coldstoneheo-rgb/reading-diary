package com.example

import com.example.data.Book
import com.example.data.Diary
import com.example.data.knowledge.KeywordExtractor
import com.example.data.knowledge.KeywordLinks
import com.example.data.knowledge.QuoteSource
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
  fun stem_protectsNounsEndingInIGa_andIsmWords_butStripsVerbEndings() {
    // 3글자 토큰의 "이/가"는 명사 끝소리일 가능성이 커서 떼지 않는다.
    assertEquals("고양이", KeywordExtractor.stem("고양이"))
    assertEquals("호랑이", KeywordExtractor.stem("호랑이"))
    // "~주의"는 조사 "의"가 아니다.
    assertEquals("자본주의", KeywordExtractor.stem("자본주의"))
    assertEquals("민주주의", KeywordExtractor.stem("민주주의를"))
    // 용언 어미는 2글자 규칙으로 뗀다. 활용형이 달라도 같은 단어가 되도록 한다.
    assertEquals("필요", KeywordExtractor.stem("필요하다"))
    assertEquals("중요", KeywordExtractor.stem("중요하고"))
    assertEquals("위대", KeywordExtractor.stem("위대한"))
    // 어미만 남은 토큰은 단어가 아니다 — 두 책이 "했다"로만 겹쳐 연결이 되는 것을 막는다.
    assertNull(KeywordExtractor.stem("했다"))
    assertNull(KeywordExtractor.stem("그렇게"))
    assertNull(KeywordExtractor.stem("하는"))
  }

  /**
   * 골든 코퍼스. 규칙이 바뀌면 여기서 먼저 깨진다.
   * **개선(고쳐야 할 것)과 알려진 한계(감수하기로 한 것)를 같은 표에 둔다** — 무엇이 대가인지 코드가 아니라 테스트가 증언하게 한다.
   */
  @Test
  fun stem_goldenCorpus() {
    val expected: List<Pair<String, String?>> = listOf(
      // ── 명사 + 조사: 조사를 뗀다 ──────────────────────────────
      "주인이" to "주인", "습관이" to "습관", "욕망이" to "욕망", "가치가" to "가치",
      "생각도" to "생각", "정도로" to "정도", "여성의" to "여성", "이야기도" to "이야기",
      "가능성의" to "가능성", "문제도" to "문제", "행복도" to "행복", "내용도" to "내용",
      "사회의" to "사회", "운동의" to "운동", "학문의" to "학문", "혼자만" to "혼자",
      "세계를" to "세계", "인격으로" to "인격", "도서관에서는" to "도서관", "학생들은" to "학생", "친구에게는" to "친구",
      // ── 명사인데 조사처럼 끝난다: 사전이 보호한다 ─────────────
      "고양이" to "고양이", "호랑이" to "호랑이", "전문가" to "전문가", "자본주의" to "자본주의", "민주주의를" to "민주주의",
      "종이" to "종이", "나이" to "나이", "바다" to "바다", "붓다" to "붓다",
      // ── 용언: 어간을 통일하거나 버린다 ─────────────────────────
      "생각하다" to "생각", "생각한다" to "생각", "생각했다" to "생각", "생각하는" to "생각",
      "생각하고" to "생각", "생각하며" to "생각", "생각하라는" to "생각", "필요하다는" to "필요",
      "필요하다" to "필요", "중요하고" to "중요", "위대한" to "위대", "투쟁한다" to "투쟁", "세계이다" to "세계",
      "간다" to null, "온다" to null, "본다" to null, "산다" to null,
      "나온다" to null, "만든다" to null, "떠났다" to null, "몰랐다" to null, "아름답다" to null,
      "했다" to null, "하는" to null, "그렇게" to null,
      // ── 2글자 한자어 명사: 1글자 어미로 지워지면 안 된다 ────────
      "제한" to "제한", "권한" to "권한", "무한" to "무한", "기한" to "기한", "인하" to "인하",
      // ── 버리는 것 ─────────────────────────────────────────────
      "2026" to null, "a" to null, "돈" to null, "하나의" to null,
      // ── 그 밖 ─────────────────────────────────────────────────
      "Demian" to "demian", "_성장_" to "성장", "자존감_회복" to "자존감_회복", "새는" to "새는", "알에서" to "알에서"
    )
    val wrong = expected.filter { (token, want) -> KeywordExtractor.stem(token) != want }
      .map { (token, want) -> "$token → ${KeywordExtractor.stem(token)} (기대: $want)" }
    assertTrue("골든 코퍼스 불일치:\n" + wrong.joinToString("\n"), wrong.isEmpty())
  }

  /**
   * 감수하기로 한 한계. **고쳐야 할 버그가 아니라 선택한 대가다**(ADR-004 Q3).
   * 조사로 끝나 보이는 2글자 명사를 `endsWith`로 보호하면 `생각도`·`정도로`·`여성의`가 통째로 살아남는데,
   * 그쪽이 합성어보다 훨씬 흔해서 합성어를 포기했다. 이 기대값을 "고치려면" 위 골든 코퍼스의 명사+조사 항목이 함께 깨진다.
   */
  @Test
  fun stem_knownLimitations_compoundNounsEndingLikeParticles() {
    assertEquals("연구결", KeywordExtractor.stem("연구결과"))
    assertEquals("적정온", KeywordExtractor.stem("적정온도"))
    assertEquals("국무회", KeywordExtractor.stem("국무회의"))
    // `다`로 끝나면 활용형으로 본다. 서술격 명사도 함께 버려진다.
    assertNull(KeywordExtractor.stem("인격체다"))
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
    // 일기 10은 메모에만 "세계"가 있다 → 근거는 메모 원문이어야 한다. 구절만 보여 주면 근거 없는 연결로 보인다.
    val fromNotes = world.quotes.single { it.diaryId == 10 }
    assertEquals(QuoteSource.NOTES, fromNotes.source)
    assertEquals("세계를 깨뜨리는 용기", fromNotes.text)
    assertEquals(QuoteSource.TEXT, world.quotes.single { it.diaryId == 11 }.source)
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
    assertTrue(links[0].quotes.all { it.source == QuoteSource.TAG && it.text.startsWith("#자기") })
    assertEquals(3, links[1].bookCount)
  }

  @Test
  fun seedDiaries_produceNoConnection_pinned() {
    // AppDatabase.onCreate 시드 3건(데미안 2, 돈의 속성 1). 두 책이 공유하는 단어가 없어 신규 설치의 "연결 발견"은 비어 있다.
    // 시드를 바꾸는 것은 ADR-003 보류 1(오너 결정). 이 테스트는 현 상태를 고정해 규칙 변경이 시드에 미치는 영향을 드러낸다.
    val seed = listOf(
      Diary(id = 1, bookId = 1, page = 45,
        selectedText = "내 안에서 솟아 나오려는 것, 바로 그것을 살아보려고 했다. 왜 그것이 그토록 어려웠을까?",
        notes = "내 삶의 주인이 되는 것의 찬란함과 두려움을 일깨워주는 위대한 문장. 나는 과연 온전히 나로서 살아보고 있는가."),
      Diary(id = 2, bookId = 1, page = 92,
        selectedText = "새는 알에서 나오려고 투쟁한다. 알은 세계이다. 태어나려는 자는 하나의 세계를 깨뜨려야 한다.",
        notes = "성장에 필수적인 고통과 가치관 극복에 관한 불후의 교훈. 새로운 시각을 끊임없이 깨어나야 한다."),
      Diary(id = 3, bookId = 2, page = 112,
        selectedText = "돈은 인격체다. 자기를 소중히 대하는 사람에게 머물며 함부로 다루면 언제든 떠나갈 궁리를 한다.",
        notes = "돈을 단순 욕망이 아닌 인격으로 대하라는 혜안. 나의 자산관리 습관이 돈을 인격적으로 존중하고 있었는지 생각하게 된다.")
    )
    assertEquals(emptyList<String>(), KeywordLinks.build(listOf(demian, money), seed).map { it.word })
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
