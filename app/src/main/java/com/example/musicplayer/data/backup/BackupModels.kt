package com.example.musicplayer.data.backup

/**
 * Riferimento portabile a un brano audio per il backup locale.
 * Non dipende da ID interni di Room o URI di MediaStore.
 */
data class BackupTrackRef(
    val relativePath: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

/**
 * Riepilogo dettagliato dell'operazione di importazione del backup.
 */
data class ImportSummary(
    val importedPlaylistsCount: Int,
    val updatedPlaylistsCount: Int,
    val favoritesAddedCount: Int,
    val restoredStatsCount: Int,
    val restoredPendingScrobblesCount: Int,
    val preferencesRestored: Boolean,
    val unmatchedTrackLabels: List<String>
) {
    val totalUnmatchedCount: Int
        get() = unmatchedTrackLabels.size
}

/**
 * Codici di errore tipizzati per il backup/ripristino locale,
 * mappati a messaggi localizzati sicuri senza esporre dettagli interni dell'eccezione.
 */
enum class BackupErrorCode {
    FILE_TOO_LARGE,
    UNSUPPORTED_VERSION,
    INVALID_FORMAT,
    IO_ERROR
}

class BackupException(
    val code: BackupErrorCode,
    cause: Throwable? = null
) : IllegalArgumentException(code.name, cause)
