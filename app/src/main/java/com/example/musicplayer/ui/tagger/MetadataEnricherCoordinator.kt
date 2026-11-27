package com.example.musicplayer.ui.tagger

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.LyricsData
import com.example.musicplayer.data.LyricsRepository
import com.example.musicplayer.data.UNRELEASED_ALBUM_TITLE
import com.example.musicplayer.data.UnreleasedTrackStore
import com.example.musicplayer.data.extractMainArtist
import com.example.musicplayer.data.isGenericAlbum
import com.example.musicplayer.data.isMixedOrGeneric
import com.example.musicplayer.data.isTrackUnreleased
import com.example.musicplayer.data.isUnreleasedAlbumTitle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed class UnreleasedScanState {
    object Idle : UnreleasedScanState()
    data class Scanning(
        val current: Int,
        val total: Int,
        val currentTrackTitle: String
    ) : UnreleasedScanState()
    data class Completed(
        val scannedCount: Int,
        val newlyGroupedUnreleasedCount: Int,
        val affectedArtistsCount: Int,
        val officialMatches: Map<AudioTrack, EnrichedMetadata> = emptyMap()
    ) : UnreleasedScanState()
}

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
    private val appContext: Context = context.applicationContext ?: context
    private val enricherRepository = MetadataEnricherRepository()
    private val storageWriteManager = StorageWriteManager(appContext)
    val lyricsRepository = LyricsRepository(appContext)

    private val _enricherState = MutableStateFlow<EnricherUiState>(EnricherUiState.Idle)
    val enricherState: StateFlow<EnricherUiState> = _enricherState.asStateFlow()

    private val _unreleasedScanState = MutableStateFlow<UnreleasedScanState>(UnreleasedScanState.Idle)
    val unreleasedScanState: StateFlow<UnreleasedScanState> = _unreleasedScanState.asStateFlow()

    private val _writeRequestIntentSender = MutableStateFlow<IntentSender?>(null)
    val writeRequestIntentSender: StateFlow<IntentSender?> = _writeRequestIntentSender.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LyricsData?>(null)
    val currentLyrics: StateFlow<LyricsData?> = _currentLyrics.asStateFlow()

    private val pendingWrites = ConcurrentHashMap<AudioTrack, Pair<File, EnrichedMetadata>>()
    private var activeSearchJob: Job? = null
    private var unreleasedScanJob: Job? = null
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
        val currentReviewTrack = _enricherState.value as? EnricherUiState.ReviewTrack
        val currentCandidates = currentReviewTrack?.candidates.orEmpty()
        val parentAlbum = currentReviewTrack?.parentAlbumState
        _enricherState.value = EnricherUiState.Loading(
            message = "Caricamento dettagli da Deezer...",
            parentAlbumState = parentAlbum
        )
        activeSearchJob = scope.launch {
            val res = enricherRepository.enrichFromCandidate(track, candidate, options)
            res.fold(
                onSuccess = { enriched ->
                    _enricherState.value = EnricherUiState.ReviewTrack(
                        track = track,
                        enriched = enriched,
                        candidates = currentCandidates,
                        options = options,
                        parentAlbumState = parentAlbum
                    )
                },
                onFailure = { error ->
                    _enricherState.value = EnricherUiState.Error(
                        message = error.message ?: "Impossibile recuperare i dettagli del brano selezionato.",
                        parentAlbumState = parentAlbum
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
        val currentReviewTrack = _enricherState.value as? EnricherUiState.ReviewTrack
        val parentAlbum = currentReviewTrack?.parentAlbumState
        val currentOptions = currentReviewTrack?.options ?: EnricherOptions()
        _enricherState.value = EnricherUiState.Loading(
            message = "Ricerca personalizzata...",
            parentAlbumState = parentAlbum
        )
        activeSearchJob = scope.launch {
            val candidatesRes = enricherRepository.searchCustom(customTitle, customArtist)
            candidatesRes.fold(
                onSuccess = { candidates ->
                    if (candidates.isEmpty()) {
                        _enricherState.value = EnricherUiState.Error(
                            message = "Nessun risultato trovato per '$customTitle - $customArtist'",
                            parentAlbumState = parentAlbum
                        )
                    } else {
                        val best = candidates.first()
                        val enriched = enricherRepository.enrichFromCandidate(track, best, currentOptions).getOrNull()
                        if (enriched != null) {
                            _enricherState.value = EnricherUiState.ReviewTrack(
                                track = track,
                                enriched = enriched,
                                candidates = candidates,
                                options = currentOptions,
                                parentAlbumState = parentAlbum
                            )
                        } else {
                            _enricherState.value = EnricherUiState.Error(
                                message = "Impossibile elaborare il risultato Deezer.",
                                parentAlbumState = parentAlbum
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _enricherState.value = EnricherUiState.Error(
                        message = err.message ?: "Errore durante la ricerca personalizzata.",
                        parentAlbumState = parentAlbum
                    )
                }
            )
        }
    }

    /**
     * Apre la sottofinestra "Auto-tagga e testi" per un singolo brano a partire dalla finestra
     * "Uniforma / Separa Album", preservando lo stato completo dell'album padre.
     */
    fun openTrackEditorFromAlbum(
        track: AudioTrack,
        albumSnapshot: EnricherUiState.ReviewAlbum
    ) {
        activeSearchJob?.cancel()
        val currentMeta = albumSnapshot.tracksEnriched[track.id] ?: EnrichedMetadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = QuerySanitizer.extractPrimaryAlbumArtist(track.albumArtist.ifBlank { track.artist })
                .ifBlank { track.albumArtist.ifBlank { track.artist } },
            trackNumber = track.trackNumber.takeIf { it > 0 },
            discNumber = track.discNumber.coerceAtLeast(1),
            genre = track.genre.takeIf { it.isNotBlank() }
        )
        val trackOptions = albumSnapshot.trackCustomOptions[track.id] ?: albumSnapshot.options

        _enricherState.value = EnricherUiState.ReviewTrack(
            track = track,
            enriched = currentMeta,
            candidates = emptyList(),
            options = trackOptions,
            parentAlbumState = albumSnapshot
        )

        // Carica in background i candidati alternativi da Deezer senza bloccare la sottofinestra
        activeSearchJob = scope.launch {
            val (searchArtist, searchTitle) = QuerySanitizer.getEffectiveSearchTerms(
                track.copy(title = currentMeta.title, artist = currentMeta.artist)
            )
            val candidates = enricherRepository.searchCustom(searchTitle, searchArtist)
                .getOrNull()
                .orEmpty()
            val stateNow = _enricherState.value
            if (stateNow is EnricherUiState.ReviewTrack &&
                stateNow.track.id == track.id &&
                stateNow.parentAlbumState != null
            ) {
                _enricherState.value = stateNow.copy(candidates = candidates)
            }
        }
    }

    /**
     * Ritorna alla finestra Album padre annullando le modifiche non confermate nella sottofinestra del singolo brano.
     */
    fun returnToAlbumFromTrackEditor(): Boolean {
        val parentAlbum = when (val s = _enricherState.value) {
            is EnricherUiState.ReviewTrack -> s.parentAlbumState
            is EnricherUiState.Loading -> s.parentAlbumState
            is EnricherUiState.Error -> s.parentAlbumState
            else -> null
        } ?: return false

        activeSearchJob?.cancel()
        activeSearchJob = null
        _enricherState.value = parentAlbum
        return true
    }

    /**
     * Conferma in-memory le modifiche effettuate sul singolo brano nella sottofinestra e ritorna
     * alla finestra "Uniforma / Separa Album" aggiornando la lista dei brani.
     * Se in modalità "Uniforma Album" l'utente ha cambiato manualmente l'album di appartenenza del brano,
     * il brano viene rimosso dalla lista dei brani dell'album corrente e accodato in [EnricherUiState.ReviewAlbum.movedOutTracks]
     * per essere salvato col nuovo album al momento della conferma finale.
     */
    fun confirmTrackEditInAlbum(
        track: AudioTrack,
        editedMetadata: EnrichedMetadata,
        trackOptions: EnricherOptions
    ) {
        val current = _enricherState.value as? EnricherUiState.ReviewTrack ?: return
        val parent = current.parentAlbumState ?: return
        activeSearchJob?.cancel()
        activeSearchJob = null

        val targetUnifiedAlbum = (
            parent.editedAlbumTitle
                ?: parent.unifiedTracksEnriched.values.firstOrNull()?.album
                ?: parent.album.title
            ).trim()

        val isMovedToDifferentAlbum = !parent.isSeparationMode &&
            editedMetadata.album.trim().isNotBlank() &&
            !editedMetadata.album.trim().equals(targetUnifiedAlbum, ignoreCase = true)

        if (isMovedToDifferentAlbum) {
            val remainingTracks = parent.album.tracks.filter { it.id != track.id }
            val updatedMovedOut = parent.movedOutTracks + (track.id to (track to editedMetadata))
            val updatedCustomOpts = parent.trackCustomOptions + (track.id to trackOptions)
            val updatedTracksEnriched = parent.tracksEnriched - track.id
            val updatedUnified = parent.unifiedTracksEnriched - track.id
            val updatedIndividual = parent.individualTracksEnriched + (track.id to editedMetadata)

            val updatedAlbumState = parent.copy(
                album = parent.album.copy(tracks = remainingTracks),
                tracksEnriched = updatedTracksEnriched,
                unifiedTracksEnriched = updatedUnified,
                individualTracksEnriched = updatedIndividual,
                manuallyEditedTrackIds = parent.manuallyEditedTrackIds + track.id,
                movedOutTracks = updatedMovedOut,
                trackCustomOptions = updatedCustomOpts
            )

            if (remainingTracks.isEmpty()) {
                applyAlbumEnrichment(
                    album = updatedAlbumState.album,
                    tracksEnriched = updatedTracksEnriched,
                    options = updatedAlbumState.options,
                    movedOutTracks = updatedMovedOut,
                    trackCustomOptions = updatedCustomOpts
                )
            } else {
                _enricherState.value = updatedAlbumState
            }
        } else {
            val updatedTracksEnriched = parent.tracksEnriched + (track.id to editedMetadata)
            val updatedUnified = if (!parent.isSeparationMode) {
                parent.unifiedTracksEnriched + (track.id to editedMetadata)
            } else {
                parent.unifiedTracksEnriched
            }
            val updatedIndividual = parent.individualTracksEnriched + (track.id to editedMetadata)
            val updatedCustomOpts = parent.trackCustomOptions + (track.id to trackOptions)

            _enricherState.value = parent.copy(
                tracksEnriched = updatedTracksEnriched,
                unifiedTracksEnriched = updatedUnified,
                individualTracksEnriched = updatedIndividual,
                manuallyEditedTrackIds = parent.manuallyEditedTrackIds + track.id,
                trackCustomOptions = updatedCustomOpts
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
        val shouldSeparate = separateAlbums ?: (album.isMixedOrGeneric() || isUnreleasedAlbumTitle(album.title))
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
                        albumArtist = QuerySanitizer.extractPrimaryAlbumArtist(track.albumArtist.ifBlank { track.artist })
                .ifBlank { track.albumArtist.ifBlank { track.artist } },
                        trackNumber = inferredNum,
                        discNumber = track.discNumber.coerceAtLeast(1)
                    )
                    individualMap[track.id] = res ?: fallback
                }
            }

            // 1.5 Se alcune tracce in individualMap condividono coverUrl ma una di esse ha fallito il download di coverBytes,
            // riutilizza i byte già scaricati dalle tracce sorelle.
            val downloadedByUrl = individualMap.values
                .mapNotNull { meta ->
                    val url = meta.coverUrl?.takeIf { it.isNotBlank() }
                    val bytes = meta.coverBytes?.takeIf { it.isNotEmpty() }
                    if (url != null && bytes != null) url to bytes else null
                }
                .toMap()
            individualMap.entries.toList().forEach { (trackId, meta) ->
                if ((meta.coverBytes == null || meta.coverBytes.isEmpty()) && !meta.coverUrl.isNullOrBlank()) {
                    val sharedBytes = downloadedByUrl[meta.coverUrl]
                    if (sharedBytes != null) {
                        individualMap[trackId] = meta.copy(coverBytes = sharedBytes)
                    }
                }
            }

            // 2. Mappa unificata (per raggruppare i brani sotto l'album ufficiale unico senza corrompere tracce già valide)
            val hasKnownLocalAlbum = !isGenericAlbum(album.title) &&
                !album.title.contains("(Brani singoli)") &&
                !isUnreleasedAlbumTitle(album.title)
            val nonGenericCandidates = individualMap.values.filter {
                !it.isUnreleased &&
                    !isGenericAlbum(it.album) &&
                    !it.album.contains("(Brani singoli)") &&
                    !isUnreleasedAlbumTitle(it.album)
            }
            val studioCandidates = nonGenericCandidates.filter {
                !it.recordType.equals("single", ignoreCase = true)
            }.ifEmpty { nonGenericCandidates }

            val candidateAlbumGroups = studioCandidates
                .groupBy {
                    QuerySanitizer.normalizeAlbumTitle(it.album).trim().lowercase(java.util.Locale.ROOT) to
                        QuerySanitizer.extractPrimaryArtistForSearch(it.albumArtist).lowercase(java.util.Locale.ROOT)
                }
            val bestGroupKey = candidateAlbumGroups.maxByOrNull { it.value.size }?.key
            val bestGroupCandidates = candidateAlbumGroups[bestGroupKey].orEmpty()
            val unifiedAlbumRef = bestGroupCandidates.firstOrNull { it.coverBytes != null && it.coverBytes.isNotEmpty() }
                ?: bestGroupCandidates.firstOrNull { !it.coverUrl.isNullOrBlank() }
                ?: bestGroupCandidates.firstOrNull()
                ?: individualMap.values.firstOrNull { it.coverBytes != null && it.coverBytes.isNotEmpty() }
                ?: individualMap[firstTrack.id]
                ?: individualMap.values.first()

            // Cerca prima l'album usando il titolo/artista già noti dell'album locale (se non generico);
            // altrimenti usa il candidato di consenso emerso dalle tracce.
            val primarySearchId = if (hasKnownLocalAlbum) {
                enricherRepository.searchAlbumId(album.title, album.artist).getOrNull()
            } else null

            val deezerAlbumId = primarySearchId ?: run {
                val fallbackSearchTitle = unifiedAlbumRef.album.ifBlank { album.title }
                val fallbackSearchArtist = unifiedAlbumRef.albumArtist.ifBlank { album.artist }
                // Se l'album locale aveva già un titolo valido, accetta il fallback solo se coincide o c'è consenso maggioritario
                val hasStrongConsensus = bestGroupCandidates.size >= 2 &&
                    bestGroupCandidates.size >= (album.tracks.size + 1) / 2
                if (!hasKnownLocalAlbum || hasStrongConsensus || QuerySanitizer.titlesMatch(album.title, fallbackSearchTitle)) {
                    enricherRepository.searchAlbumId(fallbackSearchTitle, fallbackSearchArtist).getOrNull()
                } else {
                    null
                }
            }

            val officialAlbumDetail = if (deezerAlbumId != null && deezerAlbumId > 0L) {
                enricherRepository.getAlbumDetail(deezerAlbumId).getOrNull()
            } else null

            val matchedOfficialTracks = if (officialAlbumDetail != null && officialAlbumDetail.tracks.isNotEmpty()) {
                enricherRepository.alignAlbumTracks(album.tracks, officialAlbumDetail.tracks)
            } else {
                emptyMap()
            }

            val hasMajorityConsensus = bestGroupCandidates.size >= 2 &&
                bestGroupCandidates.size >= (album.tracks.size + 1) / 2

            val resolvedUnifiedAlbumTitle = when {
                !officialAlbumDetail?.title.isNullOrBlank() -> officialAlbumDetail!!.title
                hasKnownLocalAlbum && !hasMajorityConsensus -> album.title.trim()
                unifiedAlbumRef.album.isNotBlank() -> unifiedAlbumRef.album.trim()
                else -> album.title.trim()
            }

            val resolvedUnifiedAlbumArtist = resolveUnifiedAlbumArtist(
                officialAlbumArtist = officialAlbumDetail?.albumArtist,
                unifiedCandidateAlbumArtist = unifiedAlbumRef.albumArtist,
                localAlbumArtist = album.artist,
                firstTrackArtist = firstTrack.artist,
                hasMajorityConsensus = hasMajorityConsensus
            )

            val refMatchesUnifiedAlbum = QuerySanitizer.titlesMatch(
                QuerySanitizer.normalizeAlbumTitle(unifiedAlbumRef.album),
                QuerySanitizer.normalizeAlbumTitle(resolvedUnifiedAlbumTitle)
            )

            val finalCoverUrl = officialAlbumDetail?.coverXl?.takeIf { it.isNotBlank() }
                ?: (if (refMatchesUnifiedAlbum) unifiedAlbumRef.coverUrl?.takeIf { it.isNotBlank() } else null)
                ?: bestGroupCandidates.firstOrNull {
                    QuerySanitizer.titlesMatch(
                        QuerySanitizer.normalizeAlbumTitle(it.album),
                        QuerySanitizer.normalizeAlbumTitle(resolvedUnifiedAlbumTitle)
                    )
                }?.coverUrl?.takeIf { it.isNotBlank() }
                ?: (if (!hasKnownLocalAlbum) individualMap.values.firstNotNullOfOrNull { it.coverUrl?.takeIf { u -> u.isNotBlank() } } else null)

            val finalCoverBytes = run {
                val matchingFromTracks = if (!finalCoverUrl.isNullOrBlank()) {
                    individualMap.values.firstOrNull {
                        it.coverUrl == finalCoverUrl && it.coverBytes != null && it.coverBytes.isNotEmpty()
                    }?.coverBytes
                } else null

                matchingFromTracks
                    ?: (if (!finalCoverUrl.isNullOrBlank()) {
                        enricherRepository.downloadImage(finalCoverUrl).getOrNull()?.takeIf { it.isNotEmpty() }
                    } else null)
                    ?: (if (refMatchesUnifiedAlbum) unifiedAlbumRef.coverBytes?.takeIf { it.isNotEmpty() } else null)
                    ?: storageWriteManager.extractExistingAlbumCoverBytes(album)
                    ?: bestGroupCandidates.firstNotNullOfOrNull { it.coverBytes?.takeIf { b -> b.isNotEmpty() } }
                    ?: individualMap.values.firstNotNullOfOrNull { it.coverBytes?.takeIf { b -> b.isNotEmpty() } }
            }
            val finalReleaseYear = officialAlbumDetail?.releaseYear
                ?: (if (refMatchesUnifiedAlbum) unifiedAlbumRef.releaseYear else null)
            val finalGenre = officialAlbumDetail?.genre?.takeIf { it.isNotBlank() }
                ?: (if (refMatchesUnifiedAlbum) unifiedAlbumRef.genre else null)
                ?: album.tracks.firstNotNullOfOrNull { it.genre.takeIf { g -> g.isNotBlank() } }

            val unifiedMap = mutableMapOf<Long, EnrichedMetadata>()
            album.tracks.forEachIndexed { index, track ->
                val ind = individualMap[track.id] ?: unifiedAlbumRef
                val officialTrackInfo = matchedOfficialTracks[track.id]

                // In Uniforma Album:
                // - Preserva sempre il titolo locale se è già pulito e valido (non "Sconosciuto", non "Track 01", non nome file grezzo).
                // - Preserva sempre l'artista locale del brano se è già valido (mantenendo così i feat. e collaboratori).
                val isLocalTitleDirty = QuerySanitizer.isTitleUnknownOrDirty(
                    track.title,
                    track.displayName,
                    track.artist,
                    track.albumArtist
                )
                val resolvedTitle = if (!isLocalTitleDirty) {
                    track.title.trim()
                } else {
                    officialTrackInfo?.title?.takeIf { it.isNotBlank() }
                        ?: ind.title.takeIf {
                            it.isNotBlank() && !QuerySanitizer.isTitleUnknownOrDirty(
                                it,
                                track.displayName,
                                track.artist,
                                track.albumArtist
                            )
                        }
                        ?: QuerySanitizer.getEffectiveSearchTerms(track).second.takeIf { it.isNotBlank() }
                        ?: QuerySanitizer.cleanFileName(track.displayName).title.ifBlank { track.title.trim() }
                }

                val isLocalArtistDirty = QuerySanitizer.isArtistUnknownOrDirty(track.artist)
                val resolvedArtist = if (!isLocalArtistDirty) {
                    track.artist.trim()
                } else {
                    ind.artist.takeIf { !QuerySanitizer.isArtistUnknownOrDirty(it) }
                        ?: resolvedUnifiedAlbumArtist.ifBlank { track.artist.trim() }
                }

                val indBelongsToUnifiedAlbum = !ind.recordType.equals("single", ignoreCase = true) &&
                    QuerySanitizer.titlesMatch(
                        QuerySanitizer.normalizeAlbumTitle(ind.album),
                        QuerySanitizer.normalizeAlbumTitle(resolvedUnifiedAlbumTitle)
                    )

                val resolvedTrackNumber = officialTrackInfo?.trackPosition?.takeIf { it > 0 }
                    ?: track.trackNumber.takeIf { it > 0 }
                    ?: QuerySanitizer.extractTrackNumberFromFileName(track.displayName)
                    ?: (if (indBelongsToUnifiedAlbum) ind.trackNumber?.takeIf { it > 0 } else null)
                    ?: (index + 1)

                val resolvedDiscNumber = officialTrackInfo?.diskNumber?.takeIf { it > 0 }
                    ?: track.discNumber.takeIf { it > 0 }
                    ?: (if (indBelongsToUnifiedAlbum) ind.discNumber?.takeIf { it > 0 } else null)
                    ?: 1

                unifiedMap[track.id] = ind.copy(
                    title = resolvedTitle,
                    artist = resolvedArtist,
                    album = resolvedUnifiedAlbumTitle,
                    albumArtist = resolvedUnifiedAlbumArtist,
                    coverUrl = finalCoverUrl,
                    coverBytes = finalCoverBytes,
                    releaseYear = finalReleaseYear,
                    genre = finalGenre ?: ind.genre,
                    trackNumber = resolvedTrackNumber,
                    discNumber = resolvedDiscNumber,
                    isUnreleased = isUnreleasedAlbumTitle(resolvedUnifiedAlbumTitle)
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
        if (separate) {
            val restoredTracks = (current.album.tracks + current.movedOutTracks.values.map { it.first })
                .distinctBy { it.id }
            _enricherState.value = current.copy(
                album = current.album.copy(tracks = restoredTracks),
                tracksEnriched = current.individualTracksEnriched,
                movedOutTracks = emptyMap(),
                isSeparationMode = true,
                options = current.options.copy(normalizeAlbum = false)
            )
        } else {
            val targetUnifiedAlbum = (
                current.editedAlbumTitle
                    ?: current.unifiedTracksEnriched.values.firstOrNull()?.album
                    ?: current.album.title
                ).trim()

            val allTracks = (current.album.tracks + current.movedOutTracks.values.map { it.first })
                .distinctBy { it.id }
            val newMovedOut = mutableMapOf<Long, Pair<AudioTrack, EnrichedMetadata>>()
            val remainingTracks = mutableListOf<AudioTrack>()
            val newUnified = current.unifiedTracksEnriched.toMutableMap()

            allTracks.forEach { track ->
                val indMeta = current.individualTracksEnriched[track.id]
                val wasManuallyEdited = track.id in current.manuallyEditedTrackIds
                if (wasManuallyEdited &&
                    indMeta != null &&
                    indMeta.album.trim().isNotBlank() &&
                    !indMeta.album.trim().equals(targetUnifiedAlbum, ignoreCase = true)
                ) {
                    newMovedOut[track.id] = track to indMeta
                    newUnified.remove(track.id)
                } else {
                    remainingTracks.add(track)
                    if (wasManuallyEdited && indMeta != null) {
                        val baseUnified = newUnified[track.id] ?: current.unifiedTracksEnriched.values.firstOrNull()
                        newUnified[track.id] = indMeta.copy(
                            coverBytes = indMeta.coverBytes?.takeIf { it.isNotEmpty() } ?: baseUnified?.coverBytes,
                            coverUrl = indMeta.coverUrl?.takeIf { it.isNotBlank() } ?: baseUnified?.coverUrl
                        )
                    }
                }
            }

            _enricherState.value = current.copy(
                album = current.album.copy(tracks = remainingTracks),
                tracksEnriched = newUnified,
                unifiedTracksEnriched = newUnified,
                movedOutTracks = newMovedOut,
                isSeparationMode = false,
                options = current.options.copy(normalizeAlbum = true)
            )
        }
    }

    fun applyTrackEnrichment(
        track: AudioTrack,
        metadata: EnrichedMetadata,
        options: EnricherOptions
    ) {
        _enricherState.value = EnricherUiState.Writing(1, 1, metadata.title)
        scope.launch {
            val effectiveMeta = if (
                options.embedCover &&
                (metadata.coverBytes == null || metadata.coverBytes.isEmpty()) &&
                !metadata.coverUrl.isNullOrBlank()
            ) {
                val downloaded = enricherRepository.downloadImage(metadata.coverUrl).getOrNull()
                if (downloaded != null && downloaded.isNotEmpty()) {
                    metadata.copy(coverBytes = downloaded)
                } else {
                    metadata
                }
            } else {
                metadata
            }

            val preparedFile = storageWriteManager.prepareTrackInCache(track, effectiveMeta, options)
            if (preparedFile == null) {
                _enricherState.value = EnricherUiState.Error("Impossibile elaborare i frame audio.")
                return@launch
            }

            clearPendingWrites()
            pendingWrites[track] = Pair(preparedFile, effectiveMeta)

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
        options: EnricherOptions,
        movedOutTracks: Map<Long, Pair<AudioTrack, EnrichedMetadata>>? = null,
        trackCustomOptions: Map<Long, EnricherOptions>? = null
    ) {
        val currentAlbumState = _enricherState.value as? EnricherUiState.ReviewAlbum
        val isSeparationMode = currentAlbumState?.isSeparationMode == true
        val customAlbumCoverBytes = currentAlbumState?.customAlbumCoverBytes?.takeIf { it.isNotEmpty() }
        val effectiveMovedOut = movedOutTracks ?: currentAlbumState?.movedOutTracks.orEmpty()
        val effectiveCustomOptions = trackCustomOptions ?: currentAlbumState?.trackCustomOptions.orEmpty()
        val totalToProcess = album.tracks.size + effectiveMovedOut.size

        _enricherState.value = EnricherUiState.Writing(1, totalToProcess.coerceAtLeast(1), "Elaborazione file...")
        scope.launch {
            clearPendingWrites()
            val urisToAuthorize = mutableListOf<Uri>()
            val downloadedCoverCache = mutableMapOf<String, ByteArray>()

            suspend fun resolveCoverBytesForUrl(url: String?): ByteArray? {
                val cleanUrl = url?.takeIf { it.isNotBlank() } ?: return null
                downloadedCoverCache[cleanUrl]?.let { return it }
                val fromSibling = tracksEnriched.values.firstOrNull {
                    it.coverUrl == cleanUrl && it.coverBytes != null && it.coverBytes.isNotEmpty()
                }?.coverBytes
                if (fromSibling != null) {
                    downloadedCoverCache[cleanUrl] = fromSibling
                    return fromSibling
                }
                val downloaded = enricherRepository.downloadImage(cleanUrl).getOrNull()?.takeIf { it.isNotEmpty() }
                if (downloaded != null) {
                    downloadedCoverCache[cleanUrl] = downloaded
                }
                return downloaded
            }

            val sharedUnifiedCoverBytes: ByteArray? = if (!isSeparationMode) {
                customAlbumCoverBytes
                    ?: tracksEnriched.values.firstNotNullOfOrNull { it.coverBytes?.takeIf { b -> b.isNotEmpty() } }
                    ?: resolveCoverBytesForUrl(
                        tracksEnriched.values.firstNotNullOfOrNull { it.coverUrl?.takeIf { u -> u.isNotBlank() } }
                    )
                    ?: storageWriteManager.extractExistingAlbumCoverBytes(album)
            } else {
                null
            }

            album.tracks.forEach { track ->
                val rawMeta = tracksEnriched[track.id] ?: return@forEach
                val trackOpts = effectiveCustomOptions[track.id] ?: options
                val resolvedBytes = if (trackOpts.embedCover) {
                    rawMeta.coverBytes?.takeIf { it.isNotEmpty() }
                        ?: resolveCoverBytesForUrl(rawMeta.coverUrl)
                        ?: sharedUnifiedCoverBytes
                } else {
                    rawMeta.coverBytes
                }
                val meta = if (resolvedBytes != null && resolvedBytes !== rawMeta.coverBytes) {
                    rawMeta.copy(coverBytes = resolvedBytes)
                } else {
                    rawMeta
                }
                val prepared = storageWriteManager.prepareTrackInCache(track, meta, trackOpts)
                if (prepared != null) {
                    pendingWrites[track] = Pair(prepared, meta)
                    urisToAuthorize.add(track.uri)
                }
            }

            effectiveMovedOut.values.forEach { (movedTrack, rawMovedMeta) ->
                if (!pendingWrites.containsKey(movedTrack)) {
                    val trackOpts = effectiveCustomOptions[movedTrack.id] ?: options.copy(normalizeAlbum = false)
                    val resolvedBytes = if (trackOpts.embedCover) {
                        rawMovedMeta.coverBytes?.takeIf { it.isNotEmpty() }
                            ?: resolveCoverBytesForUrl(rawMovedMeta.coverUrl)
                    } else {
                        rawMovedMeta.coverBytes
                    }
                    val movedMeta = if (resolvedBytes != null && resolvedBytes !== rawMovedMeta.coverBytes) {
                        rawMovedMeta.copy(coverBytes = resolvedBytes)
                    } else {
                        rawMovedMeta
                    }
                    val prepared = storageWriteManager.prepareTrackInCache(movedTrack, movedMeta, trackOpts)
                    if (prepared != null) {
                        pendingWrites[movedTrack] = Pair(prepared, movedMeta)
                        urisToAuthorize.add(movedTrack.uri)
                    }
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

        val unreleasedToMark = mutableListOf<AudioTrack>()
        val officialToUnmark = mutableListOf<AudioTrack>()

        snapshot.forEach { (track, pair) ->
            val (file, metadata) = pair
            current++
            _enricherState.value = EnricherUiState.Writing(current, total, metadata.title)
            val ok = storageWriteManager.commitTrackWrite(track, file, metadata)
            if (ok) {
                successCount++
                if (metadata.isUnreleased || isUnreleasedAlbumTitle(metadata.album)) {
                    unreleasedToMark.add(track)
                } else {
                    officialToUnmark.add(track)
                }
            }
        }

        if (unreleasedToMark.isNotEmpty()) {
            UnreleasedTrackStore.markUnreleased(appContext, unreleasedToMark)
        }
        if (officialToUnmark.isNotEmpty()) {
            UnreleasedTrackStore.unmarkUnreleased(appContext, officialToUnmark)
        }

        // Notifica il ViewModel per l'aggiornamento ottimistico in memoria e il ricaricamento asincrono della libreria
        onReloadLibrary(appliedMetadataMap)

        _enricherState.value = EnricherUiState.Success(
            "Aggiornati con successo $successCount di $total brani!"
        )
    }

    /**
     * Scansiona in batch (con throttling sicuro) i brani della libreria attualmente privi di un album ufficiale
     * verificandone l'esistenza su Deezer: i brani non trovati (con artista valido) vengono registrati
     * in [UnreleasedTrackStore] e raggruppati immediatamente nell'album "Unreleased" di ciascun artista,
     * senza necessità di riscrivere i file audio su disco.
     */
    fun scanAndOrganizeUnreleasedTracks(
        allTracks: List<AudioTrack>,
        onUnreleasedGrouped: suspend () -> Unit
    ) {
        if (_unreleasedScanState.value is UnreleasedScanState.Scanning) return
        unreleasedScanJob?.cancel()
        unreleasedScanJob = scope.launch {
            val currentSnapshot = withContext(Dispatchers.IO) {
                UnreleasedTrackStore.getSnapshot(appContext)
            }
            val candidateTracks = withContext(Dispatchers.Default) {
                allTracks.filter { track ->
                    !isTrackUnreleased(track, currentSnapshot) && (
                        isGenericAlbum(track.album) ||
                            track.album.contains("(Brani singoli)") ||
                            track.album.trim().lowercase(Locale.ROOT) in SCAN_CANDIDATE_PLATFORM_ALBUMS
                        )
                }
            }

            if (candidateTracks.isEmpty()) {
                _unreleasedScanState.value = UnreleasedScanState.Completed(
                    scannedCount = 0,
                    newlyGroupedUnreleasedCount = 0,
                    affectedArtistsCount = 0
                )
                return@launch
            }

            val total = candidateTracks.size
            var processed = 0
            val newlyUnreleased = mutableListOf<AudioTrack>()
            val affectedArtists = mutableSetOf<String>()
            val officialMatches = LinkedHashMap<AudioTrack, EnrichedMetadata>()
            val lightweightOptions = EnricherOptions(
                normalizeAlbum = true,
                embedCover = false,
                includeLyrics = false,
                prioritizeStudioAlbum = true
            )

            val chunks = candidateTracks.chunked(3)
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                if (!isActive) return@launch
                _unreleasedScanState.value = UnreleasedScanState.Scanning(
                    current = (processed + 1).coerceAtMost(total),
                    total = total,
                    currentTrackTitle = chunk.first().title
                )

                val chunkResults = withContext(Dispatchers.IO) {
                    coroutineScope {
                        chunk.map { track ->
                            async {
                                track to enricherRepository.enrichTrack(track, lightweightOptions).getOrNull()
                            }
                        }.awaitAll()
                    }
                }

                for ((track, enriched) in chunkResults) {
                    processed++
                    if (enriched == null) continue
                    if (enriched.isUnreleased || isUnreleasedAlbumTitle(enriched.album)) {
                        val mainArtist = extractMainArtist(enriched.albumArtist.ifBlank { enriched.artist })
                        if (mainArtist.isNotBlank() && !QuerySanitizer.isArtistUnknownOrDirty(mainArtist)) {
                            newlyUnreleased.add(track)
                            affectedArtists.add(mainArtist.lowercase(Locale.ROOT))
                        }
                    } else if (!isGenericAlbum(enriched.album) && !enriched.album.contains("(Brani singoli)")) {
                        officialMatches[track] = enriched
                    }
                }

                _unreleasedScanState.value = UnreleasedScanState.Scanning(
                    current = processed.coerceAtMost(total),
                    total = total,
                    currentTrackTitle = chunk.last().title
                )

                if (chunkIndex < chunks.lastIndex) {
                    delay(320L)
                }
            }

            if (newlyUnreleased.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    UnreleasedTrackStore.markUnreleased(appContext, newlyUnreleased)
                }
                onUnreleasedGrouped()
            }

            _unreleasedScanState.value = UnreleasedScanState.Completed(
                scannedCount = total,
                newlyGroupedUnreleasedCount = newlyUnreleased.size,
                affectedArtistsCount = affectedArtists.size,
                officialMatches = officialMatches
            )
        }
    }

    fun cancelUnreleasedScan() {
        unreleasedScanJob?.cancel()
        unreleasedScanJob = null
        _unreleasedScanState.value = UnreleasedScanState.Idle
    }

    fun dismissUnreleasedScanSummary() {
        if (_unreleasedScanState.value !is UnreleasedScanState.Scanning) {
            _unreleasedScanState.value = UnreleasedScanState.Idle
        }
    }

    /**
     * Apre la finestra di revisione Smart Auto-Tagger (in modalità Separazione Album) per i brani
     * che durante la scansione batch sono risultati presenti ufficialmente su Deezer.
     */
    fun reviewOfficialMatchesFromScan(): Boolean {
        val completed = _unreleasedScanState.value as? UnreleasedScanState.Completed ?: return false
        if (completed.officialMatches.isEmpty()) return false

        val matchedTracks = completed.officialMatches.keys.toList()
        val metadataById = completed.officialMatches.entries.associate { (track, meta) ->
            track.id to meta
        }
        val syntheticAlbum = Album(
            id = -999_001L,
            title = UNRELEASED_ALBUM_TITLE,
            artist = matchedTracks.first().artist,
            albumArtUri = matchedTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri,
            tracks = matchedTracks
        )
        _unreleasedScanState.value = UnreleasedScanState.Idle
        _enricherState.value = EnricherUiState.ReviewAlbum(
            album = syntheticAlbum,
            tracksEnriched = metadataById,
            individualTracksEnriched = metadataById,
            unifiedTracksEnriched = metadataById,
            options = EnricherOptions(normalizeAlbum = false),
            isSeparationMode = true
        )
        return true
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

    companion object {
        private val SCAN_CANDIDATE_PLATFORM_ALBUMS = setOf(
            "youtube",
            "youtube music",
            "soundcloud",
            "audiomack",
            "bandcamp",
            "datpiff",
            "leak",
            "leaks",
            "discord",
            "telegram"
        )

        /**
         * Determina l'Album Artist univoco in modalità "Uniforma Album" garantendo di conservare
         * automaticamente solo il nome del primo artista principale (escludendo co-autori/featuring)
         * affinché tutti i brani del disco restino raggruppati in un unico album.
         */
        internal fun resolveUnifiedAlbumArtist(
            officialAlbumArtist: String?,
            unifiedCandidateAlbumArtist: String,
            localAlbumArtist: String,
            firstTrackArtist: String,
            hasMajorityConsensus: Boolean
        ): String {
            val rawChosen = when {
                !officialAlbumArtist.isNullOrBlank() &&
                    !QuerySanitizer.isArtistUnknownOrDirty(officialAlbumArtist) ->
                    officialAlbumArtist.trim()
                !QuerySanitizer.isArtistUnknownOrDirty(localAlbumArtist) && !hasMajorityConsensus ->
                    localAlbumArtist.trim()
                unifiedCandidateAlbumArtist.isNotBlank() &&
                    !QuerySanitizer.isArtistUnknownOrDirty(unifiedCandidateAlbumArtist) ->
                    unifiedCandidateAlbumArtist.trim()
                !QuerySanitizer.isArtistUnknownOrDirty(localAlbumArtist) ->
                    localAlbumArtist.trim()
                !QuerySanitizer.isArtistUnknownOrDirty(firstTrackArtist) ->
                    firstTrackArtist.trim()
                else ->
                    localAlbumArtist.trim().ifBlank { firstTrackArtist.trim() }
            }
            return QuerySanitizer.extractPrimaryAlbumArtist(rawChosen).ifBlank { rawChosen }
        }
    }
}
