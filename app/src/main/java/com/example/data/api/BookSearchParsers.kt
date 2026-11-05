package com.example.data.api

import org.json.JSONObject

data class SearchResultBook(
    val title: String,
    val author: String,
    /** 응답에 쪽수가 없으면 null. 지어낸 기본값을 넣지 않는다(등록 화면이 그대로 저장한다). */
    val pageCount: Int?,
    val coverUrl: String,
    /** 쪽수를 다른 곳에서 찾을 때 쓴다. 카카오 결과에만 있다. */
    val isbn: String? = null
)

/**
 * 도서 검색 API 응답 본문(JSON 문자열)을 [SearchResultBook] 목록으로 바꾸는 순수 함수 모음.
 * 네트워크 호출과 분리되어 있어 오프라인 단위 테스트가 가능하다.
 */
object BookSearchParsers {

    /** 검색어 강조 태그만 뗀다. 제목의 꺾쇠(«<어린 왕자> 깊이 읽기»)는 책 이름의 일부다. */
    private val HIGHLIGHT_TAG = Regex("</?b>", RegexOption.IGNORE_CASE)
    private val TITLE_EDITION_SUFFIX =
        Regex("\\s*[\\(\\[](반양장본|양장본|개정판|제\\d+판|Paperback|Hardcover|소설|단행본|Korean Edition|번역본)[\\)\\]]")
    private val AUTHOR_ROLE_PREFIX = Regex("^(저자|지은이|글|그림|옮김)\\s*:\\s*")
    private val AUTHOR_ROLE_SUFFIX = Regex("\\s+(저|지음|글|그림|역|옮김|편|편저|감수)$")
    /** 역할어만 남은 조각(예: author가 "지음")은 저자가 아니다. */
    private val AUTHOR_ROLE_ONLY = Regex("^(저|지음|글|그림|역|옮김|편|편저|감수)$")

    private val ISBN_10 = Regex("^[0-9]{9}[0-9Xx]$")

    /**
     * 카카오 책 검색 API(`/v3/search/book`) 응답 파싱. `documents`가 없으면 빈 목록.
     * 본문이 유효한 JSON이 아니거나 항목이 객체가 아니면 `JSONException`을 전파한다(호출자가 처리).
     */
    fun parseKakaoBooks(body: String): List<SearchResultBook> {
        val documents = JSONObject(body).optJSONArray("documents") ?: return emptyList()
        val list = mutableListOf<SearchResultBook>()
        for (i in 0 until documents.length()) {
            val item = documents.getJSONObject(i)

            val cleanTitle = item.text("title")
                .replace(HIGHLIGHT_TAG, "")
                .replace(TITLE_EDITION_SUFFIX, "")
                .trim()
                .ifBlank { "알 수 없는 제목" }

            val authors = item.optJSONArray("authors")
            val cleanAuthor = (0 until (authors?.length() ?: 0))
                .map { index -> if (authors!!.isNull(index)) "" else authors.optString(index, "") }
                .map { it.replace(HIGHLIGHT_TAG, "").trim().replace(AUTHOR_ROLE_PREFIX, "").replace(AUTHOR_ROLE_SUFFIX, "").trim() }
                .filterNot { it.isEmpty() || AUTHOR_ROLE_ONLY.matches(it) }
                .joinToString(", ")
                .ifBlank { "지은이 미상" }

            // 표지 주소 앞머리만 https로 올린다. 쿼리에 든 원본 주소(fname=http://...)는 건드리지 않는다.
            val thumbnail = item.text("thumbnail").trim()
            val cover = if (thumbnail.startsWith("http://")) "https://" + thumbnail.removePrefix("http://") else thumbnail
            // 카카오 응답에는 쪽수가 없다.
            list.add(SearchResultBook(cleanTitle, cleanAuthor, null, cover, parseKakaoIsbn(item.text("isbn"))))
        }
        return list
    }

    /** 카카오 isbn은 "10자리 13자리"처럼 공백으로 이어지고 한쪽이 빌 수 있다. 13자리를 먼저 쓴다. */
    private fun parseKakaoIsbn(raw: String): String? {
        val parts = raw.trim().split(Regex("\\s+"))
        return parts.firstNotNullOfOrNull { Isbn.fromBarcode(it) } ?: parts.firstOrNull { ISBN_10.matches(it) }
    }

    /** Android의 optString은 JSON null을 문자열 "null"로 돌려주므로 먼저 거른다. */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")

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
            val titleStr = volumeInfo.text("title").ifBlank { "알 수 없는 제목" }
            val authorsArray = volumeInfo.optJSONArray("authors")
            val authorStr = if (authorsArray != null && authorsArray.length() > 0 && !authorsArray.isNull(0)) {
                authorsArray.getString(0)
            } else "지은이 미상"
            val pageCount = volumeInfo.optInt("pageCount", 0).takeIf { it > 0 }
            val imageLinks = volumeInfo.optJSONObject("imageLinks")
            // optString은 결측 시 null이 아닌 ""를 돌려주므로 isNotBlank로 걸러야 smallThumbnail 폴백이 동작한다.
            val thumb = listOf("thumbnail", "smallThumbnail")
                .map { imageLinks?.text(it).orEmpty() }
                .firstOrNull { it.isNotBlank() }
                ?.replace("http://", "https://") ?: ""
            list.add(SearchResultBook(titleStr, authorStr, pageCount, thumb))
        }
        return list
    }
}
