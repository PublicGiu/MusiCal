package com.example.musicplayer.data.tagger.client

import com.example.musicplayer.data.tagger.model.LrclibResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client HTTP per le API pubbliche di LRCLIB (nessuna chiave richiesta).
 * Fornisce testi sincronizzati (.lrc con timestamp [mm:ss.xx]) e testi normali.
 */
class LrclibApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private val baseUrl = "https://lrclib.net/api"

    /**
     * Recupera i testi sincronizzati o semplici per un brano.
     * Tenta prima la chiamata puntuale /api/get e poi, se fallisce (es. 404), la ricerca /api/search.
     */
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSec: Long? = null
    ): Result<LrclibResponse?> = withContext(Dispatchers.IO) {
        try {
            // Tentativo 1: Chiamata diretta a /api/get
            val getResult = fetchExact(trackName, artistName, albumName, durationSec)
            if (getResult != null) {
                return@withContext Result.success(getResult)
            }

            // Tentativo 2: Fallback su /api/search
            val searchResult = searchLyrics(trackName, artistName)
            Result.success(searchResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchExact(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Long?
    ): LrclibResponse? {
        val urlBuilder = StringBuilder("$baseUrl/get?")
        urlBuilder.append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
        urlBuilder.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
        if (!albumName.isNullOrBlank()) {
            urlBuilder.append("&album_name=").append(URLEncoder.encode(albumName, "UTF-8"))
        }
        if (durationSec != null && durationSec > 0) {
            urlBuilder.append("&duration=").append(durationSec)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "MusiCal/0.1.4 (Android; Mobile)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404 || !response.isSuccessful) {
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return parseLrclibJson(JSONObject(body))
        }
    }

    private fun searchLyrics(trackName: String, artistName: String): LrclibResponse? {
        val query = "$artistName $trackName".trim()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?q=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusiCal/0.1.4 (Android; Mobile)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val jsonArray = try {
                JSONArray(body)
            } catch (e: Exception) {
                return null
            }

            if (jsonArray.length() == 0) return null

            // Cerchiamo il primo risultato che abbia testi sincronizzati o testi semplici
            var bestCandidate: LrclibResponse? = null
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val parsed = parseLrclibJson(item)
                if (parsed.hasSyncedLyrics) {
                    return parsed // Ottima corrispondenza con testi karaoke
                }
                if (bestCandidate == null && parsed.hasPlainLyrics) {
                    bestCandidate = parsed
                }
            }

            return bestCandidate ?: parseLrclibJson(jsonArray.getJSONObject(0))
        }
    }

    private fun parseLrclibJson(json: JSONObject): LrclibResponse {
        val id = json.optLong("id", 0L)
        val trackName = json.optString("trackName", json.optString("name", ""))
        val artistName = json.optString("artistName", "")
        val albumName = json.optString("albumName")?.takeIf { it.isNotBlank() }
        val durationSec = json.optDouble("duration", 0.0)
        val instrumental = json.optBoolean("instrumental", false)
        val plainLyrics = json.optString("plainLyrics")?.takeIf { it.isNotBlank() }
        val syncedLyrics = json.optString("syncedLyrics")?.takeIf { it.isNotBlank() }

        return LrclibResponse(
            id = id,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSec = durationSec,
            instrumental = instrumental,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics
        )
    }
}
