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
 * Gestore locale della persistenza della cache della libreria musicale (tracce e album indicizzati).
 * Consente l'avvio a zero latenza (Instant-On) ricaricando in pochi millisecondi l'intera libreria
 * musicale senza attendere la scansione sincrona di MediaStore, che viene differita in background.
 */
class LibraryCacheRepository(
    context: Context,
    private val uriParser: (String) -> Uri = { Uri.parse(it) }
) {

    private val cacheFile = File(context.filesDir, "library_cache.json")
    private val atomicFile = AtomicFile(cacheFile)

    @Synchronized
    fun loadCache(
        expectedSourceMode: AudioSourceMode,
        expectedCustomFolders: Set<String>
    ): Pair<List<AudioTrack>, List<Album>>? {
        if (!cacheFile.exists()) return null

        return try {
            val content = atomicFile.openRead().use { input ->
                InputStreamReader(input, Charsets.UTF_8).readText()
            }
            if (content.isBlank()) return null

            val rootObj = JSONObject(content)
            val cachedModeName = rootObj.optString("sourceMode", "")
            if (cachedModeName != expectedSourceMode.name) {
                return null
            }

            val cachedFoldersArray = rootObj.optJSONArray("customFolders") ?: JSONArray()
            val cachedFolders = mutableSetOf<String>()
            for (i in 0 until cachedFoldersArray.length()) {
                cachedFolders.add(cachedFoldersArray.getString(i))
            }
            if (cachedFolders != expectedCustomFolders) {
                return null
            }

            val tracksArray = rootObj.optJSONArray("tracks") ?: JSONArray()
            val tracks = ArrayList<AudioTrack>(tracksArray.length())
            val tracksById = HashMap<Long, AudioTrack>(tracksArray.length())

            for (i in 0 until tracksArray.length()) {
                val tObj = tracksArray.getJSONObject(i)
                val id = tObj.getLong("id")
                val uriString = tObj.optString("uri", "")
                val albumArtUriString = tObj.optString("albumArtUri", "").takeIf { it.isNotBlank() }

                val track = AudioTrack(
                    id = id,
                    title = tObj.optString("title", "Sconosciuto"),
                    artist = tObj.optString("artist", "Artista sconosciuto"),
                    duration = tObj.optLong("duration", 0L),
                    uri = if (uriString.isNotBlank()) uriParser(uriString) else Uri.EMPTY,
                    albumId = tObj.optLong("albumId", 0L),
                    album = tObj.optString("album", "Album sconosciuto"),
                    albumArtUri = albumArtUriString?.let { uriParser(it) },
                    dateAdded = tObj.optLong("dateAdded", 0L),
                    genre = tObj.optString("genre", ""),
                    trackNumber = tObj.optInt("trackNumber", 0),
                    discNumber = tObj.optInt("discNumber", 1),
                    albumArtist = tObj.optString("albumArtist", ""),
                    displayName = tObj.optString("displayName", ""),
                    relativePath = tObj.optString("relativePath", "")
                )
                tracks.add(track)
                tracksById[id] = track
            }

            val albumsArray = rootObj.optJSONArray("albums") ?: JSONArray()
            val albums = ArrayList<Album>(albumsArray.length())

            for (i in 0 until albumsArray.length()) {
                val aObj = albumsArray.getJSONObject(i)
                val id = aObj.getLong("id")
                val title = aObj.optString("title", "Sconosciuto")
                val artist = aObj.optString("artist", "Artista sconosciuto")
                val albumArtUriString = aObj.optString("albumArtUri", "").takeIf { it.isNotBlank() }
                val trackIdsArray = aObj.optJSONArray("trackIds") ?: JSONArray()

                val albumTracks = ArrayList<AudioTrack>(trackIdsArray.length())
                for (j in 0 until trackIdsArray.length()) {
                    val tId = trackIdsArray.getLong(j)
                    tracksById[tId]?.let { albumTracks.add(it) }
                }

                if (albumTracks.isNotEmpty()) {
                    albums.add(
                        Album(
                            id = id,
                            title = title,
                            artist = artist,
                            albumArtUri = albumArtUriString?.let { uriParser(it) },
                            tracks = albumTracks
                        )
                    )
                }
            }

            if (tracks.isEmpty() && albums.isEmpty()) null else (tracks to albums)
        } catch (e: Exception) {
            Log.e("LibraryCacheRepository", "Errore durante la lettura della cache libreria", e)
            null
        }
    }

    @Synchronized
    fun saveCache(
        sourceMode: AudioSourceMode,
        customFolders: Set<String>,
        tracks: List<AudioTrack>,
        albums: List<Album>
    ) {
        try {
            val rootObj = JSONObject()
            rootObj.put("sourceMode", sourceMode.name)

            val foldersArray = JSONArray()
            customFolders.forEach { foldersArray.put(it) }
            rootObj.put("customFolders", foldersArray)

            val tracksArray = JSONArray()
            for (track in tracks) {
                val tObj = JSONObject()
                tObj.put("id", track.id)
                tObj.put("title", track.title)
                tObj.put("artist", track.artist)
                tObj.put("duration", track.duration)
                tObj.put("uri", track.uri.toString())
                tObj.put("albumId", track.albumId)
                tObj.put("album", track.album)
                track.albumArtUri?.let { tObj.put("albumArtUri", it.toString()) }
                tObj.put("dateAdded", track.dateAdded)
                tObj.put("genre", track.genre)
                tObj.put("trackNumber", track.trackNumber)
                tObj.put("discNumber", track.discNumber)
                tObj.put("albumArtist", track.albumArtist)
                tObj.put("displayName", track.displayName)
                tObj.put("relativePath", track.relativePath)
                tracksArray.put(tObj)
            }
            rootObj.put("tracks", tracksArray)

            val albumsArray = JSONArray()
            for (album in albums) {
                val aObj = JSONObject()
                aObj.put("id", album.id)
                aObj.put("title", album.title)
                aObj.put("artist", album.artist)
                album.albumArtUri?.let { aObj.put("albumArtUri", it.toString()) }

                val trackIdsArray = JSONArray()
                for (track in album.tracks) {
                    trackIdsArray.put(track.id)
                }
                aObj.put("trackIds", trackIdsArray)
                albumsArray.put(aObj)
            }
            rootObj.put("albums", albumsArray)

            val bytes = rootObj.toString().toByteArray(Charsets.UTF_8)
            val fos = atomicFile.startWrite()
            try {
                fos.write(bytes)
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                Log.e("LibraryCacheRepository", "Errore durante il salvataggio della cache libreria", e)
                atomicFile.failWrite(fos)
            }

            // Fallback per filesystem che richiedono rename esplicito (es. Windows NTFS o test)
            val newFile = File(cacheFile.parentFile, "${cacheFile.name}.new")
            if (newFile.exists()) {
                java.nio.file.Files.move(
                    newFile.toPath(),
                    cacheFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            val backupFile = File(cacheFile.parentFile, "${cacheFile.name}.bak")
            if (backupFile.exists() && cacheFile.exists()) {
                try {
                    java.nio.file.Files.deleteIfExists(backupFile.toPath())
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("LibraryCacheRepository", "Eccezione durante la serializzazione della cache libreria", e)
        }
    }

    @Synchronized
    fun clearCache() {
        try {
            atomicFile.delete()
        } catch (_: Exception) {}
    }
}
