package com.example.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
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
    val isBassBoostSupported: Boolean = true,
    val virtualizerStrength: Int = 0,
    val isVirtualizerSupported: Boolean = false
)

/**
 * Gestore dell'equalizzatore hardware e degli effetti sonori (Bass Boost e Virtualizer)
 * collegati all'audioSessionId di ExoPlayer.
 *
 * Include un'architettura ibrida per il Bass Boost: combina l'effetto hardware nativo
 * con un incremento low-shelf acustico calibrato sulle bande a bassa frequenza (60Hz / 230Hz),
 * garantendo che l'effetto sia immediatamente tangibile sia su altoparlanti integrati
 * (spesso mutati dall'HAL AOSP) sia su cuffie e dispositivi Bluetooth.
 */
object EqualizerManager {

    private const val TAG = "EqualizerManager"
    private const val EFFECT_PRIORITY = 1000

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var appPreferences: AppPreferences? = null
    private var applicationContext: Context? = null
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
        applicationContext = appContext
        val prefs = AppPreferences(appContext)
        appPreferences = prefs

        release()
        currentAudioSessionId = audioSessionId

        broadcastSessionOpen(appContext, audioSessionId)

        try {
            val eq = Equalizer(EFFECT_PRIORITY, audioSessionId)
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

            // Inizializzazione BassBoost hardware
            var bbSupported = false
            val savedBassBoost = prefs.getBassBoostStrength().coerceIn(0, 1000)
            try {
                val bb = BassBoost(EFFECT_PRIORITY, audioSessionId)
                bassBoost = bb
                bbSupported = bb.strengthSupported
                if (bbSupported) {
                    bb.setStrength(savedBassBoost.toShort())
                    bb.enabled = isEnabled && savedBassBoost > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost hardware nativo non supportato su questo dispositivo: ${e.message}")
            }

            // Inizializzazione Virtualizer hardware
            var virtSupported = false
            val savedVirtualizer = prefs.getVirtualizerStrength().coerceIn(0, 1000)
            try {
                val virt = Virtualizer(EFFECT_PRIORITY, audioSessionId)
                virtualizer = virt
                virtSupported = virt.strengthSupported
                if (virtSupported) {
                    virt.setStrength(savedVirtualizer.toShort())
                    virt.enabled = isEnabled && savedVirtualizer > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer hardware non supportato su questo dispositivo: ${e.message}")
            }

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
                bassBoostStrength = savedBassBoost,
                isBassBoostSupported = true, // L'architettura ibrida rende il Bass Boost sempre fruibile
                virtualizerStrength = savedVirtualizer,
                isVirtualizerSupported = virtSupported
            )

            applyEffectiveBands()
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile inizializzare l'equalizzatore: ${e.message}")
            _state.value = EqualizerState(isAvailable = false)
        }
    }

    private fun broadcastSessionOpen(context: Context, sessionId: Int) {
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile trasmettere ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION: ${e.message}")
        }
    }

    private fun broadcastSessionClose(context: Context, sessionId: Int) {
        try {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile trasmettere ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION: ${e.message}")
        }
    }

    /**
     * Applica i livelli audio effettivi all'hardware:
     * Combina le bande utente con l'incremento del Bass Boost su Band 0 (sub-bass / ~60 Hz)
     * e Band 1 (mid-bass / ~230 Hz), assicurando che il potenziamento dei bassi sia
     * potente, corposo e percepibile in qualsiasi condizione di ascolto.
     */
    private fun applyEffectiveBands() {
        val eq = equalizer ?: return
        val isEnabled = _state.value.isEnabled
        val bassBoostStrength = _state.value.bassBoostStrength
        val minLevel = _state.value.minLevelMb
        val maxLevel = _state.value.maxLevelMb

        try {
            eq.enabled = isEnabled
        } catch (e: Exception) {
            Log.w(TAG, "Errore eq.enabled: ${e.message}")
        }

        try {
            bassBoost?.enabled = (isEnabled && bassBoostStrength > 0)
        } catch (e: Exception) {
            Log.w(TAG, "Errore bassBoost.enabled: ${e.message}")
        }

        try {
            virtualizer?.enabled = (isEnabled && _state.value.virtualizerStrength > 0)
        } catch (e: Exception) {
            Log.w(TAG, "Errore virtualizer.enabled: ${e.message}")
        }

        // Potenziamento acustico Bass Boost: fino a +700 mB (+7 dB) su banda 0
        // e circa il 45% (+315 mB) su banda 1 per una curva di equalizzazione morbida e calda
        val bassBoostOffsetMb = if (isEnabled && bassBoostStrength > 0) {
            ((bassBoostStrength / 1000f) * 700f).toInt()
        } else {
            0
        }
        val lowMidOffsetMb = (bassBoostOffsetMb * 0.45f).toInt()

        _state.value.bands.forEach { band ->
            val extraBoost = when (band.bandIndex) {
                0 -> bassBoostOffsetMb
                1 -> lowMidOffsetMb
                else -> 0
            }
            val effectiveLevel = (band.levelMb + extraBoost).coerceIn(minLevel, maxLevel).toShort()
            try {
                eq.setBandLevel(band.bandIndex.toShort(), effectiveLevel)
            } catch (e: Exception) {
                Log.w(TAG, "Errore applicazione banda ${band.bandIndex} ($effectiveLevel): ${e.message}")
            }
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        try {
            appPreferences?.setEqualizerEnabled(enabled)
            _state.value = _state.value.copy(isEnabled = enabled)
            applyEffectiveBands()
        } catch (e: Exception) {
            Log.e(TAG, "Errore abilitazione equalizzatore", e)
        }
    }

    @Synchronized
    fun setBandLevel(bandIndex: Int, levelMb: Int, persist: Boolean = false) {
        val clamped = levelMb.coerceIn(_state.value.minLevelMb, _state.value.maxLevelMb)
        val updatedBands = _state.value.bands.map {
            if (it.bandIndex == bandIndex) it.copy(levelMb = clamped) else it
        }

        _state.value = _state.value.copy(
            bands = updatedBands,
            currentPresetIndex = -1 // Passa a personalizzato
        )
        applyEffectiveBands()

        if (persist) {
            persistBandLevels()
        }
    }

    @Synchronized
    fun persistBandLevels() {
        val updatedBands = _state.value.bands
        val levelsCsv = updatedBands.joinToString(",") { "${it.bandIndex}:${it.levelMb}" }
        appPreferences?.setEqualizerBandLevels(levelsCsv)
        appPreferences?.setEqualizerPreset(_state.value.currentPresetIndex)
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
                applyEffectiveBands()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore impostazione preset $presetIndex", e)
        }
    }

    @Synchronized
    fun setBassBoostStrength(strength: Int, persist: Boolean = false) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.setStrength(clamped.toShort())
            bassBoost?.enabled = (_state.value.isEnabled && clamped > 0)
        } catch (e: Exception) {
            Log.e(TAG, "Errore BassBoost hardware", e)
        }

        _state.value = _state.value.copy(bassBoostStrength = clamped)
        applyEffectiveBands()

        if (persist) {
            persistBassBoostStrength(clamped)
        }
    }

    @Synchronized
    fun persistBassBoostStrength(strength: Int) {
        appPreferences?.setBassBoostStrength(strength.coerceIn(0, 1000))
    }

    @Synchronized
    fun setVirtualizerStrength(strength: Int, persist: Boolean = false) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            val virt = virtualizer
            if (virt != null) {
                virt.setStrength(clamped.toShort())
                virt.enabled = (_state.value.isEnabled && clamped > 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore Virtualizer hardware", e)
        }

        _state.value = _state.value.copy(virtualizerStrength = clamped)

        if (persist) {
            persistVirtualizerStrength(clamped)
        }
    }

    @Synchronized
    fun persistVirtualizerStrength(strength: Int) {
        appPreferences?.setVirtualizerStrength(strength.coerceIn(0, 1000))
    }

    @Synchronized
    fun resetToFlat() {
        try {
            val updatedBands = _state.value.bands.map { it.copy(levelMb = 0) }
            appPreferences?.setEqualizerBandLevels(updatedBands.joinToString(",") { "${it.bandIndex}:0" })
            appPreferences?.setEqualizerPreset(-1)
            appPreferences?.setBassBoostStrength(0)
            appPreferences?.setVirtualizerStrength(0)

            try {
                bassBoost?.setStrength(0)
                bassBoost?.enabled = false
            } catch (_: Exception) {}

            try {
                virtualizer?.setStrength(0)
                virtualizer?.enabled = false
            } catch (_: Exception) {}

            _state.value = _state.value.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                bassBoostStrength = 0,
                virtualizerStrength = 0
            )
            applyEffectiveBands()
        } catch (e: Exception) {
            Log.e(TAG, "Errore reset equalizzatore", e)
        }
    }

    @Synchronized
    fun release() {
        val ctx = applicationContext
        val sid = currentAudioSessionId
        if (ctx != null && sid != 0) {
            broadcastSessionClose(ctx, sid)
        }
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
