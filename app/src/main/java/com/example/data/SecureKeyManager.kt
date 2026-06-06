package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureKeyManager {
    private const val PREFS_FILE = "secure_user_prefs"
    private const val KEY_GEMINI_API = "gemini_api_key"
    private const val KEY_NAVER_CLIENT_ID = "naver_client_id"
    private const val KEY_NAVER_CLIENT_SECRET = "naver_client_secret"

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

    fun saveGeminiApiKey(context: Context, key: String) {
        getEncryptedPrefs(context).edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_GEMINI_API, "") ?: ""
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
}
