package com.example.musicplayer.ui.tagger

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.LyricsData
import com.example.musicplayer.data.LyricsRepository
import com.example.musicplayer.data.isMixedOrGeneric
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.data.tagger.repository.MetadataEnricherRepository
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import com.example.musicplayer.data.tagger.writer.StorageWriteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinatore per l'arricchimento dei metadati (Smart Auto-Tagger) e la gestione dei testi (.lrc).
 * Coordina le query concorrenti a Deezer e LRCLIB, la generazione e scrittura sicura
 * dei file ID3/Vorbis in cache, la richiesta di autorizzazione MediaStore (Android 16 Scoped Storage)
 * e la commit fisica con notifica a MediaScanner.
 */
class MetadataEnricherCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val onReloadLibrary: suspend (affectedMetadata: Map<Long, EnrichedMetadata>) -> Unit
) {
    private val enricherRepository = MetadataEnricherRepository()
    private val storageWriteManager = StorageWriteManager(context)
    val lyricsRepository = LyricsRepository(context)

    private val _enricherState = MutableStateFlow<EnricherUiState>(EnricherUiState.Idle)
    val enricherState: StateFlow<EnricherUiState> = _enricherState.asStateFlow()

    private val _writeRequestIntentSender = MutableStateFlow<IntentSender?>(null)
    val writeRequestIntentSender: StateFlow<IntentSender?> = _writeRequestIntentSender.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LyricsData?>(null)
    val currentLyrics: StateFlow<LyricsData?> = _currentLyrics.asStateFlow()

    private val pendingWrites = ConcurrentHashMap<AudioTrack, Pair<File, EnrichedMetadata>>()
    private var activeSearchJob: Job? = null
    private var lyricsLoadJob: Job? = null

    private fun clearPendingWrites() {
        pendingWrites.values.forEach { (file, _) ->
            try {
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        pendingWrites.clear()
    }

    fun updateCurrentLyricsForTrack(track: AudioTrack?) {
        lyricsLoadJob?.cancel()
        if (track == null) {
            _currentLyrics.value = null
            return
        }
        lyricsLoadJob = scope.launch(Dispatchers.IO) {
            val loaded = lyricsRepository.getLyricsForTrack(track.id)
            _currentLyrics.value = loaded
        }
    }

    fun startTrackEnrichment(track: AudioTrack) {
        activeSearchJob?.cancel()
        _enricherState.value = EnricherUiState.Loading("Ricerca metadati Deezer e testi LRCLIB...")
        activeSearchJob = scope.launch {
            val result = enricherRepository.enrichTrackWithCandidates(track, EnricherOptions())
            result.onSuccess { (enriched, candidates) ->
                _enricherState.value = EnricherUiState.ReviewTrack(
                    track = track,
                    enriched = enriched,
                    candidates = candidates,
                    options = EnricherOptions()
                )
            }.onFailure { err ->
                _enricherState.value = EnricherUiState.Error(
                    err.message ?: "Errore durante la ricerca dei metadati online"
                )
            }
        }
    }

    fun selectAlternativeCandidate(
        track: AudioTrack,
        candidate: DeezerSearchTrack,
        options: EnricherOptions
    ) {
        activeSearchJob?.cancel()
        val currentCandidates = (_enricherState.value as? EnricherUiState.ReviewTrack)?.candidates.orEmpty()
        _enricherState.value = EnricherUiState.Loading("Caricamento dettagli da Deezer...")
        activeSearchJob = scope.launch {
            val res = enricherRepository.enrichFromCandidate(track, candidate, options)
            res.fold(
                onSuccess = { enriched ->
                    _enricherState.value = EnricherUiState.ReviewTrack(
                        track = track,
                        enriched = enriched,
                        candidates = currentCandidates,
                        options = options
                    )
                },
                onFailure = { error ->
                    _enricherState.value = EnricherUiState.Error(
                        error.message ?: "Impossibile recuperare i dettagli del brano selezionato."
                    )
                }
            )
        }
    }

    fun searchCustomTrackEnrichment(
        track: AudioTrack,
        customTitle: String,
        customArtist: String
    ) {
        activeSearchJob?.cancel()
        _enricherState.value = EnricherUiState.Loading("Ricerca personalizzata...")
        activeSearchJob = scope.launch {
            val candidatesRes = enricherRepository.searchCustom(customTitle, customArtist)
            candidatesRes.fold(
                onSuccess = { candidates ->
                    if (candidates.isEmpty()) {
                        _enricherState.value = EnricherUiState.Error("Nessun risultato trovato per '$customTitle - $customArtist'")
                    } else {
                        val best = candidates.first()
                        val enriched = enricherRepository.enrichFromCandidate(track, best, EnricherOptions()).getOrNull()
                        if (enriched != null) {
                            _enricherState.value = EnricherUiState.ReviewTrack(
                                track = track,
                                enriched = enriched,
                                candidates = candidates,
                                options = EnricherOptions()
                            )
                        } else {
                            _enricherState.value = EnricherUiState.Error("Impossibile elaborare il risultato Deezer.")
                        }
                    }
                },
                onFailure = { err ->
                    _enricherState.value = EnricherUiState.Error(err.message ?: "Errore durante la ricerca personalizzata.")
                }
            )
        }
    }

    fun adjustLyricsOffset(currentTrack: AudioTrack?, offsetDeltaMs: Long) {
        val track = currentTrack ?: return
        scope.launch(Dispatchers.IO) {
            val updated = lyricsRepository.adjustOffset(track.id, offsetDeltaMs)
            if (updated != null) {
                _currentLyrics.value = updated
            }
        }
    }

    fun startAlbumEnrichment(album: Album, separateAlbums: Boolean? = null) {
        activeSearchJob?.cancel()
        val shouldSeparate = separateAlbums ?: album.isMixedOrGeneric()
        _enricherState.value = EnricherUiState.Loading(
            if (shouldSeparate) "Analisi brani e separazione in album distinti..."
            else "Analisi album e unificazione tracce..."
        )
        activeSearchJob = scope.launch {
            val firstTrack = album.tracks.firstOrNull() ?: run {
                _enricherState.value = EnricherUiState.Error("L'album selezionato non contiene tracce.")
                return@launch
            }

            // 1. Arricchimento in parallelo di ciascuna traccia per identificare il disco originale
            val individualMap = mutableMapOf<Long, EnrichedMetadata>()
            coroutineScope {
                val deferreds = album.tracks.map { track ->
                    async {
                        val enriched = enricherRepository.enrichTrack(
                            track,
                            EnricherOptions(normalizeAlbum = false)
                        ).getOrNull()
                        track to enriched
                    }
                }
                deferreds.awaitAll().forEachIndexed { index, (track, res) ->
                    val inferredNum = QuerySanitizer.extractTrackNumberFromFileName(track.displayName)
                        ?: track.trackNumber.takeIf { it > 0 }
                        ?: (index + 1)
                    val fallback = EnrichedMetadata(
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        albumArtist = track.albumArtist.ifBlank { track.artist },
                        trackNumber = inferredNum,
                        discNumber = track.discNumber.coerceAtLeast(1)
                    )
                    individualMap[track.id] = res ?: fallback
                }
            }

            // 2. Mappa unificata (per raggruppare i brani sotto l'album ufficiale unico)
            val candidateAlbumGroups = individualMap.values
                .groupBy { it.album.trim().lowercase(java.util.Locale.ROOT) to it.albumArtist.trim().lowercase(java.util.Locale.ROOT) }
            val bestGroupKey = candidateAlbumGroups.maxByOrNull { it.value.size }?.key
            val unifiedAlbumRef = candidateAlbumGroups[bestGroupKey]?.firstOrNull()
                ?: individualMap[firstTrack.id]
                ?: individualMap.values.first()

            val albumSearchTitle = unifiedAlbumRef.album.ifBlank { album.title }
            val albumSearchArtist = unifiedAlbumRef.albumArtist.ifBlank { album.artist }
            val deezerAlbumId = enricherRepository.searchAlbumId(albumSearchTitle, albumSearchArtist).getOrNull()
            val officialAlbumDetail = if (deezerAlbumId != null && deezerAlbumId > 0L) {
                enricherRepository.getAlbumDetail(deezerAlbumId).getOrNull()
            } else null

            val matchedOfficialTracks = if (officialAlbumDetail != null && officialAlbumDetail.tracks.isNotEmpty()) {
                enricherRepository.alignAlbumTracks(album.tracks, officialAlbumDetail.tracks)
            } else {
                emptyMap()
            }

            val unifiedMap = mutableMapOf<Long, EnrichedMetadata>()
            album.tracks.forEachIndexed { index, track ->
                val ind = individualMap[track.id] ?: unifiedAlbumRef
                val officialTrackInfo = matchedOfficialTracks[track.id]

                val resolvedTrackNumber = officialTrackInfo?.trackPosition?.takeIf { it > 0 }
                    ?: ind.trackNumber?.takeIf { it > 0 }
                    ?: QuerySanitizer.extractTrackNumberFromFileName(track.displayName)
                    ?: track.trackNumber.takeIf { it > 0 }
                    ?: (index + 1)

                val resolvedDiscNumber = officialTrackInfo?.diskNumber?.takeIf { it > 0 }
                    ?: ind.discNumber?.takeIf { it > 0 }
                    ?: track.discNumber.takeIf { it > 0 }
                    ?: 1

                val finalCoverUrl = officialAlbumDetail?.coverXl ?: unifiedAlbumRef.coverUrl
                val finalCoverBytes = unifiedAlbumRef.coverBytes
                val finalReleaseYear = officialAlbumDetail?.releaseYear ?: unifiedAlbumRef.releaseYear
                val finalGenre = officialAlbumDetail?.genre?.takeIf { it.isNotBlank() } ?: unifiedAlbumRef.genre

                unifiedMap[track.id] = ind.copy(
                    album = officialAlbumDetail?.title?.takeIf { it.isNotBlank() } ?: unifiedAlbumRef.album,
                    albumArtist = officialAlbumDetail?.albumArtist?.takeIf { it.isNotBlank() } ?: unifiedAlbumRef.albumArtist,
                    coverUrl = finalCoverUrl,
                    coverBytes = finalCoverBytes,
                    releaseYear = finalReleaseYear,
                    genre = finalGenre,
                    trackNumber = resolvedTrackNumber,
                    discNumber = resolvedDiscNumber
                )
            }

            val activeMap = if (shouldSeparate) individualMap else unifiedMap

            _enricherState.value = EnricherUiState.ReviewAlbum(
                album = album,
                tracksEnriched = activeMap,
                individualTracksEnriched = individualMap,
                unifiedTracksEnriched = unifiedMap,
                options = EnricherOptions(normalizeAlbum = !shouldSeparate),
                isSeparationMode = shouldSeparate
            )
        }
    }

    fun toggleAlbumEnrichmentMode(separate: Boolean) {
        val current = _enricherState.value as? EnricherUiState.ReviewAlbum ?: return
        val newMap = if (separate) current.individualTracksEnriched else current.unifiedTracksEnriched
        _enricherState.value = current.copy(
            tracksEnriched = newMap,
            isSeparationMode = separate,
            options = current.options.copy(normalizeAlbum = !separate)
        )
    }

    fun applyTrackEnrichment(
        track: AudioTrack,
        metadata: EnrichedMetadata,
        options: EnricherOptions
    ) {
        _enricherState.value = EnricherUiState.Writing(1, 1, metadata.title)
        scope.launch {
            val preparedFile = storageWriteManager.prepareTrackInCache(track, metadata, options)
            if (preparedFile == null) {
                _enricherState.value = EnricherUiState.Error("Impossibile elaborare i frame audio.")
                return@launch
            }

            clearPendingWrites()
            pendingWrites[track] = Pair(preparedFile, metadata)

            val intentSender = storageWriteManager.createWriteRequestIntentSender(listOf(track.uri))
            if (intentSender != null) {
                _writeRequestIntentSender.value = intentSender
            } else {
                executePendingWrites()
            }
        }
    }

    fun applyAlbumEnrichment(
        album: Album,
        tracksEnriched: Map<Long, EnrichedMetadata>,
        options: EnricherOptions
    ) {
        _enricherState.value = EnricherUiState.Writing(1, album.tracks.size, "Elaborazione file...")
        scope.launch {
            clearPendingWrites()
            val urisToAuthorize = mutableListOf<Uri>()

            album.tracks.forEach { track ->
                val meta = tracksEnriched[track.id] ?: return@forEach
                val prepared = storageWriteManager.prepareTrackInCache(track, meta, options)
                if (prepared != null) {
                    pendingWrites[track] = Pair(prepared, meta)
                    urisToAuthorize.add(track.uri)
                }
            }

            if (pendingWrites.isEmpty()) {
                _enricherState.value = EnricherUiState.Error("Nessun file elaborato.")
                return@launch
            }

            val intentSender = storageWriteManager.createWriteRequestIntentSender(urisToAuthorize)
            if (intentSender != null) {
                _writeRequestIntentSender.value = intentSender
            } else {
                executePendingWrites()
            }
        }
    }

    fun onWritePermissionResult(isGranted: Boolean) {
        _writeRequestIntentSender.value = null
        if (isGranted) {
            scope.launch {
                executePendingWrites()
            }
        } else {
            clearPendingWrites()
            _enricherState.value = EnricherUiState.Error("Permesso di scrittura negato dall'utente.")
        }
    }

    private suspend fun executePendingWrites() = withContext(Dispatchers.IO) {
        val snapshot = pendingWrites.toMap()
        pendingWrites.clear()
        val total = snapshot.size
        var current = 0
        var successCount = 0
        val appliedMetadataMap = snapshot.map { (track, pair) -> track.id to pair.second }.toMap()

        snapshot.forEach { (track, pair) ->
            val (file, metadata) = pair
            current++
            _enricherState.value = EnricherUiState.Writing(current, total, metadata.title)
            val ok = storageWriteManager.commitTrackWrite(track, file, metadata)
            if (ok) successCount++
        }

        // Notifica il ViewModel per l'aggiornamento ottimistico in memoria e il ricaricamento asincrono della libreria
        onReloadLibrary(appliedMetadataMap)

        _enricherState.value = EnricherUiState.Success(
            "Aggiornati con successo $successCount di $total brani!"
        )
    }

    fun closeEnricher() {
        activeSearchJob?.cancel()
        activeSearchJob = null
        clearPendingWrites()
        _enricherState.value = EnricherUiState.Idle
        _writeRequestIntentSender.value = null
    }

    fun createDeleteRequestIntentSender(uris: List<Uri>): IntentSender? {
        return storageWriteManager.createDeleteRequestIntentSender(uris)
    }
}
