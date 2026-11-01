package com.example.musicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.res.painterResource
import com.example.musicplayer.R
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.data.AudioTrack

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: AudioTrack,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((AudioTrack) -> Unit)? = null,
    onAddToPlaylistClick: ((AudioTrack) -> Unit)? = null,
    onAddToFavoritesClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onRemoveClick: (() -> Unit)? = null,
    removeLabel: String = "Rimuovi da playlist",
    playCount: Int = 0
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasOptionsMenu = onAddToQueueClick != null || onAddToFavoritesClick != null || onAddToPlaylistClick != null || onRemoveClick != null

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (hasOptionsMenu) {
                    { menuExpanded = true }
                } else {
                    null
                }
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copertina dell'album con fallback a icona segnaposto
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(track.id) { mutableStateOf(false) }
                val context = LocalContext.current
                val imageRequest = remember(track.albumArtUri) {
                    track.albumArtUri?.let { uri ->
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(160)
                            .precision(Precision.INEXACT)
                            .allowHardware(true)
                            .build()
                    }
                }

                if (imageRequest != null && !isImageError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Copertina ${track.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageError = true }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Overlay con equalizzatore visivo se il brano è attualmente in riproduzione
                if (isSelected && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "In riproduzione",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Dettagli del brano (Titolo, Artista ed eventuale contatore ascolti)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playCount > 0) {
                        Text(
                            text = " • $playCount ${if (playCount == 1) "ascolto" else "ascolti"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Durata formattata
            Text(
                text = track.formattedDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Menu opzioni (tre puntini)
            if (onAddToQueueClick != null || onAddToFavoritesClick != null || onAddToPlaylistClick != null || onRemoveClick != null) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Altre opzioni",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
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
                                    onAddToQueueClick(track)
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
                                    onAddToFavoritesClick()
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
                                    onAddToPlaylistClick(track)
                                }
                            )
                        }
                        if (onRemoveClick != null) {
                            DropdownMenuItem(
                                text = { Text(removeLabel) },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
