package com.example.musicplayer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.PlaybackContext
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.matchesArtist
import com.example.musicplayer.ui.tagger.MetadataEnricherSheet
import com.example.musicplayer.ui.MusicViewModel
import kotlinx.coroutines.launch

/**
 * Host centralizzato per tutti i fogli modali (bottom sheet) e finestre di dialogo dell'interfaccia principale.
 * Decongestiona [com.example.musicplayer.ui.MainScreen], isolando la logica di presentazione modale
 * e ottimizzando i flussi di ricomposizione.
 */
@Composable
fun MainModalsHost(
    viewModel: MusicViewModel,
    // Stati di visibilità e controllo modali
    showQueueSheet: Boolean,
    onDismissQueueSheet: () -> Unit,
    showFullScreenPlayer: Boolean,
    onDismissFullScreenPlayer: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    currentPositionProvider: () -> Long,
    showCreatePlaylistDialog: Boolean,
    onDismissCreatePlaylistDialog: () -> Unit,
    onOpenCreatePlaylistDialog: () -> Unit,
    showLastFmDialog: Boolean,
    onDismissLastFmDialog: () -> Unit,
    onOpenLastFmDialog: () -> Unit,
    showSettingsDialog: Boolean,
    onDismissSettingsDialog: () -> Unit,
    onPickCustomFolder: () -> Unit,
    showEqualizerDialog: Boolean,
    onDismissEqualizerDialog: () -> Unit,
    onOpenEqualizer: () -> Unit,
    showSleepTimerDialog: Boolean,
    onDismissSleepTimerDialog: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    showStatsDialog: Boolean,
    onDismissStatsDialog: () -> Unit,
    onOpenStats: () -> Unit,
    selectedArtistForSheet: String?,
    onDismissArtistDetailSheet: () -> Unit,
    onSelectArtistForSheet: (String?) -> Unit,
    // Dati e collezioni
    tracks: List<AudioTrack>,
    albums: List<Album>,
    playlists: List<Playlist>,
    favoriteTrackIds: Set<Long>,
    // Callback di navigazione contestuale e azioni
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onAddToFavorites: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylistRequest: (AudioTrack) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()

    // Flussi di stato del ViewModel per la riproduzione e le opzioni modali
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()
    val userQueue by viewModel.userQueue.collectAsStateWithLifecycle()
    val autoplayQueue by viewModel.autoplayQueue.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val playbackContext by viewModel.playbackContext.collectAsStateWithLifecycle()

    // Stati Last.fm
    val isLastFmLoggedIn by viewModel.isLastFmLoggedIn.collectAsStateWithLifecycle()
    val lastFmUsername by viewModel.lastFmUsername.collectAsStateWithLifecycle()
    val lastFmAvatarPath by viewModel.lastFmAvatarPath.collectAsStateWithLifecycle()
    val lastFmAuthLoading by viewModel.lastFmAuthLoading.collectAsStateWithLifecycle()
    val lastFmAuthError by viewModel.lastFmAuthError.collectAsStateWithLifecycle()
    val lastFmStatusMessage by viewModel.lastFmStatusMessage.collectAsStateWithLifecycle()
    val lastFmCustomApiKey by viewModel.lastFmCustomApiKey.collectAsStateWithLifecycle()
    val lastFmCustomSharedSecret by viewModel.lastFmCustomSharedSecret.collectAsStateWithLifecycle()
    val pendingScrobblesCount by viewModel.pendingScrobblesCount.collectAsStateWithLifecycle()

    // Altri stati
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val enricherState by viewModel.enricherState.collectAsStateWithLifecycle()

    // 1. ModalBottomSheet Coda di Riproduzione
    if (showQueueSheet) {
        QueueBottomSheet(
            currentTrack = currentTrack,
            userQueue = userQueue,
            autoplayQueue = autoplayQueue,
            isPlaying = isPlaying,
            onDismissRequest = onDismissQueueSheet,
            onSkipToUserQueueItem = { index -> viewModel.skipToUserQueueItem(index) },
            onRemoveUserQueueItem = { index -> viewModel.removeUserQueueItem(index) },
            onMoveUserQueueItem = { fromIndex, toIndex -> viewModel.moveUserQueueItem(fromIndex, toIndex) },
            onMoveUserQueueItemToTop = { index -> viewModel.moveUserQueueItemToTop(index) },
            onClearUserQueue = { viewModel.clearUserQueue() },
            onSkipToAutoplayItem = { index -> viewModel.skipToAutoplayItem(index) },
            onRemoveAutoplayItem = { index -> viewModel.removeAutoplayItem(index) },
            onMoveAutoplayItem = { fromIndex, toIndex -> viewModel.moveAutoplayItem(fromIndex, toIndex) },
            onMoveAutoplayItemToTop = { index -> viewModel.moveAutoplayItemToTop(index) },
            onAddAutoplayItemToUserQueue = { index -> viewModel.moveAutoplayItemToUserQueue(index) }
        )
    }

    // 2. ModalBottomSheet Player a tutto schermo (Full Screen Player)
    if (showFullScreenPlayer && currentTrack != null) {
        FullScreenPlayerSheet(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            durationMs = durationMs,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isAutoplayEnabled = isAutoplayEnabled,
            queue = queue,
            currentQueueIndex = currentQueueIndex,
            onDismissRequest = onDismissFullScreenPlayer,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onPreviousClick = { viewModel.playPrevious() },
            onNextClick = { viewModel.playNext() },
            onShuffleClick = { viewModel.toggleShuffle() },
            onRepeatClick = { viewModel.toggleRepeatMode() },
            onToggleAutoplay = { viewModel.toggleAutoplay() },
            onQueueClick = onOpenQueueSheet,
            onSeekTo = { seekMs -> viewModel.seekTo(seekMs) },
            onSkipToQueueItem = { index -> viewModel.skipToQueueItem(index) },
            currentLyrics = currentLyrics,
            onAddToQueueClick = onAddToQueue,
            onAddToPlaylistClick = onAddToPlaylistRequest,
            onAddToFavoritesClick = onAddToFavorites,
            isFavorite = currentTrack?.id in favoriteTrackIds,
            onAutoTagClick = { viewModel.startTrackEnrichment(it) },
            playbackContext = playbackContext,
            onContextClick = {
                when (val ctx = playbackContext) {
                    is PlaybackContext.Album -> {
                        val matchingAlbum = albums.find { it.id == ctx.id }
                            ?: albums.find { it.title.equals(ctx.title, ignoreCase = true) }
                        if (matchingAlbum != null) {
                            onNavigateToAlbum(matchingAlbum)
                            onDismissFullScreenPlayer()
                        }
                    }
                    is PlaybackContext.Playlist -> {
                        val matchingPlaylist = playlists.find { it.id == ctx.id }
                        if (matchingPlaylist != null) {
                            onNavigateToPlaylist(matchingPlaylist)
                            onDismissFullScreenPlayer()
                        }
                    }
                    is PlaybackContext.Artist -> {
                        onSelectArtistForSheet(ctx.name)
                        onDismissFullScreenPlayer()
                    }
                    is PlaybackContext.Library -> {
                        onNavigateToLibrary()
                        onDismissFullScreenPlayer()
                    }
                }
            },
            onArtistClick = { artist ->
                onSelectArtistForSheet(artist)
                onDismissFullScreenPlayer()
            },
            onOpenSleepTimer = onOpenSleepTimer,
            onAlbumClick = {
                val track = currentTrack
                if (track != null) {
                    val matchingAlbum = albums.find { it.id == track.albumId }
                        ?: albums.find { it.title.equals(track.album, ignoreCase = true) }
                        ?: if (track.album.isNotBlank()) {
                            val albumTracks = tracks.filter {
                                it.albumId == track.albumId || it.album.equals(track.album, ignoreCase = true)
                            }.ifEmpty { listOf(track) }
                            Album(
                                id = track.albumId,
                                title = track.album,
                                artist = track.artist,
                                albumArtUri = track.albumArtUri,
                                tracks = albumTracks
                            )
                        } else null

                    if (matchingAlbum != null) {
                        onNavigateToAlbum(matchingAlbum)
                        onDismissFullScreenPlayer()
                    }
                }
            }
        )
    }

    // 3. Dialog Nuova Playlist
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = onDismissCreatePlaylistDialog,
            onConfirm = { name, coverUri ->
                viewModel.createPlaylist(name, coverUri)
                onDismissCreatePlaylistDialog()
            }
        )
    }

    // 4. Dialog Last.fm Scrobbler
    if (showLastFmDialog) {
        LaunchedEffect(isLastFmLoggedIn) {
            if (isLastFmLoggedIn) {
                viewModel.refreshLastFmAvatar()
            }
        }
        LastFmDialog(
            isLoggedIn = isLastFmLoggedIn,
            username = lastFmUsername,
            avatarPath = lastFmAvatarPath,
            isLoading = lastFmAuthLoading,
            errorMessage = lastFmAuthError,
            lastStatusMessage = lastFmStatusMessage,
            pendingCount = pendingScrobblesCount,
            initialApiKey = lastFmCustomApiKey,
            initialSharedSecret = lastFmCustomSharedSecret,
            onLogin = { user, pass, apiKey, secret ->
                viewModel.loginLastFm(user, pass, apiKey, secret)
            },
            onSaveApiCredentials = { apiKey, secret ->
                viewModel.saveLastFmApiCredentials(apiKey, secret)
            },
            onLogout = { viewModel.logoutLastFm() },
            onSyncPending = { viewModel.syncOfflineScrobbles() },
            onDismiss = onDismissLastFmDialog
        )
    }

    // 6. Finestra Impostazioni a schermo intero
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = onDismissSettingsDialog,
            onOpenLastFmDialog = onOpenLastFmDialog,
            onPickCustomFolder = onPickCustomFolder,
            onOpenEqualizer = onOpenEqualizer,
            onOpenSleepTimer = onOpenSleepTimer,
            onOpenStats = onOpenStats
        )
    }

    // 7. Dialog Equalizzatore Audio ed Effetti Sonori
    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismissRequest = onDismissEqualizerDialog
        )
    }

    // 8. Dialog Timer di Spegnimento con dissolvenza progressiva
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerState = sleepTimerState,
            onSetMinutes = { minutes -> viewModel.setSleepTimerMinutes(minutes) },
            onSetEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismissRequest = onDismissSleepTimerDialog
        )
    }

    // 9. Dialog Statistiche d'ascolto
    if (showStatsDialog) {
        PlaybackStatsDialog(
            tracks = tracks,
            stats = stats,
            onDismissRequest = onDismissStatsDialog,
            onResetStats = { viewModel.clearListeningStats() },
            onTrackClick = { track ->
                onDismissStatsDialog()
                viewModel.playTrack(track)
            },
            onArtistClick = { artist ->
                onDismissStatsDialog()
                onSelectArtistForSheet(artist)
            },
            onAlbumClick = { albumTitle ->
                val matchingAlbum = albums.find { it.title.equals(albumTitle, ignoreCase = true) }
                if (matchingAlbum != null) {
                    onNavigateToAlbum(matchingAlbum)
                    onDismissStatsDialog()
                }
            }
        )
    }

    // 10. ModalBottomSheet Vista Dettaglio Artista
    selectedArtistForSheet?.let { artistName ->
        val artistTracks = remember(artistName, tracks) {
            tracks.filter { it.matchesArtist(artistName) }
        }
        ArtistDetailSheet(
            artistName = artistName,
            tracks = tracks,
            albums = albums,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onDismissRequest = onDismissArtistDetailSheet,
            onPlayTrackIndex = { index ->
                viewModel.playArtist(artistName, index)
            },
            onPlayAllClick = {
                viewModel.playArtist(artistName, 0)
            },
            onAddAllToQueueClick = {
                viewModel.addTracksToQueue(artistTracks)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "${artistTracks.size} brani di $artistName aggiunti alla coda",
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onAlbumClick = { album ->
                onDismissArtistDetailSheet()
                onNavigateToAlbum(album)
            },
            onAddToQueueClick = onAddToQueue,
            onAddToPlaylistClick = onAddToPlaylistRequest,
            onAddToFavoritesClick = onAddToFavorites,
            favoriteTrackIds = favoriteTrackIds,
            onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) }
        )
    }

    // 11. Dialog Notifica Aggiornamento GitHub all'avvio
    if (isUpdateDialogVisible) {
        updateInfo?.let { info ->
            UpdateAvailableDialog(
                updateInfo = info,
                onDismiss = { viewModel.dismissUpdateDialog(neverShowAgainForThisVersion = false) },
                onNeverShowAgain = { viewModel.dismissUpdateDialog(neverShowAgainForThisVersion = true) }
            )
        }
    }

    // 12. Foglio modale Smart Auto-Tagger & Metadata Enricher
    MetadataEnricherSheet(
        state = enricherState,
        onDismissRequest = { viewModel.closeEnricher() },
        onApplyTrack = { track, metadata, options ->
            viewModel.applyTrackEnrichment(track, metadata, options)
        },
        onApplyAlbum = { album, tracksMap, options ->
            viewModel.applyAlbumEnrichment(album, tracksMap, options)
        },
        onSelectCandidate = { track, candidate, options ->
            viewModel.selectAlternativeCandidate(track, candidate, options)
        },
        onCustomSearch = { track, title, artist ->
            viewModel.searchCustomTrackEnrichment(track, title, artist)
        },
        onToggleAlbumSeparationMode = { viewModel.toggleAlbumEnrichmentMode(it) },
        onRetry = {
            when (val s = enricherState) {
                is com.example.musicplayer.ui.tagger.EnricherUiState.ReviewTrack -> viewModel.startTrackEnrichment(s.track)
                is com.example.musicplayer.ui.tagger.EnricherUiState.ReviewAlbum -> viewModel.startAlbumEnrichment(s.album)
                else -> viewModel.closeEnricher()
            }
        }
    )
}
