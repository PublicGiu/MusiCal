package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.LyricsData

/**
 * Visualizzatore di testi sincronizzati in stile Karaoke con scorrimento automatico,
 * evidenziazione luminosa del verso attivo, tap-to-seek e regolazione fine offset +/- 0.5s.
 */
@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (lyricsData == null || lyricsData.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nessun testo sincronizzato disponibile per questo brano.\nUsa l'Auto-Tagger per cercarlo su LRCLIB.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val lines = lyricsData.lines
    val isSynced = lyricsData.isSynced

    // Trova l'indice del verso correntemente attivo in base a currentPositionMs
    val activeIndex = remember(currentPositionMs, lines, isSynced) {
        if (!isSynced) -1
        else {
            val idx = lines.indexOfLast { it.timeMs in 0..currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    // Scorrimento automatico fluido al verso corrente
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScrollIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isSynced) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Regola sync:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onAdjustOffset(-500L) }
                ) {
                    Text(
                        text = "-0.5s",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onAdjustOffset(500L) }
                ) {
                    Text(
                        text = "+0.5s",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { index, item -> "${index}_${item.timeMs}" }) { index, line ->
                val isActive = isSynced && index == activeIndex

                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isSynced -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
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
