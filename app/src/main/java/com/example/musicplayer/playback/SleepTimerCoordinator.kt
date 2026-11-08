package com.example.musicplayer.playback

import com.example.musicplayer.data.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinatore dello Sleep Timer per MusiCal.
 * Gestisce il conto alla rovescia, l'opzione "fine brano" e la dissolvenza progressiva del volume
 * (fade-out graduale da 1.0f a 0.0f negli ultimi 15 secondi) prima dell'arresto automatico.
 */
class SleepTimerCoordinator(
    private val scope: CoroutineScope,
    private val onPausePlayback: () -> Unit,
    private val onSetVolume: (Float) -> Unit,
    private val getCurrentTrack: () -> AudioTrack?,
    private val getCurrentPositionMs: () -> Long,
    private val getDurationMs: () -> Long
) {
    private val _sleepTimerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancel()
            return
        }
        val totalMs = minutes * 60 * 1000L
        val targetTime = System.currentTimeMillis() + totalMs
        _sleepTimerState.value = SleepTimerState.Active(
            remainingMillis = totalMs,
            totalMillis = totalMs,
            isEndOfTrack = false
        )

        sleepTimerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = targetTime - now
                if (remaining <= 0) {
                    onPausePlayback()
                    onSetVolume(1.0f)
                    _sleepTimerState.value = SleepTimerState.Inactive
                    break
                }

                // Smooth fade-out nei 15 secondi finali
                if (remaining <= 15_000L) {
                    val fadeVolume = (remaining.toFloat() / 15_000f).coerceIn(0f, 1f)
                    onSetVolume(fadeVolume)
                } else {
                    onSetVolume(1.0f)
                }

                _sleepTimerState.value = SleepTimerState.Active(
                    remainingMillis = remaining,
                    totalMillis = totalMs,
                    isEndOfTrack = false
                )
                delay(500L)
            }
        }
    }

    fun setEndOfTrack() {
        sleepTimerJob?.cancel()
        val current = getCurrentTrack()
        if (current == null) {
            cancel()
            return
        }
        val duration = getDurationMs()
        val dur = if (duration > 0L) duration else current.duration
        val pos = getCurrentPositionMs()
        val remaining = (dur - pos).coerceAtLeast(0L)

        _sleepTimerState.value = SleepTimerState.Active(
            remainingMillis = remaining,
            totalMillis = dur.coerceAtLeast(remaining),
            isEndOfTrack = true
        )

        sleepTimerJob = scope.launch(Dispatchers.Main) {
            val initialTrackId = current.id
            while (isActive) {
                val currTrack = getCurrentTrack()
                val currentPos = getCurrentPositionMs()
                val liveDuration = getDurationMs()
                val currentDur = if (liveDuration > 0L) liveDuration else (currTrack?.duration ?: 0L)
                val rem = (currentDur - currentPos).coerceAtLeast(0L)

                if (currTrack?.id != initialTrackId || (currentDur > 0L && currentPos >= currentDur - 300L)) {
                    onPausePlayback()
                    cancel()
                    break
                }

                if (rem <= 10_000L && currentDur > 15_000L) {
                    val fadeVolume = (rem.toFloat() / 10_000f).coerceIn(0f, 1f)
                    onSetVolume(fadeVolume)
                } else {
                    onSetVolume(1.0f)
                }

                _sleepTimerState.value = SleepTimerState.Active(
                    remainingMillis = rem,
                    totalMillis = currentDur.coerceAtLeast(rem),
                    isEndOfTrack = true
                )
                delay(500L)
            }
        }
    }

    fun cancel() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        onSetVolume(1.0f)
        _sleepTimerState.value = SleepTimerState.Inactive
    }
}
