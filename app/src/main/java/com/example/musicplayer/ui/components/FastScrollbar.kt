package com.example.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.ui.theme.LocalMusicVioletNeon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Estrae l'intestazione alfabetica (A-Z o #) da una stringa di testo.
 * Rimuove eventuali caratteri di punteggiatura o virgolette iniziali (es. "(Don't Fear)..." -> "D")
 * e normalizza i caratteri accentati (es. "Édith Piaf" -> "E").
 */
fun extractAlphabeticalHeader(title: String): String {
    val trimmed = title.trim().trim('"', '\'', '(', '[', '{', '«', '“', '‘', '`')
    if (trimmed.isEmpty()) return "#"
    val firstChar = trimmed.first()
    return if (firstChar.isLetter()) {
        val normalized = java.text.Normalizer.normalize(firstChar.toString(), java.text.Normalizer.Form.NFD)
        val stripped = normalized.replace("\\p{Mn}+".toRegex(), "")
        (stripped.firstOrNull() ?: firstChar).uppercase()
    } else {
        "#"
    }
}

/**
 * Barra di scorrimento rapido (Fast Scroller) per [LazyListState].
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier,
    isAlphabetical: Boolean = false,
    letterProvider: ((Int) -> String?)? = null,
    thumbWidth: Dp = 4.dp,
    draggedThumbWidth: Dp = 8.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    draggedThumbColor: Color = LocalMusicVioletNeon
) {
    val isScrollInProgress = state.isScrollInProgress
    val firstVisibleIndex by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex }
    }
    val scrollProgress by remember(state, totalItems) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 1 || visibleItems.isEmpty()) return@derivedStateOf 0f

            val first = visibleItems.first()
            val firstIndex = first.index
            val firstOffset = state.firstVisibleItemScrollOffset
            val firstSize = first.size.coerceAtLeast(1)
            val itemProgress = (firstOffset.toFloat() / firstSize.toFloat()).coerceIn(0f, 1f)
            val floatIndex = firstIndex.toFloat() + itemProgress
            val maxScrollIndex = (totalItems - visibleItems.size).coerceAtLeast(1).toFloat()
            (floatIndex / maxScrollIndex).coerceIn(0f, 1f)
        }
    }
    val visibleItemsCount by remember(state) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }

    FastScrollbarCore(
        totalItems = totalItems,
        visibleItemsCount = visibleItemsCount,
        firstVisibleIndex = firstVisibleIndex,
        isScrollInProgress = isScrollInProgress,
        scrollProgress = scrollProgress,
        onScrollToIndex = { targetIndex -> state.scrollToItem(targetIndex) },
        modifier = modifier,
        isAlphabetical = isAlphabetical,
        letterProvider = letterProvider,
        thumbWidth = thumbWidth,
        draggedThumbWidth = draggedThumbWidth,
        thumbColor = thumbColor,
        draggedThumbColor = draggedThumbColor
    )
}

/**
 * Barra di scorrimento rapido (Fast Scroller) per [LazyGridState].
 */
@Composable
fun FastScrollbar(
    gridState: LazyGridState,
    totalItems: Int,
    modifier: Modifier = Modifier,
    isAlphabetical: Boolean = false,
    letterProvider: ((Int) -> String?)? = null,
    thumbWidth: Dp = 4.dp,
    draggedThumbWidth: Dp = 8.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    draggedThumbColor: Color = LocalMusicVioletNeon
) {
    val isScrollInProgress = gridState.isScrollInProgress
    val firstVisibleIndex by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex }
    }
    val scrollProgress by remember(gridState, totalItems) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 1 || visibleItems.isEmpty()) return@derivedStateOf 0f

            val first = visibleItems.first()
            val firstIndex = gridState.firstVisibleItemIndex
            val firstOffset = gridState.firstVisibleItemScrollOffset
            val firstHeight = first.size.height.coerceAtLeast(1)
            val itemProgress = (firstOffset.toFloat() / firstHeight.toFloat()).coerceIn(0f, 1f)
            val floatIndex = firstIndex.toFloat() + itemProgress
            val maxScrollIndex = (totalItems - visibleItems.size).coerceAtLeast(1).toFloat()
            (floatIndex / maxScrollIndex).coerceIn(0f, 1f)
        }
    }
    val visibleItemsCount by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size }
    }

    FastScrollbarCore(
        totalItems = totalItems,
        visibleItemsCount = visibleItemsCount,
        firstVisibleIndex = firstVisibleIndex,
        isScrollInProgress = isScrollInProgress,
        scrollProgress = scrollProgress,
        onScrollToIndex = { targetIndex ->
            // Allinea sempre alla riga (2 colonne) per garantire stabilità visiva ed evitare salti orizzontali
            val alignedIndex = (targetIndex / 2) * 2
            gridState.scrollToItem(alignedIndex)
        },
        modifier = modifier,
        isAlphabetical = isAlphabetical,
        letterProvider = letterProvider,
        thumbWidth = thumbWidth,
        draggedThumbWidth = draggedThumbWidth,
        thumbColor = thumbColor,
        draggedThumbColor = draggedThumbColor
    )
}

/**
 * Motore condiviso di rendering, calcolo geometrico e gestione gesti touch per il Fast Scroller.
 */
@Composable
private fun FastScrollbarCore(
    totalItems: Int,
    visibleItemsCount: Int,
    firstVisibleIndex: Int,
    isScrollInProgress: Boolean,
    scrollProgress: Float,
    onScrollToIndex: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
    isAlphabetical: Boolean = false,
    letterProvider: ((Int) -> String?)? = null,
    thumbWidth: Dp = 4.dp,
    draggedThumbWidth: Dp = 8.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    draggedThumbColor: Color = LocalMusicVioletNeon
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val touchSlopPx = viewConfig.touchSlop

    // Canale di scroll CONFLATED: assicura uno scroll rigorosamente sequenziale, scartando gli indici intermedi
    // obsoleti ed eliminando la cancellazione a raffica di coroutine che causava lo sfarfallio visivo degli item.
    val scrollChannel = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(scrollChannel) {
        for (targetIndex in scrollChannel) {
            onScrollToIndex(targetIndex)
        }
    }

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var activeIndex by remember { mutableIntStateOf(0) }

    val minThumbHeightPx = with(density) { 48.dp.toPx() }
    val thumbHeightPx by remember(totalItems, visibleItemsCount, trackHeightPx) {
        derivedStateOf {
            if (trackHeightPx <= 0f || totalItems <= 0 || visibleItemsCount >= totalItems) return@derivedStateOf 0f
            val ratio = visibleItemsCount.toFloat() / totalItems.toFloat()
            val maxHeight = trackHeightPx * 0.45f
            (trackHeightPx * ratio).coerceIn(minThumbHeightPx, maxHeight.coerceAtLeast(minThumbHeightPx))
        }
    }

    val maxThumbOffsetPx by remember(trackHeightPx, thumbHeightPx) {
        derivedStateOf { (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) }
    }

    val currentThumbOffsetPx by remember(isDragging, dragOffsetPx, scrollProgress, maxThumbOffsetPx) {
        derivedStateOf {
            if (isDragging) {
                dragOffsetPx
            } else {
                scrollProgress * maxThumbOffsetPx
            }
        }
    }

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible && thumbHeightPx > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 150 else 350, easing = FastOutSlowInEasing),
        label = "FastScrollbarAlpha"
    )

    val animatedThumbWidth by animateDpAsState(
        targetValue = if (isDragging) draggedThumbWidth else thumbWidth,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "FastScrollbarThumbWidth"
    )

    val currentLetter by remember(isAlphabetical, letterProvider, isDragging, activeIndex, firstVisibleIndex) {
        derivedStateOf {
            if (!isAlphabetical || letterProvider == null) return@derivedStateOf null
            val idx = if (isDragging) activeIndex else firstVisibleIndex
            letterProvider(idx)
        }
    }

    val activeThumbColor = if (isDragging) draggedThumbColor else thumbColor

    val isInteractive = isVisible && thumbHeightPx > 0f && totalItems > 1

    val gestureModifier = if (isInteractive) {
        Modifier.pointerInput(totalItems, trackHeightPx, thumbHeightPx, isInteractive) {
            if (trackHeightPx <= 0f || thumbHeightPx <= 0f) return@pointerInput
            val maxOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
            val thumbMarginPx = 12.dp.toPx()

            awaitEachGesture {
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val thumbTop = currentThumbOffsetPx - thumbMarginPx
                    val thumbBottom = currentThumbOffsetPx + thumbHeightPx + thumbMarginPx
                    val isTouchOnThumb = down.position.y in thumbTop..thumbBottom

                    if (isTouchOnThumb) {
                        down.consume()
                        isDragging = true
                        dragOffsetPx = (down.position.y - thumbHeightPx / 2f).coerceIn(0f, maxOffset)
                        var progress = (dragOffsetPx / maxOffset).coerceIn(0f, 1f)
                        var targetIndex = (progress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                        activeIndex = targetIndex
                        scrollChannel.trySend(targetIndex)

                        do {
                            val event = awaitPointerEvent()
                            val dragChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (dragChange.pressed) {
                                val dragAmount = dragChange.position.y - dragChange.previousPosition.y
                                if (dragAmount != 0f) {
                                    dragChange.consume()
                                    dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxOffset)
                                    progress = (dragOffsetPx / maxOffset).coerceIn(0f, 1f)
                                    val newIndex = (progress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                    if (newIndex != activeIndex) {
                                        activeIndex = newIndex
                                        scrollChannel.trySend(newIndex)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } else {
                        // Tocco FUORI dal pollice (es. sui 3 puntini o sulla freccia espandi):
                        // NON consumare il down! Attendere per verificare se l'utente trascina (drag) o fa un semplice tap.
                        var accumulatedDrag = 0f
                        var dragStarted = false

                        do {
                            val event = awaitPointerEvent()
                            val dragChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (dragChange.pressed) {
                                val dragAmount = dragChange.position.y - dragChange.previousPosition.y
                                accumulatedDrag += dragAmount

                                if (!dragStarted && abs(accumulatedDrag) > touchSlopPx) {
                                    dragStarted = true
                                    isDragging = true
                                    dragOffsetPx = (dragChange.position.y - thumbHeightPx / 2f).coerceIn(0f, maxOffset)
                                    val progress = (dragOffsetPx / maxOffset).coerceIn(0f, 1f)
                                    val newIndex = (progress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                    activeIndex = newIndex
                                    scrollChannel.trySend(newIndex)
                                }

                                if (dragStarted) {
                                    dragChange.consume()
                                    if (dragAmount != 0f) {
                                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxOffset)
                                        val progress = (dragOffsetPx / maxOffset).coerceIn(0f, 1f)
                                        val newIndex = (progress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                        if (newIndex != activeIndex) {
                                            activeIndex = newIndex
                                            scrollChannel.trySend(newIndex)
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                } finally {
                    isDragging = false
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .width(36.dp)
            .fillMaxHeight()
            .onSizeChanged { size ->
                trackHeightPx = size.height.toFloat()
            }
            .then(gestureModifier)
    ) {
        if (alpha > 0.01f && thumbHeightPx > 0f) {
            // Disegno Hardware Canvas del pollice e della traccia
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .graphicsLayer { this.alpha = alpha }
            ) {
                val widthPx = animatedThumbWidth.toPx()
                val rightPaddingPx = 4.dp.toPx()
                val left = size.width - rightPaddingPx - widthPx
                val top = currentThumbOffsetPx

                // Sottile linea di guida della traccia durante l'interazione
                if (isDragging || isVisible) {
                    val trackWidthPx = 2.dp.toPx()
                    val trackLeft = size.width - rightPaddingPx - (widthPx / 2f) - (trackWidthPx / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(trackLeft, 0f),
                        size = Size(trackWidthPx, size.height),
                        cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                    )
                }

                // Pollice arrotondato ad alta precisione
                drawRoundRect(
                    color = activeThumbColor,
                    topLeft = Offset(left, top),
                    size = Size(widthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f)
                )
            }

            // Badge alfabetico fluttuante alla sinistra della barra
            val badgeVisible = isAlphabetical && currentLetter != null && alpha > 0.1f
            val badgeAlpha by animateFloatAsState(
                targetValue = if (badgeVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "FastScrollbarBadgeAlpha"
            )
            val badgeScale by animateFloatAsState(
                targetValue = if (badgeVisible) 1f else 0.6f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "FastScrollbarBadgeScale"
            )

            if (badgeAlpha > 0.01f) {
                val badgeSize = 46.dp
                val badgeSizePx = with(density) { badgeSize.toPx() }
                val badgeXOffsetPx = with(density) { (-badgeSize - 10.dp).roundToPx() }

                Box(
                    modifier = Modifier
                        .offset {
                            val badgeYPx = (currentThumbOffsetPx + (thumbHeightPx - badgeSizePx) / 2f)
                                .coerceIn(0f, (trackHeightPx - badgeSizePx).coerceAtLeast(0f))
                            IntOffset(badgeXOffsetPx, badgeYPx.roundToInt())
                        }
                        .size(badgeSize)
                        .graphicsLayer {
                            this.alpha = badgeAlpha
                            this.scaleX = badgeScale
                            this.scaleY = badgeScale
                        }
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = LocalMusicVioletNeon.copy(alpha = 0.85f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentLetter ?: "",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                        },
                        label = "FastScrollbarLetterAnim"
                    ) { letter ->
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
