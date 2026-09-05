package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.SecureKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 책 페이지 이미지에서 밑줄/형광펜 구절을 추출하는 Gemini(vision) 클라이언트.
 *
 * 키 정책(docs/adr/ADR-001): Gemini 키는 앱 바이너리에 포함하지 않는다. 유일한 출처는
 * 사용자가 직접 등록해 기기 암호화 저장소에 보관한 [SecureKeyManager.getGeminiApiKey]다.
 * 키가 없으면 네트워크 호출 없이 예시 문장으로 폴백한다.
 */
object GeminiApiClient {
    private const val TAG = "LocalOcrAnalyzer"
    // ADR-001: 2단계 착수 시 이 모델 ID가 실재하는지 최우선 확인
    private const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /**
     * Extracts text from a captured/selected page image.
     * Uses real Gemini API if the user has registered an API key, falls back to simulation otherwise.
     */
    suspend fun extractUnderlinedText(context: Context, bitmap: Bitmap, bookTitle: String = ""): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initiating OCR analyzer for book: $bookTitle")

        // 1. Resolve Gemini API key from the user's encrypted on-device store (never from BuildConfig)
        val apiKey = try {
            SecureKeyManager.getGeminiApiKey(context)
        } catch (e: Exception) {
            ""
        }.trim()

        // 헤더에 실을 수 없는 문자가 섞인 키는 OkHttp가 예외 메시지에 값을 통째로 넣으므로 아예 시도하지 않는다
        val isKeyValid = apiKey.isNotEmpty() && SecureKeyManager.isHeaderSafe(apiKey)

        if (isKeyValid) {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(45, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(45, TimeUnit.SECONDS)
                    .build()

                // Compress bitmap to JPEG byte array and encode to Base64
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                // Build request JSON with native org.json primitives
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }

                // Highly precise system prompt optimized for book page fragment extracts
                val prompt = "이 이미지에서 형광펜으로 칠해져 있거나 연필/볼펜/색연필 등으로 밑줄이 그어져 조각으로 자른 한국어 도서 본문 구절(문장)을 찾아 한글 텍스트로 보정 복원하여 정확히 추출해 주세요. 다른 분석 설명, 추상적인 뜻풀이, 제목, 작가 등 사족은 일절 없이, 오직 이미지에서 직접 형광펜/필기구 밑줄 표시가 장식된 책 본문 글씨(문장)만 그대로 완벽하게 텍스트로 똑같이 출력하여 응답하십시오."

                val partText = JSONObject().apply {
                    put("text", prompt)
                }

                val partImage = JSONObject().apply {
                    put("inlineData", inlineData)
                }

                val content = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(partText)
                        put(partImage)
                    })
                }

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(content)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = requestJson.toString().toRequestBody(mediaType)

                // 키는 URL 쿼리가 아니라 헤더로 보낸다(로그·프록시에 URL이 남아도 키가 새지 않도록)
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body)
                    .build()

                Log.d(TAG, "Requesting Gemini API ($MODEL) with real-time OCR...")
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Gemini API response received.")
                        val jsonResponse = JSONObject(responseBody)
                        val candidates = jsonResponse.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val responseContent = candidates.getJSONObject(0).getJSONObject("content")
                            val parts = responseContent.getJSONArray("parts")
                            if (parts.length() > 0) {
                                val textResult = parts.getJSONObject(0).getString("text").trim()
                                // Strip any unwanted markdown or quotation marks
                                val cleanText = textResult
                                    .removePrefix("\"")
                                    .removeSuffix("\"")
                                    .removePrefix("`")
                                    .removeSuffix("`")
                                    .trim()
                                
                                if (cleanText.isNotEmpty()) {
                                    return@withContext cleanText
                                }
                            }
                        }
                    } else {
                        // 응답 본문은 신뢰할 수 없는 외부 입력이라 코드와 앞부분만 남긴다
                        val errPreview = (response.body?.string() ?: "").take(200)
                        Log.e(TAG, "Gemini API rejected request: Code ${response.code} Body(200): $errPreview")
                    }
                }
            } catch (e: Exception) {
                // 예외 메시지에 헤더 값(키)이 실릴 수 있으므로 종류만 기록한다
                Log.e(TAG, "Exception during real Gemini API execution: ${e::class.java.simpleName}")
            }
        }

        // 2. 폴백. 키가 없으면 시뮬레이션(처리 지연 연출), 키가 있는데 실패했으면 지연 없이 실패 사실을 알린다
        if (!isKeyValid) delay(1200)
        val normalizedTitle = bookTitle.trim().lowercase()
        val simulatedText = when {
            normalizedTitle.contains("데미안") || normalizedTitle.contains("demian") -> {
                "태어나려는 자는 하나의 세계를 깨뜨려야 한다. 새는 신을 향해 날아간다. 그 신의 이름은 아브락사스다."
            }
            normalizedTitle.contains("돈의") || normalizedTitle.contains("돈") -> {
                "돈은 스스로 생각하는 주체적 인격체와 같다. 자기를 존중하면 그 사람을 지키고, 무시하면 기회를 보아 빠져나간다."
            }
            normalizedTitle.contains("사피엔스") || normalizedTitle.contains("sapiens") -> {
                "우리가 인지혁명이라고 부르는 이 혁명 덕분에 호모 사피엔스는 가상의 실재를 창조해 내고 협동할 수 있게 되었다."
            }
            normalizedTitle.contains("머니") || normalizedTitle.contains("재테크") || normalizedTitle.contains("세이노") -> {
                "투자의 핵심은 이익을 얼마나 많이 내느냐가 아니라, 살아남아서 시간과 복리의 마법을 온전히 누리는 것이다."
            }
            else -> {
                "마음속에 깊이 남는 문장을 발견하고 그것을 기록해 두는 습관은 우리의 독서를 깨어있게 만들고 내면을 풍요롭게 가꾸어 줍니다."
            }
        }

        val hint = if (isKeyValid) {
            "\n\n*(AI 분석 요청이 실패해 예시 문장을 표시합니다. 등록한 Gemini 키, 네트워크, 모델 설정을 확인해 주세요.)*"
        } else {
            "\n\n*(밑줄 구절 인식을 시뮬레이션한 예시 문장입니다. 실제 촬영 사진 분석은 다음 업데이트에서 지원할 예정입니다.)*"
        }
        return@withContext simulatedText + hint
    }
}
