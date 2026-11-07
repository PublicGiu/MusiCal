package com.example.musicplayer.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import com.example.musicplayer.R
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.LyricsData
import com.example.musicplayer.data.formatDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayerSheet(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    durationMs: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isAutoplayEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onQueueClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
    currentPositionProvider: () -> Long = { currentPositionMs },
    queue: List<AudioTrack> = emptyList(),
    currentQueueIndex: Int = 0,
    onSkipToQueueItem: (Int) -> Unit = {},
    onAlbumClick: (() -> Unit)? = null,
    currentLyrics: LyricsData? = null,
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onAddToQueueClick: ((AudioTrack) -> Unit)? = null,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    isFavorite: Boolean = false,
    onAutoTagClick: ((AudioTrack) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Coda effettiva per il Pager: usa la coda completa o la traccia corrente come fallback
    val effectiveQueue = remember(queue, currentTrack) {
        if (queue.isNotEmpty()) {
            queue
        } else if (currentTrack != null) {
            listOf(currentTrack)
        } else {
            emptyList()
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

    // Sincronizzazione con ExoPlayer: quando l'utente trascina manualmente la copertina e il Pager si assesta
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

    // Sincronizzazione inversa: se il brano cambia dall'esterno (fine brano, notifiche, tasti Next/Prev),
    // aggiorna la posizione del Pager con transizione animata senza innescare un falso swipe
    LaunchedEffect(currentQueueIndex, effectiveQueue) {
        if (effectiveQueue.isNotEmpty() && currentQueueIndex in 0 until pagerState.pageCount) {
            if (pagerState.currentPage != currentQueueIndex) {
                isProgrammaticScroll = true
                try {
                    pagerState.animateScrollToPage(currentQueueIndex)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }
    }

    // 1. Estrazione dinamica della Palette dalla copertina della traccia corrente (solo su cambio confermato)
    val defaultFallbackColor = Color(0xFF261947) // Viola profondo abbinato all'icona
    var currentCoverDominantColor by remember { mutableStateOf(defaultFallbackColor) }

    LaunchedEffect(currentTrack?.id) {
        val artUri = currentTrack?.albumArtUri
        if (artUri == null) {
            currentCoverDominantColor = defaultFallbackColor
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(artUri)
                    .size(128, 128) // Ridimensionamento per Palette: riduce consumo memoria e previene OOM
                    .allowHardware(false) // Necessario per consentire l'accesso ai pixel bitmap per Palette
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        // Ordine preferenziale per tema scuro: darkVibrant -> vibrant -> dominant -> fallback
                        val targetSwatch = palette.darkVibrantSwatch
                            ?: palette.vibrantSwatch
                            ?: palette.dominantSwatch
                        val extractedColor = targetSwatch?.rgb?.let { Color(it).copy(alpha = 1f) } ?: defaultFallbackColor
                        withContext(Dispatchers.Main) {
                            currentCoverDominantColor = extractedColor
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            currentCoverDominantColor = defaultFallbackColor
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        currentCoverDominantColor = defaultFallbackColor
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentCoverDominantColor = defaultFallbackColor
                }
            }
        }
    }

    // 2. Transizione animata del colore dominante (450 ms tween con FastOutSlowInEasing)
    val animatedDominantColor by animateColorAsState(
        targetValue = currentCoverDominantColor,
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutSlowInEasing
        ),
        label = "PlayerBackgroundGradientAnimation"
    )

    // 3. Effetto respiro e profondità della copertina grande (1.0f in riproduzione, 0.92f in pausa)
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FullScreenCoverBreathingScale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color(0xFF0E0B16),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0B16)) // Base solida opaca
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedDominantColor, // Colore solido pieno in cima
                            Color(0xFF1B142E),     // Transizione morbida viola scuro
                            Color(0xFF0A0712)      // Ossidiana notte profondo in basso
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Bar interna: freccia in basso + sorgente/album + bilanciamento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Riduci player a schermo intero",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (onAlbumClick != null) Modifier.clickable(onClick = onAlbumClick)
                            else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IN RIPRODUZIONE DALL'ALBUM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Text(
                        text = currentTrack?.album?.takeIf { it.isNotBlank() } ?: "Libreria locale",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // Menu contestuale a tre puntini (Altre opzioni per il brano in riproduzione)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Altre opzioni",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (currentTrack != null) {
                            if (onAddToQueueClick != null) {
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
                                        onAddToQueueClick(currentTrack)
                                    }
                                )
                            }
                            if (onAddToFavoritesClick != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isFavorite) "Aggiunto ai brani preferiti" else "Aggiungi ai brani preferiti"
                                        )
                                    },
                                    leadingIcon = {
                                        if (isFavorite) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.FavoriteBorder,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToFavoritesClick(currentTrack)
                                    }
                                )
                            }
                            if (onAddToPlaylistClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Aggiungi a playlist") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylistClick(currentTrack)
                                    }
                                )
                            }
                            if (onAutoTagClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Auto-tagga & testi") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAutoTagClick(currentTrack)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))

            if (isLyricsViewActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    SyncedLyricsView(
                        lyricsData = currentLyrics,
                        currentPositionMs = currentPositionProvider(),
                        onSeekTo = onSeekTo,
                        onAdjustOffset = onAdjustLyricsOffset
                    )
                }
            } else {
                // 2. Copertina grande con HorizontalPager (finger-following fluido, animazione a molla e sincronizzazione reattiva)
                HorizontalPager(
                    state = pagerState,
                    key = { page -> effectiveQueue.getOrNull(page)?.id ?: page },
                    beyondViewportPageCount = 1,
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                ) { page ->
                    val trackForPage = effectiveQueue.getOrNull(page)
                    val coverImageRequest = remember(trackForPage?.albumArtUri) {
                        trackForPage?.albumArtUri?.let { uri ->
                            ImageRequest.Builder(context)
                                .data(uri)
                                .size(800)
                                .precision(Precision.INEXACT)
                                .allowHardware(true)
                                .crossfade(true)
                                .build()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        var isImageError by remember(trackForPage?.id) { mutableStateOf(false) }

                        if (coverImageRequest != null && !isImageError) {
                            AsyncImage(
                                model = coverImageRequest,
                                contentDescription = "Copertina album ${trackForPage?.title ?: ""}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onError = { isImageError = true }
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White.copy(alpha = 0.1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Dettagli traccia (Titolo ad alto contrasto + Artista)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = currentTrack?.title ?: "Nessun brano in riproduzione",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrack?.artist ?: "Artista sconosciuto",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Barra di avanzamento isolata: legge currentPositionProvider() esclusivamente qui dentro
            FullScreenProgressBar(
                currentPositionProvider = currentPositionProvider,
                durationMs = durationMs,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Controlli di riproduzione principali (Shuffle, Prev, Play/Pause grande, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shuffle
                if (isShuffleEnabled) {
                    FilledTonalIconButton(
                        onClick = onShuffleClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle attivo"
                        )
                    }
                } else {
                    IconButton(
                        onClick = onShuffleClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Attiva shuffle",
                            tint = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                // Traccia precedente
                IconButton(
                    onClick = onPreviousClick,
                    enabled = currentTrack != null,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Brano precedente",
                        modifier = Modifier.size(36.dp),
                        tint = if (currentTrack != null) Color.White else Color.White.copy(alpha = 0.38f)
                    )
                }

                // Play / Pause circolare grande (72.dp) bianco con morphing animato fluido
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF140C26)
                    )
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180)) +
                                scaleIn(initialScale = 0.72f, animationSpec = tween(180)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(140)) +
                                        scaleOut(targetScale = 0.72f, animationSpec = tween(140))
                                )
                        },
                        label = "FullScreenPlayPauseIconMorph"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausa" else "Riproduci",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Traccia successiva
                IconButton(
                    onClick = onNextClick,
                    enabled = currentTrack != null,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Brano successivo",
                        modifier = Modifier.size(36.dp),
                        tint = if (currentTrack != null) Color.White else Color.White.copy(alpha = 0.38f)
                    )
                }

                // Repeat Mode
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat

                if (isRepeatActive) {
                    FilledTonalIconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Ripeti traccia corrente"
                                Player.REPEAT_MODE_ALL -> "Ripeti playlist"
                                else -> "Ripeti"
                            }
                        )
                    }
                } else {
                    IconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Attiva ripetizione",
                            tint = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Pulsanti ausiliari in basso: Testi sincronizzati (sinistra) e Coda di riproduzione (destra)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pulsante Testi Sincronizzati (in basso a sinistra)
                IconButton(onClick = { isLyricsViewActive = !isLyricsViewActive }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Testi sincronizzati",
                        modifier = Modifier.size(28.dp),
                        tint = if (isLyricsViewActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                    )
                }

                // Pulsante Coda di riproduzione (in basso a destra)
                IconButton(onClick = onQueueClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_queue_music_custom),
                        contentDescription = "Visualizza coda di riproduzione",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))
        }
    }
}

/**
 * Sezione SeekBar e timestamp isolata per il player a schermo intero.
 * Isola gli aggiornamenti di progresso a 250ms e disegna il pallino interamente in fase Draw (Canvas GPU).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenProgressBar(
    currentPositionProvider: () -> Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isDraggingSlider || isPressed || isDragged

    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
    val displayedSliderValue = if (isDraggingSlider) {
        dragPositionMs
    } else {
        currentPositionProvider().coerceIn(0L, safeDuration.toLong()).toFloat()
    }

    // Stato animato letto esclusivamente in drawBehind (fase di Draw GPU, 0 ricomposizioni)
    val interactionFractionState = animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "FullScreenThumbInteraction"
    )

    val displayedSeconds = displayedSliderValue.toLong() / 1000L
    val formattedCurrentTime = remember(displayedSeconds) {
        formatDurationMs(displayedSeconds * 1000L)
    }
    val durationSeconds = durationMs / 1000L
    val formattedDuration = remember(durationSeconds) {
        formatDurationMs(durationMs)
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayedSliderValue,
            onValueChange = { newValue ->
                isDraggingSlider = true
                dragPositionMs = newValue
            },
            onValueChangeFinished = {
                onSeekTo(dragPositionMs.toLong())
                isDraggingSlider = false
            },
            valueRange = 0f..safeDuration,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            colors = sliderColors,
            thumb = {
                val neonColor = Color(0xFFA855F7)
                Spacer(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            val fraction = interactionFractionState.value
                            if (fraction > 0.001f) {
                                val haloRadius = (18.dp.toPx()) * lerp(0.4f, 1.0f, fraction)
                                drawCircle(
                                    color = neonColor.copy(alpha = 0.50f * fraction),
                                    radius = haloRadius,
                                    center = center
                                )
                            }
                            val thumbRadius = (6.dp.toPx()) * lerp(1.0f, 1.65f, fraction)
                            drawCircle(
                                color = Color.White,
                                radius = thumbRadius,
                                center = center
                            )
                            val borderStroke = lerp(1.dp.toPx(), 1.5.dp.toPx(), fraction)
                            val borderColor = lerp(
                                Color.White.copy(alpha = 0.6f),
                                neonColor,
                                fraction
                            )
                            drawCircle(
                                color = borderColor,
                                radius = thumbRadius - borderStroke * 0.5f,
                                center = center,
                                style = Stroke(width = borderStroke)
                            )
                        }
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formattedCurrentTime,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
