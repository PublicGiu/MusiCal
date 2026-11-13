package com.example.musicplayer.data.lastfm

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.network.SharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

sealed class UserAvatarResult {
    data object NoAvatar : UserAvatarResult()
    data class Unchanged(val hash: String) : UserAvatarResult()
    data class Downloaded(val hash: String, val bytes: ByteArray, val isGif: Boolean) : UserAvatarResult()
}

/**
 * Client HTTP per interagire con le API REST di Last.fm.
 */
class LastFmClient(
    private val client: OkHttpClient = SharedHttpClient.instance
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

    /**
     * Recupera l'immagine profilo (avatar) dell'utente tramite `user.getInfo`.
     * Gestisce automaticamente gli avatar animati GIF sulla CDN Fastly di Last.fm
     * interrogando l'endpoint senza estensione (`avatar170s/<hash>`) per preservare i frame GIF
     * che altrimenti verrebbero convertiti in PNG statici dall'URL standard `300x300/<hash>.png`.
     */
    suspend fun fetchUserAvatar(
        username: String,
        knownHash: String? = null
    ): Result<UserAvatarResult> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = username.trim()
            if (cleanUser.isBlank() || LastFmConfig.API_KEY.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username o API Key mancanti"))
            }

            val url = LastFmConfig.BASE_URL.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("method", "user.getInfo")
                ?.addQueryParameter("user", cleanUser)
                ?.addQueryParameter("api_key", LastFmConfig.API_KEY)
                ?.addQueryParameter("format", "json")
                ?.build()
                ?: return@withContext Result.failure(IllegalStateException("URL Last.fm non valido"))

            val infoRequest = Request.Builder().url(url).get().build()
            val rawImageUrl = client.newCall(infoRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseErrorMessage(body, response.code)))
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    return@withContext Result.failure(Exception(json.optString("message", "Errore user.getInfo")))
                }
                val images = json.optJSONObject("user")?.optJSONArray("image")
                extractBestImageUrl(images)
            }

            // Hash di default di Last.fm per utenti senza immagine profilo ("mystery man")
            val defaultAvatarHash = "2a96cbd8b46e442fc41c2b86b821562f"
            if (rawImageUrl.isNullOrBlank() || rawImageUrl.contains(defaultAvatarHash, ignoreCase = true)) {
                return@withContext Result.success(UserAvatarResult.NoAvatar)
            }

            val fastlyHashRegex = Regex("""/i/u/[^/]+/([a-fA-F0-9]{32})""")
            val fastlyHash = fastlyHashRegex.find(rawImageUrl)?.groupValues?.getOrNull(1)?.lowercase()
            val effectiveHash = fastlyHash ?: rawImageUrl.hashCode().toUInt().toString(16)

            if (knownHash != null && knownHash == effectiveHash) {
                return@withContext Result.success(UserAvatarResult.Unchanged(effectiveHash))
            }

            if (fastlyHash != null) {
                // La CDN di Last.fm serve la GIF animata originale sull'endpoint privo di estensione .png
                val gifCandidateUrl = "https://lastfm.freetls.fastly.net/i/u/avatar170s/$fastlyHash"
                val candidateBytes = downloadBytes(gifCandidateUrl)
                if (candidateBytes != null && isGifBytes(candidateBytes)) {
                    return@withContext Result.success(
                        UserAvatarResult.Downloaded(
                            hash = effectiveHash,
                            bytes = candidateBytes,
                            isGif = true
                        )
                    )
                }

                // Se non è una GIF, scarica la versione ad alta risoluzione 300x300 (o usa il fallback già scaricato)
                val hiResBytes = downloadBytes(rawImageUrl) ?: candidateBytes
                if (hiResBytes != null && hiResBytes.isNotEmpty()) {
                    return@withContext Result.success(
                        UserAvatarResult.Downloaded(
                            hash = effectiveHash,
                            bytes = hiResBytes,
                            isGif = isGifBytes(hiResBytes)
                        )
                    )
                }
            } else {
                val bytes = downloadBytes(rawImageUrl)
                if (bytes != null && bytes.isNotEmpty()) {
                    return@withContext Result.success(
                        UserAvatarResult.Downloaded(
                            hash = effectiveHash,
                            bytes = bytes,
                            isGif = isGifBytes(bytes)
                        )
                    )
                }
            }

            Result.failure(IOException("Impossibile scaricare l'immagine profilo Last.fm"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBestImageUrl(images: org.json.JSONArray?): String? {
        if (images == null || images.length() == 0) return null
        val bySize = mutableMapOf<String, String>()
        var lastNonEmpty: String? = null
        for (i in 0 until images.length()) {
            val obj = images.optJSONObject(i) ?: continue
            val text = obj.optString("#text", "").trim()
            val size = obj.optString("size", "").trim().lowercase()
            if (text.isNotEmpty()) {
                bySize[size] = text
                lastNonEmpty = text
            }
        }
        return bySize["extralarge"]
            ?: bySize["large"]
            ?: bySize["medium"]
            ?: bySize["small"]
            ?: lastNonEmpty
    }

    private fun downloadBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isGifBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        // Magic bytes GIF87a oppure GIF89a
        return bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
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
