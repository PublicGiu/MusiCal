package com.example.musicplayer.playback

/**
 * Stato del timer di spegnimento automatico.
 */
sealed interface SleepTimerState {
    data object Inactive : SleepTimerState
    data class Active(
        val remainingMillis: Long,
        val totalMillis: Long,
        val isEndOfTrack: Boolean = false
    ) : SleepTimerState
}
