package com.example.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.R
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.DuplicateTrackGroup
import com.example.musicplayer.data.findDuplicateTrackGroups
import com.example.musicplayer.data.isMixedOrGeneric
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun AlbumDetailView(
    album: Album,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayTrackIndex: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
    onAddToPlaylistClick: (AudioTrack) -> Unit,
    onAddToQueueClick: (AudioTrack) -> Unit,
    onAddAlbumToQueueClick: (Album) -> Unit,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
    onPlayPauseClick: (() -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null,
    onDeleteTracks: ((List<AudioTrack>) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    ignoredDuplicateKeys: Set<String> = emptySet(),
    onIgnoreDuplicates: ((List<DuplicateTrackGroup>) -> Unit)? = null,
    onRestoreIgnoredDuplicates: (() -> Unit)? = null,
    hasIgnoredDuplicates: Boolean = false
) {
    val isCurrentAlbumActive = currentTrack != null &&
        (currentTrack.albumId == album.id || album.tracks.any { it.id == currentTrack.id })
    val isAlbumPlaying = isPlaying && isCurrentAlbumActive

    val albumSongs = remember(album.tracks) {
        album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
    }

    val totalDurationMinutes = remember(albumSongs) {
        val totalMs = albumSongs.sumOf { it.duration }
        (totalMs / 60000).coerceAtLeast(0)
    }

    val duplicateGroups = remember(album.tracks, ignoredDuplicateKeys) {
        album.findDuplicateTrackGroups(ignoredDuplicateKeys)
    }
    var showDuplicatesSheet by remember { mutableStateOf(false) }

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
        label = "AlbumCollapseFraction"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Header compatto a riga singola: incastrato tra la freccia indietro (←) e il menu a tre puntini (⋮)
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
                    contentDescription = stringResource(R.string.back_to_albums)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Copertina adattiva dell'album (da 62dp a 44dp durante lo scroll)
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val sizePx = lerp(62.dp, 44.dp, collapseFractionState.value).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))
                        layout(sizePx, sizePx) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(album.id) { mutableStateOf(false) }

                if (album.albumArtUri != null && !isImageError) {
                    val context = LocalContext.current
                    val imageRequest = remember(album.albumArtUri) {
                        ImageRequest.Builder(context)
                            .data(album.albumArtUri)
                            .size(360)
                            .precision(Precision.INEXACT)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageError = true }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 3. Dettagli dell'album incastrati al centro (Titolo e Artista con basicMarquee)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (onArtistClick != null && album.artist.isNotBlank()) {
                                Modifier.clickable { onArtistClick(album.artist) }
                            } else Modifier
                        )
                        .basicMarquee()
                )
            }

            // 4. Pulsante circolare Play pieno identico a quello del mini-player con animazione Play/Pausa
            FilledIconButton(
                onClick = {
                    if (isCurrentAlbumActive) {
                        onPlayPauseClick?.invoke() ?: onPlayAllClick()
                    } else {
                        onPlayAllClick()
                    }
                },
                enabled = albumSongs.isNotEmpty(),
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AnimatedContent(
                    targetState = isAlbumPlaying,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) +
                            scaleIn(initialScale = 0.72f, animationSpec = tween(180)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(140)) +
                                    scaleOut(targetScale = 0.72f, animationSpec = tween(140))
                            )
                    },
                    label = "AlbumPlayPauseIconAnimation"
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
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.player_more_options)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
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
                            onAddAlbumToQueueClick(album)
                        }
                    )
                    if (duplicateGroups.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicates_manage_menu)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                showDuplicatesSheet = true
                            }
                        )
                    }
                    if (hasIgnoredDuplicates && onRestoreIgnoredDuplicates != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicates_restore_menu)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRestoreIgnoredDuplicates()
                            }
                        )
                    }
                    if (album.isMixedOrGeneric()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_separate_option)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoTagAlbumSeparationClick?.invoke(album) ?: onAutoTagAlbumClick?.invoke(album)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_unify_option)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoTagAlbumClick?.invoke(album)
                            }
                        )
                    } else if (onAutoTagAlbumClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_autotag_option)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoTagAlbumClick(album)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Banner intelligente per album misto o con brani separati
        if (album.isMixedOrGeneric() && album.tracks.size > 1) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        onAutoTagAlbumSeparationClick?.invoke(album) ?: onAutoTagAlbumClick?.invoke(album)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.album_mixed_banner_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.album_mixed_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Banner per brani duplicati nell'album
        if (duplicateGroups.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showDuplicatesSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val totalDuplicates = duplicateGroups.sumOf { it.tracks.size - 1 }
                        Text(
                            text = if (totalDuplicates == 1) {
                                stringResource(R.string.duplicates_banner_single)
                            } else {
                                stringResource(R.string.duplicates_banner_multiple, totalDuplicates)
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.duplicates_banner_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Lista brani dell'album con supporto allo swipe verso sinistra per mettere in coda
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(
                items = albumSongs,
                key = { _, track -> track.id },
                contentType = { _, _ -> "album_track_item" }
            ) { index, track ->
                SwipeToQueueTrackItem(
                    track = track,
                    onSwipeToQueue = { onAddToQueueClick(track) }
                ) {
                    TrackListItem(
                        track = track,
                        isSelected = currentTrack?.id == track.id,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        onClick = { onPlayTrackIndex(index) },
                        onAddToQueueClick = onAddToQueueClick,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onAddToFavoritesClick = onAddToFavoritesClick?.let { cb -> { cb(track) } },
                        isFavorite = track.id in favoriteTrackIds,
                        onAutoTagClick = onAutoTagTrackClick,
                        onArtistClick = onArtistClick
                    )
                }
            }

            // Riepilogo in fondo all'album: numero di brani e durata totale in minuti (es. "14 brani • 40 min")
            item(key = "album_footer_summary") {
                val trackCountText = if (albumSongs.size == 1) {
                    stringResource(R.string.album_songs_count_single)
                } else {
                    stringResource(R.string.album_songs_count_multiple, albumSongs.size)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.album_tracks_summary, trackCountText, totalDurationMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        // Foglio di gestione e rimozione dei brani duplicati nell'album
        if (showDuplicatesSheet && duplicateGroups.isNotEmpty()) {
            AlbumDuplicatesSheet(
                album = album,
                duplicateGroups = duplicateGroups,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onPlayTrack = { track ->
                    val idx = albumSongs.indexOfFirst { it.id == track.id }
                    if (idx >= 0) onPlayTrackIndex(idx)
                },
                onDeleteTracks = { tracksToDelete ->
                    showDuplicatesSheet = false
                    onDeleteTracks?.invoke(tracksToDelete)
                },
                onIgnoreDuplicates = { groups ->
                    showDuplicatesSheet = false
                    onIgnoreDuplicates?.invoke(groups)
                },
                onIgnoreGroup = { group ->
                    if (duplicateGroups.size <= 1) {
                        showDuplicatesSheet = false
                    }
                    onIgnoreDuplicates?.invoke(listOf(group))
                },
                onDismissRequest = { showDuplicatesSheet = false }
            )
        }
    }
}

/**
 * Contenitore che abilita il gesto di swipe orizzontale verso sinistra su una canzone
 * per metterla in coda di riproduzione con resistenza elastica e feedback visivo animato,
 * senza interferire con lo scorrimento verticale della LazyColumn.
 */
@Composable
internal fun SwipeToQueueTrackItem(
    track: AudioTrack,
    onSwipeToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = track.id,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxElasticOffsetPx = with(density) { 120.dp.toPx() }

    val swipeOffsetX = remember(key) { Animatable(0f) }
    val isThresholdReached by remember {
        derivedStateOf { swipeOffsetX.value <= -thresholdPx }
    }

    val swipeProgress by remember {
        derivedStateOf {
            (-swipeOffsetX.value / thresholdPx).coerceIn(0f, 1f)
        }
    }

    val iconScale by animateFloatAsState(
        targetValue = when {
            isThresholdReached -> 1.18f
            swipeProgress > 0.05f -> lerp(0.55f, 1.0f, swipeProgress)
            else -> 0.55f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SwipeQueueIconScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isThresholdReached) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = (0.35f + 0.55f * swipeProgress).coerceIn(0f, 1f))
        },
        animationSpec = tween(durationMillis = 150),
        label = "SwipeQueueBgColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isThresholdReached) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 150),
        label = "SwipeQueueIconTint"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pointerId = down.id
                    var isHorizontalSwipe = false
                    var gestureRejected = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) break
                        pointerId = change.id

                        val deltaX = change.position.x - down.position.x
                        val deltaY = change.position.y - down.position.y

                        if (!isHorizontalSwipe && !gestureRejected) {
                            val absX = abs(deltaX)
                            val absY = abs(deltaY)
                            if (absY > touchSlopPx && absY >= absX) {
                                // Movimento verticale dominante: lascia scorrere liberamente la LazyColumn
                                gestureRejected = true
                                break
                            } else if (deltaX > touchSlopPx) {
                                // Swipe verso destra: ignorato
                                gestureRejected = true
                                break
                            } else if (deltaX < -touchSlopPx && absX > absY * 1.15f) {
                                isHorizontalSwipe = true
                            }
                        }

                        if (isHorizontalSwipe) {
                            change.consume()
                            val rawLeftDrag = (-deltaX - touchSlopPx).coerceAtLeast(0f)
                            val elasticDistance = if (rawLeftDrag <= thresholdPx) {
                                rawLeftDrag
                            } else {
                                thresholdPx + (rawLeftDrag - thresholdPx) * 0.45f
                            }
                            val clampedOffset = (-elasticDistance).coerceIn(-maxElasticOffsetPx, 0f)
                            coroutineScope.launch {
                                swipeOffsetX.snapTo(clampedOffset)
                            }
                        }
                    }

                    if (isHorizontalSwipe) {
                        val triggered = swipeOffsetX.value <= -thresholdPx
                        if (triggered) {
                            onSwipeToQueue()
                        }
                        coroutineScope.launch {
                            swipeOffsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    } else if (swipeOffsetX.value != 0f) {
                        coroutineScope.launch {
                            swipeOffsetX.animateTo(0f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        // Sfondo animato con la nuova icona della coda rivelato a destra durante lo swipe verso sinistra
        if (swipeOffsetX.value < -1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music_custom),
                    contentDescription = stringResource(R.string.player_menu_add_to_queue),
                    tint = iconTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            alpha = swipeProgress.coerceIn(0.25f, 1f)
                        }
                )
            }
        }

        // Contenuto della riga del brano traslato orizzontalmente in fase di Draw GPU
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffsetX.value
                }
        ) {
            content()
        }
    }
}
