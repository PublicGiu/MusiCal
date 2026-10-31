package com.example.musicplayer.data

import android.net.Uri

/**
 * Modello dati che rappresenta un Album musicale con le relative tracce.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val tracks: List<AudioTrack>
) {
    val trackCount: Int get() = tracks.size
}
