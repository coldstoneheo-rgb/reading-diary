package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.example.data.SecureKeyManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 도서 검색 한 번의 결과.
 * @param error 결과가 없을 때 사용자에게 보일 원인. null이면 실패가 아니라 «결과 없음»이다.
 * @param notice 결과는 있지만 카카오가 실패해 Google 결과를 보여 줄 때 그 원인 1줄.
 * @param searchedWithKakao 카카오 키가 있어 카카오를 먼저 시도했는가. 안내 문구가 경로를 구분할 때 쓴다.
 */
data class BookSearchOutcome(
    val books: List<SearchResultBook>,
    val error: String?,
    val notice: String?,
    val searchedWithKakao: Boolean
)

/**
 * 카카오 책 검색 1차 + Google Books 폴백.
 * 폴백 조건: 키 없음 · 카카오 0건 · 카카오 실패. 실패 원인은 숨기지 않고 [BookSearchOutcome]에 담는다.
 * 키 값과 예외 메시지(URL·헤더가 실릴 수 있음)는 결과 문구에 넣지 않는다.
 */
class BookSearchClient(private val http: OkHttpClient = OkHttpClient()) {

    private class Attempt(val books: List<SearchResultBook>, val error: String?)

    suspend fun search(query: String, kakaoKey: String?): BookSearchOutcome {
        val isbn = Isbn.fromQuery(query)
        if (kakaoKey == null) {
            val google = searchGoogle(query, isbn)
            return BookSearchOutcome(google.books, google.error, null, searchedWithKakao = false)
        }

        val kakao = searchKakao(query, isbn, kakaoKey)
        if (kakao.books.isNotEmpty()) return BookSearchOutcome(kakao.books, null, null, searchedWithKakao = true)

        // 입력이 바뀌어 취소된 검색의 검색어를 두 번째 서비스로 보내지 않는다.
        currentCoroutineContext().ensureActive()
        val google = searchGoogle(query, isbn)
        return when {
            kakao.error == null -> BookSearchOutcome(google.books, null, null, searchedWithKakao = true)
            google.books.isNotEmpty() -> BookSearchOutcome(
                google.books, null, "${kakao.error} 이번 결과는 Google 도서에서 가져왔습니다.", searchedWithKakao = true
            )
            google.error == null -> BookSearchOutcome(emptyList(), kakao.error, null, searchedWithKakao = true)
            else -> BookSearchOutcome(emptyList(), "${kakao.error}\n${google.error}", null, searchedWithKakao = true)
        }
    }

    private fun searchKakao(query: String, isbn: String?, key: String): Attempt {
        // 비ASCII가 섞인 키를 헤더에 넣으면 OkHttp 예외 메시지에 값이 통째로 실린다(CLAUDE.md 안전 규칙).
        if (!SecureKeyManager.isHeaderSafe(key)) {
            return Attempt(emptyList(), "등록된 카카오 REST 키에 쓸 수 없는 문자가 섞여 있습니다. 키를 확인해 주세요.")
        }
        val url = KAKAO_BOOK_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", isbn ?: query)
            .addQueryParameter("size", "10")
            // 일반 검색은 target을 빼야 제목·저자를 함께 찾는다.
            .apply { if (isbn != null) addQueryParameter("target", "isbn") }
            .build()
        val request = Request.Builder().url(url).addHeader("Authorization", "KakaoAK $key").build()
        return attempt(
            request = request,
            parse = BookSearchParsers::parseKakaoBooks,
            httpError = ::kakaoHttpErrorText,
            badResponse = "카카오 응답을 해석할 수 없습니다.",
            serviceName = "카카오"
        )
    }

    private fun searchGoogle(query: String, isbn: String?): Attempt {
        val url = GOOGLE_BOOKS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", isbn?.let { "isbn:$it" } ?: query)
            .addQueryParameter("maxResults", "10")
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        return attempt(
            request = request,
            parse = BookSearchParsers::parseGoogleBooks,
            httpError = ::googleHttpErrorText,
            badResponse = "Google 도서 응답을 해석할 수 없습니다.",
            serviceName = "Google 도서"
        )
    }

    private fun attempt(
        request: Request,
        parse: (String) -> List<SearchResultBook>,
        httpError: (Int) -> String,
        badResponse: String,
        serviceName: String
    ): Attempt = try {
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) Attempt(parse(response.body?.string().orEmpty()), null)
            else Attempt(emptyList(), httpError(response.code))
        }
    } catch (e: JSONException) {
        Attempt(emptyList(), badResponse)
    } catch (e: Exception) {
        // 예외 메시지에는 URL·헤더가 실릴 수 있어 종류만 보여 준다.
        Attempt(emptyList(), "$serviceName ${connectionFailureText(e)}")
    }

    private companion object {
        const val KAKAO_BOOK_URL = "https://dapi.kakao.com/v3/search/book"
        const val GOOGLE_BOOKS_URL = "https://www.googleapis.com/books/v1/volumes"
    }
}

internal fun kakaoHttpErrorText(code: Int): String = when (code) {
    401 -> "카카오 REST 키를 확인해 주세요. (HTTP 401)"
    403 -> "카카오 앱에 검색 API가 활성화되어 있지 않습니다. (HTTP 403)"
    429 -> "카카오 검색의 일일 한도를 넘었습니다. (HTTP 429)"
    else -> "카카오 검색에 실패했습니다. (HTTP $code)"
}

internal fun googleHttpErrorText(code: Int): String =
    if (code == 429) "공용 Google 도서 검색의 호출 한도를 넘었습니다. (HTTP 429)"
    else "Google 도서 검색에 실패했습니다. (HTTP $code)"

/** 연결 실패를 사용자 행동으로 번역한다. 예외 메시지(URL·헤더가 실릴 수 있음)는 쓰지 않는다. */
internal fun connectionFailureText(e: Throwable): String = when (e) {
    is UnknownHostException -> "검색에 연결할 수 없습니다. 인터넷 연결을 확인해 주세요."
    is SocketTimeoutException -> "검색 응답이 늦어 중단했습니다. 잠시 후 다시 시도해 주세요."
    else -> "검색에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
}

private val KAKAO_KEY_PLACEHOLDERS = setOf(
    "KAKAO_REST_API_KEY", "KAKAO_REST_API_KEY_PLACEHOLDER", "MY_KAKAO_REST_API_KEY"
)

/** 앞선 출처부터 훑어 쓸 수 있는 첫 키를 고른다. 공백·따옴표는 떼고, 빈 값·자리표시자는 없는 것으로 친다. */
internal fun pickKakaoRestKey(vararg candidates: String): String? =
    candidates.asSequence()
        .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
        .firstOrNull { it.isNotBlank() && it !in KAKAO_KEY_PLACEHOLDERS }

/**
 * 카카오 REST 키. 출처 우선순위(CLAUDE.md): ① 기기 저장소 → ② 빌드 셸 환경변수 → ③ .env.
 * 없으면 null — 호출자는 공용 Google 도서로 검색한다. 값은 로그에 남기지 않는다.
 */
internal fun resolveKakaoRestKey(context: Context): String? = pickKakaoRestKey(
    runCatching { SecureKeyManager.getKakaoRestKey(context) }.getOrDefault(""),
    BuildConfig.ENV_KAKAO_REST_API_KEY,
    BuildConfig.KAKAO_REST_API_KEY
)
