package com.example.musicplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.musicplayer.ui.tagger.MetadataEnricherSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicplayer.R
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.PlaybackContext
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.matchesArtist
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.draw.clip
import com.example.musicplayer.ui.components.AddToPlaylistDialog
import com.example.musicplayer.ui.components.AlbumDetailView
import com.example.musicplayer.ui.components.AlbumsGridView
import com.example.musicplayer.ui.components.ArtistDetailSheet
import com.example.musicplayer.ui.components.CreatePlaylistDialog
import com.example.musicplayer.ui.components.EqualizerDialog
import com.example.musicplayer.ui.components.FastScrollbar
import com.example.musicplayer.ui.components.FullScreenPlayerSheet
import com.example.musicplayer.ui.components.GlobalSearchResultsView
import com.example.musicplayer.ui.components.LastFmDialog
import com.example.musicplayer.ui.components.MainModalsHost
import com.example.musicplayer.ui.components.PlaybackStatsDialog
import com.example.musicplayer.ui.components.PlayerBottomBar
import com.example.musicplayer.ui.components.PlaylistDetailView
import com.example.musicplayer.ui.components.PlaylistsView
import com.example.musicplayer.ui.components.QueueBottomSheet
import android.content.Intent
import com.example.musicplayer.ui.components.SettingsDialog
import com.example.musicplayer.ui.components.SleepTimerDialog
import com.example.musicplayer.ui.components.SortSelectorRow
import com.example.musicplayer.ui.components.TrackListItem
import com.example.musicplayer.ui.components.UpdateAvailableDialog
import com.example.musicplayer.ui.components.extractAlphabeticalHeader
import com.example.musicplayer.ui.components.rememberCollapsingScrollConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    // Non dereferenziamo currentPositionMs nella radice di MainScreen per evitare ricomposizioni a 250ms dell'intera schermata
    val currentPositionMsState = viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val currentPositionProvider = remember(currentPositionMsState) { { currentPositionMsState.value } }
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsStateWithLifecycle()
    val playbackContext by viewModel.playbackContext.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()

    // Coda di riproduzione
    val userQueue by viewModel.userQueue.collectAsStateWithLifecycle()
    val autoplayQueue by viewModel.autoplayQueue.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullScreenPlayer by remember { mutableStateOf(false) }

    // Dialog e visualizzazioni dedicate
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var selectedArtistForSheet by remember { mutableStateOf<String?>(null) }

    // Statistiche e ordinamento
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()

    // Ricerca globale
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Risultati pre-ordinati e filtrati (calcolati in background su Dispatchers.Default)
    val sortedTracks by viewModel.sortedTracks.collectAsStateWithLifecycle()
    val sortedAlbums by viewModel.sortedAlbums.collectAsStateWithLifecycle()
    val sortedPlaylists by viewModel.sortedPlaylists.collectAsStateWithLifecycle()
    val matchingTracks by viewModel.matchingTracks.collectAsStateWithLifecycle()
    val matchingAlbums by viewModel.matchingAlbums.collectAsStateWithLifecycle()
    val matchingPlaylists by viewModel.matchingPlaylists.collectAsStateWithLifecycle()
    val matchingArtists by viewModel.matchingArtists.collectAsStateWithLifecycle()

    // Aggiornamenti GitHub
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsStateWithLifecycle()

    // Navigazione schede e viste di dettaglio
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { 3 }
    )

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var activeAlbumForPager by remember { mutableStateOf<Album?>(null) }
    if (selectedAlbum != null && activeAlbumForPager != selectedAlbum) {
        activeAlbumForPager = selectedAlbum
    }
    var isAlbumProgrammaticScroll by remember { mutableStateOf(false) }
    val albumPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { if (activeAlbumForPager != null) 2 else 1 }
    )
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val isDetailOpen = selectedAlbum != null || selectedPlaylist != null
    var isSearchExpandedInDetail by remember { mutableStateOf(false) }

    // Sincronizzazione albumPagerState -> chiusura album SOLO al rilascio/assestamento su pagina 0 (settledPage)
    LaunchedEffect(albumPagerState) {
        var userDragged = false
        snapshotFlow {
            Triple(albumPagerState.isScrollInProgress, albumPagerState.settledPage, isAlbumProgrammaticScroll)
        }.collect { (inProgress, settledPage, programmatic) ->
            if (inProgress && !programmatic) {
                userDragged = true
            }
            if (!inProgress && !programmatic && userDragged) {
                userDragged = false
                if (settledPage == 0 && selectedAlbum != null) {
                    selectedAlbum = null
                    activeAlbumForPager = null
                    isSearchExpandedInDetail = false
                    if (searchQuery.isNotBlank()) {
                        viewModel.onSearchQueryChanged("")
                    }
                }
            }
        }
    }

    // Sincronizzazione inversa selectedAlbum -> albumPagerState (apertura album o pressione pulsante Indietro)
    LaunchedEffect(selectedAlbum) {
        if (selectedAlbum != null) {
            isSearchExpandedInDetail = false
            if (albumPagerState.currentPage != 1) {
                isAlbumProgrammaticScroll = true
                try {
                    albumPagerState.animateScrollToPage(1)
                } finally {
                    isAlbumProgrammaticScroll = false
                }
            }
        } else if (activeAlbumForPager != null) {
            isSearchExpandedInDetail = false
            if (albumPagerState.currentPage != 0) {
                isAlbumProgrammaticScroll = true
                try {
                    albumPagerState.animateScrollToPage(0)
                } finally {
                    isAlbumProgrammaticScroll = false
                }
            }
            activeAlbumForPager = null
        }
    }

    // Auto-Tagger & Testi Sincronizzati
    val enricherState by viewModel.enricherState.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val writeRequestIntentSender by viewModel.writeRequestIntentSender.collectAsStateWithLifecycle()

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onWritePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(writeRequestIntentSender) {
        writeRequestIntentSender?.let { sender ->
            writePermissionLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }
    var isHeaderCompact by remember { mutableStateOf(false) }
    var isSearchExpandedInCompact by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedAlbum, selectedPlaylist) {
        if (selectedAlbum != null || selectedPlaylist != null) {
            isSearchExpandedInDetail = false
        }
    }

    LaunchedEffect(isSearchExpandedInDetail) {
        if (isSearchExpandedInDetail) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isSearchExpandedInCompact) {
        if (isSearchExpandedInCompact) {
            searchFocusRequester.requestFocus()
        }
    }

    // Dialogs playlist
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }
    var playlistForCoverChange by remember { mutableStateOf<Playlist?>(null) }

    val favoriteTrackIds = remember(playlists) {
        playlists.find { it.isFavorites }?.trackIds?.toSet() ?: emptySet()
    }

    // Last.fm Scrobbler
    val isLastFmLoggedIn by viewModel.isLastFmLoggedIn.collectAsStateWithLifecycle()
    val lastFmUsername by viewModel.lastFmUsername.collectAsStateWithLifecycle()
    val lastFmAuthLoading by viewModel.lastFmAuthLoading.collectAsStateWithLifecycle()
    val lastFmAuthError by viewModel.lastFmAuthError.collectAsStateWithLifecycle()
    val lastFmStatusMessage by viewModel.lastFmStatusMessage.collectAsStateWithLifecycle()
    val lastFmCustomApiKey by viewModel.lastFmCustomApiKey.collectAsStateWithLifecycle()
    val lastFmCustomSharedSecret by viewModel.lastFmCustomSharedSecret.collectAsStateWithLifecycle()
    val pendingScrobblesCount by viewModel.pendingScrobblesCount.collectAsStateWithLifecycle()
    val ignoredDuplicateKeys by viewModel.ignoredDuplicateKeys.collectAsStateWithLifecycle()
    var showLastFmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val deleteRequestIntentSender by viewModel.deleteRequestIntentSender.collectAsStateWithLifecycle()

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val success = (result.resultCode == android.app.Activity.RESULT_OK)
        viewModel.onDeletePermissionResult(success) { count ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = if (count == 1) context.getString(R.string.duplicates_deleted_single) else context.getString(R.string.duplicates_deleted_multiple, count),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    LaunchedEffect(deleteRequestIntentSender) {
        deleteRequestIntentSender?.let { sender ->
            deletePermissionLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    LaunchedEffect(enricherState) {
        val s = enricherState
        if (s is com.example.musicplayer.ui.tagger.EnricherUiState.Success) {
            snackbarHostState.showSnackbar(
                message = s.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    val playlistCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetPlaylist = playlistForCoverChange
        playlistForCoverChange = null
        if (uri != null && targetPlaylist != null && !targetPlaylist.isFavorites) {
            viewModel.updatePlaylistCover(targetPlaylist.id, uri)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snackbar_cover_updated, targetPlaylist.name),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleChangePlaylistCover: (Playlist) -> Unit = remember(playlistCoverPickerLauncher) {
        { playlist ->
            if (!playlist.isFavorites) {
                playlistForCoverChange = playlist
                playlistCoverPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    val handleRemovePlaylistCover: (Playlist) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, context) {
        { playlist ->
            if (!playlist.isFavorites) {
                viewModel.updatePlaylistCover(playlist.id, null)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_cover_removed, playlist.name),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val handleAddTrackToQueue: (AudioTrack) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, context) {
        { track ->
            viewModel.addToQueue(track)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snackbar_track_added_next, track.title),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddAlbumToQueue: (Album) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, context) {
        { album ->
            viewModel.addAlbumToQueue(album)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snackbar_album_added_next, album.title, album.trackCount),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddPlaylistToQueue: (Playlist) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, context) {
        { playlist ->
            viewModel.addPlaylistToQueue(playlist)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snackbar_playlist_added_next, playlist.name, playlist.trackCount),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddToFavorites: (AudioTrack) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, favoriteTrackIds, context) {
        { track ->
            if (track.id in favoriteTrackIds) {
                viewModel.removeTrackFromPlaylist(Playlist.FAVORITES_PLAYLIST_ID, track.id)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_removed_from_favorites, track.title),
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                viewModel.addTrackToPlaylist(Playlist.FAVORITES_PLAYLIST_ID, track.id)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_added_to_favorites, track.title),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    LaunchedEffect(lastFmStatusMessage) {
        lastFmStatusMessage?.let { msg ->
            if (!msg.startsWith("In riproduzione", ignoreCase = true) &&
                !msg.startsWith("Ascolto registrato", ignoreCase = true) &&
                !msg.startsWith("Scrobble registrato", ignoreCase = true) &&
                !msg.startsWith("Offline:", ignoreCase = true)
            ) {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.clearLastFmStatusMessage()
        }
    }

    // Gestione tasto indietro
    BackHandler(enabled = showFullScreenPlayer || selectedArtistForSheet != null || isSearchExpandedInDetail || isSearchExpandedInCompact || searchQuery.isNotBlank() || selectedAlbum != null || selectedPlaylist != null || selectedTabIndex != 0) {
        when {
            showFullScreenPlayer -> showFullScreenPlayer = false
            selectedArtistForSheet != null -> selectedArtistForSheet = null
            isSearchExpandedInDetail -> {
                isSearchExpandedInDetail = false
                if (searchQuery.isNotBlank()) {
                    viewModel.onSearchQueryChanged("")
                }
            }
            isSearchExpandedInCompact -> {
                isSearchExpandedInCompact = false
                if (searchQuery.isNotBlank()) {
                    viewModel.onSearchQueryChanged("")
                }
            }
            searchQuery.isNotBlank() -> viewModel.onSearchQueryChanged("")
            selectedAlbum != null -> {
                selectedAlbum = null
                isSearchExpandedInDetail = false
            }
            selectedPlaylist != null -> {
                selectedPlaylist = null
                isSearchExpandedInDetail = false
            }
            selectedTabIndex != 0 -> coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    // Stati di scorrimento delle liste con scroll automatico all'inizio al cambio ordinamento
    val tracksListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()

    // Stato "in cima alla lista" tracciato separatamente per ciascuna sezione con tolleranza sui micro-offset e supporto a canScrollBackward:
    val isTracksAtTop by remember {
        derivedStateOf {
            tracksListState.firstVisibleItemIndex == 0 && (!tracksListState.canScrollBackward || tracksListState.firstVisibleItemScrollOffset <= 8)
        }
    }
    val isAlbumsAtTop by remember {
        derivedStateOf {
            albumsGridState.firstVisibleItemIndex == 0 && (!albumsGridState.canScrollBackward || albumsGridState.firstVisibleItemScrollOffset <= 8)
        }
    }
    val isPlaylistsAtTop by remember {
        derivedStateOf {
            playlistsListState.firstVisibleItemIndex == 0 && (!playlistsListState.canScrollBackward || playlistsListState.firstVisibleItemScrollOffset <= 8)
        }
    }

    // Visibilità del pulsante "Torna in alto" (spunta quando si è scesi di qualche elemento nella vista principale corrente)
    val showScrollToTop by remember {
        derivedStateOf {
            if (selectedAlbum != null || selectedPlaylist != null || searchQuery.isNotBlank()) {
                false
            } else {
                when (pagerState.currentPage) {
                    0 -> tracksListState.firstVisibleItemIndex >= 4
                    1 -> albumsGridState.firstVisibleItemIndex >= 4
                    2 -> playlistsListState.firstVisibleItemIndex >= 4
                    else -> false
                }
            }
        }
    }

    var lastActivePage by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            val page = pagerState.currentPage
            val atTop = when (page) {
                0 -> tracksListState.firstVisibleItemIndex == 0 && (!tracksListState.canScrollBackward || tracksListState.firstVisibleItemScrollOffset <= 8)
                1 -> albumsGridState.firstVisibleItemIndex == 0 && (!albumsGridState.canScrollBackward || albumsGridState.firstVisibleItemScrollOffset <= 8)
                2 -> playlistsListState.firstVisibleItemIndex == 0 && (!playlistsListState.canScrollBackward || playlistsListState.firstVisibleItemScrollOffset <= 8)
                else -> true
            }
            val isListScrolling = when (page) {
                0 -> tracksListState.isScrollInProgress
                1 -> albumsGridState.isScrollInProgress
                2 -> playlistsListState.isScrollInProgress
                else -> false
            }
            Triple(page, atTop, isListScrolling)
        }.collect { (page, atTop, isListScrolling) ->
            if (page != lastActivePage) {
                // Swipe orizzontale o cambio scheda: registra la pagina corrente senza riespandere l'header
                lastActivePage = page
            } else if (atTop && isListScrolling && !pagerState.isScrollInProgress) {
                // L'utente stava scorrendo la lista verticale ed è arrivato in cima: riespandi l'header!
                if (isHeaderCompact) {
                    isHeaderCompact = false
                }
                if (isSearchExpandedInCompact) {
                    isSearchExpandedInCompact = false
                }
            }
        }
    }

    val isCurrentTabAtTop = when (pagerState.currentPage) {
        0 -> isTracksAtTop
        1 -> isAlbumsAtTop
        2 -> isPlaylistsAtTop
        else -> true
    }

    val nestedScrollConnection = rememberCollapsingScrollConnection(
        onCollapse = {
            if (!isDetailOpen && !albumPagerState.isScrollInProgress && !pagerState.isScrollInProgress && !isHeaderCompact) {
                isHeaderCompact = true
            }
        },
        onExpand = {
            // Riespande la parte superiore solo se siamo già in cima alla lista della scheda attiva (e non durante uno swipe orizzontale)
            if (!isDetailOpen && !albumPagerState.isScrollInProgress && !pagerState.isScrollInProgress && isCurrentTabAtTop) {
                if (isHeaderCompact) {
                    isHeaderCompact = false
                }
                if (isSearchExpandedInCompact) {
                    isSearchExpandedInCompact = false
                }
            }
        }
    )

    LaunchedEffect(sortOption, isAscending) {
        when (pagerState.currentPage) {
            0 -> {
                if (sortedTracks.isNotEmpty() && (tracksListState.firstVisibleItemIndex > 0 || tracksListState.firstVisibleItemScrollOffset > 0)) {
                    tracksListState.animateScrollToItem(index = 0)
                }
            }
            1 -> {
                if (sortedAlbums.isNotEmpty() && (albumsGridState.firstVisibleItemIndex > 0 || albumsGridState.firstVisibleItemScrollOffset > 0)) {
                    albumsGridState.animateScrollToItem(index = 0)
                }
            }
            2 -> {
                if (sortedPlaylists.isNotEmpty() && (playlistsListState.firstVisibleItemIndex > 0 || playlistsListState.firstVisibleItemScrollOffset > 0)) {
                    playlistsListState.animateScrollToItem(index = 0)
                }
            }
        }
        if (pagerState.currentPage != 0 && sortedTracks.isNotEmpty()) {
            tracksListState.scrollToItem(index = 0)
        }
        if (pagerState.currentPage != 1 && sortedAlbums.isNotEmpty()) {
            albumsGridState.scrollToItem(index = 0)
        }
        if (pagerState.currentPage != 2 && sortedPlaylists.isNotEmpty()) {
            playlistsListState.scrollToItem(index = 0)
        }
        isHeaderCompact = false
        isSearchExpandedInCompact = false
    }

    // Permessi Android 16 (API 36)
    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val relativePath = if (docId.startsWith("primary:")) {
                docId.removePrefix("primary:").trimEnd('/') + "/"
            } else {
                docId.substringAfter(":", "").trimEnd('/') + "/"
            }
            if (relativePath.isNotBlank() && relativePath != "/") {
                viewModel.addCustomFolder(relativePath)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val audioGranted = permissionsMap[Manifest.permission.READ_MEDIA_AUDIO] == true
        val notifGranted = permissionsMap[Manifest.permission.POST_NOTIFICATIONS] == true
        hasPermissions = audioGranted && notifGranted
        if (audioGranted) {
            viewModel.loadTracks()
            viewModel.loadPlaylists()
            viewModel.checkForUpdates(isManual = false)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            if (tracks.isEmpty()) {
                viewModel.loadTracks()
                viewModel.loadPlaylists()
            }
        }
        viewModel.checkForUpdates(isManual = false)
    }

    // Unico stato di animazione guida per l'header: letto esclusivamente nelle fasi di Layout e Draw (0 ricomposizioni)
    val headerCollapseProgressState = animateFloatAsState(
        targetValue = if (isHeaderCompact) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "HeaderCollapseProgress"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = lerp(0.dp, 2.dp, headerCollapseProgressState.value).toPx()
                    },
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (hasPermissions) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val spinTransition = rememberInfiniteTransition(label = "refresh_spin")
                                        val spinAngle by spinTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(800, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "spin_angle"
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.loadTracks(force = true)
                                                viewModel.loadPlaylists()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.updating_library_message),
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.refresh_library_tooltip),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .graphicsLayer {
                                                        if (isLoading) {
                                                            rotationZ = spinAngle
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                                if (hasPermissions && tracks.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.library_tracks_count, tracks.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (hasPermissions) {
                                val showSearchActionIcon = isDetailOpen || (isHeaderCompact && searchQuery.isBlank() && !isSearchExpandedInCompact)
                                AnimatedVisibility(
                                    visible = showSearchActionIcon,
                                    enter = fadeIn(tween(140)) + expandHorizontally(tween(160)),
                                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isDetailOpen) {
                                                isSearchExpandedInDetail = !isSearchExpandedInDetail
                                                if (!isSearchExpandedInDetail && searchQuery.isNotEmpty()) {
                                                    viewModel.onSearchQueryChanged("")
                                                }
                                            } else {
                                                isSearchExpandedInCompact = !isSearchExpandedInCompact
                                                if (!isSearchExpandedInCompact && searchQuery.isNotEmpty()) {
                                                    viewModel.onSearchQueryChanged("")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.search)
                                        )
                                    }
                                }
                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.tab_settings)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // 3. Barra di ricerca assottigliata ed elegante: collassata via fase Layout (senza mount/unmount dell'albero)
                    val isSearchBarVisible = hasPermissions && (
                        (isDetailOpen && isSearchExpandedInDetail) ||
                        (!isDetailOpen && (!isHeaderCompact || isSearchExpandedInCompact || searchQuery.isNotBlank()))
                    )
                    val searchBarProgressState = animateFloatAsState(
                        targetValue = if (isSearchBarVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "SearchBarVisibilityProgress"
                    )

                    if (hasPermissions) {
                        val searchInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .collapsibleVerticalSection {
                                    val baseProgress = searchBarProgressState.value
                                    if (activeAlbumForPager != null && !isSearchExpandedInDetail && searchQuery.isBlank() && selectedPlaylist == null) {
                                        val albumOpenFraction = (albumPagerState.currentPage + albumPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                                        val tabsSearchFraction = if (!isHeaderCompact || isSearchExpandedInCompact) 1f else 0f
                                        tabsSearchFraction * (1f - albumOpenFraction)
                                    } else {
                                        baseProgress
                                    }
                                }
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .focusRequester(searchFocusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                interactionSource = searchInteractionSource,
                                decorationBox = { innerTextField ->
                                    OutlinedTextFieldDefaults.DecorationBox(
                                        value = searchQuery,
                                        innerTextField = innerTextField,
                                        enabled = true,
                                        singleLine = true,
                                        visualTransformation = VisualTransformation.None,
                                        interactionSource = searchInteractionSource,
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.search_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { viewModel.onSearchQueryChanged("") },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = stringResource(R.string.cancel),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else if (isDetailOpen && isSearchExpandedInDetail) {
                                                IconButton(
                                                    onClick = { isSearchExpandedInDetail = false },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = stringResource(R.string.close),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else if (isHeaderCompact && isSearchExpandedInCompact) {
                                                IconButton(
                                                    onClick = { isSearchExpandedInCompact = false },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = stringResource(R.string.close),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                            start = 12.dp,
                                            end = 8.dp,
                                            top = 0.dp,
                                            bottom = 0.dp
                                        ),
                                        container = {
                                            Surface(
                                                shape = RoundedCornerShape(21.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.fillMaxSize()
                                            ) {}
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 350)
                ) + fadeIn(animationSpec = tween(durationMillis = 350)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                PlayerBottomBar(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPositionProvider = currentPositionProvider,
                    durationMs = durationMs,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isAutoplayEnabled = isAutoplayEnabled,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPreviousClick = { viewModel.playPrevious() },
                    onNextClick = { viewModel.playNext() },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onRepeatClick = { viewModel.toggleRepeatMode() },
                    onToggleAutoplay = { viewModel.toggleAutoplay() },
                    onQueueClick = { showQueueSheet = true },
                    onSeekTo = { seekMs -> viewModel.seekTo(seekMs) },
                    onOpenFullScreen = { showFullScreenPlayer = true },
                    onAddToPlaylistClick = { track -> trackToAddToPlaylist = track },
                    queue = queue,
                    currentQueueIndex = currentQueueIndex,
                    onSkipToQueueItem = { index -> viewModel.skipToQueueItem(index) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            when {
                // Permessi non concessi
                !hasPermissions -> {
                    PermissionRequestView(
                        onGrantClicked = { permissionLauncher.launch(requiredPermissions) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Caricamento in corso
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Nessun brano trovato nella cartella Music/
                tracks.isEmpty() -> {
                    EmptyTracksView(
                        onRefreshClicked = {
                            viewModel.loadTracks(force = true)
                            viewModel.loadPlaylists()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Contenuto principale con transizioni Shared-Axis animate fluide e pager interattivo per il dettaglio Album
                else -> {
                    val currentContentScreen = when {
                        searchQuery.isNotBlank() -> MainContentScreen.Search(searchQuery)
                        selectedPlaylist != null -> MainContentScreen.PlaylistDetail(selectedPlaylist!!)
                        else -> MainContentScreen.Tabs
                    }

                    AnimatedContent(
                        targetState = currentContentScreen,
                        transitionSpec = {
                            val isNavigatingBack = (targetState is MainContentScreen.Tabs && initialState !is MainContentScreen.Tabs)
                            if (isNavigatingBack) {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
                                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 240))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() },
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 200))
                                )
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() },
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 260))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 220))
                                )
                            }
                        },
                        contentKey = { screen ->
                            when (screen) {
                                is MainContentScreen.Tabs -> "screen_tabs"
                                is MainContentScreen.Search -> "screen_search_${screen.query}"
                                is MainContentScreen.PlaylistDetail -> "screen_playlist_${screen.playlist.id}"
                            }
                        },
                        label = "MainContentScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetScreen ->
                        when (targetScreen) {
                            is MainContentScreen.Search -> {
                                GlobalSearchResultsView(
                                    query = targetScreen.query,
                                    matchingTracks = matchingTracks,
                                    matchingAlbums = matchingAlbums,
                                    matchingPlaylists = matchingPlaylists,
                                    matchingArtists = matchingArtists,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    stats = stats,
                                    onTrackClick = { track ->
                                        viewModel.playTrack(track)
                                    },
                                    onAlbumClick = { album ->
                                        selectedAlbum = album
                                        viewModel.onSearchQueryChanged("")
                                    },
                                    onPlaylistClick = { playlist ->
                                        selectedPlaylist = playlist
                                        viewModel.onSearchQueryChanged("")
                                    },
                                    onAddToQueueClick = handleAddTrackToQueue,
                                    onAddToPlaylistClick = { track ->
                                        trackToAddToPlaylist = track
                                    },
                                    onDeletePlaylistClick = { playlist ->
                                        viewModel.deletePlaylist(playlist.id)
                                    },
                                    onAddAlbumToQueueClick = handleAddAlbumToQueue,
                                    onAddPlaylistToQueueClick = handleAddPlaylistToQueue,
                                    onAddToFavoritesClick = handleAddToFavorites,
                                    favoriteTrackIds = favoriteTrackIds,
                                    onChangePlaylistCoverClick = handleChangePlaylistCover,
                                    onRemovePlaylistCoverClick = handleRemovePlaylistCover,
                                    onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) },
                                    onArtistClick = { artist -> selectedArtistForSheet = artist }
                                )
                            }

                            is MainContentScreen.PlaylistDetail -> {
                                val activePlaylist = playlists.find { it.id == targetScreen.playlist.id } ?: targetScreen.playlist
                                val playlistTracks = remember(activePlaylist.trackIds, tracks) {
                                    activePlaylist.trackIds.mapNotNull { id -> tracks.find { it.id == id } }
                                }

                                PlaylistDetailView(
                                    playlist = activePlaylist,
                                    playlistTracks = playlistTracks,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    onBackClick = {
                                        selectedPlaylist = null
                                        isSearchExpandedInDetail = false
                                        if (searchQuery.isNotBlank()) {
                                            viewModel.onSearchQueryChanged("")
                                        }
                                    },
                                    onPlayTrackIndex = { index ->
                                        viewModel.playPlaylist(activePlaylist.id, activePlaylist.name, playlistTracks, index)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playPlaylist(activePlaylist.id, activePlaylist.name, playlistTracks, 0)
                                    },
                                    onPlayPauseClick = {
                                        viewModel.togglePlayPause()
                                    },
                                    onRemoveTrack = { trackId ->
                                        viewModel.removeTrackFromPlaylist(activePlaylist.id, trackId)
                                        selectedPlaylist = activePlaylist.copy(
                                            trackIds = activePlaylist.trackIds - trackId
                                        )
                                    },
                                    onAddToQueueClick = handleAddTrackToQueue,
                                    onAddPlaylistToQueueClick = handleAddPlaylistToQueue,
                                    onAddToPlaylistClick = { trackToAddToPlaylist = it },
                                    onAddToFavoritesClick = handleAddToFavorites,
                                    favoriteTrackIds = favoriteTrackIds,
                                    onChangeCoverClick = if (!activePlaylist.isFavorites) handleChangePlaylistCover else null,
                                    onRemoveCoverClick = if (!activePlaylist.isFavorites) handleRemovePlaylistCover else null,
                                    onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) },
                                    onMoveTrack = { from, to ->
                                        viewModel.moveTrackInPlaylist(activePlaylist.id, from, to)
                                    },
                                    onArtistClick = { artist ->
                                        selectedArtistForSheet = artist
                                    }
                                )
                            }

                            is MainContentScreen.Tabs -> {
                                // Carosello interattivo a schermata intera tra Schede Principali (pagina 0) e Dettaglio Album (pagina 1):
                                // identico alla logica del mini-player (settledPage), trascinando a destra dentro l'album
                                // l'intera schermata dell'album segue il dito e rivela progressivamente la schermata precedente.
                                HorizontalPager(
                                    state = albumPagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    userScrollEnabled = albumPagerState.currentPage == 1
                                ) { navPage ->
                                    when (navPage) {
                                        0 -> {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                val tabItems = remember {
                                                    listOf(
                                                        Triple(0, R.string.tab_tracks, Icons.Default.MusicNote),
                                                        Triple(1, R.string.tab_albums, Icons.Default.Album),
                                                        Triple(2, R.string.tab_playlists, Icons.AutoMirrored.Filled.QueueMusic)
                                                    )
                                                }

                                                PrimaryTabRow(
                                                    selectedTabIndex = selectedTabIndex,
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ) {
                                                    tabItems.forEach { (index, titleRes, icon) ->
                                                        val title = stringResource(titleRes)
                                                        Tab(
                                                            selected = selectedTabIndex == index,
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    pagerState.animateScrollToPage(index)
                                                                }
                                                            },
                                                            modifier = Modifier.layout { measurable, constraints ->
                                                                val h = lerp(64.dp, 48.dp, headerCollapseProgressState.value).roundToPx()
                                                                val fixedConstraints = constraints.copy(minHeight = h, maxHeight = h)
                                                                val placeable = measurable.measure(fixedConstraints)
                                                                layout(placeable.width, h) {
                                                                    placeable.placeRelative(0, 0)
                                                                }
                                                            }
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxSize(),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = title,
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .graphicsLayer {
                                                                            val s = lerp(1f, 20f / 24f, headerCollapseProgressState.value)
                                                                            scaleX = s
                                                                            scaleY = s
                                                                        }
                                                                )
                                                                Column(
                                                                    modifier = Modifier.collapsibleVerticalSection {
                                                                        1f - headerCollapseProgressState.value
                                                                    },
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                    Text(
                                                                        text = title,
                                                                        style = MaterialTheme.typography.labelMedium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // Selettore criteri di ordinamento: collassato via fase Layout con 0 ricomposizioni
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .collapsibleVerticalSection { 1f - headerCollapseProgressState.value }
                                                ) {
                                                    SortSelectorRow(
                                                        selectedOption = sortOption,
                                                        onOptionSelected = { viewModel.setSortOption(it) },
                                                        isAscending = isAscending,
                                                        onToggleSortDirection = { viewModel.toggleSortDirection() }
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                ) {
                                                    HorizontalPager(
                                                        state = pagerState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        beyondViewportPageCount = 0,
                                                        userScrollEnabled = true
                                                    ) { page ->
                                                        when (page) {
                                                            // 1. Scheda 'Brani'
                                                            0 -> {
                                                                Box(modifier = Modifier.fillMaxSize()) {
                                                                    LazyColumn(
                                                                        state = tracksListState,
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        contentPadding = PaddingValues(bottom = 12.dp)
                                                                    ) {
                                                                        items(
                                                                            items = sortedTracks,
                                                                            key = { it.id },
                                                                            contentType = { "track_list_item" }
                                                                        ) { track ->
                                                                            TrackListItem(
                                                                                modifier = Modifier.animateItem(
                                                                                    fadeInSpec = null,
                                                                                    fadeOutSpec = null
                                                                                ),
                                                                                track = track,
                                                                                isSelected = currentTrack?.id == track.id,
                                                                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                                                                onClick = { viewModel.playTrack(track) },
                                                                                onAddToQueueClick = handleAddTrackToQueue,
                                                                                onAddToPlaylistClick = { trackToAddToPlaylist = it },
                                                                                onAddToFavoritesClick = { handleAddToFavorites(track) },
                                                                                isFavorite = track.id in favoriteTrackIds,
                                                                                playCount = stats[track.id]?.playCount ?: 0,
                                                                                onAutoTagClick = { viewModel.startTrackEnrichment(it) },
                                                                                onArtistClick = { artist -> selectedArtistForSheet = artist }
                                                                            )
                                                                        }
                                                                    }

                                                                    if (sortedTracks.isNotEmpty()) {
                                                                        FastScrollbar(
                                                                            state = tracksListState,
                                                                            totalItems = sortedTracks.size,
                                                                            isAlphabetical = sortOption == SortOption.ALPHABETICAL,
                                                                            letterProvider = { index ->
                                                                                sortedTracks.getOrNull(index)?.title?.let { extractAlphabeticalHeader(it) }
                                                                            },
                                                                            modifier = Modifier
                                                                                .align(Alignment.CenterEnd)
                                                                                .fillMaxHeight()
                                                                                .padding(top = 8.dp, bottom = 12.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            // 2. Scheda 'Album'
                                                            1 -> {
                                                                AlbumsGridView(
                                                                    albums = sortedAlbums,
                                                                    onAlbumClick = { album -> selectedAlbum = album },
                                                                    onAddToQueueClick = handleAddAlbumToQueue,
                                                                    onAutoTagAlbumClick = { viewModel.startAlbumEnrichment(it, separateAlbums = false) },
                                                                    onAutoTagAlbumSeparationClick = { viewModel.startAlbumEnrichment(it, separateAlbums = true) },
                                                                    state = albumsGridState,
                                                                    isAlphabetical = sortOption == SortOption.ALPHABETICAL,
                                                                    letterProvider = { index ->
                                                                        sortedAlbums.getOrNull(index)?.title?.let { extractAlphabeticalHeader(it) }
                                                                    }
                                                                )
                                                            }

                                                            // 3. Scheda 'Playlist'
                                                            2 -> {
                                                                PlaylistsView(
                                                                    playlists = sortedPlaylists,
                                                                    onPlaylistClick = { playlist -> selectedPlaylist = playlist },
                                                                    onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                                                    onDeletePlaylistClick = { playlist ->
                                                                        viewModel.deletePlaylist(playlist.id)
                                                                    },
                                                                    onAddToQueueClick = handleAddPlaylistToQueue,
                                                                    onChangeCoverClick = handleChangePlaylistCover,
                                                                    onRemoveCoverClick = handleRemovePlaylistCover,
                                                                    state = playlistsListState,
                                                                    isAlphabetical = sortOption == SortOption.ALPHABETICAL,
                                                                    letterProvider = { index ->
                                                                        sortedPlaylists.getOrNull(index)?.name?.let { extractAlphabeticalHeader(it) }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Pulsante circolare "Torna in alto" (in alto al centro sotto l'icona Album, con risalita fluida e viola primario)
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = showScrollToTop,
                                                        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                                                                scaleIn(animationSpec = tween(durationMillis = 220), initialScale = 0.7f) +
                                                                slideInVertically(animationSpec = tween(durationMillis = 220), initialOffsetY = { -it }),
                                                        exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                                                               scaleOut(animationSpec = tween(durationMillis = 180), targetScale = 0.7f) +
                                                               slideOutVertically(animationSpec = tween(durationMillis = 180), targetOffsetY = { -it }),
                                                        modifier = Modifier
                                                            .align(Alignment.TopCenter)
                                                            .padding(top = 10.dp)
                                                    ) {
                                                        Surface(
                                                            onClick = {
                                                                isHeaderCompact = false
                                                                coroutineScope.launch {
                                                                    when (pagerState.currentPage) {
                                                                        0 -> {
                                                                            if (tracksListState.firstVisibleItemIndex > 10) {
                                                                                tracksListState.scrollToItem(10)
                                                                            }
                                                                            tracksListState.animateScrollToItem(0)
                                                                        }
                                                                        1 -> {
                                                                            if (albumsGridState.firstVisibleItemIndex > 10) {
                                                                                albumsGridState.scrollToItem(10)
                                                                            }
                                                                            albumsGridState.animateScrollToItem(0)
                                                                        }
                                                                        2 -> {
                                                                            if (playlistsListState.firstVisibleItemIndex > 10) {
                                                                                playlistsListState.scrollToItem(10)
                                                                            }
                                                                            playlistsListState.animateScrollToItem(0)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                                            shadowElevation = 8.dp,
                                                            tonalElevation = 4.dp,
                                                            modifier = Modifier.size(48.dp)
                                                        ) {
                                                            Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.ArrowUpward,
                                                                    contentDescription = stringResource(R.string.scroll_to_top),
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        1 -> {
                                            val targetAlbum = selectedAlbum ?: activeAlbumForPager
                                            if (targetAlbum != null) {
                                                val currentAlbum = albums.find { it.id == targetAlbum.id }
                                                    ?: targetAlbum.tracks.firstOrNull()?.let { firstTrack ->
                                                        albums.find { alb -> alb.tracks.any { it.id == firstTrack.id } }
                                                    } ?: targetAlbum

                                                val hasIgnoredDuplicates = remember(currentAlbum, ignoredDuplicateKeys) {
                                                    if (ignoredDuplicateKeys.isEmpty()) false
                                                    else {
                                                        val albumTrackIds = currentAlbum.tracks.map { it.id }.toSet()
                                                        val albumPaths = currentAlbum.tracks.map { "${it.relativePath}/${it.displayName}" }.filter { it.isNotBlank() }.toSet()
                                                        ignoredDuplicateKeys.any { key ->
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
                                                    }
                                                }

                                                AlbumDetailView(
                                                    album = currentAlbum,
                                                    currentTrack = currentTrack,
                                                    isPlaying = isPlaying,
                                                    onBackClick = {
                                                        selectedAlbum = null
                                                        isSearchExpandedInDetail = false
                                                        if (searchQuery.isNotBlank()) {
                                                            viewModel.onSearchQueryChanged("")
                                                        }
                                                    },
                                                    onPlayTrackIndex = { index ->
                                                        viewModel.playAlbum(currentAlbum, index)
                                                    },
                                                    onPlayAllClick = {
                                                        viewModel.playAlbum(currentAlbum, 0)
                                                    },
                                                    onPlayPauseClick = {
                                                        viewModel.togglePlayPause()
                                                    },
                                                    onAddToPlaylistClick = { trackToAddToPlaylist = it },
                                                    onAddToQueueClick = handleAddTrackToQueue,
                                                    onAddAlbumToQueueClick = handleAddAlbumToQueue,
                                                    onAddToFavoritesClick = handleAddToFavorites,
                                                    favoriteTrackIds = favoriteTrackIds,
                                                    onAutoTagAlbumClick = { viewModel.startAlbumEnrichment(it, separateAlbums = false) },
                                                    onAutoTagAlbumSeparationClick = { viewModel.startAlbumEnrichment(it, separateAlbums = true) },
                                                    onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) },
                                                    onDeleteTracks = { tracks -> viewModel.deleteTracks(tracks) },
                                                    onArtistClick = { artist -> selectedArtistForSheet = artist },
                                                    ignoredDuplicateKeys = ignoredDuplicateKeys,
                                                    hasIgnoredDuplicates = hasIgnoredDuplicates,
                                                    onIgnoreDuplicates = { groups ->
                                                        viewModel.ignoreDuplicateGroups(groups)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(context.getString(R.string.duplicates_snackbar_ignored))
                                                        }
                                                    },
                                                    onRestoreIgnoredDuplicates = {
                                                        viewModel.restoreIgnoredDuplicatesForAlbum(currentAlbum)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(context.getString(R.string.duplicates_snackbar_restored))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Aggiungi a Playlist
    trackToAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { trackToAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                if (playlist.id == Playlist.FAVORITES_PLAYLIST_ID) {
                    handleAddToFavorites(track)
                } else {
                    viewModel.addTrackToPlaylist(playlist.id, track.id)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.snackbar_track_added_to_playlist, track.title, playlist.name),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                trackToAddToPlaylist = null
            },
            onCreateNewPlaylistClick = {
                showCreatePlaylistDialog = true
            }
        )
    }

    // Host centralizzato per la presentazione modale (Bottom Sheets e Dialogs)
    MainModalsHost(
        viewModel = viewModel,
        showQueueSheet = showQueueSheet,
        onDismissQueueSheet = { showQueueSheet = false },
        showFullScreenPlayer = showFullScreenPlayer,
        onDismissFullScreenPlayer = { showFullScreenPlayer = false },
        onOpenQueueSheet = { showQueueSheet = true },
        currentPositionProvider = currentPositionProvider,
        showCreatePlaylistDialog = showCreatePlaylistDialog,
        onDismissCreatePlaylistDialog = { showCreatePlaylistDialog = false },
        onOpenCreatePlaylistDialog = { showCreatePlaylistDialog = true },
        showLastFmDialog = showLastFmDialog,
        onDismissLastFmDialog = { showLastFmDialog = false },
        onOpenLastFmDialog = { showLastFmDialog = true },
        showSettingsDialog = showSettingsDialog,
        onDismissSettingsDialog = { showSettingsDialog = false },
        onPickCustomFolder = { folderPickerLauncher.launch(null) },
        showEqualizerDialog = showEqualizerDialog,
        onDismissEqualizerDialog = { showEqualizerDialog = false },
        onOpenEqualizer = { showEqualizerDialog = true },
        showSleepTimerDialog = showSleepTimerDialog,
        onDismissSleepTimerDialog = { showSleepTimerDialog = false },
        onOpenSleepTimer = { showSleepTimerDialog = true },
        showStatsDialog = showStatsDialog,
        onDismissStatsDialog = { showStatsDialog = false },
        onOpenStats = { showStatsDialog = true },
        selectedArtistForSheet = selectedArtistForSheet,
        onDismissArtistDetailSheet = { selectedArtistForSheet = null },
        onSelectArtistForSheet = { selectedArtistForSheet = it },
        tracks = tracks,
        albums = albums,
        playlists = playlists,
        favoriteTrackIds = favoriteTrackIds,
        onNavigateToAlbum = { album ->
            if (searchQuery.isNotBlank()) viewModel.onSearchQueryChanged("")
            isSearchExpandedInDetail = false
            selectedArtistForSheet = null
            selectedAlbum = album
            selectedPlaylist = null
        },
        onNavigateToPlaylist = { playlist ->
            if (searchQuery.isNotBlank()) viewModel.onSearchQueryChanged("")
            isSearchExpandedInDetail = false
            selectedArtistForSheet = null
            selectedPlaylist = playlist
            selectedAlbum = null
        },
        onNavigateToLibrary = {
            if (searchQuery.isNotBlank()) viewModel.onSearchQueryChanged("")
            isSearchExpandedInDetail = false
            selectedArtistForSheet = null
            selectedAlbum = null
            selectedPlaylist = null
        },
        onAddToFavorites = handleAddToFavorites,
        onAddToQueue = handleAddTrackToQueue,
        onAddToPlaylistRequest = { trackToAddToPlaylist = it },
        snackbarHostState = snackbarHostState
    )
}

/**
 * Modificatore che contrae/espande verticalmente un componente lavorando esclusivamente
 * nelle fasi di Layout e Draw (0 ricomposizioni e nessun mount/unmount dell'albero Compose).
 */
private fun Modifier.collapsibleVerticalSection(
    visibleFractionProvider: () -> Float
): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val fraction = visibleFractionProvider().coerceIn(0f, 1f)
        if (fraction <= 0.001f) {
            layout(constraints.minWidth, 0) {}
        } else {
            val placeable = measurable.measure(constraints)
            val currentHeight = (placeable.height * fraction).toInt()
            layout(placeable.width, currentHeight) {
                placeable.placeWithLayer(0, 0) {
                    alpha = fraction
                }
            }
        }
    }

@Composable
private fun PermissionRequestView(
    onGrantClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permissions_required_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGrantClicked,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = stringResource(R.string.grant_permissions))
            }
        }
    }
}

@Composable
private fun EmptyTracksView(
    onRefreshClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_songs_found),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefreshClicked) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

private sealed interface MainContentScreen {
    data object Tabs : MainContentScreen
    data class Search(val query: String) : MainContentScreen
    data class PlaylistDetail(val playlist: Playlist) : MainContentScreen
}
