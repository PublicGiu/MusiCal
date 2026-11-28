package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import java.util.concurrent.ConcurrentHashMap

private val EXCLUDED_PATH_SUBSTRINGS = listOf(
    "com.whatsapp",
    "org.telegram",
    "org.thoughtcrime.securesms",
    "com.viber",
    "com.discord",
    "com.instagram",
    "com.facebook.orca",
    "com.skype",
    "com.tencent.mm",
    "micromsg",
    "truecaller",
    "cubeacr",
    "boldbeast",
    "whatsapp audio",
    "whatsapp voice",
    "telegram audio",
    "telegram voice",
    "telegram documents",
    "voice record",
    "voicerecord",
    "voice_record",
    "voicememo",
    "voice memo",
    "voice_memo",
    "voice-memo",
    "voicenote",
    "voice note",
    "voice_note",
    "voice-note",
    "note vocali",
    "nota vocale",
    "memo vocali",
    "memo vocale",
    "messaggi vocali",
    "registrazioni vocali",
    "registrazione vocale",
    "callrecording",
    "call recording",
    "call_recording",
    "call_rec",
    "callrecord",
    "phonerecord",
    "phone_record",
    "phone record",
    "sound_recorder",
    "soundrecorder",
    "audiorecorder",
    "audio_recorder",
    "dictaphone",
    "dittafono",
    "screenrecord",
    "screen_record",
    "screen-record",
    "miui/ringtone",
    "samsung/ringtone",
    "/.trashed",
    "/.cache",
    "/cache/",
    "/temp/",
    "/tmp/"
)

private val EXCLUDED_ROOT_PREFIXES = listOf(
    "android/data/",
    "android/obb/",
    "dcim/",
    "pictures/",
    "movies/"
)

private val EXCLUDED_EXACT_SEGMENTS_EVERYWHERE = setOf(
    "whatsapp",
    "telegram",
    "recordings",
    "registrazioni",
    "registrazione",
    "recorder",
    "registratore",
    "ringtones",
    "suonerie",
    "notifications",
    "notifiche",
    "alarms",
    "sveglie",
    "allarmi",
    "podcasts",
    "podcast",
    "audiobooks",
    "audiolibri",
    "chiamate",
    "voicememos",
    "cache",
    "temp",
    "tmp"
)

private val EXCLUDED_CHAT_SEGMENTS_OUTSIDE_MUSIC = setOf(
    "viber",
    "signal",
    "messenger",
    "instagram",
    "wechat",
    "discord",
    "skype"
)

private val RECORDING_EXCLUDED_EXTENSIONS = setOf(".amr", ".3gp", ".3ga", ".awb", ".qcp", ".spx")

private val RECORDING_ROOT_SEGMENT_REGEX = Regex(
    """^(?:music/)?(?:sounds|record|calls|voice|memo|audio\s*notes?)(?:/|$)""",
    RegexOption.IGNORE_CASE
)

private val SYNTHETIC_VOICE_NOTE_PREFIXES = listOf(
    "ptt-",
    "ptt_",
    "aud-",
    "aud_",
    "audio_",
    "rec_",
    "rec-",
    "record_",
    "record-",
    "recording_",
    "recording-",
    "audio recording",
    "voice recording",
    "screen recording",
    "recorder -",
    "registrazione vocale",
    "registrazione schermo",
    "registrazione_",
    "registrazione-",
    "nuova registrazione",
    "new recording",
    "voice_",
    "voice-",
    "voce_",
    "voce-",
    "memo_",
    "memo-",
    "call_",
    "call-",
    "call@",
    "chiamata_",
    "chiamata-",
    "chiamata ",
    "audiorecord",
    "soundrecord",
    "dictaphone",
    "dittafono"
)

private val NUMBERED_RECORDING_NAME_REGEX = Regex(
    """^(?:(?:voice|voce|rec|record|recording|registrazione(?:\s+vocale)?|memo(?:\s+vocale)?|nota\s+vocale|audio|call|chiamata)(?:[\s_#-]*\d+(?:[\s_#-].*)?)?|(?:standard|interview|intervista|clip|sound)[\s_#-]+\d+|rec\d{6,})$""",
    RegexOption.IGNORE_CASE
)

private val UI_SFX_NAME_REGEX = Regex(
    """^(?:ringtone|notification|alarm|suoneria|notifica|sveglia|camera_click|shutter|screenshot|lock|unlock|keypress|charging|low_battery|beep|ding|chime|sfx|sound_effect)(?:[\s_#-]*\d+)?$""",
    RegexOption.IGNORE_CASE
)

private val TIMESTAMP_RECORDING_NAME_REGEX = Regex(
    """^(?:@(?:rec|call)?[\s_-]*)?\d{4}[-_.]?\d{2}[-_.]?\d{2}(?:[\s_@.-]+\d{2}[-_.]?\d{2}(?:[-_.]?\d{2})?)?(?:[\s_-]*\d*)?$""",
    RegexOption.IGNORE_CASE
)

private val DAY_TIME_RECORDING_NAME_REGEX = Regex(
    """^(?:luned[ìi]|marted[ìi]|mercoled[ìi]|gioved[ìi]|venerd[ìi]|sabato|domenica|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+(?:alle|at)\s+\d{1,2}[-.:]\d{2}(?:[-.:]\d{2})?(?:\s*\(\d+\))?$""",
    RegexOption.IGNORE_CASE
)

private val WHATSAPP_WA_ID_REGEX = Regex("""^(?:ptt|aud|wa)[_-]?\d{4,}""", RegexOption.IGNORE_CASE)

private val RECORDER_METADATA_NAMES = setOf(
    "voice recorder",
    "sound recorder",
    "google recorder",
    "samsung voice recorder",
    "xiaomirecorder",
    "miui sound recorder",
    "easy voice recorder",
    "smart recorder",
    "recorder",
    "registratore",
    "registratore vocale",
    "call recorder",
    "voice memos",
    "voice memo",
    "memo vocali",
    "recordings",
    "registrazioni",
    "whatsapp audio",
    "telegram audio",
    "ringtones",
    "notifications",
    "alarms",
    "suonerie",
    "notifiche",
    "sounds",
    "acr",
    "cube acr"
)

private fun isExcludedIntelligentPath(lowerPath: String): Boolean {
    if (lowerPath.isEmpty()) return false
    val cleanPath = lowerPath.trimStart('/')
    if (EXCLUDED_ROOT_PREFIXES.any { cleanPath.startsWith(it) }) return true
    if (EXCLUDED_PATH_SUBSTRINGS.any { lowerPath.contains(it) }) return true

    val segments = cleanPath.split('/').filter { it.isNotBlank() }
    if (segments.isEmpty()) return false
    if (segments.any { it in EXCLUDED_EXACT_SEGMENTS_EVERYWHERE }) return true

    val isInsideMusicRoot = segments.first() == "music"
    if (!isInsideMusicRoot && segments.any { it in EXCLUDED_CHAT_SEGMENTS_OUTSIDE_MUSIC }) {
        return true
    }
    return false
}

private fun stripAudioExtension(fileName: String): String {
    val dotIdx = fileName.lastIndexOf('.')
    return if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
}

private fun matchesRecordingNamePattern(baseName: String, title: String): Boolean {
    val candidates = listOf(baseName.trim(), title.trim()).filter { it.isNotBlank() }
    return candidates.any { value ->
        val lower = value.lowercase(java.util.Locale.ROOT)
        SYNTHETIC_VOICE_NOTE_PREFIXES.any { lower.startsWith(it) } ||
            NUMBERED_RECORDING_NAME_REGEX.matches(lower) ||
            TIMESTAMP_RECORDING_NAME_REGEX.matches(lower) ||
            DAY_TIME_RECORDING_NAME_REGEX.matches(lower) ||
            WHATSAPP_WA_ID_REGEX.containsMatchIn(lower)
    }
}

private fun hasRecorderPlaceholderMetadata(artist: String, album: String): Boolean {
    val normArtist = artist.trim().lowercase(java.util.Locale.ROOT)
    val normAlbum = album.trim().lowercase(java.util.Locale.ROOT)
    return normArtist in RECORDER_METADATA_NAMES || normAlbum in RECORDER_METADATA_NAMES
}

/**
 * Valuta se un brano audio rientra nei criteri di musica autentica per la Scansione Intelligente,
 * escludendo note vocali WhatsApp/Telegram, registrazioni audio, chiamate, suoni di sistema e flag MediaStore.
 */
fun isIntelligentMusicTrack(
    relativePath: String,
    displayName: String,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    isSystemNonMusic: Boolean = false,
    fileSizeBytes: Long = -1L
): Boolean {
    // 0. Esclusione immediata tramite flag nativi MediaStore o file corrotti/a durata zero
    if (isSystemNonMusic || durationMs <= 0L) {
        return false
    }

    val lowerPath = relativePath.trim().replace('\\', '/').lowercase(java.util.Locale.ROOT)
    val lowerName = displayName.trim().lowercase(java.util.Locale.ROOT)
    val lowerBaseName = stripAudioExtension(lowerName)

    // 1. Esclusione file o cartelle nascoste
    if (lowerName.startsWith(".") || lowerPath.contains("/.")) {
        return false
    }

    // 2. Esclusione formati esclusivi di memo/registrazioni
    if (RECORDING_EXCLUDED_EXTENSIONS.any { lowerName.endsWith(it) }) {
        return false
    }

    // 3. Esclusione percorsi indesiderati (segment-safe per non escludere album/artisti tipo Music/Signal/ o Music/The Alarms/)
    if (isExcludedIntelligentPath(lowerPath)) {
        return false
    }

    val hasAuthenticMetadata = hasAuthenticMusicMetadata(artist, album) &&
        !hasRecorderPlaceholderMetadata(artist, album)

    // 3.5 Esclusione micro-file (< 16 KB, es. click UI/suoni di sistema) privi di metadati musicali autentici
    if (fileSizeBytes in 1 until 16_384L && !hasAuthenticMetadata) {
        return false
    }

    // 4. Metadati espliciti di app registratore / messaggistica
    if (hasRecorderPlaceholderMetadata(artist, album)) {
        return false
    }

    val cleanLowerPath = lowerPath.trimStart('/')
    val isInsideMusicFolder = cleanLowerPath.startsWith("music/")

    // 5. Cartelle OEM di registrazione (Sounds/, Record/, Calls/) a radice o prive di metadati musicali autentici
    if (RECORDING_ROOT_SEGMENT_REGEX.containsMatchIn(cleanLowerPath)) {
        if (!isInsideMusicFolder || !hasAuthenticMetadata) {
            return false
        }
    }

    // 6. Pattern nome file / titolo tipici di registratori, note vocali o timestamp puri
    val matchesRecordingName = matchesRecordingNamePattern(lowerBaseName, title)
    if (matchesRecordingName) {
        // Dentro Music/ ammettiamo solo brani con metadati musicali autentici completi (artista + album validi)
        if (!isInsideMusicFolder || !hasAuthenticMetadata) {
            return false
        }
    }

    // 6.5 Effetti sonori di sistema / suonerie brevi prive di metadati
    val matchesUiSfx = UI_SFX_NAME_REGEX.matches(lowerBaseName.trim()) || UI_SFX_NAME_REGEX.matches(title.trim())
    if (matchesUiSfx && durationMs < 45_000L && !hasAuthenticMetadata) {
        return false
    }

    // 6.6 Clip vocali .opus / .ogg brevi (< 45s) fuori da Music/ prive di metadati e di formato "Artista - Titolo"
    if (!isInsideMusicFolder &&
        (lowerName.endsWith(".opus") || lowerName.endsWith(".ogg")) &&
        durationMs < 45_000L &&
        !hasAuthenticMetadata &&
        !lowerBaseName.contains(" - ")
    ) {
        return false
    }

    // 7. Fast-path sicuro per file nella cartella canonica "music/" che hanno superato i controlli precedenti
    if (isInsideMusicFolder) {
        if (durationMs < 15_000L && !hasAuthenticMetadata) return false
        if (durationMs > 1_200_000L && !hasAuthenticMetadata) return false
        return true
    }

    // 8. Soglia minima di durata: 15 secondi (15.000 ms)
    if (durationMs < 15_000L && !hasAuthenticMetadata) {
        return false
    }

    // 9. Soglia massima di durata: 20 minuti (1.200.000 ms)
    if (durationMs > 1_200_000L && !hasAuthenticMetadata) {
        return false
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
        MediaStore.Audio.Media.IS_RECORDING,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_PODCAST,
        MediaStore.Audio.Media.IS_AUDIOBOOK,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DISC_NUMBER,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ALBUM_ARTIST,
        MediaStore.Audio.Media.SIZE
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
        AudioSourceMode.ALL -> {
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            sel to null
        }
        AudioSourceMode.INTELLIGENT -> {
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
                    "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            sel to arrayOf("Music/%", "Music/")
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
        val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val isRecordingColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING)
        val isRingtoneColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
        val isNotificationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
        val isAlarmColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
        val isPodcastColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PODCAST)
        val isAudiobookColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK)

        fun isFlagColumnTrue(colIdx: Int): Boolean =
            colIdx >= 0 && !cursor.isNull(colIdx) && cursor.getInt(colIdx) != 0

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
            val fileSizeBytes = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L
            val isSystemNonMusic = isFlagColumnTrue(isRecordingColumn) ||
                isFlagColumnTrue(isRingtoneColumn) ||
                isFlagColumnTrue(isNotificationColumn) ||
                isFlagColumnTrue(isAlarmColumn) ||
                isFlagColumnTrue(isPodcastColumn) ||
                isFlagColumnTrue(isAudiobookColumn)

            if (sourceMode == AudioSourceMode.INTELLIGENT &&
                !isIntelligentMusicTrack(
                    relativePath = relativePath,
                    displayName = displayName,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    isSystemNonMusic = isSystemNonMusic,
                    fileSizeBytes = fileSizeBytes
                )
            ) {
                continue
            }

            val contentUri = ContentUris.withAppendedId(collectionUri, id)
            val defaultAlbumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)
            val albumArtUri = TaggedCoverStore.getCoverUri(context, id) ?: defaultAlbumArtUri

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

const val UNRELEASED_ALBUM_TITLE = "Unreleased"

fun isUnreleasedAlbumTitle(name: String?): Boolean {
    return name?.trim()?.equals(UNRELEASED_ALBUM_TITLE, ignoreCase = true) == true
}

private val FEAT_ARTIST_REGEX = Regex(
    """(?i)(?:\s*,\s+.*|\s+(?:feat\.?|ft\.?|featuring|with|con|vs\.?|&|\+|x|/)\s+.*|\s*[\(\[]\s*(?:feat\.?|ft\.?|featuring|with|con)\s+.*)$"""
)
private val COLLAB_SEPARATORS_REGEX = Regex("""(?i)(\s*,\s*|\s+(&|x|\+|and)\s+)""")

private const val ARTIST_MEMO_CACHE_LIMIT = 4096
private val isCollabArtistMemoCache = ConcurrentHashMap<String, Boolean>(512)
private val extractCollaboratorsMemoCache = ConcurrentHashMap<String, List<String>>(256)
private val extractMainArtistMemoCache = ConcurrentHashMap<String, String>(512)

/**
 * Verifica se un nome artista rappresenta un progetto collaborativo o di coppia
 * (es. "Drake & 21 Savage", "Salmo & Noyz Narcos"), escludendo singoli artisti
 * il cui nome canonico contiene una virgola interna (es. "Tyler, The Creator").
 */
fun isCollabArtist(artistName: String?): Boolean {
    if (artistName.isNullOrBlank()) return false
    val trimmed = artistName.trim()
    isCollabArtistMemoCache[trimmed]?.let { return it }

    val computed = if (isVariousArtists(trimmed)) {
        false
    } else {
        val masked = QuerySanitizer.maskProtectedArtists(trimmed)
        COLLAB_SEPARATORS_REGEX.containsMatchIn(masked.maskedText)
    }
    if (isCollabArtistMemoCache.size >= ARTIST_MEMO_CACHE_LIMIT) {
        isCollabArtistMemoCache.clear()
    }
    isCollabArtistMemoCache[trimmed] = computed
    return computed
}

/**
 * Estrae i singoli artisti che compongono il progetto collaborativo.
 * Es: "Drake & 21 Savage" -> listOf("Drake", "21 Savage")
 * Es: "Tyler, The Creator & A$AP Rocky" -> listOf("Tyler, The Creator", "A$AP Rocky")
 */
fun extractCollaborators(artistName: String?): List<String> {
    if (artistName.isNullOrBlank()) return emptyList()
    val trimmed = artistName.trim()
    extractCollaboratorsMemoCache[trimmed]?.let { return it }

    val masked = QuerySanitizer.maskProtectedArtists(trimmed)
    val parts = masked.maskedText.split(COLLAB_SEPARATORS_REGEX)
    val computed = parts.map { masked.unmask(it).trim() }.filter { it.isNotBlank() }
    if (extractCollaboratorsMemoCache.size >= ARTIST_MEMO_CACHE_LIMIT) {
        extractCollaboratorsMemoCache.clear()
    }
    extractCollaboratorsMemoCache[trimmed] = computed
    return computed
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
 * Estrae l'artista principale rimuovendo collaborazioni ("feat.", "ft.", "&", "+", "x", ecc.),
 * preservando intatti i nomi d'arte protetti che contengono virgola (es. "Tyler, The Creator").
 */
fun extractMainArtist(artist: String?): String {
    if (artist.isNullOrBlank()) return ""
    val trimmed = artist.trim()
    extractMainArtistMemoCache[trimmed]?.let { return it }

    val masked = QuerySanitizer.maskProtectedArtists(trimmed)
    val stripped = masked.maskedText.replace(FEAT_ARTIST_REGEX, "").trim()
    val computed = masked.unmask(stripped).trim()
    if (extractMainArtistMemoCache.size >= ARTIST_MEMO_CACHE_LIMIT) {
        extractMainArtistMemoCache.clear()
    }
    extractMainArtistMemoCache[trimmed] = computed
    return computed
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
 * Determina se un brano deve essere classificato come Unreleased e raggruppato nell'album "Unreleased"
 * del proprio artista principale:
 * 1. Il tag album del file è già "Unreleased".
 * 2. L'album attuale è generico/assente (o "Brani singoli") E il brano è registrato in [unreleasedSnapshot]
 *    (non trovato su Deezer tramite Auto-Tag o scansione batch) oppure presenta un marcatore esplicito
 *    di inedito nel titolo/nome file/cartella.
 */
fun isTrackUnreleased(
    track: AudioTrack,
    unreleasedSnapshot: UnreleasedSnapshot = UnreleasedSnapshot.EMPTY
): Boolean {
    val rawAlbum = track.album.trim()
    if (isUnreleasedAlbumTitle(rawAlbum)) return true

    val hasOfficialAlbum = !isGenericAlbum(rawAlbum) && !rawAlbum.contains("(Brani singoli)")
    if (hasOfficialAlbum) return false

    if (!unreleasedSnapshot.isEmpty() && unreleasedSnapshot.matches(track)) {
        return true
    }

    if (QuerySanitizer.hasExplicitUnreleasedMarker(track.title) ||
        QuerySanitizer.hasExplicitUnreleasedMarker(track.displayName)
    ) {
        return true
    }

    val folderSegments = track.relativePath.split('/', '\\').map { it.trim().lowercase(java.util.Locale.ROOT) }
    return folderSegments.any { it == "unreleased" || it == "leaks" }
}

/**
 * Verifica se un album raggruppa tracce eterogenee (album diversi o titolo generico)
 * che necessitano di separazione/disaggregazione.
 */
fun Album.isMixedOrGeneric(): Boolean {
    // Un album "Unreleased" di un singolo artista è una raccolta intenzionale e non un album misto anomalo
    if (isUnreleasedAlbumTitle(title)) return false

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
 * Crea automaticamente un album "Unreleased" per singolo artista solo quando sono presenti
 * almeno un brano unreleased di quell'artista.
 */
fun groupTracksIntoAlbums(
    tracks: List<AudioTrack>,
    unreleasedSnapshot: UnreleasedSnapshot = UnreleasedSnapshot.EMPTY,
    artistPhotoUriProvider: ((String) -> Uri?)? = null
): List<Album> {
    val usedAlbumIds = mutableSetOf<Long>()
    val mainArtistCache = HashMap<String, String>()
    fun getMainArtist(artist: String?): String {
        if (artist.isNullOrBlank()) return ""
        return mainArtistCache.getOrPut(artist) { extractMainArtist(artist) }
    }

    fun resolveUnreleasedMainArtist(track: AudioTrack): String {
        val fromAlbumArtist = track.albumArtist.trim()
            .takeIf { it.isNotBlank() && !isVariousArtists(it) && !QuerySanitizer.isArtistUnknownOrDirty(it) }
            ?.let { getMainArtist(it) }
            .orEmpty()
        if (fromAlbumArtist.isNotBlank() && fromAlbumArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
            return fromAlbumArtist
        }

        val fromTrackArtist = track.artist.trim()
            .takeIf { it.isNotBlank() && !isVariousArtists(it) && !QuerySanitizer.isArtistUnknownOrDirty(it) }
            ?.let { getMainArtist(it) }
            .orEmpty()
        if (fromTrackArtist.isNotBlank() && fromTrackArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
            return fromTrackArtist
        }

        val fromTitleFallback = QuerySanitizer.extractFallbackArtistAndTitle(track.title)?.first
            ?.takeIf { !isVariousArtists(it) && !QuerySanitizer.isArtistUnknownOrDirty(it) }
            ?.let { getMainArtist(it) }
            .orEmpty()
        if (fromTitleFallback.isNotBlank() && fromTitleFallback.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
            return fromTitleFallback
        }

        val fromDisplayNameFallback = QuerySanitizer.cleanFileName(track.displayName).artist
            ?.takeIf { !isVariousArtists(it) && !QuerySanitizer.isArtistUnknownOrDirty(it) }
            ?.let { getMainArtist(it) }
            .orEmpty()
        if (fromDisplayNameFallback.isNotBlank() && fromDisplayNameFallback.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
            return fromDisplayNameFallback
        }

        return ""
    }

    return tracks.groupBy { track ->
        val rawAlbum = track.album.trim()
        val isUnreleased = isTrackUnreleased(track, unreleasedSnapshot)

        if (isUnreleased) {
            val mainArtist = resolveUnreleasedMainArtist(track)
            if (mainArtist.isNotBlank()) {
                "unreleased:artist:${mainArtist.lowercase(java.util.Locale.ROOT)}"
            } else {
                "unreleased:unknown"
            }
        } else {
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
        }
    }.map { (groupKey, albumTracks) ->
        val first = albumTracks.first()
        val isUnreleasedGroup = groupKey.startsWith("unreleased:")
        val isGeneric = !isUnreleasedGroup && isGenericAlbum(first.album)

        val albumTitle = when {
            isUnreleasedGroup -> UNRELEASED_ALBUM_TITLE
            isGeneric -> {
                val mainArtist = getMainArtist(first.artist)
                if (mainArtist.isNotBlank() && mainArtist.lowercase(java.util.Locale.ROOT) !in GENERIC_ALBUM_NAMES) {
                    "$mainArtist (Brani singoli)"
                } else {
                    first.title
                }
            }
            else -> first.album
        }

        val albumArtist = when {
            isUnreleasedGroup -> {
                resolveUnreleasedMainArtist(first).ifBlank {
                    getMainArtist(first.artist).ifBlank { first.artist }
                }
            }
            isGeneric -> {
                getMainArtist(first.artist).ifBlank { first.artist }
            }
            else -> {
                val rawAlbumArtist = first.albumArtist.trim().ifBlank { getMainArtist(first.artist) }
                if (isVariousArtists(rawAlbumArtist)) "Artisti Vari" else rawAlbumArtist.ifBlank { first.artist }
            }
        }

        // Calcolo ID univoco e stabile: per gli album sintetici "Unreleased" per-artista
        // generiamo sempre un ID deterministico basato su groupKey per evitare collisioni MediaStore
        val albumIdCandidates = albumTracks.map { it.albumId }.distinct()
        val primaryCandidate = if (!isUnreleasedGroup && albumIdCandidates.size == 1 && albumIdCandidates.first() > 0L) {
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

        val representativeArtUri = albumTracks.firstOrNull { it.albumArtUri?.scheme == "file" }?.albumArtUri
            ?: albumTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
            ?: first.albumArtUri
            ?: (if (isUnreleasedGroup && albumArtist.isNotBlank()) artistPhotoUriProvider?.invoke(albumArtist) else null)

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
    }.sortedWith(
        compareBy<Album> { it.title.lowercase(java.util.Locale.ROOT) }
            .thenBy { it.artist.lowercase(java.util.Locale.ROOT) }
    )
}
