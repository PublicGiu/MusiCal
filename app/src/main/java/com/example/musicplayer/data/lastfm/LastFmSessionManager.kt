package com.example.musicplayer.data.lastfm

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestore della sessione e credenziali locali per Last.fm.
 */
class LastFmSessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lastfm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_SHARED_SECRET = "custom_shared_secret"
    }

    init {
        LastFmConfig.setCustomCredentials(
            apiKey = getCustomApiKey(),
            sharedSecret = getCustomSharedSecret()
        )
    }

    fun saveCustomApiCredentials(apiKey: String, sharedSecret: String) {
        val cleanKey = apiKey.trim()
        val cleanSecret = sharedSecret.trim()
        prefs.edit()
            .putString(KEY_CUSTOM_API_KEY, cleanKey)
            .putString(KEY_CUSTOM_SHARED_SECRET, cleanSecret)
            .apply()
        LastFmConfig.setCustomCredentials(cleanKey, cleanSecret)
    }

    fun clearCustomApiCredentials() {
        prefs.edit()
            .remove(KEY_CUSTOM_API_KEY)
            .remove(KEY_CUSTOM_SHARED_SECRET)
            .apply()
        LastFmConfig.setCustomCredentials(null, null)
    }

    fun getCustomApiKey(): String? = prefs.getString(KEY_CUSTOM_API_KEY, null)

    fun getCustomSharedSecret(): String? = prefs.getString(KEY_CUSTOM_SHARED_SECRET, null)

    fun saveSession(username: String, sessionKey: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_SESSION_KEY, sessionKey)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_SESSION_KEY)
            .apply()
    }

    fun getSessionKey(): String? = prefs.getString(KEY_SESSION_KEY, null)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun isLoggedIn(): Boolean {
        val sk = getSessionKey()
        return !sk.isNullOrBlank()
    }
}
