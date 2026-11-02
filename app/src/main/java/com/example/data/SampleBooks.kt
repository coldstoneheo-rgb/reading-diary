package com.example.data

/**
 * ADR-003-1 «예시 2권»: 신규 설치는 빈 서재로 시작하고, 사용자가 설정에서 원할 때만 예시를 넣는다.
 *
 * 스키마를 바꾸지 않으려고 예시 표식은 Room이 아니라 일반 설정 SharedPreferences에 `"id|title"` 집합으로 둔다.
 * 판정은 id와 제목이 **둘 다** 같을 때만 한다 — DB가 새로 만들어져 id가 1부터 다시 매겨져도 사용자의 진짜 책이
 * 예시로 오인돼 일괄 삭제되지 않게 하기 위해서다. 같은 이유로 [AppDatabase]의 onCreate가 이 키를 지운다.
 *
 * 본문은 저작권 보호기간이 끝난 국내 작품(김소월 1902–1934, 윤동주 1917–1945)의 구절이고, 메모는 직접 쓴 문장이다.
 * 두 책의 메모가 같은 `#그리움` 태그를 달아 기억 서랍 «연결»이 자동 추출 품질과 무관하게 첫 화면에 보인다.
 */
object SampleBooks {
    const val PREFS_NAME = "diary_general_settings"
    const val KEY = "sample_books"
    const val SHARED_TAG = "그리움"

    data class SampleDiary(val page: Int, val selectedText: String, val notes: String)

    data class SampleBook(
        val title: String,
        val author: String,
        val totalPages: Int,
        val currentPage: Int,
        val diaries: List<SampleDiary>
    )

    val BOOKS: List<SampleBook> = listOf(
        SampleBook(
            title = "진달래꽃",
            author = "김소월",
            totalPages = 120,
            currentPage = 30,
            diaries = listOf(
                SampleDiary(
                    page = 12,
                    selectedText = "나 보기가 역겨워 가실 때에는 말없이 고이 보내 드리우리다.",
                    notes = "붙잡지 않겠다는 말 속에서 오히려 붙잡고 싶은 마음이 읽힌다. #$SHARED_TAG"
                )
            )
        ),
        SampleBook(
            title = "하늘과 바람과 별과 시",
            author = "윤동주",
            totalPages = 140,
            currentPage = 40,
            diaries = listOf(
                SampleDiary(
                    page = 8,
                    selectedText = "죽는 날까지 하늘을 우러러 한 점 부끄럼이 없기를,",
                    notes = "다짐이 이렇게 조용할 수도 있다는 것. 오늘 하루를 돌아보게 된다."
                ),
                SampleDiary(
                    page = 40,
                    selectedText = "별 하나에 추억과 별 하나에 사랑과 별 하나에 쓸쓸함과 별 하나에 동경과 별 하나에 시와 별 하나에 어머니, 어머니,",
                    notes = "멀리 있는 것들을 하나씩 이름 불러 보는 마음. 진달래꽃의 이별과 닮았다. #$SHARED_TAG"
                )
            )
        )
    )

    fun markOf(book: Book): String = "${book.id}|${book.title}"

    /** id와 제목이 모두 표식과 같을 때만 예시다. */
    fun isSample(book: Book, marks: Set<String>): Boolean = markOf(book) in marks

    fun sampleIds(books: List<Book>, marks: Set<String>): Set<Int> =
        books.filter { isSample(it, marks) }.map { it.id }.toSet()

    /**
     * 예시 책에 붙은 기록이 넣을 때 그대로인지. 사용자가 일기를 더 쓰거나 고쳤으면 false — 그 책은 «예시 지우기»로 지우지 않는다.
     * 예시 일기를 지우기만 한 경우는 사용자 기록이 없으므로 true.
     */
    fun isUntouched(book: Book, diariesOfBook: List<Diary>): Boolean {
        val sample = BOOKS.firstOrNull { it.title == book.title } ?: return false
        val original = sample.diaries.map { Triple(it.page, it.selectedText, it.notes) }.toSet()
        return diariesOfBook.all { Triple(it.page, it.selectedText, it.notes) in original }
    }
}
