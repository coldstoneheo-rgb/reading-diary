package com.example.data.knowledge

import com.example.data.Book
import com.example.data.Diary

/**
 * 검색어가 기록의 어디에 맞았는지. 고정 점수 대신 근거를 보여준다(ADR-003 Q5).
 * 조합 라벨은 "제목·구절"만 둔다(가장 흔한 조합). 제목+메모처럼 다른 조합은 우선순위가 높은 한 가지만 표시한다 — 알려진 단순화.
 */
enum class MatchKind(val label: String) {
    TITLE_AND_TEXT("제목·구절 일치"),
    TEXT("구절 일치"),
    NOTES("메모 일치"),
    TITLE("제목 일치")
}

/** 순수 함수: 검색어가 비면 null(전체 표시), 아니면 일치 종류. 어디에도 안 맞으면 결과에서 제외된다. */
fun matchKind(diary: Diary, bookTitle: String, query: String): MatchKind? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return null
    val inText = diary.selectedText.lowercase().contains(q)
    val inNotes = diary.notes.lowercase().contains(q)
    val inTitle = bookTitle.lowercase().contains(q)
    return when {
        inTitle && inText -> MatchKind.TITLE_AND_TEXT
        inText -> MatchKind.TEXT
        inNotes -> MatchKind.NOTES
        inTitle -> MatchKind.TITLE
        else -> null
    }
}

/** 한 책 안에서 검색어에 맞은 기록 하나. [note]가 비어 있지 않으면 화면이 그것을 먼저 보여준다. */
data class MatchedRecord(
    val diaryId: Int,
    val page: Int,
    val excerpt: String,
    val note: String,
    val matchKind: MatchKind,
    /**
     * 검색어가 내가 쓴 메모 안에 있는가. 오너 비전의 "그때 내 생각"이 걸린 기록이다.
     * [matchKind]에서 파생시키지 않는다 — 구절과 메모에 **둘 다** 있으면 라벨은 "구절 일치"가 되지만
     * 그런 기록이야말로 가장 값진 비교 대상이므로 여기서는 true여야 한다.
     */
    val matchedInNote: Boolean,
    val createdAt: Long
)

/** 검색어가 발견된 책 하나와 그 안의 기록들. */
data class BookMatch(
    val bookId: Int,
    val bookTitle: String,
    val author: String,
    val records: List<MatchedRecord>
)

/**
 * 검색 결과를 **책별로 묶는** 순수 함수.
 *
 * 오너 비전: "A라는 책을 생각하고 키워드를 검색했는데 B라는 책에서도 해당 키워드와 본인이 기록한 독서메모가 나오면
 * 매우 색다른 경험이고 새로운 인사이트를 제공할 수 있다." 그 경험은 결과가 **책 단위로 나뉘어 나란히 보일 때** 생긴다.
 * 평면 리스트로 섞어 놓으면 "같은 단어가 다른 책에서도 나왔다"는 사실 자체가 눈에 띄지 않는다.
 *
 * **[books]에 없는 책의 일기는 검색 대상이 아니다** — 비교의 단위가 책이므로 소속을 모르는 기록은 열을 만들 수 없다.
 * Room·Compose에 의존하지 않아 JVM 테스트가 가능하다.
 */
object BookComparison {

    /**
     * @return 검색어에 맞은 책들. 정렬은 결정적이다: 기록 많은 책 → 제목 사전순 → bookId.
     *   검색어가 비었거나 맞은 기록이 없으면 빈 목록.
     */
    fun compare(query: String, books: List<Book>, diaries: List<Diary>): List<BookMatch> {
        val q = query.trim()
        if (q.isEmpty() || books.isEmpty() || diaries.isEmpty()) return emptyList()
        val bookById = books.associateBy { it.id }

        return diaries
            .mapNotNull { diary ->
                val book = bookById[diary.bookId] ?: return@mapNotNull null // 책이 지워진 일기는 제외
                val kind = matchKind(diary, book.title, q) ?: return@mapNotNull null
                book to MatchedRecord(
                    diaryId = diary.id,
                    page = diary.page,
                    excerpt = diary.selectedText,
                    note = diary.notes,
                    matchKind = kind,
                    matchedInNote = diary.notes.lowercase().contains(q.lowercase()),
                    createdAt = diary.createdAt
                )
            }
            .groupBy({ it.first }, { it.second })
            .map { (book, records) ->
                BookMatch(
                    bookId = book.id,
                    bookTitle = book.title,
                    author = book.author,
                    // 메모가 걸린 기록을 먼저 — 오너 비전의 "그때 내 생각"이 화면 위쪽에 오게 한다.
                    records = records.sortedWith(
                        compareByDescending<MatchedRecord> { it.matchedInNote }.thenBy { it.page }.thenBy { it.diaryId }
                    )
                )
            }
            .sortedWith(
                compareByDescending<BookMatch> { it.records.size }.thenBy { it.bookTitle }.thenBy { it.bookId }
            )
    }

    /** 검색 결과가 걸친 책 수. 2 이상이면 "여러 책에서 발견"이라는 사실 자체가 화면의 주인공이 된다. */
    fun bookCount(matches: List<BookMatch>): Int = matches.size

    /** 전체 기록 수. */
    fun recordCount(matches: List<BookMatch>): Int = matches.sumOf { it.records.size }
}
