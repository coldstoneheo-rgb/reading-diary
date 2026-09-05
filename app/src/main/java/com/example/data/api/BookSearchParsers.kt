package com.example.data.api

import org.json.JSONObject

data class SearchResultBook(
    val title: String,
    val author: String,
    val pageCount: Int,
    val coverUrl: String
)

/**
 * 도서 검색 API 응답 본문(JSON 문자열)을 [SearchResultBook] 목록으로 바꾸는 순수 함수 모음.
 * 네트워크 호출과 분리되어 있어 오프라인 단위 테스트가 가능하다.
 */
object BookSearchParsers {

    private val HTML_TAG = Regex("<[^>]*>")
    private val TITLE_EDITION_SUFFIX =
        Regex("\\s*[\\(\\[](반양장본|양장본|개정판|제\\d+판|Paperback|Hardcover|소설|단행본|Korean Edition|번역본)[\\)\\]]")
    private val AUTHOR_ROLE_PREFIX = Regex("^(저자|지은이|글|그림|옮김)\\s*:\\s*")
    private val AUTHOR_ROLE_SUFFIX = Regex("\\s+(저|지음|글|그림|역)$")
    /** 역할어만 남은 조각(예: author가 "지음")은 저자가 아니다. */
    private val AUTHOR_ROLE_ONLY = Regex("^(저|지음|글|그림|역|옮김|편|편저|감수)$")

    /**
     * 네이버 책 검색 API(`/v1/search/book.json`) 응답 파싱. `items`가 없으면 빈 목록.
     * 본문이 유효한 JSON이 아니거나 항목이 객체가 아니면 `JSONException`을 전파한다(호출자가 처리).
     */
    fun parseNaverBooks(body: String): List<SearchResultBook> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SearchResultBook>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val rawTitle = item.optString("title", "알 수 없는 제목")
            val rawAuthor = item.optString("author", "지은이 미상")

            val cleanTitle = rawTitle
                .replace(HTML_TAG, "")
                .replace(TITLE_EDITION_SUFFIX, "")
                .trim()

            // 네이버는 공저자를 ^ 또는 |로 잇는다. 역할어(저/지음/옮김…)는 저자마다 붙으므로 나눈 뒤 각자 떼고 다시 잇는다.
            val cleanAuthor = rawAuthor
                .replace(HTML_TAG, "")
                .split('^', '|')
                .map { it.trim().replace(AUTHOR_ROLE_PREFIX, "").replace(AUTHOR_ROLE_SUFFIX, "").trim() }
                .filterNot { it.isEmpty() || AUTHOR_ROLE_ONLY.matches(it) }
                .joinToString(", ")
                .ifBlank { "지은이 미상" }

            val cover = item.optString("image", "")
            list.add(SearchResultBook(cleanTitle, cleanAuthor, 250, cover))
        }
        return list
    }

    /**
     * Google Books API(`/books/v1/volumes`) 응답 파싱. `volumeInfo`가 없는 항목은 건너뛴다.
     * 본문이 유효한 JSON이 아니면 `JSONException`을 전파한다(호출자가 처리).
     */
    fun parseGoogleBooks(body: String): List<SearchResultBook> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SearchResultBook>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val volumeInfo = item.optJSONObject("volumeInfo") ?: continue
            val titleStr = volumeInfo.optString("title", "알 수 없는 제목")
            val authorsArray = volumeInfo.optJSONArray("authors")
            val authorStr = if (authorsArray != null && authorsArray.length() > 0) {
                authorsArray.getString(0)
            } else "지은이 미상"
            val pageCount = volumeInfo.optInt("pageCount", 250)
            val imageLinks = volumeInfo.optJSONObject("imageLinks")
            // optString은 결측 시 null이 아닌 ""를 돌려주므로 isNotBlank로 걸러야 smallThumbnail 폴백이 동작한다.
            val thumb = listOf("thumbnail", "smallThumbnail")
                .map { imageLinks?.optString(it, "").orEmpty() }
                .firstOrNull { it.isNotBlank() }
                ?.replace("http://", "https://") ?: ""
            list.add(SearchResultBook(titleStr, authorStr, pageCount, thumb))
        }
        return list
    }
}
