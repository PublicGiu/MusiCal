package com.example.musicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.musicplayer.R
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.SectionViewMode
import com.example.musicplayer.data.isMixedOrGeneric

@Composable
fun AlbumsGridView(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewMode: SectionViewMode = SectionViewMode.GRID_2,
    onAddToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState(),
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
    } else if (!viewMode.isGrid) {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(
                    items = albums,
                    key = { it.id },
                    contentType = { "album_list_item" }
                ) { album ->
                    AlbumListItem(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null
                        ),
                        album = album,
                        onClick = { onAlbumClick(album) },
                        onAddToQueueClick = onAddToQueueClick,
                        onAutoTagAlbumClick = onAutoTagAlbumClick,
                        onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick,
                        onArtistClick = onArtistClick
                    )
                }
            }

            FastScrollbar(
                state = listState,
                totalItems = albums.size,
                isAlphabetical = isAlphabetical,
                letterProvider = letterProvider,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 12.dp)
            )
        }
    } else {
        val columns = viewMode.columns
        val gridPadding = when {
            columns >= 4 -> 8.dp
            columns == 3 -> 10.dp
            else -> 12.dp
        }
        val gridSpacing = when {
            columns >= 4 -> 8.dp
            columns == 3 -> 10.dp
            else -> 12.dp
        }
        Box(modifier = modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(gridPadding),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing)
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
                        columns = columns,
                        onClick = { onAlbumClick(album) },
                        onAddToQueueClick = onAddToQueueClick,
                        onAutoTagAlbumClick = onAutoTagAlbumClick,
                        onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick,
                        onArtistClick = onArtistClick
                    )
                }
            }

            FastScrollbar(
                gridState = state,
                totalItems = albums.size,
                columnsCount = columns,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumListItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    val resolvedArtist = remember(album) { album.resolvableArtist() }
    val hasOptionsMenu = onAddToQueueClick != null || onAutoTagAlbumClick != null || (onArtistClick != null && resolvedArtist != null)
    var menuExpanded by remember { mutableStateOf(false) }

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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(album.id, album.albumArtUri) { mutableStateOf(false) }
                val context = LocalContext.current
                val imageRequest = remember(album.albumArtUri) {
                    album.albumArtUri?.let { uri ->
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
                        contentDescription = stringResource(R.string.album_cover_content_desc, album.title),
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
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val tracksCountText = if (album.trackCount == 1) {
                    stringResource(R.string.album_songs_count_single)
                } else {
                    stringResource(R.string.album_songs_count_multiple, album.trackCount)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " • $tracksCountText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            if (hasOptionsMenu) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.options_for_item, album.title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AlbumOptionsDropdownMenu(
                        album = album,
                        resolvedArtist = resolvedArtist,
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onArtistClick = onArtistClick,
                        onAddToQueueClick = onAddToQueueClick,
                        onAutoTagAlbumClick = onAutoTagAlbumClick,
                        onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onAddToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    val isCompact = columns >= 3
    val isUltraCompact = columns >= 4
    val cardPadding = when {
        isUltraCompact -> 6.dp
        isCompact -> 8.dp
        else -> 10.dp
    }
    val cardShape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp)
    val coverShape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp)
    val resolvedArtist = remember(album) { album.resolvableArtist() }
    val hasOptionsMenu = onAddToQueueClick != null || onAutoTagAlbumClick != null || (onArtistClick != null && resolvedArtist != null)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (hasOptionsMenu) {
                    { menuExpanded = true }
                } else {
                    null
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = cardShape
    ) {
        Column(
            modifier = Modifier.padding(cardPadding)
        ) {
            // Copertina quadrata dell'album
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(coverShape),
                contentAlignment = Alignment.Center
            ) {
                var isImageError by remember(album.id, album.albumArtUri) { mutableStateOf(false) }
                val context = LocalContext.current
                val imageRequest = remember(album.albumArtUri, columns) {
                    album.albumArtUri?.let { uri ->
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(if (columns >= 3) 240 else 360)
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
                            modifier = Modifier.padding(if (isUltraCompact) 14.dp else if (isCompact) 20.dp else 28.dp)
                        )
                    }
                }

                if (isCompact && hasOptionsMenu) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { menuExpanded = true },
                            color = Color.Black.copy(alpha = 0.48f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.options_for_item, album.title),
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        AlbumOptionsDropdownMenu(
                            album = album,
                            resolvedArtist = resolvedArtist,
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            onArtistClick = onArtistClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onAutoTagAlbumClick = onAutoTagAlbumClick,
                            onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))

            // Dettagli Album e Menu Opzioni
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Titolo Album
                    Text(
                        text = album.title,
                        style = when {
                            isUltraCompact -> MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                            isCompact -> MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Artista
                    Text(
                        text = album.artist,
                        style = when {
                            isUltraCompact -> MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            isCompact -> MaterialTheme.typography.labelSmall
                            else -> MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!isUltraCompact) {
                        val tracksCountText = if (album.trackCount == 1) {
                            stringResource(R.string.album_songs_count_single)
                        } else {
                            stringResource(R.string.album_songs_count_multiple, album.trackCount)
                        }
                        Text(
                            text = tracksCountText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isCompact) 10.sp else 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isCompact && hasOptionsMenu) {
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
                        AlbumOptionsDropdownMenu(
                            album = album,
                            resolvedArtist = resolvedArtist,
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            onArtistClick = onArtistClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onAutoTagAlbumClick = onAutoTagAlbumClick,
                            onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumOptionsDropdownMenu(
    album: Album,
    resolvedArtist: String?,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onArtistClick: ((String) -> Unit)?,
    onAddToQueueClick: ((Album) -> Unit)?,
    onAutoTagAlbumClick: ((Album) -> Unit)?,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (onArtistClick != null && resolvedArtist != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_menu_view_artist)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    onDismissRequest()
                    onArtistClick(resolvedArtist)
                }
            )
        }
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
                    onDismissRequest()
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
                    onDismissRequest()
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
                    onDismissRequest()
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
                    onDismissRequest()
                    onAutoTagAlbumClick(album)
                }
            )
        }
    }
}

internal fun Album.resolvableArtist(): String? {
    val genericArtists = setOf(
        "artisti vari",
        "various artists",
        "artista sconosciuto",
        "unknown artist",
        "<unknown>"
    )
    val primary = artist.trim()
    if (primary.isNotBlank() && primary.lowercase() !in genericArtists) {
        return primary
    }
    return tracks.firstOrNull { track ->
        val tArtist = track.artist.trim()
        tArtist.isNotBlank() && tArtist.lowercase() !in genericArtists
    }?.artist?.trim()
}
