package com.example.musicplayer.data.tagger.client

import com.example.musicplayer.data.network.SharedHttpClient
import com.example.musicplayer.data.tagger.model.DeezerAlbumDetail
import com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.DeezerTrackDetail
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Client HTTP per le API pubbliche di Deezer (senza chiave API).
 * Permette di effettuare ricerche brani, scaricare dettagli dell'album e la copertina in formato HD (1000x1000).
 */
class DeezerApiClient(
    private val client: OkHttpClient = SharedHttpClient.instance
) {

    private val baseUrl = "https://api.deezer.com"
    private val userAgent = "MusiCal/${com.example.musicplayer.BuildConfig.VERSION_NAME} (Android; Mobile)"

    /**
     * Cerca una traccia su Deezer per titolo e artista.
     * Esegue prima una query avanzata strutturata, poi con l'artista principale (senza feat.) e infine un fallback su ricerca libera.
     */
    suspend fun searchTrack(title: String, artist: String): Result<List<DeezerSearchTrack>> =
        withContext(Dispatchers.IO) {
            try {
                val coreTitle = (QuerySanitizer.stripFeaturingAndEdition(title).ifBlank { title.trim() })
                    .replace("\"", "")
                    .trim()
                val cleanArtist = QuerySanitizer.cleanArtistString(artist).ifBlank { artist.trim() }
                    .replace("\"", "")
                    .trim()
                val primaryArtist = QuerySanitizer.extractPrimaryArtistForSearch(cleanArtist)
                    .replace("\"", "")
                    .trim()

                // Tentativo 1: query avanzata strutturata con artista completo
                val advancedQuery = buildString {
                    if (coreTitle.isNotBlank()) append("track:\"$coreTitle\" ")
                    if (cleanArtist.isNotBlank()) append("artist:\"$cleanArtist\"")
                }.trim()

                var results = if (advancedQuery.isNotBlank()) executeSearch(advancedQuery) else emptyList()

                // Tentativo 2: se l'artista conteneva feat./collaboratori, riprova con l'artista principale
                if (results.isEmpty() && primaryArtist.isNotBlank() && !primaryArtist.equals(cleanArtist, ignoreCase = true)) {
                    val primaryQuery = buildString {
                        if (coreTitle.isNotBlank()) append("track:\"$coreTitle\" ")
                        append("artist:\"$primaryArtist\"")
                    }.trim()
                    results = executeSearch(primaryQuery)
                }

                // Tentativo 2.5: se il nome dell'artista contiene una virgola interna (es. "Tyler, The Creator")
                // e il parser avanzato Deezer non restituisce match con la virgola tra virgolette, riprova senza virgola
                val commaFreeArtist = (primaryArtist.ifBlank { cleanArtist })
                    .replace(",", " ")
                    .replace(Regex("""\s{2,}"""), " ")
                    .trim()
                if (results.isEmpty() && (cleanArtist.contains(',') || primaryArtist.contains(',')) && commaFreeArtist.isNotBlank()) {
                    val commaFreeAdvanced = buildString {
                        if (coreTitle.isNotBlank()) append("track:\"$coreTitle\" ")
                        append("artist:\"$commaFreeArtist\"")
                    }.trim()
                    results = executeSearch(commaFreeAdvanced)
                }

                // Tentativo 3 (fallback): ricerca testuale libera se la query avanzata non ha trovato nulla
                if (results.isEmpty() && (coreTitle.isNotBlank() || cleanArtist.isNotBlank())) {
                    val searchArtist = commaFreeArtist.ifBlank { primaryArtist.ifBlank { cleanArtist } }
                    val broadQuery = "$searchArtist $coreTitle".trim()
                    results = executeSearch(broadQuery)
                }

                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Cerca un album su Deezer tramite titolo e artista e ne restituisce l'id Deezer
     * solo se il titolo e l'artista dell'album trovato corrispondono effettivamente a quelli cercati.
     */
    suspend fun searchAlbumId(title: String, artist: String): Result<Long?> = withContext(Dispatchers.IO) {
        try {
            val cleanAlbumTitle = QuerySanitizer.normalizeAlbumTitle(title).ifBlank { title.trim() }
            if (cleanAlbumTitle.isBlank()) return@withContext Result.success(null)

            val primaryArtist = QuerySanitizer.extractPrimaryArtistForSearch(artist)
                .replace(",", " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            val query = buildString {
                append(cleanAlbumTitle)
                if (primaryArtist.isNotBlank()) {
                    append(" ")
                    append(primaryArtist)
                }
            }.trim()

            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/search/album?q=$encoded&limit=10"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.success(null)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Result.success(null)

                val json = JSONObject(body)
                if (!json.has("data")) return@withContext Result.success(null)
                val data = json.getJSONArray("data")
                if (data.length() == 0) return@withContext Result.success(null)

                val isArtistGenericOrVa = QuerySanitizer.isArtistUnknownOrDirty(artist) ||
                    artist.trim().lowercase(java.util.Locale.ROOT) in setOf("artisti vari", "various artists", "various", "va")

                var bestFallbackValidatedId: Long? = null
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.getLong("id")
                    val itemTitle = item.optString("title", "")
                    val recType = item.optString("record_type", "")
                    val itemArtist = item.optJSONObject("artist")?.optString("name", "").orEmpty()

                    val titleMatches = QuerySanitizer.titlesMatch(
                        cleanAlbumTitle,
                        QuerySanitizer.normalizeAlbumTitle(itemTitle).ifBlank { itemTitle }
                    )
                    val itemArtistIsVa = itemArtist.trim().lowercase(java.util.Locale.ROOT) in setOf("artisti vari", "various artists", "various")
                    val artistMatches = isArtistGenericOrVa ||
                        itemArtistIsVa ||
                        QuerySanitizer.artistsMatch(artist, itemArtist)

                    if (titleMatches && artistMatches) {
                        if (recType.equals("album", ignoreCase = true)) {
                            return@withContext Result.success(id)
                        }
                        if (bestFallbackValidatedId == null) {
                            bestFallbackValidatedId = id
                        }
                    }
                }
                Result.success(bestFallbackValidatedId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeSearch(query: String): List<DeezerSearchTrack> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?q=$encodedQuery&limit=15"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Deezer HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()

            return parseSearchResponse(body)
        }
    }

    private fun parseSearchResponse(jsonStr: String): List<DeezerSearchTrack> {
        val json = JSONObject(jsonStr)
        if (json.has("error")) {
            val errMsg = json.optJSONObject("error")?.optString("message", "Deezer error") ?: "Deezer error"
            throw IOException(errMsg)
        }
        if (!json.has("data")) return emptyList()

        val dataArray = json.getJSONArray("data")
        val tracks = mutableListOf<DeezerSearchTrack>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val id = item.getLong("id")
            val title = item.optString("title", "")
            val titleShort = item.optString("title_short", title)
            val duration = item.optLong("duration", 0L)

            val artistObj = item.optJSONObject("artist")
            val artistName = artistObj?.optString("name", "").orEmpty()

            val albumObj = item.optJSONObject("album")
            val albumId = albumObj?.optLong("id", 0L) ?: 0L
            val albumTitle = albumObj?.optString("title", "").orEmpty()
            val coverXl = albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                ?: albumObj?.optString("cover_medium")?.takeIf { it.isNotBlank() }

            tracks.add(
                DeezerSearchTrack(
                    id = id,
                    title = title,
                    titleShort = titleShort,
                    duration = duration,
                    artistName = artistName,
                    albumId = albumId,
                    albumTitle = albumTitle,
                    coverXl = coverXl
                )
            )
        }

        return tracks
    }

    /**
     * Recupera le informazioni dettagliate dell'album da Deezer:
     * release_date, record_type, genere, tracce e l'artista dell'album (ALBUM_ARTIST).
     */
    suspend fun getAlbumDetail(albumId: Long): Result<DeezerAlbumDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/album/$albumId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Errore Deezer album API HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext Result.failure(IOException("Risposta vuota da Deezer album API"))
                }

                val json = JSONObject(body)
                if (json.has("error")) {
                    val err = json.getJSONObject("error").optString("message", "Errore Deezer")
                    return@withContext Result.failure(IOException(err))
                }

                val title = json.optString("title", "")
                val releaseDate = json.optString("release_date")?.takeIf { it.isNotBlank() }
                val recordType = json.optString("record_type", "album")

                val artistObj = json.optJSONObject("artist")
                val albumArtist = artistObj?.optString("name", "").orEmpty()

                val coverXl = json.optString("cover_xl")?.takeIf { it.isNotBlank() }
                    ?: json.optString("cover_big")?.takeIf { it.isNotBlank() }
                    ?: json.optString("cover_medium")?.takeIf { it.isNotBlank() }

                var genreName: String? = null
                val genresObj = json.optJSONObject("genres")
                if (genresObj != null && genresObj.has("data")) {
                    val genresArr = genresObj.getJSONArray("data")
                    if (genresArr.length() > 0) {
                        genreName = genresArr.getJSONObject(0).optString("name")?.takeIf { it.isNotBlank() }
                    }
                }

                val nbTracks = json.optInt("nb_tracks", 0)
                var trackList = mutableListOf<DeezerAlbumTrackInfo>()
                val tracksObj = json.optJSONObject("tracks")
                if (tracksObj != null && tracksObj.has("data")) {
                    val tracksArr = tracksObj.getJSONArray("data")
                    for (i in 0 until tracksArr.length()) {
                        val t = tracksArr.getJSONObject(i)
                        trackList.add(
                            DeezerAlbumTrackInfo(
                                id = t.getLong("id"),
                                title = t.optString("title", ""),
                                trackPosition = t.optInt("track_position", 0),
                                diskNumber = t.optInt("disk_number", 1)
                            )
                        )
                    }
                }

                // Se l'album contiene più di 25 tracce (limite standard dell'endpoint /album/{id}),
                // interroga l'endpoint dedicato /album/{id}/tracks?limit=300 per caricare l'intera tracklist
                if (nbTracks > trackList.size) {
                    val fullTracks = fetchAllAlbumTracks(albumId)
                    if (fullTracks.isNotEmpty()) {
                        trackList = fullTracks.toMutableList()
                    }
                }

                Result.success(
                    DeezerAlbumDetail(
                        id = albumId,
                        title = title,
                        albumArtist = albumArtist,
                        releaseDate = releaseDate,
                        recordType = recordType,
                        genre = genreName,
                        coverXl = coverXl,
                        tracks = trackList
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recupera l'elenco completo di tutte le tracce di un album con le rispettive posizioni (track_position e disk_number).
     */
    suspend fun getAlbumTracks(albumId: Long): Result<List<DeezerAlbumTrackInfo>> = withContext(Dispatchers.IO) {
        try {
            val tracks = fetchAllAlbumTracks(albumId)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchAllAlbumTracks(albumId: Long): List<DeezerAlbumTrackInfo> {
        return try {
            val url = "$baseUrl/album/$albumId/tracks?limit=300"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val json = JSONObject(body)
                if (!json.has("data")) return emptyList()

                val dataArr = json.getJSONArray("data")
                val list = mutableListOf<DeezerAlbumTrackInfo>()
                for (i in 0 until dataArr.length()) {
                    val t = dataArr.getJSONObject(i)
                    list.add(
                        DeezerAlbumTrackInfo(
                            id = t.getLong("id"),
                            title = t.optString("title", ""),
                            trackPosition = t.optInt("track_position", 0),
                            diskNumber = t.optInt("disk_number", 1)
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Recupera le informazioni dettagliate di una singola traccia tramite Deezer (/track/{id}),
     * inclusa la sua posizione autentica nel disco (track_position e disk_number).
     */
    suspend fun getTrackDetail(trackId: Long): Result<DeezerTrackDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/track/$trackId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Errore Deezer track API HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext Result.failure(IOException("Risposta vuota da Deezer track API"))
                }

                val json = JSONObject(body)
                if (json.has("error")) {
                    val err = json.getJSONObject("error").optString("message", "Errore Deezer track")
                    return@withContext Result.failure(IOException(err))
                }

                val id = json.getLong("id")
                val title = json.optString("title", "")
                val titleShort = json.optString("title_short", title)
                val trackPosition = json.optInt("track_position", 0)
                val diskNumber = json.optInt("disk_number", 1)
                val duration = json.optLong("duration", 0L)
                val releaseDate = json.optString("release_date")?.takeIf { it.isNotBlank() }

                val albumObj = json.optJSONObject("album")
                val albumId = albumObj?.optLong("id", 0L) ?: 0L
                val albumTitle = albumObj?.optString("title", "").orEmpty()
                val coverXl = albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                    ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                    ?: albumObj?.optString("cover_medium")?.takeIf { it.isNotBlank() }

                val artistObj = json.optJSONObject("artist")
                val artistName = artistObj?.optString("name", "").orEmpty()

                Result.success(
                    DeezerTrackDetail(
                        id = id,
                        title = title,
                        titleShort = titleShort,
                        trackPosition = trackPosition,
                        diskNumber = diskNumber,
                        duration = duration,
                        releaseDate = releaseDate,
                        albumId = albumId,
                        albumTitle = albumTitle,
                        artistName = artistName,
                        coverXl = coverXl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scarica l'immagine JPEG da un URL e restituisce l'array di byte da incorporare nei tag.
     */
    suspend fun downloadImage(imageUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Errore download copertina HTTP ${response.code}"))
                }
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    Result.success(bytes)
                } else {
                    Result.failure(IOException("Immagine scaricata vuota"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
