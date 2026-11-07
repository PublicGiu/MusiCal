package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import com.example.musicplayer.R
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.data.AudioTrack
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foglio modale della Coda di Riproduzione suddiviso in:
 * 1. Brano attualmente in riproduzione.
 * 2. Coda Utente ("In coda"): brani aggiunti manualmente, prioritaria rispetto all'autoplay.
 *    Nascosta se non ci sono brani aggiunti manualmente.
 * 3. Riproduzione Automatica ("Riproduzione automatica"): finestra rolling di 15 brani successivi,
 *    con controlli completi di rimozione (X), spostamento in cima (↑) e drag & drop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    currentTrack: AudioTrack?,
    userQueue: List<AudioTrack>,
    autoplayQueue: List<AudioTrack>,
    isPlaying: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipToUserQueueItem: (Int) -> Unit = {},
    onRemoveUserQueueItem: (Int) -> Unit = {},
    onMoveUserQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onMoveUserQueueItemToTop: (Int) -> Unit = {},
    onClearUserQueue: () -> Unit = {},
    onSkipToAutoplayItem: (Int) -> Unit = {},
    onRemoveAutoplayItem: (Int) -> Unit = {},
    onMoveAutoplayItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onMoveAutoplayItemToTop: (Int) -> Unit = {},
    onAddAutoplayItemToUserQueue: (Int) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(bottom = 24.dp)
        ) {
            // Intestazione Generale Foglio Coda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music_custom),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Coda di riproduzione",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 1. Scheda Brano in Riproduzione Corrente
                if (currentTrack != null) {
                    item(key = "current_playing_header") {
                        Text(
                            text = "IN RIPRODUZIONE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
                        )
                    }

                    item(key = "current_playing_card") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Copertina o icona
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    var isImageError by remember(currentTrack.id) { mutableStateOf(false) }

                                    if (currentTrack.albumArtUri != null && !isImageError) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(currentTrack.albumArtUri)
                                                .crossfade(true)
                                                .build(),
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
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AnimatedEqualizerBars(
                                                isPlaying = isPlaying,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Sezione Coda Utente (Visibile SOLO se l'utente ha effettivamente messo brani in coda)
                if (userQueue.isNotEmpty()) {
                    item(key = "user_queue_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "In coda (${userQueue.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onClearUserQueue) {
                                Text("Svuota coda", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    item(key = "user_queue_list") {
                        ReorderableTrackList(
                            tracks = userQueue,
                            onItemClick = onSkipToUserQueueItem,
                            onRemoveItem = onRemoveUserQueueItem,
                            onMoveToTop = onMoveUserQueueItemToTop,
                            onMoveItem = onMoveUserQueueItem
                        )
                    }
                }

                // 3. Sezione Riproduzione Automatica (15 brani successivi con rolling window)
                item(key = "autoplay_queue_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Riproduzione automatica (${autoplayQueue.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Successivi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (autoplayQueue.isEmpty()) {
                    item(key = "autoplay_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nessun brano successivo programmato",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item(key = "autoplay_queue_list") {
                        ReorderableTrackList(
                            tracks = autoplayQueue,
                            onItemClick = onSkipToAutoplayItem,
                            onRemoveItem = onRemoveAutoplayItem,
                            onMoveToTop = onMoveAutoplayItemToTop,
                            onMoveItem = onMoveAutoplayItem,
                            onSwipeLeftItem = onAddAutoplayItemToUserQueue
                        )
                    }
                }

                item(key = "queue_bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Lista riordinabile e fluida per elementi della coda con controlli uniformi:
 * - Rimozione ('X' o swipe a destra)
 * - Aggiunta in coda utente tramite swipe a sinistra (se abilitato, es. in Riproduzione automatica)
 * - Spostamento in cima ('↑')
 * - Drag & Drop stabile e privo di scatti o bug di layout
 */
@Composable
private fun ReorderableTrackList(
    tracks: List<AudioTrack>,
    onItemClick: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMoveToTop: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeLeftItem: ((Int) -> Unit)? = null
) {
    var localTracks by remember(tracks) { mutableStateOf(tracks) }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tracks) {
        if (draggingIndex == null) {
            localTracks = tracks
        }
    }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { 62.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        localTracks.forEachIndexed { index, track ->
            androidx.compose.runtime.key(track.id) {
                val isBeingDragged = draggingIndex == index
                val canMoveToTop = index > 0

                SwipeableQueueTrackItem(
                    key = track.id,
                    enabled = draggingIndex == null,
                    onSwipeRightToRemove = { onRemoveItem(index) },
                    onSwipeLeftToQueue = onSwipeLeftItem?.let { cb -> { cb(index) } },
                    modifier = Modifier.zIndex(if (isBeingDragged) 10f else 1f)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                            .graphicsLayer {
                                translationY = if (isBeingDragged) dragOffsetY else 0f
                                scaleX = if (isBeingDragged) 1.02f else 1f
                                scaleY = if (isBeingDragged) 1.02f else 1f
                            }
                            .shadow(
                                elevation = if (isBeingDragged) 8.dp else 0.dp,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = draggingIndex == null) { onItemClick(index) },
                        color = if (isBeingDragged) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copertina
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                var isImageError by remember(track.id) { mutableStateOf(false) }
                                val context = LocalContext.current
                                val imageRequest = remember(track.albumArtUri) {
                                    track.albumArtUri?.let { uri ->
                                        ImageRequest.Builder(context)
                                            .data(uri)
                                            .crossfade(true)
                                            .build()
                                    }
                                }

                                if (imageRequest != null && !isImageError) {
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
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Dettagli brano
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Pulsante Rimuovi ('X')
                            IconButton(
                                onClick = { onRemoveItem(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Rimuovi",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Pulsante Sposta in cima ('↑')
                            IconButton(
                                onClick = { onMoveToTop(index) },
                                enabled = canMoveToTop,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Sposta in cima",
                                    tint = if (canMoveToTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Maniglia Drag & Drop per riordinare
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pointerInput(localTracks.size) {
                                        detectDragGestures(
                                            onDragStart = {
                                                initialDragIndex = index
                                                draggingIndex = index
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
                                                    onMoveItem(from, to)
                                                }
                                                initialDragIndex = null
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                initialDragIndex = null
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                                localTracks = tracks
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Trascina per riordinare",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenitore che abilita i gesti di swipe orizzontale sulle voci della coda di riproduzione:
 * - Swipe a destra: rimuove il brano dalla lista corrente (sia in Coda Utente sia in Riproduzione Automatica).
 * - Swipe a sinistra (se onSwipeLeftToQueue != null): promuove il brano nella Coda Utente prioritaria.
 */
@Composable
private fun SwipeableQueueTrackItem(
    key: Any,
    enabled: Boolean,
    onSwipeRightToRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeLeftToQueue: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxElasticOffsetPx = with(density) { 120.dp.toPx() }

    val currentOnSwipeRight by rememberUpdatedState(onSwipeRightToRemove)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeftToQueue)

    val swipeOffsetX = remember(key) { Animatable(0f) }

    val isRightThresholdReached by remember {
        derivedStateOf { swipeOffsetX.value >= thresholdPx }
    }
    val isLeftThresholdReached by remember {
        derivedStateOf { swipeOffsetX.value <= -thresholdPx }
    }

    val swipeProgress by remember {
        derivedStateOf {
            (abs(swipeOffsetX.value) / thresholdPx).coerceIn(0f, 1f)
        }
    }

    val isSwipingRight by remember {
        derivedStateOf { swipeOffsetX.value > 1f }
    }
    val isSwipingLeft by remember {
        derivedStateOf { swipeOffsetX.value < -1f }
    }

    val iconScale by animateFloatAsState(
        targetValue = when {
            isRightThresholdReached || isLeftThresholdReached -> 1.18f
            swipeProgress > 0.05f -> lerp(0.55f, 1.0f, swipeProgress)
            else -> 0.55f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "QueueSwipeIconScale"
    )

    val rightBgColor by animateColorAsState(
        targetValue = if (isRightThresholdReached) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = (0.35f + 0.55f * swipeProgress).coerceIn(0f, 1f))
        },
        animationSpec = tween(durationMillis = 150),
        label = "QueueSwipeRightBgColor"
    )

    val rightIconTint by animateColorAsState(
        targetValue = if (isRightThresholdReached) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = tween(durationMillis = 150),
        label = "QueueSwipeRightIconTint"
    )

    val leftBgColor by animateColorAsState(
        targetValue = if (isLeftThresholdReached) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = (0.35f + 0.55f * swipeProgress).coerceIn(0f, 1f))
        },
        animationSpec = tween(durationMillis = 150),
        label = "QueueSwipeLeftBgColor"
    )

    val leftIconTint by animateColorAsState(
        targetValue = if (isLeftThresholdReached) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 150),
        label = "QueueSwipeLeftIconTint"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(key, enabled, onSwipeLeftToQueue != null) {
                if (!enabled) return@pointerInput
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
                                // Movimento verticale dominante (scroll o riordino verticale)
                                gestureRejected = true
                                break
                            } else if (deltaX < -touchSlopPx && currentOnSwipeLeft == null) {
                                // Swipe a sinistra disabilitato (es. già nella coda utente)
                                gestureRejected = true
                                break
                            } else if (absX > touchSlopPx && absX > absY * 1.15f) {
                                if (deltaX > 0f || currentOnSwipeLeft != null) {
                                    isHorizontalSwipe = true
                                }
                            }
                        }

                        if (isHorizontalSwipe) {
                            change.consume()
                            if (deltaX >= 0f) {
                                val rawRightDrag = (deltaX - touchSlopPx).coerceAtLeast(0f)
                                val elasticDistance = if (rawRightDrag <= thresholdPx) {
                                    rawRightDrag
                                } else {
                                    thresholdPx + (rawRightDrag - thresholdPx) * 0.45f
                                }
                                val clampedOffset = elasticDistance.coerceIn(0f, maxElasticOffsetPx)
                                coroutineScope.launch {
                                    swipeOffsetX.snapTo(clampedOffset)
                                }
                            } else if (currentOnSwipeLeft != null) {
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
                            } else {
                                coroutineScope.launch {
                                    swipeOffsetX.snapTo(0f)
                                }
                            }
                        }
                    }

                    if (isHorizontalSwipe) {
                        val finalOffset = swipeOffsetX.value
                        if (finalOffset >= thresholdPx) {
                            currentOnSwipeRight()
                        } else if (finalOffset <= -thresholdPx && currentOnSwipeLeft != null) {
                            currentOnSwipeLeft?.invoke()
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
            }
    ) {
        if (isSwipingRight) {
            // Sfondo rosso/errore rivelato a sinistra durante lo swipe verso destra (Rimozione)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(rightBgColor)
                    .padding(start = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Rimuovi dalla lista",
                    tint = rightIconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            alpha = swipeProgress.coerceIn(0.25f, 1f)
                        }
                )
            }
        } else if (isSwipingLeft && currentOnSwipeLeft != null) {
            // Sfondo primario/viola rivelato a destra durante lo swipe verso sinistra (Metti in coda)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(leftBgColor)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music_custom),
                    contentDescription = "Metti in coda",
                    tint = leftIconTint,
                    modifier = Modifier
                        .size(22.dp)
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
