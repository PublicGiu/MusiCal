package com.example.musicplayer.data.tagger.model

data class DeezerSearchTrack(
    val id: Long,
    val title: String,
    val titleShort: String,
    val duration: Long,
    val artistName: String,
    val albumId: Long,
    val albumTitle: String,
    val coverXl: String?
)

data class DeezerAlbumTrackInfo(
    val id: Long,
    val title: String,
    val trackPosition: Int,
    val diskNumber: Int
)

data class DeezerTrackDetail(
    val id: Long,
    val title: String,
    val titleShort: String,
    val trackPosition: Int,
    val diskNumber: Int,
    val duration: Long,
    val releaseDate: String?,
    val albumId: Long,
    val albumTitle: String,
    val artistName: String,
    val coverXl: String?
)

data class DeezerAlbumDetail(
    val id: Long,
    val title: String,
    val albumArtist: String,
    val releaseDate: String?,
    val recordType: String,
    val genre: String?,
    val coverXl: String?,
    val tracks: List<DeezerAlbumTrackInfo>
) {
    val releaseYear: Int?
        get() {
            if (releaseDate.isNullOrBlank()) return null
            val parts = releaseDate.split("-")
            return parts.firstOrNull()?.toIntOrNull()
        }
}
