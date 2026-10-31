package com.example.musicplayer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Modalità tema dell'applicazione.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("Segui il sistema"),
    LIGHT("Tema chiaro"),
    DARK("Tema scuro")
}

/**
 * Gestore centralizzato e persistente delle preferenze dell'applicazione
 * salvate in SharedPreferences.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTOPLAY_ENABLED = "autoplay_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }

    /**
     * Autoplay continuo (Radio Mode): default true.
     */
    fun isAutoplayEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_ENABLED, enabled).apply()
    }

    /**
     * Modalità tema (default SYSTEM).
     */
    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /**
     * Colori dinamici Material You (default false per preservare l'identità cromatica dell'icona).
     */
    fun isDynamicColorEnabled(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
