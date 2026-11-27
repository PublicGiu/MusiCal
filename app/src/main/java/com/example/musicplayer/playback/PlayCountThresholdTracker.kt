package com.example.musicplayer.playback

/**
 * Traccia il tempo effettivamente ascoltato del brano corrente e segnala il raggiungimento
 * di una riproduzione valida solo quando l'ascolto effettivo accumulato raggiunge almeno il 50%
 * della durata complessiva della traccia.
 *
 * Previene falsi conteggi e riordinamenti prematuri in "Ascoltati di recente" (per brani, album e playlist)
 * in caso di skip rapidi o salti manuali sulla seekbar.
 */
class PlayCountThresholdTracker(
    private val maxContinuousTickDeltaMs: Long = 3_000L
) {
    var activeTrackId: Long? = null
        private set

    var accumulatedListenedMs: Long = 0L
        private set

    var hasRecordedValidPlay: Boolean = false
        private set

    private var lastObservedPositionMs: Long = 0L

    /**
     * Inizializza (o riavvia, es. in modalità Repeat One) la sessione d'ascolto per [trackId].
     */
    fun onTrackSessionStarted(trackId: Long, initialPositionMs: Long = 0L) {
        activeTrackId = trackId
        lastObservedPositionMs = initialPositionMs.coerceAtLeast(0L)
        accumulatedListenedMs = 0L
        hasRecordedValidPlay = false
    }

    /**
     * Aggiorna il riferimento di posizione in seguito a un seek manuale dell'utente,
     * evitando che il salto in avanti venga conteggiato come tempo effettivamente ascoltato.
     */
    fun onManualSeek(newPositionMs: Long) {
        lastObservedPositionMs = newPositionMs.coerceAtLeast(0L)
    }

    /**
     * Azzera completamente lo stato (es. a coda svuotata o assenza traccia).
     */
    fun reset() {
        activeTrackId = null
        lastObservedPositionMs = 0L
        accumulatedListenedMs = 0L
        hasRecordedValidPlay = false
    }

    /**
     * Elabora un aggiornamento periodico del player e restituisce `true` esattamente una volta
     * per sessione quando il tempo effettivamente ascoltato raggiunge almeno il 50% di [durationMs].
     */
    fun onPlaybackTick(
        trackId: Long,
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ): Boolean {
        if (trackId <= 0L) return false
        val safePos = currentPositionMs.coerceAtLeast(0L)

        if (activeTrackId != trackId) {
            onTrackSessionStarted(trackId, safePos)
            return false
        }

        val delta = safePos - lastObservedPositionMs
        lastObservedPositionMs = safePos

        if (hasRecordedValidPlay || durationMs <= 0L) {
            return false
        }

        if (isPlaying && delta in 1L..maxContinuousTickDeltaMs) {
            accumulatedListenedMs += delta
        }

        val thresholdMs = (durationMs / 2L).coerceAtLeast(1L)
        if (accumulatedListenedMs >= thresholdMs) {
            hasRecordedValidPlay = true
            return true
        }
        return false
    }
}
