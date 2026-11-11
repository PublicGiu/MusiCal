package com.example.musicplayer.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

/**
 * Gestore locale della persistenza delle statistiche d'ascolto (playCount e lastPlayedTimestamp)
 * salvato in formato JSON nella memoria interna privata dell'app tramite AtomicFile per prevenire corruzioni.
 */
class StatsRepository(context: Context) {

    private val statsFile = File(context.filesDir, "track_stats.json")
    private val atomicFile = AtomicFile(statsFile)

    @Synchronized
    fun loadStats(): Map<Long, TrackStats> {
        if (!statsFile.exists()) return emptyMap()

        return try {
            val content = atomicFile.openRead().use { input ->
                InputStreamReader(input, Charsets.UTF_8).readText()
            }
            val jsonArray = JSONArray(content)
            val map = mutableMapOf<Long, TrackStats>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val audioId = obj.optLong("audioId", -1L)
                val playCount = obj.optInt("playCount", 0)
                val lastPlayedTimestamp = obj.optLong("lastPlayedTimestamp", 0L)
                if (audioId != -1L) {
                    map[audioId] = TrackStats(
                        audioId = audioId,
                        playCount = playCount,
                        lastPlayedTimestamp = lastPlayedTimestamp
                    )
                }
            }
            map
        } catch (e: Exception) {
            Log.e("StatsRepository", "Errore durante la lettura delle statistiche", e)
            emptyMap()
        }
    }

    @Synchronized
    fun saveStats(statsMap: Map<Long, TrackStats>) {
        val jsonArray = JSONArray()
        for ((_, stat) in statsMap) {
            val obj = JSONObject()
            obj.put("audioId", stat.audioId)
            obj.put("playCount", stat.playCount)
            obj.put("lastPlayedTimestamp", stat.lastPlayedTimestamp)
            jsonArray.put(obj)
        }

        val bytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
        val fos = atomicFile.startWrite()
        try {
            fos.write(bytes)
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            Log.e("StatsRepository", "Errore durante la scrittura delle statistiche", e)
            atomicFile.failWrite(fos)
        }
        val newFile = File(statsFile.parentFile, "${statsFile.name}.new")
        if (newFile.exists()) {
            java.nio.file.Files.move(newFile.toPath(), statsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val backupFile = File(statsFile.parentFile, "${statsFile.name}.bak")
        if (backupFile.exists() && statsFile.exists()) {
            try {
                java.nio.file.Files.deleteIfExists(backupFile.toPath())
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun recordTrackPlayed(audioId: Long): TrackStats {
        val current = loadStats().toMutableMap()
        val existing = current[audioId]
        val updated = if (existing != null) {
            existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        } else {
            TrackStats(
                audioId = audioId,
                playCount = 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        }
        current[audioId] = updated
        saveStats(current)
        return updated
    }

    @Synchronized
    fun clearStats() {
        saveStats(emptyMap())
    }
}
