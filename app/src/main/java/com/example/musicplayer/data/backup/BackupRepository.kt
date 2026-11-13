package com.example.musicplayer.data.backup

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.AppLanguage
import com.example.musicplayer.data.AppPreferences
import com.example.musicplayer.data.AudioSourceMode
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.FAVORITES_PLAYLIST_ID
import com.example.musicplayer.data.FAVORITES_PLAYLIST_NAME
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.StatsRepository
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.lastfm.cache.PendingScrobbleDao
import com.example.musicplayer.data.lastfm.cache.PendingScrobbleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Gestisce esportazione e importazione sicura del backup locale in formato JSON.
 * Esclude intenzionalmente credenziali e chiavi di sessione (Last.fm / AndroidKeyStore).
 */
class BackupRepository(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val playlistRepository: PlaylistRepository,
    private val statsRepository: StatsRepository,
    private val pendingScrobbleDao: PendingScrobbleDao
) {
    companion object {
        const val BACKUP_VERSION = 1
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 // 10 MB
        private const val DURATION_MATCH_TOLERANCE_MS = 2_000L
        private const val MAX_STRING_LENGTH = 500
    }

    suspend fun exportBackupToUri(
        uri: Uri,
        libraryTracks: List<AudioTrack>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildBackupJson(libraryTracks)
            val bytes = json.toString(2).toByteArray(StandardCharsets.UTF_8)
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw BackupException(BackupErrorCode.IO_ERROR)
            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        }
    }

    suspend fun importBackupFromUri(
        uri: Uri,
        libraryTracks: List<AudioTrack>
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw BackupException(BackupErrorCode.IO_ERROR)
            val text = inputStream.use { readBoundedUtf8(it) }
            val root = try {
                JSONObject(text)
            } catch (e: org.json.JSONException) {
                throw BackupException(BackupErrorCode.INVALID_FORMAT, e)
            }
            importBackupJson(root, libraryTracks)
        }
    }

    internal suspend fun buildBackupJson(libraryTracks: List<AudioTrack>): JSONObject {
        val playlists = playlistRepository.loadPlaylists()
        val statsMap = statsRepository.loadStats()
        val pendingScrobbles = pendingScrobbleDao.getAllPendingAsc()
        val trackById = libraryTracks.associateBy { it.id }

        val playlistsArray = JSONArray()
        for (playlist in playlists) {
            val tracksArray = JSONArray()
            for (trackId in playlist.trackIds) {
                val track = trackById[trackId] ?: continue
                tracksArray.put(trackToJson(track))
            }
            playlistsArray.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("isFavorites", playlist.isFavorites)
                    put("tracks", tracksArray)
                }
            )
        }

        val statsArray = JSONArray()
        for ((audioId, stats) in statsMap) {
            val track = trackById[audioId] ?: continue
            if (stats.playCount <= 0 && stats.lastPlayedTimestamp <= 0L) continue
            statsArray.put(
                JSONObject().apply {
                    put("track", trackToJson(track))
                    put("playCount", stats.playCount)
                    put("lastPlayedTimestamp", stats.lastPlayedTimestamp)
                }
            )
        }

        val pendingArray = JSONArray()
        for (pending in pendingScrobbles) {
            pendingArray.put(
                JSONObject().apply {
                    put("artist", pending.artist)
                    put("track", pending.track)
                    put("album", pending.album)
                    put("timestamp", pending.timestamp)
                }
            )
        }

        val prefsObj = JSONObject().apply {
            put("autoplayEnabled", appPreferences.isAutoplayEnabled())
            put("themeMode", appPreferences.getThemeMode().name)
            put("appLanguage", appPreferences.getAppLanguage().name)
            put("dynamicColorEnabled", appPreferences.isDynamicColorEnabled())
            put("audioSourceMode", appPreferences.getAudioSourceMode().name)
            put("customFolders", JSONArray(appPreferences.getCustomFolders().sorted()))
            put("equalizerEnabled", appPreferences.isEqualizerEnabled())
            put("equalizerPreset", appPreferences.getEqualizerPreset())
            appPreferences.getEqualizerBandLevels()?.let { put("equalizerBandLevels", it) }
            put("bassBoostStrength", appPreferences.getBassBoostStrength())
            put("virtualizerStrength", appPreferences.getVirtualizerStrength())
            put("ignoredDuplicateKeys", JSONArray(appPreferences.getIgnoredDuplicateKeys().sorted()))
        }

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", Instant.now().toString())
            put("playlists", playlistsArray)
            put("listeningStats", statsArray)
            put("pendingScrobbles", pendingArray)
            put("preferences", prefsObj)
        }
    }

    internal suspend fun importBackupJson(
        root: JSONObject,
        libraryTracks: List<AudioTrack>
    ): ImportSummary {
        validateBackupSchema(root)

        val matcher = TrackMatcher(libraryTracks)
        val unmatchedLabels = linkedSetOf<String>()

        val (importedPlaylists, updatedPlaylists, favoritesAdded) =
            restorePlaylists(root.optJSONArray("playlists"), matcher, unmatchedLabels)

        val restoredStatsCount =
            restoreListeningStats(root.optJSONArray("listeningStats"), matcher, unmatchedLabels)

        val restoredPendingCount =
            restorePendingScrobbles(root.optJSONArray("pendingScrobbles"))

        val preferencesRestored =
            restorePreferences(root.optJSONObject("preferences"))

        return ImportSummary(
            importedPlaylistsCount = importedPlaylists,
            updatedPlaylistsCount = updatedPlaylists,
            favoritesAddedCount = favoritesAdded,
            restoredStatsCount = restoredStatsCount,
            restoredPendingScrobblesCount = restoredPendingCount,
            preferencesRestored = preferencesRestored,
            unmatchedTrackLabels = unmatchedLabels.toList()
        )
    }

    private fun validateBackupSchema(root: JSONObject) {
        val version = root.optInt("version", -1)
        if (version !in 1..BACKUP_VERSION) {
            throw BackupException(BackupErrorCode.UNSUPPORTED_VERSION)
        }

        val hasAnySection = root.has("playlists") ||
            root.has("listeningStats") ||
            root.has("pendingScrobbles") ||
            root.has("preferences")
        if (!hasAnySection) {
            throw BackupException(BackupErrorCode.INVALID_FORMAT)
        }

        if (root.has("playlists") && root.optJSONArray("playlists") == null) {
            throw BackupException(BackupErrorCode.INVALID_FORMAT)
        }
        if (root.has("listeningStats") && root.optJSONArray("listeningStats") == null) {
            throw BackupException(BackupErrorCode.INVALID_FORMAT)
        }
        if (root.has("pendingScrobbles") && root.optJSONArray("pendingScrobbles") == null) {
            throw BackupException(BackupErrorCode.INVALID_FORMAT)
        }
        if (root.has("preferences") && root.optJSONObject("preferences") == null) {
            throw BackupException(BackupErrorCode.INVALID_FORMAT)
        }
    }

    private fun restorePlaylists(
        playlistsArray: JSONArray?,
        matcher: TrackMatcher,
        unmatchedLabels: MutableSet<String>
    ): Triple<Int, Int, Int> {
        if (playlistsArray == null || playlistsArray.length() == 0) {
            return Triple(0, 0, 0)
        }

        val existing = playlistRepository.loadPlaylists().toMutableList()
        var importedCount = 0
        var updatedCount = 0
        var favoritesAddedCount = 0
        var modified = false

        for (i in 0 until playlistsArray.length()) {
            val obj = playlistsArray.optJSONObject(i) ?: continue
            val rawId = sanitize(obj.optString("id", ""))
            val rawName = sanitize(obj.optString("name", ""))
            val isFavorites = obj.optBoolean("isFavorites", false) || rawId == FAVORITES_PLAYLIST_ID
            val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()

            val matchedIds = ArrayList<Long>(tracksArray.length())
            val seenMatched = HashSet<Long>(tracksArray.length())
            for (j in 0 until tracksArray.length()) {
                val trackObj = tracksArray.optJSONObject(j) ?: continue
                val ref = jsonToTrackRef(trackObj)
                val match = matcher.match(ref)
                if (match != null) {
                    if (seenMatched.add(match.id)) {
                        matchedIds.add(match.id)
                    }
                } else {
                    unmatchedLabels.add(formatUnmatchedLabel(ref))
                }
            }

            if (isFavorites) {
                val favIndex = existing.indexOfFirst { it.id == FAVORITES_PLAYLIST_ID || it.isFavorites }
                if (favIndex >= 0) {
                    val current = existing[favIndex]
                    val currentSet = current.trackIds.toHashSet()
                    val toAdd = matchedIds.filter { currentSet.add(it) }
                    if (toAdd.isNotEmpty()) {
                        existing[favIndex] = current.copy(trackIds = current.trackIds + toAdd)
                        favoritesAddedCount += toAdd.size
                        modified = true
                    }
                } else {
                    existing.add(
                        0,
                        Playlist(
                            id = FAVORITES_PLAYLIST_ID,
                            name = FAVORITES_PLAYLIST_NAME,
                            trackIds = matchedIds
                        )
                    )
                    favoritesAddedCount += matchedIds.size
                    modified = true
                }
            } else if (rawName.isNotBlank()) {
                val matchIdx = existing.indexOfFirst {
                    !it.isFavorites && (
                        (rawId.isNotBlank() && it.id == rawId) ||
                            it.name.equals(rawName, ignoreCase = true)
                        )
                }
                if (matchIdx >= 0) {
                    val current = existing[matchIdx]
                    val currentSet = current.trackIds.toHashSet()
                    val toAdd = matchedIds.filter { currentSet.add(it) }
                    if (toAdd.isNotEmpty()) {
                        existing[matchIdx] = current.copy(trackIds = current.trackIds + toAdd)
                        updatedCount++
                        modified = true
                    }
                } else {
                    val newId = rawId.ifBlank { UUID.randomUUID().toString() }
                    existing.add(
                        Playlist(
                            id = newId,
                            name = rawName,
                            trackIds = matchedIds
                        )
                    )
                    importedCount++
                    modified = true
                }
            }
        }

        if (modified) {
            playlistRepository.savePlaylists(existing)
        }
        return Triple(importedCount, updatedCount, favoritesAddedCount)
    }

    private fun restoreListeningStats(
        statsArray: JSONArray?,
        matcher: TrackMatcher,
        unmatchedLabels: MutableSet<String>
    ): Int {
        if (statsArray == null || statsArray.length() == 0) return 0

        val existingStats = statsRepository.loadStats().toMutableMap()
        var restoredCount = 0
        var modified = false

        for (i in 0 until statsArray.length()) {
            val obj = statsArray.optJSONObject(i) ?: continue
            val trackObj = obj.optJSONObject("track") ?: continue
            val ref = jsonToTrackRef(trackObj)
            val playCount = obj.optInt("playCount", 0).coerceAtLeast(0)
            val lastPlayed = obj.optLong("lastPlayedTimestamp", 0L).coerceAtLeast(0L)
            if (playCount == 0 && lastPlayed == 0L) continue

            val match = matcher.match(ref)
            if (match == null) {
                unmatchedLabels.add(formatUnmatchedLabel(ref))
                continue
            }

            val current = existingStats[match.id]
            if (current == null) {
                existingStats[match.id] = TrackStats(
                    audioId = match.id,
                    playCount = playCount,
                    lastPlayedTimestamp = lastPlayed
                )
                restoredCount++
                modified = true
            } else {
                val mergedPlays = maxOf(current.playCount, playCount)
                val mergedLast = maxOf(current.lastPlayedTimestamp, lastPlayed)
                if (mergedPlays != current.playCount || mergedLast != current.lastPlayedTimestamp) {
                    existingStats[match.id] = current.copy(
                        playCount = mergedPlays,
                        lastPlayedTimestamp = mergedLast
                    )
                    restoredCount++
                    modified = true
                }
            }
        }

        if (modified) {
            statsRepository.saveStats(existingStats)
        }
        return restoredCount
    }

    private suspend fun restorePendingScrobbles(pendingArray: JSONArray?): Int {
        if (pendingArray == null || pendingArray.length() == 0) return 0

        val existingKeys = pendingScrobbleDao.getAllPendingAsc()
            .map { Triple(norm(it.artist), norm(it.track), it.timestamp) }
            .toHashSet()
        var restoredCount = 0

        for (i in 0 until pendingArray.length()) {
            val obj = pendingArray.optJSONObject(i) ?: continue
            val artist = sanitize(obj.optString("artist", ""))
            val track = sanitize(obj.optString("track", ""))
            val album = sanitize(obj.optString("album", ""))
            val timestamp = obj.optLong("timestamp", 0L)
            if (artist.isBlank() || track.isBlank() || timestamp <= 0L) continue

            val key = Triple(norm(artist), norm(track), timestamp)
            if (existingKeys.add(key)) {
                pendingScrobbleDao.insert(
                    PendingScrobbleEntity(
                        artist = artist,
                        track = track,
                        album = album,
                        timestamp = timestamp
                    )
                )
                restoredCount++
            }
        }
        return restoredCount
    }

    private fun restorePreferences(prefsObj: JSONObject?): Boolean {
        if (prefsObj == null) return false

        if (prefsObj.has("autoplayEnabled")) {
            appPreferences.setAutoplayEnabled(prefsObj.optBoolean("autoplayEnabled", true))
        }
        if (prefsObj.has("themeMode")) {
            runCatching { ThemeMode.valueOf(prefsObj.optString("themeMode")) }
                .getOrNull()?.let { appPreferences.setThemeMode(it) }
        }
        if (prefsObj.has("appLanguage")) {
            runCatching { AppLanguage.valueOf(prefsObj.optString("appLanguage")) }
                .getOrNull()?.let { appPreferences.setAppLanguage(it) }
        }
        if (prefsObj.has("dynamicColorEnabled")) {
            appPreferences.setDynamicColorEnabled(prefsObj.optBoolean("dynamicColorEnabled", false))
        }
        if (prefsObj.has("audioSourceMode")) {
            runCatching { AudioSourceMode.valueOf(prefsObj.optString("audioSourceMode")) }
                .getOrNull()?.let { appPreferences.setAudioSourceMode(it) }
        }
        prefsObj.optJSONArray("customFolders")?.let { arr ->
            val folders = linkedSetOf<String>()
            for (i in 0 until arr.length()) {
                val folder = sanitize(arr.optString(i, ""))
                if (folder.isNotBlank()) folders.add(folder)
            }
            appPreferences.setCustomFolders(folders)
        }
        if (prefsObj.has("equalizerEnabled")) {
            appPreferences.setEqualizerEnabled(prefsObj.optBoolean("equalizerEnabled", false))
        }
        if (prefsObj.has("equalizerPreset")) {
            appPreferences.setEqualizerPreset(prefsObj.optInt("equalizerPreset", -1).coerceIn(-1, 50))
        }
        if (prefsObj.has("equalizerBandLevels")) {
            val rawBands = prefsObj.optString("equalizerBandLevels", "")
            val validatedCsv = rawBands.split(',')
                .mapNotNull { it.trim().toIntOrNull()?.coerceIn(-1500, 1500) }
                .joinToString(",")
            if (validatedCsv.isNotBlank()) {
                appPreferences.setEqualizerBandLevels(validatedCsv)
            }
        }
        if (prefsObj.has("bassBoostStrength")) {
            appPreferences.setBassBoostStrength(prefsObj.optInt("bassBoostStrength", 0).coerceIn(0, 1000))
        }
        if (prefsObj.has("virtualizerStrength")) {
            appPreferences.setVirtualizerStrength(prefsObj.optInt("virtualizerStrength", 0).coerceIn(0, 1000))
        }
        prefsObj.optJSONArray("ignoredDuplicateKeys")?.let { arr ->
            val keys = linkedSetOf<String>()
            for (i in 0 until arr.length()) {
                val key = sanitize(arr.optString(i, ""))
                if (key.isNotBlank()) keys.add(key)
            }
            if (keys.isNotEmpty()) {
                appPreferences.addIgnoredDuplicateKeys(keys)
            }
        }
        return true
    }

    private fun trackToJson(track: AudioTrack): JSONObject {
        return JSONObject().apply {
            put("relativePath", track.relativePath)
            put("displayName", track.displayName)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationMs", track.duration)
        }
    }

    private fun jsonToTrackRef(obj: JSONObject): BackupTrackRef {
        return BackupTrackRef(
            relativePath = sanitize(obj.optString("relativePath", "")),
            displayName = sanitize(obj.optString("displayName", "")),
            title = sanitize(obj.optString("title", "")),
            artist = sanitize(obj.optString("artist", "")),
            album = sanitize(obj.optString("album", "")),
            durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L)
        )
    }

    private fun formatUnmatchedLabel(ref: BackupTrackRef): String {
        val hasTitle = ref.title.isNotBlank()
        val hasArtist = ref.artist.isNotBlank() && !ref.artist.equals("<unknown>", ignoreCase = true)
        return when {
            hasArtist && hasTitle -> "${ref.artist} - ${ref.title}"
            hasTitle -> ref.title
            ref.displayName.isNotBlank() -> ref.displayName
            else -> "Brano sconosciuto"
        }
    }

    private fun readBoundedUtf8(inputStream: java.io.InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = inputStream.read(chunk)
            if (read == -1) break
            total += read
            if (total > MAX_BACKUP_BYTES) {
                throw BackupException(BackupErrorCode.FILE_TOO_LARGE)
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    private fun sanitize(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length > MAX_STRING_LENGTH) trimmed.substring(0, MAX_STRING_LENGTH) else trimmed
    }

    private fun norm(value: String): String = value.trim().lowercase()

    private class TrackMatcher(tracks: List<AudioTrack>) {
        private val byPathAndFile: Map<Pair<String, String>, AudioTrack>
        private val byFileNameOnly: Map<String, List<AudioTrack>>
        private val byTitleAndArtist: Map<Pair<String, String>, List<AudioTrack>>

        init {
            val pathMap = HashMap<Pair<String, String>, AudioTrack>(tracks.size)
            val fileMap = HashMap<String, MutableList<AudioTrack>>(tracks.size)
            val titleArtistMap = HashMap<Pair<String, String>, MutableList<AudioTrack>>(tracks.size)

            for (track in tracks) {
                val fileNorm = track.displayName.trim().lowercase()
                val pathNorm = normalizePath(track.relativePath)
                if (fileNorm.isNotEmpty()) {
                    pathMap.putIfAbsent(pathNorm to fileNorm, track)
                    fileMap.getOrPut(fileNorm) { mutableListOf() }.add(track)
                }
                val titleNorm = track.title.trim().lowercase()
                val artistNorm = track.artist.trim().lowercase()
                if (titleNorm.isNotEmpty()) {
                    titleArtistMap.getOrPut(titleNorm to artistNorm) { mutableListOf() }.add(track)
                }
            }

            byPathAndFile = pathMap
            byFileNameOnly = fileMap
            byTitleAndArtist = titleArtistMap
        }

        fun match(ref: BackupTrackRef): AudioTrack? {
            val fileNorm = ref.displayName.trim().lowercase()
            val pathNorm = normalizePath(ref.relativePath)

            // 1. Match esatto per percorso relativo + nome file (case-insensitive)
            if (fileNorm.isNotEmpty()) {
                byPathAndFile[pathNorm to fileNorm]?.let { return it }

                // 1b. Match per solo nome file se univoco o compatibile per durata
                val fileCandidates = byFileNameOnly[fileNorm]
                if (!fileCandidates.isNullOrEmpty()) {
                    if (fileCandidates.size == 1) return fileCandidates[0]
                    if (ref.durationMs > 0L) {
                        val durationMatch = fileCandidates
                            .filter { abs(it.duration - ref.durationMs) <= DURATION_MATCH_TOLERANCE_MS }
                            .minByOrNull { abs(it.duration - ref.durationMs) }
                        if (durationMatch != null) return durationMatch
                    }
                }
            }

            // 2. Fallback per titolo + artista + durata (case-insensitive)
            val titleNorm = ref.title.trim().lowercase()
            val artistNorm = ref.artist.trim().lowercase()
            if (titleNorm.isNotEmpty()) {
                val candidates = byTitleAndArtist[titleNorm to artistNorm]
                if (!candidates.isNullOrEmpty()) {
                    if (ref.durationMs > 0L) {
                        val best = candidates
                            .filter { abs(it.duration - ref.durationMs) <= DURATION_MATCH_TOLERANCE_MS }
                            .minByOrNull { abs(it.duration - ref.durationMs) }
                        if (best != null) return best
                    } else if (candidates.size == 1) {
                        return candidates[0]
                    }
                }
            }

            return null
        }

        private fun normalizePath(path: String): String {
            return path.trim().replace('\\', '/').trim('/').lowercase()
        }
    }
}
