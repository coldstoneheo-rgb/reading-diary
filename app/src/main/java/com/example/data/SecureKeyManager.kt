package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureKeyManager {
    /** 백업 제외 규칙(res/xml)과 테스트가 이 이름을 참조한다. 바꾸면 backup_rules/data_extraction_rules도 같이 바꿔야 한다. */
    internal const val PREFS_FILE = "secure_user_prefs"
    private const val KEY_NAVER_CLIENT_ID = "naver_client_id"
    private const val KEY_NAVER_CLIENT_SECRET = "naver_client_secret"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    /** API 키에 허용하는 문자: 출력 가능한 ASCII. HTTP 헤더에 그대로 실리므로 그 외 문자는 거부한다. */
    private val PRINTABLE_ASCII = Regex("^[\\x20-\\x7E]+$")

    private fun encryptedPrefsOrNull(context: Context): SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    /** 암호화 저장소가 열리지 않으면 같은 파일명의 평문 prefs로 폴백한다(네이버 키 기존 동작 유지). */
    private fun getEncryptedPrefs(context: Context): SharedPreferences =
        encryptedPrefsOrNull(context) ?: context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun put(context: Context, key: String, value: String) {
        getEncryptedPrefs(context).edit().putString(key, value).apply()
    }

    private fun get(context: Context, key: String): String =
        getEncryptedPrefs(context).getString(key, "") ?: ""

    fun saveNaverClientId(context: Context, clientId: String) = put(context, KEY_NAVER_CLIENT_ID, clientId)

    fun getNaverClientId(context: Context): String = get(context, KEY_NAVER_CLIENT_ID)

    fun saveNaverClientSecret(context: Context, clientSecret: String) = put(context, KEY_NAVER_CLIENT_SECRET, clientSecret)

    fun getNaverClientSecret(context: Context): String = get(context, KEY_NAVER_CLIENT_SECRET)

    /**
     * 사용자가 직접 등록한 Gemini API 키. 앱 바이너리에는 절대 포함하지 않으며(ADR-001), 이 저장소가 유일한 출처다.
     *
     * 유료 키이므로 **fail-closed**: 암호화 저장소를 열 수 없으면 평문으로 저장하지 않고 `false`를 돌려준다.
     * 출력 가능한 ASCII가 아닌 문자(IME가 섞는 전각 문자·제로폭 공백 등)가 있으면 헤더 조립 시 예외 메시지에
     * 키가 실릴 수 있으므로 저장 단계에서 거부한다.
     *
     * @return 저장 성공 여부
     */
    fun saveGeminiApiKey(context: Context, apiKey: String): Boolean {
        val normalized = apiKey.trim()
        if (!PRINTABLE_ASCII.matches(normalized)) return false
        val prefs = encryptedPrefsOrNull(context) ?: return false
        prefs.edit().putString(KEY_GEMINI_API_KEY, normalized).apply()
        return true
    }

    /** 등록된 Gemini 키. 없거나 암호화 저장소를 열 수 없으면 빈 문자열(평문 폴백을 읽지 않는다). */
    fun getGeminiApiKey(context: Context): String =
        encryptedPrefsOrNull(context)?.getString(KEY_GEMINI_API_KEY, "") ?: ""

    /** 등록된 Gemini 키 삭제. 암호화 저장소를 열 수 없으면 아무것도 하지 않는다(평문에 저장된 적이 없으므로). */
    fun clearGeminiApiKey(context: Context) {
        encryptedPrefsOrNull(context)?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
    }

    /** 헤더에 실어도 안전한 키인지(출력 가능한 ASCII만). */
    fun isHeaderSafe(value: String): Boolean = PRINTABLE_ASCII.matches(value)
}
