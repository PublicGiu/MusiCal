package com.example.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AppPreferences
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.PlaybackContext
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.playback.SleepTimerState
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.StatsRepository
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.LibraryCacheRepository
import com.example.musicplayer.data.extractMainArtist
import com.example.musicplayer.data.groupTracksIntoAlbums
import com.example.musicplayer.data.isMixedOrGeneric
import com.example.musicplayer.data.loadAudioFiles
import com.example.musicplayer.data.matchesArtist
import com.example.musicplayer.data.sortAlbums
import com.example.musicplayer.data.sortPlaylists
import com.example.musicplayer.data.sortTracks
import com.example.musicplayer.data.LyricsData
import com.example.musicplayer.data.LyricsRepository
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.data.AudioSourceMode
import com.example.musicplayer.data.update.AppUpdateInfo
import com.example.musicplayer.data.update.GithubUpdateChecker
import com.example.musicplayer.ui.tagger.EnricherUiState
import com.example.musicplayer.ui.tagger.MetadataEnricherCoordinator
import com.example.musicplayer.playback.PlaybackService
import com.example.musicplayer.playback.SleepTimerCoordinator
import com.example.musicplayer.data.lastfm.LastFmCoordinator
import android.content.IntentSender
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository(application)
    private val statsRepository = StatsRepository(application)
    private val libraryCacheRepository = LibraryCacheRepository(application)
    val metadataEnricherCoordinator = MetadataEnricherCoordinator(
        context = application,
        scope = viewModelScope,
        onReloadLibrary = { affectedMap -> handleEnricherPostSave(affectedMap) }
    )
    val enricherState: StateFlow<EnricherUiState> = metadataEnricherCoordinator.enricherState
    val writeRequestIntentSender: StateFlow<IntentSender?> = metadataEnricherCoordinator.writeRequestIntentSender
    val currentLyrics: StateFlow<LyricsData?> = metadataEnricherCoordinator.currentLyrics

    private val _deleteRequestIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteRequestIntentSender: StateFlow<IntentSender?> = _deleteRequestIntentSender.asStateFlow()
    private var pendingTracksToDelete: List<AudioTrack> = emptyList()

    // --- StateFlow principali ---
    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // --- Contesto di riproduzione e Timer di spegnimento ---
    private val _playbackContext = MutableStateFlow<PlaybackContext>(PlaybackContext.Library)
    val playbackContext: StateFlow<PlaybackContext> = _playbackContext.asStateFlow()

    val sleepTimerCoordinator = SleepTimerCoordinator(
        scope = viewModelScope,
        onPausePlayback = { mediaController?.pause() },
        onSetVolume = { vol -> mediaController?.setVolume(vol) },
        getCurrentTrack = { _currentTrack.value },
        getCurrentPositionMs = { _currentPositionMs.value },
        getDurationMs = { _durationMs.value }
    )
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerCoordinator.sleepTimerState
    private var isPlaybackStateRestored: Boolean = false
    private var isRestoringPlaybackState: Boolean = false
    private var hasRecordedPlayForRestoredTrack: Boolean = true

    // --- 1. Coda di riproduzione (Coda Utente, Autoplay a 15 brani e Timeline generale) ---
    private val _userQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val userQueue: StateFlow<List<AudioTrack>> = _userQueue.asStateFlow()

    private val _autoplayQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val autoplayQueue: StateFlow<List<AudioTrack>> = _autoplayQueue.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    // --- 2. Statistiche d'ascolto e ordinamento ---
    private val _stats = MutableStateFlow<Map<Long, TrackStats>>(emptyMap())
    val stats: StateFlow<Map<Long, TrackStats>> = _stats.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_PLAYED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    // --- 3. Ricerca globale ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- Flussi Reattivi Pre-Ordinati e Filtrati (Calcolati fuori dal Main Thread su Dispatchers.Default) ---
    val sortedTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks, _sortOption, _stats, _isAscending
    ) { tracks, sortOption, stats, isAscending ->
        tracks.sortTracks(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedAlbums: StateFlow<List<Album>> = combine(
        _albums, _sortOption, _stats, _isAscending
    ) { albums, sortOption, stats, isAscending ->
        albums.sortAlbums(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedPlaylists: StateFlow<List<Playlist>> = combine(
        _playlists, _sortOption, _stats, _isAscending
    ) { playlists, sortOption, stats, isAscending ->
        playlists.sortPlaylists(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks, _searchQuery
    ) { tracks, query ->
        if (query.isBlank()) emptyList()
        else tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingAlbums: StateFlow<List<Album>> = combine(
        _albums, _searchQuery
    ) { albums, query ->
        if (query.isBlank()) emptyList()
        else albums.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingPlaylists: StateFlow<List<Playlist>> = combine(
        _playlists, _searchQuery
    ) { playlists, query ->
        if (query.isBlank()) emptyList()
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- 4. Last.fm Scrobbling & Offline Cache ---
    val lastFmCoordinator = LastFmCoordinator(
        context = application,
        scope = viewModelScope,
        getCurrentTrack = { _currentTrack.value },
        isPlayingProvider = { _isPlaying.value }
    )
    val isLastFmLoggedIn: StateFlow<Boolean> = lastFmCoordinator.isLastFmLoggedIn
    val lastFmUsername: StateFlow<String?> = lastFmCoordinator.lastFmUsername
    val lastFmAuthLoading: StateFlow<Boolean> = lastFmCoordinator.lastFmAuthLoading
    val lastFmAuthError: StateFlow<String?> = lastFmCoordinator.lastFmAuthError
    val lastFmStatusMessage: StateFlow<String?> = lastFmCoordinator.lastFmStatusMessage
    val lastFmCustomApiKey: StateFlow<String> = lastFmCoordinator.lastFmCustomApiKey
    val lastFmCustomSharedSecret: StateFlow<String> = lastFmCoordinator.lastFmCustomSharedSecret
    val pendingScrobblesCount: StateFlow<Int> = lastFmCoordinator.pendingScrobblesCount

    // --- 5. Preferenze dell'applicazione (Tema, Colori dinamici, Autoplay) ---
    private val appPreferences = AppPreferences(application)

    private val _themeMode = MutableStateFlow(appPreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(appPreferences.isDynamicColorEnabled())
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _isAutoplayEnabled = MutableStateFlow(appPreferences.isAutoplayEnabled())
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    // --- 5. Fonti di Scansione & Aggiornamenti GitHub ---
    private val updateChecker = GithubUpdateChecker()

    private val _audioSourceMode = MutableStateFlow(appPreferences.getAudioSourceMode())
    val audioSourceMode: StateFlow<AudioSourceMode> = _audioSourceMode.asStateFlow()

    private val _customFolders = MutableStateFlow(appPreferences.getCustomFolders())
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckError = MutableStateFlow<String?>(null)
    val updateCheckError: StateFlow<String?> = _updateCheckError.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    private val recentPlayedTrackIds = ArrayDeque<Long>()

    // Contesto di riproduzione attivo (es. tracce dell'album o della playlist avviata)
    private var activeContextTracks: List<AudioTrack> = emptyList()
    private var isCustomContextActive: Boolean = false

    private var lastKnownQueueIndex: Int = 0

    // --- Riferimenti interni ---
    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var isViewModelCleared = false
    private var progressTickerJob: Job? = null

    // Listener per gli eventi di ExoPlayer / MediaSession
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                if (!hasRecordedPlayForRestoredTrack) {
                    hasRecordedPlayForRestoredTrack = true
                    _currentTrack.value?.let { current ->
                        recordTrackPlayed(current.id)
                        recordRecentTrack(current.id)
                        lastFmCoordinator.handleTrackStarted(current)
                    }
                }
                startProgressTicker()
            } else {
                stopProgressTicker()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // Se un controller esterno (es. Bluetooth/notifica) abilita shuffleModeEnabled su ExoPlayer,
            // invertiamo lo stato logico di shuffle interno mantenendo sequenziale ExoPlayer (1:1 con la Coda).
            if (shuffleModeEnabled) {
                mediaController?.shuffleModeEnabled = false
                val newShuffle = !_isShuffleEnabled.value
                _isShuffleEnabled.value = newShuffle
                rebuildAutoplayForCurrentMode()
                saveCurrentPlaybackState()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val changed = _repeatMode.value != repeatMode
            _repeatMode.value = repeatMode
            if (changed) {
                rebuildAutoplayForCurrentMode()
                saveCurrentPlaybackState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
                val controller = mediaController
                val prevIndex = lastKnownQueueIndex
                val newIndex = controller?.currentMediaItemIndex ?: 0
                val isSteppingBack = newIndex < prevIndex
                val stepsForward = newIndex - prevIndex
                lastKnownQueueIndex = newIndex

                val trackId = item.mediaId.toLongOrNull()
                val foundTrack = _tracks.value.find { it.id == trackId }
                val current = foundTrack ?: AudioTrack(
                    id = trackId ?: 0L,
                    title = item.mediaMetadata.title?.toString() ?: "Sconosciuto",
                    artist = item.mediaMetadata.artist?.toString() ?: "Sconosciuto",
                    duration = 0L,
                    uri = item.requestMetadata.mediaUri ?: android.net.Uri.EMPTY,
                    albumArtUri = item.mediaMetadata.artworkUri
                )
                val previousTrackId = _currentTrack.value?.id
                _currentTrack.value = current

                // Se la transizione è causata dal ripristino iniziale a freddo (in pausa),
                // non registriamo un falso ascolto né sovrascriviamo la posizione salvata.
                if (isRestoringPlaybackState) {
                    isRestoringPlaybackState = false
                    updateDuration()
                    updateQueueFromController()
                    return
                }

                val timer = sleepTimerCoordinator.sleepTimerState.value
                if (timer is SleepTimerState.Active && timer.isEndOfTrack && previousTrackId != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                    ) {
                        sleepTimerCoordinator.cancel()
                        controller?.pause()
                        hasRecordedPlayForRestoredTrack = false
                        saveCurrentPlaybackState()
                        updateDuration()
                        updateQueueFromController()
                        return
                    } else {
                        // Cambio traccia manuale da parte dell'utente: ripristina il volume pieno per il nuovo brano
                        controller?.setVolume(1.0f)
                    }
                }

                hasRecordedPlayForRestoredTrack = true
                // Registra la statistica d'ascolto della traccia avviata
                trackId?.let { id ->
                    recordTrackPlayed(id)
                    recordRecentTrack(id)
                }
                lastFmCoordinator.handleTrackStarted(current)
                saveCurrentPlaybackState()

                if (_repeatMode.value == Player.REPEAT_MODE_ONE && previousTrackId != null && previousTrackId != current.id) {
                    rebuildAutoplayForCurrentMode()
                } else if (isSteppingBack && controller != null) {
                    // Ritorno a un brano precedente: riallinea _autoplayQueue con gli elementi effettivi dopo _userQueue
                    val allTracks = _tracks.value
                    val autoStartExo = (newIndex + 1 + _userQueue.value.size).coerceAtMost(controller.mediaItemCount)
                    val rebuiltAutoplay = mutableListOf<AudioTrack>()
                    for (i in autoStartExo until controller.mediaItemCount) {
                        val mItem = controller.getMediaItemAt(i)
                        val tId = mItem.mediaId.toLongOrNull()
                        val resolved = allTracks.find { it.id == tId } ?: AudioTrack(
                            id = tId ?: i.toLong(),
                            title = mItem.mediaMetadata.title?.toString() ?: "Sconosciuto",
                            artist = mItem.mediaMetadata.artist?.toString() ?: "Sconosciuto",
                            duration = 0L,
                            uri = mItem.requestMetadata.mediaUri ?: android.net.Uri.EMPTY,
                            albumArtUri = mItem.mediaMetadata.artworkUri
                        )
                        rebuiltAutoplay.add(resolved)
                    }
                    if (rebuiltAutoplay.size > 15) {
                        val excessStart = autoStartExo + 15
                        if (excessStart < controller.mediaItemCount) {
                            controller.removeMediaItems(excessStart, controller.mediaItemCount)
                        }
                        _autoplayQueue.value = rebuiltAutoplay.take(15)
                    } else {
                        _autoplayQueue.value = rebuiltAutoplay
                    }
                } else if (stepsForward > 0) {
                    // Gestione consumo Coda Utente vs Riproduzione Automatica in avanzamento basata sull'offset esatto
                    val userList = _userQueue.value
                    if (stepsForward <= userList.size) {
                        _userQueue.value = userList.drop(stepsForward)
                    } else {
                        val autoConsumed = stepsForward - userList.size
                        _userQueue.value = emptyList()
                        _autoplayQueue.value = _autoplayQueue.value.drop(autoConsumed)
                    }
                }
                replenishAutoplayIfNeeded(current)
            } ?: run {
                _currentTrack.value = null
                lastFmCoordinator.resetCurrentTrackScrobble()
                lastKnownQueueIndex = 0
            }
            updateDuration()
            updateQueueFromController()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateQueueFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateDuration()
            if (playbackState == Player.STATE_READY) {
                _currentPositionMs.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
            }
            if (playbackState == Player.STATE_ENDED) {
                val timer = sleepTimerCoordinator.sleepTimerState.value
                if (timer is SleepTimerState.Active && timer.isEndOfTrack) {
                    mediaController?.pause()
                    sleepTimerCoordinator.cancel()
                } else {
                    checkAndTriggerAutoplayIfNeeded()
                }
            }
            updateQueueFromController()
        }
    }

    init {
        initMediaController()
        loadInitialCachedLibrary()
        loadPlaylists()
        loadStats()
        initLyricsObserver()
    }

    private fun loadInitialCachedLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMode = _audioSourceMode.value
            val currentCustomFolders = _customFolders.value
            val cached = libraryCacheRepository.loadCache(currentMode, currentCustomFolders)
            if (cached != null) {
                val (cachedTracks, cachedAlbums) = cached
                if (cachedTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (_tracks.value.isEmpty()) {
                            _tracks.value = cachedTracks
                            _albums.value = cachedAlbums
                            _isLoading.value = false
                            restoreLastPlaybackStateIfPossible()
                        }
                    }
                }
            }
        }
    }

    private fun initLyricsObserver() {
        viewModelScope.launch {
            _currentTrack.collect { track ->
                metadataEnricherCoordinator.updateCurrentLyricsForTrack(track)
            }
        }
    }

    private fun initMediaController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                if (isViewModelCleared) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)

                _isPlaying.value = controller.isPlaying
                controller.shuffleModeEnabled = false
                _isShuffleEnabled.value = appPreferences.getLastShuffleEnabled()
                _repeatMode.value = controller.repeatMode

                controller.currentMediaItem?.let { item ->
                    val trackId = item.mediaId.toLongOrNull()
                    _currentTrack.value = _tracks.value.find { it.id == trackId }
                }
                updateDuration()
                updateQueueFromController()

                if (controller.isPlaying) {
                    startProgressTicker()
                } else if (controller.playbackState == Player.STATE_IDLE && _tracks.value.isNotEmpty()) {
                    restoreLastPlaybackStateIfPossible()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun reloadLibraryInternal(force: Boolean = false) {
        withContext(Dispatchers.Main) {
            // Se la libreria non è ancora popolata o se è un refresh forzato, attiva l'indicatore di caricamento
            if (_tracks.value.isEmpty() || force) {
                _isLoading.value = true
            }
        }
        val currentMode = _audioSourceMode.value
        val currentCustomFolders = _customFolders.value
        val (loadedTracks, groupedAlbums) = withContext(Dispatchers.IO) {
            val tracks = loadAudioFiles(getApplication(), currentMode, currentCustomFolders)
            val albums = groupTracksIntoAlbums(tracks)
            tracks to albums
        }

        withContext(Dispatchers.Main) {
            // Confronto reattivo profondo: se force == false ed i brani sono identici, evita riemissione superflua
            val currentTracks = _tracks.value
            val isUnchanged = !force &&
                    currentTracks.size == loadedTracks.size &&
                    currentTracks.isNotEmpty() &&
                    currentTracks == loadedTracks

            if (!isUnchanged || currentTracks.isEmpty()) {
                _tracks.value = loadedTracks
                _albums.value = groupedAlbums
                withContext(Dispatchers.IO) {
                    libraryCacheRepository.saveCache(currentMode, currentCustomFolders, loadedTracks, groupedAlbums)
                }
            }

            mediaController?.currentMediaItem?.let { item ->
                val trackId = item.mediaId.toLongOrNull()
                val foundTrack = loadedTracks.find { it.id == trackId }
                _currentTrack.value = foundTrack
                if (foundTrack != null && !lastFmCoordinator.isCurrentScrobbleTrack(trackId ?: -1L) && mediaController?.isPlaying == true) {
                    lastFmCoordinator.handleTrackStarted(foundTrack)
                }
            }
            updateQueueFromController()
            _isLoading.value = false
            restoreLastPlaybackStateIfPossible()
        }
    }

    fun loadTracks(force: Boolean = false) {
        viewModelScope.launch {
            reloadLibraryInternal(force)
        }
    }

    // --- Gestione Statistiche ---
    private fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = statsRepository.loadStats()
            _stats.value = loaded
        }
    }

    private fun recordTrackPlayed(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            statsRepository.recordTrackPlayed(trackId)
            val updated = statsRepository.loadStats()
            _stats.value = updated
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleSortDirection() {
        _isAscending.value = !_isAscending.value
    }

    fun setSortAscending(ascending: Boolean) {
        _isAscending.value = ascending
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // --- Gestione Playlist ---
    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = playlistRepository.loadPlaylists()
            _playlists.value = list
        }
    }

    fun createPlaylist(name: String, coverUri: android.net.Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.createPlaylistWithSourceUri(name, coverUri)
            val updated = playlistRepository.loadPlaylists()
            _playlists.value = updated
        }
    }

    fun updatePlaylistCover(playlistId: String, coverUri: android.net.Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.updatePlaylistCoverFromUri(playlistId, coverUri)
            val updated = playlistRepository.loadPlaylists()
            _playlists.value = updated
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.deletePlaylist(playlistId)
            val updated = playlistRepository.loadPlaylists()
            _playlists.value = updated
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
            val updated = playlistRepository.loadPlaylists()
            _playlists.value = updated
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            val updated = playlistRepository.loadPlaylists()
            _playlists.value = updated
        }
    }

    // --- 1. Gestione Coda di Riproduzione ---
    private fun updateQueueFromController() {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val queueItems = mutableListOf<AudioTrack>()
        val allTracks = _tracks.value

        for (i in 0 until count) {
            val item = controller.getMediaItemAt(i)
            val trackId = item.mediaId.toLongOrNull()
            val track = allTracks.find { it.id == trackId } ?: AudioTrack(
                id = trackId ?: i.toLong(),
                title = item.mediaMetadata.title?.toString() ?: "Sconosciuto",
                artist = item.mediaMetadata.artist?.toString() ?: "Sconosciuto",
                duration = 0L,
                uri = item.requestMetadata.mediaUri ?: android.net.Uri.EMPTY,
                albumArtUri = item.mediaMetadata.artworkUri
            )
            queueItems.add(track)
        }
        _queue.value = queueItems
        _currentQueueIndex.value = controller.currentMediaItemIndex
    }

    /**
     * Converte un oggetto AudioTrack in MediaItem per ExoPlayer.
     */
    private fun trackToMediaItem(track: AudioTrack): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
        track.albumArtUri?.let { metadataBuilder.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * Aggiunge un brano subito dopo il brano in riproduzione nella Coda Utente (prioritaria).
     * Eseguito tassativamente sul Main Thread.
     */
    fun addToQueue(track: AudioTrack) {
        addTracksToQueue(listOf(track))
    }

    /**
     * Aggiunge una lista di brani subito dopo il brano in riproduzione nella Coda Utente (prioritaria).
     * Se la riproduzione era ferma, avvia la prima traccia.
     */
    fun addTracksToQueue(tracksToAdd: List<AudioTrack>) {
        if (tracksToAdd.isEmpty()) return
        val controller = mediaController ?: return
        val mediaItems = tracksToAdd.map { trackToMediaItem(it) }

        if (controller.mediaItemCount == 0) {
            playTrack(tracksToAdd.first())
            if (tracksToAdd.size > 1) {
                addTracksToQueue(tracksToAdd.drop(1))
            }
            return
        }

        val currentIndex = controller.currentMediaItemIndex
        val baseOffset = currentIndex + 1 + _userQueue.value.size
        val insertIndex = if (baseOffset in 0..controller.mediaItemCount) {
            baseOffset
        } else {
            controller.mediaItemCount
        }

        controller.addMediaItems(insertIndex, mediaItems)
        _userQueue.value = _userQueue.value + tracksToAdd
        updateQueueFromController()
    }

    /**
     * Aggiunge tutte le tracce di un album in coda utente.
     */
    fun addAlbumToQueue(album: Album) {
        val sortedTracks = album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
        addTracksToQueue(sortedTracks)
    }

    /**
     * Aggiunge tutte le tracce di una playlist in coda utente.
     */
    fun addPlaylistToQueue(playlist: Playlist) {
        val trackMap = _tracks.value.associateBy { it.id }
        val playlistTracks = playlist.trackIds.mapNotNull { trackMap[it] }
        addTracksToQueue(playlistTracks)
    }

    // --- Metodi Gestione Coda Utente (Prioritaria) ---

    fun removeUserQueueItem(index: Int) {
        val controller = mediaController ?: return
        val currentList = _userQueue.value
        if (index !in currentList.indices) return
        val currentIndex = controller.currentMediaItemIndex
        val exoIndex = currentIndex + 1 + index
        if (exoIndex in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(exoIndex)
        }
        _userQueue.value = currentList.filterIndexed { i, _ -> i != index }
        updateQueueFromController()
    }

    fun moveUserQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentList = _userQueue.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _userQueue.value = currentList

        val currentIndex = controller.currentMediaItemIndex
        val fromExo = currentIndex + 1 + fromIndex
        val toExo = currentIndex + 1 + toIndex
        if (fromExo in 0 until controller.mediaItemCount && toExo in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromExo, toExo)
        }
        updateQueueFromController()
    }

    fun moveUserQueueItemToTop(fromIndex: Int) {
        if (fromIndex == 0) return
        moveUserQueueItem(fromIndex, 0)
    }

    fun clearUserQueue() {
        val controller = mediaController ?: return
        val count = _userQueue.value.size
        if (count == 0) return
        val currentIndex = controller.currentMediaItemIndex
        val startExo = currentIndex + 1
        val endExo = (startExo + count).coerceAtMost(controller.mediaItemCount)
        if (startExo in 0 until controller.mediaItemCount && startExo < endExo) {
            controller.removeMediaItems(startExo, endExo)
        }
        _userQueue.value = emptyList()
        updateQueueFromController()
    }

    fun skipToUserQueueItem(index: Int) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val targetIndex = currentIndex + 1 + index
        if (targetIndex in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(targetIndex)
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
        }
    }

    // --- Metodi Gestione Riproduzione Automatica (15 Brani Successivi) ---

    fun removeAutoplayItem(index: Int) {
        val controller = mediaController ?: return
        val currentAutoplay = _autoplayQueue.value
        if (index !in currentAutoplay.indices) return
        val currentIndex = controller.currentMediaItemIndex
        val exoIndex = currentIndex + 1 + _userQueue.value.size + index
        if (exoIndex in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(exoIndex)
        }
        _autoplayQueue.value = currentAutoplay.filterIndexed { i, _ -> i != index }
        replenishAutoplayIfNeeded()
        updateQueueFromController()
    }

    fun moveAutoplayItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentList = _autoplayQueue.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _autoplayQueue.value = currentList

        val currentIndex = controller.currentMediaItemIndex
        val baseOffset = currentIndex + 1 + _userQueue.value.size
        val fromExo = baseOffset + fromIndex
        val toExo = baseOffset + toIndex
        if (fromExo in 0 until controller.mediaItemCount && toExo in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromExo, toExo)
        }
        updateQueueFromController()
    }

    fun moveAutoplayItemToTop(fromIndex: Int) {
        if (fromIndex == 0) return
        moveAutoplayItem(fromIndex, 0)
    }

    /**
     * Promuove un brano dalla lista di Riproduzione Automatica alla fine della Coda Utente (prioritaria),
     * riallineando la timeline di ExoPlayer e rifornendo la finestra di 15 brani.
     */
    fun moveAutoplayItemToUserQueue(index: Int) {
        val controller = mediaController ?: return
        val currentAutoplay = _autoplayQueue.value
        if (index !in currentAutoplay.indices) return
        val track = currentAutoplay[index]
        val currentIndex = controller.currentMediaItemIndex
        val userQueueSize = _userQueue.value.size
        val fromExo = currentIndex + 1 + userQueueSize + index
        val toExo = currentIndex + 1 + userQueueSize

        if (fromExo != toExo && fromExo in 0 until controller.mediaItemCount && toExo in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromExo, toExo)
        }

        _userQueue.value = _userQueue.value + track
        _autoplayQueue.value = currentAutoplay.filterIndexed { i, _ -> i != index }
        replenishAutoplayIfNeeded()
        updateQueueFromController()
    }

    fun skipToAutoplayItem(index: Int) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val targetIndex = currentIndex + 1 + _userQueue.value.size + index
        if (targetIndex in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(targetIndex)
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
        }
    }

    // --- Metodi Generali / Retrocompatibilità ---

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            updateQueueFromController()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex == toIndex) return
        val count = controller.mediaItemCount
        if (fromIndex in 0 until count && toIndex in 0 until count) {
            val currentList = _queue.value.toMutableList()
            if (fromIndex in currentList.indices && toIndex in currentList.indices) {
                val item = currentList.removeAt(fromIndex)
                currentList.add(toIndex, item)
                _queue.value = currentList
            }
            controller.moveMediaItem(fromIndex, toIndex)
            _currentQueueIndex.value = controller.currentMediaItemIndex
        }
    }

    fun calculateMoveToTopTargetIndex(fromIndex: Int, currentIndex: Int, count: Int): Int? {
        if (fromIndex !in 0 until count) return null
        return if (currentIndex in 0 until count) {
            if (fromIndex == currentIndex || fromIndex == currentIndex + 1) null
            else if (fromIndex < currentIndex) currentIndex else currentIndex + 1
        } else {
            if (fromIndex == 0) null else 0
        }
    }

    fun moveQueueItemToTop(fromIndex: Int) {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val targetIndex = calculateMoveToTopTargetIndex(
            fromIndex = fromIndex,
            currentIndex = controller.currentMediaItemIndex,
            count = count
        ) ?: return
        moveQueueItem(fromIndex, targetIndex)
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        controller.clearMediaItems()
        activeContextTracks = emptyList()
        isCustomContextActive = false
        _currentTrack.value = null
        _queue.value = emptyList()
        _userQueue.value = emptyList()
        _autoplayQueue.value = emptyList()
        _currentQueueIndex.value = 0
    }

    fun skipToQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
        }
    }

    /**
     * Restituisce le tracce del contesto attivo per la modalità di ripetizione:
     * 1. Se è attiva una coda contestuale (album o playlist avviati dall'utente) e il brano vi appartiene, usa quella.
     * 2. Altrimenti cerca l'album del brano di riferimento nella libreria e ne restituisce le tracce ordinate.
     * 3. Fallback: lista con il solo brano di riferimento.
     */
    private fun getRepeatContextTracks(referenceTrack: AudioTrack?): List<AudioTrack> {
        if (activeContextTracks.isNotEmpty() && (referenceTrack == null || activeContextTracks.any { it.id == referenceTrack.id })) {
            return activeContextTracks
        }
        if (referenceTrack != null) {
            val matchingAlbum = _albums.value.find { it.id == referenceTrack.albumId }
                ?: _albums.value.find { alb -> alb.tracks.any { it.id == referenceTrack.id } }
                ?: _albums.value.find { it.title.equals(referenceTrack.album, ignoreCase = true) }
            val albumTracks = matchingAlbum?.tracks?.sortedWith(
                compareBy<AudioTrack> { it.discNumber }
                    .thenBy { it.trackNumber }
                    .thenBy { it.title }
            )
            if (!albumTracks.isNullOrEmpty()) {
                return albumTracks
            }
            return listOf(referenceTrack)
        }
        return emptyList()
    }

    fun playCustomQueue(trackList: List<AudioTrack>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (trackList.isEmpty()) return

        isRestoringPlaybackState = false
        hasRecordedPlayForRestoredTrack = true
        activeContextTracks = trackList
        isCustomContextActive = true

        val safeIndex = startIndex.coerceIn(0, trackList.size - 1)
        val previousInList = trackList.subList(0, safeIndex)
        val current = trackList[safeIndex]
        _userQueue.value = emptyList()

        val isShuffled = _isShuffleEnabled.value
        val initialAutoplay = when (_repeatMode.value) {
            Player.REPEAT_MODE_ALL -> buildCyclicContextQueue(trackList, current, count = 15, isShuffled = isShuffled)
            Player.REPEAT_MODE_ONE -> List(15) { current }
            else -> {
                val orderedUpcoming = if (isShuffled) {
                    trackList.filterIndexed { index, _ -> index != safeIndex }.shuffled()
                } else {
                    trackList.subList(safeIndex + 1, trackList.size)
                }
                val needed = 15 - orderedUpcoming.size
                val autoplaySupplement = if (_isAutoplayEnabled.value && needed > 0) {
                    val excludeIds = (trackList.map { it.id } + recentPlayedTrackIds).toSet()
                    val candidates = _tracks.value.filter { it.id !in excludeIds }
                    candidates.shuffled().take(needed)
                } else {
                    emptyList()
                }
                (orderedUpcoming + autoplaySupplement).take(15)
            }
        }
        _autoplayQueue.value = initialAutoplay

        val allItems = (previousInList + listOf(current) + initialAutoplay).map { trackToMediaItem(it) }
        lastKnownQueueIndex = previousInList.size
        controller.setMediaItems(allItems, previousInList.size, 0L)
        controller.prepare()
        controller.play()
        _currentTrack.value = current
        _currentPositionMs.value = 0L
        _isPlaying.value = true
        startProgressTicker()
        updateQueueFromController()
        saveCurrentPlaybackState()
    }

    fun playAlbum(album: Album, startIndex: Int = 0) {
        _playbackContext.value = PlaybackContext.Album(album.id, album.title, album.artist)
        val albumSongs = album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
        playCustomQueue(albumSongs, startIndex)
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        val trackMap = _tracks.value.associateBy { it.id }
        val playlistTracks = playlist.trackIds.mapNotNull { trackMap[it] }
        _playbackContext.value = PlaybackContext.Playlist(playlist.id, playlist.name)
        playCustomQueue(playlistTracks, startIndex)
    }

    fun playPlaylist(playlistTracks: List<AudioTrack>, startIndex: Int = 0) {
        if (_playbackContext.value !is PlaybackContext.Playlist) {
            _playbackContext.value = PlaybackContext.Playlist("", "Playlist")
        }
        playCustomQueue(playlistTracks, startIndex)
    }

    fun playPlaylist(playlistId: String, playlistName: String, playlistTracks: List<AudioTrack>, startIndex: Int = 0) {
        _playbackContext.value = PlaybackContext.Playlist(playlistId, playlistName)
        playCustomQueue(playlistTracks, startIndex)
    }

    fun playArtist(artistName: String, startIndex: Int = 0) {
        val artistTracks = _tracks.value.filter { it.matchesArtist(artistName) }
        if (artistTracks.isEmpty()) return
        val displayArtist = extractMainArtist(artistName).ifBlank { artistName }
        _playbackContext.value = PlaybackContext.Artist(displayArtist)
        playCustomQueue(artistTracks, startIndex)
    }

    fun playTrack(track: AudioTrack) {
        val controller = mediaController ?: return
        val currentQueueSnapshot = _queue.value
        val currentIdxSnapshot = _currentQueueIndex.value

        isRestoringPlaybackState = false
        hasRecordedPlayForRestoredTrack = true
        _playbackContext.value = PlaybackContext.Library
        activeContextTracks = emptyList()
        isCustomContextActive = false

        // Preserva i brani già riprodotti nella sessione corrente (fino a max 25 brani) per consentire il tasto Precedente
        val rawHistory = if (currentQueueSnapshot.isNotEmpty() && currentIdxSnapshot in currentQueueSnapshot.indices) {
            currentQueueSnapshot.take(currentIdxSnapshot + 1)
        } else {
            emptyList()
        }
        val historyTracks = rawHistory
            .dropLastWhile { it.id == track.id }
            .takeLast(25)

        _userQueue.value = emptyList()
        _autoplayQueue.value = emptyList()

        val isShuffled = _isShuffleEnabled.value
        val autoplayCandidates = when (_repeatMode.value) {
            Player.REPEAT_MODE_ALL -> buildCyclicContextQueue(getRepeatContextTracks(track), track, count = 15, isShuffled = isShuffled)
            Player.REPEAT_MODE_ONE -> List(15) { track }
            else -> if (_isAutoplayEnabled.value) {
                selectAutoplayTracks(referenceTrack = track, count = 15)
            } else {
                emptyList()
            }
        }
        _autoplayQueue.value = autoplayCandidates

        val allItems = (historyTracks + listOf(track) + autoplayCandidates).map { trackToMediaItem(it) }
        val startIndex = historyTracks.size
        lastKnownQueueIndex = startIndex
        controller.setMediaItems(allItems, startIndex, 0L)
        controller.prepare()
        controller.play()
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _isPlaying.value = true
        startProgressTicker()
        updateQueueFromController()
        saveCurrentPlaybackState()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            _isPlaying.value = false
            stopProgressTicker()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _tracks.value.isNotEmpty()) {
                val trackToPlay = _currentTrack.value ?: _tracks.value.first()
                playTrack(trackToPlay)
            } else {
                controller.play()
                _isPlaying.value = true
                startProgressTicker()
            }
        }
    }

    fun playNext() {
        mediaController?.let { controller ->
            if (controller.repeatMode == Player.REPEAT_MODE_ONE) {
                controller.seekTo(0L)
                _currentPositionMs.value = 0L
                _currentTrack.value?.let { lastFmCoordinator.handleTrackStarted(it) }
            } else if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            }
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
        }
    }

    fun playPrevious() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex > 0) {
            controller.seekToDefaultPosition(currentIndex - 1)
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
            return
        }

        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
            return
        }

        // Se siamo all'indice 0 senza brani precedenti nella timeline di ExoPlayer,
        // cerca il brano precedente nella libreria ordinata corrente e inseriscilo in testa
        val current = _currentTrack.value
        val currentSorted = _tracks.value.sortTracks(_sortOption.value, _stats.value, _isAscending.value)
        val idxInSorted = if (current != null) currentSorted.indexOfFirst { it.id == current.id } else -1
        val prevTrack = when {
            idxInSorted > 0 -> currentSorted[idxInSorted - 1]
            idxInSorted == 0 && currentSorted.size > 1 -> currentSorted.last()
            else -> null
        }

        if (prevTrack != null && controller.mediaItemCount > 0) {
            controller.addMediaItem(0, trackToMediaItem(prevTrack))
            lastKnownQueueIndex = 1
            controller.seekToDefaultPosition(0)
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
            updateQueueFromController()
        } else {
            controller.seekTo(0L)
            _currentPositionMs.value = 0L
            controller.play()
            _isPlaying.value = true
            startProgressTicker()
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        controller.shuffleModeEnabled = false
        rebuildAutoplayForCurrentMode()
        saveCurrentPlaybackState()
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
        rebuildAutoplayForCurrentMode()
        saveCurrentPlaybackState()
    }

    /**
     * Ricalcola immediatamente la lista di Riproduzione Automatica (_autoplayQueue, 15 brani)
     * e aggiorna i MediaItem corrispondenti in ExoPlayer quando cambia la modalità di ripetizione,
     * lo shuffle o l'impostazione di autoplay, rendendo immediatamente visibile nella coda l'effetto.
     */
    private fun rebuildAutoplayForCurrentMode() {
        val controller = mediaController ?: return
        val current = _currentTrack.value ?: return

        _autoplayQueue.value = emptyList()

        val isShuffled = _isShuffleEnabled.value
        val newAutoplay = when (_repeatMode.value) {
            Player.REPEAT_MODE_ALL -> {
                val contextTracks = getRepeatContextTracks(current)
                buildCyclicContextQueue(contextTracks, current, count = 15, isShuffled = isShuffled)
            }
            Player.REPEAT_MODE_ONE -> {
                List(15) { current }
            }
            else -> {
                if (isCustomContextActive && activeContextTracks.isNotEmpty()) {
                    val idxInContext = activeContextTracks.indexOfFirst { it.id == current.id }
                    val orderedUpcoming = if (isShuffled) {
                        activeContextTracks.filter { it.id != current.id }.shuffled()
                    } else if (idxInContext != -1 && idxInContext + 1 < activeContextTracks.size) {
                        activeContextTracks.subList(idxInContext + 1, activeContextTracks.size)
                    } else {
                        emptyList()
                    }
                    val needed = 15 - orderedUpcoming.size
                    val autoplaySupplement = if (_isAutoplayEnabled.value && needed > 0) {
                        val excludeIds = (activeContextTracks.map { it.id } + _userQueue.value.map { it.id } + recentPlayedTrackIds + current.id).toSet()
                        val candidates = _tracks.value.filter { it.id !in excludeIds }
                            .ifEmpty { _tracks.value.filter { it.id != current.id } }
                        candidates.shuffled().take(needed)
                    } else {
                        emptyList()
                    }
                    (orderedUpcoming + autoplaySupplement).take(15)
                } else {
                    if (_isAutoplayEnabled.value) {
                        selectAutoplayTracks(referenceTrack = current, count = 15)
                    } else {
                        emptyList()
                    }
                }
            }
        }

        _autoplayQueue.value = newAutoplay

        if (controller.mediaItemCount > 0) {
            val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            val autoStartExo = (currentIndex + 1 + _userQueue.value.size).coerceAtMost(controller.mediaItemCount)
            if (autoStartExo < controller.mediaItemCount) {
                controller.removeMediaItems(autoStartExo, controller.mediaItemCount)
            }
            if (newAutoplay.isNotEmpty()) {
                controller.addMediaItems(newAutoplay.map { trackToMediaItem(it) })
            }
            updateQueueFromController()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    private fun updateDuration() {
        val dur = mediaController?.duration ?: 0L
        _durationMs.value = if (dur > 0L) dur else (_currentTrack.value?.duration ?: 0L)
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaController?.let { controller ->
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    val dur = controller.duration
                    val effectiveDur = if (dur > 0L) {
                        _durationMs.value = dur
                        dur
                    } else {
                        _durationMs.value
                    }

                    lastFmCoordinator.checkAndTriggerScrobble(pos, effectiveDur)
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        mediaController?.let { controller ->
            _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
        }
        saveCurrentPlaybackState()
    }

    // --- Metodi Pubblici Last.fm ---
    fun saveLastFmApiCredentials(apiKey: String, sharedSecret: String) {
        lastFmCoordinator.saveApiCredentials(apiKey, sharedSecret)
    }

    fun loginLastFm(
        username: String,
        password: String,
        customApiKey: String? = null,
        customSharedSecret: String? = null
    ) {
        lastFmCoordinator.login(username, password, customApiKey, customSharedSecret)
    }

    fun logoutLastFm() {
        lastFmCoordinator.logout()
    }

    fun syncOfflineScrobbles() {
        lastFmCoordinator.syncOfflineScrobbles()
    }

    fun clearLastFmStatusMessage() {
        lastFmCoordinator.clearStatusMessage()
    }

    // --- Gestione Riproduzione Automatica Continua (Autoplay) ---
    private fun recordRecentTrack(trackId: Long) {
        recentPlayedTrackIds.remove(trackId)
        recentPlayedTrackIds.addLast(trackId)
        while (recentPlayedTrackIds.size > 30) {
            recentPlayedTrackIds.removeFirst()
        }
    }

    /**
     * Seleziona in modo intelligente nuovi brani dalla libreria locale:
     * - Esclude i brani attualmente in coda utente, in autoplay, il brano corrente e quelli riprodotti di recente.
     * - Priorità 1: brani dello stesso artista o dello stesso genere del brano di riferimento.
     * - Priorità 2: brani casuali estratti dalla libreria completa dei brani validi.
     */
    private fun selectAutoplayTracks(referenceTrack: AudioTrack?, count: Int = 15): List<AudioTrack> {
        val allTracks = _tracks.value
        if (allTracks.isEmpty()) return emptyList()

        val queuedIds = (_userQueue.value.map { it.id } + _autoplayQueue.value.map { it.id }).toSet()
        val currentId = (_currentTrack.value?.id ?: referenceTrack?.id)?.let { setOf(it) } ?: emptySet()
        val excludedIds = queuedIds + currentId + recentPlayedTrackIds

        var candidates = allTracks.filter { it.id !in excludedIds }
        if (candidates.isEmpty()) {
            candidates = allTracks.filter { it.id !in (queuedIds + currentId) }
        }
        if (candidates.isEmpty()) {
            candidates = allTracks.filter { it.id !in currentId }
        }
        if (candidates.isEmpty()) {
            candidates = allTracks
        }

        val selected = if (referenceTrack != null) {
            val sameArtist = candidates.filter {
                it.artist.isNotBlank() &&
                it.artist.equals(referenceTrack.artist, ignoreCase = true) &&
                !it.artist.contains("sconosciut", ignoreCase = true) &&
                !it.artist.contains("unknown", ignoreCase = true)
            }
            val sameGenre = if (referenceTrack.genre.isNotBlank()) {
                candidates.filter {
                    it.genre.isNotBlank() && it.genre.equals(referenceTrack.genre, ignoreCase = true)
                }
            } else {
                emptyList()
            }

            val priority1 = (sameArtist + sameGenre).distinctBy { it.id }.shuffled()
            val priority1Ids = priority1.map { it.id }.toSet()
            val priority2 = candidates.filter { it.id !in priority1Ids }.shuffled()

            (priority1 + priority2).take(count)
        } else {
            candidates.shuffled().take(count)
        }

        selected.forEach { recordRecentTrack(it.id) }
        return selected
    }

    /**
     * Mantiene la lista di riproduzione automatica a esattamente 15 brani successivi.
     * Chiamato quando un brano autoplay viene consumato o rimosso.
     */
    fun replenishAutoplayIfNeeded(referenceTrack: AudioTrack? = _currentTrack.value) {
        val controller = mediaController ?: return
        val mode = _repeatMode.value
        val currentSize = _autoplayQueue.value.size
        val needed = 15 - currentSize
        if (needed <= 0) return

        val newTracks = when (mode) {
            Player.REPEAT_MODE_ALL -> {
                val contextTracks = getRepeatContextTracks(referenceTrack)
                val tailTrack = _autoplayQueue.value.lastOrNull() ?: referenceTrack
                buildCyclicContextQueue(contextTracks, tailTrack, count = needed, isShuffled = _isShuffleEnabled.value)
            }
            Player.REPEAT_MODE_ONE -> {
                val target = referenceTrack ?: _currentTrack.value
                if (target != null) List(needed) { target } else emptyList()
            }
            else -> {
                // In modalità REPEAT_MODE_OFF: se c'è un contesto attivo (es. album/playlist con >15 brani),
                // accoda prima le eventuali tracce rimanenti non ancora inserite di quel contesto
                val remainingFromContext = if (isCustomContextActive && activeContextTracks.isNotEmpty()) {
                    val contextIdSet = activeContextTracks.map { it.id }.toSet()
                    val allQueuedAreFromContext = _autoplayQueue.value.all { it.id in contextIdSet }
                    val tailTrack = _autoplayQueue.value.lastOrNull() ?: referenceTrack
                    val tailIndex = if (tailTrack != null && allQueuedAreFromContext) {
                        activeContextTracks.indexOfFirst { it.id == tailTrack.id }
                    } else {
                        -1
                    }
                    if (tailIndex != -1 && tailIndex + 1 < activeContextTracks.size) {
                        activeContextTracks.subList(tailIndex + 1, activeContextTracks.size).take(needed)
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val stillNeeded = needed - remainingFromContext.size
                val supplement = if (_isAutoplayEnabled.value && stillNeeded > 0) {
                    selectAutoplayTracks(referenceTrack, count = stillNeeded)
                } else {
                    emptyList()
                }
                remainingFromContext + supplement
            }
        }
        if (newTracks.isEmpty()) return

        _autoplayQueue.value = _autoplayQueue.value + newTracks
        val mediaItems = newTracks.map { trackToMediaItem(it) }
        controller.addMediaItems(mediaItems)
        updateQueueFromController()
    }

    /**
     * Verifica lo stato della coda e, se l'Autoplay è attivo e siamo all'ultimo brano,
     * accoda i nuovi brani alla playlist di ExoPlayer senza pause audio.
     */
    private fun checkAndTriggerAutoplayIfNeeded() {
        replenishAutoplayIfNeeded()
    }

    fun toggleAutoplay() {
        val newValue = !_isAutoplayEnabled.value
        setAutoplayEnabled(newValue)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        appPreferences.setAutoplayEnabled(enabled)
        if (enabled) {
            checkAndTriggerAutoplayIfNeeded()
        } else if (_repeatMode.value == Player.REPEAT_MODE_OFF) {
            rebuildAutoplayForCurrentMode()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appPreferences.setThemeMode(mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        _isDynamicColorEnabled.value = enabled
        appPreferences.setDynamicColorEnabled(enabled)
    }

    fun setAudioSourceMode(mode: AudioSourceMode) {
        if (_audioSourceMode.value != mode) {
            _audioSourceMode.value = mode
            appPreferences.setAudioSourceMode(mode)
            loadTracks(force = true)
        }
    }

    fun addCustomFolder(folderPath: String) {
        val clean = folderPath.trim().trimStart('/')
        val normalized = if (clean.endsWith('/')) clean else "$clean/"
        val updated = _customFolders.value.toMutableSet()
        if (updated.add(normalized)) {
            _customFolders.value = updated
            appPreferences.setCustomFolders(updated)
            if (_audioSourceMode.value == AudioSourceMode.CUSTOM_FOLDERS) {
                loadTracks(force = true)
            }
        }
    }

    fun removeCustomFolder(folderPath: String) {
        val updated = _customFolders.value.toMutableSet()
        if (updated.remove(folderPath)) {
            _customFolders.value = updated
            appPreferences.setCustomFolders(updated)
            if (_audioSourceMode.value == AudioSourceMode.CUSTOM_FOLDERS) {
                loadTracks(force = true)
            }
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            if (!isManual) {
                delay(3000L) // Differito di 3 secondi all'avvio per non intasare le risorse durante il cold start
            }
            _isCheckingUpdate.value = true
            _updateCheckError.value = null
            val currentVersion = com.example.musicplayer.BuildConfig.VERSION_NAME
            val result = updateChecker.checkForUpdates(currentVersion)
            _isCheckingUpdate.value = false

            result.onSuccess { info ->
                _updateInfo.value = info
                appPreferences.setLastUpdateCheckTimestamp(System.currentTimeMillis())
                if (info.isUpdateAvailable) {
                    val dismissed = appPreferences.getDismissedUpdateVersion()
                    if (isManual || dismissed != info.latestVersion) {
                        _isUpdateDialogVisible.value = true
                    }
                }
            }.onFailure { error ->
                _updateCheckError.value = error.message ?: "Impossibile verificare gli aggiornamenti"
            }
        }
    }

    fun dismissUpdateDialog(neverShowAgainForThisVersion: Boolean = false) {
        _isUpdateDialogVisible.value = false
        if (neverShowAgainForThisVersion) {
            _updateInfo.value?.let { info ->
                appPreferences.setDismissedUpdateVersion(info.latestVersion)
            }
        }
    }

    // --- Funzionalità Smart Auto-Tagger & Metadata Enricher ---

    fun startTrackEnrichment(track: AudioTrack) {
        metadataEnricherCoordinator.startTrackEnrichment(track)
    }

    fun selectAlternativeCandidate(
        track: AudioTrack,
        candidate: DeezerSearchTrack,
        options: EnricherOptions
    ) {
        metadataEnricherCoordinator.selectAlternativeCandidate(track, candidate, options)
    }

    fun searchCustomTrackEnrichment(
        track: AudioTrack,
        customTitle: String,
        customArtist: String
    ) {
        metadataEnricherCoordinator.searchCustomTrackEnrichment(track, customTitle, customArtist)
    }

    fun adjustLyricsOffset(offsetDeltaMs: Long) {
        metadataEnricherCoordinator.adjustLyricsOffset(_currentTrack.value, offsetDeltaMs)
    }

    fun startAlbumEnrichment(album: Album, separateAlbums: Boolean? = null) {
        metadataEnricherCoordinator.startAlbumEnrichment(album, separateAlbums)
    }

    fun toggleAlbumEnrichmentMode(separate: Boolean) {
        metadataEnricherCoordinator.toggleAlbumEnrichmentMode(separate)
    }

    fun applyTrackEnrichment(
        track: AudioTrack,
        metadata: EnrichedMetadata,
        options: EnricherOptions
    ) {
        metadataEnricherCoordinator.applyTrackEnrichment(track, metadata, options)
    }

    fun applyAlbumEnrichment(
        album: Album,
        tracksEnriched: Map<Long, EnrichedMetadata>,
        options: EnricherOptions
    ) {
        metadataEnricherCoordinator.applyAlbumEnrichment(album, tracksEnriched, options)
    }

    fun onWritePermissionResult(isGranted: Boolean) {
        metadataEnricherCoordinator.onWritePermissionResult(isGranted)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun handleEnricherPostSave(appliedMetadataMap: Map<Long, EnrichedMetadata>) {
        if (appliedMetadataMap.isNotEmpty()) {
            val currentTracks = _tracks.value
            val patchedTracks = currentTracks.map { track ->
                val meta = appliedMetadataMap[track.id]
                if (meta != null) {
                    track.copy(
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        albumArtist = meta.albumArtist.ifBlank { meta.artist },
                        trackNumber = meta.trackNumber ?: track.trackNumber,
                        discNumber = meta.discNumber ?: track.discNumber,
                        genre = meta.genre ?: track.genre
                    )
                } else {
                    track
                }
            }
            val patchedAlbums = groupTracksIntoAlbums(patchedTracks)
            withContext(Dispatchers.Main) {
                _tracks.value = patchedTracks
                _albums.value = patchedAlbums
                _currentTrack.value?.let { curr ->
                    if (appliedMetadataMap.containsKey(curr.id)) {
                        patchedTracks.find { it.id == curr.id }?.let { _currentTrack.value = it }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            try {
                val imageLoader = coil.Coil.imageLoader(getApplication<Application>())
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (_: Exception) {}
        }

        reloadLibraryInternal(force = true)
        val updatedPlaylists = playlistRepository.loadPlaylists()
        withContext(Dispatchers.Main) {
            _playlists.value = updatedPlaylists
        }

        _currentTrack.value?.let { current ->
            metadataEnricherCoordinator.updateCurrentLyricsForTrack(current)
        }
    }

    fun closeEnricher() {
        metadataEnricherCoordinator.closeEnricher()
    }

    /**
     * Avvia la richiesta di eliminazione fisica dei file audio specificati dallo storage del dispositivo.
     */
    fun deleteTracks(tracks: List<AudioTrack>, onCompleted: ((Int) -> Unit)? = null) {
        if (tracks.isEmpty()) return
        pendingTracksToDelete = tracks
        val uris = tracks.map { it.uri }
        val intentSender = metadataEnricherCoordinator.createDeleteRequestIntentSender(uris)
        if (intentSender != null) {
            _deleteRequestIntentSender.value = intentSender
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                var deletedCount = 0
                for (track in tracks) {
                    try {
                        val count = getApplication<android.app.Application>().contentResolver.delete(track.uri, null, null)
                        if (count > 0) deletedCount++
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    onDeletePermissionResult(isGranted = deletedCount > 0, onDeletedCount = onCompleted)
                }
            }
        }
    }

    /**
     * Gestisce l'esito della finestra di dialogo di autorizzazione eliminazione di sistema (Android 16).
     */
    fun onDeletePermissionResult(isGranted: Boolean, onDeletedCount: ((Int) -> Unit)? = null) {
        val tracks = pendingTracksToDelete
        pendingTracksToDelete = emptyList()
        _deleteRequestIntentSender.value = null

        if (isGranted && tracks.isNotEmpty()) {
            val deletedIds = tracks.map { it.id }.toSet()
            val currentTracks = _tracks.value
            val remainingTracks = currentTracks.filter { it.id !in deletedIds }
            _tracks.value = remainingTracks
            val updatedAlbums = groupTracksIntoAlbums(remainingTracks)
            _albums.value = updatedAlbums

            // Se la traccia attualmente in riproduzione è stata eliminata:
            if (_currentTrack.value?.id in deletedIds) {
                playNext()
            }
            val currentQueue = _queue.value
            if (currentQueue.any { it.id in deletedIds }) {
                _queue.value = currentQueue.filter { it.id !in deletedIds }
            }

            viewModelScope.launch(Dispatchers.IO) {
                libraryCacheRepository.saveCache(_audioSourceMode.value, _customFolders.value, remainingTracks, updatedAlbums)
            }

            onDeletedCount?.invoke(tracks.size)
        }
    }

    fun saveCurrentPlaybackState() {
        val track = _currentTrack.value ?: return
        val pos = _currentPositionMs.value
        val rep = _repeatMode.value
        val shuf = _isShuffleEnabled.value
        val (cType, cId, cName) = when (val ctx = _playbackContext.value) {
            is PlaybackContext.Album -> Triple("ALBUM", ctx.id.toString(), ctx.title)
            is PlaybackContext.Playlist -> Triple("PLAYLIST", ctx.id, ctx.name)
            is PlaybackContext.Artist -> Triple("ARTIST", null, ctx.name)
            is PlaybackContext.Library -> Triple("LIBRARY", null, null)
        }
        appPreferences.saveLastPlaybackState(
            trackId = track.id,
            positionMs = pos,
            repeatMode = rep,
            shuffleEnabled = shuf,
            contextType = cType,
            contextId = cId,
            contextName = cName
        )
    }

    private fun restoreLastPlaybackStateIfPossible() {
        val controller = mediaController ?: return
        if (isPlaybackStateRestored || controller.playbackState != Player.STATE_IDLE || _currentTrack.value != null) {
            return
        }
        val allTracks = _tracks.value
        if (allTracks.isEmpty()) return

        val lastTrackId = appPreferences.getLastPlayedTrackId()
        if (lastTrackId == -1L) return
        val track = allTracks.find { it.id == lastTrackId } ?: return

        isPlaybackStateRestored = true
        isRestoringPlaybackState = true
        hasRecordedPlayForRestoredTrack = false

        val lastRepeat = appPreferences.getLastRepeatMode()
        val lastShuffle = appPreferences.getLastShuffleEnabled()
        _repeatMode.value = lastRepeat
        _isShuffleEnabled.value = lastShuffle
        controller.repeatMode = lastRepeat
        controller.shuffleModeEnabled = false

        val cType = appPreferences.getLastContextType()
        val cId = appPreferences.getLastContextId()
        val cName = appPreferences.getLastContextName()
        when (cType) {
            "ALBUM" -> {
                val albumId = cId?.toLongOrNull()
                val alb = _albums.value.find { it.id == albumId } ?: _albums.value.find { it.tracks.any { t -> t.id == track.id } }
                if (alb != null) {
                    _playbackContext.value = PlaybackContext.Album(alb.id, alb.title, alb.artist)
                    activeContextTracks = alb.tracks.sortedWith(compareBy<AudioTrack> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title })
                    isCustomContextActive = true
                } else {
                    _playbackContext.value = PlaybackContext.Album(track.albumId, track.album, track.artist)
                    activeContextTracks = emptyList()
                    isCustomContextActive = false
                }
            }
            "PLAYLIST" -> {
                val currentPlaylists = _playlists.value.ifEmpty { playlistRepository.loadPlaylists() }
                val p = currentPlaylists.find { it.id == cId }
                _playbackContext.value = PlaybackContext.Playlist(cId ?: "", cName ?: (p?.name ?: "Playlist"))
                if (p != null) {
                    val trackMap = allTracks.associateBy { it.id }
                    activeContextTracks = p.trackIds.mapNotNull { trackMap[it] }
                    isCustomContextActive = true
                }
            }
            "ARTIST" -> {
                val artistName = cName ?: track.artist
                _playbackContext.value = PlaybackContext.Artist(artistName)
                activeContextTracks = allTracks.filter { it.matchesArtist(artistName) }
                isCustomContextActive = true
            }
            else -> {
                _playbackContext.value = PlaybackContext.Library
                activeContextTracks = emptyList()
                isCustomContextActive = false
            }
        }

        val posMs = appPreferences.getLastPlaybackPositionMs().coerceAtLeast(0L)
        val contextTracks = if (activeContextTracks.isNotEmpty()) activeContextTracks else getRepeatContextTracks(track)
        val initialAutoplay = when (lastRepeat) {
            Player.REPEAT_MODE_ALL -> buildCyclicContextQueue(contextTracks, track, count = 15, isShuffled = lastShuffle)
            Player.REPEAT_MODE_ONE -> List(15) { track }
            else -> {
                if (isCustomContextActive && activeContextTracks.isNotEmpty()) {
                    val idx = activeContextTracks.indexOfFirst { it.id == track.id }
                    val ord = if (lastShuffle) {
                        activeContextTracks.filter { it.id != track.id }.shuffled()
                    } else if (idx != -1 && idx + 1 < activeContextTracks.size) {
                        activeContextTracks.subList(idx + 1, activeContextTracks.size)
                    } else emptyList()
                    val needed = 15 - ord.size
                    val supp = if (_isAutoplayEnabled.value && needed > 0) {
                        val exIds = (activeContextTracks.map { it.id } + track.id).toSet()
                        allTracks.filter { it.id !in exIds }.shuffled().take(needed)
                    } else emptyList()
                    (ord + supp).take(15)
                } else if (_isAutoplayEnabled.value) {
                    selectAutoplayTracks(track, count = 15)
                } else {
                    emptyList()
                }
            }
        }

        _autoplayQueue.value = initialAutoplay
        _userQueue.value = emptyList()
        val allItems = (listOf(track) + initialAutoplay).map { trackToMediaItem(it) }
        controller.setMediaItems(allItems, 0, posMs)
        controller.prepare()
        controller.pause()
        _currentTrack.value = track
        _currentPositionMs.value = posMs
        _isPlaying.value = false
        updateDuration()
        updateQueueFromController()
    }

    // --- Sleep Timer ---
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerCoordinator.setMinutes(minutes)
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerCoordinator.setEndOfTrack()
    }

    fun cancelSleepTimer() {
        sleepTimerCoordinator.cancel()
    }

    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = playlistRepository.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
            if (success) {
                val updated = playlistRepository.loadPlaylists()
                withContext(Dispatchers.Main) {
                    _playlists.value = updated
                }
            }
        }
    }

    fun clearListeningStats() {
        viewModelScope.launch(Dispatchers.IO) {
            statsRepository.clearStats()
            withContext(Dispatchers.Main) {
                _stats.value = emptyMap()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        isViewModelCleared = true
        sleepTimerCoordinator.cancel()
        lastFmCoordinator.stop()
        saveCurrentPlaybackState()
        stopProgressTicker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController?.run {
            removeListener(playerListener)
            release()
        }
        mediaController = null
    }
}

/**
 * Costruisce una coda ciclica di [count] brani a partire dal brano successivo ad [anchorTrack]
 * all'interno di [contextTracks]. Se le tracce del contesto (es. album o playlist) terminano,
 * ricomincia dalle prime tracce in modo da rendere visibile nella coda l'effetto della ripetizione.
 * Se [isShuffled] è true, mescola l'ordine dei brani del ciclo.
 */
fun buildCyclicContextQueue(
    contextTracks: List<AudioTrack>,
    anchorTrack: AudioTrack?,
    count: Int = 15,
    isShuffled: Boolean = false
): List<AudioTrack> {
    if (contextTracks.isEmpty() || count <= 0) return emptyList()
    if (contextTracks.size == 1) return List(count) { contextTracks[0] }

    if (isShuffled) {
        val otherTracks = if (anchorTrack != null) {
            contextTracks.filter { it.id != anchorTrack.id }
        } else {
            contextTracks
        }
        val pool = otherTracks.ifEmpty { contextTracks }
        val result = mutableListOf<AudioTrack>()
        while (result.size < count) {
            result.addAll(pool.shuffled())
        }
        return result.take(count)
    }

    val anchorIndex = if (anchorTrack != null) {
        contextTracks.indexOfFirst { it.id == anchorTrack.id }
    } else {
        -1
    }
    val startOffset = if (anchorIndex != -1) anchorIndex + 1 else 0
    return List(count) { i ->
        contextTracks[(startOffset + i) % contextTracks.size]
    }
}
