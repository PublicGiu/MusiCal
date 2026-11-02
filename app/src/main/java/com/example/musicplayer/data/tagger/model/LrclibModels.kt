package com.example.musicplayer.data.tagger.model

data class LrclibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
) {
    val hasSyncedLyrics: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasPlainLyrics: Boolean get() = !plainLyrics.isNullOrBlank()
}
