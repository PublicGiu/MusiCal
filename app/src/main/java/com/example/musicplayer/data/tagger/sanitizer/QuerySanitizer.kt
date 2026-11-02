package com.example.musicplayer.data.tagger.sanitizer

import com.example.musicplayer.data.AudioTrack
import java.util.Locale
import kotlin.math.abs

/**
 * Risultato della pulizia euristica del nome file o tag audio.
 */
data class CleanQuery(
    val artist: String?,
    val title: String,
    val rawCleaned: String
)

/**
 * Utility per la sanitizzazione di query di ricerca, pulizia nomi file sporchi
 * e normalizzazione dei titoli degli album per evitare frammentazioni.
 */
object QuerySanitizer {

    private val AUDIO_EXTENSIONS_REGEX = Regex("""\.(mp3|flac|m4a|ogg|wav|aac|opus|wma)$""", RegexOption.IGNORE_CASE)

    private val LEADING_TRACK_NUMBER_REGEX = Regex("""^(\d{1,3}[\s._-]+|\d{1,2}\.\s*)""")

    private val QUALITY_AND_BITRATE_REGEX = Regex(
        """(?i)(320\s*kbps|256\s*kbps|192\s*kbps|128\s*kbps|320k|128k|flac|lossless|24bit|vbr|cbr)"""
    )

    private val NOISE_TAGS_REGEX = Regex(
        """(?i)[\(\[]\s*(official\s*(music\s*)?(video|audio)|lyrics?|visualizer|clip\s*officiel|((hd|hq|4k|hi-res|master)\s*(audio|video)?)|audio|video|full\s*track)\s*[\)\]]"""
    )

    private val ALBUM_NORMALIZATION_REGEX = Regex(
        """(?i)\s*[\(\[]\s*(deluxe(\s*edition)?|remaster(ed)?(\s*\d{4})?|(\d{1,2}(th|nd|rd|st)\s*)?anniversary(\s*edition)?|expanded(\s*edition)?|special\s*edition|bonus(\s*track)?\s*version)\s*[\)\]]"""
    )

    private val EMPTY_BRACKETS_REGEX = Regex("""[\(\[]\s*[\)\]]""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    /**
     * Pulisce una stringa generica (es. nome file o titolo sporco) rimuovendo estensioni,
     * numeri traccia iniziali, diciture video/qualità e parentesi vuote.
     */
    fun cleanRawString(input: String): String {
        var text = input.trim()
        text = text.replace(AUDIO_EXTENSIONS_REGEX, "")
        text = text.replace(LEADING_TRACK_NUMBER_REGEX, "")
        text = text.replace(NOISE_TAGS_REGEX, "")
        text = text.replace(QUALITY_AND_BITRATE_REGEX, "")
        text = text.replace(EMPTY_BRACKETS_REGEX, "")
        text = text.replace(MULTI_SPACE_REGEX, " ")
        return text.trim(' ', '-', '_', '.')
    }

    /**
     * Estrae artista e titolo da un nome file (displayName).
     * Se è presente un delimitatore " - " o " _ ", separa artista e titolo.
     */
    fun cleanFileName(displayName: String): CleanQuery {
        val cleaned = cleanRawString(displayName)

        // Verifichiamo la presenza del pattern standard "Artista - Titolo"
        val dashIndex = cleaned.indexOf(" - ")
        if (dashIndex > 0 && dashIndex < cleaned.length - 3) {
            val artistPart = cleaned.substring(0, dashIndex).trim(' ', '-', '_')
            val titlePart = cleaned.substring(dashIndex + 3).trim(' ', '-', '_')
            if (artistPart.isNotBlank() && titlePart.isNotBlank()) {
                return CleanQuery(
                    artist = artistPart,
                    title = titlePart,
                    rawCleaned = cleaned
                )
            }
        }

        return CleanQuery(
            artist = null,
            title = cleaned.ifBlank { displayName },
            rawCleaned = cleaned
        )
    }

    /**
     * Normalizza il titolo di un album rimuovendo suffissi quali "(Deluxe Edition)" o "[Remastered 2023]",
     * consentendo di unificare le tracce sotto il titolo canonico del disco in studio.
     */
    fun normalizeAlbumTitle(rawAlbumTitle: String): String {
        var normalized = rawAlbumTitle.replace(ALBUM_NORMALIZATION_REGEX, "")
        normalized = normalized.replace(EMPTY_BRACKETS_REGEX, "")
        normalized = normalized.replace(MULTI_SPACE_REGEX, " ")
        return normalized.trim()
    }

    /**
     * Determina se la durata reale del file locale (in ms) corrisponde alla durata del candidato online (in sec)
     * entro la tolleranza specificata (default 3 secondi).
     */
    fun isDurationMatching(actualDurationMs: Long, candidateDurationSec: Long, toleranceSec: Long = 3): Boolean {
        if (actualDurationMs <= 0 || candidateDurationSec <= 0) return true
        val actualSec = actualDurationMs / 1000
        return abs(actualSec - candidateDurationSec) <= toleranceSec
    }

    /**
     * Estrae un eventuale numero di traccia all'inizio del nome file (es. "01 - Track.mp3", "04. Track.mp3", "1-05 Song.flac").
     */
    fun extractTrackNumberFromFileName(displayName: String): Int? {
        val trimmed = displayName.trim()
        val match1 = Regex("""^(?:(\d{1,2})[-_.])?(\d{1,3})[\s._-]+""").find(trimmed)
        if (match1 != null) {
            val numStr = match1.groupValues[2].ifBlank { match1.groupValues[1] }
            val num = numStr.toIntOrNull()
            if (num != null && num in 1..999) return num
        }
        val match2 = Regex("""^(\d{1,2})\.\s*""").find(trimmed)
        if (match2 != null) {
            val num = match2.groupValues[1].toIntOrNull()
            if (num != null && num in 1..999) return num
        }
        return null
    }

    /**
     * Semplifica un titolo per il confronto rimuovendo punteggiatura, estensioni e caratteri speciali.
     */
    fun simplifyForComparison(title: String): String {
        val cleaned = cleanRawString(title).lowercase(Locale.ROOT)
        return cleaned.replace(Regex("""[^a-z0-9\s]"""), "").replace(Regex("""\s{2,}"""), " ").trim()
    }

    /**
     * Confronta due titoli di tracce in modo tollerante rispetto a variazioni di remaster, feat. e punteggiatura.
     */
    fun titlesMatch(titleA: String, titleB: String): Boolean {
        if (titleA.equals(titleB, ignoreCase = true)) return true
        val cleanA = cleanRawString(titleA)
        val cleanB = cleanRawString(titleB)
        if (cleanA.equals(cleanB, ignoreCase = true)) return true

        val simpA = simplifyForComparison(titleA)
        val simpB = simplifyForComparison(titleB)
        if (simpA.isNotBlank() && simpA == simpB) return true

        // Contenimento per stringhe significative (>= 4 caratteri)
        if (simpA.length >= 4 && simpB.length >= 4) {
            if (simpA.startsWith(simpB) || simpB.startsWith(simpA)) return true
            if (simpA.contains(simpB) || simpB.contains(simpA)) return true
        }
        return false
    }

    /**
     * Restituisce la coppia (Artista, Titolo) migliore da usare per la ricerca online.
     * Se i tag ID3 correnti sono assenti o generici (<unknown>, "Sconosciuto"),
     * analizza ed estrae i termini dal nome del file (displayName).
     */
    fun getEffectiveSearchTerms(track: AudioTrack): Pair<String, String> {
        val isTitleUnknown = track.title.isBlank() ||
                track.title.equals("Sconosciuto", ignoreCase = true) ||
                track.title.equals("Unknown", ignoreCase = true) ||
                track.title.startsWith("track", ignoreCase = true)

        val isArtistUnknown = track.artist.isBlank() ||
                track.artist.equals("Artista sconosciuto", ignoreCase = true) ||
                track.artist.equals("Unknown Artist", ignoreCase = true) ||
                track.artist.equals("Unknown", ignoreCase = true)

        if (isTitleUnknown || isArtistUnknown) {
            val fileNameClean = cleanFileName(track.displayName.ifBlank { track.title })
            val effectiveArtist = when {
                !isArtistUnknown -> cleanRawString(track.artist)
                fileNameClean.artist != null -> fileNameClean.artist
                else -> ""
            }
            val effectiveTitle = when {
                !isTitleUnknown -> cleanRawString(track.title)
                fileNameClean.title.isNotBlank() -> fileNameClean.title
                else -> cleanRawString(track.displayName)
            }
            return Pair(effectiveArtist, effectiveTitle)
        }

        return Pair(cleanRawString(track.artist), cleanRawString(track.title))
    }
}
