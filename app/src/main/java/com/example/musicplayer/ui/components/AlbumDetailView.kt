package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val albumSongs = remember(album.tracks) {
        album.tracks.sortedWith(
            compareBy<AudioTrack> { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )
    }

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
                    contentDescription = "Torna agli album"
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

            // 3. Dettagli dell'album incastrati al centro (Titolo, Artista, Conteggio brani con basicMarquee)
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
                        .fillMaxWidth()
                        .basicMarquee()
                )
                Text(
                    text = "${album.trackCount} ${if (album.trackCount == 1) "brano" else "brani"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            // 4. Pulsante circolare Play pieno identico a quello del mini-player
            FilledIconButton(
                onClick = onPlayAllClick,
                enabled = albumSongs.isNotEmpty(),
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Riproduci",
                    modifier = Modifier.size(26.dp)
                )
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
                        contentDescription = "Altre opzioni per ${album.title}"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Aggiungi alla coda") },
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
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                        isFavorite = track.id in favoriteTrackIds
                    )
                }
            }
        }
    }
}

/**
 * Contenitore che abilita il gesto di swipe orizzontale verso sinistra su una canzone
 * per metterla in coda di riproduzione con resistenza elastica e feedback visivo animato,
 * senza interferire con lo scorrimento verticale della LazyColumn.
 */
@Composable
private fun SwipeToQueueTrackItem(
    track: AudioTrack,
    onSwipeToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxElasticOffsetPx = with(density) { 120.dp.toPx() }

    val swipeOffsetX = remember(track.id) { Animatable(0f) }
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
            .pointerInput(track.id) {
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
                    contentDescription = "Aggiungi alla coda",
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
