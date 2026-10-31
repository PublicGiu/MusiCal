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
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.StatsRepository
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.groupTracksIntoAlbums
import com.example.musicplayer.data.loadAudioFiles
import com.example.musicplayer.data.lastfm.LastFmClient
import com.example.musicplayer.data.lastfm.LastFmConfig
import com.example.musicplayer.data.lastfm.LastFmSessionManager
import com.example.musicplayer.data.lastfm.cache.ScrobbleSyncManager
import com.example.musicplayer.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository(application)
    private val statsRepository = StatsRepository(application)

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

    // --- 4. Last.fm Scrobbling & Offline Cache ---
    private val lastFmSessionManager = LastFmSessionManager(application)
    private val lastFmClient = LastFmClient()
    private val syncManager = ScrobbleSyncManager(application, lastFmClient, lastFmSessionManager)

    private val _isLastFmLoggedIn = MutableStateFlow(lastFmSessionManager.isLoggedIn())
    val isLastFmLoggedIn: StateFlow<Boolean> = _isLastFmLoggedIn.asStateFlow()

    private val _lastFmUsername = MutableStateFlow(lastFmSessionManager.getUsername())
    val lastFmUsername: StateFlow<String?> = _lastFmUsername.asStateFlow()

    private val _lastFmAuthLoading = MutableStateFlow(false)
    val lastFmAuthLoading: StateFlow<Boolean> = _lastFmAuthLoading.asStateFlow()

    private val _lastFmAuthError = MutableStateFlow<String?>(null)
    val lastFmAuthError: StateFlow<String?> = _lastFmAuthError.asStateFlow()

    private val _lastFmStatusMessage = MutableStateFlow<String?>(null)
    val lastFmStatusMessage: StateFlow<String?> = _lastFmStatusMessage.asStateFlow()

    val pendingScrobblesCount: StateFlow<Int> = syncManager.pendingCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- 5. Preferenze dell'applicazione (Tema, Colori dinamici, Autoplay) ---
    private val appPreferences = AppPreferences(application)

    private val _themeMode = MutableStateFlow(appPreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDynamicColorEnabled = MutableStateFlow(appPreferences.isDynamicColorEnabled())
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _isAutoplayEnabled = MutableStateFlow(appPreferences.isAutoplayEnabled())
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val recentPlayedTrackIds = ArrayDeque<Long>()

    // Tracciamento stato scrobble per il brano corrente
    private var currentTrackStartTimeSeconds: Long = 0L
    private var scrobbleSentForCurrentTrack: Boolean = false
    private var currentScrobbleTrackId: Long? = null

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
                startProgressTicker()
            } else {
                stopProgressTicker()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
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
                _currentTrack.value = current
                // Registra la statistica d'ascolto della traccia avviata
                trackId?.let { id ->
                    recordTrackPlayed(id)
                    recordRecentTrack(id)
                }
                handleTrackStartedForScrobble(current)

                // Gestione consumo Coda Utente vs Riproduzione Automatica
                val userList = _userQueue.value
                val userIndex = userList.indexOfFirst { it.id == current.id }
                if (userIndex != -1) {
                    _userQueue.value = userList.drop(userIndex + 1)
                } else {
                    val autoList = _autoplayQueue.value
                    val autoIndex = autoList.indexOfFirst { it.id == current.id }
                    if (autoIndex != -1) {
                        _autoplayQueue.value = autoList.drop(autoIndex + 1)
                    }
                }
                replenishAutoplayIfNeeded(current)
            } ?: run {
                _currentTrack.value = null
                currentScrobbleTrackId = null
                scrobbleSentForCurrentTrack = false
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
                checkAndTriggerAutoplayIfNeeded()
            }
            updateQueueFromController()
        }
    }

    init {
        initMediaController()
        loadPlaylists()
        loadStats()
        initOfflineScrobbleSync()
    }

    private fun initOfflineScrobbleSync() {
        syncManager.startMonitoring()
        syncManager.onSyncCompletedListener = { syncedCount ->
            Log.d("LastFm", "Sincronizzati $syncedCount scrobble dalla cache offline")
        }
        viewModelScope.launch(Dispatchers.IO) {
            syncManager.syncPendingScrobbles()
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
                _isShuffleEnabled.value = controller.shuffleModeEnabled
                _repeatMode.value = controller.repeatMode

                controller.currentMediaItem?.let { item ->
                    val trackId = item.mediaId.toLongOrNull()
                    _currentTrack.value = _tracks.value.find { it.id == trackId }
                }
                updateDuration()
                updateQueueFromController()

                if (controller.isPlaying) {
                    startProgressTicker()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun loadTracks() {
        viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            val (loadedTracks, groupedAlbums) = withContext(Dispatchers.IO) {
                val tracks = loadAudioFiles(getApplication())
                val albums = groupTracksIntoAlbums(tracks)
                tracks to albums
            }
            _tracks.value = loadedTracks
            _albums.value = groupedAlbums

            mediaController?.currentMediaItem?.let { item ->
                val trackId = item.mediaId.toLongOrNull()
                val foundTrack = loadedTracks.find { it.id == trackId }
                _currentTrack.value = foundTrack
                if (foundTrack != null && currentScrobbleTrackId != trackId && mediaController?.isPlaying == true) {
                    handleTrackStartedForScrobble(foundTrack)
                }
            }
            updateQueueFromController()
            _isLoading.value = false
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

    fun skipToAutoplayItem(index: Int) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val targetIndex = currentIndex + 1 + _userQueue.value.size + index
        if (targetIndex in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(targetIndex)
            controller.play()
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
        }
    }

    fun playCustomQueue(trackList: List<AudioTrack>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (trackList.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, trackList.size - 1)
        val current = trackList[safeIndex]
        _userQueue.value = emptyList()

        val upcomingInList = trackList.subList(safeIndex + 1, trackList.size)
        val needed = 15 - upcomingInList.size
        val autoplaySupplement = if (_isAutoplayEnabled.value && needed > 0) {
            val excludeIds = (trackList.map { it.id } + recentPlayedTrackIds).toSet()
            val candidates = _tracks.value.filter { it.id !in excludeIds }
            candidates.shuffled().take(needed)
        } else {
            emptyList()
        }

        val initialAutoplay = (upcomingInList + autoplaySupplement).take(15)
        _autoplayQueue.value = initialAutoplay

        val allItems = (listOf(current) + initialAutoplay).map { trackToMediaItem(it) }
        controller.setMediaItems(allItems, 0, 0L)
        controller.prepare()
        controller.play()
        _currentTrack.value = current
        updateQueueFromController()
    }

    fun playAlbum(album: Album, startIndex: Int = 0) {
        val albumSongs = album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
        playCustomQueue(albumSongs, startIndex)
    }

    fun playPlaylist(playlistTracks: List<AudioTrack>, startIndex: Int = 0) {
        playCustomQueue(playlistTracks, startIndex)
    }

    fun playTrack(track: AudioTrack) {
        val controller = mediaController ?: return
        _userQueue.value = emptyList()

        val autoplayCandidates = if (_isAutoplayEnabled.value) {
            selectAutoplayTracks(referenceTrack = track, count = 15)
        } else {
            emptyList()
        }
        _autoplayQueue.value = autoplayCandidates

        val allItems = (listOf(track) + autoplayCandidates).map { trackToMediaItem(it) }
        controller.setMediaItems(allItems, 0, 0L)
        controller.prepare()
        controller.play()
        _currentTrack.value = track
        updateQueueFromController()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _tracks.value.isNotEmpty()) {
                val trackToPlay = _currentTrack.value ?: _tracks.value.first()
                playTrack(trackToPlay)
            } else {
                controller.play()
            }
        }
    }

    fun playNext() {
        mediaController?.let { controller ->
            if (controller.repeatMode == Player.REPEAT_MODE_ONE) {
                controller.seekTo(0L)
                _currentPositionMs.value = 0L
                _currentTrack.value?.let { handleTrackStartedForScrobble(it) }
            } else if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            }
            controller.play()
        }
    }

    fun playPrevious() {
        mediaController?.let { controller ->
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekTo(0L)
            }
            controller.play()
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _isShuffleEnabled.value = newShuffle
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

                    checkAndTriggerScrobble(pos, effectiveDur)
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
    }

    // --- Scrobbling & Now Playing Logic ---
    private fun handleTrackStartedForScrobble(track: AudioTrack) {
        currentScrobbleTrackId = track.id
        currentTrackStartTimeSeconds = System.currentTimeMillis() / 1000
        scrobbleSentForCurrentTrack = false

        val sessionKey = lastFmSessionManager.getSessionKey()
        if (!sessionKey.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val result = lastFmClient.updateNowPlaying(track, sessionKey)
                result.onSuccess {
                    Log.d("LastFm", "Now Playing inviato con successo: ${track.title} - ${track.artist}")
                }.onFailure { e ->
                    Log.d("LastFm", "Errore invio Now Playing (${track.title}): ${e.message}")
                }
            }
        }
    }

    private fun checkAndTriggerScrobble(positionMs: Long, durationMs: Long) {
        if (scrobbleSentForCurrentTrack) return
        val track = _currentTrack.value ?: return
        val sessionKey = lastFmSessionManager.getSessionKey() ?: return

        val trackDurMs = if (durationMs > 0L) durationMs else track.duration
        if (trackDurMs < 30_000L) return

        val thresholdMs = minOf(trackDurMs / 2, 240_000L)
        if (positionMs >= thresholdMs) {
            scrobbleSentForCurrentTrack = true
            val startTime = currentTrackStartTimeSeconds
            viewModelScope.launch(Dispatchers.IO) {
                if (!syncManager.isNetworkAvailable()) {
                    // Dispositivo offline: salva nella tabella Room dei pendenti
                    syncManager.savePendingScrobble(track, startTime)
                    Log.d("LastFm", "Dispositivo offline: scrobble salvato in cache Room per ${track.title} - ${track.artist}")
                } else {
                    // Dispositivo online: tenta l'invio immediato
                    val result = lastFmClient.scrobble(track, startTime, sessionKey)
                    result.onSuccess {
                        Log.d("LastFm", "Ascolto registrato su Last.fm con successo: ${track.title} - ${track.artist}")
                    }.onFailure { e ->
                        // In caso di errore di rete: salva nei pendenti anziché scartarlo
                        syncManager.savePendingScrobble(track, startTime)
                        Log.d("LastFm", "Errore invio scrobble (${e.message}): salvato in cache offline Room per ${track.title}")
                    }
                }
            }
        }
    }

    // --- Metodi Pubblici Last.fm ---
    fun loginLastFm(username: String, password: String) {
        if (!LastFmConfig.isConfigured) {
            _lastFmAuthError.value = "Credenziali Last.fm non configurate. Aggiungile in local.properties."
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _lastFmAuthError.value = "Inserisci username e password"
            return
        }
        viewModelScope.launch(Dispatchers.Main) {
            _lastFmAuthLoading.value = true
            _lastFmAuthError.value = null
            val result = lastFmClient.getMobileSession(username, password)
            result.onSuccess { (user, sessionKey) ->
                lastFmSessionManager.saveSession(user, sessionKey)
                _isLastFmLoggedIn.value = true
                _lastFmUsername.value = user
                _lastFmAuthLoading.value = false
                _lastFmStatusMessage.value = "Connesso a Last.fm come $user"

                _currentTrack.value?.let { current ->
                    if (_isPlaying.value) {
                        handleTrackStartedForScrobble(current)
                    }
                }

                // Sincronizza eventuali scrobble offline accumulati in precedenza
                viewModelScope.launch(Dispatchers.IO) {
                    syncManager.syncPendingScrobbles()
                }
            }.onFailure { error ->
                _lastFmAuthLoading.value = false
                _lastFmAuthError.value = error.message ?: "Errore durante l'accesso a Last.fm"
            }
        }
    }

    fun logoutLastFm() {
        lastFmSessionManager.clearSession()
        _isLastFmLoggedIn.value = false
        _lastFmUsername.value = null
        _lastFmAuthError.value = null
        _lastFmStatusMessage.value = "Disconnesso da Last.fm"
    }

    fun syncOfflineScrobbles() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = syncManager.syncPendingScrobbles()
            if (count > 0) {
                _lastFmStatusMessage.value = "Sincronizzati $count scrobble offline"
            } else if (!syncManager.isNetworkAvailable()) {
                _lastFmStatusMessage.value = "Connessione assente: scrobble mantenuti in coda"
            } else {
                _lastFmStatusMessage.value = "Nessuno scrobble in attesa"
            }
        }
    }

    fun clearLastFmStatusMessage() {
        _lastFmStatusMessage.value = null
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
        if (!_isAutoplayEnabled.value) return
        val currentSize = _autoplayQueue.value.size
        val needed = 15 - currentSize
        if (needed <= 0) return

        val newTracks = selectAutoplayTracks(referenceTrack, count = needed)
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

    override fun onCleared() {
        super.onCleared()
        isViewModelCleared = true
        syncManager.stopMonitoring()
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
