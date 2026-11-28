package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.util.AtomicFile
import com.example.musicplayer.data.lastfm.LastFmConfig
import com.example.musicplayer.data.network.SharedHttpClient
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Repository per il recupero, la normalizzazione, la memorizzazione persistente su cache locale
 * e la personalizzazione manuale (foto e biografia) delle informazioni sugli artisti.
 */
class ArtistInfoRepository(
    private val context: Context,
    private val client: OkHttpClient = SharedHttpClient.withTimeouts(connectSeconds = 10, readSeconds = 10)
) {

    private val tag = "ArtistInfoRepository"
    private val memoryCache = ConcurrentHashMap<String, ArtistInfo>()
    private val cacheFile = File(context.filesDir, "artist_cache.json")
    private val atomicFile = AtomicFile(cacheFile)
    private val artistUpdates = MutableSharedFlow<String>(extraBufferCapacity = 8)

    companion object {
        const val CURRENT_DISAMBIGUATION_VERSION = 2
    }

    @Volatile
    private var isCacheLoaded = false
    private val cacheLoadLock = Any()

    /**
     * Carica la cache degli artisti da disco una sola volta in modo thread-safe.
     * Deve essere invocato su un thread di background (IO/Default).
     */
    fun ensureCacheLoaded() {
        if (isCacheLoaded) return
        synchronized(cacheLoadLock) {
            if (!isCacheLoaded) {
                loadCacheFromDisk()
                isCacheLoaded = true
            }
        }
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
     * Restituisce in modo istantaneo e non bloccante (zero I/O su disco) le informazioni
     * dell'artista se la cache in memoria è già stata inizializzata. Sicuro sul Main Thread.
     */
    fun peekCachedArtistInfo(artistName: String): ArtistInfo? {
        if (!isCacheLoaded) return null
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) return null
        return memoryCache[clean.lowercase(Locale.ROOT)]
    }

    /**
     * Restituisce le informazioni dell'artista da cache in-memory se presenti,
     * altrimenti null.
     */
    fun getCachedArtistInfo(artistName: String): ArtistInfo? {
        ensureCacheLoaded()
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) return null
        return memoryCache[clean.lowercase()]
    }

    /**
     * Emette prima il dato in cache (se disponibile) e poi, se mancante, obsoleto o su richiesta,
     * interroga la rete usando i nomi dei brani e degli album locali per evitare conflitti di omonimia.
     * Rimane in ascolto di successivi aggiornamenti manuali per emetterli tempestivamente.
     */
    fun getArtistInfoFlow(
        artistName: String,
        forceRefresh: Boolean = false,
        referenceTrackTitles: List<String> = emptyList(),
        referenceAlbumTitles: List<String> = emptyList()
    ): Flow<ArtistInfo> = flow {
        ensureCacheLoaded()
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) {
            emit(ArtistInfo(name = artistName))
            return@flow
        }

        val cached = memoryCache[clean.lowercase()]
        val hasRefs = referenceTrackTitles.isNotEmpty() || referenceAlbumTitles.isNotEmpty()

        // Verifica se la cache necessita di aggiornamento:
        // 1. Se la biografia proviene da Wikipedia non verificata o troncata ("...")
        // 2. Se la biografia o la foto mancano
        // 3. Se l'entry è stata salvata prima del motore di disambiguazione anti-omonimia e abbiamo brani di riferimento
        val isBogusBio = cached?.biography != null &&
            !cached.isCustomBio &&
            (!isReliableMusicText(cached.biography) || isSongOrAlbumDescriptionText(cached.biography))
        val isOldWikipediaBio = cached?.source == "Wikipedia" && !cached.isCustomBio && cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION
        val isTruncatedBio = cached?.biography != null && !cached.isCustomBio && cached.biography.endsWith("...")
        val needsDisambiguationUpgrade = hasRefs &&
            cached != null &&
            cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION &&
            (!cached.isCustomBio || !cached.isCustomPhoto)
        val needsBioRefresh = cached == null || isBogusBio || isOldWikipediaBio || isTruncatedBio ||
            (!cached.isCustomBio && cached.biography.isNullOrBlank() && cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION)
        val needsPhotoRefresh = cached == null || (!cached.isCustomPhoto && cached.photoUrl == null)

        if (cached != null) {
            // Se la biografia era palesemente errata o non musicale, emetti subito la versione depurata
            if (isBogusBio) {
                emit(cached.copy(biography = null, source = null))
            } else {
                emit(cached)
            }

            if (!forceRefresh && !needsBioRefresh && !needsPhotoRefresh && !needsDisambiguationUpgrade) {
                artistUpdates.filter { it.equals(clean, ignoreCase = true) }.collect {
                    memoryCache[clean.lowercase()]?.let { emit(it) }
                }
                return@flow
            }
        }

        val fetched = fetchArtistInfoRemote(clean, referenceTrackTitles, referenceAlbumTitles)
        memoryCache[clean.lowercase()] = fetched
        saveCacheToDisk()
        emit(fetched)

        artistUpdates.filter { it.equals(clean, ignoreCase = true) }.collect {
            memoryCache[clean.lowercase()]?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Recupera le informazioni dell'artista in modo asincrono disambiguando eventuali omonimie
     * tramite i titoli dei brani e degli album locali.
     */
    suspend fun getArtistInfo(
        artistName: String,
        forceRefresh: Boolean = false,
        referenceTrackTitles: List<String> = emptyList(),
        referenceAlbumTitles: List<String> = emptyList()
    ): ArtistInfo = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
        val clean = cleanArtistName(artistName)
        if (clean.isBlank()) return@withContext ArtistInfo(name = artistName)

        val cached = memoryCache[clean.lowercase()]
        val hasRefs = referenceTrackTitles.isNotEmpty() || referenceAlbumTitles.isNotEmpty()
        val isBogusBio = cached?.biography != null &&
            !cached.isCustomBio &&
            (!isReliableMusicText(cached.biography) || isSongOrAlbumDescriptionText(cached.biography))
        val isOldWikipediaBio = cached?.source == "Wikipedia" && !cached.isCustomBio && cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION
        val isTruncatedBio = cached?.biography != null && !cached.isCustomBio && cached.biography.endsWith("...")
        val needsDisambiguationUpgrade = hasRefs &&
            cached != null &&
            cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION &&
            (!cached.isCustomBio || !cached.isCustomPhoto)
        val needsBioRefresh = cached == null || isBogusBio || isOldWikipediaBio || isTruncatedBio ||
            (!cached.isCustomBio && cached.biography.isNullOrBlank() && cached.disambiguationVersion < CURRENT_DISAMBIGUATION_VERSION)
        val needsPhotoRefresh = cached == null || (!cached.isCustomPhoto && cached.photoUrl == null)

        if (cached != null && !forceRefresh && !needsBioRefresh && !needsPhotoRefresh && !needsDisambiguationUpgrade) {
            return@withContext cached
        }

        val fetched = fetchArtistInfoRemote(clean, referenceTrackTitles, referenceAlbumTitles)
        memoryCache[clean.lowercase()] = fetched
        saveCacheToDisk()
        fetched
    }

    /**
     * Imposta un'immagine personalizzata per l'artista da un Uri (Galleria o file locale).
     * L'immagine viene copiata nello storage interno dell'app per garantire persistenza permanente.
     */
    suspend fun setCustomArtistPhoto(artistName: String, uri: Uri): ArtistInfo = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
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
        ensureCacheLoaded()
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
    suspend fun resetCustomArtistPhoto(
        artistName: String,
        referenceTrackTitles: List<String> = emptyList(),
        referenceAlbumTitles: List<String> = emptyList()
    ): ArtistInfo = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
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

        val refreshed = fetchArtistInfoRemote(clean, referenceTrackTitles, referenceAlbumTitles)
        memoryCache[clean.lowercase()] = refreshed
        saveCacheToDisk()
        artistUpdates.emit(clean.lowercase())
        refreshed
    }

    /**
     * Imposta o modifica manualmente il testo della biografia dell'artista.
     */
    suspend fun setCustomArtistBio(artistName: String, customBio: String?): ArtistInfo = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
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
    suspend fun resetCustomArtistBio(
        artistName: String,
        referenceTrackTitles: List<String> = emptyList(),
        referenceAlbumTitles: List<String> = emptyList()
    ): ArtistInfo = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
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

        val refreshed = fetchArtistInfoRemote(clean, referenceTrackTitles, referenceAlbumTitles)
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
        ensureCacheLoaded()
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

    private data class DeezerResolvedProfile(
        val photoUrl: String? = null,
        val catalogTracks: List<String> = emptyList(),
        val catalogAlbums: List<String> = emptyList()
    )

    private data class VerifiedBioCandidate(
        val biography: String,
        val source: String,
        val lang: String,
        val catalogMatches: Int,
        val topTrackOverlap: Int,
        val score: Int,
        val photoUrl: String? = null
    )

    private data class LastFmFetchResult(
        val candidate: VerifiedBioCandidate? = null,
        val rejectedAsHomonym: Boolean = false
    )

    /**
     * Risolve profilo ufficiale, foto HD e catalogo top su Deezer disambiguando eventuali omonimi
     * tramite i brani e gli album locali dell'artista.
     */
    private fun resolveDeezerArtistProfile(
        cleanName: String,
        localTrackRefs: List<String>,
        localAlbumRefs: List<String>
    ): DeezerResolvedProfile {
        var resolvedArtistId: Long? = null
        var photoUrl: String? = null

        // 1. Disambiguazione primaria tramite ricerca "track + artist" sui brani locali
        if (localTrackRefs.isNotEmpty()) {
            val candidateVotes = mutableMapOf<Long, Int>()
            val candidatePhotos = mutableMapOf<Long, String>()

            val queryTracks = localTrackRefs
                .sortedByDescending { it.length >= 4 }
                .take(3)

            for (trackTitle in queryTracks) {
                val safeTitle = trackTitle.replace("\"", "").trim()
                val safeArtist = cleanName.replace("\"", "").trim()
                if (safeTitle.isBlank()) continue

                val queries = listOf(
                    "track:\"$safeTitle\" artist:\"$safeArtist\"",
                    "$safeArtist $safeTitle"
                )
                for (q in queries) {
                    val encoded = URLEncoder.encode(q, "UTF-8")
                    val json = executeGet("https://api.deezer.com/search?q=$encoded&limit=10") ?: continue
                    val dataArr = json.optJSONArray("data") ?: continue
                    var foundOnQuery = false

                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val itemTitle = item.optString("title", "")
                        val itemTitleShort = item.optString("title_short", itemTitle)
                        val artistObj = item.optJSONObject("artist") ?: continue
                        val itemArtistId = artistObj.optLong("id", 0L)
                        val itemArtistName = artistObj.optString("name", "")
                        if (itemArtistId <= 0L || itemArtistName.isBlank()) continue

                        val artistCompatible = itemArtistName.equals(cleanName, ignoreCase = true) ||
                            QuerySanitizer.artistsMatch(cleanName, itemArtistName)
                        val titleCompatible = QuerySanitizer.titlesMatch(trackTitle, itemTitle) ||
                            QuerySanitizer.titlesMatch(trackTitle, itemTitleShort)

                        if (artistCompatible && titleCompatible) {
                            foundOnQuery = true
                            var weight = if (itemArtistName.equals(cleanName, ignoreCase = true)) 4 else 2
                            val albumTitle = item.optJSONObject("album")?.optString("title", "").orEmpty()
                            if (albumTitle.isNotBlank() && localAlbumRefs.any { QuerySanitizer.titlesMatch(it, albumTitle) }) {
                                weight += 3
                            }
                            candidateVotes[itemArtistId] = (candidateVotes[itemArtistId] ?: 0) + weight

                            val artPic = artistObj.optString("picture_xl").takeIf { it.isNotBlank() }
                                ?: artistObj.optString("picture_big").takeIf { it.isNotBlank() }
                                ?: artistObj.optString("picture_medium").takeIf { it.isNotBlank() }
                            if (artPic != null && !candidatePhotos.containsKey(itemArtistId)) {
                                candidatePhotos[itemArtistId] = artPic
                            }
                        }
                    }
                    if (foundOnQuery) break
                }

                val topScore = candidateVotes.values.maxOrNull() ?: 0
                if (topScore >= 6) break
            }

            resolvedArtistId = candidateVotes.maxByOrNull { it.value }?.key
            if (resolvedArtistId != null) {
                photoUrl = candidatePhotos[resolvedArtistId]
            }
        }

        // 2. Fallback: search/artist con verifica incrociata sui brani in caso di più omonimi
        if (resolvedArtistId == null) {
            try {
                val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
                val json = executeGet("https://api.deezer.com/search/artist?q=$encodedQuery&limit=8")
                val dataArray = json?.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val exactMatches = mutableListOf<JSONObject>()
                    val allMatches = mutableListOf<JSONObject>()
                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.optJSONObject(i) ?: continue
                        allMatches.add(obj)
                        if (obj.optString("name").equals(cleanName, ignoreCase = true)) {
                            exactMatches.add(obj)
                        }
                    }

                    val candidatesToInspect = exactMatches.ifEmpty { allMatches }
                    if (localTrackRefs.isNotEmpty() && candidatesToInspect.size > 1) {
                        var bestId: Long? = null
                        var bestHits = 0
                        var bestObj: JSONObject? = null

                        for (cand in candidatesToInspect.take(3)) {
                            val candId = cand.optLong("id", 0L)
                            if (candId <= 0L) continue
                            val topTracks = fetchDeezerArtistTopTracks(candId)
                            val hits = countTrackOverlap(localTrackRefs, topTracks)
                            if (hits > bestHits) {
                                bestHits = hits
                                bestId = candId
                                bestObj = cand
                            }
                        }

                        if (bestId != null && bestHits > 0) {
                            resolvedArtistId = bestId
                            photoUrl = extractDeezerPicture(bestObj)
                        } else if (exactMatches.size == 1) {
                            resolvedArtistId = exactMatches.first().optLong("id", 0L).takeIf { it > 0L }
                            photoUrl = extractDeezerPicture(exactMatches.first())
                        }
                        // Se ci sono più omonimi esatti ma nessuno corrisponde ai brani locali,
                        // non selezioniamo alla cieca la foto dell'omonimo sbagliato.
                    } else {
                        val target = exactMatches.firstOrNull() ?: allMatches.firstOrNull()
                        if (target != null) {
                            resolvedArtistId = target.optLong("id", 0L).takeIf { it > 0L }
                            photoUrl = extractDeezerPicture(target)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Errore recupero foto Deezer per '$cleanName': ${e.message}")
            }
        }

        // 3. Se abbiamo identificato l'ID artista su Deezer, recuperiamo foto HD e catalogo verificato
        var catalogTracks = emptyList<String>()
        var catalogAlbums = emptyList<String>()
        if (resolvedArtistId != null && resolvedArtistId > 0L) {
            if (photoUrl.isNullOrBlank()) {
                val artistJson = executeGet("https://api.deezer.com/artist/$resolvedArtistId")
                photoUrl = extractDeezerPicture(artistJson)
            }
            catalogTracks = fetchDeezerArtistTopTracks(resolvedArtistId)
            catalogAlbums = fetchDeezerArtistAlbums(resolvedArtistId)
        }

        return DeezerResolvedProfile(
            photoUrl = photoUrl,
            catalogTracks = catalogTracks,
            catalogAlbums = catalogAlbums
        )
    }

    private fun extractDeezerPicture(obj: JSONObject?): String? {
        if (obj == null) return null
        return obj.optString("picture_xl").takeIf { it.isNotBlank() }
            ?: obj.optString("picture_big").takeIf { it.isNotBlank() }
            ?: obj.optString("picture_medium").takeIf { it.isNotBlank() }
    }

    private fun fetchDeezerArtistTopTracks(artistId: Long): List<String> {
        val json = executeGet("https://api.deezer.com/artist/$artistId/top?limit=25") ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        val tracks = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val shortTitle = item.optString("title_short", "").trim()
            val fullTitle = item.optString("title", "").trim()
            val chosen = QuerySanitizer.stripFeaturingAndEdition(shortTitle.ifBlank { fullTitle })
                .ifBlank { shortTitle.ifBlank { fullTitle } }
            if (chosen.isNotBlank()) tracks.add(chosen)
        }
        return tracks.distinctBy { QuerySanitizer.simplifyForComparison(it) }
    }

    private fun fetchDeezerArtistAlbums(artistId: Long): List<String> {
        val json = executeGet("https://api.deezer.com/artist/$artistId/albums?limit=25") ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        val albums = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val rawTitle = item.optString("title", "").trim()
            val norm = QuerySanitizer.normalizeAlbumTitle(rawTitle).ifBlank { rawTitle }
            if (norm.isNotBlank() && !isGenericAlbum(norm)) {
                albums.add(norm)
            }
        }
        return albums.distinctBy { QuerySanitizer.simplifyForComparison(it) }
    }

    /**
     * Interroga Wikipedia (MediaWiki Search con brani/album + query batch + REST summary di riserva)
     * valutando tutti i candidati musicali affidabili tramite punteggio basato su brani e album dell'artista,
     * evitando conflitti di omonimia.
     */
    private fun fetchWikipediaSummary(
        title: String,
        lang: String = "it",
        trackRefs: List<String> = emptyList(),
        albumRefs: List<String> = emptyList()
    ): VerifiedBioCandidate? {
        val domain = if (lang.equals("it", ignoreCase = true)) "it.wikipedia.org" else "en.wikipedia.org"
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null

        val hasRefs = trackRefs.isNotEmpty() || albumRefs.isNotEmpty()
        val candidateTitles = linkedSetOf<String>()

        // 1. Titolo diretto e suffissi disambiguati musicali dell'artista (prioritari affinché non vengano
        // scartati da eventuali voci di singoli/album nella ricerca incrociata)
        candidateTitles.add(cleanTitle)
        val suffixes = if (lang.equals("it", ignoreCase = true)) {
            listOf("(cantante)", "(gruppo musicale)", "(rapper)", "(cantautore)", "(cantautrice)", "(musicista)", "(band)")
        } else {
            listOf("(musician)", "(singer)", "(band)", "(rapper)", "(musical group)", "(songwriter)")
        }
        suffixes.forEach { suffix -> candidateTitles.add("$cleanTitle $suffix") }

        // 2. Ricerca full-text incrociata "Artista + (Brani/Album)" per voci omonime o nomi anagrafici,
        // escludendo preventivamente pagine chiaramente intitolate a singoli/album/brani
        if (hasRefs) {
            val distinctTerms = (trackRefs + albumRefs)
                .map { it.replace("\"", "").trim() }
                .filter { it.length >= 3 }
                .distinct()
                .take(3)
            if (distinctTerms.isNotEmpty()) {
                val orClause = distinctTerms.joinToString(" OR ") { "\"$it\"" }
                val crossQuery = "\"${cleanTitle.replace("\"", "")}\" ($orClause)"
                searchWikipediaPageTitles(domain, crossQuery, limit = 5)
                    .filterNot { hasMusicWorkTitleSuffix(it) }
                    .forEach { candidateTitles.add(it) }
            }
        }

        // 3. Ricerca MediaWiki standard su titolo/contenuto (escludendo opere)
        searchWikipediaPageTitles(domain, "\"${cleanTitle.replace("\"", "")}\"", limit = 5)
            .filterNot { hasMusicWorkTitleSuffix(it) }
            .forEach { candidateTitles.add(it) }

        // Valutazione batch via MediaWiki Query API (1 singola chiamata HTTP per tutti i candidati principali)
        val evaluated = mutableListOf<VerifiedBioCandidate>()
        val batchTitles = candidateTitles.take(15)
        val batchPages = fetchWikipediaBatchPages(domain, batchTitles)

        if (batchPages.isNotEmpty()) {
            for (page in batchPages) {
                evaluateWikipediaPage(
                    pageJson = page.validationJson,
                    fullText = page.fullExtract,
                    displayBio = page.introBio,
                    photoUrl = page.photoUrl,
                    cleanTitle = cleanTitle,
                    lang = lang,
                    trackRefs = trackRefs,
                    albumRefs = albumRefs
                )?.let { evaluated.add(it) }
            }
        }

        // Fallback REST /page/summary/ + OpenSearch se l'endpoint batch non ha prodotto risultati
        if (evaluated.isEmpty()) {
            val restCandidates = linkedSetOf<String>().apply {
                add(cleanTitle)
                suffixes.forEach { add("$cleanTitle $it") }
                addAll(fetchOpenSearchTitles(domain, cleanTitle))
            }
            for (candTitle in restCandidates.take(8)) {
                val encoded = URLEncoder.encode(candTitle.replace(' ', '_'), "UTF-8")
                val resp = executeGet("https://$domain/api/rest_v1/page/summary/$encoded") ?: continue
                val extract = resp.optString("extract").trim()
                val originalImg = resp.optJSONObject("originalimage")?.optString("source")?.takeIf { it.isNotBlank() }
                val thumbImg = resp.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }
                val cand = evaluateWikipediaPage(
                    pageJson = resp,
                    fullText = extract,
                    displayBio = extract,
                    photoUrl = originalImg ?: thumbImg,
                    cleanTitle = cleanTitle,
                    lang = lang,
                    trackRefs = trackRefs,
                    albumRefs = albumRefs
                )
                if (cand != null) {
                    evaluated.add(cand)
                    if (cand.catalogMatches > 0 || !hasRefs) {
                        break
                    }
                }
            }
        }

        val best = evaluated.maxByOrNull { it.score } ?: return null
        // Se abbiamo brani/album di riferimento ma il candidato ha una biografia estesa con 0 match
        // (punteggio penalizzato < 0), lo scartiamo per evitare di mostrare un omonimo sbagliato.
        if (hasRefs && best.catalogMatches == 0 && best.score < 0) {
            return null
        }
        return best
    }

    private data class WikiBatchPage(
        val validationJson: JSONObject,
        val fullExtract: String,
        val introBio: String,
        val photoUrl: String?
    )

    private fun searchWikipediaPageTitles(domain: String, query: String, limit: Int = 5): List<String> {
        return try {
            val url = "https://$domain/w/api.php?action=query&list=search&srsearch=${URLEncoder.encode(query, "UTF-8")}&srlimit=$limit&format=json"
            val json = executeGet(url) ?: return emptyList()
            val searchArr = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val titles = mutableListOf<String>()
            for (i in 0 until searchArr.length()) {
                val item = searchArr.optJSONObject(i) ?: continue
                val t = item.optString("title", "").trim()
                if (t.isNotBlank()) titles.add(t)
            }
            titles
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchOpenSearchTitles(domain: String, query: String): List<String> {
        return try {
            val openSearchUrl = "https://$domain/w/api.php?action=opensearch&search=${URLEncoder.encode(query, "UTF-8")}&limit=6&namespace=0&format=json"
            val searchReq = Request.Builder()
                .url(openSearchUrl)
                .header("User-Agent", "MusiCal/0.2.5 (music player app; Android 16)")
                .build()
            client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                val jsonArray = JSONArray(body)
                if (jsonArray.length() < 2) return emptyList()
                val titlesArray = jsonArray.optJSONArray(1) ?: return emptyList()
                buildList {
                    for (i in 0 until titlesArray.length()) {
                        val t = titlesArray.optString(i).trim()
                        if (t.isNotBlank()) add(t)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchWikipediaBatchPages(domain: String, titles: List<String>): List<WikiBatchPage> {
        if (titles.isEmpty()) return emptyList()
        return try {
            val joined = titles.joinToString("|") { it.replace(' ', '_') }
            val url = "https://$domain/w/api.php?action=query&prop=extracts|pageimages|description|pageprops" +
                "&exchars=6000&explaintext=1&piprop=original|thumbnail&pithumbsize=1000&redirects=1" +
                "&titles=${URLEncoder.encode(joined, "UTF-8")}&format=json"
            val json = executeGet(url) ?: return emptyList()
            val pagesObj = json.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val result = mutableListOf<WikiBatchPage>()
            val keys = pagesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val p = pagesObj.optJSONObject(key) ?: continue
                if (p.has("missing")) continue

                val pageTitle = p.optString("title", "").trim()
                val fullExtract = p.optString("extract", "").trim()
                if (pageTitle.isBlank() || fullExtract.isBlank()) continue

                val pageProps = p.optJSONObject("pageprops")
                val isDisambiguation = pageProps?.has("disambiguation") == true
                val description = p.optString("description", "").trim()

                val introBio = extractWikipediaIntroText(fullExtract)
                val validationJson = JSONObject().apply {
                    put("title", pageTitle)
                    put("type", if (isDisambiguation) "disambiguation" else "standard")
                    put("description", description)
                    put("extract", introBio.ifBlank { fullExtract.take(1500) })
                }
                val originalImg = p.optJSONObject("original")?.optString("source")?.takeIf { it.isNotBlank() }
                val thumbImg = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }

                result.add(
                    WikiBatchPage(
                        validationJson = validationJson,
                        fullExtract = fullExtract,
                        introBio = introBio.ifBlank { fullExtract },
                        photoUrl = originalImg ?: thumbImg
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun evaluateWikipediaPage(
        pageJson: JSONObject,
        fullText: String,
        displayBio: String,
        photoUrl: String?,
        cleanTitle: String,
        lang: String,
        trackRefs: List<String>,
        albumRefs: List<String>
    ): VerifiedBioCandidate? {
        if (!isReliableMusicWikipediaPage(pageJson, cleanTitle, lang, fullArticleText = fullText)) {
            return null
        }

        val cleanBioText = displayBio.trim()
        if (cleanBioText.isBlank()) return null

        val rawTitle = pageJson.optString("title", "").trim()
        val baseTitle = rawTitle.replace(Regex("""\s*\(.*?\)\s*"""), "").trim()
        val cleanArtistNorm = cleanTitle.lowercase()
        val baseTitleNorm = baseTitle.lowercase()
        val exactTitleMatch = baseTitle.equals(cleanTitle, ignoreCase = true)
        val artistTitleCompatible = exactTitleMatch ||
            baseTitleNorm.replace("'", "").replace("-", " ") == cleanArtistNorm.replace("'", "").replace("-", " ") ||
            baseTitleNorm.startsWith("$cleanArtistNorm ") ||
            cleanArtistNorm.startsWith("$baseTitleNorm ")

        // Se il titolo della pagina coincide con un brano o album dell'artista (e non con l'artista stesso),
        // si tratta della voce di una canzone o di un disco, non della biografia del cantante.
        if (!artistTitleCompatible && (
                trackRefs.any { QuerySanitizer.titlesMatch(baseTitle, it) } ||
                albumRefs.any { QuerySanitizer.titlesMatch(baseTitle, it) }
            )
        ) {
            return null
        }

        val mentions = countCatalogMentionsInText(fullText, trackRefs, albumRefs)

        var score = (mentions.trackMatches * 60) + (mentions.albumMatches * 45) + (if (exactTitleMatch) 15 else 0)
        val hasRefs = trackRefs.isNotEmpty() || albumRefs.isNotEmpty()
        if (hasRefs && mentions.total == 0 && fullText.length >= 500) {
            score -= 100
        }

        return VerifiedBioCandidate(
            biography = limitBiographyLength(cleanBioText),
            source = "Wikipedia",
            lang = lang,
            catalogMatches = mentions.total,
            topTrackOverlap = 0,
            score = score,
            photoUrl = photoUrl
        )
    }

    /**
     * Interroga l'API di Last.fm per ottenere e verificare la biografia dell'artista
     * gestendo sia le biografie multi-artista per omonimia sia la verifica contro le top tracks.
     */
    private fun fetchVerifiedLastFmBio(
        artistName: String,
        lang: String,
        trackRefs: List<String>,
        albumRefs: List<String>
    ): LastFmFetchResult {
        if (LastFmConfig.API_KEY.isBlank()) return LastFmFetchResult()
        return try {
            val encodedLastFm = URLEncoder.encode(artistName, "UTF-8")
            val lastFmUrl = "${LastFmConfig.BASE_URL}?method=artist.getinfo&artist=$encodedLastFm&api_key=${LastFmConfig.API_KEY}&format=json&autocorrect=1&lang=$lang"
            val json = executeGet(lastFmUrl) ?: return LastFmFetchResult()

            val artistObj = json.optJSONObject("artist") ?: return LastFmFetchResult()
            val bioObj = artistObj.optJSONObject("bio")
            val rawContent = bioObj?.optString("content")?.trim().orEmpty()
            val rawSummary = bioObj?.optString("summary")?.trim().orEmpty()
            val rawBio = rawContent.ifBlank { rawSummary }
            if (rawBio.isBlank()) return LastFmFetchResult()

            val cleanedBio = cleanLastFmBio(rawBio)
            if (cleanedBio.isBlank()) return LastFmFetchResult()

            val hasRefs = trackRefs.isNotEmpty() || albumRefs.isNotEmpty()

            // Gestione biografie multi-artista su Last.fm (es. "There are multiple artists... 1) ... 2) ...")
            val multiResult = disambiguateMultiArtistBio(cleanedBio, trackRefs, albumRefs)
            when (multiResult.status) {
                MultiArtistBioStatus.AMBIGUOUS_NO_MATCH -> {
                    return LastFmFetchResult(candidate = null, rejectedAsHomonym = true)
                }
                MultiArtistBioStatus.SELECTED_SECTION -> {
                    val selectedText = multiResult.selectedBio ?: return LastFmFetchResult(rejectedAsHomonym = true)
                    if (isSongOrAlbumDescriptionText(selectedText)) {
                        return LastFmFetchResult(candidate = null, rejectedAsHomonym = true)
                    }
                    val mentions = countCatalogMentionsInText(selectedText, trackRefs, albumRefs)
                    return LastFmFetchResult(
                        candidate = VerifiedBioCandidate(
                            biography = limitBiographyLength(selectedText),
                            source = "Last.fm",
                            lang = lang,
                            catalogMatches = mentions.total,
                            topTrackOverlap = 0,
                            score = mentions.trackMatches * 60 + mentions.albumMatches * 45 + 25
                        )
                    )
                }
                MultiArtistBioStatus.SINGLE_ARTIST -> {
                    if (isSongOrAlbumDescriptionText(cleanedBio)) {
                        return LastFmFetchResult(candidate = null, rejectedAsHomonym = true)
                    }
                    val mentions = countCatalogMentionsInText(cleanedBio, trackRefs, albumRefs)
                    if (mentions.total > 0) {
                        return LastFmFetchResult(
                            candidate = VerifiedBioCandidate(
                                biography = limitBiographyLength(cleanedBio),
                                source = "Last.fm",
                                lang = lang,
                                catalogMatches = mentions.total,
                                topTrackOverlap = 0,
                                score = mentions.trackMatches * 60 + mentions.albumMatches * 45 + 20
                            )
                        )
                    }

                    // Se la bio non cita brani nel testo ma abbiamo brani di riferimento, verifichiamo le top tracks su Last.fm
                    var topTrackOverlap = 0
                    if (hasRefs && trackRefs.isNotEmpty()) {
                        val lastFmTopTracks = fetchLastFmTopTrackTitles(artistName)
                        if (lastFmTopTracks.isNotEmpty()) {
                            topTrackOverlap = countTrackOverlap(trackRefs, lastFmTopTracks)
                            if (topTrackOverlap == 0) {
                                // Il profilo Last.fm ha brani popolari noti che NON coincidono minimamente
                                // con i brani locali/Deezer: profilo omonimo errato.
                                return LastFmFetchResult(candidate = null, rejectedAsHomonym = true)
                            }
                        }
                    }

                    val baseScore = if (topTrackOverlap > 0) topTrackOverlap * 15 else 5
                    return LastFmFetchResult(
                        candidate = VerifiedBioCandidate(
                            biography = limitBiographyLength(cleanedBio),
                            source = "Last.fm",
                            lang = lang,
                            catalogMatches = 0,
                            topTrackOverlap = topTrackOverlap,
                            score = baseScore
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Errore recupero biografia Last.fm per '$artistName': ${e.message}")
            LastFmFetchResult()
        }
    }

    private fun fetchLastFmTopTrackTitles(artistName: String): List<String> {
        if (LastFmConfig.API_KEY.isBlank()) return emptyList()
        return try {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val url = "${LastFmConfig.BASE_URL}?method=artist.gettoptracks&artist=$encoded&api_key=${LastFmConfig.API_KEY}&format=json&autocorrect=1&limit=30"
            val json = executeGet(url) ?: return emptyList()
            val tracksArr = json.optJSONObject("toptracks")?.optJSONArray("track") ?: return emptyList()
            buildList {
                for (i in 0 until tracksArr.length()) {
                    val obj = tracksArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    val clean = QuerySanitizer.stripFeaturingAndEdition(name).ifBlank { name }
                    if (clean.isNotBlank()) add(clean)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Effettua l'interrogazione delle sorgenti remote disambiguando le omonimie tramite i brani e album locali:
     * 1. Deezer: identifica l'`artist.id` esatto dai brani locali e ne estrae foto HD + catalogo top.
     * 2. Last.fm e Wikipedia: valuta i candidati in base alle menzioni dei brani/album (locali + catalogo Deezer)
     *    e alle `topTracks`, preferendo sempre la biografia verificata che appartiene effettivamente all'artista.
     */
    private suspend fun fetchArtistInfoRemote(
        cleanName: String,
        referenceTrackTitles: List<String> = emptyList(),
        referenceAlbumTitles: List<String> = emptyList()
    ): ArtistInfo = withContext(Dispatchers.IO) {
        val existing = memoryCache[cleanName.lowercase()]
        val isCustomPhoto = existing?.isCustomPhoto == true
        val isCustomBio = existing?.isCustomBio == true
        var photoUrl: String? = existing?.photoUrl.takeIf { isCustomPhoto }
        var biography: String? = existing?.biography.takeIf { isCustomBio }
        var sourceLabel = if (isCustomBio) (existing?.source ?: "Personalizzata") else "Deezer / Last.fm"

        val localTrackRefs = prepareReferenceTracks(referenceTrackTitles, cleanName)
        val localAlbumRefs = prepareReferenceAlbums(referenceAlbumTitles)

        // 1. Disambiguazione su Deezer (foto HD + catalogo ufficiale verificato tramite i brani locali)
        var deezerProfile = DeezerResolvedProfile()
        if (!isCustomPhoto || !isCustomBio) {
            deezerProfile = resolveDeezerArtistProfile(cleanName, localTrackRefs, localAlbumRefs)
            if (!isCustomPhoto && photoUrl == null) {
                photoUrl = deezerProfile.photoUrl
            }
        }

        val allTrackRefs = (localTrackRefs + deezerProfile.catalogTracks)
            .distinctBy { QuerySanitizer.simplifyForComparison(it) }
        val allAlbumRefs = (localAlbumRefs + deezerProfile.catalogAlbums)
            .distinctBy { QuerySanitizer.simplifyForComparison(it) }
        val hasCatalogRefs = allTrackRefs.isNotEmpty() || allAlbumRefs.isNotEmpty()

        // 2. Recupero Biografia verificata anti-omonimia
        val preferredLang = getPreferredLanguageCode()
        if (!isCustomBio && biography == null) {
            val candidates = mutableListOf<VerifiedBioCandidate>()

            // A. Last.fm nella lingua preferita
            val lastFmPrimary = fetchVerifiedLastFmBio(cleanName, preferredLang, allTrackRefs, allAlbumRefs)
            lastFmPrimary.candidate?.let { candidates.add(it) }

            val hasStrongPrimaryLastFm = lastFmPrimary.candidate != null && lastFmPrimary.candidate.catalogMatches > 0

            // B. Se Last.fm primario non cita direttamente i brani/album o è stato scartato per omonimia,
            // interroghiamo Wikipedia (nella lingua preferita) e i fallback EN
            if (!hasStrongPrimaryLastFm) {
                if (preferredLang == "it") {
                    fetchWikipediaSummary(cleanName, "it", allTrackRefs, allAlbumRefs)?.let { candidates.add(it) }
                }

                val hasStrongWikiIt = candidates.any { it.catalogMatches > 0 }
                if (!hasStrongWikiIt && preferredLang != "en") {
                    val lastFmEn = fetchVerifiedLastFmBio(cleanName, "en", allTrackRefs, allAlbumRefs)
                    lastFmEn.candidate?.let { candidates.add(it) }
                }

                if (candidates.none { it.catalogMatches > 0 }) {
                    fetchWikipediaSummary(cleanName, "en", allTrackRefs, allAlbumRefs)?.let { candidates.add(it) }
                }
            }

            // Selezione del miglior candidato verificato:
            // 1) Chi cita esplicitamente i brani/album dell'artista (catalogMatches > 0)
            // 2) Altrimenti chi ha corrispondenza nelle topTracks di Last.fm (topTrackOverlap > 0)
            // 3) Altrimenti, solo se non abbiamo segnalazioni di omonimia, il miglior fallback compatibile
            val bestBio = candidates
                .sortedWith(
                    compareByDescending<VerifiedBioCandidate> { it.catalogMatches > 0 }
                        .thenByDescending { it.topTrackOverlap > 0 }
                        .thenByDescending { it.lang == preferredLang }
                        .thenByDescending { it.score }
                        .thenByDescending { it.source == "Last.fm" }
                )
                .firstOrNull()

            if (bestBio != null) {
                biography = bestBio.biography
                sourceLabel = bestBio.source
                if (!isCustomPhoto && photoUrl == null && !bestBio.photoUrl.isNullOrBlank()) {
                    photoUrl = bestBio.photoUrl
                }
            }
        } else if (!isCustomPhoto && photoUrl == null) {
            try {
                val wikiCand = fetchWikipediaSummary(cleanName, preferredLang, allTrackRefs, allAlbumRefs)
                if (wikiCand?.photoUrl != null) {
                    photoUrl = wikiCand.photoUrl
                }
            } catch (_: Exception) {}
        }

        val disambigVer = if (hasCatalogRefs) CURRENT_DISAMBIGUATION_VERSION else (existing?.disambiguationVersion ?: 0)
        ArtistInfo(
            name = cleanName,
            photoUrl = photoUrl,
            biography = biography,
            source = sourceLabel,
            isCustomPhoto = isCustomPhoto,
            isCustomBio = isCustomBio,
            lastUpdated = System.currentTimeMillis(),
            disambiguationVersion = disambigVer
        )
    }

    private fun executeGet(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusiCal/0.2.5 (music player app; Android 16)")
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
                                lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                                disambiguationVersion = obj.optInt("disambiguationVersion", 0)
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
                if (info.disambiguationVersion > 0) obj.put("disambiguationVersion", info.disambiguationVersion)
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
    lang: String = "it",
    fullArticleText: String = ""
): Boolean {
    val type = json.optString("type").trim()
    if (type.equals("disambiguation", ignoreCase = true) || type.equals("no-extract", ignoreCase = true)) {
        return false
    }

    val extract = json.optString("extract").trim()
    if (extract.isBlank()) return false
    val lowerExtract = extract.lowercase()
    val combinedLower = if (fullArticleText.isNotBlank()) fullArticleText.lowercase() else lowerExtract

    // Controllo frasi tipiche di disambiguazione
    if (lowerExtract.startsWith("può riferirsi a:") ||
        lowerExtract.contains("può indicare:") ||
        lowerExtract.startsWith("may refer to:") ||
        lowerExtract.contains("may also refer to:")
    ) {
        return false
    }

    val rawTitle = json.optString("title").trim()
    if (hasMusicWorkTitleSuffix(rawTitle)) {
        return false
    }

    val lowerTitle = rawTitle.lowercase()
    val description = json.optString("description").trim().lowercase()

    if (isSongOrAlbumDescriptionText(extract, description)) {
        return false
    }

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

    // 4. Indicatori di ruolo/biografia d'artista nell'estratto (evitando parole generiche come "canzone"/"album" che compaiono anche sulle schede dei brani)
    val musicKeywords = listOf(
        "cantante", "cantautore", "cantautrice", "rapper", "trapper", "gruppo musicale", "band",
        "musicista", "compositore", "compositrice", "disc jockey", "dj", "chitarrista", "batterista",
        "bassista", "pianista", "tastierista", "sassofonista", "produttore discografico", "beatmaker",
        "polistrumentista", "paroliere", "discografia", "discography", "festival di sanremo", "tournée",
        "singer", "songwriter", "musician", "musical group", "record producer", "vocalist", "guitarist",
        "drummer", "bassist", "pianist", "keyboardist"
    )

    val hasMusicKeywordsInExtract = musicKeywords.any { combinedLower.contains(it) }

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

    val extractMentionsArtist = combinedLower.contains(cleanArtist) ||
        (cleanArtist.contains(" ") && cleanArtist.split(" ").all { it.length > 2 && combinedLower.contains(it) })

    return titleMatches || extractMentionsArtist
}

private val WORK_TITLE_SUFFIX_REGEX = Regex(
    """\([^)]*\b(?:singolo|brano(?:\s+musicale)?|canzone|traccia|album|ep|mixtape|raccolta|compilation|colonna\s+sonora|soundtrack|tourn[eé]e|video\s+musicale|discografia|videografia|song|single|track|extended\s+play|music\s+video|discography|videography)\b[^)]*\)""",
    RegexOption.IGNORE_CASE
)

private val WORK_DESCRIPTION_REGEX = Regex(
    """\b(?:singolo(?:\s+discografico)?|brano\s+musicale|singolo\s+di|brano\s+di|canzone\s+di|album\s+(?:in\s+studio|dal\s+vivo|di\s+debutto|di|del)|ep\s+(?:di|del)|colonna\s+sonora|mixtape\s+(?:di|by)|single\s+by|song\s+by|song\s+of|track\s+by|studio\s+album|debut\s+album|live\s+album|compilation\s+album|soundtrack\s+album|extended\s+play|music\s+video|discography|discografia)\b|^(?:\d{4}\s+)?(?:album|single|song|brano|canzone|singolo|ep|mixtape)\b""",
    RegexOption.IGNORE_CASE
)

private val WORK_LEAD_COPULA_REGEX = Regex(
    """(?:^|[\s"'(\[«.,;:—–-])(?:[èÈéÉe]|f[uo]|era|erano|sono|furono|was|were|is|are)\s+(?:stato\s+|stata\s+|stati\s+|state\s+)?(?:un['’]?|una|uno|il|lo|la|i|gli|le|l['’]|a|an|the)(?:\s+(?:primo|secondo|terzo|quarto|quinto|sesto|nuovo|ultimo|celebre|famoso|noto|principale|debut|lead|first|second|third|fourth|fifth|promotional|promo|charity|collaborative|live|studio|compilation|remix|greatest|soundtrack|extended|self-titled|hit|popular))*\s+(?:singolo(?:\s+discografico)?|brano(?:\s+musicale)?|canzone|traccia|album(?:\s+in\s+studio|\s+dal\s+vivo|\s+di\s+debutto)?|ep|extended\s+play|mixtape|raccolta|compilation|colonna\s+sonora|cofanetto|single|song|track|ballad|album|mixtape|soundtrack|music\s+video|discography)(?=$|[\s.,;:!?)"'»])""",
    RegexOption.IGNORE_CASE
)

private val ARTIST_ROLE_COPULA_REGEX = Regex(
    """(?:^|[\s"'(\[«.,;:—–-])(?:[èÈéÉe]|f[uo]|era|erano|sono|furono|was|were|is|are)\s+(?:stato\s+|stata\s+|stati\s+|state\s+)?(?:un['’]?|una|dei|degli|delle|a|an|the)?(?:\s+(?!di\b|del\b|della\b|dello\b|dei\b|degli\b|delle\b|by\b|of\b|from\b|singol|bran|canzon|tracc|album|ep\b|mixtape|song|single|track|raccolta|compilation|colonna|soundtrack)[a-zà-ù'-]+){0,3}\s+(?:cantante|cantautore|cantautrice|rapper|trapper|musicista|gruppo(?:\s+musicale)?|band|duo|trio|compositore|compositrice|produttore|disc\s+jockey|dj|polistrumentista|artista|singer|songwriter|musician|musical\s+group|record\s+producer|composer|vocalist|guitarist|drummer|bassist|pianist)(?=$|[\s.,;:!?)"'»])""",
    RegexOption.IGNORE_CASE
)

/**
 * Rileva se il titolo di una pagina Wikipedia appartiene chiaramente a un'opera musicale
 * (singolo, brano, album, EP, raccolta, tour, discografia) anziché a un artista.
 */
fun hasMusicWorkTitleSuffix(rawTitle: String): Boolean {
    return rawTitle.isNotBlank() && WORK_TITLE_SUFFIX_REGEX.containsMatchIn(rawTitle)
}

/**
 * Rileva se la descrizione Wikidata o l'incipit di un testo descrivono un brano/singolo/album
 * invece della biografia di un cantante o gruppo musicale.
 */
fun isSongOrAlbumDescriptionText(text: String?, description: String = ""): Boolean {
    if (description.isNotBlank() && WORK_DESCRIPTION_REGEX.containsMatchIn(description)) {
        return true
    }
    if (text.isNullOrBlank()) return false
    val leadSlice = text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(420)
    if (leadSlice.isBlank()) return false

    val workMatch = WORK_LEAD_COPULA_REGEX.find(leadSlice) ?: return false
    val artistMatch = ARTIST_ROLE_COPULA_REGEX.find(leadSlice)
    // Se il soggetto viene definito prima come artista/band (es. "... è un cantante; il suo debutto è un album") è una bio valida;
    // se invece la frase definisce il soggetto stesso come singolo/canzone/album, va scartata.
    return artistMatch == null || workMatch.range.first < artistMatch.range.first
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

enum class MultiArtistBioStatus {
    SINGLE_ARTIST,
    SELECTED_SECTION,
    AMBIGUOUS_NO_MATCH
}

data class MultiArtistBioResult(
    val status: MultiArtistBioStatus,
    val selectedBio: String? = null
)

data class CatalogMatchStats(
    val trackMatches: Int,
    val albumMatches: Int
) {
    val total: Int get() = trackMatches + albumMatches
}

private val MULTI_ARTIST_HEADER_REGEX = Regex(
    """(?i)(there\s+(?:are|is)\s+(?:multiple|more\s+than\s+one)\s+artists?|more\s+than\s+one\s+artist\s+with\s+this\s+name|ci\s+sono\s+più\s+artisti|esistono\s+più\s+artisti|più\s+artisti\s+con\s+questo\s+nome)"""
)
private val NUMBERED_PAREN_BLOCK_REGEX = Regex("""(?:^|\n|\s)(?=\d{1,2}\)\s+)""")
private val NUMBERED_DOT_BLOCK_REGEX = Regex("""(?:^|\n)(?=\d{1,2}\.\s+)""")
private val DIACRITICS_CLEAN_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
private val MULTI_WS_REGEX = Regex("""\s{2,}""")

private fun normalizeTextForCatalogMatch(input: String): String {
    val lower = input.lowercase(Locale.ROOT)
    val deaccented = Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace(DIACRITICS_CLEAN_REGEX, "")
    return deaccented
        .replace(Regex("""[^a-z0-9\s]"""), " ")
        .replace(MULTI_WS_REGEX, " ")
        .trim()
}

fun prepareReferenceTracks(trackTitles: List<String>, artistName: String): List<String> {
    return trackTitles.mapNotNull { raw ->
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val split = QuerySanitizer.splitArtistAndTitle(trimmed, allowAsymmetric = true)
        val baseTitle = if (split != null && QuerySanitizer.artistsMatch(split.first, artistName)) {
            split.second
        } else {
            trimmed
        }
        val stripped = QuerySanitizer.stripFeaturingAndEdition(baseTitle).ifBlank { baseTitle }
        val norm = normalizeTextForCatalogMatch(stripped)
        if (norm.length < 2 || norm in setOf("sconosciuto", "unknown", "untitled")) {
            null
        } else {
            stripped.trim()
        }
    }.distinctBy { normalizeTextForCatalogMatch(it) }
}

fun prepareReferenceAlbums(albumTitles: List<String>): List<String> {
    return albumTitles.mapNotNull { raw ->
        val trimmed = raw.trim()
        if (trimmed.isBlank() || isGenericAlbum(trimmed) || trimmed.contains("(Brani singoli)")) {
            return@mapNotNull null
        }
        val normalized = QuerySanitizer.normalizeAlbumTitle(trimmed).ifBlank { trimmed }
        if (normalizeTextForCatalogMatch(normalized).length < 2) null else normalized
    }.distinctBy { normalizeTextForCatalogMatch(it) }
}

private val GENERIC_CATALOG_BIO_WORDS = setOf(
    "music", "musica", "album", "single", "singolo", "singoli", "band", "song", "songs",
    "canzone", "canzoni", "brano", "brani", "live", "tour", "rock", "pop", "rap", "trap",
    "jazz", "metal", "indie", "soul", "funk", "blues", "disco", "solo", "intro", "outro",
    "italia", "italy", "italian", "italiano", "italiana", "american", "british", "english"
)

fun containsCatalogTitle(normalizedText: String, rawTitle: String): Boolean {
    val normTitle = normalizeTextForCatalogMatch(rawTitle)
    if (normTitle.length < 3 || normTitle in GENERIC_CATALOG_BIO_WORDS || normalizedText.isBlank()) return false
    if (!normalizedText.contains(normTitle)) return false
    val pattern = Regex("""(?<![a-z0-9])${Regex.escape(normTitle)}(?![a-z0-9])""")
    return pattern.containsMatchIn(normalizedText)
}

fun countCatalogMentionsInText(
    text: String?,
    trackRefs: List<String>,
    albumRefs: List<String>
): CatalogMatchStats {
    if (text.isNullOrBlank()) return CatalogMatchStats(0, 0)
    val normText = normalizeTextForCatalogMatch(text)
    if (normText.isBlank()) return CatalogMatchStats(0, 0)

    val uniqueTracks = trackRefs.distinctBy { normalizeTextForCatalogMatch(it) }
    val uniqueAlbums = albumRefs.distinctBy { normalizeTextForCatalogMatch(it) }

    val trackMatches = uniqueTracks.count { containsCatalogTitle(normText, it) }
    val albumMatches = uniqueAlbums.count { containsCatalogTitle(normText, it) }
    return CatalogMatchStats(trackMatches = trackMatches, albumMatches = albumMatches)
}

fun countTrackOverlap(referenceTracks: List<String>, remoteTracks: List<String>): Int {
    if (referenceTracks.isEmpty() || remoteTracks.isEmpty()) return 0
    return referenceTracks.count { localTitle ->
        remoteTracks.any { remoteTitle -> QuerySanitizer.titlesMatch(localTitle, remoteTitle) }
    }
}

/**
 * Rileva se una biografia (tipicamente da Last.fm) raccoglie più artisti omonimi
 * (es. "There are multiple artists with this name: 1) ... 2) ...") ed estrae la sola sezione
 * che corrisponde ai brani e agli album dell'artista.
 */
fun disambiguateMultiArtistBio(
    bio: String,
    trackRefs: List<String>,
    albumRefs: List<String>
): MultiArtistBioResult {
    val trimmed = bio.trim()
    if (trimmed.isBlank()) return MultiArtistBioResult(MultiArtistBioStatus.SINGLE_ARTIST)

    val hasHeader = MULTI_ARTIST_HEADER_REGEX.containsMatchIn(trimmed)
    val parenSections = trimmed.split(NUMBERED_PAREN_BLOCK_REGEX)
        .map { it.trim() }
        .filter { Regex("""^\d{1,2}\)\s+""").containsMatchIn(it) && it.length >= 25 }

    val dotSections = if (parenSections.size < 2) {
        trimmed.split(NUMBERED_DOT_BLOCK_REGEX)
            .map { it.trim() }
            .filter { Regex("""^\d{1,2}\.\s+""").containsMatchIn(it) && it.length >= 25 }
    } else emptyList()

    val sections = if (parenSections.size >= 2) parenSections else dotSections
    if (sections.size < 2 && !hasHeader) {
        return MultiArtistBioResult(MultiArtistBioStatus.SINGLE_ARTIST)
    }
    if (sections.size < 2 && hasHeader) {
        return MultiArtistBioResult(MultiArtistBioStatus.AMBIGUOUS_NO_MATCH)
    }

    if (trackRefs.isEmpty() && albumRefs.isEmpty()) {
        return MultiArtistBioResult(MultiArtistBioStatus.AMBIGUOUS_NO_MATCH)
    }

    val scored = sections.map { section ->
        val cleanSection = section.replace(Regex("""^\d{1,2}[).]\s+"""), "").trim()
        val stats = countCatalogMentionsInText(cleanSection, trackRefs, albumRefs)
        Triple(cleanSection, stats, stats.trackMatches * 60 + stats.albumMatches * 45)
    }

    val bestScore = scored.maxOfOrNull { it.third } ?: 0
    if (bestScore <= 0) {
        return MultiArtistBioResult(MultiArtistBioStatus.AMBIGUOUS_NO_MATCH)
    }

    val winners = scored.filter { it.third == bestScore }
    if (winners.size > 1) {
        return MultiArtistBioResult(MultiArtistBioStatus.AMBIGUOUS_NO_MATCH)
    }

    return MultiArtistBioResult(
        status = MultiArtistBioStatus.SELECTED_SECTION,
        selectedBio = winners.first().first
    )
}

/**
 * Estrae la parte biografica iniziale/rilevante da un estratto `explaintext=1` di MediaWiki,
 * rimuovendo le sezioni finali di discografia/note/collegamenti esterni e le intestazioni `== ... ==`.
 */
fun extractWikipediaIntroText(fullExtract: String): String {
    val lines = fullExtract.lines()
    val stopHeadings = setOf(
        "discografia", "discography", "videografia", "videography", "filmografia", "filmography",
        "tour", "tournée", "note", "notes", "references", "bibliografia", "bibliography",
        "voci correlate", "see also", "altri progetti", "collegamenti esterni", "external links"
    )
    val kept = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("==")) {
            val headingTitle = trimmed.trim('=', ' ').lowercase(Locale.ROOT)
            if (headingTitle in stopHeadings) {
                break
            }
            continue
        }
        if (trimmed.isNotBlank() || kept.lastOrNull()?.isNotBlank() == true) {
            kept.add(trimmed)
        }
    }
    return kept.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
}
