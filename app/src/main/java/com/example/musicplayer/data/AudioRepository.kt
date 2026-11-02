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
        MediaStore.Audio.Media.DISC_NUMBER,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ALBUM_ARTIST
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
        val relativePathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
        val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val albumArtistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST).takeIf { it >= 0 }
            ?: cursor.getColumnIndex("album_artist")

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Sconosciuto"
            val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() } ?: "Artista sconosciuto"
            val duration = cursor.getLong(durationColumn)
            val albumId = cursor.getLong(albumIdColumn)
            val album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Album sconosciuto"
            val dateAdded = cursor.getLong(dateAddedColumn)
            val genre = if (genreColumn >= 0) cursor.getString(genreColumn)?.trim().orEmpty() else ""
            val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn)?.trim().orEmpty() else ""
            val displayName = if (displayNameColumn >= 0) cursor.getString(displayNameColumn)?.trim().orEmpty() else ""
            val albumArtist = if (albumArtistColumn >= 0) cursor.getString(albumArtistColumn)?.trim().orEmpty() else ""
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
                    discNumber = discNumber,
                    albumArtist = albumArtist,
                    displayName = displayName,
                    relativePath = relativePath
                )
            )
        }
    }

    return audioList
}

private val GENERIC_ALBUM_NAMES = setOf(
    "sconosciuto",
    "album sconosciuto",
    "unknown",
    "unknown album",
    "<unknown>",
    "download",
    "downloads",
    "music",
    "musica",
    "audio",
    "tracks",
    "canzoni",
    "soundtrack"
)

private val VARIOUS_ARTISTS_NAMES = setOf(
    "various artists",
    "various artist",
    "artisti vari",
    "various",
    "va",
    "v/a",
    "compilation",
    "soundtrack",
    "ost"
)

private val FEAT_ARTIST_REGEX = Regex("""(?i)\s+(feat\.?|ft\.?|featuring|with|vs\.?|&|,|\+|x|/)\s+.*""")

/**
 * Verifica se una stringa di album è generica o assente.
 */
fun isGenericAlbum(name: String?): Boolean {
    if (name.isNullOrBlank()) return true
    return name.trim().lowercase(java.util.Locale.ROOT) in GENERIC_ALBUM_NAMES
}

/**
 * Verifica se un artista rappresenta una compilation / Artisti Vari.
 */
fun isVariousArtists(name: String?): Boolean {
    if (name.isNullOrBlank()) return false
    return name.trim().lowercase(java.util.Locale.ROOT) in VARIOUS_ARTISTS_NAMES
}

/**
 * Estrae l'artista principale rimuovendo collaborazioni ("feat.", "ft.", "&", "+", "x", ecc.).
 */
fun extractMainArtist(artist: String?): String {
    if (artist.isNullOrBlank()) return ""
    return artist.replace(FEAT_ARTIST_REGEX, "").trim()
}

/**
 * Verifica se un album raggruppa tracce eterogenee (album diversi o titolo generico)
 * che necessitano di separazione/disaggregazione.
 */
fun Album.isMixedOrGeneric(): Boolean {
    // 1. Album con nome generico ("Sconosciuto", "Downloads", ecc.) o collezione di brani singoli
    if (isGenericAlbum(title) || title.contains("(Brani singoli)")) return true

    // 2. Tracce che presentano tag album differenti e non generici
    val distinctAlbums = tracks.map { it.album.trim().lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotBlank() && !isGenericAlbum(it) }
        .distinct()
    if (distinctAlbums.size > 1) return true

    // Se tutte le tracce condividono lo stesso titolo album non generico, l'album è unificato/ufficiale
    return false
}

/**
 * Raggruppa i brani in una lista di modelli Album con tracce associate.
 * 
 * Risolve la frammentazione e l'errata incorporazione di brani di artisti e album diversi:
 * 1. Non raggruppa alla cieca per MediaStore albumId: se i brani hanno titoli album diversi
 *    o artisti diversi, vengono separati in album distinti anche in presenza di collisioni MediaStore.
 * 2. I brani senza tag album ("Sconosciuto" o generico) vengono separati per artista anziché essere
 *    ammassati in un unico album indefinito.
 * 3. Supporta compilation "Various Artists" quando indicato nei metadati.
 * 4. Assegna ID univoci e stabili a ciascun album.
 */
fun groupTracksIntoAlbums(tracks: List<AudioTrack>): List<Album> {
    val usedAlbumIds = mutableSetOf<Long>()

    return tracks.groupBy { track ->
        val rawAlbum = track.album.trim()
        val isGeneric = isGenericAlbum(rawAlbum)

        if (isGeneric) {
            val mainArtist = extractMainArtist(track.artist)
            if (mainArtist.isNotBlank() && mainArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
                "generic_artist:${mainArtist.lowercase(java.util.Locale.ROOT)}"
            } else {
                "generic_single:${track.id}"
            }
        } else {
            val cleanAlbum = rawAlbum.lowercase(java.util.Locale.ROOT)
            val rawAlbumArtist = track.albumArtist.trim().ifBlank { extractMainArtist(track.artist) }
            val isVa = isVariousArtists(rawAlbumArtist) || isVariousArtists(track.artist)
            if (isVa) {
                "album:$cleanAlbum:va"
            } else {
                "album:$cleanAlbum:artist:${rawAlbumArtist.lowercase(java.util.Locale.ROOT)}"
            }
        }
    }.map { (groupKey, albumTracks) ->
        val first = albumTracks.first()
        val isGeneric = isGenericAlbum(first.album)

        val albumTitle = if (isGeneric) {
            val mainArtist = extractMainArtist(first.artist)
            if (mainArtist.isNotBlank() && mainArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
                "$mainArtist (Brani singoli)"
            } else {
                first.title
            }
        } else {
            first.album
        }

        val albumArtist = if (isGeneric) {
            extractMainArtist(first.artist).ifBlank { first.artist }
        } else {
            val rawAlbumArtist = first.albumArtist.trim().ifBlank { extractMainArtist(first.artist) }
            if (isVariousArtists(rawAlbumArtist)) "Artisti Vari" else rawAlbumArtist.ifBlank { first.artist }
        }

        // Calcolo ID univoco e stabile
        val albumIdCandidates = albumTracks.map { it.albumId }.distinct()
        val primaryCandidate = if (albumIdCandidates.size == 1 && albumIdCandidates.first() > 0L) {
            albumIdCandidates.first()
        } else {
            null
        }

        val albumId = if (primaryCandidate != null && primaryCandidate !in usedAlbumIds) {
            usedAlbumIds.add(primaryCandidate)
            primaryCandidate
        } else {
            var syntheticId = (groupKey.hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL)
            while (syntheticId == 0L || syntheticId in usedAlbumIds) {
                syntheticId++
            }
            usedAlbumIds.add(syntheticId)
            syntheticId
        }

        val representativeArtUri = albumTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: first.albumArtUri

        val sortedAlbumTracks = albumTracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )

        Album(
            id = albumId,
            title = albumTitle,
            artist = albumArtist,
            albumArtUri = representativeArtUri,
            tracks = sortedAlbumTracks
        )
    }.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
}
