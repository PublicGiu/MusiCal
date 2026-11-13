package com.example.musicplayer.data

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinatore per la gestione della libreria musicale (MediaStore, cache instant-on,
 * album, playlist, statistiche, ricerca globale, ordinamento, duplicati ed eliminazione file).
 */
class LibraryCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val appPreferences: AppPreferences,
    private val artistInfoRepository: ArtistInfoRepository,
    private val playlistRepository: PlaylistRepository,
    private val statsRepository: StatsRepository,
    private val libraryCacheRepository: LibraryCacheRepository,
    private val createDeleteRequestIntentSender: (List<Uri>) -> IntentSender?
) {
    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _stats = MutableStateFlow<Map<Long, TrackStats>>(emptyMap())
    val stats: StateFlow<Map<Long, TrackStats>> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_PLAYED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _audioSourceMode = MutableStateFlow(appPreferences.getAudioSourceMode())
    val audioSourceMode: StateFlow<AudioSourceMode> = _audioSourceMode.asStateFlow()

    private val _customFolders = MutableStateFlow(appPreferences.getCustomFolders())
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _ignoredDuplicateKeys = MutableStateFlow(appPreferences.getIgnoredDuplicateKeys())
    val ignoredDuplicateKeys: StateFlow<Set<String>> = _ignoredDuplicateKeys.asStateFlow()

    private val _deleteRequestIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteRequestIntentSender: StateFlow<IntentSender?> = _deleteRequestIntentSender.asStateFlow()
    private var pendingTracksToDelete: List<AudioTrack> = emptyList()

    val sortedTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks, _sortOption, _stats, _isAscending
    ) { tracks, sortOption, stats, isAscending ->
        tracks.sortTracks(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedAlbums: StateFlow<List<Album>> = combine(
        _albums, _sortOption, _stats, _isAscending
    ) { albums, sortOption, stats, isAscending ->
        albums.sortAlbums(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedPlaylists: StateFlow<List<Playlist>> = combine(
        _playlists, _sortOption, _stats, _isAscending
    ) { playlists, sortOption, stats, isAscending ->
        playlists.sortPlaylists(sortOption, stats, isAscending)
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks, _searchQuery
    ) { tracks, query ->
        if (query.isBlank()) emptyList()
        else tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingAlbums: StateFlow<List<Album>> = combine(
        _albums, _searchQuery
    ) { albums, query ->
        if (query.isBlank()) emptyList()
        else albums.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchingPlaylists: StateFlow<List<Playlist>> = combine(
        _playlists, _searchQuery
    ) { playlists, query ->
        if (query.isBlank()) emptyList()
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery.debounce(150L)

    val matchingArtists: StateFlow<List<ArtistSearchResult>> = combine(
        _tracks, _albums, debouncedSearchQuery
    ) { tracks, albums, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            artistInfoRepository.ensureCacheLoaded()
            val distinctArtists = tracks.map { it.artist.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .filter { artist ->
                    artist.contains(trimmed, ignoreCase = true) ||
                        extractMainArtist(artist).contains(trimmed, ignoreCase = true)
                }

            distinctArtists.map { artistName ->
                val artistTracks = tracks.filter { it.matchesArtist(artistName) }
                val artistAlbums = albums.filter { it.matchesArtist(artistName) }
                val cached = artistInfoRepository.getCachedArtistInfo(artistName)
                val fallbackUri = artistTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
                ArtistSearchResult(
                    name = artistName,
                    trackCount = artistTracks.size,
                    albumCount = artistAlbums.size,
                    photoUrl = cached?.photoUrl,
                    fallbackCoverUri = fallbackUri
                )
            }.sortedByDescending { it.trackCount }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadInitialCachedLibrary(onLoaded: () -> Unit) {
        scope.launch(Dispatchers.IO) {
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
                            onLoaded()
                        }
                    }
                }
            }
        }
    }

    suspend fun reloadLibraryInternal(
        force: Boolean = false,
        onAfterLoad: (List<AudioTrack>) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            if (_tracks.value.isEmpty() || force) {
                _isLoading.value = true
            }
        }
        val currentMode = _audioSourceMode.value
        val currentCustomFolders = _customFolders.value
        val (loadedTracks, groupedAlbums) = withContext(Dispatchers.IO) {
            val tracks = loadAudioFiles(application, currentMode, currentCustomFolders)
            val albums = groupTracksIntoAlbums(tracks)
            tracks to albums
        }

        withContext(Dispatchers.Main) {
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

            onAfterLoad(loadedTracks)
            _isLoading.value = false
        }
    }

    fun loadTracks(force: Boolean = false, onAfterLoad: (List<AudioTrack>) -> Unit) {
        scope.launch {
            reloadLibraryInternal(force, onAfterLoad)
        }
    }

    // --- Statistiche ---
    fun loadStats() {
        scope.launch(Dispatchers.IO) {
            _stats.value = statsRepository.loadStats()
        }
    }

    fun recordTrackPlayed(trackId: Long) {
        scope.launch(Dispatchers.IO) {
            statsRepository.recordTrackPlayed(trackId)
            _stats.value = statsRepository.loadStats()
        }
    }

    fun clearListeningStats() {
        scope.launch(Dispatchers.IO) {
            statsRepository.clearStats()
            withContext(Dispatchers.Main) {
                _stats.value = emptyMap()
            }
        }
    }

    // --- Ordinamento e Ricerca ---
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

    // --- Playlist ---
    fun loadPlaylists() {
        scope.launch(Dispatchers.IO) {
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    suspend fun ensurePlaylistsLoaded(): List<Playlist> {
        val current = _playlists.value
        if (current.isNotEmpty()) return current
        val loaded = withContext(Dispatchers.IO) {
            playlistRepository.loadPlaylists()
        }
        withContext(Dispatchers.Main) {
            if (_playlists.value.isEmpty()) {
                _playlists.value = loaded
            }
        }
        return _playlists.value.ifEmpty { loaded }
    }

    fun createPlaylist(name: String, coverUri: Uri? = null) {
        scope.launch(Dispatchers.IO) {
            playlistRepository.createPlaylistWithSourceUri(name, coverUri)
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    fun updatePlaylistCover(playlistId: String, coverUri: Uri?) {
        scope.launch(Dispatchers.IO) {
            playlistRepository.updatePlaylistCoverFromUri(playlistId, coverUri)
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    fun deletePlaylist(playlistId: String) {
        scope.launch(Dispatchers.IO) {
            playlistRepository.deletePlaylist(playlistId)
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        scope.launch(Dispatchers.IO) {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        scope.launch(Dispatchers.IO) {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            _playlists.value = playlistRepository.loadPlaylists()
        }
    }

    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.IO) {
            val success = playlistRepository.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
            if (success) {
                val updated = playlistRepository.loadPlaylists()
                withContext(Dispatchers.Main) {
                    _playlists.value = updated
                }
            }
        }
    }

    // --- Sorgenti Audio & Cartelle ---
    fun setAudioSourceMode(mode: AudioSourceMode, onAfterLoad: (List<AudioTrack>) -> Unit) {
        if (_audioSourceMode.value != mode) {
            _audioSourceMode.value = mode
            appPreferences.setAudioSourceMode(mode)
            loadTracks(force = true, onAfterLoad = onAfterLoad)
        }
    }

    fun addCustomFolder(folderPath: String, onAfterLoad: (List<AudioTrack>) -> Unit) {
        val clean = folderPath.trim().trimStart('/')
        val normalized = if (clean.endsWith('/')) clean else "$clean/"
        val updated = _customFolders.value.toMutableSet()
        if (updated.add(normalized)) {
            _customFolders.value = updated
            appPreferences.setCustomFolders(updated)
            if (_audioSourceMode.value == AudioSourceMode.CUSTOM_FOLDERS) {
                loadTracks(force = true, onAfterLoad = onAfterLoad)
            }
        }
    }

    fun removeCustomFolder(folderPath: String, onAfterLoad: (List<AudioTrack>) -> Unit) {
        val updated = _customFolders.value.toMutableSet()
        if (updated.remove(folderPath)) {
            _customFolders.value = updated
            appPreferences.setCustomFolders(updated)
            if (_audioSourceMode.value == AudioSourceMode.CUSTOM_FOLDERS) {
                loadTracks(force = true, onAfterLoad = onAfterLoad)
            }
        }
    }

    // --- Aggiornamento Ottimistico Post-Tagging ---
    suspend fun applyOptimisticMetadataPatch(
        appliedMetadataMap: Map<Long, EnrichedMetadata>,
        onTrackPatched: (AudioTrack) -> Unit
    ) {
        if (appliedMetadataMap.isEmpty()) return
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
            patchedTracks.forEach { track ->
                if (appliedMetadataMap.containsKey(track.id)) {
                    onTrackPatched(track)
                }
            }
        }
    }

    // --- Eliminazione Tracce & Duplicati ---
    fun deleteTracks(tracks: List<AudioTrack>, onCompleted: ((Int) -> Unit)? = null) {
        if (tracks.isEmpty()) return
        pendingTracksToDelete = tracks
        val uris = tracks.map { it.uri }
        val intentSender = createDeleteRequestIntentSender(uris)
        if (intentSender != null) {
            _deleteRequestIntentSender.value = intentSender
        } else {
            scope.launch(Dispatchers.IO) {
                var deletedCount = 0
                for (track in tracks) {
                    try {
                        val count = application.contentResolver.delete(track.uri, null, null)
                        if (count > 0) deletedCount++
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    onDeletePermissionResult(isGranted = deletedCount > 0, onDeletedCount = onCompleted)
                }
            }
        }
    }

    fun onDeletePermissionResult(
        isGranted: Boolean,
        onDeletedIds: ((Set<Long>) -> Unit)? = null,
        onDeletedCount: ((Int) -> Unit)? = null
    ) {
        val tracks = pendingTracksToDelete
        pendingTracksToDelete = emptyList()
        _deleteRequestIntentSender.value = null

        if (isGranted && tracks.isNotEmpty()) {
            val deletedIds = tracks.map { it.id }.toSet()
            val remainingTracks = _tracks.value.filter { it.id !in deletedIds }
            _tracks.value = remainingTracks
            val updatedAlbums = groupTracksIntoAlbums(remainingTracks)
            _albums.value = updatedAlbums

            onDeletedIds?.invoke(deletedIds)

            scope.launch(Dispatchers.IO) {
                libraryCacheRepository.saveCache(_audioSourceMode.value, _customFolders.value, remainingTracks, updatedAlbums)
            }

            onDeletedCount?.invoke(tracks.size)
        }
    }

    fun ignoreDuplicateGroups(groups: List<DuplicateTrackGroup>) {
        val keys = groups.toPairKeys()
        if (keys.isNotEmpty()) {
            appPreferences.addIgnoredDuplicateKeys(keys)
            _ignoredDuplicateKeys.value = appPreferences.getIgnoredDuplicateKeys()
        }
    }

    fun ignoreDuplicateGroup(group: DuplicateTrackGroup) {
        ignoreDuplicateGroups(listOf(group))
    }

    fun restoreIgnoredDuplicatesForAlbum(album: Album) {
        val currentIgnored = _ignoredDuplicateKeys.value
        if (currentIgnored.isEmpty()) return

        val albumTrackIds = album.tracks.map { it.id }.toSet()
        val albumPaths = album.tracks.map { "${it.relativePath}/${it.displayName}" }.filter { it.isNotBlank() }.toSet()

        val keysToRemove = currentIgnored.filter { key ->
            if (key.contains(":")) {
                val parts = key.split(":")
                parts.size == 2 && (parts[0].toLongOrNull() in albumTrackIds || parts[1].toLongOrNull() in albumTrackIds)
            } else if (key.contains("|")) {
                val parts = key.split("|")
                parts.size == 2 && (parts[0] in albumPaths || parts[1] in albumPaths)
            } else {
                false
            }
        }

        if (keysToRemove.isNotEmpty()) {
            appPreferences.removeIgnoredDuplicateKeys(keysToRemove)
            _ignoredDuplicateKeys.value = appPreferences.getIgnoredDuplicateKeys()
        }
    }

    fun clearAllIgnoredDuplicates() {
        appPreferences.clearIgnoredDuplicateKeys()
        _ignoredDuplicateKeys.value = emptySet()
    }

    /**
     * Aggiorna gli stati in memoria dopo il ripristino di un backup locale.
     */
    fun refreshAfterBackupImport(onAfterLoad: (List<AudioTrack>) -> Unit = {}) {
        val newSourceMode = appPreferences.getAudioSourceMode()
        val newCustomFolders = appPreferences.getCustomFolders()
        val sourceChanged = newSourceMode != _audioSourceMode.value || newCustomFolders != _customFolders.value

        _audioSourceMode.value = newSourceMode
        _customFolders.value = newCustomFolders
        _ignoredDuplicateKeys.value = appPreferences.getIgnoredDuplicateKeys()

        scope.launch(Dispatchers.IO) {
            val loadedPlaylists = playlistRepository.loadPlaylists()
            val loadedStats = statsRepository.loadStats()
            withContext(Dispatchers.Main) {
                _playlists.value = loadedPlaylists
                _stats.value = loadedStats
                if (sourceChanged) {
                    loadTracks(force = true, onAfterLoad = onAfterLoad)
                }
            }
        }
    }
}
