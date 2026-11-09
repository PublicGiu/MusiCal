package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.R
import com.example.musicplayer.data.LyricsData
import kotlinx.coroutines.delay

/**
 * Anticipo temporale automatico (150ms) per compensare la latenza vocale hardware e Bluetooth.
 */
private const val AUTO_SYNC_LEAD_MS = 150L

/**
 * Visualizzatore di testi sincronizzati in stile Karaoke fluido, con auto-scroll ottimizzato,
 * centratura della riga attiva nel viewport, protezione durante lo scorrimento manuale,
 * tap-to-seek interattivo e gestione dedicata dei brani strumentali.
 */
@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isInstrumental: Boolean = lyricsData?.isInstrumental == true
) {
    if (isInstrumental) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.lyrics_no_lyrics_for_track),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lyrics_instrumental),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    if (lyricsData == null || lyricsData.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.lyrics_no_lyrics_available),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lyrics_use_auto_tagger),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val lines = lyricsData.lines
    val isSynced = lyricsData.isSynced

    // Trova l'indice del verso correntemente attivo applicando la compensazione automatica
    val effectivePosition = (currentPositionMs + AUTO_SYNC_LEAD_MS).coerceAtLeast(0L)
    val activeIndex = remember(effectivePosition, lines, isSynced) {
        if (!isSynced || lines.isEmpty()) -1
        else {
            val idx = lines.indexOfLast { it.timeMs in 0..effectivePosition }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    // Sospensione dell'auto-scroll quando l'utente scorre manualmente con il dito, con riaggancio morbido
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            delay(2500L)
            isUserScrolling = false
        }
    }

    // Scorrimento automatico fluido con centratura della riga attiva nel viewport
    LaunchedEffect(activeIndex, isUserScrolling) {
        if (activeIndex >= 0 && !isUserScrolling && lines.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val activeItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }

            val scrollOffset = if (activeIndex == 0) {
                0
            } else if (viewportHeight > 0) {
                val itemHeight = activeItemInfo?.size ?: 50
                -((viewportHeight - itemHeight) / 2)
            } else {
                0
            }

            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = scrollOffset
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
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
                animationSpec = tween(durationMillis = 200),
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
