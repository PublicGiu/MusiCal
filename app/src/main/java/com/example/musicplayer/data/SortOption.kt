package com.example.musicplayer.data

import com.example.musicplayer.R

/**
 * Criteri di ordinamento disponibili per la libreria musicale.
 */
enum class SortOption {
    RECENTLY_PLAYED,
    MOST_PLAYED,
    RECENTLY_ADDED,
    ALPHABETICAL;

    val label: String
        get() = getLabel(isAscending = true)

    fun getLabel(isAscending: Boolean): String = when (this) {
        RECENTLY_PLAYED -> "Ascoltati di recente"
        MOST_PLAYED -> "Più ascoltati"
        RECENTLY_ADDED -> "Aggiunti di recente"
        ALPHABETICAL -> if (isAscending) "Alfabetico (A-Z)" else "Alfabetico (Z-A)"
    }

    fun getLabelResId(isAscending: Boolean): Int = when (this) {
        RECENTLY_PLAYED -> R.string.sort_recently_played
        MOST_PLAYED -> R.string.sort_most_played
        RECENTLY_ADDED -> R.string.sort_recently_added
        ALPHABETICAL -> if (isAscending) R.string.sort_az else R.string.sort_za
    }
}

/**
 * Ordina una lista di [AudioTrack] in base al criterio selezionato, statistiche d'ascolto e direzione.
 */
fun List<AudioTrack>.sortTracks(
    sortOption: SortOption,
    stats: Map<Long, TrackStats> = emptyMap(),
    isAscending: Boolean = true
): List<AudioTrack> {
    val list = when (sortOption) {
        SortOption.ALPHABETICAL -> sortedBy { it.title.lowercase() }
        SortOption.RECENTLY_PLAYED -> sortedByDescending { stats[it.id]?.lastPlayedTimestamp ?: 0L }
        SortOption.RECENTLY_ADDED -> sortedByDescending { it.dateAdded }
        SortOption.MOST_PLAYED -> sortedByDescending { stats[it.id]?.playCount ?: 0 }
    }
    return if (isAscending) list else list.reversed()
}

/**
 * Ordina una lista di [Album] in base al criterio selezionato, statistiche d'ascolto e direzione.
 */
fun List<Album>.sortAlbums(
    sortOption: SortOption,
    stats: Map<Long, TrackStats> = emptyMap(),
    isAscending: Boolean = true
): List<Album> {
    val list = when (sortOption) {
        SortOption.ALPHABETICAL -> sortedBy { it.title.lowercase() }
        SortOption.RECENTLY_PLAYED -> sortedByDescending { it.tracks.maxOfOrNull { t -> stats[t.id]?.lastPlayedTimestamp ?: 0L } ?: 0L }
        SortOption.RECENTLY_ADDED -> sortedByDescending { it.tracks.maxOfOrNull { t -> t.dateAdded } ?: 0L }
        SortOption.MOST_PLAYED -> sortedByDescending { it.tracks.sumOf { t -> stats[t.id]?.playCount ?: 0 } }
    }
    return if (isAscending) list else list.reversed()
}

/**
 * Ordina una lista di [Playlist] in base al criterio selezionato, statistiche d'ascolto e direzione.
 * La playlist predefinita 'Brani preferiti' (FAVORITES_PLAYLIST_ID) viene sempre estratta,
 * le restanti playlist vengono ordinate secondo il criterio e la direzione specificati,
 * e 'Brani preferiti' viene riposizionata inderogabilmente all'indice 0.
 */
fun List<Playlist>.sortPlaylists(
    sortOption: SortOption,
    stats: Map<Long, TrackStats> = emptyMap(),
    isAscending: Boolean = true
): List<Playlist> {
    val favorites = find { it.id == FAVORITES_PLAYLIST_ID }
    val others = filterNot { it.id == FAVORITES_PLAYLIST_ID }

    val sortedOthers = when (sortOption) {
        SortOption.ALPHABETICAL -> others.sortedBy { it.name.lowercase() }
        SortOption.RECENTLY_PLAYED -> others.sortedByDescending { it.trackIds.maxOfOrNull { id -> stats[id]?.lastPlayedTimestamp ?: 0L } ?: 0L }
        SortOption.RECENTLY_ADDED -> others
        SortOption.MOST_PLAYED -> others.sortedByDescending { it.trackIds.sumOf { id -> stats[id]?.playCount ?: 0 } }
    }
    val directedOthers = if (isAscending) sortedOthers else sortedOthers.reversed()

    return if (favorites != null) {
        listOf(favorites) + directedOthers
    } else {
        directedOthers
    }
}


