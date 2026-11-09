package com.example.musicplayer.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Componente circolare per la visualizzazione della foto profilo dell'account Last.fm.
 * Supporta nativamente sia immagini statiche (PNG/JPG/WebP) sia avatar animati GIF
 * tramite [ImageDecoder] e [AnimatedImageDrawable] di Android 16 (API 36),
 * eseguendo l'animazione sul RenderThread senza overhead di ricomposizione Compose.
 */
@Composable
fun LastFmAvatar(
    avatarPath: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    var decodedDrawable by remember(avatarPath) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(avatarPath) {
        if (avatarPath.isNullOrBlank()) {
            decodedDrawable = null
            return@LaunchedEffect
        }
        val drawable = withContext(Dispatchers.IO) {
            try {
                val file = File(avatarPath)
                if (!file.exists() || file.length() == 0L) {
                    null
                } else {
                    val source = ImageDecoder.createSource(file)
                    ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
        decodedDrawable = drawable
    }

    DisposableEffect(decodedDrawable) {
        val current = decodedDrawable
        if (current is AnimatedImageDrawable) {
            current.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            current.start()
        }
        onDispose {
            if (current is AnimatedImageDrawable) {
                current.stop()
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val drawable = decodedDrawable
        if (drawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    if (imageView.drawable !== drawable) {
                        imageView.setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable && !drawable.isRunning) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                    }
                },
                onRelease = { imageView ->
                    (imageView.drawable as? AnimatedImageDrawable)?.stop()
                    imageView.setImageDrawable(null)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
