package com.example.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.formatDurationMs
import com.example.musicplayer.ui.theme.LocalMusicVioletNeon
import kotlin.math.abs

/**
 * Mini-player compatto a pillola fluttuante con bordo luminescente e carosello interattivo (Material 3 Expressive).
 *
 * Caratteristiche principali:
 * - Full Pill Carousel su HorizontalPager: trascinando orizzontalmente il miniplayer, l'intera card/pillola
 *   (sfondo, barra di avanzamento, copertina, testi e pulsanti) segue fisicamente il dito rivelando la pillola
 *   completa del brano successivo (swipe a sinistra) o precedente (swipe a destra) della coda di riproduzione.
 * - Margini costanti di 12.dp a riposo e pageSpacing di 12.dp per mantenere il perfetto look da isola fluttuante.
 * - Cambio traccia SOLO al rilascio/assestamento (settledPage): durante lo swipe la musica continua normalmente,
 *   mentre il cambio traccia effettivo viene invocato solo al completamento dello snap sulla nuova pillola.
 * - Snap-back fluido se il gesto viene interrotto prima della soglia o trascinato all'indietro.
 * - Barra di scorrimento bianca a filo bordo superiore con espansione dinamica sincronizzata (2.5dp -> 6dp)
 *   e pallino bianco con alone viola neon (4dp -> 8.5dp) durante tocco e trascinamento.
 * - Badge fluttuante del minutaggio (Floating Time Badge) elevato a -40dp sopra il dito dell'utente per
 *   garantire massima visibilità e non essere mai coperto durante lo scrubbing.
 * - Gesti avanzati sul corpo del mini-player: tap rapido o swipe verso l'alto per aprire il player a tutto schermo,
 *   senza interferire con lo scorrimento orizzontale del carosello.
 * - Pulsante circolare viola (+) per aggiungere il brano a una playlist esistente o crearne una nuova.
 * - Pulsante circolare viola Play/Pausa reattivo.
 * - Titolo e artista con scorrimento automatico continuo basicMarquee().
 */
@Composable
fun PlayerBottomBar(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    durationMs: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
    currentPositionProvider: () -> Long = { currentPositionMs },
    isAutoplayEnabled: Boolean = true,
    onToggleAutoplay: () -> Unit = {},
    onOpenFullScreen: () -> Unit = {},
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null,
    queue: List<AudioTrack> = emptyList(),
    currentQueueIndex: Int = 0,
    onSkipToQueueItem: (Int) -> Unit = {}
) {
    if (currentTrack == null) return

    // Coda effettiva per il carosello del miniplayer
    val effectiveQueue = remember(queue, currentTrack) {
        if (queue.isNotEmpty()) {
            queue
        } else {
            listOf(currentTrack)
        }
    }

    val initialPage = remember {
        if (effectiveQueue.isEmpty()) 0
        else currentQueueIndex.coerceIn(0, effectiveQueue.size - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { effectiveQueue.size }
    )

    val currentOnSkipToQueueItem by rememberUpdatedState(onSkipToQueueItem)
    val latestQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestEffectiveQueue by rememberUpdatedState(effectiveQueue)
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val zeroPositionProvider = remember { { 0L } }

    // Sincronizzazione PagerState -> ExoPlayer: cambio traccia SOLO al rilascio/assestamento (settledPage)
    LaunchedEffect(pagerState) {
        var userDragged = false
        snapshotFlow { Triple(pagerState.isScrollInProgress, pagerState.settledPage, isProgrammaticScroll) }
            .collect { (inProgress, settledPage, programmatic) ->
                if (inProgress && !programmatic) {
                    userDragged = true
                }
                if (!inProgress && !programmatic && userDragged) {
                    userDragged = false
                    val queueSnapshot = latestEffectiveQueue
                    val targetQueueIdx = latestQueueIndex
                    if (queueSnapshot.isNotEmpty() && settledPage in queueSnapshot.indices && settledPage != targetQueueIdx) {
                        currentOnSkipToQueueItem(settledPage)
                    }
                }
            }
    }

    // Sincronizzazione inversa ExoPlayer -> PagerState (fine brano, notifiche, pulsanti esterni)
    LaunchedEffect(currentQueueIndex, effectiveQueue.size) {
        if (effectiveQueue.isNotEmpty() && currentQueueIndex in 0 until pagerState.pageCount) {
            if (pagerState.currentPage != currentQueueIndex) {
                isProgrammaticScroll = true
                try {
                    if (abs(pagerState.currentPage - currentQueueIndex) <= 2) {
                        pagerState.animateScrollToPage(currentQueueIndex)
                    } else {
                        pagerState.scrollToPage(currentQueueIndex)
                    }
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    val barWidthPx = remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .onSizeChanged { size ->
                val horizontalPaddingPx = with(density) { 24.dp.toPx() }
                barWidthPx.floatValue = (size.width - horizontalPaddingPx).coerceAtLeast(1f)
            }
    ) {
        // Carosello orizzontale a pillola intera (Full Pill Carousel)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { page -> if (page in effectiveQueue.indices) "${effectiveQueue[page].id}_$page" else "$page" }
        ) { page ->
            val track = effectiveQueue.getOrNull(page)
            val isCurrentTrackPage = (page == currentQueueIndex && track?.id == currentTrack.id)

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, LocalMusicVioletNeon.copy(alpha = 0.22f)),
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp
                ) {
                    PlayerTrackInfoRow(
                        track = track,
                        isCurrentTrack = isCurrentTrackPage,
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick,
                        onOpenFullScreen = onOpenFullScreen,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onPlayTrack = { onSkipToQueueItem(page) }
                    )
                }

                // Barra di avanzamento a filo del bordo superiore della pillola
                TopEdgeProgressBar(
                    currentPositionProvider = if (isCurrentTrackPage) currentPositionProvider else zeroPositionProvider,
                    durationMs = if (isCurrentTrackPage) durationMs else (track?.duration ?: 0L),
                    onSeekTo = onSeekTo,
                    isSeeking = if (isCurrentTrackPage) isSeeking else false,
                    onSeekingChange = { if (isCurrentTrackPage) isSeeking = it },
                    seekFraction = seekFraction,
                    onSeekFractionChange = { if (isCurrentTrackPage) seekFraction = it },
                    enabled = isCurrentTrackPage,
                    showThumb = isCurrentTrackPage,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        // Badge fluttuante del minutaggio: elevato a -40dp sopra il dito dell'utente per evitare ostruzioni visive
        FloatingTimeBadge(
            visible = isSeeking,
            seekFraction = seekFraction,
            durationMs = durationMs,
            barWidthPx = barWidthPx.floatValue,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
        )
    }
}

/**
 * Riga informativa del brano con copertina, testi a scorrimento (basicMarquee), pulsante (+) playlist e Play/Pausa.
 * Gestisce tap rapido e swipe-up verso il player a tutto schermo, lasciando lo swipe orizzontale a HorizontalPager.
 */
@Composable
private fun PlayerTrackInfoRow(
    track: AudioTrack?,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)?,
    onPlayTrack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val verticalThresholdPx = with(density) { 24.dp.toPx() }
    val currentOnOpenFullScreen by rememberUpdatedState(onOpenFullScreen)

    val imageRequest = remember(track?.albumArtUri) {
        track?.albumArtUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(160)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(true)
                .build()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sezione interattiva per tap rapido, swipe-up e swipe orizzontale (lasciato ad HorizontalPager)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isUpwardDrag = false
                        var isHorizontalDrag = false
                        var totalDragX = 0f
                        var totalDragY = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Tap rapido su rilascio senza trascinamento significativo
                                if (!isUpwardDrag && !isHorizontalDrag && abs(totalDragX) < 14f && abs(totalDragY) < 14f) {
                                    currentOnOpenFullScreen()
                                }
                                break
                            }

                            val deltaX = change.position.x - down.position.x
                            val deltaY = change.position.y - down.position.y
                            totalDragX = deltaX
                            totalDragY = deltaY

                            if (!isUpwardDrag && !isHorizontalDrag) {
                                if (abs(deltaY) > abs(deltaX) && deltaY < -verticalThresholdPx) {
                                    isUpwardDrag = true
                                    change.consume()
                                    currentOnOpenFullScreen()
                                    break
                                } else if (abs(deltaX) > 16f && abs(deltaX) > abs(deltaY)) {
                                    isHorizontalDrag = true
                                    // Non consuma l'evento: lascia che HorizontalPager gestisca lo scorrimento continuo
                                }
                            }

                            if (isUpwardDrag) {
                                change.consume()
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura copertina album (50dp x 50dp)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(track?.id) { mutableStateOf(false) }

                if (imageRequest != null && !isImageError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Copertina ${track?.title ?: ""}",
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

            // Titolo e Artista con basicMarquee a scorrimento continuo
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = track?.title ?: "Nessun brano in riproduzione",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track?.artist ?: "Seleziona una traccia dalla libreria",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Pulsante circolare viola (+) Aggiungi a playlist
        FilledIconButton(
            onClick = { track?.let { onAddToPlaylistClick?.invoke(it) } },
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Aggiungi a playlist",
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pulsante circolare viola Play / Pause con micro-animazione fluida
        FilledIconButton(
            onClick = {
                if (isCurrentTrack) {
                    onPlayPauseClick()
                } else {
                    onPlayTrack?.invoke()
                }
            },
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            AnimatedContent(
                targetState = isCurrentTrack && isPlaying,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) +
                        scaleIn(initialScale = 0.72f, animationSpec = tween(180)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.72f, animationSpec = tween(140))
                        )
                },
                label = "MiniPlayerPlayPauseIconAnimation"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausa" else "Riproduci",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Barra di avanzamento situata a filo del bordo superiore della pillola.
 *
 * Durante il riposo:
 * - Linea bianca sottile di 2.5dp.
 * - Pallino bianco discreto di raggio 4dp (se showThumb = true).
 *
 * Durante il tocco/trascinamento:
 * - Espansione dinamica a 6dp di spessore.
 * - Pallino ingrandito a raggio 8.5dp con alone viola neon (18dp).
 * - Calcolo di posizione al 100% fluido su Canvas GPU.
 */
@Composable
private fun TopEdgeProgressBar(
    currentPositionProvider: () -> Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    isSeeking: Boolean,
    onSeekingChange: (Boolean) -> Unit,
    seekFraction: Float,
    onSeekFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showThumb: Boolean = true
) {
    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
    val progressFraction = if (isSeeking) {
        seekFraction
    } else {
        (currentPositionProvider().toFloat() / safeDuration).coerceIn(0f, 1f)
    }

    val interactionFraction by animateFloatAsState(
        targetValue = if (isSeeking) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "MiniPlayerInteractionFraction"
    )

    val trackHeight = lerp(2.5.dp, 6.dp, interactionFraction)
    val thumbRadius = lerp(4.dp, 8.5.dp, interactionFraction)
    val haloRadius = lerp(8.dp, 18.dp, interactionFraction)

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val activeTrackColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
    val thumbColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
    val thumbStrokeBase = if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)

    val trackModifier = if (enabled) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(16.dp)
            .pointerInput(safeDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onSeekingChange(true)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var lastFrac = (down.position.x / width).coerceIn(0f, 1f)
                    onSeekFractionChange(lastFrac)

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) break
                        pointerId = change.id
                        change.consume()
                        lastFrac = (change.position.x / width).coerceIn(0f, 1f)
                        onSeekFractionChange(lastFrac)
                    }

                    onSeekTo((lastFrac * safeDuration).toLong())
                    onSeekingChange(false)
                }
            }
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(16.dp)
    }

    Box(modifier = trackModifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val width = size.width
            val h = trackHeight.toPx()
            val radius = CornerRadius(h / 2f, h / 2f)

            // 1. Traccia inattiva di sfondo (con estremità arrotondate)
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(0f, 0f),
                size = Size(width, h),
                cornerRadius = radius
            )

            // 2. Traccia attiva (con estremità arrotondate)
            val activeWidth = width * progressFraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, h),
                    cornerRadius = radius
                )
            }

            // 3. Pallino (thumb) con alone luminescente viola neon (solo se showThumb è true)
            if (showThumb) {
                val thumbCenter = Offset(activeWidth.coerceIn(0f, width), h / 2f)

                if (interactionFraction > 0.01f) {
                    drawCircle(
                        color = LocalMusicVioletNeon.copy(alpha = 0.45f * interactionFraction),
                        radius = haloRadius.toPx(),
                        center = thumbCenter
                    )
                }

                val r = thumbRadius.toPx()
                drawCircle(
                    color = thumbColor,
                    radius = r,
                    center = thumbCenter
                )

                val strokeW = lerp(1.dp.toPx(), 1.5.dp.toPx(), interactionFraction)
                drawCircle(
                    color = lerp(thumbStrokeBase, LocalMusicVioletNeon, interactionFraction),
                    radius = (r - strokeW * 0.5f).coerceAtLeast(0.5f),
                    center = thumbCenter,
                    style = Stroke(width = strokeW)
                )
            }
        }
    }
}

/**
 * Badge fluttuante del minutaggio (Floating Time Badge).
 *
 * Posizionato a -40dp sopra il bordo superiore della barra, garantisce che durante lo scorrimento
 * con il dito o il pollice, il minutaggio (mm:ss) resti perfettamente leggibile e privo di ostacoli.
 */
@Composable
private fun FloatingTimeBadge(
    visible: Boolean,
    seekFraction: Float,
    durationMs: Long,
    barWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val badgeWidth = 62.dp
    val badgeWidthPx = with(density) { badgeWidth.toPx() }
    val horizontalInsetPx = with(density) { 16.dp.toPx() }
    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.8f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
        modifier = modifier
    ) {
        val currentScrubMs = (seekFraction * safeDuration).toLong()
        val formattedTime = remember(currentScrubMs / 1000L) {
            formatDurationMs(currentScrubMs)
        }

        val badgeXOffset = with(density) {
            val trackWidthPx = (barWidthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
            val rawX = horizontalInsetPx + seekFraction * trackWidthPx
            val minX = 0f
            val maxX = (barWidthPx - badgeWidthPx).coerceAtLeast(0f)
            (rawX - badgeWidthPx / 2f).coerceIn(minX, maxX).toDp()
        }

        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val badgeBg = if (isDark) Color(0xFF221E32) else MaterialTheme.colorScheme.surfaceContainerHighest
        val badgeBorder = if (isDark) LocalMusicVioletNeon.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
        val badgeText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

        Box(
            modifier = Modifier
                .offset(x = badgeXOffset, y = (-40).dp)
                .width(badgeWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBg)
                .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeText
            )
        }
    }
}
