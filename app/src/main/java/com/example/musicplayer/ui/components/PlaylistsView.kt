package com.example.musicplayer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.musicplayer.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.Playlist

@Composable
private fun CustomPlaylistCoverOrFallback(
    playlist: Playlist,
    imageSizePx: Int,
    fallback: @Composable () -> Unit
) {
    val coverUri = playlist.effectiveCoverUri
    var isImageError by remember(playlist.id, coverUri) { mutableStateOf(false) }

    if (coverUri != null && !isImageError) {
        val context = LocalContext.current
        val imageRequest = remember(coverUri, imageSizePx) {
            ImageRequest.Builder(context)
                .data(coverUri)
                .size(imageSizePx)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.cover_content_desc, playlist.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { isImageError = true }
        )
    } else {
        fallback()
    }
}

@Composable
fun PlaylistsView(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((Playlist) -> Unit)? = null,
    onChangeCoverClick: ((Playlist) -> Unit)? = null,
    onRemoveCoverClick: ((Playlist) -> Unit)? = null,
    state: LazyListState = rememberLazyListState()
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.no_playlists_created),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCreatePlaylistClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.create_first_playlist))
                    }
                }
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 80.dp // Spazio per il FAB
                )
            ) {
                items(
                    items = playlists,
                    key = { it.id },
                    contentType = { "playlist_item" }
                ) { playlist ->
                    PlaylistItem(
                        modifier = Modifier.animateItem(),
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDeleteClick = { onDeletePlaylistClick(playlist) },
                        onAddToQueueClick = onAddToQueueClick,
                        onChangeCoverClick = if (!playlist.isFavorites) onChangeCoverClick else null,
                        onRemoveCoverClick = if (!playlist.isFavorites) onRemoveCoverClick else null
                    )
                }
            }
        }

        // Floating Action Button per creare una nuova playlist
        FloatingActionButton(
            onClick = onCreatePlaylistClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_playlist))
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((Playlist) -> Unit)? = null,
    onChangeCoverClick: ((Playlist) -> Unit)? = null,
    onRemoveCoverClick: ((Playlist) -> Unit)? = null
) {
    val hasImageCover = playlist.isFavorites || playlist.effectiveCoverUri != null
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = if (hasImageCover) RoundedCornerShape(8.dp) else CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (playlist.isFavorites) {
                    Image(
                        painter = painterResource(R.drawable.cover_favorites),
                        contentDescription = stringResource(R.string.cover_content_desc, playlist.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CustomPlaylistCoverOrFallback(
                        playlist = playlist,
                        imageSizePx = 180
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (playlist.isFavorites) stringResource(R.string.playlist_favorites_title) else playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.trackCount == 1) stringResource(R.string.album_songs_count_single) else stringResource(R.string.album_songs_count_multiple, playlist.trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            var menuExpanded by remember { mutableStateOf(false) }
            val showMenu = !playlist.isFavorites || onAddToQueueClick != null
            if (showMenu) {
                Box {
                    val displayName = if (playlist.isFavorites) stringResource(R.string.playlist_favorites_title) else playlist.name
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.options_for_item, displayName),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onAddToQueueClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_add_to_queue)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_queue_music_custom),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueueClick(playlist)
                                }
                            )
                        }
                        if (!playlist.isFavorites && onChangeCoverClick != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (playlist.effectiveCoverUri != null) stringResource(R.string.change_cover) else stringResource(R.string.set_cover))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onChangeCoverClick(playlist)
                                }
                            )
                        }
                        if (!playlist.isFavorites && playlist.effectiveCoverUri != null && onRemoveCoverClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_cover)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveCoverClick(playlist)
                                }
                            )
                        }
                        if (!playlist.isFavorites) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.delete_playlist_action),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    playlistTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayTrackIndex: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((AudioTrack) -> Unit)? = null,
    onAddPlaylistToQueueClick: ((Playlist) -> Unit)? = null,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    onChangeCoverClick: ((Playlist) -> Unit)? = null,
    onRemoveCoverClick: ((Playlist) -> Unit)? = null,
    onPlayPauseClick: (() -> Unit)? = null,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    val isCurrentPlaylistActive = currentTrack != null && playlistTracks.any { it.id == currentTrack.id }
    val isPlaylistPlaying = isPlaying && isCurrentPlaylistActive

    val listState = rememberLazyListState()
    var isScrolledDown by remember { mutableStateOf(false) }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && (!listState.canScrollBackward || listState.firstVisibleItemScrollOffset <= 8)
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            isScrolledDown = false
        }
    }

    val nestedScrollConnection = rememberCollapsingScrollConnection(
        collapseThreshold = -18f,
        expandThreshold = 20f,
        onCollapse = { if (!isScrolledDown) isScrolledDown = true },
        onExpand = { if (isAtTop && isScrolledDown) isScrolledDown = false }
    )

    // Unico stato di animazione guida letto esclusivamente nella fase di Layout (0 ricomposizioni)
    val collapseFractionState = animateFloatAsState(
        targetValue = if (isScrolledDown) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "PlaylistCollapseFraction"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Header compatto a riga singola identico a quello degli Album: incastrato tra freccia indietro (←) e menu a tre puntini (⋮)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .layout { measurable, constraints ->
                    val vPad = lerp(8.dp, 4.dp, collapseFractionState.value).roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height + vPad * 2) {
                        placeable.placeRelative(0, vPad)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Freccia indietro a sinistra
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_to_playlists)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Copertina adattiva della playlist (da 62dp a 44dp durante lo scroll)
            Surface(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val sizePx = lerp(62.dp, 44.dp, collapseFractionState.value).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))
                        layout(sizePx, sizePx) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .let { baseModifier ->
                        if (!playlist.isFavorites && onChangeCoverClick != null) {
                            baseModifier.clickable { onChangeCoverClick(playlist) }
                        } else {
                            baseModifier
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (playlist.isFavorites) {
                    Image(
                        painter = painterResource(R.drawable.cover_favorites),
                        contentDescription = stringResource(R.string.playlist_favorites_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CustomPlaylistCoverOrFallback(
                        playlist = playlist,
                        imageSizePx = 360
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 3. Dettagli della playlist incastrati al centro (Titolo e Conteggio brani con basicMarquee)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val displayName = if (playlist.isFavorites) stringResource(R.string.playlist_favorites_title) else playlist.name
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                Text(
                    text = if (playlistTracks.size == 1) stringResource(R.string.album_songs_count_single) else stringResource(R.string.album_songs_count_multiple, playlistTracks.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            // 4. Pulsante circolare Play pieno identico a quello degli album e del mini-player con animazione Play/Pausa
            FilledIconButton(
                onClick = {
                    if (isCurrentPlaylistActive) {
                        onPlayPauseClick?.invoke() ?: onPlayAllClick()
                    } else {
                        onPlayAllClick()
                    }
                },
                enabled = playlistTracks.isNotEmpty(),
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AnimatedContent(
                    targetState = isPlaylistPlaying,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) +
                            scaleIn(initialScale = 0.72f, animationSpec = tween(180)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(140)) +
                                    scaleOut(targetScale = 0.72f, animationSpec = tween(140))
                            )
                    },
                    label = "PlaylistPlayPauseIconAnimation"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // 5. Menu opzioni a tre puntini (⋮) a destra
            val showHeaderMenu = onAddPlaylistToQueueClick != null || (!playlist.isFavorites && onChangeCoverClick != null)
            if (showHeaderMenu) {
                var menuExpanded by remember { mutableStateOf(false) }
                val headerDisplayName = if (playlist.isFavorites) stringResource(R.string.playlist_favorites_title) else playlist.name
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.options_for_item, headerDisplayName)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onAddPlaylistToQueueClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_add_to_queue)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_queue_music_custom),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddPlaylistToQueueClick(playlist)
                                }
                            )
                        }
                        if (!playlist.isFavorites && onChangeCoverClick != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (playlist.effectiveCoverUri != null) stringResource(R.string.change_cover) else stringResource(R.string.set_cover))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onChangeCoverClick(playlist)
                                }
                            )
                        }
                        if (!playlist.isFavorites && playlist.effectiveCoverUri != null && onRemoveCoverClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_cover)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveCoverClick(playlist)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Lista brani contenuti nella playlist (o stato vuoto centrato e contestualizzato)
        if (playlistTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (playlist.isFavorites) {
                            Icons.Default.FavoriteBorder
                        } else {
                            Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (playlist.isFavorites) {
                            stringResource(R.string.empty_favorites_title)
                        } else {
                            stringResource(R.string.empty_playlist_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (playlist.isFavorites) {
                            stringResource(R.string.empty_favorites_desc)
                        } else {
                            stringResource(R.string.empty_playlist_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            var localTracks by remember { mutableStateOf(playlistTracks) }
            var initialDragIndex by remember { mutableStateOf<Int?>(null) }
            var draggingIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

            LaunchedEffect(playlistTracks) {
                if (draggingIndex == null) {
                    localTracks = playlistTracks
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(
                    items = localTracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "playlist_track_item" }
                ) { index, track ->
                    val currentIndex by rememberUpdatedState(index)
                    val isBeingDragged = draggingIndex == index
                    val itemModifier = if (isBeingDragged) {
                        Modifier
                            .zIndex(10f)
                            .graphicsLayer {
                                translationY = dragOffsetY
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 8f
                            }
                    } else {
                        Modifier.zIndex(1f)
                    }

                    val dragHandleComponent: (@Composable () -> Unit)? = if (!playlist.isFavorites && onMoveTrack != null && localTracks.size > 1) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pointerInput(track.id, localTracks.size) {
                                        detectDragGestures(
                                            onDragStart = {
                                                initialDragIndex = currentIndex
                                                draggingIndex = currentIndex
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val curr = draggingIndex ?: return@detectDragGestures
                                                val threshold = itemHeightPx * 0.7f
                                                if (dragOffsetY > threshold && curr < localTracks.size - 1) {
                                                    val next = curr + 1
                                                    val mutable = localTracks.toMutableList()
                                                    val item = mutable.removeAt(curr)
                                                    mutable.add(next, item)
                                                    localTracks = mutable
                                                    draggingIndex = next
                                                    dragOffsetY -= itemHeightPx
                                                } else if (dragOffsetY < -threshold && curr > 0) {
                                                    val prev = curr - 1
                                                    val mutable = localTracks.toMutableList()
                                                    val item = mutable.removeAt(curr)
                                                    mutable.add(prev, item)
                                                    localTracks = mutable
                                                    draggingIndex = prev
                                                    dragOffsetY += itemHeightPx
                                                }
                                            },
                                            onDragEnd = {
                                                val from = initialDragIndex
                                                val to = draggingIndex
                                                if (from != null && to != null && from != to) {
                                                    onMoveTrack(from, to)
                                                }
                                                initialDragIndex = null
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                initialDragIndex = null
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                                localTracks = playlistTracks
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.drag_to_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null

                    if (onAddToQueueClick != null && draggingIndex == null) {
                        SwipeToQueueTrackItem(
                            track = track,
                            onSwipeToQueue = { onAddToQueueClick(track) },
                            key = track.id.toString()
                        ) {
                            TrackListItem(
                                track = track,
                                isSelected = currentTrack?.id == track.id,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onClick = { onPlayTrackIndex(currentIndex) },
                                modifier = itemModifier,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onAddToFavoritesClick = if (!playlist.isFavorites) onAddToFavoritesClick?.let { cb -> { cb(track) } } else null,
                                isFavorite = track.id in favoriteTrackIds,
                                onRemoveClick = { onRemoveTrack(track.id) },
                                removeLabel = if (playlist.isFavorites) "Rimuovi dai brani preferiti" else "Rimuovi da playlist",
                                onAutoTagClick = onAutoTagTrackClick,
                                onArtistClick = onArtistClick,
                                dragHandle = dragHandleComponent
                            )
                        }
                    } else {
                        TrackListItem(
                            track = track,
                            isSelected = currentTrack?.id == track.id,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onPlayTrackIndex(currentIndex) },
                            modifier = itemModifier,
                            onAddToQueueClick = onAddToQueueClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onAddToFavoritesClick = if (!playlist.isFavorites) onAddToFavoritesClick?.let { cb -> { cb(track) } } else null,
                            isFavorite = track.id in favoriteTrackIds,
                            onRemoveClick = { onRemoveTrack(track.id) },
                            removeLabel = if (playlist.isFavorites) "Rimuovi dai brani preferiti" else "Rimuovi da playlist",
                            onAutoTagClick = onAutoTagTrackClick,
                            onArtistClick = onArtistClick,
                            dragHandle = dragHandleComponent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog per creare una nuova playlist con nome ed eventuale copertina personalizzata.
 */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedCoverUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (selectedCoverUri != null) {
                        AsyncImage(
                            model = selectedCoverUri,
                            contentDescription = stringResource(R.string.change_cover),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = stringResource(R.string.set_cover),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text(
                        text = if (selectedCoverUri != null) stringResource(R.string.change_cover) else stringResource(R.string.add_cover_optional),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), selectedCoverUri)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create_playlist_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Dialog per selezionare a quale playlist aggiungere un brano.
 */
@Composable
fun AddToPlaylistDialog(
    track: AudioTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylistClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_playlist_for_track, track.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_playlists_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val pinnedPlaylists = remember(playlists) {
                        val favorites = playlists.filter { it.isFavorites }
                        val others = playlists.filter { !it.isFavorites }
                        favorites + others
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        items(pinnedPlaylists) { playlist ->
                            val isFav = playlist.isFavorites
                            val hasCover = isFav || playlist.effectiveCoverUri != null
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectPlaylist(playlist) },
                                color = if (isFav) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFav) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Image(
                                                painter = painterResource(R.drawable.cover_favorites),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = if (hasCover) RoundedCornerShape(6.dp) else CircleShape,
                                            color = if (hasCover) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            }
                                        ) {
                                            CustomPlaylistCoverOrFallback(
                                                playlist = playlist,
                                                imageSizePx = 144
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFav) stringResource(R.string.playlist_favorites_title) else playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isFav) FontWeight.Bold else FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = if (playlist.trackCount == 1) stringResource(R.string.album_songs_count_single) else stringResource(R.string.album_songs_count_multiple, playlist.trackCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isFav) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateNewPlaylistClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.new_playlist))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
