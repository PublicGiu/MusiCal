package com.example.musicplayer.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import com.example.musicplayer.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.ArtistInfo
import com.example.musicplayer.data.ArtistInfoRepository
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.extractMainArtist
import com.example.musicplayer.data.isCollabArtist
import com.example.musicplayer.data.isPartnerInCollab
import com.example.musicplayer.data.matchesArtist
import com.example.musicplayer.data.matchesCollabArtist
import com.example.musicplayer.ui.theme.LocalMusicVioletNeon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailSheet(
    artistName: String,
    tracks: List<AudioTrack>,
    albums: List<Album>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onDismissRequest: () -> Unit,
    onPlayTrackIndex: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
    onAddAllToQueueClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAddToQueueClick: ((AudioTrack) -> Unit)? = null,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null,
    artistInfoRepository: ArtistInfoRepository? = null,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    val artistListState = rememberLazyListState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden) {
                artistListState.firstVisibleItemIndex == 0
            } else {
                true
            }
        }
    )

    val artistSheetNestedScrollConnection = remember(artistListState) {
        object : NestedScrollConnection {
            private var isGestureActive = false
            private var dragStartedAtTop = false

            fun reset() {
                isGestureActive = false
                dragStartedAtTop = false
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (!isGestureActive) {
                        isGestureActive = true
                        val isAtTop = artistListState.firstVisibleItemIndex == 0 &&
                                artistListState.firstVisibleItemScrollOffset == 0
                        dragStartedAtTop = isAtTop
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    if (!dragStartedAtTop) {
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                reset()
                return if (available.y > 0f) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    LaunchedEffect(artistListState.isScrollInProgress) {
        if (!artistListState.isScrollInProgress) {
            artistSheetNestedScrollConnection.reset()
        }
    }

    val displayArtistName = remember(artistName) {
        if (isCollabArtist(artistName)) artistName.trim() else extractMainArtist(artistName).ifBlank { artistName }
    }

    val artistTracks = remember(artistName, tracks) {
        tracks.filter { it.matchesArtist(artistName) }
    }

    var isTracksExpanded by remember(artistName) { mutableStateOf(false) }

    val displayedTracks = remember(artistTracks, isTracksExpanded) {
        if (isTracksExpanded || artistTracks.size <= 10) artistTracks else artistTracks.take(10)
    }

    val artistAlbums = remember(artistName, albums) {
        albums.filter { it.matchesArtist(artistName) }
    }

    // Progetti collaborativi (di coppia) correlati per artisti solisti
    val relatedCollabs = remember(artistName, albums, tracks) {
        if (!isCollabArtist(artistName)) {
            val distinctCollabNames = (tracks.map { it.artist } + tracks.map { it.albumArtist } + albums.map { it.artist })
                .map { it.trim() }
                .filter { isCollabArtist(it) && isPartnerInCollab(artistName, it) }
                .distinct()

            distinctCollabNames.map { collabName ->
                val cTracks = tracks.filter { it.matchesCollabArtist(collabName) }
                val cAlbums = albums.filter { it.matchesCollabArtist(collabName) }
                val cover = cTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
                    ?: cAlbums.firstOrNull { it.albumArtUri != null }?.albumArtUri
                CollabProjectInfo(
                    name = collabName,
                    tracksCount = cTracks.size,
                    albumsCount = cAlbums.size,
                    coverUri = cover
                )
            }.filter { it.tracksCount > 0 || it.albumsCount > 0 }
        } else {
            emptyList()
        }
    }

    // Informazioni profilo artista (foto HD Deezer e biografia Wikipedia/Last.fm)
    val artistInfoFlow = remember(displayArtistName, artistInfoRepository) {
        if (displayArtistName.isNotBlank() && artistInfoRepository != null) {
            artistInfoRepository.getArtistInfoFlow(displayArtistName)
        } else null
    }
    val artistInfo by (artistInfoFlow?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf<ArtistInfo?>(null) })

    var showBiographyDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Copertina più rappresentativa dell'artista (primo brano con copertina)
    val artistCoverUri = remember(artistTracks) {
        artistTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    // Seleziona la foto autentica HD dall'artistInfo (Deezer), con fallback sulla copertina dell'album
    val displayPhotoModel = artistInfo?.photoUrl ?: artistCoverUri

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header: pulsante indietro / chiudi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.artist_close_desc)
                    )
                }

                Text(
                    text = stringResource(R.string.artist_view_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = { showEditSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = artistListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(artistSheetNestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Profilo Artista Hero
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { showEditSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayPhotoModel != null) {
                                AsyncImage(
                                    model = displayPhotoModel,
                                    contentDescription = artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = displayArtistName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Anteprima Biografia (2 righe, cliccabile per aprire la pagina a schermo intero)
                        val biographyText = artistInfo?.biography?.trim()
                        if (!biographyText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showBiographyDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = biographyText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val songsCount = if (artistTracks.size == 1) {
                            stringResource(R.string.album_songs_count_single)
                        } else {
                            stringResource(R.string.album_songs_count_multiple, artistTracks.size)
                        }
                        val albumsCount = if (artistAlbums.size == 1) {
                            stringResource(R.string.artist_album_count_single)
                        } else {
                            stringResource(R.string.artist_album_count_multiple, artistAlbums.size)
                        }
                        Text(
                            text = "$songsCount • $albumsCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Pulsanti azione: Riproduci tutti e Metti in coda
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onPlayAllClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.album_play_all))
                            }

                            OutlinedButton(
                                onClick = onAddAllToQueueClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_queue_music_custom),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.add_to_queue))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sezione Album (se presenti)
                if (artistAlbums.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.tab_albums),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(artistAlbums) { album ->
                                    Card(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clickable { onAlbumClick(album) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(114.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (album.albumArtUri != null) {
                                                    AsyncImage(
                                                        model = album.albumArtUri,
                                                        contentDescription = album.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Album,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = album.title,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val albumTrackCount = if (album.trackCount == 1) {
                                                stringResource(R.string.album_songs_count_single)
                                            } else {
                                                stringResource(R.string.album_songs_count_multiple, album.trackCount)
                                            }
                                            Text(
                                                text = albumTrackCount,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                // Sezione Brani con freccia espandi/comprimi a destra
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.tab_tracks),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (artistTracks.size > 10) {
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isTracksExpanded) 180f else 0f,
                                label = "expandTracksRotation"
                            )
                            IconButton(
                                onClick = { isTracksExpanded = !isTracksExpanded }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.keyboard_arrow_down_24),
                                    contentDescription = if (isTracksExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = rotationAngle }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = displayedTracks,
                    key = { index, track -> "${track.id}_$index" }
                ) { index, track ->
                    val isTenthItem = index == 9
                    val isPeekItem = !isTracksExpanded && isTenthItem && artistTracks.size > 10

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        SwipeToQueueTrackItem(
                            track = track,
                            onSwipeToQueue = { onAddToQueueClick?.invoke(track) }
                        ) {
                            TrackListItem(
                                track = track,
                                isSelected = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onClick = {
                                    val actualIndex = artistTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                    onPlayTrackIndex(actualIndex)
                                },
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onAddToFavoritesClick = onAddToFavoritesClick?.let { cb -> { cb(track) } },
                                isFavorite = track.id in favoriteTrackIds,
                                onAutoTagClick = onAutoTagTrackClick
                            )
                        }

                        if (isPeekItem) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            0.20f to Color.Transparent,
                                            0.75f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            1.0f to MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { isTracksExpanded = true }
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .border(
                                            width = 1.dp,
                                            color = LocalMusicVioletNeon.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                    shadowElevation = 4.dp
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.keyboard_arrow_down_24),
                                        contentDescription = stringResource(R.string.expand),
                                        tint = LocalMusicVioletNeon,
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 4.dp)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isTracksExpanded && artistTracks.size > 10) {
                    item(key = "collapse_tracks_pill") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(top = 6.dp, bottom = 12.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { isTracksExpanded = false }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = LocalMusicVioletNeon.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    ),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                shadowElevation = 4.dp
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.keyboard_arrow_up_24),
                                    contentDescription = stringResource(R.string.collapse),
                                    tint = LocalMusicVioletNeon,
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Sezione Collaborazioni (per artista solista con progetti congiunti di coppia)
                if (relatedCollabs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.artist_collaborations),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    items(relatedCollabs, key = { "collab_${it.name}" }) { collab ->
                        CollabProjectItem(
                            collab = collab,
                            onClick = { onNavigateToArtist?.invoke(collab.name) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

    if (showBiographyDialog && artistInfo != null) {
        ArtistBiographyDialog(
            artistName = displayArtistName,
            artistInfo = artistInfo,
            onDismissRequest = { showBiographyDialog = false }
        )
    }

    if (showEditSheet) {
        EditArtistSheet(
            artistName = displayArtistName,
            currentPhotoUrl = artistInfo?.photoUrl ?: artistCoverUri?.toString(),
            currentBio = artistInfo?.biography,
            isCustomPhoto = artistInfo?.isCustomPhoto == true,
            isCustomBio = artistInfo?.isCustomBio == true,
            artistAlbums = artistAlbums,
            onDismissRequest = { showEditSheet = false },
            onSavePhoto = { photoUri, albumArtUri, resetPhoto ->
                coroutineScope.launch {
                    when {
                        resetPhoto -> artistInfoRepository?.resetCustomArtistPhoto(displayArtistName)
                        photoUri != null -> artistInfoRepository?.setCustomArtistPhoto(displayArtistName, photoUri)
                        albumArtUri != null -> artistInfoRepository?.setCustomArtistAlbumCover(displayArtistName, albumArtUri)
                    }
                }
            },
            onSaveBio = { newBio, resetBio ->
                coroutineScope.launch {
                    if (resetBio) {
                        artistInfoRepository?.resetCustomArtistBio(displayArtistName)
                    } else if (newBio != artistInfo?.biography) {
                        artistInfoRepository?.setCustomArtistBio(displayArtistName, newBio)
                    }
                }
            }
        )
    }
}

/**
 * Informazioni sintetiche per la rappresentazione di un progetto di coppia/collaborativo.
 */
data class CollabProjectInfo(
    val name: String,
    val tracksCount: Int,
    val albumsCount: Int,
    val coverUri: Uri?
)

/**
 * Elemento d'elenco per un progetto collaborativo (es. "Drake & 21 Savage").
 * Mostra copertina, nome della coppia e statistiche (brani e album), consentendo
 * la navigazione al profilo di coppia dedicato.
 */
@Composable
fun CollabProjectItem(
    collab: CollabProjectInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (collab.coverUri != null) {
                    AsyncImage(
                        model = collab.coverUri,
                        contentDescription = collab.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collab.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val songsText = if (collab.tracksCount == 1) {
                    stringResource(R.string.album_songs_count_single)
                } else {
                    stringResource(R.string.album_songs_count_multiple, collab.tracksCount)
                }
                val albumsText = if (collab.albumsCount == 1) {
                    stringResource(R.string.artist_album_count_single)
                } else {
                    stringResource(R.string.artist_album_count_multiple, collab.albumsCount)
                }
                Text(
                    text = "$songsText • $albumsText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
