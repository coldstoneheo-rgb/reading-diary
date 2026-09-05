package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureKeyManager {
    private const val PREFS_FILE = "secure_user_prefs"
    private const val KEY_NAVER_CLIENT_ID = "naver_client_id"
    private const val KEY_NAVER_CLIENT_SECRET = "naver_client_secret"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    private fun getEncryptedPrefs(context: Context) = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveNaverClientId(context: Context, clientId: String) {
        getEncryptedPrefs(context).edit().putString(KEY_NAVER_CLIENT_ID, clientId).apply()
    }

    fun getNaverClientId(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_NAVER_CLIENT_ID, "") ?: ""
    }

    fun saveNaverClientSecret(context: Context, clientSecret: String) {
        getEncryptedPrefs(context).edit().putString(KEY_NAVER_CLIENT_SECRET, clientSecret).apply()
    }

    fun getNaverClientSecret(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_NAVER_CLIENT_SECRET, "") ?: ""
    }

    /**
     * 사용자가 직접 등록한 Gemini API 키. 앱 바이너리에는 절대 포함하지 않으며(ADR-001),
     * 이 저장소가 유일한 출처다. 등록된 키가 없으면 빈 문자열.
     */
    fun saveGeminiApiKey(context: Context, apiKey: String) {
        getEncryptedPrefs(context).edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }
}
