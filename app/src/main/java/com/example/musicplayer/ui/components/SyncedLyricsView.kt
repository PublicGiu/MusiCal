package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.R
import com.example.musicplayer.data.LyricLine
import com.example.musicplayer.data.LyricsData
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Anticipo temporale automatico (350ms) per compensare la latenza vocale hardware/DSP/Bluetooth
 * e la durata della transizione visiva di centramento, facendo sì che il verso sia già centrato
 * e illuminato all'attacco vocale.
 */
internal const val AUTO_SYNC_LEAD_MS = 350L

/**
 * Connessione NestedScroll che assorbe integralmente i delta verticali residui (overscroll/fling)
 * all'interno dell'area dei testi, impedendo che lo scorrimento in cima, in fondo o su testi brevi
 * attivi lo swipe-down della ModalBottomSheet o lo scroll del contenitore padre.
 */
internal object LyricsVerticalScrollBlocker : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = Offset(x = 0f, y = available.y)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
    ): Velocity = Velocity(x = 0f, y = available.y)
}

/**
 * Contenitore centrato per gli stati non scorrevoli dei testi (brano strumentale o assenza testo)
 * che assorbe localmente i trascinamenti verticali per prevenire chiusure accidentali tramite swipe-down.
 */
@Composable
private fun LyricsPlaceholderView(
    icon: ImageVector,
    iconTint: Color,
    badgeBackgroundColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(LyricsVerticalScrollBlocker)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeBackgroundColor,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Ricerca binaria O(log N) dell'indice del verso attivo al tempo [currentPositionMs] con anticipo [leadMs].
 */
internal fun findActiveLyricIndex(
    lines: List<LyricLine>,
    isSynced: Boolean,
    currentPositionMs: Long,
    leadMs: Long = AUTO_SYNC_LEAD_MS
): Int {
    if (!isSynced || lines.isEmpty()) return -1
    val effectivePosition = (currentPositionMs + leadMs).coerceAtLeast(0L)
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midTime = lines[mid].timeMs
        if (midTime < 0L) {
            high = mid - 1
        } else if (midTime <= effectivePosition) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return if (result >= 0) result else 0
}

/**
 * Visualizzatore di testi sincronizzati in stile Karaoke fluido, con auto-scroll rapido (220ms),
 * centratura della riga attiva nel viewport, protezione durante il trascinamento manuale dell'utente
 * (senza interferenze con l'auto-scroll programmatico), tap-to-seek interattivo e gestione dei brani strumentali.
 */
@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
    currentPositionProvider: () -> Long = { currentPositionMs },
    isInstrumental: Boolean = lyricsData?.isInstrumental == true
) {
    if (isInstrumental) {
        LyricsPlaceholderView(
            icon = Icons.Default.MusicNote,
            iconTint = MaterialTheme.colorScheme.primary,
            badgeBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            title = stringResource(R.string.lyrics_no_lyrics_for_track),
            subtitle = stringResource(R.string.lyrics_instrumental),
            modifier = modifier
        )
        return
    }

    if (lyricsData == null || lyricsData.lines.isEmpty()) {
        LyricsPlaceholderView(
            icon = Icons.Default.Lyrics,
            iconTint = Color.White.copy(alpha = 0.6f),
            badgeBackgroundColor = Color.White.copy(alpha = 0.1f),
            title = stringResource(R.string.lyrics_no_lyrics_available),
            subtitle = stringResource(R.string.lyrics_use_auto_tagger),
            modifier = modifier
        )
        return
    }

    val lines = lyricsData.lines
    val isSynced = lyricsData.isSynced

    // Calcola reattivamente l'indice attivo senza causare ricomposizioni finché il verso non cambia
    val activeIndex by remember(lines, isSynced, currentPositionProvider, currentPositionMs) {
        derivedStateOf {
            findActiveLyricIndex(
                lines = lines,
                isSynced = isSynced,
                currentPositionMs = currentPositionProvider()
            )
        }
    }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var hasInitialScrolled by remember(lyricsData.trackId) { mutableStateOf(false) }

    // Sospensione dell'auto-scroll SOLO quando l'utente trascina fisicamente la lista con il dito.
    // Lo scorrimento automatico programmatico non imposta mai isDragged = true e non si auto-cancella.
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            while (listState.isScrollInProgress) {
                delay(50L)
            }
            delay(1500L)
            isUserScrolling = false
        }
    }

    // Scorrimento automatico rapido (220ms) con centratura millimetrica della riga attiva nel viewport
    LaunchedEffect(activeIndex, isUserScrolling, lyricsData.trackId) {
        if (activeIndex >= 0 && !isUserScrolling && lines.isNotEmpty()) {
            val initialViewportHeight = listState.layoutInfo.viewportSize.height
            val initialVisible = listState.layoutInfo.visibleItemsInfo
            val initialActiveItem = initialVisible.firstOrNull { it.index == activeIndex }
            val avgItemHeight = if (initialVisible.isNotEmpty()) {
                initialVisible.sumOf { it.size } / initialVisible.size
            } else {
                120
            }
            val estimatedItemHeight = initialActiveItem?.size ?: avgItemHeight
            val estimatedScrollOffset = if (activeIndex == 0) {
                0
            } else if (initialViewportHeight > 0) {
                -((initialViewportHeight - estimatedItemHeight) / 2)
            } else {
                0
            }

            if (!hasInitialScrolled) {
                hasInitialScrolled = true
                listState.scrollToItem(
                    index = activeIndex,
                    scrollOffset = estimatedScrollOffset
                )
                val postItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                val vh = listState.layoutInfo.viewportSize.height
                if (postItem != null && vh > 0 && activeIndex > 0) {
                    val targetOffset = ((vh - postItem.size) / 2).coerceAtLeast(0)
                    val correction = (postItem.offset - targetOffset).toFloat()
                    if (abs(correction) > 1f) {
                        listState.scroll { scrollBy(correction) }
                    }
                }
                return@LaunchedEffect
            }

            // Se la riga di destinazione è fuori schermo (es. salto ampio tramite seekbar),
            // pre-posiziona vicino al target prima dell'animazione fluida di 220ms
            if (initialActiveItem == null) {
                val firstVisible = listState.firstVisibleItemIndex
                if (abs(activeIndex - firstVisible) > 6) {
                    val preJumpIndex = if (activeIndex > firstVisible) {
                        (activeIndex - 3).coerceAtLeast(0)
                    } else {
                        (activeIndex + 3).coerceAtMost(lines.lastIndex)
                    }
                    listState.scrollToItem(index = preJumpIndex, scrollOffset = estimatedScrollOffset)
                } else {
                    listState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = estimatedScrollOffset
                    )
                    return@LaunchedEffect
                }
            }

            val targetBeforeAnim = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (targetBeforeAnim == null) {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = estimatedScrollOffset
                )
            } else {
                listState.scroll {
                    var lastFraction = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) { fraction, _ ->
                        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                        val vh = listState.layoutInfo.viewportSize.height
                        if (currentItem != null && vh > 0) {
                            val targetOffset = if (activeIndex == 0) 0 else ((vh - currentItem.size) / 2).coerceAtLeast(0)
                            val remainingDelta = (currentItem.offset - targetOffset).toFloat()
                            val remainingFraction = 1f - lastFraction
                            if (remainingFraction > 0.001f && abs(remainingDelta) > 0.5f) {
                                val stepFraction = ((fraction - lastFraction) / remainingFraction).coerceIn(0f, 1f)
                                scrollBy(remainingDelta * stepFraction)
                            }
                        }
                        lastFraction = fraction
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(LyricsVerticalScrollBlocker)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {}
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { index, item -> "${index}_${item.timeMs}" }) { index, line ->
                val isActive = isSynced && index == activeIndex

                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isSynced -> Color.White.copy(alpha = 0.45f)
                        else -> Color.White.copy(alpha = 0.85f)
                    },
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    label = "lyricTextColor"
                )

                val fontSize = if (isActive) 22.sp else 18.sp
                val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = line.timeMs >= 0,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (line.timeMs >= 0) {
                                isUserScrolling = false
                                onSeekTo(line.timeMs)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            lineHeight = 28.sp
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
