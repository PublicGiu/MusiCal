package com.example.musicplayer.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.data.AudioTrack

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
    onMoveAutoplayItemToTop: (Int) -> Unit = {}
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
                            onMoveItem = onMoveAutoplayItem
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
 * - Rimozione ('X')
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
    modifier: Modifier = Modifier
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

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                        .zIndex(if (isBeingDragged) 10f else 1f)
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
