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
        private const val KEY_AUDIO_SOURCE_MODE = "audio_source_mode"
        private const val KEY_CUSTOM_FOLDERS = "custom_folders"
        private const val KEY_DISMISSED_UPDATE_VERSION = "dismissed_update_version"
        private const val KEY_LAST_UPDATE_CHECK_TIMESTAMP = "last_update_check_timestamp"
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

    /**
     * Modalità di selezione cartella/fonti musicali (default INTELLIGENT).
     */
    fun getAudioSourceMode(): AudioSourceMode {
        val name = prefs.getString(KEY_AUDIO_SOURCE_MODE, AudioSourceMode.INTELLIGENT.name)
            ?: AudioSourceMode.INTELLIGENT.name
        return try {
            AudioSourceMode.valueOf(name)
        } catch (_: Exception) {
            AudioSourceMode.INTELLIGENT
        }
    }

    fun setAudioSourceMode(mode: AudioSourceMode) {
        prefs.edit().putString(KEY_AUDIO_SOURCE_MODE, mode.name).apply()
    }

    /**
     * Insieme delle cartelle personalizzate selezionate dall'utente (percorsi relativi).
     */
    fun getCustomFolders(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_FOLDERS, emptySet())?.toSet() ?: emptySet()
    }

    fun setCustomFolders(folders: Set<String>) {
        prefs.edit().putStringSet(KEY_CUSTOM_FOLDERS, folders.toSet()).apply()
    }

    fun addCustomFolder(folderPath: String) {
        val current = getCustomFolders().toMutableSet()
        current.add(folderPath)
        setCustomFolders(current)
    }

    fun removeCustomFolder(folderPath: String) {
        val current = getCustomFolders().toMutableSet()
        current.remove(folderPath)
        setCustomFolders(current)
    }

    /**
     * Versione di aggiornamento ignorata dall'utente all'avvio.
     */
    fun getDismissedUpdateVersion(): String? {
        return prefs.getString(KEY_DISMISSED_UPDATE_VERSION, null)
    }

    fun setDismissedUpdateVersion(version: String) {
        prefs.edit().putString(KEY_DISMISSED_UPDATE_VERSION, version).apply()
    }

    /**
     * Timestamp dell'ultimo controllo aggiornamenti eseguito.
     */
    fun getLastUpdateCheckTimestamp(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK_TIMESTAMP, 0L)
    }

    fun setLastUpdateCheckTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_TIMESTAMP, timestamp).apply()
    }
}
