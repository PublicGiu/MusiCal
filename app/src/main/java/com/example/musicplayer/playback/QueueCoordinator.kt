package com.example.musicplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AppPreferences
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.PlaybackContext
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.extractMainArtist
import com.example.musicplayer.data.matchesArtist
import com.example.musicplayer.data.sortTracks
import com.example.musicplayer.ui.buildCyclicContextQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinatore per la gestione della coda di riproduzione (Coda Utente, Autoplay 15 brani,
 * timeline ExoPlayer, shuffle/repeat, contesto di riproduzione e ripristino sessione).
 */
class QueueCoordinator(
    private val scope: CoroutineScope,
    private val appPreferences: AppPreferences,
    private val getMediaController: () -> MediaController?,
    private val getTracks: () -> List<AudioTrack>,
    private val getAlbums: () -> List<Album>,
    private val getPlaylists: () -> List<Playlist>,
    private val ensurePlaylistsLoaded: suspend () -> List<Playlist>,
    private val getCurrentTrack: () -> AudioTrack?,
    private val setCurrentTrack: (AudioTrack?) -> Unit,
    private val setCurrentPositionMs: (Long) -> Unit,
    private val setIsPlaying: (Boolean) -> Unit,
    private val startProgressTicker: () -> Unit,
    private val updateDuration: () -> Unit,
    private val onRecordTrackPlayed: (Long) -> Unit,
    private val onLastFmTrackStarted: (AudioTrack) -> Unit,
    private val onLastFmResetScrobble: () -> Unit,
    private val onSavePlaybackState: () -> Unit,
    private val getSleepTimerState: () -> SleepTimerState,
    private val onCancelSleepTimer: () -> Unit
) {
    private val _userQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val userQueue: StateFlow<List<AudioTrack>> = _userQueue.asStateFlow()

    private val _autoplayQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val autoplayQueue: StateFlow<List<AudioTrack>> = _autoplayQueue.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isAutoplayEnabled = MutableStateFlow(appPreferences.isAutoplayEnabled())
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _playbackContext = MutableStateFlow<PlaybackContext>(PlaybackContext.Library)
    val playbackContext: StateFlow<PlaybackContext> = _playbackContext.asStateFlow()

    private val recentPlayedTrackIds = ArrayDeque<Long>()
    private var activeContextTracks: List<AudioTrack> = emptyList()
    private var isCustomContextActive: Boolean = false
    var lastKnownQueueIndex: Int = 0
        private set

    private var isPlaybackStateRestored: Boolean = false
    private var isRestoringPlaybackState: Boolean = false
    var hasRecordedPlayForRestoredTrack: Boolean = true

    fun syncShuffleFromPrefs() {
        _isShuffleEnabled.value = appPreferences.getLastShuffleEnabled()
    }

    fun setRepeatModeDirect(mode: Int) {
        _repeatMode.value = mode
    }

    fun setShuffleEnabledDirect(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
    }

    fun updateQueueFromController() {
        val controller = getMediaController() ?: return
        val count = controller.mediaItemCount
        val queueItems = mutableListOf<AudioTrack>()
        val allTracks = getTracks()

        for (i in 0 until count) {
            val item = controller.getMediaItemAt(i)
            val trackId = item.mediaId.toLongOrNull()
            val track = allTracks.find { it.id == trackId } ?: AudioTrack(
                id = trackId ?: i.toLong(),
                title = item.mediaMetadata.title?.toString() ?: "Sconosciuto",
                artist = item.mediaMetadata.artist?.toString() ?: "Sconosciuto",
                duration = 0L,
                uri = item.requestMetadata.mediaUri ?: Uri.EMPTY,
                albumArtUri = item.mediaMetadata.artworkUri
            )
            queueItems.add(track)
        }
        _queue.value = queueItems
        _currentQueueIndex.value = controller.currentMediaItemIndex
    }

    fun trackToMediaItem(track: AudioTrack): MediaItem {
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

    fun addToQueue(track: AudioTrack) {
        addTracksToQueue(listOf(track))
    }

    fun addTracksToQueue(tracksToAdd: List<AudioTrack>) {
        if (tracksToAdd.isEmpty()) return
        val controller = getMediaController() ?: return
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

    fun addAlbumToQueue(album: Album) {
        val sortedTracks = album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
        addTracksToQueue(sortedTracks)
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        val trackMap = getTracks().associateBy { it.id }
        val playlistTracks = playlist.trackIds.mapNotNull { trackMap[it] }
        addTracksToQueue(playlistTracks)
    }

    // --- Coda Utente ---
    fun removeUserQueueItem(index: Int) {
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
        val currentIndex = controller.currentMediaItemIndex
        val targetIndex = currentIndex + 1 + index
        if (targetIndex in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(targetIndex)
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
        }
    }

    // --- Riproduzione Automatica ---
    fun removeAutoplayItem(index: Int) {
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
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

    fun moveAutoplayItemToUserQueue(index: Int) {
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
        val currentIndex = controller.currentMediaItemIndex
        val targetIndex = currentIndex + 1 + _userQueue.value.size + index
        if (targetIndex in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(targetIndex)
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
        }
    }

    // --- Metodi Generali ---
    fun removeFromQueue(index: Int) {
        val controller = getMediaController() ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            updateQueueFromController()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = getMediaController() ?: return
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
        val controller = getMediaController() ?: return
        val count = controller.mediaItemCount
        val targetIndex = calculateMoveToTopTargetIndex(
            fromIndex = fromIndex,
            currentIndex = controller.currentMediaItemIndex,
            count = count
        ) ?: return
        moveQueueItem(fromIndex, targetIndex)
    }

    fun clearQueue() {
        val controller = getMediaController() ?: return
        controller.clearMediaItems()
        activeContextTracks = emptyList()
        isCustomContextActive = false
        setCurrentTrack(null)
        _queue.value = emptyList()
        _userQueue.value = emptyList()
        _autoplayQueue.value = emptyList()
        _currentQueueIndex.value = 0
    }

    fun skipToQueueItem(index: Int) {
        val controller = getMediaController() ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
        }
    }

    private fun getRepeatContextTracks(referenceTrack: AudioTrack?): List<AudioTrack> {
        if (activeContextTracks.isNotEmpty() && (referenceTrack == null || activeContextTracks.any { it.id == referenceTrack.id })) {
            return activeContextTracks
        }
        if (referenceTrack != null) {
            val albums = getAlbums()
            val matchingAlbum = albums.find { it.id == referenceTrack.albumId }
                ?: albums.find { alb -> alb.tracks.any { it.id == referenceTrack.id } }
                ?: albums.find { it.title.equals(referenceTrack.album, ignoreCase = true) }
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
        val controller = getMediaController() ?: return
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
                    val candidates = getTracks().filter { it.id !in excludeIds }
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
        setCurrentTrack(current)
        setCurrentPositionMs(0L)
        setIsPlaying(true)
        startProgressTicker()
        updateQueueFromController()
        onSavePlaybackState()
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
        val trackMap = getTracks().associateBy { it.id }
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
        val artistTracks = getTracks().filter { it.matchesArtist(artistName) }
        if (artistTracks.isEmpty()) return
        val displayArtist = extractMainArtist(artistName).ifBlank { artistName }
        _playbackContext.value = PlaybackContext.Artist(displayArtist)
        playCustomQueue(artistTracks, startIndex)
    }

    fun playTrack(track: AudioTrack) {
        val controller = getMediaController() ?: return
        val currentQueueSnapshot = _queue.value
        val currentIdxSnapshot = _currentQueueIndex.value

        isRestoringPlaybackState = false
        hasRecordedPlayForRestoredTrack = true
        _playbackContext.value = PlaybackContext.Library
        activeContextTracks = emptyList()
        isCustomContextActive = false

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
        setCurrentTrack(track)
        setCurrentPositionMs(0L)
        setIsPlaying(true)
        startProgressTicker()
        updateQueueFromController()
        onSavePlaybackState()
    }

    fun playNext() {
        getMediaController()?.let { controller ->
            if (controller.repeatMode == Player.REPEAT_MODE_ONE) {
                controller.seekTo(0L)
                setCurrentPositionMs(0L)
                getCurrentTrack()?.let { onLastFmTrackStarted(it) }
            } else if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            }
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
        }
    }

    fun playPrevious(sortOption: SortOption, stats: Map<Long, TrackStats>, isAscending: Boolean) {
        val controller = getMediaController() ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex > 0) {
            controller.seekToDefaultPosition(currentIndex - 1)
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
            return
        }

        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
            return
        }

        val current = getCurrentTrack()
        val currentSorted = getTracks().sortTracks(sortOption, stats, isAscending)
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
            setIsPlaying(true)
            startProgressTicker()
            updateQueueFromController()
        } else {
            controller.seekTo(0L)
            setCurrentPositionMs(0L)
            controller.play()
            setIsPlaying(true)
            startProgressTicker()
        }
    }

    fun toggleShuffle() {
        val controller = getMediaController() ?: return
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        controller.shuffleModeEnabled = false
        rebuildAutoplayForCurrentMode()
        onSavePlaybackState()
    }

    fun toggleRepeatMode() {
        val controller = getMediaController() ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
        rebuildAutoplayForCurrentMode()
        onSavePlaybackState()
    }

    fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) {
            getMediaController()?.shuffleModeEnabled = false
            _isShuffleEnabled.value = !_isShuffleEnabled.value
            rebuildAutoplayForCurrentMode()
            onSavePlaybackState()
        }
    }

    fun onRepeatModeChanged(repeatMode: Int) {
        val changed = _repeatMode.value != repeatMode
        _repeatMode.value = repeatMode
        if (changed) {
            rebuildAutoplayForCurrentMode()
            onSavePlaybackState()
        }
    }

    fun rebuildAutoplayForCurrentMode() {
        val controller = getMediaController() ?: return
        val current = getCurrentTrack() ?: return

        _autoplayQueue.value = emptyList()

        val isShuffled = _isShuffleEnabled.value
        val newAutoplay = when (_repeatMode.value) {
            Player.REPEAT_MODE_ALL -> {
                val contextTracks = getRepeatContextTracks(current)
                buildCyclicContextQueue(contextTracks, current, count = 15, isShuffled = isShuffled)
            }
            Player.REPEAT_MODE_ONE -> List(15) { current }
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
                        val allTracks = getTracks()
                        val excludeIds = (activeContextTracks.map { it.id } + _userQueue.value.map { it.id } + recentPlayedTrackIds + current.id).toSet()
                        val candidates = allTracks.filter { it.id !in excludeIds }
                            .ifEmpty { allTracks.filter { it.id != current.id } }
                        candidates.shuffled().take(needed)
                    } else {
                        emptyList()
                    }
                    (orderedUpcoming + autoplaySupplement).take(15)
                } else if (_isAutoplayEnabled.value) {
                    selectAutoplayTracks(referenceTrack = current, count = 15)
                } else {
                    emptyList()
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

    fun recordRecentTrack(trackId: Long) {
        recentPlayedTrackIds.remove(trackId)
        recentPlayedTrackIds.addLast(trackId)
        while (recentPlayedTrackIds.size > 30) {
            recentPlayedTrackIds.removeFirst()
        }
    }

    private fun selectAutoplayTracks(referenceTrack: AudioTrack?, count: Int = 15): List<AudioTrack> {
        val allTracks = getTracks()
        if (allTracks.isEmpty()) return emptyList()

        val queuedIds = (_userQueue.value.map { it.id } + _autoplayQueue.value.map { it.id }).toSet()
        val currentId = (getCurrentTrack()?.id ?: referenceTrack?.id)?.let { setOf(it) } ?: emptySet()
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

    fun replenishAutoplayIfNeeded(referenceTrack: AudioTrack? = getCurrentTrack()) {
        val controller = getMediaController() ?: return
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
                val target = referenceTrack ?: getCurrentTrack()
                if (target != null) List(needed) { target } else emptyList()
            }
            else -> {
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

    fun toggleAutoplay() {
        setAutoplayEnabled(!_isAutoplayEnabled.value)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        appPreferences.setAutoplayEnabled(enabled)
        if (enabled) {
            replenishAutoplayIfNeeded()
        } else if (_repeatMode.value == Player.REPEAT_MODE_OFF) {
            rebuildAutoplayForCurrentMode()
        }
    }

    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val controller = getMediaController()
        mediaItem?.let { item ->
            val prevIndex = lastKnownQueueIndex
            val newIndex = controller?.currentMediaItemIndex ?: 0
            val isSteppingBack = newIndex < prevIndex
            val stepsForward = newIndex - prevIndex
            lastKnownQueueIndex = newIndex

            val trackId = item.mediaId.toLongOrNull()
            val allTracks = getTracks()
            val foundTrack = allTracks.find { it.id == trackId }
            val current = foundTrack ?: AudioTrack(
                id = trackId ?: 0L,
                title = item.mediaMetadata.title?.toString() ?: "Sconosciuto",
                artist = item.mediaMetadata.artist?.toString() ?: "Sconosciuto",
                duration = 0L,
                uri = item.requestMetadata.mediaUri ?: Uri.EMPTY,
                albumArtUri = item.mediaMetadata.artworkUri
            )
            val previousTrackId = getCurrentTrack()?.id
            setCurrentTrack(current)

            if (isRestoringPlaybackState) {
                isRestoringPlaybackState = false
                updateDuration()
                updateQueueFromController()
                return
            }

            val timer = getSleepTimerState()
            if (timer is SleepTimerState.Active && timer.isEndOfTrack && previousTrackId != null) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    onCancelSleepTimer()
                    controller?.pause()
                    hasRecordedPlayForRestoredTrack = false
                    onSavePlaybackState()
                    updateDuration()
                    updateQueueFromController()
                    return
                } else {
                    controller?.setVolume(1.0f)
                }
            }

            hasRecordedPlayForRestoredTrack = true
            trackId?.let { id ->
                onRecordTrackPlayed(id)
                recordRecentTrack(id)
            }
            onLastFmTrackStarted(current)
            onSavePlaybackState()

            if (_repeatMode.value == Player.REPEAT_MODE_ONE && previousTrackId != null && previousTrackId != current.id) {
                rebuildAutoplayForCurrentMode()
            } else if (isSteppingBack && controller != null) {
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
                        uri = mItem.requestMetadata.mediaUri ?: Uri.EMPTY,
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
            setCurrentTrack(null)
            onLastFmResetScrobble()
            lastKnownQueueIndex = 0
        }
        updateDuration()
        updateQueueFromController()
    }

    fun restoreLastPlaybackStateIfPossible() {
        val controller = getMediaController() ?: return
        if (isPlaybackStateRestored || controller.playbackState != Player.STATE_IDLE || getCurrentTrack() != null) {
            return
        }
        val allTracks = getTracks()
        if (allTracks.isEmpty()) return

        val lastTrackId = appPreferences.getLastPlayedTrackId()
        if (lastTrackId == -1L) return
        val track = allTracks.find { it.id == lastTrackId } ?: return

        val cType = appPreferences.getLastContextType()
        if (cType == "PLAYLIST" && getPlaylists().isEmpty()) {
            scope.launch(Dispatchers.IO) {
                ensurePlaylistsLoaded()
                withContext(Dispatchers.Main) {
                    restoreLastPlaybackStateIfPossible()
                }
            }
            return
        }

        isPlaybackStateRestored = true
        isRestoringPlaybackState = true
        hasRecordedPlayForRestoredTrack = false

        val lastRepeat = appPreferences.getLastRepeatMode()
        val lastShuffle = appPreferences.getLastShuffleEnabled()
        _repeatMode.value = lastRepeat
        _isShuffleEnabled.value = lastShuffle
        controller.repeatMode = lastRepeat
        controller.shuffleModeEnabled = false

        val cId = appPreferences.getLastContextId()
        val cName = appPreferences.getLastContextName()
        when (cType) {
            "ALBUM" -> {
                val albumId = cId?.toLongOrNull()
                val albums = getAlbums()
                val alb = albums.find { it.id == albumId } ?: albums.find { it.tracks.any { t -> t.id == track.id } }
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
                val currentPlaylists = getPlaylists()
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
        setCurrentTrack(track)
        setCurrentPositionMs(posMs)
        setIsPlaying(false)
        updateDuration()
        updateQueueFromController()
    }

    fun removeDeletedTracksFromQueue(deletedIds: Set<Long>) {
        if (getCurrentTrack()?.id in deletedIds) {
            playNext()
        }
        val currentQueue = _queue.value
        if (currentQueue.any { it.id in deletedIds }) {
            _queue.value = currentQueue.filter { it.id !in deletedIds }
        }
    }
}
