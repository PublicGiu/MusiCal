package com.example.musicplayer.data

/**
 * Rappresenta il contesto di origine della riproduzione corrente:
 * Libreria generale, Album specifico, Playlist personalizzata o Artista.
 */
sealed interface PlaybackContext {
    data object Library : PlaybackContext
    data class Album(val id: Long, val title: String, val artist: String = "") : PlaybackContext
    data class Playlist(val id: String, val name: String) : PlaybackContext
    data class Artist(val name: String) : PlaybackContext
}
