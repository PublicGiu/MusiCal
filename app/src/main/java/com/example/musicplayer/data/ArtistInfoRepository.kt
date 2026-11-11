package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.util.AtomicFile
import com.example.musicplayer.data.lastfm.LastFmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Repository per il recupero, la normalizzazione, la memorizzazione persistente su cache locale
 * e la personalizzazione manuale (foto e biografia) delle informazioni sugli artisti.
 */
class ArtistInfoRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    private val tag = "ArtistInfoRepository"
    private val memoryCache = ConcurrentHashMap<String, ArtistInfo>()
    private val cacheFile = File(context.filesDir, "artist_cache.json")
    private val atomicFile = AtomicFile(cacheFile)
    private val artistUpdates = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        loadCacheFromDisk()
    }

    /**
     * Restituisce il codice lingua preferito per i contenuti testuali delle biografie ("it" o "en").
     */
    fun getPreferredLanguageCode(): String {
        return try {
            val appLang = AppPreferences(context).getAppLanguage()
            if (appLang == AppLanguage.SYSTEM) {
                val defaultLang = java.util.Locale.getDefault().language
                if (defaultLang.equals("it", ignoreCase = true)) "it" else "en"
            } else {
                if (appLang.code.isNotBlank()) appLang.code else "it"
            }
        } catch (_: Exception) {
            "it"
        }
    }

    private fun cleanArtistName(artistName: String): String {
        return if (isCollabArtist(artistName)) {
            artistName.trim()
        } else {
            extractMainArtist(artistName).ifBlank { artistName }.trim()
        }
    }

    /**
     * Restituisce le informazioni dell'artista da cache in-memory se presenti,
     * altrimenti null.
     */
    fun getCachedArtistInfo(artistName: String): ArtistInfo? {
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) return null
        return memoryCache[clean.lowercase()]
    }

    /**
     * Emette prima il dato in cache (se disponibile) e poi, se mancante o su richiesta,
     * interroga la rete ed emette il dato aggiornato. Rimane in ascolto di successivi
     * aggiornamenti manuali per emetterli tempestivamente.
     */
    fun getArtistInfoFlow(artistName: String, forceRefresh: Boolean = false): Flow<ArtistInfo> = flow {
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) {
            emit(ArtistInfo(name = artistName))
            return@flow
        }

        val cached = memoryCache[clean.lowercase()]
        val preferredLang = getPreferredLanguageCode()
        val isLangMismatched = preferredLang == "it" && cached?.source == "Last.fm" && !cached.isCustomBio
        if (cached != null) {
            emit(cached)
            if (!forceRefresh && !isLangMismatched && (cached.isCustomPhoto || cached.isCustomBio || (cached.photoUrl != null && !cached.biography.isNullOrBlank()))) {
                artistUpdates.filter { it.equals(clean, ignoreCase = true) }.collect {
                    memoryCache[clean.lowercase()]?.let { emit(it) }
                }
                return@flow
            }
        }

        val fetched = fetchArtistInfoRemote(clean)
        memoryCache[clean.lowercase()] = fetched
        saveCacheToDisk()
        emit(fetched)

        artistUpdates.filter { it.equals(clean, ignoreCase = true) }.collect {
            memoryCache[clean.lowercase()]?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Recupera le informazioni dell'artista in modo asincrono.
     */
    suspend fun getArtistInfo(artistName: String, forceRefresh: Boolean = false): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) return@withContext ArtistInfo(name = artistName)

        val cached = memoryCache[clean.lowercase()]
        val preferredLang = getPreferredLanguageCode()
        val isLangMismatched = preferredLang == "it" && cached?.source == "Last.fm" && !cached.isCustomBio
        if (cached != null && !forceRefresh && !isLangMismatched && (cached.isCustomPhoto || cached.isCustomBio || (cached.photoUrl != null && !cached.biography.isNullOrBlank()))) {
            return@withContext cached
        }

        val fetched = fetchArtistInfoRemote(clean)
        memoryCache[clean.lowercase()] = fetched
        saveCacheToDisk()
        fetched
    }

    /**
     * Imposta un'immagine personalizzata per l'artista da un Uri (Galleria o file locale).
     * L'immagine viene copiata nello storage interno dell'app per garantire persistenza permanente.
     */
    suspend fun setCustomArtistPhoto(artistName: String, uri: Uri): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        val customDir = File(context.filesDir, "custom_artists").apply { if (!exists()) mkdirs() }
        val customFile = File(customDir, "${clean.lowercase().hashCode()}_photo.jpg")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                customFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore salvataggio foto personalizzata per '$clean'", e)
        }

        val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
        val updated = existing.copy(
            photoUrl = customFile.toURI().toString(),
            isCustomPhoto = true,
            lastUpdated = System.currentTimeMillis()
        )
        memoryCache[clean.lowercase()] = updated
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        updated
    }

    /**
     * Imposta come foto profilo dell'artista la copertina di uno dei suoi album locali.
     */
    suspend fun setCustomArtistAlbumCover(artistName: String, albumArtUri: Uri): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        val customDir = File(context.filesDir, "custom_artists").apply { if (!exists()) mkdirs() }
        val customFile = File(customDir, "${clean.lowercase().hashCode()}_photo.jpg")

        try {
            context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                customFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore copia copertina album come foto artista per '$clean'", e)
        }

        val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
        val photoPath = if (customFile.exists() && customFile.length() > 0) {
            customFile.toURI().toString()
        } else {
            albumArtUri.toString()
        }
        val updated = existing.copy(
            photoUrl = photoPath,
            isCustomPhoto = true,
            lastUpdated = System.currentTimeMillis()
        )
        memoryCache[clean.lowercase()] = updated
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        updated
    }

    /**
     * Ripristina la foto profilo dell'artista, eliminando l'override personalizzato
     * e rieseguendo la ricerca automatica.
     */
    suspend fun resetCustomArtistPhoto(artistName: String): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        val customDir = File(context.filesDir, "custom_artists")
        val customFile = File(customDir, "${clean.lowercase().hashCode()}_photo.jpg")
        if (customFile.exists()) {
            customFile.delete()
        }
        val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
        val updated = existing.copy(
            photoUrl = null,
            isCustomPhoto = false,
            lastUpdated = System.currentTimeMillis()
        )
        memoryCache[clean.lowercase()] = updated
        saveCacheToDisk()

        val refreshed = fetchArtistInfoRemote(clean)
        memoryCache[clean.lowercase()] = refreshed
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        refreshed
    }

    /**
     * Imposta o modifica manualmente il testo della biografia dell'artista.
     */
    suspend fun setCustomArtistBio(artistName: String, customBio: String?): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
        val cleanText = customBio?.trim()?.ifBlank { null }
        val updated = existing.copy(
            biography = cleanText,
            source = if (cleanText != null) "Personalizzata" else null,
            isCustomBio = true,
            lastUpdated = System.currentTimeMillis()
        )
        memoryCache[clean.lowercase()] = updated
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        updated
    }

    /**
     * Ripristina la biografia dell'artista ai dati automatici remoti.
     */
    suspend fun resetCustomArtistBio(artistName: String): ArtistInfo = withContext(Dispatchers.IO) {
        val clean = cleanArtistName(artistName)
        val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
        val updated = existing.copy(
            biography = null,
            isCustomBio = false,
            source = null,
            lastUpdated = System.currentTimeMillis()
        )
        memoryCache[clean.lowercase()] = updated
        saveCacheToDisk()

        val refreshed = fetchArtistInfoRemote(clean)
        memoryCache[clean.lowercase()] = refreshed
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        refreshed
    }

    /**
     * Cerca la biografia dell'artista su Wikipedia utilizzando una chiave di ricerca alternativa
     * (utile per omonimie o pseudonimi, es. "Nicola Pittalis" o "Pitta rapper").
     */
    suspend fun searchArtistBioOnWikipedia(artistName: String, query: String): ArtistInfo? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext null
        val clean = cleanArtistName(artistName)

        var wikiResponse: JSONObject? = null

        // 1. Prova prima con il titolo esatto del query
        val wikiTitle = cleanQuery.replace(' ', '_')
        val encodedWiki = URLEncoder.encode(wikiTitle, "UTF-8")
        val itWikiUrl = "https://it.wikipedia.org/api/rest_v1/page/summary/$encodedWiki"
        wikiResponse = executeGet(itWikiUrl)

        if (wikiResponse == null || wikiResponse.optString("extract").isBlank()) {
            val enWikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedWiki"
            wikiResponse = executeGet(enWikiUrl)
        }

        // 2. Se non trova la pagina diretta, usa l'API di ricerca MediaWiki OpenSearch
        if (wikiResponse == null || wikiResponse.optString("extract").isBlank()) {
            try {
                val openSearchUrl = "https://it.wikipedia.org/w/api.php?action=opensearch&search=${URLEncoder.encode(cleanQuery, "UTF-8")}&limit=3&namespace=0&format=json"
                val searchReq = Request.Builder()
                    .url(openSearchUrl)
                    .header("User-Agent", "MusiCal/0.2.0 (music player app; Android 16)")
                    .build()
                client.newCall(searchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val jsonArray = org.json.JSONArray(body)
                            if (jsonArray.length() >= 2) {
                                val titlesArray = jsonArray.optJSONArray(1)
                                if (titlesArray != null && titlesArray.length() > 0) {
                                    val firstTitle = titlesArray.optString(0)
                                    if (firstTitle.isNotBlank()) {
                                        val firstEncoded = URLEncoder.encode(firstTitle.replace(' ', '_'), "UTF-8")
                                        wikiResponse = executeGet("https://it.wikipedia.org/api/rest_v1/page/summary/$firstEncoded")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val extract = wikiResponse?.optString("extract")?.trim()
        if (!extract.isNullOrBlank()) {
            val existing = memoryCache[clean.lowercase()] ?: ArtistInfo(name = clean)
            val updated = existing.copy(
                biography = limitBiographyLength(extract),
                source = "Wikipedia",
                isCustomBio = true,
                lastUpdated = System.currentTimeMillis()
            )
            memoryCache[clean.lowercase()] = updated
            saveCacheToDisk()
            artistUpdates.emit(clean.lowercase())
            updated
        } else {
            null
        }
    }

    /**
     * Interroga Wikipedia (summary REST API + MediaWiki OpenSearch di riserva)
     * per recuperare l'estratto biografico e l'eventuale foto promozionale.
     */
    private fun fetchWikipediaSummary(title: String, lang: String = "it"): Pair<String?, String?> {
        var biography: String? = null
        var photoUrl: String? = null
        val domain = if (lang.equals("it", ignoreCase = true)) "it.wikipedia.org" else "en.wikipedia.org"
        val wikiTitle = title.trim().replace(' ', '_')
        val encodedWiki = URLEncoder.encode(wikiTitle, "UTF-8")

        // 1. Prova prima con il titolo esatto
        var wikiResponse = executeGet("https://$domain/api/rest_v1/page/summary/$encodedWiki")

        // 2. Se non trova nulla, prova con OpenSearch su quel dominio
        if (wikiResponse == null || wikiResponse.optString("extract").isBlank()) {
            try {
                val openSearchUrl = "https://$domain/w/api.php?action=opensearch&search=${URLEncoder.encode(title.trim(), "UTF-8")}&limit=3&namespace=0&format=json"
                val searchReq = Request.Builder()
                    .url(openSearchUrl)
                    .header("User-Agent", "MusiCal/0.2.0 (music player app; Android 16)")
                    .build()
                client.newCall(searchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val jsonArray = org.json.JSONArray(body)
                            if (jsonArray.length() >= 2) {
                                val titlesArray = jsonArray.optJSONArray(1)
                                if (titlesArray != null && titlesArray.length() > 0) {
                                    val firstTitle = titlesArray.optString(0)
                                    if (firstTitle.isNotBlank()) {
                                        val firstEncoded = URLEncoder.encode(firstTitle.replace(' ', '_'), "UTF-8")
                                        wikiResponse = executeGet("https://$domain/api/rest_v1/page/summary/$firstEncoded")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (wikiResponse != null) {
            val extract = wikiResponse.optString("extract").trim()
            if (extract.isNotBlank()) {
                biography = extract
            }
            val originalImg = wikiResponse.optJSONObject("originalimage")?.optString("source")
            val thumbImg = wikiResponse.optJSONObject("thumbnail")?.optString("source")
            photoUrl = originalImg?.takeIf { it.isNotBlank() } ?: thumbImg?.takeIf { it.isNotBlank() }
        }
        return Pair(biography, photoUrl)
    }

    /**
     * Interroga l'API di Last.fm per ottenere la biografia dell'artista con specificazione di lingua.
     */
    private fun fetchLastFmBio(artistName: String, lang: String): String? {
        if (LastFmConfig.API_KEY.isBlank()) return null
        return try {
            val encodedLastFm = URLEncoder.encode(artistName, "UTF-8")
            val lastFmUrl = "${LastFmConfig.BASE_URL}?method=artist.getinfo&artist=$encodedLastFm&api_key=${LastFmConfig.API_KEY}&format=json&autocorrect=1&lang=$lang"
            val json = executeGet(lastFmUrl)
            if (json != null) {
                val artistObj = json.optJSONObject("artist")
                val bioObj = artistObj?.optJSONObject("bio")
                val rawContent = bioObj?.optString("content")?.trim().orEmpty()
                val rawSummary = bioObj?.optString("summary")?.trim().orEmpty()
                val rawBio = rawContent.ifBlank { rawSummary }
                if (rawBio.isNotBlank()) {
                    cleanLastFmBio(rawBio).takeIf { it.isNotBlank() }
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(tag, "Errore recupero biografia Last.fm per '$artistName': ${e.message}")
            null
        }
    }

    /**
     * Effettua l'interrogazione delle sorgenti remote rispettando la lingua preferita dell'utente:
     * - Se la lingua è l'italiano: priorità assoluta a Wikipedia in italiano (it.wikipedia.org) per
     *   garantire biografie in italiano anche per artisti internazionali (es. Drake, Travis Scott),
     *   con fallback su Last.fm (&lang=it) e poi Wikipedia EN.
     * - Se la lingua è l'inglese: Last.fm come fonte primaria con fallback su Wikipedia EN.
     * - Deezer rimane fonte primaria per le foto HD promozionali.
     */
    private suspend fun fetchArtistInfoRemote(cleanName: String): ArtistInfo = withContext(Dispatchers.IO) {
        val existing = memoryCache[cleanName.lowercase()]
        val isCustomPhoto = existing?.isCustomPhoto == true
        val isCustomBio = existing?.isCustomBio == true
        var photoUrl: String? = existing?.photoUrl.takeIf { isCustomPhoto }
        var biography: String? = existing?.biography.takeIf { isCustomBio }
        var sourceLabel = if (isCustomBio) (existing?.source ?: "Personalizzata") else "Deezer / Wikipedia"

        // 1. Recupero Foto HD da Deezer Public API se non è personalizzata
        if (!isCustomPhoto && photoUrl == null) {
            try {
                val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
                val deezerUrl = "https://api.deezer.com/search/artist?q=$encodedQuery"
                val request = Request.Builder()
                    .url(deezerUrl)
                    .header("User-Agent", "MusiCal/0.2.0 (music player app; Android 16)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string().orEmpty()
                        if (bodyString.isNotBlank()) {
                            val json = JSONObject(bodyString)
                            val dataArray = json.optJSONArray("data")
                            if (dataArray != null && dataArray.length() > 0) {
                                var bestObj: JSONObject? = null
                                for (i in 0 until dataArray.length()) {
                                    val obj = dataArray.optJSONObject(i)
                                    if (obj != null && obj.optString("name").equals(cleanName, ignoreCase = true)) {
                                        bestObj = obj
                                        break
                                    }
                                }
                                val target = bestObj ?: dataArray.optJSONObject(0)
                                if (target != null) {
                                    photoUrl = target.optString("picture_xl").takeIf { it.isNotBlank() }
                                        ?: target.optString("picture_big").takeIf { it.isNotBlank() }
                                        ?: target.optString("picture_medium").takeIf { it.isNotBlank() }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Errore recupero foto Deezer per '$cleanName': ${e.message}")
            }
        }

        // 2. Recupero Biografia coerente con la lingua impostata se non è personalizzata
        val preferredLang = getPreferredLanguageCode()
        if (!isCustomBio && biography == null) {
            if (preferredLang == "it") {
                // Fonte Primaria per lingua italiana: Wikipedia in italiano
                val (wikiItBio, wikiItPhoto) = fetchWikipediaSummary(cleanName, "it")
                if (!wikiItBio.isNullOrBlank()) {
                    biography = limitBiographyLength(wikiItBio)
                    sourceLabel = "Wikipedia"
                }
                if (!isCustomPhoto && photoUrl == null && wikiItPhoto != null) {
                    photoUrl = wikiItPhoto
                }

                // Fallback 1: Last.fm con lingua italiana
                if (biography.isNullOrBlank()) {
                    val lastFmBio = fetchLastFmBio(cleanName, "it")
                    if (!lastFmBio.isNullOrBlank()) {
                        biography = limitBiographyLength(lastFmBio)
                        sourceLabel = "Last.fm"
                    }
                }

                // Fallback 2: Wikipedia in lingua inglese
                if (biography.isNullOrBlank()) {
                    val (wikiEnBio, wikiEnPhoto) = fetchWikipediaSummary(cleanName, "en")
                    if (!wikiEnBio.isNullOrBlank()) {
                        biography = limitBiographyLength(wikiEnBio)
                        sourceLabel = "Wikipedia"
                    }
                    if (!isCustomPhoto && photoUrl == null && wikiEnPhoto != null) {
                        photoUrl = wikiEnPhoto
                    }
                }
            } else {
                // Per lingua inglese (o altre): Last.fm come fonte primaria
                val lastFmBio = fetchLastFmBio(cleanName, preferredLang)
                if (!lastFmBio.isNullOrBlank()) {
                    biography = limitBiographyLength(lastFmBio)
                    sourceLabel = "Last.fm"
                }

                // Fallback: Wikipedia in lingua inglese
                if (biography.isNullOrBlank()) {
                    val (wikiEnBio, wikiEnPhoto) = fetchWikipediaSummary(cleanName, "en")
                    if (!wikiEnBio.isNullOrBlank()) {
                        biography = limitBiographyLength(wikiEnBio)
                        sourceLabel = "Wikipedia"
                    }
                    if (!isCustomPhoto && photoUrl == null && wikiEnPhoto != null) {
                        photoUrl = wikiEnPhoto
                    }
                }
            }
        } else if (!isCustomPhoto && photoUrl == null) {
            // Se abbiamo già la bio ma manca la foto, prova a recuperarla da Wikipedia
            try {
                val (_, wikiPhoto) = fetchWikipediaSummary(cleanName, preferredLang)
                if (wikiPhoto != null) {
                    photoUrl = wikiPhoto
                }
            } catch (_: Exception) {}
        }

        ArtistInfo(
            name = cleanName,
            photoUrl = photoUrl,
            biography = biography,
            source = sourceLabel,
            isCustomPhoto = isCustomPhoto,
            isCustomBio = isCustomBio,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun executeGet(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusiCal/0.2.0 (music player app; Android 16)")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isNotBlank()) JSONObject(bodyString) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun loadCacheFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val fis: FileInputStream = atomicFile.openRead()
            fis.bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonString = reader.readText()
                if (jsonString.isNotBlank()) {
                    val json = JSONObject(jsonString)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key)
                        if (obj != null) {
                            val info = ArtistInfo(
                                name = obj.optString("name", key),
                                photoUrl = obj.optString("photoUrl").takeIf { it.isNotBlank() },
                                biography = obj.optString("biography").takeIf { it.isNotBlank() },
                                source = obj.optString("source").takeIf { it.isNotBlank() },
                                isCustomPhoto = obj.optBoolean("isCustomPhoto", false),
                                isCustomBio = obj.optBoolean("isCustomBio", false),
                                lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                            )
                            memoryCache[key.lowercase()] = info
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore durante il caricamento di artist_cache.json", e)
        }
    }

    @Synchronized
    private fun saveCacheToDisk() {
        try {
            val root = JSONObject()
            for ((key, info) in memoryCache) {
                val obj = JSONObject()
                obj.put("name", info.name)
                info.photoUrl?.let { obj.put("photoUrl", it) }
                info.biography?.let { obj.put("biography", it) }
                info.source?.let { obj.put("source", it) }
                if (info.isCustomPhoto) obj.put("isCustomPhoto", true)
                if (info.isCustomBio) obj.put("isCustomBio", true)
                obj.put("lastUpdated", info.lastUpdated)
                root.put(key, obj)
            }

            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            val fos = atomicFile.startWrite()
            try {
                fos.write(bytes)
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                atomicFile.failWrite(fos)
                throw e
            }

            val newFile = File(cacheFile.parentFile, "${cacheFile.name}.new")
            if (newFile.exists()) {
                java.nio.file.Files.move(newFile.toPath(), cacheFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val backupFile = File(cacheFile.parentFile, "${cacheFile.name}.bak")
            if (backupFile.exists() && cacheFile.exists()) {
                try {
                    java.nio.file.Files.deleteIfExists(backupFile.toPath())
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore durante il salvataggio di artist_cache.json", e)
        }
    }
}

/**
 * Pulisce il testo della biografia fornito da Last.fm rimuovendo tag HTML,
 * rimandi "Read more on Last.fm" e note di licenza Creative Commons in calce.
 */
fun cleanLastFmBio(rawBio: String): String {
    var text = rawBio
    // Rimuove tag HTML
    text = text.replace(Regex("<[^>]*>"), "")
    // Rimuove rimandi promozionali "Read more on Last.fm"
    text = text.replace(Regex("(?i)Read more on Last\\.fm.*"), "")
    // Rimuove note di licenza Creative Commons in calce
    text = text.replace(Regex("(?i)User-contributed text is available under the Creative Commons.*"), "")
    // Normalizza ritorni a capo multipli mantenendo i paragrafi puliti
    text = text.replace(Regex("\n{3,}"), "\n\n").trim()
    return text
}

/**
 * Limita la lunghezza delle biografie troppo estese (es. pagine enciclopediche di Last.fm o Wikipedia),
 * troncando in modo intelligente alla fine della frase più vicina entro o nei pressi della soglia limite.
 */
fun limitBiographyLength(bio: String, maxChars: Int = 1200): String {
    val trimmed = bio.trim()
    if (trimmed.length <= maxChars) return trimmed

    val sub = trimmed.substring(0, maxChars)
    val sentenceEndRegex = Regex("""[.!?](\s+|$)""")
    val matches = sentenceEndRegex.findAll(sub).toList()
    val lastMatch = matches.lastOrNull()

    if (lastMatch != null && lastMatch.range.first >= (maxChars * 0.4).toInt()) {
        return trimmed.substring(0, lastMatch.range.first + 1).trim()
    }

    // Se non troviamo una chiusura prima di maxChars, prova a cercare la fine frase poco dopo (fino a maxChars + 150)
    val extensionLimit = (maxChars + 150).coerceAtMost(trimmed.length)
    val extendedSub = trimmed.substring(0, extensionLimit)
    val extendedMatches = sentenceEndRegex.findAll(extendedSub).toList()
    val extendedMatch = extendedMatches.firstOrNull { it.range.first >= maxChars }
    if (extendedMatch != null) {
        return trimmed.substring(0, extendedMatch.range.first + 1).trim()
    }

    // Fallback: tronca all'ultimo spazio aggiungendo ellissi
    val lastSpace = sub.lastIndexOf(' ')
    return if (lastSpace > (maxChars * 0.4).toInt()) {
        trimmed.substring(0, lastSpace).trim() + "..."
    } else {
        sub.trim() + "..."
    }
}
