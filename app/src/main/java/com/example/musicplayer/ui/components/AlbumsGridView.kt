package com.example.musicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.musicplayer.R
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.isMixedOrGeneric

@Composable
fun AlbumsGridView(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    isAlphabetical: Boolean = false,
    letterProvider: ((Int) -> String?)? = null
) {
    if (albums.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.no_albums_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = albums,
                    key = { it.id },
                    contentType = { "album_grid_item" }
                ) { album ->
                    AlbumGridItem(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null
                        ),
                        album = album,
                        onClick = { onAlbumClick(album) },
                        onAddToQueueClick = onAddToQueueClick,
                        onAutoTagAlbumClick = onAutoTagAlbumClick,
                        onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick
                    )
                }
            }

            if (albums.isNotEmpty()) {
                FastScrollbar(
                    gridState = state,
                    totalItems = albums.size,
                    isAlphabetical = isAlphabetical,
                    letterProvider = letterProvider,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 8.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Copertina quadrata dell'album
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(album.id) { mutableStateOf(false) }
                val context = LocalContext.current
                val imageRequest = remember(album.albumArtUri) {
                    album.albumArtUri?.let { uri ->
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(360)
                            .precision(Precision.INEXACT)
                            .allowHardware(true)
                            .build()
                    }
                }

                if (imageRequest != null && !isImageError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.album_cover_content_desc, album.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageError = true }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dettagli Album e Menu Opzioni
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Titolo Album
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Artista
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Conteggio brani
                    val tracksCountText = if (album.trackCount == 1) {
                        stringResource(R.string.album_songs_count_single)
                    } else {
                        stringResource(R.string.album_songs_count_multiple, album.trackCount)
                    }
                    Text(
                        text = tracksCountText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                if (onAddToQueueClick != null || onAutoTagAlbumClick != null) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.options_for_item, album.title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (onAddToQueueClick != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_add_to_queue)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_queue_music_custom),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToQueueClick(album)
                                    }
                                )
                            }
                            if (album.isMixedOrGeneric()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_separate_option)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAutoTagAlbumSeparationClick?.invoke(album) ?: onAutoTagAlbumClick?.invoke(album)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_unify_option)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAutoTagAlbumClick?.invoke(album)
                                    }
                                )
                            } else if (onAutoTagAlbumClick != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_autotag_option)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAutoTagAlbumClick(album)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
