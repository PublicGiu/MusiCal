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
    }

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
