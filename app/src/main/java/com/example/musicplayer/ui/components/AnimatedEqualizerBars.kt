package com.example.musicplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Equalizzatore grafico a barre verticali animate a basso consumo computazionale (Draw phase GPU).
 *
 * - Quando [isPlaying] è true, 4 barre verticali oscillano asincronamente con curve morbide.
 * - Quando [isPlaying] è false, le barre si adagiano fluidamente a un'altezza di riposo minima.
 * - Disegnato interamente su Canvas GPU tramite [drawRoundRect], senza allocazioni o ricomposizioni superflue.
 */
@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqualizerInfiniteTransition")

    // Animazioni indipendenti per ogni barra con sfasamenti temporali asincroni
    val rawBar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar1"
    )

    val rawBar2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 310, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar2"
    )

    val rawBar3 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 510, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar3"
    )

    val rawBar4 by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 370, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar4"
    )

    // Transizione fluida verso lo stato di riposo quando la musica viene messa in pausa
    val bar1Height by animateFloatAsState(
        targetValue = if (isPlaying) rawBar1 else 0.28f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AnimatedBar1Rest"
    )
    val bar2Height by animateFloatAsState(
        targetValue = if (isPlaying) rawBar2 else 0.45f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AnimatedBar2Rest"
    )
    val bar3Height by animateFloatAsState(
        targetValue = if (isPlaying) rawBar3 else 0.32f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AnimatedBar3Rest"
    )
    val bar4Height by animateFloatAsState(
        targetValue = if (isPlaying) rawBar4 else 0.22f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AnimatedBar4Rest"
    )

    Canvas(
        modifier = modifier.size(24.dp)
    ) {
        val totalWidth = size.width
        val maxHeight = size.height

        val barFractions = when (barCount) {
            3 -> floatArrayOf(bar1Height, bar2Height, bar3Height)
            else -> floatArrayOf(bar1Height, bar2Height, bar3Height, bar4Height)
        }

        val count = barFractions.size
        // Calcolo dinamico dello spessore e della spaziatura delle barre
        val spacing = (totalWidth * 0.12f).coerceAtLeast(1.5f)
        val availableWidth = totalWidth - (spacing * (count - 1))
        val barWidth = (availableWidth / count).coerceAtLeast(2f)
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until count) {
            val fraction = barFractions[i].coerceIn(0.12f, 1f)
            val barHeight = maxHeight * fraction
            val left = i * (barWidth + spacing)
            val top = maxHeight - barHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
