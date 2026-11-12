package com.example.musicplayer.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Informazioni relative allo stato di aggiornamento dell'applicazione su GitHub.
 */
data class AppUpdateInfo(
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseNotesIt: String? = null,
    val releaseNotesEn: String? = null,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val isUpdateAvailable: Boolean
) {
    /**
     * Indica se la release contiene note distinte in entrambe le lingue.
     */
    val hasBothLanguages: Boolean
        get() = !releaseNotesIt.isNullOrBlank() && !releaseNotesEn.isNullOrBlank()

    /**
     * Restituisce le note di rilascio più appropriate in base alla lingua desiderata.
     */
    fun getReleaseNotesForLanguage(isEnglish: Boolean): String {
        return if (isEnglish) {
            releaseNotesEn?.takeIf { it.isNotBlank() }
                ?: releaseNotesIt?.takeIf { it.isNotBlank() }
                ?: releaseNotes
        } else {
            releaseNotesIt?.takeIf { it.isNotBlank() }
                ?: releaseNotesEn?.takeIf { it.isNotBlank() }
                ?: releaseNotes
        }
    }
}

/**
 * Gestore del controllo degli aggiornamenti dell'applicazione tramite le API pubbliche di GitHub Releases.
 */
class GithubUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val GITHUB_REPO_URL = "https://github.com/PublicGiu/MusiCal"
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/PublicGiu/MusiCal/releases/latest"
    }

    /**
     * Interroga le API di GitHub per determinare se è disponibile una versione più recente
     * rispetto a [currentVersion].
     */
    suspend fun checkForUpdates(currentVersion: String): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "MusiCal-Android-App")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Risposta del server GitHub non valida: HTTP ${response.code}")
                        )
                    }

                    val body = response.body?.string()
                        ?: return@withContext Result.failure(Exception("Risposta vuota da GitHub"))

                    val json = JSONObject(body)
                    val rawTagName = json.optString("tag_name", "").trim()
                    val cleanVersion = sanitizeVersionString(rawTagName)
                    val releaseName = json.optString("name", rawTagName).ifBlank { rawTagName }
                    val releaseNotes = json.optString("body", "").trim()
                    val htmlUrl = json.optString("html_url", GITHUB_REPO_URL)

                    var apkDownloadUrl: String? = null
                    val assetsArray = json.optJSONArray("assets")
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    }

                    val isUpdateAvailable = isRemoteNewer(cleanVersion, currentVersion)
                    val parsedNotes = ReleaseNotesParser.parse(releaseNotes)

                    val updateInfo = AppUpdateInfo(
                        latestVersion = cleanVersion,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        releaseNotesIt = parsedNotes.italianNotes,
                        releaseNotesEn = parsedNotes.englishNotes,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkDownloadUrl,
                        isUpdateAvailable = isUpdateAvailable
                    )

                    Result.success(updateInfo)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Rimuove prefissi non numerici come "v" o "V" e spazi.
     */
    fun sanitizeVersionString(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
    }

    /**
     * Confronta due versioni SemVer (es. "0.1.5" e "0.1.4").
     * Restituisce true se [remote] è strettamente maggiore di [current].
     */
    fun isRemoteNewer(remote: String, current: String): Boolean {
        val cleanRemote = sanitizeVersionString(remote)
        val cleanCurrent = sanitizeVersionString(current)

        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) return false
        if (cleanRemote == cleanCurrent) return false

        val remoteParts = parseVersionParts(cleanRemote)
        val currentParts = parseVersionParts(cleanCurrent)

        val maxParts = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxParts) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        // Rimuove eventuali metadati di pre-release (es. "0.1.5-beta1" -> "0.1.5")
        val base = version.substringBefore("-").substringBefore("+")
        return base.split(".")
            .map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    }
}
