package com.example.musicplayer.data

import java.util.UUID

const val FAVORITES_PLAYLIST_ID = "favorites_default"
const val FAVORITES_PLAYLIST_NAME = "Brani preferiti"

/**
 * Modello dati per una playlist personalizzata salvata in memoria locale.
 */
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<Long> = emptyList(),
    val coverUri: String? = null
) {
    val trackCount: Int get() = trackIds.size
    val isFavorites: Boolean get() = id == FAVORITES_PLAYLIST_ID
    val effectiveCoverUri: String? get() = if (isFavorites) null else coverUri?.takeIf { it.isNotBlank() }

    companion object {
        const val FAVORITES_PLAYLIST_ID = com.example.musicplayer.data.FAVORITES_PLAYLIST_ID
        const val FAVORITES_PLAYLIST_NAME = com.example.musicplayer.data.FAVORITES_PLAYLIST_NAME
    }
}

