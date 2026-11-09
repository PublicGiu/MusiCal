package com.example.musicplayer.data

import android.net.Uri
import java.util.Locale

/**
 * Modello dati per un brano musicale estratto da MediaStore.
 */
data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long = 0L,
    val album: String = "Sconosciuto",
    val albumArtUri: Uri? = null,
    val dateAdded: Long = 0L,
    val genre: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val albumArtist: String = "",
    val displayName: String = "",
    val relativePath: String = ""
) {
    /**
     * Formatta la durata del brano in formato mm:ss o hh:mm:ss.
     */
    fun formattedDuration(): String = formatDurationMs(duration)

    /**
     * Determina euristicamente se il brano è strumentale in base a titolo, nome file o genere.
     */
    fun isInstrumental(): Boolean {
        val lowerTitle = title.lowercase(Locale.ROOT)
        val lowerName = displayName.lowercase(Locale.ROOT)
        return lowerTitle.contains("(instrumental)") ||
                lowerTitle.contains("[instrumental]") ||
                lowerTitle.contains("- instrumental") ||
                lowerTitle.contains("(strumentale)") ||
                lowerTitle.contains("[strumentale]") ||
                lowerTitle.contains("- strumentale") ||
                lowerName.contains("(instrumental)") ||
                lowerName.contains("[instrumental]") ||
                lowerName.contains("(strumentale)") ||
                lowerName.contains("[strumentale]") ||
                genre.equals("Instrumental", ignoreCase = true) ||
                genre.equals("Strumentale", ignoreCase = true)
    }
}

/**
 * Formatta una durata in millisecondi nel formato mm:ss (o h:mm:ss se supera 60 minuti).
 */
fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        val remainingMinutes = minutes % 60
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }
}
