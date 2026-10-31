package com.example.musicplayer.data.lastfm

import com.example.musicplayer.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client HTTP per interagire con le API REST di Last.fm.
 */
class LastFmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Esegue l'autenticazione tramite mobile session (auth.getMobileSession).
     * Restituisce Pair(username, sessionKey) o eccezione.
     */
    suspend fun getMobileSession(username: String, password: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val params = mutableMapOf(
                    "method" to "auth.getMobileSession",
                    "username" to username.trim(),
                    "password" to password,
                    "api_key" to LastFmConfig.API_KEY
                )

                val request = buildSignedPostRequest(params)

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(
                            responseBody,
                            response.code,
                            "Errore di rete Last.fm (HTTP ${response.code})"
                        )
                        return@withContext Result.failure(IOException(errorMsg))
                    }

                    val json = try {
                        JSONObject(responseBody)
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception("Risposta Last.fm non valida (formato JSON errato)"))
                    }

                    if (json.has("error")) {
                        val errorMsg = json.optString("message", "Errore durante l'autenticazione Last.fm")
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    if (json.has("session")) {
                        val sessionObj = json.getJSONObject("session")
                        val returnedName = sessionObj.optString("name", username)
                        val sessionKey = sessionObj.getString("key")
                        Result.success(Pair(returnedName, sessionKey))
                    } else {
                        Result.failure(Exception("Risposta Last.fm non valida"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Invia la notifica "Now Playing" a Last.fm quando una traccia inizia la riproduzione.
     */
    suspend fun updateNowPlaying(track: AudioTrack, sessionKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (track.title.isBlank() || track.artist.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Titolo o artista non validi"))
                }

                val params = mutableMapOf(
                    "method" to "track.updateNowPlaying",
                    "artist" to track.artist.trim(),
                    "track" to track.title.trim(),
                    "api_key" to LastFmConfig.API_KEY,
                    "sk" to sessionKey
                )

                if (track.album.isNotBlank() && track.album != "Sconosciuto") {
                    params["album"] = track.album.trim()
                }

                if (track.duration > 0) {
                    params["duration"] = (track.duration / 1000).toString()
                }

                val request = buildSignedPostRequest(params)

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(responseBody, response.code)
                        return@withContext Result.failure(IOException(errorMsg))
                    }

                    if (responseBody.isNotBlank()) {
                        try {
                            val json = JSONObject(responseBody)
                            if (json.has("error")) {
                                val errorMsg = json.optString("message", "Errore updateNowPlaying")
                                return@withContext Result.failure(Exception(errorMsg))
                            }
                        } catch (_: Exception) {
                            // Ignora eccezione se la risposta 200 non è JSON strict
                        }
                    }

                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Invia lo scrobble effettivo della traccia a Last.fm specificando i singoli campi.
     * Utilizzato sia per le tracce in riproduzione sia per la sincronizzazione della cache offline.
     */
    suspend fun scrobble(
        artist: String,
        track: String,
        album: String?,
        timestampSeconds: Long,
        sessionKey: String,
        durationSeconds: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (track.isBlank() || artist.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Titolo o artista non validi"))
            }

            val params = mutableMapOf(
                "method" to "track.scrobble",
                "artist" to artist.trim(),
                "track" to track.trim(),
                "timestamp" to timestampSeconds.toString(),
                "api_key" to LastFmConfig.API_KEY,
                "sk" to sessionKey
            )

            if (!album.isNullOrBlank() && album != "Sconosciuto") {
                params["album"] = album.trim()
            }

            if (durationSeconds != null && durationSeconds > 0) {
                params["duration"] = durationSeconds.toString()
            }

            val request = buildSignedPostRequest(params)

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(responseBody, response.code)
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val json = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    return@withContext Result.failure(Exception("Risposta Last.fm non in formato JSON"))
                }

                if (json.has("error")) {
                    val errorMsg = json.optString("message", "Errore scrobble")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Invia lo scrobble a partire da un oggetto [AudioTrack].
     */
    suspend fun scrobble(
        track: AudioTrack,
        timestampSeconds: Long,
        sessionKey: String
    ): Result<Unit> {
        val durSec = if (track.duration > 0) track.duration / 1000 else null
        return scrobble(
            artist = track.artist,
            track = track.title,
            album = track.album,
            timestampSeconds = timestampSeconds,
            sessionKey = sessionKey,
            durationSeconds = durSec
        )
    }

    private fun buildSignedPostRequest(params: Map<String, String>): Request {
        val apiSig = LastFmConfig.generateApiSig(params)
        val formBuilder = FormBody.Builder()
        for ((k, v) in params) {
            formBuilder.add(k, v)
        }
        formBuilder.add("api_sig", apiSig)
        formBuilder.add("format", "json")
        return Request.Builder()
            .url(LastFmConfig.BASE_URL)
            .post(formBuilder.build())
            .build()
    }

    private fun parseErrorMessage(
        responseBody: String,
        httpCode: Int,
        defaultMsg: String = "HTTP $httpCode da Last.fm"
    ): String {
        return try {
            if (responseBody.isNotBlank()) {
                val json = JSONObject(responseBody)
                json.optString("message", defaultMsg)
            } else {
                defaultMsg
            }
        } catch (_: Exception) {
            defaultMsg
        }
    }
}
