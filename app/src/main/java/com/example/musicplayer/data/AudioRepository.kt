package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/**
 * Interroga MediaStore.Audio.Media.EXTERNAL_CONTENT_URI per estrarre tutti i brani
 * audio presenti sul dispositivo con ID, titolo, artista, durata, URI, album e copertina.
 *
 * Implementazione moderna per Android 16 (API 36) senza controlli o fallback legacy.
 */
fun loadAudioFiles(context: Context): List<AudioTrack> {
    val audioList = mutableListOf<AudioTrack>()
    val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val albumArtBaseUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.IS_MUSIC,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DISC_NUMBER
    )

    // Consideriamo esclusivamente i file musicali presenti nella cartella Music/ (e relative sottocartelle)
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (" +
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?" +
            ")"
    val selectionArgs = arrayOf("Music/%", "Music/")
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(
        collectionUri,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
        val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val discNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER).takeIf { it >= 0 }
            ?: cursor.getColumnIndex("disc_number").takeIf { it >= 0 }
            ?: cursor.getColumnIndex("cd_track_number")

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Sconosciuto"
            val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() } ?: "Artista sconosciuto"
            val duration = cursor.getLong(durationColumn)
            val albumId = cursor.getLong(albumIdColumn)
            val album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Album sconosciuto"
            val dateAdded = cursor.getLong(dateAddedColumn)
            val genre = if (genreColumn >= 0) cursor.getString(genreColumn)?.trim().orEmpty() else ""
            val contentUri = ContentUris.withAppendedId(collectionUri, id)
            val albumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)

            val rawTrack = if (trackColumn >= 0 && !cursor.isNull(trackColumn)) {
                cursor.getInt(trackColumn)
            } else {
                0
            }
            var trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
            var discNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1

            if (discNumberColumn >= 0 && !cursor.isNull(discNumberColumn)) {
                val disc = cursor.getInt(discNumberColumn)
                if (disc > 0) {
                    discNumber = disc
                }
            }
            trackNumber = trackNumber.coerceAtLeast(0)
            discNumber = discNumber.coerceAtLeast(1)

            audioList.add(
                AudioTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    duration = duration,
                    uri = contentUri,
                    albumId = albumId,
                    album = album,
                    albumArtUri = albumArtUri,
                    dateAdded = dateAdded,
                    genre = genre,
                    trackNumber = trackNumber,
                    discNumber = discNumber
                )
            )
        }
    }

    return audioList
}

/**
 * Raggruppa i brani in una lista di modelli Album con tracce associate.
 * Le tracce di ciascun album vengono ordinate secondo la scaletta originale del disco:
 * prima per numero di disco (discNumber), poi per numero di traccia (trackNumber) e infine per titolo.
 */
fun groupTracksIntoAlbums(tracks: List<AudioTrack>): List<Album> {
    return tracks.groupBy { it.albumId }
        .map { (albumId, albumTracks) ->
            val first = albumTracks.first()
            val sortedAlbumTracks = albumTracks.sortedWith(
                compareBy<AudioTrack> { it.discNumber }
                    .thenBy { it.trackNumber }
                    .thenBy { it.title }
            )
            Album(
                id = albumId,
                title = first.album,
                artist = first.artist,
                albumArtUri = first.albumArtUri,
                tracks = sortedAlbumTracks
            )
        }
        .sortedBy { it.title.lowercase() }
}
