package com.example.musicplayer.ui

import android.app.Application
import android.app.LocaleManager
import android.content.ComponentName
import android.content.IntentSender
import android.net.Uri
import android.os.LocaleList
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AppLanguage
import com.example.musicplayer.data.AppPreferences
import com.example.musicplayer.data.ArtistInfoRepository
import com.example.musicplayer.data.ArtistSearchResult
import com.example.musicplayer.data.AudioSourceMode
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.DuplicateTrackGroup
import com.example.musicplayer.data.LibraryCacheRepository
import com.example.musicplayer.data.LibraryCoordinator
import com.example.musicplayer.data.LyricsData
import com.example.musicplayer.data.PlaybackContext
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.StatsRepository
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.lastfm.LastFmCoordinator
import com.example.musicplayer.data.tagger.model.DeezerSearchTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.data.update.AppUpdateInfo
import com.example.musicplayer.data.update.GithubUpdateChecker
import com.example.musicplayer.playback.PlaybackService
import com.example.musicplayer.playback.QueueCoordinator
import com.example.musicplayer.playback.SleepTimerCoordinator
import com.example.musicplayer.playback.SleepTimerState
import com.example.musicplayer.data.backup.BackupRepository
import com.example.musicplayer.data.backup.ImportSummary
import com.example.musicplayer.data.lastfm.cache.AppDatabase
import com.example.musicplayer.ui.tagger.EnricherUiState
import com.example.musicplayer.ui.tagger.MetadataEnricherCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)
    private val playlistRepository = PlaylistRepository(application)
    private val statsRepository = StatsRepository(application)
    private val libraryCacheRepository = LibraryCacheRepository(application)
    val artistInfoRepository = ArtistInfoRepository(application)
    private val backupRepository = BackupRepository(
        context = application,
        appPreferences = appPreferences,
        playlistRepository = playlistRepository,
        statsRepository = statsRepository,
        pendingScrobbleDao = AppDatabase.getInstance(application).pendingScrobbleDao()
    )

    val metadataEnricherCoordinator = MetadataEnricherCoordinator(
        context = application,
        scope = viewModelScope,
        onReloadLibrary = { affectedMap -> handleEnricherPostSave(affectedMap) }
    )
    val enricherState: StateFlow<EnricherUiState> = metadataEnricherCoordinator.enricherState
    val writeRequestIntentSender: StateFlow<IntentSender?> = metadataEnricherCoordinator.writeRequestIntentSender
    val currentLyrics: StateFlow<LyricsData?> = metadataEnricherCoordinator.currentLyrics

    val libraryCoordinator = LibraryCoordinator(
        application = application,
        scope = viewModelScope,
        appPreferences = appPreferences,
        artistInfoRepository = artistInfoRepository,
        playlistRepository = playlistRepository,
        statsRepository = statsRepository,
        libraryCacheRepository = libraryCacheRepository,
        createDeleteRequestIntentSender = { uris -> metadataEnricherCoordinator.createDeleteRequestIntentSender(uris) }
    )

    // --- StateFlow Libreria, Ricerca e Statistiche ---
    val tracks: StateFlow<List<AudioTrack>> = libraryCoordinator.tracks
    val albums: StateFlow<List<Album>> = libraryCoordinator.albums
    val playlists: StateFlow<List<Playlist>> = libraryCoordinator.playlists
    val stats: StateFlow<Map<Long, TrackStats>> = libraryCoordinator.stats
    val isLoading: StateFlow<Boolean> = libraryCoordinator.isLoading
    val sortOption: StateFlow<SortOption> = libraryCoordinator.sortOption
    val isAscending: StateFlow<Boolean> = libraryCoordinator.isAscending
    val searchQuery: StateFlow<String> = libraryCoordinator.searchQuery
    val audioSourceMode: StateFlow<AudioSourceMode> = libraryCoordinator.audioSourceMode
    val customFolders: StateFlow<Set<String>> = libraryCoordinator.customFolders
    val ignoredDuplicateKeys: StateFlow<Set<String>> = libraryCoordinator.ignoredDuplicateKeys
    val deleteRequestIntentSender: StateFlow<IntentSender?> = libraryCoordinator.deleteRequestIntentSender

    val sortedTracks: StateFlow<List<AudioTrack>> = libraryCoordinator.sortedTracks
    val sortedAlbums: StateFlow<List<Album>> = libraryCoordinator.sortedAlbums
    val sortedPlaylists: StateFlow<List<Playlist>> = libraryCoordinator.sortedPlaylists
    val matchingTracks: StateFlow<List<AudioTrack>> = libraryCoordinator.matchingTracks
    val matchingAlbums: StateFlow<List<Album>> = libraryCoordinator.matchingAlbums
    val matchingPlaylists: StateFlow<List<Playlist>> = libraryCoordinator.matchingPlaylists
    val matchingArtists: StateFlow<List<ArtistSearchResult>> = libraryCoordinator.matchingArtists

    // --- StateFlow Riproduzione ---
    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // --- Coordinatori Playback, Timer & Last.fm ---
    val sleepTimerCoordinator = SleepTimerCoordinator(
        scope = viewModelScope,
        onPausePlayback = { mediaController?.pause() },
        onSetVolume = { vol -> mediaController?.setVolume(vol) },
        getCurrentTrack = { _currentTrack.value },
        getCurrentPositionMs = { _currentPositionMs.value },
        getDurationMs = { _durationMs.value }
    )
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerCoordinator.sleepTimerState

    val lastFmCoordinator = LastFmCoordinator(
        context = application,
        scope = viewModelScope,
        getCurrentTrack = { _currentTrack.value },
        isPlayingProvider = { _isPlaying.value }
    )
    val isLastFmLoggedIn: StateFlow<Boolean> = lastFmCoordinator.isLastFmLoggedIn
    val lastFmUsername: StateFlow<String?> = lastFmCoordinator.lastFmUsername
    val lastFmAvatarPath: StateFlow<String?> = lastFmCoordinator.lastFmAvatarPath
    val lastFmAuthLoading: StateFlow<Boolean> = lastFmCoordinator.lastFmAuthLoading
    val lastFmAuthError: StateFlow<String?> = lastFmCoordinator.lastFmAuthError
    val lastFmStatusMessage: StateFlow<String?> = lastFmCoordinator.lastFmStatusMessage
    val lastFmCustomApiKey: StateFlow<String> = lastFmCoordinator.lastFmCustomApiKey
    val lastFmCustomSharedSecret: StateFlow<String> = lastFmCoordinator.lastFmCustomSharedSecret
    val pendingScrobblesCount: StateFlow<Int> = lastFmCoordinator.pendingScrobblesCount

    val queueCoordinator = QueueCoordinator(
        scope = viewModelScope,
        appPreferences = appPreferences,
        getMediaController = { mediaController },
        getTracks = { tracks.value },
        getAlbums = { albums.value },
        getPlaylists = { playlists.value },
        ensurePlaylistsLoaded = { libraryCoordinator.ensurePlaylistsLoaded() },
        getCurrentTrack = { _currentTrack.value },
        setCurrentTrack = { _currentTrack.value = it },
        setCurrentPositionMs = { _currentPositionMs.value = it },
        setIsPlaying = { _isPlaying.value = it },
        startProgressTicker = { startProgressTicker() },
        updateDuration = { updateDuration() },
        onRecordTrackPlayed = { trackId -> libraryCoordinator.recordTrackPlayed(trackId) },
        onLastFmTrackStarted = { track -> lastFmCoordinator.handleTrackStarted(track) },
        onLastFmResetScrobble = { lastFmCoordinator.resetCurrentTrackScrobble() },
        onSavePlaybackState = { saveCurrentPlaybackState() },
        getSleepTimerState = { sleepTimerCoordinator.sleepTimerState.value },
        onCancelSleepTimer = { sleepTimerCoordinator.cancel() }
    )

    val userQueue: StateFlow<List<AudioTrack>> = queueCoordinator.userQueue
    val autoplayQueue: StateFlow<List<AudioTrack>> = queueCoordinator.autoplayQueue
    val queue: StateFlow<List<AudioTrack>> = queueCoordinator.queue
    val currentQueueIndex: StateFlow<Int> = queueCoordinator.currentQueueIndex
    val isShuffleEnabled: StateFlow<Boolean> = queueCoordinator.isShuffleEnabled
    val repeatMode: StateFlow<Int> = queueCoordinator.repeatMode
    val isAutoplayEnabled: StateFlow<Boolean> = queueCoordinator.isAutoplayEnabled
    val playbackContext: StateFlow<PlaybackContext> = queueCoordinator.playbackContext

    // --- Preferenze UI & Aggiornamenti GitHub ---
    private val _themeMode = MutableStateFlow(appPreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(appPreferences.getAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(appPreferences.isDynamicColorEnabled())
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val updateChecker = GithubUpdateChecker()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckError = MutableStateFlow<String?>(null)
    val updateCheckError: StateFlow<String?> = _updateCheckError.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    // --- Riferimenti MediaController ---
    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var isViewModelCleared = false
    private var progressTickerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                if (!queueCoordinator.hasRecordedPlayForRestoredTrack) {
                    queueCoordinator.hasRecordedPlayForRestoredTrack = true
                    _currentTrack.value?.let { current ->
                        libraryCoordinator.recordTrackPlayed(current.id)
                        queueCoordinator.recordRecentTrack(current.id)
                        lastFmCoordinator.handleTrackStarted(current)
                    }
                }
                startProgressTicker()
            } else {
                stopProgressTicker()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            queueCoordinator.onShuffleModeEnabledChanged(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            queueCoordinator.onRepeatModeChanged(repeatMode)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            queueCoordinator.onMediaItemTransition(mediaItem, reason)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            queueCoordinator.updateQueueFromController()
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
                    queueCoordinator.replenishAutoplayIfNeeded()
                }
            }
            queueCoordinator.updateQueueFromController()
        }
    }

    init {
        initAppLanguage()
        initMediaController()
        libraryCoordinator.loadInitialCachedLibrary {
            queueCoordinator.restoreLastPlaybackStateIfPossible()
        }
        libraryCoordinator.loadPlaylists()
        libraryCoordinator.loadStats()
        initLyricsObserver()
    }

    private fun initAppLanguage() {
        try {
            val saved = appPreferences.getAppLanguage()
            val localeManager = getApplication<Application>().getSystemService(LocaleManager::class.java)
            val currentLocales = localeManager?.applicationLocales
            if (currentLocales != null && !currentLocales.isEmpty) {
                val tag = currentLocales[0]?.language ?: ""
                val detected = when {
                    tag.startsWith("en") -> AppLanguage.ENGLISH
                    tag.startsWith("it") -> AppLanguage.ITALIAN
                    else -> AppLanguage.SYSTEM
                }
                _appLanguage.value = detected
            } else if (saved != AppLanguage.SYSTEM) {
                localeManager?.applicationLocales = when (saved) {
                    AppLanguage.ITALIAN -> LocaleList.forLanguageTags("it")
                    AppLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
                    AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                }
                _appLanguage.value = saved
            }
        } catch (_: Exception) {}
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
                queueCoordinator.syncShuffleFromPrefs()
                queueCoordinator.setRepeatModeDirect(controller.repeatMode)

                controller.currentMediaItem?.let { item ->
                    val trackId = item.mediaId.toLongOrNull()
                    _currentTrack.value = tracks.value.find { it.id == trackId }
                }
                updateDuration()
                queueCoordinator.updateQueueFromController()

                if (controller.isPlaying) {
                    startProgressTicker()
                } else if (controller.playbackState == Player.STATE_IDLE && tracks.value.isNotEmpty()) {
                    queueCoordinator.restoreLastPlaybackStateIfPossible()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onAfterLibraryReloaded(loadedTracks: List<AudioTrack>) {
        mediaController?.currentMediaItem?.let { item ->
            val trackId = item.mediaId.toLongOrNull()
            val foundTrack = loadedTracks.find { it.id == trackId }
            _currentTrack.value = foundTrack
            if (foundTrack != null && !lastFmCoordinator.isCurrentScrobbleTrack(trackId ?: -1L) && mediaController?.isPlaying == true) {
                lastFmCoordinator.handleTrackStarted(foundTrack)
            }
        }
        queueCoordinator.updateQueueFromController()
        queueCoordinator.restoreLastPlaybackStateIfPossible()
    }

    fun loadTracks(force: Boolean = false) {
        libraryCoordinator.loadTracks(force) { loadedTracks ->
            onAfterLibraryReloaded(loadedTracks)
        }
    }

    // --- Ordinamento, Ricerca e Playlist ---
    fun setSortOption(option: SortOption) = libraryCoordinator.setSortOption(option)
    fun toggleSortDirection() = libraryCoordinator.toggleSortDirection()
    fun setSortAscending(ascending: Boolean) = libraryCoordinator.setSortAscending(ascending)
    fun onSearchQueryChanged(query: String) = libraryCoordinator.onSearchQueryChanged(query)

    fun loadPlaylists() = libraryCoordinator.loadPlaylists()
    fun createPlaylist(name: String, coverUri: Uri? = null) = libraryCoordinator.createPlaylist(name, coverUri)
    fun updatePlaylistCover(playlistId: String, coverUri: Uri?) = libraryCoordinator.updatePlaylistCover(playlistId, coverUri)
    fun deletePlaylist(playlistId: String) = libraryCoordinator.deletePlaylist(playlistId)
    fun addTrackToPlaylist(playlistId: String, trackId: Long) = libraryCoordinator.addTrackToPlaylist(playlistId, trackId)
    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) = libraryCoordinator.removeTrackFromPlaylist(playlistId, trackId)
    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) =
        libraryCoordinator.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
    fun clearListeningStats() = libraryCoordinator.clearListeningStats()

    // --- Gestione Coda e Riproduzione ---
    fun addToQueue(track: AudioTrack) = queueCoordinator.addToQueue(track)
    fun addTracksToQueue(tracksToAdd: List<AudioTrack>) = queueCoordinator.addTracksToQueue(tracksToAdd)
    fun addAlbumToQueue(album: Album) = queueCoordinator.addAlbumToQueue(album)
    fun addPlaylistToQueue(playlist: Playlist) = queueCoordinator.addPlaylistToQueue(playlist)

    fun removeUserQueueItem(index: Int) = queueCoordinator.removeUserQueueItem(index)
    fun moveUserQueueItem(fromIndex: Int, toIndex: Int) = queueCoordinator.moveUserQueueItem(fromIndex, toIndex)
    fun moveUserQueueItemToTop(fromIndex: Int) = queueCoordinator.moveUserQueueItemToTop(fromIndex)
    fun clearUserQueue() = queueCoordinator.clearUserQueue()
    fun skipToUserQueueItem(index: Int) = queueCoordinator.skipToUserQueueItem(index)

    fun removeAutoplayItem(index: Int) = queueCoordinator.removeAutoplayItem(index)
    fun moveAutoplayItem(fromIndex: Int, toIndex: Int) = queueCoordinator.moveAutoplayItem(fromIndex, toIndex)
    fun moveAutoplayItemToTop(fromIndex: Int) = queueCoordinator.moveAutoplayItemToTop(fromIndex)
    fun moveAutoplayItemToUserQueue(index: Int) = queueCoordinator.moveAutoplayItemToUserQueue(index)
    fun skipToAutoplayItem(index: Int) = queueCoordinator.skipToAutoplayItem(index)

    fun removeFromQueue(index: Int) = queueCoordinator.removeFromQueue(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = queueCoordinator.moveQueueItem(fromIndex, toIndex)
    fun calculateMoveToTopTargetIndex(fromIndex: Int, currentIndex: Int, count: Int): Int? =
        queueCoordinator.calculateMoveToTopTargetIndex(fromIndex, currentIndex, count)
    fun moveQueueItemToTop(fromIndex: Int) = queueCoordinator.moveQueueItemToTop(fromIndex)
    fun clearQueue() = queueCoordinator.clearQueue()
    fun skipToQueueItem(index: Int) = queueCoordinator.skipToQueueItem(index)

    fun playCustomQueue(trackList: List<AudioTrack>, startIndex: Int = 0) =
        queueCoordinator.playCustomQueue(trackList, startIndex)
    fun playAlbum(album: Album, startIndex: Int = 0) = queueCoordinator.playAlbum(album, startIndex)
    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) = queueCoordinator.playPlaylist(playlist, startIndex)
    fun playPlaylist(playlistTracks: List<AudioTrack>, startIndex: Int = 0) =
        queueCoordinator.playPlaylist(playlistTracks, startIndex)
    fun playPlaylist(playlistId: String, playlistName: String, playlistTracks: List<AudioTrack>, startIndex: Int = 0) =
        queueCoordinator.playPlaylist(playlistId, playlistName, playlistTracks, startIndex)
    fun playArtist(artistName: String, startIndex: Int = 0) = queueCoordinator.playArtist(artistName, startIndex)
    fun playTrack(track: AudioTrack) = queueCoordinator.playTrack(track)

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            _isPlaying.value = false
            stopProgressTicker()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && tracks.value.isNotEmpty()) {
                val trackToPlay = _currentTrack.value ?: tracks.value.first()
                queueCoordinator.playTrack(trackToPlay)
            } else {
                controller.play()
                _isPlaying.value = true
                startProgressTicker()
            }
        }
    }

    fun playNext() = queueCoordinator.playNext()
    fun playPrevious() = queueCoordinator.playPrevious(sortOption.value, stats.value, isAscending.value)
    fun toggleShuffle() = queueCoordinator.toggleShuffle()
    fun toggleRepeatMode() = queueCoordinator.toggleRepeatMode()
    fun replenishAutoplayIfNeeded(referenceTrack: AudioTrack? = _currentTrack.value) =
        queueCoordinator.replenishAutoplayIfNeeded(referenceTrack)
    fun toggleAutoplay() = queueCoordinator.toggleAutoplay()
    fun setAutoplayEnabled(enabled: Boolean) = queueCoordinator.setAutoplayEnabled(enabled)

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

    // --- Last.fm ---
    fun saveLastFmApiCredentials(apiKey: String, sharedSecret: String) =
        lastFmCoordinator.saveApiCredentials(apiKey, sharedSecret)
    fun loginLastFm(username: String, password: String, customApiKey: String? = null, customSharedSecret: String? = null) =
        lastFmCoordinator.login(username, password, customApiKey, customSharedSecret)
    fun logoutLastFm() = lastFmCoordinator.logout()
    fun syncOfflineScrobbles() = lastFmCoordinator.syncOfflineScrobbles()
    fun clearLastFmStatusMessage() = lastFmCoordinator.clearStatusMessage()
    fun refreshLastFmAvatar() = lastFmCoordinator.refreshUserAvatar()

    // --- Impostazioni & Aggiornamenti ---
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appPreferences.setThemeMode(mode)
    }

    fun setAppLanguage(language: AppLanguage) {
        if (_appLanguage.value == language) return
        _appLanguage.value = language
        appPreferences.setAppLanguage(language)
        try {
            val localeManager = getApplication<Application>().getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = when (language) {
                AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                AppLanguage.ITALIAN -> LocaleList.forLanguageTags("it")
                AppLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
            }
        } catch (_: Exception) {}
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        _isDynamicColorEnabled.value = enabled
        appPreferences.setDynamicColorEnabled(enabled)
    }

    fun setAudioSourceMode(mode: AudioSourceMode) =
        libraryCoordinator.setAudioSourceMode(mode) { loaded -> onAfterLibraryReloaded(loaded) }
    fun addCustomFolder(folderPath: String) =
        libraryCoordinator.addCustomFolder(folderPath) { loaded -> onAfterLibraryReloaded(loaded) }
    fun removeCustomFolder(folderPath: String) =
        libraryCoordinator.removeCustomFolder(folderPath) { loaded -> onAfterLibraryReloaded(loaded) }

    private var hasCheckedForUpdatesOnStartup = false

    fun checkForUpdates(isManual: Boolean = false) {
        if (!isManual && hasCheckedForUpdatesOnStartup) return
        if (!isManual) {
            hasCheckedForUpdatesOnStartup = true
        }
        viewModelScope.launch {
            if (!isManual) {
                delay(3000L)
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

    // --- Smart Auto-Tagger & Testi ---
    fun startTrackEnrichment(track: AudioTrack) = metadataEnricherCoordinator.startTrackEnrichment(track)
    fun selectAlternativeCandidate(track: AudioTrack, candidate: DeezerSearchTrack, options: EnricherOptions) =
        metadataEnricherCoordinator.selectAlternativeCandidate(track, candidate, options)
    fun searchCustomTrackEnrichment(track: AudioTrack, customTitle: String, customArtist: String) =
        metadataEnricherCoordinator.searchCustomTrackEnrichment(track, customTitle, customArtist)
    fun adjustLyricsOffset(offsetDeltaMs: Long) =
        metadataEnricherCoordinator.adjustLyricsOffset(_currentTrack.value, offsetDeltaMs)
    fun startAlbumEnrichment(album: Album, separateAlbums: Boolean? = null) =
        metadataEnricherCoordinator.startAlbumEnrichment(album, separateAlbums)
    fun toggleAlbumEnrichmentMode(separate: Boolean) = metadataEnricherCoordinator.toggleAlbumEnrichmentMode(separate)
    fun applyTrackEnrichment(track: AudioTrack, metadata: EnrichedMetadata, options: EnricherOptions) =
        metadataEnricherCoordinator.applyTrackEnrichment(track, metadata, options)
    fun applyAlbumEnrichment(album: Album, tracksEnriched: Map<Long, EnrichedMetadata>, options: EnricherOptions) =
        metadataEnricherCoordinator.applyAlbumEnrichment(album, tracksEnriched, options)
    fun onWritePermissionResult(isGranted: Boolean) = metadataEnricherCoordinator.onWritePermissionResult(isGranted)
    fun closeEnricher() = metadataEnricherCoordinator.closeEnricher()

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun handleEnricherPostSave(appliedMetadataMap: Map<Long, EnrichedMetadata>) {
        libraryCoordinator.applyOptimisticMetadataPatch(appliedMetadataMap) { patchedTrack ->
            if (_currentTrack.value?.id == patchedTrack.id) {
                _currentTrack.value = patchedTrack
            }
        }

        withContext(Dispatchers.Main) {
            try {
                val imageLoader = coil.Coil.imageLoader(getApplication<Application>())
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (_: Exception) {}
        }

        libraryCoordinator.reloadLibraryInternal(force = true) { loaded ->
            onAfterLibraryReloaded(loaded)
        }
        libraryCoordinator.loadPlaylists()

        _currentTrack.value?.let { current ->
            metadataEnricherCoordinator.updateCurrentLyricsForTrack(current)
        }
    }

    // --- Eliminazione & Duplicati ---
    fun deleteTracks(tracks: List<AudioTrack>, onCompleted: ((Int) -> Unit)? = null) =
        libraryCoordinator.deleteTracks(tracks, onCompleted)

    fun onDeletePermissionResult(isGranted: Boolean, onDeletedCount: ((Int) -> Unit)? = null) {
        libraryCoordinator.onDeletePermissionResult(
            isGranted = isGranted,
            onDeletedIds = { deletedIds -> queueCoordinator.removeDeletedTracksFromQueue(deletedIds) },
            onDeletedCount = onDeletedCount
        )
    }

    fun ignoreDuplicateGroups(groups: List<DuplicateTrackGroup>) = libraryCoordinator.ignoreDuplicateGroups(groups)
    fun ignoreDuplicateGroup(group: DuplicateTrackGroup) = libraryCoordinator.ignoreDuplicateGroup(group)
    fun restoreIgnoredDuplicatesForAlbum(album: Album) = libraryCoordinator.restoreIgnoredDuplicatesForAlbum(album)
    fun clearAllIgnoredDuplicates() = libraryCoordinator.clearAllIgnoredDuplicates()

    // --- Persistenza Stato Riproduzione & Sleep Timer ---
    fun saveCurrentPlaybackState() {
        val track = _currentTrack.value ?: return
        val pos = _currentPositionMs.value
        val rep = repeatMode.value
        val shuf = isShuffleEnabled.value
        val (cType, cId, cName) = when (val ctx = playbackContext.value) {
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

    fun setSleepTimerMinutes(minutes: Int) = sleepTimerCoordinator.setMinutes(minutes)
    fun setSleepTimerEndOfTrack() = sleepTimerCoordinator.setEndOfTrack()
    fun cancelSleepTimer() = sleepTimerCoordinator.cancel()

    fun exportListeningData(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.exportBackupToUri(uri, tracks.value)
            onResult(result)
        }
    }

    fun importListeningData(uri: Uri, onResult: (Result<ImportSummary>) -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.importBackupFromUri(uri, tracks.value)
            if (result.isSuccess) {
                _themeMode.value = appPreferences.getThemeMode()
                _appLanguage.value = appPreferences.getAppLanguage()
                _isDynamicColorEnabled.value = appPreferences.isDynamicColorEnabled()
                queueCoordinator.setAutoplayEnabled(appPreferences.isAutoplayEnabled())
                libraryCoordinator.refreshAfterBackupImport()
            }
            onResult(result)
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
