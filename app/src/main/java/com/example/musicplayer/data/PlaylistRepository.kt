package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

/**
 * Gestore della persistenza locale delle playlist personalizzate nella memoria
 * interna privata dell'applicazione (file JSON) tramite AtomicFile.
 */
class PlaylistRepository(private val context: Context) {

    private val playlistFile = File(context.filesDir, "playlists.json")
    private val atomicFile = AtomicFile(playlistFile)
    private val coversDir = File(context.filesDir, "playlist_covers")

    @Synchronized
    fun loadPlaylists(): List<Playlist> {
        val defaultFavorites = Playlist(
            id = FAVORITES_PLAYLIST_ID,
            name = FAVORITES_PLAYLIST_NAME,
            trackIds = emptyList(),
            coverUri = null
        )

        if (!playlistFile.exists()) return listOf(defaultFavorites)

        return try {
            val content = atomicFile.openRead().use { input ->
                InputStreamReader(input, Charsets.UTF_8).readText()
            }
            if (content.isBlank()) return listOf(defaultFavorites)

            val jsonArray = JSONArray(content)
            val list = mutableListOf<Playlist>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "Playlist senza nome")
                val tracksArray = obj.optJSONArray("trackIds") ?: JSONArray()
                val trackIds = mutableListOf<Long>()
                for (j in 0 until tracksArray.length()) {
                    trackIds.add(tracksArray.getLong(j))
                }
                val rawCoverUri = obj.optString("coverUri", "").takeIf { it.isNotBlank() }
                val coverUri = if (id == FAVORITES_PLAYLIST_ID) null else rawCoverUri
                if (id.isNotBlank()) {
                    list.add(Playlist(id = id, name = name, trackIds = trackIds, coverUri = coverUri))
                }
            }

            val existingFavorites = list.find { it.id == FAVORITES_PLAYLIST_ID }?.copy(coverUri = null)
            val otherPlaylists = list.filterNot { it.id == FAVORITES_PLAYLIST_ID }
            val favorites = existingFavorites ?: defaultFavorites
            listOf(favorites) + otherPlaylists
        } catch (e: Exception) {
            Log.e("PlaylistRepository", "Errore durante la lettura delle playlist", e)
            emptyList()
        }
    }

    @Synchronized
    fun savePlaylists(playlists: List<Playlist>) {
        val jsonArray = JSONArray()
        for (playlist in playlists) {
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val tracksArray = JSONArray()
            playlist.trackIds.forEach { tracksArray.put(it) }
            obj.put("trackIds", tracksArray)
            if (!playlist.isFavorites && !playlist.coverUri.isNullOrBlank()) {
                obj.put("coverUri", playlist.coverUri)
            }
            jsonArray.put(obj)
        }

        val bytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
        val fos = atomicFile.startWrite()
        try {
            fos.write(bytes)
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            Log.e("PlaylistRepository", "Errore durante la scrittura delle playlist", e)
            atomicFile.failWrite(fos)
        }
        val newFile = File(playlistFile.parentFile, "${playlistFile.name}.new")
        if (newFile.exists()) {
            java.nio.file.Files.move(newFile.toPath(), playlistFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val backupFile = File(playlistFile.parentFile, "${playlistFile.name}.bak")
        if (backupFile.exists() && playlistFile.exists()) {
            try {
                java.nio.file.Files.deleteIfExists(backupFile.toPath())
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun createPlaylist(name: String, coverUri: String? = null): Playlist {
        val current = loadPlaylists().toMutableList()
        val trimmed = name.trim().ifBlank { "Nuova Playlist" }
        val newPlaylist = Playlist(
            name = trimmed,
            coverUri = coverUri?.takeIf { it.isNotBlank() }
        )
        val favIndex = current.indexOfFirst { it.id == FAVORITES_PLAYLIST_ID }
        if (favIndex >= 0) {
            current.add(favIndex + 1, newPlaylist)
        } else {
            current.add(0, newPlaylist)
        }
        savePlaylists(current)
        return newPlaylist
    }

    @Synchronized
    fun createPlaylistWithSourceUri(name: String, sourceUri: Uri?): Playlist {
        val created = createPlaylist(name)
        if (sourceUri != null) {
            updatePlaylistCoverFromUri(created.id, sourceUri)
            return loadPlaylists().find { it.id == created.id } ?: created
        }
        return created
    }

    @Synchronized
    fun updatePlaylistCoverUri(playlistId: String, coverUri: String?): Boolean {
        if (playlistId == FAVORITES_PLAYLIST_ID) return false
        val current = loadPlaylists()
        val target = current.find { it.id == playlistId } ?: return false
        val normalizedCover = coverUri?.takeIf { it.isNotBlank() }
        if (target.coverUri == normalizedCover) return true

        val updated = current.map {
            if (it.id == playlistId) {
                it.copy(coverUri = normalizedCover)
            } else {
                it
            }
        }
        savePlaylists(updated)
        return true
    }

    @Synchronized
    fun updatePlaylistCoverFromUri(playlistId: String, sourceUri: Uri?): Boolean {
        if (playlistId == FAVORITES_PLAYLIST_ID) return false
        val current = loadPlaylists()
        if (current.none { it.id == playlistId }) return false

        if (sourceUri == null) {
            deleteCoverFilesForPlaylist(playlistId)
            return updatePlaylistCoverUri(playlistId, null)
        }

        val copiedUriString = copyCoverToInternalStorage(playlistId, sourceUri) ?: return false
        return updatePlaylistCoverUri(playlistId, copiedUriString)
    }

    private fun copyCoverToInternalStorage(playlistId: String, sourceUri: Uri): String? {
        return try {
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }
            val destFile = File(coversDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
            val resolver = context.contentResolver
            val copied = resolver?.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false

            if (!copied || !destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return null
            }

            // Rimuovi i file copertina precedenti per questa playlist
            deleteCoverFilesForPlaylist(playlistId, excludeFileName = destFile.name)
            destFile.toURI().toString()
        } catch (e: Exception) {
            Log.e("PlaylistRepository", "Errore durante il salvataggio della copertina playlist", e)
            null
        }
    }

    private fun deleteCoverFilesForPlaylist(playlistId: String, excludeFileName: String? = null) {
        if (!coversDir.exists()) return
        val prefix = "cover_${playlistId}_"
        coversDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) && file.name != excludeFileName) {
                try {
                    file.delete()
                } catch (_: Exception) {}
            }
        }
    }

    @Synchronized
    fun deletePlaylist(playlistId: String): Boolean {
        if (playlistId == FAVORITES_PLAYLIST_ID) {
            return false
        }
        val current = loadPlaylists()
        val updated = current.filterNot { it.id == playlistId }
        if (updated.size == current.size) {
            return false
        }
        deleteCoverFilesForPlaylist(playlistId)
        savePlaylists(updated)
        return true
    }

    @Synchronized
    fun addTrackToPlaylist(playlistId: String, trackId: Long): Boolean {
        val current = loadPlaylists()
        val target = current.find { it.id == playlistId } ?: return false
        if (target.trackIds.contains(trackId)) return false // Già presente

        val updated = current.map {
            if (it.id == playlistId) {
                it.copy(trackIds = it.trackIds + trackId)
            } else {
                it
            }
        }
        savePlaylists(updated)
        return true
    }

    @Synchronized
    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        val current = loadPlaylists()
        val updated = current.map {
            if (it.id == playlistId) {
                it.copy(trackIds = it.trackIds - trackId)
            } else {
                it
            }
        }
        savePlaylists(updated)
    }

    @Synchronized
    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        val current = loadPlaylists()
        val target = current.find { it.id == playlistId } ?: return false
        val trackIds = target.trackIds.toMutableList()
        if (fromIndex !in trackIds.indices || toIndex !in trackIds.indices || fromIndex == toIndex) return false
        val moved = trackIds.removeAt(fromIndex)
        trackIds.add(toIndex, moved)
        val updated = current.map {
            if (it.id == playlistId) {
                it.copy(trackIds = trackIds)
            } else {
                it
            }
        }
        savePlaylists(updated)
        return true
    }
}
