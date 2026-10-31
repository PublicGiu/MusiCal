package com.example.musicplayer.data.lastfm.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room per la memorizzazione offline degli scrobble pendenti di Last.fm.
 */
@Entity(tableName = "pending_scrobbles")
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String = "",
    val timestamp: Long // Timestamp Unix di inizio ascolto in secondi
)
