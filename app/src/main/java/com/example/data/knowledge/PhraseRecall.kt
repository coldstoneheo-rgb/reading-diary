package com.example.data.knowledge

import com.example.data.Book
import com.example.data.Diary
import java.text.BreakIterator
import java.util.Calendar
import java.util.TimeZone

/**
 * 문구 회상 필터(명세 PHRASE-RECALL-SPEC §2.3). 모든 조건은 AND.
 * 기간 경계는 **포함**이다. 화면은 기기 로컬 시간대로 [RecallPeriods.dayStart]·[RecallPeriods.dayEnd]를 써서 만든다.
 */
data class RecallFilter(
    val bookIds: Set<Int> = emptySet(),     // 비면 전체
    val fromMillis: Long? = null,           // createdAt 하한(포함)
    val toMillis: Long? = null,             // createdAt 상한(포함)
    val pageRange: IntRange? = null         // bookIds.size == 1 일 때만 적용 — 여러 책의 쪽 범위는 의미가 없다
)

/** 회상 결과 한 줄. 새로 만든 문장 0 — 전부 사용자가 남긴 기록에서 잘라 온 것이다(ADR-003 Q2). */
data class RecallHit(
    val diaryId: Int,
    val bookId: Int,
    val bookTitle: String,
    val author: String,
    val page: Int,
    val createdAt: Long,
    /** 검색어 주변 발췌(앞뒤 각 radius 글자, 잘리면 …). 글자 = 사용자가 보는 한 글자(grapheme). */
    val excerpt: String,
    /** [excerpt] 안 검색어 위치. 단어 정규형 일치·메모 일치·제목 일치·검색어 없음이면 null. */
    val highlight: IntRange?,
    val selectedText: String,
    val note: String,
    /** 검색어가 비면 null(필터만으로 둘러보기). */
    val matchKind: MatchKind?
)

/**
 * «문구 회상»: 떠오른 문구·단어 하나로 내가 남긴 기록을 찾아 **어느 책 몇 쪽, 언제** 남겼는지 보여 준다.
 * 정렬은 기록한 시각 내림차순 → diaryId 내림차순(결정적). 관련도 점수 0(ADR-003 Q5).
 * Room·Compose 의존 0 — JVM 테스트([com.example.PhraseRecallTest]).
 */
object PhraseRecall {

    const val EXCERPT_RADIUS = 40

    fun search(query: String, filter: RecallFilter, books: List<Book>, diaries: List<Diary>): List<RecallHit> {
        if (books.isEmpty() || diaries.isEmpty()) return emptyList()
        val q = query.trim()
        val bookById = books.associateBy { it.id }
        // 공백 없는 한 단어면 조사 차이를 흡수한다(«그리움을» ↔ «그리움이»). 정규형이 없으면(조사뿐·한 글자) 부분 일치만.
        val queryStem = if (q.isNotEmpty() && q.none { it.isWhitespace() }) KeywordExtractor.stem(q) else null
        val pageRange = filter.pageRange?.takeIf { filter.bookIds.size == 1 }

        return diaries
            .asSequence()
            .filter { filter.bookIds.isEmpty() || it.bookId in filter.bookIds }
            .filter { filter.fromMillis == null || it.createdAt >= filter.fromMillis }
            .filter { filter.toMillis == null || it.createdAt <= filter.toMillis }
            .filter { pageRange == null || it.page in pageRange }
            .mapNotNull { diary ->
                val book = bookById[diary.bookId] ?: return@mapNotNull null // 책이 지워진 일기는 제외
                if (q.isEmpty()) return@mapNotNull hit(diary, book, excerpt(diary.selectedText, ""), null)

                val kind = matchKind(diary, book.title, q)
                when {
                    kind == MatchKind.TEXT || kind == MatchKind.TITLE_AND_TEXT ->
                        hit(diary, book, excerpt(diary.selectedText, q), kind)
                    kind != null ->
                        hit(diary, book, excerpt(diary.selectedText, ""), kind)
                    queryStem != null -> {
                        val stemKind = stemMatch(diary, queryStem) ?: return@mapNotNull null
                        // 발췌 위치는 정규형 근처로 잡되 강조는 하지 않는다(사용자가 친 글자 그대로가 아니므로).
                        val ex = excerpt(diary.selectedText, if (stemKind == MatchKind.TEXT) queryStem else "")
                        hit(diary, book, ex.first to null, stemKind)
                    }
                    else -> null
                }
            }
            .sortedWith(compareByDescending<RecallHit> { it.createdAt }.thenByDescending { it.diaryId })
            .toList()
    }

    private fun stemMatch(diary: Diary, stem: String): MatchKind? = when {
        stem in KeywordExtractor.extract(diary.selectedText) -> MatchKind.TEXT
        stem in KeywordExtractor.extract(diary.notes) -> MatchKind.NOTES
        KeywordExtractor.tags(diary.notes).any { it.lowercase() == stem } -> MatchKind.NOTES
        else -> null
    }

    private fun hit(diary: Diary, book: Book, ex: Pair<String, IntRange?>, kind: MatchKind?) = RecallHit(
        diaryId = diary.id,
        bookId = book.id,
        bookTitle = book.title,
        author = book.author,
        page = diary.page,
        createdAt = diary.createdAt,
        excerpt = ex.first,
        highlight = ex.second,
        selectedText = diary.selectedText,
        note = diary.notes,
        matchKind = kind
    )

    /**
     * [text]에서 [query](대소문자 무시) 첫 위치 앞뒤 [radius]글자를 잘라 온다. 잘린 쪽에 «…».
     * 검색어가 비었거나 없으면 앞에서부터 radius×2 글자. 경계는 grapheme 단위라 서로게이트 쌍·결합 문자를 가르지 않는다.
     * @return 발췌문과 그 안의 검색어 위치(없으면 null).
     */
    fun excerpt(text: String, query: String, radius: Int = EXCERPT_RADIUS): Pair<String, IntRange?> {
        if (text.isEmpty()) return "" to null
        val bounds = graphemeBoundaries(text)
        val q = query.trim()
        val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
        if (at < 0) {
            val end = bounds[minOf(bounds.lastIndex, radius * 2)]
            return (text.substring(0, end) + if (end < text.length) "…" else "") to null
        }
        val matchEnd = at + q.length
        val startIdx = bounds.indexOfLast { it <= at }            // 검색어가 grapheme 중간에서 시작해도 그 앞 경계로
        val endIdx = bounds.indexOfFirst { it >= matchEnd }        // 끝도 다음 경계로
        val from = bounds[maxOf(0, startIdx - radius)]
        val to = bounds[minOf(bounds.lastIndex, endIdx + radius)]
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        val hlStart = prefix.length + (at - from)
        return (prefix + text.substring(from, to) + suffix) to (hlStart until hlStart + q.length)
    }

    /** 0과 text.length를 포함한 grapheme 경계(오름차순). */
    private fun graphemeBoundaries(text: String): List<Int> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<Int>(text.length + 1)
        var b = it.first()
        while (b != BreakIterator.DONE) {
            out.add(b)
            b = it.next()
        }
        return out
    }
}

/** 기간 필터 선택지. 기준 = Diary.createdAt(«기록한 날»). */
enum class RecallPeriod(val label: String) {
    ALL("전체"),
    LAST_MONTH("최근 1개월"),
    LAST_YEAR("최근 1년"),
    CUSTOM("직접 지정")
}

/** 기간 → createdAt 경계(포함). minSdk 24라 java.time 대신 Calendar를 쓴다. */
object RecallPeriods {

    /** [millis]가 속한 날(그 시간대)의 00:00:00.000. */
    fun dayStart(millis: Long, tz: TimeZone): Long = Calendar.getInstance(tz).run {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** [millis]가 속한 날(그 시간대)의 23:59:59.999 — 다음 날 00:00에서 1ms 뺀 값이라 서머타임 날도 맞다. */
    fun dayEnd(millis: Long, tz: TimeZone): Long = Calendar.getInstance(tz).run {
        timeInMillis = dayStart(millis, tz)
        add(Calendar.DAY_OF_MONTH, 1)
        timeInMillis - 1
    }

    /** 미리 정한 기간의 하한. 상한은 없다(지금까지). ALL·CUSTOM은 null. */
    fun presetFrom(period: RecallPeriod, nowMillis: Long, tz: TimeZone): Long? {
        val field = when (period) {
            RecallPeriod.LAST_MONTH -> Calendar.MONTH
            RecallPeriod.LAST_YEAR -> Calendar.YEAR
            else -> return null
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis; add(field, -1) }
        return dayStart(cal.timeInMillis, tz)
    }
}
