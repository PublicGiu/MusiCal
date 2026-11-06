package com.example.musicplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.musicplayer.ui.tagger.MetadataEnricherSheet
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
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.sortAlbums
import com.example.musicplayer.data.sortPlaylists
import com.example.musicplayer.data.sortTracks
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
import com.example.musicplayer.ui.components.CreatePlaylistDialog
import com.example.musicplayer.ui.components.FullScreenPlayerSheet
import com.example.musicplayer.ui.components.GlobalSearchResultsView
import com.example.musicplayer.ui.components.LastFmDialog
import com.example.musicplayer.ui.components.PlayerBottomBar
import com.example.musicplayer.ui.components.PlaylistDetailView
import com.example.musicplayer.ui.components.PlaylistsView
import com.example.musicplayer.ui.components.QueueBottomSheet
import android.content.Intent
import com.example.musicplayer.ui.components.SettingsDialog
import com.example.musicplayer.ui.components.SortSelectorRow
import com.example.musicplayer.ui.components.TrackListItem
import com.example.musicplayer.ui.components.UpdateAvailableDialog
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

    // Coda di riproduzione
    val userQueue by viewModel.userQueue.collectAsStateWithLifecycle()
    val autoplayQueue by viewModel.autoplayQueue.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullScreenPlayer by remember { mutableStateOf(false) }

    // Statistiche e ordinamento
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()

    // Ricerca globale
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Aggiornamenti GitHub
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsStateWithLifecycle()

    // Navigazione schede e viste di dettaglio
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

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
    val isDetailOpen = selectedAlbum != null || selectedPlaylist != null
    var isSearchExpandedInDetail by remember { mutableStateOf(false) }
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
    var showLastFmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                    message = "Copertina aggiornata per \"${targetPlaylist.name}\"",
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

    val handleRemovePlaylistCover: (Playlist) -> Unit = remember(viewModel, coroutineScope, snackbarHostState) {
        { playlist ->
            if (!playlist.isFavorites) {
                viewModel.updatePlaylistCover(playlist.id, null)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Copertina rimossa da \"${playlist.name}\"",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val handleAddTrackToQueue: (AudioTrack) -> Unit = remember(viewModel, coroutineScope, snackbarHostState) {
        { track ->
            viewModel.addToQueue(track)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "\"${track.title}\" aggiunto come successivo",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddAlbumToQueue: (Album) -> Unit = remember(viewModel, coroutineScope, snackbarHostState) {
        { album ->
            viewModel.addAlbumToQueue(album)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Album \"${album.title}\" (${album.trackCount} brani) aggiunto come successivo",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddPlaylistToQueue: (Playlist) -> Unit = remember(viewModel, coroutineScope, snackbarHostState) {
        { playlist ->
            viewModel.addPlaylistToQueue(playlist)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Playlist \"${playlist.name}\" (${playlist.trackCount} brani) aggiunta come successiva",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val handleAddToFavorites: (AudioTrack) -> Unit = remember(viewModel, coroutineScope, snackbarHostState, favoriteTrackIds) {
        { track ->
            if (track.id in favoriteTrackIds) {
                viewModel.removeTrackFromPlaylist(Playlist.FAVORITES_PLAYLIST_ID, track.id)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "\"${track.title}\" rimosso dai preferiti",
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                viewModel.addTrackToPlaylist(Playlist.FAVORITES_PLAYLIST_ID, track.id)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "\"${track.title}\" aggiunto ai preferiti",
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
    BackHandler(enabled = showFullScreenPlayer || isSearchExpandedInDetail || isSearchExpandedInCompact || searchQuery.isNotBlank() || selectedAlbum != null || selectedPlaylist != null || selectedTabIndex != 0) {
        when {
            showFullScreenPlayer -> showFullScreenPlayer = false
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

    // Risultati ordinati tramite funzioni di dominio dedicate
    val sortedTracks = remember(tracks, sortOption, stats, isAscending) {
        tracks.sortTracks(sortOption, stats, isAscending)
    }

    val sortedAlbums = remember(albums, sortOption, stats, isAscending) {
        albums.sortAlbums(sortOption, stats, isAscending)
    }

    val sortedPlaylists = remember(playlists, sortOption, stats, isAscending) {
        playlists.sortPlaylists(sortOption, stats, isAscending)
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
            if (!pagerState.isScrollInProgress && !isHeaderCompact) {
                isHeaderCompact = true
            }
        },
        onExpand = {
            // Riespande la parte superiore solo se siamo già in cima alla lista della scheda attiva (e non durante uno swipe orizzontale)
            if (!pagerState.isScrollInProgress && isCurrentTabAtTop) {
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

    // Risultati ricerca globale
    val matchingTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val matchingAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else albums.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val matchingPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else playlists.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
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
            viewModel.loadTracks()
            viewModel.loadPlaylists()
        }
        viewModel.checkForUpdates(isManual = false)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Unico stato di animazione guida per l'header: letto esclusivamente nelle fasi di Layout e Draw (0 ricomposizioni)
            val headerCollapseProgressState = animateFloatAsState(
                targetValue = if (isHeaderCompact) 1f else 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "HeaderCollapseProgress"
            )

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
                                                viewModel.loadTracks()
                                                viewModel.loadPlaylists()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "Aggiornamento libreria in corso...",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Aggiorna libreria",
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
                                        text = "${tracks.size} brani in libreria",
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
                                            contentDescription = "Cerca"
                                        )
                                    }
                                }
                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Impostazioni"
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
                                .collapsibleVerticalSection { searchBarProgressState.value }
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
                                                text = "Cerca",
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
                                                        contentDescription = "Cancella ricerca",
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
                                                        contentDescription = "Chiudi ricerca",
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
                                                        contentDescription = "Chiudi ricerca",
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

                    // Navigazione a Schede e Ordinamento (visibili quando non c'è una ricerca attiva e non siamo nei dettagli)
                    if (hasPermissions && searchQuery.isBlank() && selectedAlbum == null && selectedPlaylist == null) {
                        val tabItems = remember {
                            listOf(
                                Triple(0, "Brani", Icons.Default.MusicNote),
                                Triple(1, "Album", Icons.Default.Album),
                                Triple(2, "Playlist", Icons.AutoMirrored.Filled.QueueMusic)
                            )
                        }

                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabItems.forEach { (index, title, icon) ->
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
                    onAddToPlaylistClick = { track -> trackToAddToPlaylist = track }
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
                            viewModel.loadTracks()
                            viewModel.loadPlaylists()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Schermata Risultati Ricerca Globale
                searchQuery.isNotBlank() -> {
                    GlobalSearchResultsView(
                        query = searchQuery,
                        matchingTracks = matchingTracks,
                        matchingAlbums = matchingAlbums,
                        matchingPlaylists = matchingPlaylists,
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
                        onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) }
                    )
                }

                // Dettaglio Album
                selectedAlbum != null -> {
                    val currentAlbum = albums.find { it.id == selectedAlbum?.id }
                    if (currentAlbum == null) {
                        selectedAlbum = null
                    } else {
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
                            onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) }
                        )
                    }
                }

                // Dettaglio Playlist
                selectedPlaylist != null -> {
                    val activePlaylist = playlists.find { it.id == selectedPlaylist?.id } ?: selectedPlaylist!!
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
                            viewModel.playPlaylist(playlistTracks, index)
                        },
                        onPlayAllClick = {
                            viewModel.playPlaylist(playlistTracks, 0)
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
                        onAutoTagTrackClick = { viewModel.startTrackEnrichment(it) }
                    )
                }

                // Schede Principali
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        userScrollEnabled = true
                    ) { page ->
                        when (page) {
                            // 1. Scheda 'Brani'
                            0 -> {
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
                                            modifier = Modifier.animateItem(),
                                            track = track,
                                            isSelected = currentTrack?.id == track.id,
                                            isPlaying = isPlaying && currentTrack?.id == track.id,
                                            onClick = { viewModel.playTrack(track) },
                                            onAddToQueueClick = handleAddTrackToQueue,
                                            onAddToPlaylistClick = { trackToAddToPlaylist = it },
                                            onAddToFavoritesClick = { handleAddToFavorites(track) },
                                            isFavorite = track.id in favoriteTrackIds,
                                            playCount = stats[track.id]?.playCount ?: 0,
                                            onAutoTagClick = { viewModel.startTrackEnrichment(it) }
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
                                    state = albumsGridState
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
                                    state = playlistsListState
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ModalBottomSheet Coda di Riproduzione
    if (showQueueSheet) {
        QueueBottomSheet(
            currentTrack = currentTrack,
            userQueue = userQueue,
            autoplayQueue = autoplayQueue,
            isPlaying = isPlaying,
            onDismissRequest = { showQueueSheet = false },
            onSkipToUserQueueItem = { index -> viewModel.skipToUserQueueItem(index) },
            onRemoveUserQueueItem = { index -> viewModel.removeUserQueueItem(index) },
            onMoveUserQueueItem = { fromIndex, toIndex -> viewModel.moveUserQueueItem(fromIndex, toIndex) },
            onMoveUserQueueItemToTop = { index -> viewModel.moveUserQueueItemToTop(index) },
            onClearUserQueue = { viewModel.clearUserQueue() },
            onSkipToAutoplayItem = { index -> viewModel.skipToAutoplayItem(index) },
            onRemoveAutoplayItem = { index -> viewModel.removeAutoplayItem(index) },
            onMoveAutoplayItem = { fromIndex, toIndex -> viewModel.moveAutoplayItem(fromIndex, toIndex) },
            onMoveAutoplayItemToTop = { index -> viewModel.moveAutoplayItemToTop(index) }
        )
    }

    // ModalBottomSheet Player a tutto schermo (Full Screen Player)
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
            onDismissRequest = { showFullScreenPlayer = false },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onPreviousClick = { viewModel.playPrevious() },
            onNextClick = { viewModel.playNext() },
            onShuffleClick = { viewModel.toggleShuffle() },
            onRepeatClick = { viewModel.toggleRepeatMode() },
            onToggleAutoplay = { viewModel.toggleAutoplay() },
            onQueueClick = { showQueueSheet = true },
            onSeekTo = { seekMs -> viewModel.seekTo(seekMs) },
            onSkipToQueueItem = { index -> viewModel.skipToQueueItem(index) },
            currentLyrics = currentLyrics,
            onAdjustLyricsOffset = { deltaMs -> viewModel.adjustLyricsOffset(deltaMs) },
            onAddToQueueClick = handleAddTrackToQueue,
            onAddToPlaylistClick = { trackToAddToPlaylist = it },
            onAddToFavoritesClick = handleAddToFavorites,
            isFavorite = currentTrack?.id in favoriteTrackIds,
            onAutoTagClick = { viewModel.startTrackEnrichment(it) },
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
                        selectedAlbum = matchingAlbum
                        selectedPlaylist = null
                        showFullScreenPlayer = false
                    }
                }
            }
        )
    }

    // Dialog Nuova Playlist
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name, coverUri ->
                viewModel.createPlaylist(name, coverUri)
                showCreatePlaylistDialog = false
            }
        )
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
                            message = "\"${track.title}\" aggiunto a \"${playlist.name}\"",
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

    // Dialog Last.fm Scrobbler
    if (showLastFmDialog) {
        LastFmDialog(
            isLoggedIn = isLastFmLoggedIn,
            username = lastFmUsername,
            isLoading = lastFmAuthLoading,
            errorMessage = lastFmAuthError,
            lastStatusMessage = lastFmStatusMessage,
            pendingCount = pendingScrobblesCount,
            initialApiKey = lastFmCustomApiKey,
            initialSharedSecret = lastFmCustomSharedSecret,
            onLogin = { user, pass, apiKey, secret ->
                viewModel.loginLastFm(user, pass, apiKey, secret)
            },
            onLogout = { viewModel.logoutLastFm() },
            onSyncPending = { viewModel.syncOfflineScrobbles() },
            onDismiss = { showLastFmDialog = false }
        )
    }

    // Finestra Impostazioni a schermo intero
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onOpenLastFmDialog = {
                showLastFmDialog = true
            },
            onPickCustomFolder = {
                folderPickerLauncher.launch(null)
            }
        )
    }

    // Dialog Notifica Aggiornamento GitHub all'avvio
    if (isUpdateDialogVisible) {
        updateInfo?.let { info ->
            UpdateAvailableDialog(
                updateInfo = info,
                onDismiss = { viewModel.dismissUpdateDialog(neverShowAgainForThisVersion = false) },
                onNeverShowAgain = { viewModel.dismissUpdateDialog(neverShowAgainForThisVersion = true) }
            )
        }
    }

    // Foglio modale Smart Auto-Tagger & Metadata Enricher
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
                text = "Permessi Richiesti",
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
            Text(text = "Riprova")
        }
    }
}
