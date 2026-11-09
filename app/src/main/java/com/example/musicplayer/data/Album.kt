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
 *
 * Consente di escludere coppie di file esplicitamente ignorate dall'utente tramite [ignoredDuplicateKeys]
 * (chiavi identificate per ID numerico `minId:maxId` o per percorso `minPath|maxPath`).
 */
fun Album.findDuplicateTrackGroups(ignoredDuplicateKeys: Set<String> = emptySet()): List<DuplicateTrackGroup> {
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

            val idKey = if (trackA.id < trackB.id) "${trackA.id}:${trackB.id}" else "${trackB.id}:${trackA.id}"
            val pathA = "${trackA.relativePath}/${trackA.displayName}"
            val pathB = "${trackB.relativePath}/${trackB.displayName}"
            val pathKey = if (pathA < pathB) "$pathA|$pathB" else "$pathB|$pathA"

            if (idKey in ignoredDuplicateKeys || (pathA.isNotBlank() && pathKey in ignoredDuplicateKeys)) {
                continue
            }

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

/**
 * Genera le chiavi identificative di tutte le coppie di tracce presenti in questo gruppo di duplicati.
 */
fun DuplicateTrackGroup.toPairKeys(): Set<String> {
    val keys = mutableSetOf<String>()
    for (i in tracks.indices) {
        for (j in i + 1 until tracks.size) {
            val tA = tracks[i]
            val tB = tracks[j]
            keys.add(if (tA.id < tB.id) "${tA.id}:${tB.id}" else "${tB.id}:${tA.id}")
            val pA = "${tA.relativePath}/${tA.displayName}"
            val pB = "${tB.relativePath}/${tB.displayName}"
            if (pA.isNotBlank() && pB.isNotBlank()) {
                keys.add(if (pA < pB) "$pA|$pB" else "$pB|$pA")
            }
        }
    }
    return keys
}

/**
 * Genera l'insieme combinato di chiavi per una lista di gruppi di duplicati.
 */
fun List<DuplicateTrackGroup>.toPairKeys(): Set<String> {
    val keys = mutableSetOf<String>()
    forEach { keys.addAll(it.toPairKeys()) }
    return keys
}
