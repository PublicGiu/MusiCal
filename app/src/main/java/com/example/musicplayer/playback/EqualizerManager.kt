package com.example.musicplayer.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.musicplayer.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val bandIndex: Int,
    val centerFreqHz: Int,
    val levelMb: Int
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Int = -1,
    val bassBoostStrength: Int = 0,
    val isBassBoostSupported: Boolean = false,
    val virtualizerStrength: Int = 0,
    val isVirtualizerSupported: Boolean = false
)

/**
 * Gestore dell'equalizzatore hardware e degli effetti sonori (Bass Boost e Virtualizer)
 * collegati all'audioSessionId di ExoPlayer.
 */
object EqualizerManager {

    private const val TAG = "EqualizerManager"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var appPreferences: AppPreferences? = null
    private var currentAudioSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context, audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == currentAudioSessionId && equalizer != null && _state.value.isAvailable) {
            return
        }
        val appContext = context.applicationContext ?: context
        val prefs = AppPreferences(appContext)
        appPreferences = prefs

        release()
        currentAudioSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val isEnabled = prefs.isEqualizerEnabled()

            val minLevel = eq.bandLevelRange?.getOrNull(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.getOrNull(1)?.toInt() ?: 1500
            val numBands = eq.numberOfBands.toInt()

            val presetsList = mutableListOf<String>()
            val numPresets = eq.numberOfPresets.toInt()
            for (i in 0 until numPresets) {
                try {
                    presetsList.add(eq.getPresetName(i.toShort()) ?: "Preset $i")
                } catch (_: Exception) {
                    presetsList.add("Preset $i")
                }
            }

            // Inizializzazione BassBoost
            var bbSupported = false
            try {
                val bb = BassBoost(0, audioSessionId)
                bassBoost = bb
                bbSupported = bb.strengthSupported
                val savedStrength = prefs.getBassBoostStrength().coerceIn(0, 1000)
                if (bbSupported) {
                    bb.setStrength(savedStrength.toShort())
                    bb.enabled = isEnabled && savedStrength > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost non supportato su questo dispositivo: ${e.message}")
            }

            // Inizializzazione Virtualizer
            var virtSupported = false
            try {
                val virt = Virtualizer(0, audioSessionId)
                virtualizer = virt
                virtSupported = virt.strengthSupported
                val savedStrength = prefs.getVirtualizerStrength().coerceIn(0, 1000)
                if (virtSupported) {
                    virt.setStrength(savedStrength.toShort())
                    virt.enabled = isEnabled && savedStrength > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer non supportato su questo dispositivo: ${e.message}")
            }

            eq.enabled = isEnabled

            // Caricamento bande salvate o default
            val savedPreset = prefs.getEqualizerPreset()
            val savedBandsCsv = prefs.getEqualizerBandLevels()
            val savedBandsMap = savedBandsCsv?.split(",")?.mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) parts[0].toIntOrNull() to parts[1].toIntOrNull() else null
            }?.filter { it.first != null && it.second != null }?.associate { it.first!! to it.second!! } ?: emptyMap()

            if (savedPreset in 0 until numPresets) {
                try {
                    eq.usePreset(savedPreset.toShort())
                } catch (_: Exception) {}
            } else if (savedBandsMap.isNotEmpty()) {
                for (b in 0 until numBands) {
                    val level = savedBandsMap[b]
                    if (level != null) {
                        try {
                            eq.setBandLevel(b.toShort(), level.coerceIn(minLevel, maxLevel).toShort())
                        } catch (_: Exception) {}
                    }
                }
            }

            val bandsList = mutableListOf<EqualizerBand>()
            for (b in 0 until numBands) {
                val freq = try { (eq.getCenterFreq(b.toShort()) / 1000) } catch (_: Exception) { 0 }
                val level = try { eq.getBandLevel(b.toShort()).toInt() } catch (_: Exception) { 0 }
                bandsList.add(EqualizerBand(b, freq, level))
            }

            _state.value = EqualizerState(
                isAvailable = true,
                isEnabled = isEnabled,
                bands = bandsList,
                minLevelMb = minLevel,
                maxLevelMb = maxLevel,
                presets = presetsList,
                currentPresetIndex = savedPreset,
                bassBoostStrength = prefs.getBassBoostStrength(),
                isBassBoostSupported = bbSupported,
                virtualizerStrength = prefs.getVirtualizerStrength(),
                isVirtualizerSupported = virtSupported
            )
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile inizializzare l'equalizzatore: ${e.message}")
            _state.value = EqualizerState(isAvailable = false)
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        try {
            eq.enabled = enabled
            bassBoost?.enabled = (enabled && (_state.value.bassBoostStrength > 0))
            virtualizer?.enabled = (enabled && (_state.value.virtualizerStrength > 0))
            appPreferences?.setEqualizerEnabled(enabled)
            _state.value = _state.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Errore abilitazione equalizzatore", e)
        }
    }

    @Synchronized
    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val eq = equalizer ?: return
        try {
            val clamped = levelMb.coerceIn(_state.value.minLevelMb, _state.value.maxLevelMb).toShort()
            eq.setBandLevel(bandIndex.toShort(), clamped)

            val updatedBands = _state.value.bands.map {
                if (it.bandIndex == bandIndex) it.copy(levelMb = clamped.toInt()) else it
            }

            val levelsCsv = updatedBands.joinToString(",") { "${it.bandIndex}:${it.levelMb}" }
            appPreferences?.setEqualizerBandLevels(levelsCsv)
            appPreferences?.setEqualizerPreset(-1)

            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPresetIndex = -1 // Passa a personalizzato
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore impostazione banda $bandIndex", e)
        }
    }

    @Synchronized
    fun usePreset(presetIndex: Int) {
        val eq = equalizer ?: return
        try {
            if (presetIndex in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(presetIndex.toShort())
                appPreferences?.setEqualizerPreset(presetIndex)

                val updatedBands = _state.value.bands.map {
                    val lvl = eq.getBandLevel(it.bandIndex.toShort()).toInt()
                    it.copy(levelMb = lvl)
                }
                val levelsCsv = updatedBands.joinToString(",") { "${it.bandIndex}:${it.levelMb}" }
                appPreferences?.setEqualizerBandLevels(levelsCsv)

                _state.value = _state.value.copy(
                    bands = updatedBands,
                    currentPresetIndex = presetIndex
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore impostazione preset $presetIndex", e)
        }
    }

    @Synchronized
    fun setBassBoostStrength(strength: Int) {
        val bb = bassBoost ?: return
        try {
            val clamped = strength.coerceIn(0, 1000).toShort()
            bb.setStrength(clamped)
            bb.enabled = (_state.value.isEnabled && clamped > 0)
            appPreferences?.setBassBoostStrength(clamped.toInt())
            _state.value = _state.value.copy(bassBoostStrength = clamped.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Errore BassBoost", e)
        }
    }

    @Synchronized
    fun setVirtualizerStrength(strength: Int) {
        val virt = virtualizer ?: return
        try {
            val clamped = strength.coerceIn(0, 1000).toShort()
            virt.setStrength(clamped)
            virt.enabled = (_state.value.isEnabled && clamped > 0)
            appPreferences?.setVirtualizerStrength(clamped.toInt())
            _state.value = _state.value.copy(virtualizerStrength = clamped.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Errore Virtualizer", e)
        }
    }

    @Synchronized
    fun resetToFlat() {
        val eq = equalizer ?: return
        try {
            val updatedBands = _state.value.bands.map {
                eq.setBandLevel(it.bandIndex.toShort(), 0)
                it.copy(levelMb = 0)
            }
            appPreferences?.setEqualizerBandLevels(updatedBands.joinToString(",") { "${it.bandIndex}:0" })
            appPreferences?.setEqualizerPreset(-1)
            setBassBoostStrength(0)
            setVirtualizerStrength(0)

            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                bassBoostStrength = 0,
                virtualizerStrength = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore reset equalizzatore", e)
        }
    }

    @Synchronized
    fun release() {
        currentAudioSessionId = 0
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (_: Exception) {}
        virtualizer = null
    }
}
