package com.example.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Mini-player compatto a pillola fluttuante con bordo luminescente (Material 3 Expressive).
 *
 * Caratteristiche principali:
 * - Design compatto a riga singola (~64dp), riducendo l'ingombro verticale rispetto al layout precedente.
 * - Barra di scorrimento bianca a filo bordo superiore con espansione dinamica sincronizzata (2.5dp -> 6dp)
 *   e pallino bianco con alone viola neon (4dp -> 8.5dp) durante tocco e trascinamento.
 * - Badge fluttuante del minutaggio (Floating Time Badge) elevato a -40dp sopra il dito dell'utente per
 *   garantire massima visibilità e non essere mai coperto durante lo scrubbing.
 * - Gesti avanzati sul corpo del mini-player: swipe orizzontale (precedente/successivo con micro-feedback a molla),
 *   swipe verso l'alto (apertura player a tutto schermo) e tocco rapido (apertura player).
 * - Pulsante circolare viola (+) per aggiungere il brano a una playlist esistente o crearne una nuova.
 * - Pulsante circolare viola Play/Pausa.
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
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    val barWidthPx = remember { mutableFloatStateOf(1f) }
    val swipeOffsetX = remember { Animatable(0f) }

    // Garanzia di riallineamento a 0f ad ogni cambio brano
    LaunchedEffect(currentTrack?.id) {
        if (swipeOffsetX.value != 0f) {
            swipeOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .onSizeChanged { size -> barWidthPx.floatValue = size.width.toFloat() }
    ) {
        // Pillola fluttuante con traslazione a molla durante lo swipe orizzontale
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffsetX.value
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, LocalMusicVioletNeon.copy(alpha = 0.22f)),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp
            ) {
                PlayerTrackInfoRow(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onOpenFullScreen = onOpenFullScreen,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    swipeOffsetX = swipeOffsetX
                )
            }

            // Barra di avanzamento a filo del bordo superiore (zero ritardo, 120Hz GPU draw)
            TopEdgeProgressBar(
                currentPositionProvider = currentPositionProvider,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                isSeeking = isSeeking,
                onSeekingChange = { isSeeking = it },
                seekFraction = seekFraction,
                onSeekFractionChange = { seekFraction = it },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Badge fluttuante del minutaggio: elevato a -40dp sopra il dito dell'utente per evitare ostruzioni visive
        FloatingTimeBadge(
            visible = isSeeking,
            seekFraction = seekFraction,
            durationMs = durationMs,
            barWidthPx = barWidthPx.floatValue,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

/**
 * Riga informativa del brano con copertina, testi a scorrimento (basicMarquee), pulsante (+) playlist e Play/Pausa.
 * Gestisce swipe orizzontale, swipe-up e tocco rapido.
 */
@Composable
private fun PlayerTrackInfoRow(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)?,
    swipeOffsetX: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val verticalThresholdPx = with(density) { 24.dp.toPx() }
    val currentOnPreviousClick by rememberUpdatedState(onPreviousClick)
    val currentOnNextClick by rememberUpdatedState(onNextClick)
    val currentOnOpenFullScreen by rememberUpdatedState(onOpenFullScreen)

    val imageRequest = remember(currentTrack?.albumArtUri) {
        currentTrack?.albumArtUri?.let { uri ->
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
            .padding(top = 10.dp, bottom = 10.dp, start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sezione interattiva per tap, swipe orizzontale e swipe-up
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isHorizontalDrag = false
                        var isVerticalDrag = false
                        var actionTriggered = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val deltaX = change.position.x - down.position.x
                                val deltaY = change.position.y - down.position.y
                                totalDragX = deltaX
                                totalDragY = deltaY

                                if (!isHorizontalDrag && !isVerticalDrag) {
                                    if (abs(deltaY) > abs(deltaX) && deltaY < -verticalThresholdPx) {
                                        isVerticalDrag = true
                                        change.consume()
                                        actionTriggered = true
                                        currentOnOpenFullScreen()
                                        break
                                    } else if (abs(deltaX) > 18f && abs(deltaX) > abs(deltaY)) {
                                        isHorizontalDrag = true
                                    }
                                }

                                if (isHorizontalDrag && !actionTriggered) {
                                    change.consume()
                                    val dampedOffset = totalDragX * 0.4f
                                    coroutineScope.launch {
                                        swipeOffsetX.snapTo(dampedOffset.coerceIn(-120f, 120f))
                                    }

                                    if (totalDragX > swipeThresholdPx) {
                                        actionTriggered = true
                                        coroutineScope.launch {
                                            swipeOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        currentOnPreviousClick()
                                    } else if (totalDragX < -swipeThresholdPx) {
                                        actionTriggered = true
                                        coroutineScope.launch {
                                            swipeOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        currentOnNextClick()
                                    }
                                }
                            }

                            // Tap rapido se non c'è stato trascinamento significativo
                            if (!isHorizontalDrag && !isVerticalDrag && !actionTriggered && abs(totalDragX) < 12f && abs(totalDragY) < 12f) {
                                currentOnOpenFullScreen()
                            }
                        } finally {
                            // Garantisce SEMPRE il ritorno fluido a 0f
                            coroutineScope.launch {
                                swipeOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura copertina album (46dp x 46dp)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(currentTrack?.id) { mutableStateOf(false) }

                if (imageRequest != null && !isImageError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Copertina ${currentTrack?.title ?: ""}",
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
                    text = currentTrack?.title ?: "Nessun brano in riproduzione",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentTrack?.artist ?: "Seleziona una traccia dalla libreria",
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
            onClick = { currentTrack?.let { onAddToPlaylistClick?.invoke(it) } },
            modifier = Modifier.size(40.dp),
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

        // Pulsante circolare viola Play / Pause
        FilledIconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Barra di avanzamento situata a filo del bordo superiore della pillola.
 *
 * Durante il riposo:
 * - Linea bianca sottile di 2.5dp.
 * - Pallino bianco discreto di raggio 4dp.
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
    modifier: Modifier = Modifier
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(28.dp)
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
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val width = size.width
            val h = trackHeight.toPx()
            val radius = CornerRadius(h / 2f, h / 2f)

            // 1. Traccia inattiva di sfondo (bianco semitrasparente con estremità arrotondate)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, 0f),
                size = Size(width, h),
                cornerRadius = radius
            )

            // 2. Traccia attiva (bianco puro con estremità arrotondate)
            val activeWidth = width * progressFraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, h),
                    cornerRadius = radius
                )
            }

            // 3. Pallino (thumb) bianco con alone luminescente viola neon
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
                color = Color.White,
                radius = r,
                center = thumbCenter
            )

            val strokeW = lerp(1.dp.toPx(), 1.5.dp.toPx(), interactionFraction)
            drawCircle(
                color = lerp(Color.White.copy(alpha = 0.7f), LocalMusicVioletNeon, interactionFraction),
                radius = (r - strokeW * 0.5f).coerceAtLeast(0.5f),
                center = thumbCenter,
                style = Stroke(width = strokeW)
            )
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

        Box(
            modifier = Modifier
                .offset(x = badgeXOffset, y = (-40).dp)
                .width(badgeWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF221E32))
                .border(1.dp, LocalMusicVioletNeon.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}
