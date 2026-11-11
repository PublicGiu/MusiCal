package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

private val EXCLUDED_PATH_KEYWORDS = listOf(
    "whatsapp",
    "telegram",
    "recorder",
    "recordings",
    "voice record",
    "callrecording",
    "call_rec",
    "sound_recorder",
    "ringtones",
    "notifications",
    "alarms",
    "podcasts",
    "audiobooks",
    "/.trashed",
    "/.cache",
    "/cache/",
    "/temp/",
    "/tmp/"
)

private val VOICE_NOTE_PREFIXES = listOf(
    "ptt-",
    "aud-",
    "rec_",
    "rec-",
    "voice_",
    "voice-",
    "call_",
    "call-",
    "audiorecord",
    "soundrecord"
)

/**
 * Valuta se un brano audio rientra nei criteri di musica autentica per la Scansione Intelligente,
 * escludendo note vocali WhatsApp/Telegram, registrazioni audio, chiamate e suoni di sistema.
 */
fun isIntelligentMusicTrack(
    relativePath: String,
    displayName: String,
    title: String,
    artist: String,
    album: String,
    durationMs: Long
): Boolean {
    val lowerPath = relativePath.lowercase(java.util.Locale.ROOT)
    val lowerName = displayName.lowercase(java.util.Locale.ROOT)

    // 1. Esclusione file o cartelle nascoste
    if (lowerName.startsWith(".") || lowerPath.contains("/.")) {
        return false
    }

    // 2. Esclusione formati esclusivi di memo/registrazioni
    if (lowerName.endsWith(".amr") || lowerName.endsWith(".3gp")) {
        return false
    }

    val hasAuthenticMetadata = hasAuthenticMusicMetadata(artist, album)

    // Fast-path: se il file è già archiviato nella cartella canonica "music/",
    // è musica autentica e non occorre eseguire i 17 controlli blacklist su stringhe di percorso.
    if (lowerPath.startsWith("music/")) {
        if (durationMs in 1 until 15_000L && !hasAuthenticMetadata) return false
        if (durationMs > 1_200_000L && !hasAuthenticMetadata) return false
        return true
    }

    // 3. Esclusione percorsi indesiderati
    if (EXCLUDED_PATH_KEYWORDS.any { lowerPath.contains(it) }) {
        return false
    }

    // 4. Esclusione prefissi tipici di note vocali e registrazioni
    if (VOICE_NOTE_PREFIXES.any { lowerName.startsWith(it) }) {
        return false
    }

    // 5. Soglia minima di durata: 15 secondi (15.000 ms)
    if (durationMs in 1 until 15_000L) {
        if (!hasAuthenticMetadata) return false
    }

    // 6. Soglia massima di durata: 20 minuti (1.200.000 ms)
    if (durationMs > 1_200_000L) {
        if (!hasAuthenticMetadata) return false
    }

    return true
}

private fun hasAuthenticMusicMetadata(artist: String, album: String): Boolean {
    val cleanArtist = artist.trim()
    val cleanAlbum = album.trim()

    val validArtist = cleanArtist.isNotBlank() &&
            cleanArtist.lowercase(java.util.Locale.ROOT) !in setOf("artista sconosciuto", "unknown artist", "unknown", "<unknown>")
    val validAlbum = cleanAlbum.isNotBlank() &&
            !isGenericAlbum(cleanAlbum)

    return validArtist && validAlbum
}

/**
 * Interroga MediaStore.Audio.Media.EXTERNAL_CONTENT_URI per estrarre tutti i brani
 * audio presenti sul dispositivo secondo la modalità [sourceMode] e le cartelle [customFolders].
 *
 * Implementazione moderna per Android 16 (API 36).
 */
fun loadAudioFiles(
    context: Context,
    sourceMode: AudioSourceMode = AudioSourceMode.INTELLIGENT,
    customFolders: Set<String> = emptySet()
): List<AudioTrack> {
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

    val (selection, selectionArgs) = when (sourceMode) {
        AudioSourceMode.MUSIC_ONLY -> {
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (" +
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
                    "${MediaStore.Audio.Media.RELATIVE_PATH} = ?" +
                    ")"
            sel to arrayOf("Music/%", "Music/")
        }
        AudioSourceMode.CUSTOM_FOLDERS -> {
            if (customFolders.isEmpty()) {
                return emptyList()
            }
            val clauses = mutableListOf<String>()
            val args = mutableListOf<String>()
            for (folder in customFolders) {
                val cleanFolder = folder.trim().trimStart('/')
                val normalized = if (cleanFolder.endsWith('/')) cleanFolder else "$cleanFolder/"
                clauses.add("(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} = ?)")
                args.add("$normalized%")
                args.add(normalized)
            }
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${clauses.joinToString(" OR ")})"
            sel to args.toTypedArray()
        }
        AudioSourceMode.ALL,
        AudioSourceMode.INTELLIGENT -> {
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            sel to null
        }
    }

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

            if (sourceMode == AudioSourceMode.INTELLIGENT &&
                !isIntelligentMusicTrack(relativePath, displayName, title, artist, album, duration)
            ) {
                continue
            }

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
private val COLLAB_SEPARATORS_REGEX = Regex("""(?i)(\s*,\s*|\s+(&|x|\+|and)\s+)""")

/**
 * Verifica se un nome artista rappresenta un progetto collaborativo o di coppia
 * (es. "Drake & 21 Savage", "Salmo & Noyz Narcos").
 */
fun isCollabArtist(artistName: String?): Boolean {
    if (artistName.isNullOrBlank()) return false
    val trimmed = artistName.trim()
    if (isVariousArtists(trimmed)) return false
    return COLLAB_SEPARATORS_REGEX.containsMatchIn(trimmed)
}

/**
 * Estrae i singoli artisti che compongono il progetto collaborativo.
 * Es: "Drake & 21 Savage" -> listOf("Drake", "21 Savage")
 */
fun extractCollaborators(artistName: String?): List<String> {
    if (artistName.isNullOrBlank()) return emptyList()
    val parts = artistName.split(COLLAB_SEPARATORS_REGEX)
    return parts.map { it.trim() }.filter { it.isNotBlank() }
}

/**
 * Verifica se un artista solista è uno dei partner in un progetto collaborativo.
 * Es: isPartnerInCollab("Drake", "Drake & 21 Savage") -> true
 */
fun isPartnerInCollab(soloArtist: String?, collabArtist: String?): Boolean {
    if (soloArtist.isNullOrBlank() || collabArtist.isNullOrBlank()) return false
    val cleanSolo = soloArtist.trim()
    val partners = extractCollaborators(collabArtist)
    return partners.any { it.equals(cleanSolo, ignoreCase = true) }
}

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
 * Verifica se un brano appartiene esclusivamente a un progetto collaborativo (es. "Drake & 21 Savage").
 */
fun AudioTrack.matchesCollabArtist(collabArtistName: String): Boolean {
    val target = collabArtistName.trim()
    if (target.isBlank() || !isCollabArtist(target)) return false

    val targetPartners = extractCollaborators(target)

    if (artist.trim().equals(target, ignoreCase = true) || albumArtist.trim().equals(target, ignoreCase = true)) {
        return true
    }

    if (isCollabArtist(artist)) {
        val trackPartners = extractCollaborators(artist)
        if (trackPartners.size == targetPartners.size && trackPartners.all { tp -> targetPartners.any { it.equals(tp, ignoreCase = true) } }) {
            return true
        }
    }
    if (isCollabArtist(albumArtist)) {
        val albumPartners = extractCollaborators(albumArtist)
        if (albumPartners.size == targetPartners.size && albumPartners.all { ap -> targetPartners.any { it.equals(ap, ignoreCase = true) } }) {
            return true
        }
    }

    return false
}

/**
 * Verifica se un brano appartiene a un artista solista, ESCLUDENDO i progetti collaborativi/di coppia
 * per evitare duplicazioni nei profili singoli.
 */
fun AudioTrack.matchesSoloArtist(soloArtistName: String): Boolean {
    val target = soloArtistName.trim()
    if (target.isBlank()) return false

    // Se la traccia appartiene esplicitamente a un progetto di coppia in cui questo artista è partner,
    // la escludiamo dal profilo solista (sarà visibile nel profilo di coppia dedicato).
    if (isCollabArtist(artist) && isPartnerInCollab(target, artist)) {
        return false
    }
    if (isCollabArtist(albumArtist) && isPartnerInCollab(target, albumArtist)) {
        return false
    }

    val mainArtist = extractMainArtist(artist)
    val mainAlbumArtist = extractMainArtist(albumArtist)

    return artist.trim().equals(target, ignoreCase = true) ||
            albumArtist.trim().equals(target, ignoreCase = true) ||
            (mainArtist.isNotBlank() && mainArtist.equals(target, ignoreCase = true)) ||
            (mainAlbumArtist.isNotBlank() && mainAlbumArtist.equals(target, ignoreCase = true))
}

/**
 * Verifica se un brano appartiene a un determinato artista, distinguendo tra profili di coppia e solisti.
 */
fun AudioTrack.matchesArtist(artistName: String): Boolean {
    return if (isCollabArtist(artistName)) {
        matchesCollabArtist(artistName)
    } else {
        matchesSoloArtist(artistName)
    }
}

/**
 * Verifica se un album appartiene al progetto collaborativo di coppia.
 */
fun Album.matchesCollabArtist(collabArtistName: String): Boolean {
    val target = collabArtistName.trim()
    if (target.isBlank() || !isCollabArtist(target)) return false

    if (artist.trim().equals(target, ignoreCase = true)) return true
    if (isCollabArtist(artist)) {
        val albumPartners = extractCollaborators(artist)
        val targetPartners = extractCollaborators(target)
        if (albumPartners.size == targetPartners.size && albumPartners.all { ap -> targetPartners.any { it.equals(ap, ignoreCase = true) } }) {
            return true
        }
    }
    return tracks.isNotEmpty() && tracks.all { it.matchesCollabArtist(target) }
}

/**
 * Verifica se un album appartiene all'artista solista, ESCLUDENDO gli album collaborativi di coppia.
 */
fun Album.matchesSoloArtist(soloArtistName: String): Boolean {
    val target = soloArtistName.trim()
    if (target.isBlank()) return false

    if (isCollabArtist(artist) && isPartnerInCollab(target, artist)) {
        return false
    }

    val validTracks = tracks.filter { it.matchesSoloArtist(target) }
    if (validTracks.isEmpty()) return false

    val mainAlbumArtist = extractMainArtist(artist)
    return artist.trim().equals(target, ignoreCase = true) ||
            (mainAlbumArtist.isNotBlank() && mainAlbumArtist.equals(target, ignoreCase = true))
}

/**
 * Verifica se un album appartiene a un determinato artista, distinguendo tra profili di coppia e solisti.
 */
fun Album.matchesArtist(artistName: String): Boolean {
    return if (isCollabArtist(artistName)) {
        matchesCollabArtist(artistName)
    } else {
        matchesSoloArtist(artistName)
    }
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
 */
fun groupTracksIntoAlbums(tracks: List<AudioTrack>): List<Album> {
    val usedAlbumIds = mutableSetOf<Long>()
    val mainArtistCache = HashMap<String, String>()
    fun getMainArtist(artist: String?): String {
        if (artist.isNullOrBlank()) return ""
        return mainArtistCache.getOrPut(artist) { extractMainArtist(artist) }
    }

    return tracks.groupBy { track ->
        val rawAlbum = track.album.trim()
        val isGeneric = isGenericAlbum(rawAlbum)

        if (isGeneric) {
            val mainArtist = getMainArtist(track.artist)
            if (mainArtist.isNotBlank() && mainArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
                "generic_artist:${mainArtist.lowercase(java.util.Locale.ROOT)}"
            } else {
                "generic_single:${track.id}"
            }
        } else {
            val cleanAlbum = rawAlbum.lowercase(java.util.Locale.ROOT)
            val rawAlbumArtist = track.albumArtist.trim().ifBlank { getMainArtist(track.artist) }
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
            val mainArtist = getMainArtist(first.artist)
            if (mainArtist.isNotBlank() && mainArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
                "$mainArtist (Brani singoli)"
            } else {
                first.title
            }
        } else {
            first.album
        }

        val albumArtist = if (isGeneric) {
            getMainArtist(first.artist).ifBlank { first.artist }
        } else {
            val rawAlbumArtist = first.albumArtist.trim().ifBlank { getMainArtist(first.artist) }
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
