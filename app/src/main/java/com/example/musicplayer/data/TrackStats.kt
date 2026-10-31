package com.example.musicplayer.data

/**
 * Modello per le statistiche d'ascolto di una traccia audio.
 */
data class TrackStats(
    val audioId: Long,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
)
