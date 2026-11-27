package com.example.musicplayer.data.tagger.sanitizer

import com.example.musicplayer.data.AudioTrack
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    /**
     * Rimuove il numero di traccia iniziale solo se seguito da un separatore esplicito (-, _, .)
     * oppure se è un numero a 2-3 cifre con zero iniziale (es. "01 Brano"), evitando di troncare
     * titoli autentici che iniziano con un numero (es. "21 Guns", "7 Rings", "99 Problems", "505", "1979").
     */
    private val LEADING_TRACK_NUMBER_REGEX = Regex(
        """^(?:\d{1,2}[-_.]\d{1,3}(?:\s*[-_.]+\s*|\s+)|\d{1,3}\s*[-_.]+\s*|0\d{1,2}\s+)"""
    )

    private val QUALITY_AND_BITRATE_REGEX = Regex(
        """(?i)\b(320\s*kbps|256\s*kbps|192\s*kbps|128\s*kbps|320k|128k|flac|lossless|24bit|vbr|cbr)\b"""
    )

    private val NOISE_TAGS_REGEX = Regex(
        """(?i)[\(\[]\s*(official\s*(music\s*)?(video|audio|lyric\s*video)?|lyrics?\s*(video)?|visualizer|clip\s*officiel|((hd|hq|4k|hi-res|master)\s*(audio|video)?)|audio|video|full\s*track|youtube\s*rip)\s*[\)\]]"""
    )

    private val TRAILING_DASH_NOISE_REGEX = Regex(
        """(?i)\s+[-–—−]\s+(official\s*(music\s*)?(video|audio|lyric\s*video)|lyric\s*video|official\s+visualizer|visualizer|youtube\s*rip)\s*$"""
    )

    private val TOPIC_CHANNEL_SUFFIX_REGEX = Regex(
        """(?i)\s+[-–—−]\s+topic\s*$"""
    )

    private val ARTIST_TITLE_SEPARATOR_REGEX = Regex("""\s+[-–—−]\s+""")
    private val ASYMMETRIC_SEPARATOR_REGEX = Regex("""(?:\s+[-–—−](?=\S)|(?<=\S)[-–—−]\s+)""")

    private val TITLE_VERSION_RIGHT_PART_REGEX = Regex(
        """(?i)^(?:part|pt\.?)\s*(?:[ivxlcdm]+|\d+).*$|^(?:acoustic|live|remix|mix|edit|radio\s*edit|extended|instrumental|karaoke|demo|version|ver\.?|remaster(?:ed)?(?:\s*\d{4})?|bonus\s*track|intro|outro|interlude|skit|sped\s*up|slowed(?:\s*(?:&|\+|and)\s*reverb)?)(?:\s+.*)?$"""
    )

    private val ALBUM_NORMALIZATION_REGEX = Regex(
        """(?i)\s*[\(\[]\s*(deluxe(\s*(edition|version))?|remaster(ed)?(\s*\d{4})?|(\d{1,2}(th|nd|rd|st)\s*)?anniversary(\s*edition)?|expanded(\s*edition)?|special\s*edition|bonus(\s*track)?\s*version|explicit(\s*version)?|clean(\s*version)?)\s*[\)\]]"""
    )

    private val FEAT_SUFFIX_REGEX = Regex(
        """(?i)(\s*[\(\[]\s*(feat\.?|ft\.?|featuring|with|con|prod\.?\s*by)\s+[^\)\]]+[\)\]]|\s+(feat\.?|ft\.?|featuring|con)\s+.*)$"""
    )

    private val ARTIST_SPLIT_REGEX = Regex(
        """(?i)(?:\s*(?:,|&|\+|/|;)\s*|\s+(?:feat\.?|ft\.?|featuring|with|con|vs\.?|x|and)\s+)"""
    )

    private val UNRELEASED_NOISE_TAGS_REGEX = Regex(
        """(?i)[\(\[]\s*(unreleased|leak|leaked|og\s*version|unmastered|snippet|vault)\s*[\)\]]"""
    )

    /**
     * Elenco ordinato (dal più lungo al più corto) di singoli artisti o band il cui nome canonico
     * contiene internamente una virgola (es. "Tyler, The Creator"), così da non spezzarli
     * erroneamente in due artisti distinti durante Auto-Tag, ricerca e raggruppamento album.
     */
    private val PROTECTED_COMMA_ARTIST_PATTERNS: List<Pair<String, Regex>> = listOf(
        "Crosby, Stills, Nash & Young" to Regex("""(?i)(?<![\p{L}\p{N}])crosby\s*,\s*stills\s*,\s*nash\s*(?:&|and)\s*young(?![\p{L}\p{N}])"""),
        "Crosby, Stills & Nash" to Regex("""(?i)(?<![\p{L}\p{N}])crosby\s*,\s*stills\s*(?:&|and)\s*nash(?![\p{L}\p{N}])"""),
        "Black Country, New Road" to Regex("""(?i)(?<![\p{L}\p{N}])black\s+country\s*,\s*new\s+road(?![\p{L}\p{N}])"""),
        "Emerson, Lake & Palmer" to Regex("""(?i)(?<![\p{L}\p{N}])emerson\s*,\s*lake\s*(?:&|and)\s*palmer(?![\p{L}\p{N}])"""),
        "Earth, Wind & Fire" to Regex("""(?i)(?<![\p{L}\p{N}])earth\s*,\s*wind\s*(?:&|and)\s*fire(?![\p{L}\p{N}])"""),
        "Blood, Sweat & Tears" to Regex("""(?i)(?<![\p{L}\p{N}])blood\s*,\s*sweat\s*(?:&|and)\s*tears(?![\p{L}\p{N}])"""),
        "Peter, Paul and Mary" to Regex("""(?i)(?<![\p{L}\p{N}])peter\s*,\s*paul\s*(?:&|and)\s*mary(?![\p{L}\p{N}])"""),
        "Tyler, The Creator" to Regex("""(?i)(?<![\p{L}\p{N}])tyler\s*,\s*the\s+creator(?![\p{L}\p{N}])"""),
        "Portugal, The Man" to Regex("""(?i)(?<![\p{L}\p{N}])portugal\s*,\s*the\s+man(?![\p{L}\p{N}])"""),
        "10,000 Maniacs" to Regex("""(?i)(?<![\p{L}\p{N}])10,000\s+maniacs(?![\p{L}\p{N}])"""),
        "Now, Now" to Regex("""(?i)(?<![\p{L}\p{N}])now\s*,\s*now(?![\p{L}\p{N}])""")
    )

    data class MaskedArtistResult(
        val maskedText: String,
        val placeholders: List<Pair<String, String>>
    ) {
        fun unmask(text: String): String {
            var result = text
            for ((token, original) in placeholders) {
                result = result.replace(token, original)
            }
            return result
        }
    }

    /**
     * Maschera temporaneamente i nomi d'arte protetti che contengono virgole interne
     * (es. "Tyler, The Creator") prima di applicare espressioni regolari di separazione
     * collaboratori/featuring, preservando la grafia originale o canonica.
     */
    fun maskProtectedArtists(input: String): MaskedArtistResult {
        if (input.isBlank() || !input.contains(',')) {
            return MaskedArtistResult(input, emptyList())
        }
        var working = input
        val placeholders = mutableListOf<Pair<String, String>>()
        for ((_, regex) in PROTECTED_COMMA_ARTIST_PATTERNS) {
            var match = regex.find(working)
            while (match != null) {
                val matchedValue = match.value
                val token = "\u0000PROTART${placeholders.size}\u0000"
                placeholders.add(token to matchedValue)
                working = working.replaceRange(match.range, token)
                match = regex.find(working)
            }
        }
        return MaskedArtistResult(working, placeholders)
    }

    private val UNKNOWN_TITLE_REGEX = Regex(
        """(?i)^(sconosciuto|unknown|untitled|<unknown>|(track|traccia|pista|brano|audio)\s*[-_#]?\s*\d+)$"""
    )

    private val UNKNOWN_ARTIST_SET = setOf(
        "artista sconosciuto",
        "unknown artist",
        "unknown",
        "<unknown>",
        "sconosciuto"
    )

    private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val EMPTY_BRACKETS_REGEX = Regex("""[\(\[]\s*[\)\]]""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    /**
     * Pulisce una stringa generica (es. nome file o titolo sporco) rimuovendo estensioni,
     * prefissi di numero traccia da file, diciture video/qualità e parentesi vuote.
     */
    fun cleanRawString(input: String): String {
        var text = input.trim()
        text = text.replace(AUDIO_EXTENSIONS_REGEX, "")
        text = text.replace(LEADING_TRACK_NUMBER_REGEX, "")
        text = text.replace(NOISE_TAGS_REGEX, "")
        text = text.replace(UNRELEASED_NOISE_TAGS_REGEX, "")
        text = text.replace(TRAILING_DASH_NOISE_REGEX, "")
        text = text.replace(QUALITY_AND_BITRATE_REGEX, "")
        text = text.replace(EMPTY_BRACKETS_REGEX, "")
        text = text.replace(MULTI_SPACE_REGEX, " ")
        return text.trim(' ', '-', '–', '—', '−', '_', '.')
    }

    /**
     * Separa una stringa nel formato "Artista - Canzone" (supportando trattini ASCII, en-dash, em-dash e minus),
     * preservando eventuali trattini interni ai nomi (es. "Jay-Z", "Ne-Yo", "blink-182").
     */
    fun splitArtistAndTitle(input: String, allowAsymmetric: Boolean = false): Pair<String, String>? {
        val cleaned = cleanRawString(input)
        if (cleaned.isBlank()) return null

        val match = ARTIST_TITLE_SEPARATOR_REGEX.find(cleaned)
            ?: (if (allowAsymmetric) ASYMMETRIC_SEPARATOR_REGEX.find(cleaned) else null)
            ?: return null

        val left = cleaned.substring(0, match.range.first).trim(' ', '-', '–', '—', '−', '_')
        val right = cleaned.substring(match.range.last + 1).trim(' ', '-', '–', '—', '−', '_')
        if (left.isBlank() || right.isBlank()) return null
        return Pair(left, right)
    }

    /**
     * Rimuove suffissi di featuring e di edizione/remaster per confrontare il nucleo del titolo.
     */
    fun stripFeaturingAndEdition(title: String): String {
        var text = cleanRawString(title)
        text = text.replace(FEAT_SUFFIX_REGEX, "")
        text = text.replace(ALBUM_NORMALIZATION_REGEX, "")
        text = text.replace(EMPTY_BRACKETS_REGEX, "")
        text = text.replace(MULTI_SPACE_REGEX, " ")
        return text.trim(' ', '-', '–', '—', '−', '_', '.')
    }

    /**
     * Estrae artista e titolo da un nome file (displayName).
     * Se è presente un delimitatore " - " (o trattini tipografici equivalenti) separa artista e titolo.
     */
    fun cleanFileName(displayName: String): CleanQuery {
        val cleaned = cleanRawString(displayName)

        val split = splitArtistAndTitle(cleaned, allowAsymmetric = true)
        if (split != null) {
            return CleanQuery(
                artist = split.first,
                title = split.second,
                rawCleaned = cleaned
            )
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
        val match1 = Regex("""^(?:(\d{1,2})[-_.])?(\d{1,3})\s*[-_.]+\s*""").find(trimmed)
        if (match1 != null) {
            val numStr = match1.groupValues[2].ifBlank { match1.groupValues[1] }
            val num = numStr.toIntOrNull()
            if (num != null && num in 1..999) return num
        }
        val matchZeroPadded = Regex("""^(?:(\d{1,2})[-_.])?(0\d{1,2})\s+""").find(trimmed)
        if (matchZeroPadded != null) {
            val num = matchZeroPadded.groupValues[2].toIntOrNull()
            if (num != null && num in 1..999) return num
        }
        return null
    }

    /**
     * Semplifica un testo per il confronto rimuovendo accenti/diacritici, punteggiatura, estensioni e caratteri speciali.
     */
    fun simplifyForComparison(title: String): String {
        val cleaned = cleanRawString(title).lowercase(Locale.ROOT)
        val deaccented = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
        return deaccented
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Confronta due titoli di tracce o album in modo accurato:
     * tollera differenze di accenti, punteggiatura, suffissi "(feat. ...)" o "(Remastered)",
     * ma impedisce falsi positivi tra brani diversi che condividono una sola parola breve.
     */
    fun titlesMatch(titleA: String, titleB: String): Boolean {
        if (titleA.isBlank() || titleB.isBlank()) return false
        if (titleA.trim().equals(titleB.trim(), ignoreCase = true)) return true

        val simpA = simplifyForComparison(titleA)
        val simpB = simplifyForComparison(titleB)
        if (simpA.isNotBlank() && simpA == simpB) return true

        val coreA = simplifyForComparison(stripFeaturingAndEdition(titleA))
        val coreB = simplifyForComparison(stripFeaturingAndEdition(titleB))
        if (coreA.isNotBlank() && coreA == coreB) return true

        // Confronto per prefisso su confine di parola solo se il titolo più corto ha almeno 5 caratteri
        val minLen = min(coreA.length, coreB.length)
        if (minLen >= 5) {
            val shorter = if (coreA.length <= coreB.length) coreA else coreB
            val longer = if (coreA.length <= coreB.length) coreB else coreA
            if (longer.startsWith("$shorter ")) {
                return true
            }
        }
        return false
    }

    /**
     * Verifica se due stringhe artista sono compatibili (stesso artista principale o almeno un collaboratore in comune).
     */
    fun artistsMatch(artistA: String, artistB: String): Boolean {
        if (artistA.isBlank() || artistB.isBlank()) return false
        val simpA = simplifyForComparison(artistA.replace(TOPIC_CHANNEL_SUFFIX_REGEX, ""))
        val simpB = simplifyForComparison(artistB.replace(TOPIC_CHANNEL_SUFFIX_REGEX, ""))
        if (simpA.isNotBlank() && simpA == simpB) return true

        val tokensA = extractArtistTokens(artistA)
        val tokensB = extractArtistTokens(artistB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false

        return tokensA.any { a ->
            tokensB.any { b ->
                val aNoThe = a.removePrefix("the ").takeIf { it.isNotBlank() && a.startsWith("the ") } ?: a
                val bNoThe = b.removePrefix("the ").takeIf { it.isNotBlank() && b.startsWith("the ") } ?: b
                a == b ||
                    aNoThe == bNoThe ||
                    (min(a.length, b.length) >= 3 && (a.startsWith("$b ") || b.startsWith("$a "))) ||
                    (min(aNoThe.length, bNoThe.length) >= 3 && (aNoThe.startsWith("$bNoThe ") || bNoThe.startsWith("$aNoThe ")))
            }
        }
    }

    private fun extractArtistTokens(artist: String): List<String> {
        val withoutTopic = artist.replace(TOPIC_CHANNEL_SUFFIX_REGEX, "")
        val withoutFeatParens = withoutTopic.replace(Regex("""[\(\[]\s*(feat\.?|ft\.?|featuring|with)\s+""", RegexOption.IGNORE_CASE), ", ")
            .replace(")", "")
            .replace("]", "")
        val masked = maskProtectedArtists(withoutFeatParens)
        return masked.maskedText.split(ARTIST_SPLIT_REGEX)
            .map { simplifyForComparison(masked.unmask(it)) }
            .filter { it.isNotBlank() && it !in UNKNOWN_ARTIST_SET }
    }

    /**
     * Determina se il titolo locale di una traccia è assente, generico ("Sconosciuto", "Track 01")
     * oppure "sporco" (contiene estensioni file, prefisso numerico da filename, tag "Official Video", "320kbps",
     * oppure il pattern grezzo "Artista - Titolo" o "Titolo - Artista" non separato).
     * Se restituisce `false`, il titolo locale è già pulito e attendibile.
     */
    /**
     * Rileva se un titolo o nome file contiene un marcatore esplicito di brano inedito/unreleased/leak.
     */
    fun hasExplicitUnreleasedMarker(text: String): Boolean {
        if (text.isBlank()) return false
        return UNRELEASED_NOISE_TAGS_REGEX.containsMatchIn(text) ||
            Regex("""(?i)(?:^|[\s\-–—_(.\[])(unreleased|leaked)(?:$|[\s\-–—_).\]]|\.(?:mp3|flac|m4a|ogg|wav|aac|opus|wma)$)""").containsMatchIn(text)
    }

    fun isTitleUnknownOrDirty(
        title: String,
        displayName: String = "",
        artist: String = "",
        albumArtist: String = ""
    ): Boolean {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return true
        if (UNKNOWN_TITLE_REGEX.matches(trimmed)) return true
        if (AUDIO_EXTENSIONS_REGEX.containsMatchIn(trimmed)) return true
        if (LEADING_TRACK_NUMBER_REGEX.containsMatchIn(trimmed)) return true
        if (NOISE_TAGS_REGEX.containsMatchIn(trimmed)) return true
        if (UNRELEASED_NOISE_TAGS_REGEX.containsMatchIn(trimmed)) return true
        if (TRAILING_DASH_NOISE_REGEX.containsMatchIn(trimmed)) return true
        if (QUALITY_AND_BITRATE_REGEX.containsMatchIn(trimmed)) return true

        val symmetricSplit = splitArtistAndTitle(trimmed, allowAsymmetric = false)
        val asymmetricSplit = symmetricSplit ?: splitArtistAndTitle(trimmed, allowAsymmetric = true)
        if (asymmetricSplit != null) {
            val (leftPart, rightPart) = asymmetricSplit
            val hasKnownArtist = !isArtistUnknownOrDirty(artist)
            val hasKnownAlbumArtist = !isArtistUnknownOrDirty(albumArtist)

            // 1. Il prefisso prima del trattino coincide con l'artista o con l'albumArtist ("Nome Artista - Canzone")
            if ((hasKnownArtist && artistsMatch(leftPart, artist)) ||
                (hasKnownAlbumArtist && artistsMatch(leftPart, albumArtist))
            ) {
                return true
            }

            // 2. Formato inverso sicuro ("Canzone - Nome Artista") con artista noto a destra
            val isVersionSuffix = TITLE_VERSION_RIGHT_PART_REGEX.matches(rightPart)
            if (!isVersionSuffix &&
                ((hasKnownArtist && artistsMatch(rightPart, artist)) ||
                    (hasKnownAlbumArtist && artistsMatch(rightPart, albumArtist)))
            ) {
                return true
            }

            // 3. Artista assente/sconosciuto: un separatore simmetrico (" - ", " – ", " — ") non di versione indica "Artista - Canzone"
            if (!hasKnownArtist && !hasKnownAlbumArtist && symmetricSplit != null && !isVersionSuffix) {
                return true
            }

            // 4. Il titolo coincide col nome file ripulito (es. tag TITLE copiato dal filename) e non è un suffisso di versione
            val displayNoExt = displayName.replace(AUDIO_EXTENSIONS_REGEX, "").trim()
            if (!isVersionSuffix && displayNoExt.isNotBlank() && trimmed.equals(displayNoExt, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Determina se l'artista locale è assente, generico ("Artista sconosciuto", "Unknown Artist") o sporco.
     */
    fun isArtistUnknownOrDirty(artist: String): Boolean {
        val trimmed = artist.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.lowercase(Locale.ROOT) in UNKNOWN_ARTIST_SET) return true
        if (AUDIO_EXTENSIONS_REGEX.containsMatchIn(trimmed)) return true
        if (NOISE_TAGS_REGEX.containsMatchIn(trimmed)) return true
        return false
    }

    /**
     * Pulisce il nome artista rimuovendo eventuali suffissi di canale (es. "- Topic") e rumore.
     */
    fun cleanArtistString(artist: String): String {
        return cleanRawString(artist.replace(TOPIC_CHANNEL_SUFFIX_REGEX, ""))
    }

    /**
     * Verifica se l'artista locale contiene già collaboratori/featuring validi (es. "Marracash feat. Guè" o "Salmo & Noyz Narcos")
     * compatibili con l'artista principale restituito da Deezer (che spesso espone solo il primo artista).
     */
    fun hasRicherArtistCredits(localArtist: String, candidateArtist: String): Boolean {
        if (isArtistUnknownOrDirty(localArtist) || candidateArtist.isBlank()) return false
        if (!artistsMatch(localArtist, candidateArtist)) return false
        val localTokens = extractArtistTokens(localArtist)
        val candidateTokens = extractArtistTokens(candidateArtist)
        return localTokens.size > candidateTokens.size
    }

    /**
     * Estrae l'artista principale (primo artista prima di feat., &, virgola, ecc.) per query mirate su Deezer.
     */
    fun extractPrimaryArtistForSearch(artist: String): String {
        if (isArtistUnknownOrDirty(artist)) return ""
        val withoutTopic = artist.replace(TOPIC_CHANNEL_SUFFIX_REGEX, "").trim()
        val withoutParens = withoutTopic.replace(FEAT_SUFFIX_REGEX, "").trim()
        val masked = maskProtectedArtists(withoutParens)
        val firstToken = masked.maskedText.split(ARTIST_SPLIT_REGEX)
            .firstOrNull()
            ?.let { masked.unmask(it) }
            ?.trim()
            .orEmpty()
        return cleanRawString(firstToken.ifBlank { withoutParens })
    }

    /**
     * Restituisce esclusivamente il nome del primo artista principale da utilizzare come Album Artist univoco,
     * rimuovendo eventuali co-autori/featuring (es. "Artista A feat. Artista B", "Artista A & B", "Artista A, B")
     * così che i brani dell'album non vengano frammentati in dischi separati.
     */
    fun extractPrimaryAlbumArtist(artist: String): String {
        val raw = cleanArtistString(artist).ifBlank { artist.trim() }
        if (raw.isBlank() || isArtistUnknownOrDirty(raw)) return ""
        return extractPrimaryArtistForSearch(raw).ifBlank { raw }
    }

    /**
     * Estrae un candidato alternativo (Artista, Canzone) da una stringa nel formato "Nome Artista - Canzone",
     * escludendo suffissi di versione/parte (es. "- Remix", "- Part I", "- Acoustic").
     */
    fun extractFallbackArtistAndTitle(title: String): Pair<String, String>? {
        val split = splitArtistAndTitle(title, allowAsymmetric = true) ?: return null
        val (leftPart, rightPart) = split
        if (TITLE_VERSION_RIGHT_PART_REGEX.matches(rightPart)) return null
        val cleanTitle = stripFeaturingAndEdition(rightPart).ifBlank { rightPart }
        val cleanArtist = cleanArtistString(leftPart).ifBlank { leftPart }
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        return Pair(cleanArtist, cleanTitle)
    }

    /**
     * Estrae un candidato alternativo (Artista, Canzone) nell'ipotesi inversa "Canzone - Nome Artista".
     */
    fun extractReversedTitleAndArtist(title: String): Pair<String, String>? {
        val split = splitArtistAndTitle(title, allowAsymmetric = false) ?: return null
        val (leftPart, rightPart) = split
        if (TITLE_VERSION_RIGHT_PART_REGEX.matches(rightPart)) return null
        val cleanTitle = stripFeaturingAndEdition(leftPart).ifBlank { leftPart }
        val cleanArtist = cleanArtistString(rightPart).ifBlank { rightPart }
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        return Pair(cleanArtist, cleanTitle)
    }

    /**
     * Restituisce la coppia (Artista, Titolo) migliore da usare per la ricerca online.
     * Se i tag ID3 correnti sono assenti, generici o contengono "Artista - Titolo" (o "Titolo - Artista"),
     * analizza ed estrae i termini puliti.
     */
    fun getEffectiveSearchTerms(track: AudioTrack): Pair<String, String> {
        val isTitleDirty = isTitleUnknownOrDirty(track.title, track.displayName, track.artist, track.albumArtist)
        val isArtistDirty = isArtistUnknownOrDirty(track.artist)

        if (isTitleDirty || isArtistDirty) {
            val fileNameClean = cleanFileName(track.displayName.ifBlank { track.title })
            val titleSplit = splitArtistAndTitle(track.title, allowAsymmetric = true)

            val isReversedFormat = titleSplit != null &&
                !TITLE_VERSION_RIGHT_PART_REGEX.matches(titleSplit.second) &&
                ((!isArtistDirty && !artistsMatch(titleSplit.first, track.artist) && artistsMatch(titleSplit.second, track.artist)) ||
                    (!isArtistUnknownOrDirty(track.albumArtist) && !artistsMatch(titleSplit.first, track.albumArtist) && artistsMatch(titleSplit.second, track.albumArtist)))

            val parsedFromTitleArtist = when {
                titleSplit == null -> null
                isReversedFormat -> cleanArtistString(titleSplit.second)
                else -> cleanArtistString(titleSplit.first)
            }
            val parsedFromTitleSong = when {
                titleSplit == null -> ""
                isReversedFormat -> titleSplit.first
                else -> titleSplit.second
            }

            val effectiveArtist = when {
                !isArtistDirty -> cleanArtistString(track.artist)
                !parsedFromTitleArtist.isNullOrBlank() -> parsedFromTitleArtist
                !fileNameClean.artist.isNullOrBlank() -> cleanArtistString(fileNameClean.artist)
                !isArtistUnknownOrDirty(track.albumArtist) -> cleanArtistString(track.albumArtist)
                else -> ""
            }
            val effectiveTitle = when {
                !isTitleDirty -> stripFeaturingAndEdition(track.title).ifBlank { cleanRawString(track.title) }
                titleSplit != null && parsedFromTitleSong.isNotBlank() ->
                    stripFeaturingAndEdition(parsedFromTitleSong).ifBlank { parsedFromTitleSong }
                !UNKNOWN_TITLE_REGEX.matches(track.title.trim()) && track.title.isNotBlank() ->
                    stripFeaturingAndEdition(track.title).ifBlank { cleanRawString(track.title) }
                fileNameClean.title.isNotBlank() ->
                    stripFeaturingAndEdition(fileNameClean.title).ifBlank { fileNameClean.title }
                else -> cleanRawString(track.displayName)
            }
            return Pair(effectiveArtist, effectiveTitle)
        }

        val cleanTitle = stripFeaturingAndEdition(track.title).ifBlank { track.title.trim() }
        return Pair(cleanArtistString(track.artist).ifBlank { track.artist.trim() }, cleanTitle)
    }
}
