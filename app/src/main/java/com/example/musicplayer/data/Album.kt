package com.example.musicplayer.data

import android.net.Uri
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import kotlin.math.abs

/**
 * Modello dati che rappresenta un Album musicale con le relative tracce.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val tracks: List<AudioTrack>
) {
    val trackCount: Int get() = tracks.size
}

/**
 * Gruppo di tracce duplicate identificate all'interno di un album.
 */
data class DuplicateTrackGroup(
    val canonicalTitle: String,
    val tracks: List<AudioTrack>
)

/**
 * Rileva i brani duplicati all'interno dell'album.
 * Criterio di rilevamento: stesso titolo (normalizzato senza maiuscole o simboli di rumore)
 * e durata compatibile (differenza <= 5 secondi oppure durata non specificata).
 */
fun Album.findDuplicateTrackGroups(): List<DuplicateTrackGroup> {
    if (tracks.size < 2) return emptyList()

    val visited = mutableSetOf<Long>()
    val groups = mutableListOf<DuplicateTrackGroup>()

    for (i in tracks.indices) {
        val trackA = tracks[i]
        if (trackA.id in visited) continue

        val matching = mutableListOf<AudioTrack>()
        matching.add(trackA)

        val cleanTitleA = QuerySanitizer.simplifyForComparison(trackA.title)

        for (j in i + 1 until tracks.size) {
            val trackB = tracks[j]
            if (trackB.id in visited) continue

            val cleanTitleB = QuerySanitizer.simplifyForComparison(trackB.title)

            val isTitleMatch = QuerySanitizer.titlesMatch(trackA.title, trackB.title) ||
                    (cleanTitleA.isNotBlank() && cleanTitleA == cleanTitleB)

            val isDurationMatch = trackA.duration <= 0L || trackB.duration <= 0L ||
                    abs(trackA.duration - trackB.duration) <= 5000L

            if (isTitleMatch && isDurationMatch) {
                matching.add(trackB)
            }
        }

        if (matching.size > 1) {
            matching.forEach { visited.add(it.id) }
            groups.add(DuplicateTrackGroup(canonicalTitle = trackA.title, tracks = matching))
        }
    }

    return groups
}
