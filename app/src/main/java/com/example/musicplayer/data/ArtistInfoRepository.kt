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

        // Verifica se la cache necessita di aggiornamento:
        // 1. Se la biografia proviene da Wikipedia (non personalizzata), Last.fm è la nuova fonte primaria
        // 2. Se la biografia memorizzata non è affidabile (non musicale) o troncata in modo incompleto ("...")
        // 3. Se la biografia o la foto mancano
        val isBogusBio = cached?.biography != null && !cached.isCustomBio && !isReliableMusicText(cached.biography)
        val isOldWikipediaBio = cached?.source == "Wikipedia" && !cached.isCustomBio
        val isTruncatedBio = cached?.biography != null && !cached.isCustomBio && cached.biography.endsWith("...")
        val needsBioRefresh = cached == null || isBogusBio || isOldWikipediaBio || isTruncatedBio || cached.biography.isNullOrBlank()
        val needsPhotoRefresh = cached == null || (!cached.isCustomPhoto && cached.photoUrl == null)

        if (cached != null) {
            // Se la biografia era palesemente errata o non musicale, emetti subito la versione depurata
            if (isBogusBio) {
                emit(cached.copy(biography = null, source = null))
            } else {
                emit(cached)
            }

            if (!forceRefresh && !needsBioRefresh && !needsPhotoRefresh) {
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
        val isBogusBio = cached?.biography != null && !cached.isCustomBio && !isReliableMusicText(cached.biography)
        val isOldWikipediaBio = cached?.source == "Wikipedia" && !cached.isCustomBio
        val isTruncatedBio = cached?.biography != null && !cached.isCustomBio && cached.biography.endsWith("...")
        val needsBioRefresh = cached == null || isBogusBio || isOldWikipediaBio || isTruncatedBio || cached.biography.isNullOrBlank()
        val needsPhotoRefresh = cached == null || (!cached.isCustomPhoto && cached.photoUrl == null)

        if (cached != null && !forceRefresh && !needsBioRefresh && !needsPhotoRefresh) {
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

        // 1. Prova prima con il titolo esatto del query su Wikipedia IT
        val wikiTitle = cleanQuery.replace(' ', '_')
        val encodedWiki = URLEncoder.encode(wikiTitle, "UTF-8")
        val itWikiUrl = "https://it.wikipedia.org/api/rest_v1/page/summary/$encodedWiki"
        var candidateResp = executeGet(itWikiUrl)
        if (candidateResp != null && isReliableMusicWikipediaPage(candidateResp, cleanQuery, "it")) {
            wikiResponse = candidateResp
        }

        // Se non trovato su IT, prova su EN
        if (wikiResponse == null) {
            val enWikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedWiki"
            candidateResp = executeGet(enWikiUrl)
            if (candidateResp != null && isReliableMusicWikipediaPage(candidateResp, cleanQuery, "en")) {
                wikiResponse = candidateResp
            }
        }

        // 2. Se non trova la pagina diretta o non era affidabile, usa OpenSearch
        if (wikiResponse == null) {
            val searchDomains = listOf("it.wikipedia.org", "en.wikipedia.org")
            for (domain in searchDomains) {
                val lang = if (domain.startsWith("it")) "it" else "en"
                try {
                    val openSearchUrl = "https://$domain/w/api.php?action=opensearch&search=${URLEncoder.encode(cleanQuery, "UTF-8")}&limit=6&namespace=0&format=json"
                    val searchReq = Request.Builder()
                        .url(openSearchUrl)
                        .header("User-Agent", "MusiCal/0.2.1 (music player app; Android 16)")
                        .build()
                    client.newCall(searchReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.isNotBlank()) {
                                val jsonArray = org.json.JSONArray(body)
                                if (jsonArray.length() >= 2) {
                                    val titlesArray = jsonArray.optJSONArray(1)
                                    if (titlesArray != null) {
                                        for (i in 0 until titlesArray.length()) {
                                            val candTitle = titlesArray.optString(i)
                                            if (candTitle.isNotBlank()) {
                                                val candEncoded = URLEncoder.encode(candTitle.replace(' ', '_'), "UTF-8")
                                                val candResp = executeGet("https://$domain/api/rest_v1/page/summary/$candEncoded")
                                                if (candResp != null && isReliableMusicWikipediaPage(candResp, cleanQuery, lang)) {
                                                    wikiResponse = candResp
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (wikiResponse != null) break
            }
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
     * Interroga Wikipedia (summary REST API + tentativi disambiguati + OpenSearch di riserva)
     * per recuperare l'estratto biografico e l'eventuale foto promozionale.
     * Applica controlli rigorosi di affidabilità (isReliableMusicWikipediaPage)
     * per garantire che la pagina appartenga effettivamente a un artista/progetto musicale.
     */
    private fun fetchWikipediaSummary(title: String, lang: String = "it"): Pair<String?, String?> {
        var biography: String? = null
        var photoUrl: String? = null
        val domain = if (lang.equals("it", ignoreCase = true)) "it.wikipedia.org" else "en.wikipedia.org"
        val cleanTitle = title.trim()
        val wikiTitle = cleanTitle.replace(' ', '_')
        val encodedWiki = URLEncoder.encode(wikiTitle, "UTF-8")

        var verifiedResponse: JSONObject? = null

        // 1. Prova prima con il titolo esatto
        val directResponse = executeGet("https://$domain/api/rest_v1/page/summary/$encodedWiki")
        if (directResponse != null && isReliableMusicWikipediaPage(directResponse, cleanTitle, lang)) {
            verifiedResponse = directResponse
        }

        // 2. Se non è affidabile (es. pagina di disambiguazione o termine comune come "Corona", "Genesis", "Kiss"),
        // prova le forme disambiguate musicali tipiche di Wikipedia
        if (verifiedResponse == null) {
            val suffixes = if (lang.equals("it", ignoreCase = true)) {
                listOf("(cantante)", "(gruppo musicale)", "(rapper)", "(cantautore)", "(cantautrice)", "(musicista)", "(disc jockey)", "(band)")
            } else {
                listOf("(musician)", "(singer)", "(band)", "(rapper)", "(musical group)", "(songwriter)", "(DJ)")
            }
            for (suffix in suffixes) {
                val disambiguatedTitle = "${cleanTitle}_$suffix".replace(' ', '_')
                val disambiguatedResp = executeGet("https://$domain/api/rest_v1/page/summary/${URLEncoder.encode(disambiguatedTitle, "UTF-8")}")
                if (disambiguatedResp != null && isReliableMusicWikipediaPage(disambiguatedResp, cleanTitle, lang)) {
                    verifiedResponse = disambiguatedResp
                    break
                }
            }
        }

        // 3. Se ancora non trovato, cerca tramite MediaWiki OpenSearch esaminando i candidati
        if (verifiedResponse == null) {
            try {
                val openSearchUrl = "https://$domain/w/api.php?action=opensearch&search=${URLEncoder.encode(cleanTitle, "UTF-8")}&limit=6&namespace=0&format=json"
                val searchReq = Request.Builder()
                    .url(openSearchUrl)
                    .header("User-Agent", "MusiCal/0.2.1 (music player app; Android 16)")
                    .build()
                client.newCall(searchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val jsonArray = org.json.JSONArray(body)
                            if (jsonArray.length() >= 2) {
                                val titlesArray = jsonArray.optJSONArray(1)
                                if (titlesArray != null) {
                                    for (i in 0 until titlesArray.length()) {
                                        val candTitle = titlesArray.optString(i)
                                        if (candTitle.isNotBlank()) {
                                            val candEncoded = URLEncoder.encode(candTitle.replace(' ', '_'), "UTF-8")
                                            val candResp = executeGet("https://$domain/api/rest_v1/page/summary/$candEncoded")
                                            if (candResp != null && isReliableMusicWikipediaPage(candResp, cleanTitle, lang)) {
                                                verifiedResponse = candResp
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (verifiedResponse != null) {
            val extract = verifiedResponse.optString("extract").trim()
            if (extract.isNotBlank()) {
                biography = extract
            }
            val originalImg = verifiedResponse.optJSONObject("originalimage")?.optString("source")
            val thumbImg = verifiedResponse.optJSONObject("thumbnail")?.optString("source")
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
     * Effettua l'interrogazione delle sorgenti remote rispettando la nuova gerarchia autoritativa:
     * - Last.fm è la FONTE PRINCIPALE per la biografia di TUTTI gli artisti (musicale per definizione).
     *   Interroga Last.fm nella lingua preferita dell'utente.
     * - Wikipedia funge da FALLBACK con verifica preventiva di affidabilità (isReliableMusicWikipediaPage):
     *   se la lingua è l'italiano e Last.fm non ha la biografia in italiano, consulta Wikipedia IT verificata,
     *   poi Last.fm EN ed infine Wikipedia EN verificata.
     * - Deezer rimane la fonte primaria per le foto HD promozionali (picture_xl 1000x1000).
     */
    private suspend fun fetchArtistInfoRemote(cleanName: String): ArtistInfo = withContext(Dispatchers.IO) {
        val existing = memoryCache[cleanName.lowercase()]
        val isCustomPhoto = existing?.isCustomPhoto == true
        val isCustomBio = existing?.isCustomBio == true
        var photoUrl: String? = existing?.photoUrl.takeIf { isCustomPhoto }
        var biography: String? = existing?.biography.takeIf { isCustomBio }
        var sourceLabel = if (isCustomBio) (existing?.source ?: "Personalizzata") else "Deezer / Last.fm"

        // 1. Recupero Foto HD da Deezer Public API se non è personalizzata
        if (!isCustomPhoto && photoUrl == null) {
            try {
                val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
                val deezerUrl = "https://api.deezer.com/search/artist?q=$encodedQuery"
                val request = Request.Builder()
                    .url(deezerUrl)
                    .header("User-Agent", "MusiCal/0.2.1 (music player app; Android 16)")
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

        // 2. Recupero Biografia con Last.fm come FONTE PRINCIPALE per tutti gli artisti
        val preferredLang = getPreferredLanguageCode()
        if (!isCustomBio && biography == null) {
            // A. Fonte Primaria: Last.fm nella lingua preferita dell'utente
            val lastFmPrimaryBio = fetchLastFmBio(cleanName, preferredLang)
            if (!lastFmPrimaryBio.isNullOrBlank()) {
                biography = limitBiographyLength(lastFmPrimaryBio)
                sourceLabel = "Last.fm"
            }

            // B. Se la lingua è l'italiano e Last.fm non ha bio in italiano:
            // Fallback 1: Wikipedia in italiano con verifica rigorosa di affidabilità musicale
            if (biography.isNullOrBlank() && preferredLang == "it") {
                val (wikiItBio, wikiItPhoto) = fetchWikipediaSummary(cleanName, "it")
                if (!wikiItBio.isNullOrBlank()) {
                    biography = limitBiographyLength(wikiItBio)
                    sourceLabel = "Wikipedia"
                    if (!isCustomPhoto && photoUrl == null && wikiItPhoto != null) {
                        photoUrl = wikiItPhoto
                    }
                }
            }

            // C. Fallback 2: Last.fm in lingua inglese (se la query primaria era in italiano ed era vuota)
            if (biography.isNullOrBlank() && preferredLang != "en") {
                val lastFmEnBio = fetchLastFmBio(cleanName, "en")
                if (!lastFmEnBio.isNullOrBlank()) {
                    biography = limitBiographyLength(lastFmEnBio)
                    sourceLabel = "Last.fm"
                }
            }

            // D. Fallback 3: Wikipedia in lingua inglese con verifica rigorosa di affidabilità musicale
            if (biography.isNullOrBlank()) {
                val (wikiEnBio, wikiEnPhoto) = fetchWikipediaSummary(cleanName, "en")
                if (!wikiEnBio.isNullOrBlank()) {
                    biography = limitBiographyLength(wikiEnBio)
                    sourceLabel = "Wikipedia"
                    if (!isCustomPhoto && photoUrl == null && wikiEnPhoto != null) {
                        photoUrl = wikiEnPhoto
                    }
                }
            }
        } else if (!isCustomPhoto && photoUrl == null) {
            // Se abbiamo già la bio ma manca la foto, prova a recuperarla da Wikipedia verificata
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
            .header("User-Agent", "MusiCal/0.2.1 (music player app; Android 16)")
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
 * Pulisce il testo della biografia fornito da Last.fm:
 * - rimuove tag HTML
 * - rimuove rimandi promozionali "Read more on Last.fm" e licenze Creative Commons in calce
 * - decodifica entità HTML comuni (&quot;, &apos;, &#39;, &amp;, &lt;, &gt;)
 * - normalizza i ritorni a capo
 * - rimuove eventuali ellissi o puntini sospesi rimasti a fine testo
 */
fun cleanLastFmBio(rawBio: String): String {
    var text = rawBio
    // Rimuove tag HTML
    text = text.replace(Regex("<[^>]*>"), "")
    // Rimuove rimandi promozionali "Read more on Last.fm"
    text = text.replace(Regex("(?i)Read more on Last\\.fm.*"), "")
    // Rimuove note di licenza Creative Commons in calce
    text = text.replace(Regex("(?i)User-contributed text is available under the Creative Commons.*"), "")
    // Decodifica entità HTML comuni
    text = text.replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
    // Normalizza ritorni a capo multipli mantenendo i paragrafi puliti
    text = text.replace(Regex("\r\n"), "\n")
    text = text.replace(Regex("\n{3,}"), "\n\n")
    // Rimuove eventuali puntini o ellissi lasciati in calce prima del link rimosso
    text = text.replace(Regex("""\s*\.{2,}\s*$"""), ".").trim()
    return text
}

/**
 * Limita la lunghezza delle biografie preservando rigorosamente l'integrità dei paragrafi e dei concetti.
 * Se il testo è strutturato in più paragrafi (separati da doppio a capo), include paragrafi completi
 * fino alla soglia desiderata (default ~2800 caratteri) e non tronca mai a metà di un paragrafo,
 * evitando di lasciare concetti incompiuti ("campati in aria").
 * Se il testo è un unico blocco indiviso, tronca alla fine della frase più vicina.
 */
fun limitBiographyLength(bio: String, maxChars: Int = 2800): String {
    val trimmed = bio.trim()
    if (trimmed.length <= maxChars) return trimmed

    // 1. Se il testo è strutturato in paragrafi (separati da doppio a capo)
    val paragraphs = trimmed.split(Regex("""\r?\n\s*\r?\n"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (paragraphs.size > 1) {
        val keptParagraphs = mutableListOf<String>()
        var accumulatedLength = 0

        for (paragraph in paragraphs) {
            val addedLength = if (keptParagraphs.isEmpty()) paragraph.length else paragraph.length + 2 // +2 per "\n\n"
            // Se l'aggiunta del paragrafo rientra nel limite, oppure se è il primo paragrafo entro una soglia estesa
            if (accumulatedLength + addedLength <= maxChars || (keptParagraphs.isEmpty() && addedLength <= maxChars + 400)) {
                keptParagraphs.add(paragraph)
                accumulatedLength += addedLength
            } else {
                // Non possiamo includere questo intero paragrafo senza superare maxChars.
                // Se abbiamo già almeno 1 paragrafo completo, ci fermiamo qui per non lasciare
                // il concetto campato in aria.
                break
            }
        }

        if (keptParagraphs.isNotEmpty()) {
            return keptParagraphs.joinToString("\n\n").trim()
        }
    }

    // 2. Se è un unico paragrafo lungo senza interruzioni di riga, tronchiamo a fine frase
    val sub = trimmed.substring(0, maxChars)
    val sentenceEndRegex = Regex("""[.!?](\s+|$)""")
    val matches = sentenceEndRegex.findAll(sub).toList()
    val lastMatch = matches.lastOrNull()

    if (lastMatch != null && lastMatch.range.first >= (maxChars * 0.4).toInt()) {
        return trimmed.substring(0, lastMatch.range.first + 1).trim()
    }

    // Se non troviamo una chiusura prima di maxChars, cerca la fine frase poco dopo (fino a maxChars + 250)
    val extensionLimit = (maxChars + 250).coerceAtMost(trimmed.length)
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

/**
 * Verifica l'affidabilità di una pagina Wikipedia rispetto all'artista musicale richiesto:
 * 1. Esclude categoricamente pagine di disambiguazione ("type == disambiguation" o testo "può riferirsi a:")
 * 2. Esclude pagine chiaramente appartenenti ad altri domini semantici (comuni, fiumi, fenomeni astronomici, chimica,
 *    specie botaniche/zoologiche, film, videogiochi, politici, atleti) che non contengono ruoli musicali
 * 3. Richiede la presenza di specifici indicatori musicali (suffissi nel titolo, descrizione di Wikidata musicale,
 *    o lemmi musicali espliciti nell'estratto come cantante, rapper, gruppo musicale, band, album, discografia, singoli)
 * 4. Verifica che il titolo della pagina corrisponda all'artista (o che il nome dell'artista sia citato nell'estratto in caso di redirect a nome anagrafico).
 */
fun isReliableMusicWikipediaPage(
    json: JSONObject,
    artistName: String,
    lang: String = "it"
): Boolean {
    val type = json.optString("type").trim()
    if (type.equals("disambiguation", ignoreCase = true) || type.equals("no-extract", ignoreCase = true)) {
        return false
    }

    val extract = json.optString("extract").trim()
    if (extract.isBlank()) return false
    val lowerExtract = extract.lowercase()

    // Controllo frasi tipiche di disambiguazione
    if (lowerExtract.startsWith("può riferirsi a:") ||
        lowerExtract.contains("può indicare:") ||
        lowerExtract.startsWith("may refer to:") ||
        lowerExtract.contains("may also refer to:")
    ) {
        return false
    }

    val rawTitle = json.optString("title").trim()
    val lowerTitle = rawTitle.lowercase()
    val description = json.optString("description").trim().lowercase()

    // 1. Riconoscimento suffissi disambiguati musicali nel titolo
    val hasMusicTitleSuffix = lowerTitle.contains("(cantante)") ||
        lowerTitle.contains("(cantautore)") ||
        lowerTitle.contains("(cantautrice)") ||
        lowerTitle.contains("(rapper)") ||
        lowerTitle.contains("(trapper)") ||
        lowerTitle.contains("(gruppo musicale)") ||
        lowerTitle.contains("(musicista)") ||
        lowerTitle.contains("(compositore)") ||
        lowerTitle.contains("(disc jockey)") ||
        lowerTitle.contains("(band)") ||
        lowerTitle.contains("(musician)") ||
        lowerTitle.contains("(singer)") ||
        lowerTitle.contains("(musical group)") ||
        lowerTitle.contains("(record producer)") ||
        lowerTitle.contains("(songwriter)") ||
        lowerTitle.contains("(dj)")

    // 2. Ruoli musicali primari
    val musicRoles = listOf(
        "cantante", "cantautore", "cantautrice", "rapper", "trapper", "gruppo musicale", "band",
        "musicista", "compositore", "compositrice", "disc jockey", "dj", "chitarrista", "batterista",
        "bassista", "pianista", "tastierista", "sassofonista", "produttore discografico", "beatmaker",
        "singer", "songwriter", "musician", "musical group", "record producer", "guitarist",
        "drummer", "bassist", "pianist", "keyboardist"
    )

    val hasMusicRoleInDescription = musicRoles.any { description.contains(it) }

    // 3. Filtro negativo: domini palesemente non musicali
    val nonMusicCategories = listOf(
        "comune", "città", "frazione", "provincia", "regione", "stato", "fiume", "lago", "valle",
        "montagna", "monte", "isola", "arcipelago", "municipality", "city", "town", "village",
        "river", "lake", "mountain", "island", "distretto",
        "costellazione", "stella", "galassia", "pianeta", "satellite", "asteroide", "minerale",
        "elemento chimico", "specie", "pianta", "albero", "fiore", "animale", "uccello", "pesce",
        "mammifero", "insetto", "constellation", "star", "galaxy", "planet", "species",
        "azienda", "società", "veicolo", "autovettura", "moneta", "valuta", "copricapo", "indumento",
        "film", "serie televisiva", "programma televisivo", "romanzo", "fumetto", "videogioco",
        "personaggio fittizio", "personaggio immaginario", "fictional character", "video game",
        "calciatore", "cestista", "nuotatore", "ciclista", "pilota", "atleta", "sportivo", "politico",
        "militare", "generale", "vescovo", "papa", "santo", "filosofo", "matematico", "fisico",
        "chimico", "astronomo", "architetto", "pittore", "scultore", "footballer", "basketball player",
        "politician", "painter", "sculptor"
    )

    if (nonMusicCategories.any { description.contains(it) } && !hasMusicRoleInDescription && !hasMusicTitleSuffix) {
        return false
    }

    // 4. Indicatori musicali generali nell'estratto
    val musicKeywords = listOf(
        "cantante", "cantautore", "cantautrice", "rapper", "trapper", "gruppo musicale", "band",
        "musicista", "compositore", "compositrice", "disc jockey", "dj", "chitarrista", "batterista",
        "bassista", "pianista", "tastierista", "sassofonista", "produttore discografico", "beatmaker",
        "album in studio", "studio album", "discografia", "discography", "singolo discografico", "singoli",
        "brano musicale", "brani", "canzone", "canzoni", "casa discografica", "etichetta discografica",
        "record label", "genere musicale", "classifica fimi", "billboard", "grammy",
        "festival di sanremo", "tournée", "concerto", "concerti", "musica pop", "musica rock",
        "musica rap", "musica trap", "musica hip hop", "musica elettronica", "musica jazz", "musica metal",
        "singer", "songwriter", "musician", "musical group", "record producer"
    )

    val hasMusicKeywordsInExtract = musicKeywords.any { lowerExtract.contains(it) }

    // Almeno uno tra suffisso titolo, descrizione musicale o keyword nell'estratto DEVE essere presente
    if (!hasMusicTitleSuffix && !hasMusicRoleInDescription && !hasMusicKeywordsInExtract) {
        return false
    }

    // 5. Verifica corrispondenza con il nome dell'artista
    val cleanArtist = artistName.trim().lowercase()
    val baseTitle = rawTitle.replace(Regex("""\s*\(.*?\)\s*"""), "").trim().lowercase()

    val titleMatches = baseTitle == cleanArtist ||
        baseTitle.replace("'", "").replace("-", " ") == cleanArtist.replace("'", "").replace("-", " ") ||
        baseTitle.startsWith("$cleanArtist ") ||
        cleanArtist.startsWith("$baseTitle ")

    val extractMentionsArtist = lowerExtract.contains(cleanArtist) ||
        (cleanArtist.contains(" ") && cleanArtist.split(" ").all { it.length > 2 && lowerExtract.contains(it) })

    return titleMatches || extractMentionsArtist
}

/**
 * Verifica sintetica se una stringa di testo contiene riferimenti a contenuti musicali.
 */
fun isReliableMusicText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val lower = text.lowercase()
    val musicKeywords = listOf(
        "cantante", "cantautore", "cantautrice", "rapper", "trapper", "gruppo musicale", "band",
        "musicista", "compositore", "compositrice", "disc jockey", "dj", "chitarrista", "batterista",
        "bassista", "pianista", "tastierista", "sassofonista", "produttore discografico", "beatmaker",
        "album", "singolo", "singoli", "discografia", "canzone", "canzoni", "brano", "brani",
        "casa discografica", "etichetta discografica", "genere musicale", "classifica fimi",
        "festival di sanremo", "tournée", "concerto", "concerti", "musica",
        "singer", "songwriter", "musician", "musical group", "record producer", "guitarist",
        "drummer", "bassist", "pianist", "discography", "studio album", "record label",
        "billboard", "grammy", "single", "singles", "song", "songs", "tour", "music"
    )
    return musicKeywords.any { lower.contains(it) }
}
