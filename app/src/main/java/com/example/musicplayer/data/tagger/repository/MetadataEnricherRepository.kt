package com.example.musicplayer.data.tagger.repository

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.tagger.client.DeezerApiClient
import com.example.musicplayer.data.tagger.client.LrclibApiClient
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class EnricherOptions(
    val normalizeAlbum: Boolean = true,
    val embedCover: Boolean = true,
    val includeLyrics: Boolean = true,
    val prioritizeStudioAlbum: Boolean = true
)

/**
 * Repository che coordina il recupero parallelo dei metadati da Deezer e LRCLIB
 * e applica le euristiche di selezione, ordinamento e normalizzazione.
 */
class MetadataEnricherRepository(
    private val deezerClient: DeezerApiClient = DeezerApiClient(),
    private val lrclibClient: LrclibApiClient = LrclibApiClient()
) {

    /**
     * Arricchisce i metadati di una traccia interrogando in parallelo Deezer e LRCLIB,
     * restituendo sia il miglior candidato arricchito sia l'elenco completo dei candidati alternativi.
     */
    suspend fun enrichTrackWithCandidates(
        track: AudioTrack,
        options: EnricherOptions = EnricherOptions()
    ): Result<Pair<EnrichedMetadata, List<DeezerSearchTrack>>> = withContext(Dispatchers.IO) {
        try {
            val (cleanArtist, cleanTitle) = QuerySanitizer.getEffectiveSearchTerms(track)

            // 1. Avvio parallelo delle richieste a Deezer e LRCLIB
            val deezerDeferred = async {
                deezerClient.searchTrack(cleanTitle, cleanArtist)
            }

            val lrclibDeferred = async {
                if (options.includeLyrics) {
                    val durationSec = if (track.duration > 0) track.duration / 1000 else null
                    lrclibClient.getLyrics(cleanTitle, cleanArtist, track.album, durationSec).getOrNull()
                } else {
                    null
                }
            }

            val deezerSearchRes = deezerDeferred.await().getOrNull().orEmpty()
            val lrclibRes = lrclibDeferred.await()

            if (deezerSearchRes.isEmpty()) {
                // Se Deezer non trova nulla, costruiamo un fallback parziale con LRCLIB o dati sanitizzati
                val extractedTrack = QuerySanitizer.extractTrackNumberFromFileName(track.displayName)
                val fallbackMetadata = EnrichedMetadata(
                    title = cleanTitle.ifBlank { track.title },
                    artist = cleanArtist.ifBlank { track.artist },
                    album = if (options.normalizeAlbum) QuerySanitizer.normalizeAlbumTitle(track.album) else track.album,
                    albumArtist = cleanArtist.ifBlank { track.artist },
                    releaseYear = null,
                    trackNumber = extractedTrack ?: track.trackNumber.takeIf { it > 0 },
                    discNumber = track.discNumber.takeIf { it > 0 } ?: 1,
                    genre = track.genre.takeIf { it.isNotBlank() },
                    coverUrl = null,
                    coverBytes = null,
                    plainLyrics = lrclibRes?.plainLyrics,
                    syncedLyrics = lrclibRes?.syncedLyrics,
                    isInstrumental = lrclibRes?.instrumental == true || track.isInstrumental(),
                    source = if (lrclibRes != null) "LRCLIB (Fallback)" else "Sanitizzazione locale"
                )
                return@withContext Result.success(Pair(fallbackMetadata, emptyList()))
            }

            // 2. Selezione del miglior candidato Deezer (durata coerente ed euristica)
            val bestCandidate = selectBestCandidate(track, deezerSearchRes)

            val enriched = buildEnrichedFromCandidate(
                track = track,
                candidate = bestCandidate,
                plainLyrics = lrclibRes?.plainLyrics,
                syncedLyrics = lrclibRes?.syncedLyrics,
                isInstrumental = lrclibRes?.instrumental == true || track.isInstrumental(),
                options = options
            )

            Result.success(Pair(enriched, deezerSearchRes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Arricchisce i metadati di una traccia interrogando in parallelo Deezer e LRCLIB.
     */
    suspend fun enrichTrack(
        track: AudioTrack,
        options: EnricherOptions = EnricherOptions()
    ): Result<EnrichedMetadata> = enrichTrackWithCandidates(track, options).map { it.first }

    /**
     * Arricchisce la traccia a partire da uno specifico candidato Deezer selezionato dall'utente.
     */
    suspend fun enrichFromCandidate(
        track: AudioTrack,
        candidate: DeezerSearchTrack,
        options: EnricherOptions = EnricherOptions()
    ): Result<EnrichedMetadata> = withContext(Dispatchers.IO) {
        try {
            val (cleanArtist, cleanTitle) = QuerySanitizer.getEffectiveSearchTerms(track)
            val durationSec = if (track.duration > 0) track.duration / 1000 else null
            val lrclibRes = if (options.includeLyrics) {
                lrclibClient.getLyrics(
                    candidate.titleShort.ifBlank { candidate.title },
                    candidate.artistName,
                    candidate.albumTitle,
                    durationSec
                ).getOrNull()
            } else null

            val enriched = buildEnrichedFromCandidate(
                track = track,
                candidate = candidate,
                plainLyrics = lrclibRes?.plainLyrics,
                syncedLyrics = lrclibRes?.syncedLyrics,
                isInstrumental = lrclibRes?.instrumental == true || track.isInstrumental(),
                options = options
            )
            Result.success(enriched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Esegue una ricerca manuale su Deezer con termini personalizzati dall'utente.
     */
    suspend fun searchCustom(
        title: String,
        artist: String
    ): Result<List<DeezerSearchTrack>> = withContext(Dispatchers.IO) {
        deezerClient.searchTrack(title.trim(), artist.trim())
    }

    /**
     * Recupera le informazioni dettagliate di un album da Deezer.
     */
    suspend fun getAlbumDetail(albumId: Long): Result<com.example.musicplayer.data.tagger.model.DeezerAlbumDetail> =
        deezerClient.getAlbumDetail(albumId)

    /**
     * Cerca l'ID Deezer di un album a partire da titolo e artista.
     */
    suspend fun searchAlbumId(title: String, artist: String): Result<Long?> =
        deezerClient.searchAlbumId(title, artist)

    /**
     * Allinea un insieme di tracce locali con la tracklist ufficiale di un album Deezer,
     * determinando la posizione esatta (trackPosition e diskNumber) per ciascun brano.
     */
    fun alignAlbumTracks(
        localTracks: List<AudioTrack>,
        officialTracks: List<com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo>
    ): Map<Long, com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo> {
        val matchedMap = mutableMapOf<Long, com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo>()
        val remainingOfficial = officialTracks.toMutableList()

        // Passata 1: corrispondenza per titolo esatto o sanitizzato
        for (local in localTracks) {
            val candidate = remainingOfficial.firstOrNull { off ->
                QuerySanitizer.titlesMatch(local.title, off.title) ||
                QuerySanitizer.titlesMatch(local.displayName, off.title)
            }
            if (candidate != null) {
                matchedMap[local.id] = candidate
                remainingOfficial.remove(candidate)
            }
        }

        // Passata 2: per le tracce rimaste, verifica se il numero traccia dedotto dal filename corrisponde alla posizione
        for (local in localTracks) {
            if (local.id in matchedMap) continue
            val inferredNum = QuerySanitizer.extractTrackNumberFromFileName(local.displayName)
                ?: local.trackNumber.takeIf { it > 0 }
            if (inferredNum != null) {
                val candidate = remainingOfficial.firstOrNull { it.trackPosition == inferredNum }
                if (candidate != null) {
                    matchedMap[local.id] = candidate
                    remainingOfficial.remove(candidate)
                }
            }
        }

        return matchedMap
    }

    private suspend fun buildEnrichedFromCandidate(
        track: AudioTrack,
        candidate: DeezerSearchTrack,
        plainLyrics: String?,
        syncedLyrics: String?,
        isInstrumental: Boolean = false,
        options: EnricherOptions
    ): EnrichedMetadata {
        val albumDetailRes = deezerClient.getAlbumDetail(candidate.albumId).getOrNull()

        val albumArtist = albumDetailRes?.albumArtist?.takeIf { it.isNotBlank() }
            ?: candidate.artistName

        val rawAlbumTitle = albumDetailRes?.title?.takeIf { it.isNotBlank() }
            ?: candidate.albumTitle

        val finalAlbumTitle = if (options.normalizeAlbum) {
            QuerySanitizer.normalizeAlbumTitle(rawAlbumTitle)
        } else {
            rawAlbumTitle
        }

        // 1. Cerca la traccia nella lista delle tracce dell'album
        val matchingAlbumTrack = albumDetailRes?.tracks?.firstOrNull { t ->
            t.id == candidate.id ||
            QuerySanitizer.titlesMatch(t.title, candidate.title) ||
            QuerySanitizer.titlesMatch(t.title, candidate.titleShort)
        }

        var trackNumber = matchingAlbumTrack?.trackPosition?.takeIf { it > 0 }
        var discNumber = matchingAlbumTrack?.diskNumber?.takeIf { it > 0 }

        // 2. Se non ancora trovato o non valido, interroga l'endpoint /track/{id} di Deezer che fornisce autoritativamente posizione e disco
        if (trackNumber == null || discNumber == null) {
            val trackDetail = deezerClient.getTrackDetail(candidate.id).getOrNull()
            if (trackDetail != null) {
                if (trackNumber == null && trackDetail.trackPosition > 0) {
                    trackNumber = trackDetail.trackPosition
                }
                if (discNumber == null && trackDetail.diskNumber > 0) {
                    discNumber = trackDetail.diskNumber
                }
            }
        }

        // 3. Se ancora non trovato, prova a ricavarlo dal nome del file locale
        if (trackNumber == null || trackNumber <= 0) {
            trackNumber = QuerySanitizer.extractTrackNumberFromFileName(track.displayName)
        }

        // 4. Fallback sui metadati preesistenti del file audio
        if (trackNumber == null || trackNumber <= 0) {
            trackNumber = track.trackNumber.takeIf { it > 0 }
        }
        if (discNumber == null || discNumber <= 0) {
            discNumber = track.discNumber.takeIf { it > 0 } ?: 1
        }

        val coverUrl = albumDetailRes?.coverXl ?: candidate.coverXl
        val coverBytes = if (options.embedCover && !coverUrl.isNullOrBlank()) {
            deezerClient.downloadImage(coverUrl).getOrNull()
        } else {
            null
        }

        return EnrichedMetadata(
            title = candidate.titleShort.ifBlank { candidate.title },
            artist = candidate.artistName,
            album = finalAlbumTitle,
            albumArtist = albumArtist,
            releaseYear = albumDetailRes?.releaseYear,
            trackNumber = trackNumber,
            discNumber = discNumber,
            genre = albumDetailRes?.genre?.takeIf { it.isNotBlank() } ?: track.genre.takeIf { it.isNotBlank() },
            coverUrl = coverUrl,
            coverBytes = coverBytes,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics,
            isInstrumental = isInstrumental,
            recordType = albumDetailRes?.recordType,
            source = "Deezer + LRCLIB"
        )
    }

    /**
     * Seleziona il miglior candidato tra i risultati di ricerca Deezer:
     * - Filtra per corrispondenza di durata (tolleranza 3s, poi 6s)
     * - Seleziona il brano più vicino temporalmente e con migliore corrispondenza
     */
    private fun selectBestCandidate(
        track: AudioTrack,
        candidates: List<DeezerSearchTrack>
    ): DeezerSearchTrack {
        if (candidates.size == 1) return candidates.first()

        val actualSec = if (track.duration > 0) track.duration / 1000 else null

        if (actualSec != null) {
            // Tentativo 1: tolleranza stretta (±3 secondi)
            val strictDurationMatches = candidates.filter {
                abs(it.duration - actualSec) <= 3
            }
            if (strictDurationMatches.isNotEmpty()) {
                return strictDurationMatches.minByOrNull { abs(it.duration - actualSec) } ?: strictDurationMatches.first()
            }

            // Tentativo 2: tolleranza più permissiva (±6 secondi)
            val relaxedMatches = candidates.filter {
                abs(it.duration - actualSec) <= 6
            }
            if (relaxedMatches.isNotEmpty()) {
                return relaxedMatches.minByOrNull { abs(it.duration - actualSec) } ?: relaxedMatches.first()
            }
        }

        // Se non abbiamo riscontri sulla durata, restituiamo il primo risultato Deezer (più rilevante per l'algoritmo)
        return candidates.first()
    }
}
