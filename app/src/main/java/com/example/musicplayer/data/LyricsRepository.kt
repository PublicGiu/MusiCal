package com.example.musicplayer.data

import android.content.Context
import java.io.File

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val trackId: Long,
    val lines: List<LyricLine>,
    val rawLyrics: String,
    val isSynced: Boolean
)

/**
 * Gestisce il caricamento e parsing dei testi (.lrc con timestamp [mm:ss.xx] o testo semplice).
 */
class LyricsRepository(private val context: Context) {

    private val timeTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    /**
     * Carica i testi salvati localmente per una data traccia.
     */
    fun getLyricsForTrack(trackId: Long): LyricsData? {
        val lrcFile = File(context.filesDir, "lyrics/$trackId.lrc")
        if (!lrcFile.exists() || lrcFile.length() == 0L) return null

        val raw = try {
            lrcFile.readText()
        } catch (_: Exception) {
            return null
        }

        return parseLyrics(trackId, raw)
    }

    /**
     * Esegue il parsing di una stringa .lrc estraendo i timestamp in millisecondi.
     */
    fun parseLyrics(trackId: Long, raw: String): LyricsData {
        val lines = mutableListOf<LyricLine>()
        var foundTimestamp = false

        raw.lines().forEach { line ->
            val match = timeTagRegex.find(line)
            if (match != null) {
                foundTimestamp = true
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val millisPart = match.groupValues.getOrNull(3).orEmpty()
                val millis = when (millisPart.length) {
                    1 -> (millisPart.toLongOrNull() ?: 0L) * 100
                    2 -> (millisPart.toLongOrNull() ?: 0L) * 10
                    3 -> millisPart.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val totalTimeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                val textContent = line.replace(timeTagRegex, "").trim()
                if (textContent.isNotBlank()) {
                    lines.add(LyricLine(totalTimeMs, textContent))
                }
            } else if (line.trim().isNotBlank()) {
                lines.add(LyricLine(-1L, line.trim()))
            }
        }

        lines.sortBy { if (it.timeMs >= 0) it.timeMs else Long.MAX_VALUE }

        return LyricsData(
            trackId = trackId,
            lines = lines,
            rawLyrics = raw,
            isSynced = foundTimestamp
        )
    }

    /**
     * Regola l'offset temporale dei versi sincronizzati di un brano (+/- millisecondi)
     * e risalva il file .lrc modificato sul disco.
     */
    fun adjustOffset(trackId: Long, offsetDeltaMs: Long): LyricsData? {
        val current = getLyricsForTrack(trackId) ?: return null
        if (!current.isSynced || current.lines.isEmpty()) return current

        val adjustedLines = current.lines.map { line ->
            if (line.timeMs >= 0) {
                val newTime = (line.timeMs + offsetDeltaMs).coerceAtLeast(0L)
                LyricLine(newTime, line.text)
            } else {
                line
            }
        }

        val sb = StringBuilder()
        adjustedLines.forEach { line ->
            if (line.timeMs >= 0) {
                val minutes = line.timeMs / 60000
                val seconds = (line.timeMs % 60000) / 1000
                val hundredths = (line.timeMs % 1000) / 10
                sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", minutes, seconds, hundredths, line.text))
            } else {
                sb.append("${line.text}\n")
            }
        }

        val lrcFile = File(context.filesDir, "lyrics/$trackId.lrc")
        try {
            lrcFile.parentFile?.mkdirs()
            lrcFile.writeText(sb.toString())
        } catch (_: Exception) {
            return null
        }

        return current.copy(
            lines = adjustedLines,
            rawLyrics = sb.toString()
        )
    }
}
