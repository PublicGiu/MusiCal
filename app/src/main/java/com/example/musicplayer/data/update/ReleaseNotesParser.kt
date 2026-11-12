package com.example.musicplayer.data.update

import com.example.musicplayer.data.AppLanguage

/**
 * Risultato dell'analisi e suddivisione multilingua delle note di rilascio di GitHub.
 */
data class ParsedReleaseNotes(
    val raw: String,
    val italianNotes: String?,
    val englishNotes: String?
) {
    val hasBothLanguages: Boolean
        get() = !italianNotes.isNullOrBlank() && !englishNotes.isNullOrBlank()

    fun getNotes(isEnglish: Boolean): String {
        return if (isEnglish) {
            englishNotes?.takeIf { it.isNotBlank() }
                ?: italianNotes?.takeIf { it.isNotBlank() }
                ?: raw
        } else {
            italianNotes?.takeIf { it.isNotBlank() }
                ?: englishNotes?.takeIf { it.isNotBlank() }
                ?: raw
        }
    }
}

/**
 * Parser per l'estrazione intelligente di note di rilascio bilingue (Italiano ed Inglese)
 * dal corpo descrittivo delle GitHub Releases.
 *
 * Supporta molteplici convenzioni di pubblicazione:
 * 1. Tag HTML / commenti: `<!-- it --> ... <!-- /it -->` e `<!-- en --> ... <!-- /en -->` (o `<it> ... </it>`)
 * 2. Intestazioni Markdown e divisori: `## 🇬🇧 English` / `## 🇮🇹 Italiano`, `## EN` / `## IT`, `[EN]` / `[IT]`
 * 3. Euristica su singola lingua per changelog monolingua.
 */
object ReleaseNotesParser {

    private enum class Section { NONE, ITALIAN, ENGLISH }

    private val IT_TAG_REGEX = Regex(
        """(?:<!--\s*(?:it|ita|italian|italiano)\s*-->|<(?:it|ita|italian|italiano)>)([\s\S]*?)(?:<!--\s*/(?:it|ita|italian|italiano)\s*-->|</(?:it|ita|italian|italiano)>)""",
        RegexOption.IGNORE_CASE
    )

    private val EN_TAG_REGEX = Regex(
        """(?:<!--\s*(?:en|eng|english|inglese)\s*-->|<(?:en|eng|english|inglese)>)([\s\S]*?)(?:<!--\s*/(?:en|eng|english|inglese)\s*-->|</(?:en|eng|english|inglese)>)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Esegue il parsing del markdown grezzo della release.
     */
    fun parse(rawBody: String): ParsedReleaseNotes {
        val trimmed = rawBody.trim()
        if (trimmed.isBlank()) {
            return ParsedReleaseNotes(raw = "", italianNotes = null, englishNotes = null)
        }

        // 1. Verifica la presenza di tag espliciti HTML o commenti
        val itFromTag = IT_TAG_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()
        val enFromTag = EN_TAG_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()

        if (!itFromTag.isNullOrBlank() || !enFromTag.isNullOrBlank()) {
            return ParsedReleaseNotes(
                raw = trimmed,
                italianNotes = itFromTag?.takeIf { it.isNotBlank() },
                englishNotes = enFromTag?.takeIf { it.isNotBlank() }
            )
        }

        // 2. Verifica la suddivisione tramite intestazioni Markdown (# English, ## Italiano, ecc.)
        val sectionResult = parseBySectionHeaders(trimmed)
        if (sectionResult != null) {
            return sectionResult
        }

        // 3. Fallback: rilevamento euristico della lingua su contenuto monolingua
        val detected = detectMonolingualLanguage(trimmed)
        return when (detected) {
            AppLanguage.ITALIAN -> ParsedReleaseNotes(raw = trimmed, italianNotes = trimmed, englishNotes = null)
            AppLanguage.ENGLISH -> ParsedReleaseNotes(raw = trimmed, italianNotes = null, englishNotes = trimmed)
            else -> ParsedReleaseNotes(raw = trimmed, italianNotes = null, englishNotes = null)
        }
    }

    private fun parseBySectionHeaders(content: String): ParsedReleaseNotes? {
        val lines = content.lines()
        var hasAnyHeader = false

        for (line in lines) {
            if (isItalianHeader(line) || isEnglishHeader(line)) {
                hasAnyHeader = true
                break
            }
        }

        if (!hasAnyHeader) return null

        var current = Section.NONE
        val itLines = mutableListOf<String>()
        val enLines = mutableListOf<String>()

        for (line in lines) {
            if (isItalianHeader(line)) {
                current = Section.ITALIAN
                continue
            } else if (isEnglishHeader(line)) {
                current = Section.ENGLISH
                continue
            }

            when (current) {
                Section.ITALIAN -> itLines.add(line)
                Section.ENGLISH -> enLines.add(line)
                Section.NONE -> { /* Ignora eventuali titoli globali posti prima delle sezioni di lingua */ }
            }
        }

        val itText = cleanSectionText(itLines)
        val enText = cleanSectionText(enLines)

        if (itText.isBlank() && enText.isBlank()) return null

        return ParsedReleaseNotes(
            raw = content,
            italianNotes = itText.takeIf { it.isNotBlank() },
            englishNotes = enText.takeIf { it.isNotBlank() }
        )
    }

    private fun cleanSectionText(lines: List<String>): String {
        val joined = lines.joinToString("\n").trim()
        return joined
            .replace(Regex("""^(?:---|\*\*\*)\s*"""), "")
            .replace(Regex("""\s*(?:---|\*\*\*)$"""), "")
            .trim()
    }

    fun isItalianHeader(line: String): Boolean {
        val trimmed = line.trim()
        if (!isPotentialHeader(trimmed)) return false

        val normalized = normalizeHeader(trimmed)
        return normalized.contains("italiano") ||
                normalized.contains("italian") ||
                normalized == "it" ||
                normalized.contains("🇮🇹") ||
                normalized.contains(":it:") ||
                normalized.contains("(it)") ||
                normalized.contains("[it]") ||
                normalized.contains("novità (it)") ||
                normalized.contains("changelog (it)") ||
                normalized.contains("note di rilascio (it)")
    }

    fun isEnglishHeader(line: String): Boolean {
        val trimmed = line.trim()
        if (!isPotentialHeader(trimmed)) return false

        val normalized = normalizeHeader(trimmed)
        return normalized.contains("english") ||
                normalized.contains("inglese") ||
                normalized == "en" ||
                normalized.contains("🇬🇧") ||
                normalized.contains("🇺🇸") ||
                normalized.contains(":gb:") ||
                normalized.contains(":uk:") ||
                normalized.contains("(en)") ||
                normalized.contains("[en]") ||
                normalized.contains("what's new (en)") ||
                normalized.contains("whats new (en)") ||
                normalized.contains("changelog (en)") ||
                normalized.contains("release notes (en)")
    }

    private fun isPotentialHeader(line: String): Boolean {
        return line.startsWith("#") ||
                line.startsWith("[") ||
                line.startsWith("---") ||
                line.startsWith("**")
    }

    private fun normalizeHeader(line: String): String {
        return line
            .replace(Regex("""^[#\s\-\*\[\]]+"""), "")
            .replace(Regex("""[#\s\-\*\[\]]+$"""), "")
            .trim()
            .lowercase()
    }

    private fun detectMonolingualLanguage(text: String): AppLanguage? {
        val lower = text.lowercase()
        val itTokens = listOf(
            "novità", "corretto", "risolto", "aggiunto", "aggiunta", "miglioramenti",
            "funzionalità", "brani", "schermata", "questa versione", "eliminato",
            "riproduzione", "impostazioni", "preferiti"
        )
        val enTokens = listOf(
            "what's new", "whats new", "fixed", "resolved", "added", "improvements",
            "features", "tracks", "songs", "screen", "this version", "removed",
            "playback", "settings", "favorites", "changelog"
        )

        val itScore = itTokens.count { lower.contains(it) }
        val enScore = enTokens.count { lower.contains(it) }

        return when {
            itScore > enScore && itScore >= 1 -> AppLanguage.ITALIAN
            enScore > itScore && enScore >= 1 -> AppLanguage.ENGLISH
            else -> null
        }
    }
}
