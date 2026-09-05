package com.example.data.knowledge

import com.example.data.Book
import com.example.data.Diary

/** 단어가 일기의 어디에서 나왔는가. 화면은 그 원문을 보여 준다(ADR-003 Q2: 근거 없는 연결은 보이지 않는다). */
enum class QuoteSource { TEXT, NOTES, TAG }

/** 연결 카드 한 줄: 어느 책의 어느 기록이 이 단어를 품고 있는가. [text]는 단어가 실제로 들어 있는 원문이다. */
data class LinkedQuote(
    val bookId: Int,
    val bookTitle: String,
    val diaryId: Int,
    val page: Int,
    val text: String,
    val source: QuoteSource
)

/**
 * 서로 다른 책 2권 이상의 일기에 함께 나온 단어 하나(ADR-003 Q1).
 * @param userTagged 어느 일기든 `#태그`로 직접 붙인 단어이면 true. 자동 추출보다 앞에 정렬된다.
 */
data class SharedWord(
    val word: String,
    val userTagged: Boolean,
    val bookIds: Set<Int>,
    val quotes: List<LinkedQuote>
) {
    val bookCount: Int get() = bookIds.size
}

/**
 * 책·일기 목록에서 "책 사이의 연결"을 계산하는 순수 함수. Room·Compose에 의존하지 않아 JVM 테스트가 가능하다.
 * 계산은 ViewModel에서 DB가 바뀔 때만 한다(ADR-003 Q4). 복잡도는 O(전체 토큰 수).
 */
object KeywordLinks {

    /**
     * @param minBooks 연결로 인정할 최소 책 수. 같은 책 안의 반복은 연결이 아니다.
     * @param limit 상위 몇 개까지. 정렬: 사용자 태그 우선 → 책 수 많은 순 → 구절 수 많은 순 → 단어 사전순.
     */
    fun build(
        books: List<Book>,
        diaries: List<Diary>,
        minBooks: Int = 2,
        limit: Int = 30
    ): List<SharedWord> {
        if (books.isEmpty() || diaries.isEmpty()) return emptyList()
        val titleById = books.associate { it.id to it.title }

        val tagged = HashSet<String>()
        val quotesByWord = LinkedHashMap<String, MutableList<LinkedQuote>>()

        for (diary in diaries) {
            val title = titleById[diary.bookId] ?: continue // 책이 지워진 일기는 연결에서 제외
            // 단어 → 이 일기에서의 출처. 구절에 있으면 구절, 태그면 태그, 메모에만 있으면 메모.
            val sourceByWord = LinkedHashMap<String, QuoteSource>()
            for (tag in KeywordExtractor.tags(diary.notes)) {
                val w = tag.lowercase()
                sourceByWord.putIfAbsent(w, QuoteSource.TAG)
                tagged.add(w)
            }
            for (w in KeywordExtractor.extract(diary.selectedText)) {
                if (sourceByWord[w] != QuoteSource.TAG) sourceByWord[w] = QuoteSource.TEXT
            }
            for (w in KeywordExtractor.extract(diary.notes)) sourceByWord.putIfAbsent(w, QuoteSource.NOTES)

            for ((word, source) in sourceByWord) {
                val text = if (source == QuoteSource.TEXT) diary.selectedText else diary.notes
                quotesByWord.getOrPut(word) { mutableListOf() }
                    .add(LinkedQuote(diary.bookId, title, diary.id, diary.page, text, source))
            }
        }

        return quotesByWord.entries
            .mapNotNull { (word, quotes) ->
                val bookIds = quotes.map { it.bookId }.toSet()
                if (bookIds.size < minBooks) null
                else SharedWord(word, word in tagged, bookIds, quotes.sortedWith(compareBy({ it.bookTitle }, { it.page })))
            }
            .sortedWith(
                compareByDescending<SharedWord> { it.userTagged }
                    .thenByDescending { it.bookCount }
                    .thenByDescending { it.quotes.size }
                    .thenBy { it.word }
            )
            .take(limit)
    }
}
