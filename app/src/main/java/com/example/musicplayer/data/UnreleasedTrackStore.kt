package com.example.musicplayer.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot immutabile delle tracce contrassegnate come Unreleased, utilizzate per il raggruppamento
 * automatico nell'album "Unreleased" di ciascun artista anche senza sovrascrivere i file su disco.
 */
data class UnreleasedSnapshot(
    val trackIds: Set<Long> = emptySet(),
    val normalizedPaths: Set<String> = emptySet()
) {
    fun isEmpty(): Boolean = trackIds.isEmpty() && normalizedPaths.isEmpty()

    fun matches(track: AudioTrack): Boolean {
        if (track.id > 0L && track.id in trackIds) return true
        val pathKey = UnreleasedTrackStore.buildPathKey(track)
        return pathKey.isNotBlank() && pathKey in normalizedPaths
    }

    companion object {
        val EMPTY = UnreleasedSnapshot()
    }
}

/**
 * Archivio persistente locale e thread-safe dei brani rilevati o confermati come "Unreleased"
 * (es. non trovati su Deezer tramite Auto-Tag o durante la scansione batch dalle Impostazioni).
 */
object UnreleasedTrackStore {

    private const val PREFS_NAME = "unreleased_tracks_prefs"
    private const val KEY_TRACK_IDS = "track_ids"
    private const val KEY_TRACK_PATHS = "track_paths"

    private val trackIdsCache = ConcurrentHashMap.newKeySet<Long>()
    private val trackPathsCache = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val appContext = context.applicationContext ?: context
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val storedIds = prefs.getStringSet(KEY_TRACK_IDS, emptySet()).orEmpty()
                val storedPaths = prefs.getStringSet(KEY_TRACK_PATHS, emptySet()).orEmpty()
                storedIds.mapNotNull { it.toLongOrNull() }.forEach { trackIdsCache.add(it) }
                storedPaths.filter { it.isNotBlank() }.forEach { trackPathsCache.add(it) }
            } catch (_: Exception) {
            } finally {
                isInitialized = true
            }
        }
    }

    fun buildPathKey(track: AudioTrack): String {
        val display = track.displayName.trim()
        if (display.isBlank()) return ""
        val rel = track.relativePath.trim().replace('\\', '/').trim('/')
        return if (rel.isNotBlank()) {
            "$rel/$display".lowercase(Locale.ROOT)
        } else {
            display.lowercase(Locale.ROOT)
        }
    }

    fun getSnapshot(context: Context): UnreleasedSnapshot {
        ensureInitialized(context)
        return UnreleasedSnapshot(
            trackIds = trackIdsCache.toSet(),
            normalizedPaths = trackPathsCache.toSet()
        )
    }

    fun isMarkedUnreleased(context: Context, track: AudioTrack): Boolean {
        ensureInitialized(context)
        if (track.id > 0L && trackIdsCache.contains(track.id)) return true
        val pathKey = buildPathKey(track)
        return pathKey.isNotBlank() && trackPathsCache.contains(pathKey)
    }

    fun markUnreleased(context: Context, tracks: Collection<AudioTrack>) {
        if (tracks.isEmpty()) return
        ensureInitialized(context)
        synchronized(this) {
            var changed = false
            for (track in tracks) {
                if (track.id > 0L && trackIdsCache.add(track.id)) {
                    changed = true
                }
                val pathKey = buildPathKey(track)
                if (pathKey.isNotBlank() && trackPathsCache.add(pathKey)) {
                    changed = true
                }
            }
            if (changed) {
                persistLocked(context)
            }
        }
    }

    fun unmarkUnreleased(context: Context, tracks: Collection<AudioTrack>) {
        if (tracks.isEmpty()) return
        ensureInitialized(context)
        synchronized(this) {
            var changed = false
            for (track in tracks) {
                if (track.id > 0L && trackIdsCache.remove(track.id)) {
                    changed = true
                }
                val pathKey = buildPathKey(track)
                if (pathKey.isNotBlank() && trackPathsCache.remove(pathKey)) {
                    changed = true
                }
            }
            if (changed) {
                persistLocked(context)
            }
        }
    }

    fun removeDeletedTracks(context: Context, tracks: Collection<AudioTrack>) {
        unmarkUnreleased(context, tracks)
    }

    private fun persistLocked(context: Context) {
        try {
            val appContext = context.applicationContext ?: context
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_TRACK_IDS, trackIdsCache.map { it.toString() }.toSet())
                .putStringSet(KEY_TRACK_PATHS, trackPathsCache.toSet())
                .apply()
        } catch (_: Exception) {
        }
    }
}
