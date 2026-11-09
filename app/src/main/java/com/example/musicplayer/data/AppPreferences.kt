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

        // Ripristino ultima sessione di riproduzione
        private const val KEY_LAST_PLAYED_TRACK_ID = "last_played_track_id"
        private const val KEY_LAST_PLAYBACK_POSITION_MS = "last_playback_position_ms"
        private const val KEY_LAST_REPEAT_MODE = "last_repeat_mode"
        private const val KEY_LAST_SHUFFLE_ENABLED = "last_shuffle_enabled"
        private const val KEY_LAST_CONTEXT_TYPE = "last_context_type"
        private const val KEY_LAST_CONTEXT_ID = "last_context_id"
        private const val KEY_LAST_CONTEXT_NAME = "last_context_name"

        // Equalizzatore ed effetti sonori
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        private const val KEY_BASS_BOOST = "bass_boost"
        private const val KEY_VIRTUALIZER = "virtualizer"

        // File duplicati ignorati dall'utente
        private const val KEY_IGNORED_DUPLICATE_KEYS = "ignored_duplicate_keys"

        // Lingua dell'applicazione (per-app language)
        private const val KEY_APP_LANGUAGE = "app_language"
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
     * Lingua dell'applicazione (default SYSTEM).
     */
    fun getAppLanguage(): AppLanguage {
        val name = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return try {
            AppLanguage.valueOf(name)
        } catch (_: Exception) {
            AppLanguage.SYSTEM
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
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

    /**
     * Salvataggio e recupero dello stato dell'ultima sessione di riproduzione.
     */
    fun saveLastPlaybackState(
        trackId: Long,
        positionMs: Long,
        repeatMode: Int,
        shuffleEnabled: Boolean,
        contextType: String? = null,
        contextId: String? = null,
        contextName: String? = null
    ) {
        prefs.edit()
            .putLong(KEY_LAST_PLAYED_TRACK_ID, trackId)
            .putLong(KEY_LAST_PLAYBACK_POSITION_MS, positionMs)
            .putInt(KEY_LAST_REPEAT_MODE, repeatMode)
            .putBoolean(KEY_LAST_SHUFFLE_ENABLED, shuffleEnabled)
            .putString(KEY_LAST_CONTEXT_TYPE, contextType)
            .putString(KEY_LAST_CONTEXT_ID, contextId)
            .putString(KEY_LAST_CONTEXT_NAME, contextName)
            .apply()
    }

    fun getLastPlayedTrackId(): Long = prefs.getLong(KEY_LAST_PLAYED_TRACK_ID, -1L)
    fun getLastPlaybackPositionMs(): Long = prefs.getLong(KEY_LAST_PLAYBACK_POSITION_MS, 0L)
    fun getLastRepeatMode(): Int = prefs.getInt(KEY_LAST_REPEAT_MODE, 0)
    fun getLastShuffleEnabled(): Boolean = prefs.getBoolean(KEY_LAST_SHUFFLE_ENABLED, false)
    fun getLastContextType(): String? = prefs.getString(KEY_LAST_CONTEXT_TYPE, null)
    fun getLastContextId(): String? = prefs.getString(KEY_LAST_CONTEXT_ID, null)
    fun getLastContextName(): String? = prefs.getString(KEY_LAST_CONTEXT_NAME, null)

    /**
     * Preferenze dell'equalizzatore audio ed effetti sonori.
     */
    fun isEqualizerEnabled(): Boolean = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false)
    fun setEqualizerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply()

    fun getEqualizerPreset(): Int = prefs.getInt(KEY_EQUALIZER_PRESET, -1)
    fun setEqualizerPreset(presetIndex: Int) = prefs.edit().putInt(KEY_EQUALIZER_PRESET, presetIndex).apply()

    fun getEqualizerBandLevels(): String? = prefs.getString(KEY_EQUALIZER_BANDS, null)
    fun setEqualizerBandLevels(levelsCsv: String) = prefs.edit().putString(KEY_EQUALIZER_BANDS, levelsCsv).apply()

    fun getBassBoostStrength(): Int = prefs.getInt(KEY_BASS_BOOST, 0)
    fun setBassBoostStrength(strength: Int) = prefs.edit().putInt(KEY_BASS_BOOST, strength).apply()

    fun getVirtualizerStrength(): Int = prefs.getInt(KEY_VIRTUALIZER, 0)
    fun setVirtualizerStrength(strength: Int) = prefs.edit().putInt(KEY_VIRTUALIZER, strength).apply()

    /**
     * Preferenze per i file duplicati ignorati dall'utente (coppie id:id o path|path).
     */
    fun getIgnoredDuplicateKeys(): Set<String> {
        return prefs.getStringSet(KEY_IGNORED_DUPLICATE_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    fun setIgnoredDuplicateKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_IGNORED_DUPLICATE_KEYS, keys.toSet()).apply()
    }

    fun addIgnoredDuplicateKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val current = getIgnoredDuplicateKeys().toMutableSet()
        current.addAll(keys)
        setIgnoredDuplicateKeys(current)
    }

    fun removeIgnoredDuplicateKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val current = getIgnoredDuplicateKeys().toMutableSet()
        current.removeAll(keys.toSet())
        setIgnoredDuplicateKeys(current)
    }

    fun clearIgnoredDuplicateKeys() {
        prefs.edit().remove(KEY_IGNORED_DUPLICATE_KEYS).apply()
    }
}
