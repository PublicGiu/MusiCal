package com.example.musicplayer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.ArtistSearchResult
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.TrackStats
import kotlinx.coroutines.launch

/**
 * Filtri della barra di ricerca globale, nell'ordine richiesto dall'utente.
 */
enum class SearchFilter(@StringRes val labelResId: Int) {
    ALL(R.string.search_filter_all),
    TRACKS(R.string.search_filter_tracks),
    ARTIST(R.string.search_filter_artist),
    ALBUM(R.string.search_filter_album),
    PLAYLIST(R.string.search_filter_playlist)
}

@Composable
fun GlobalSearchResultsView(
    query: String,
    matchingTracks: List<AudioTrack>,
    matchingAlbums: List<Album>,
    matchingPlaylists: List<Playlist>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    stats: Map<Long, TrackStats>,
    onTrackClick: (AudioTrack) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAddToQueueClick: (AudioTrack) -> Unit,
    onAddToPlaylistClick: (AudioTrack) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    matchingArtists: List<ArtistSearchResult> = emptyList(),
    onAddAlbumToQueueClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumClick: ((Album) -> Unit)? = null,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)? = null,
    onAddPlaylistToQueueClick: ((Playlist) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    onChangePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onRemovePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onTrackAlbumClick: ((AudioTrack) -> Unit)? = null
) {
    val filters = SearchFilter.entries
    val pagerState = rememberPagerState(
        initialPage = SearchFilter.ALL.ordinal,
        pageCount = { filters.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedFilter = filters.getOrElse(pagerState.targetPage) { SearchFilter.ALL }

    Column(modifier = modifier.fillMaxSize()) {
        SearchFilterSelectorRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { filter ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(filter.ordinal)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 1,
            userScrollEnabled = true
        ) { page ->
            val filter = filters[page]
            val listState = rememberLazyListState()

            LaunchedEffect(query) {
                if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                    listState.scrollToItem(0)
                }
            }

            val isPageEmpty = when (filter) {
                SearchFilter.ALL -> matchingArtists.isEmpty() &&
                    matchingTracks.isEmpty() &&
                    matchingAlbums.isEmpty() &&
                    matchingPlaylists.isEmpty()
                SearchFilter.TRACKS -> matchingTracks.isEmpty()
                SearchFilter.ARTIST -> matchingArtists.isEmpty()
                SearchFilter.ALBUM -> matchingAlbums.isEmpty()
                SearchFilter.PLAYLIST -> matchingPlaylists.isEmpty()
            }

            if (isPageEmpty) {
                val emptyText = when (filter) {
                    SearchFilter.ALL -> stringResource(R.string.search_no_results, query)
                    SearchFilter.TRACKS -> stringResource(R.string.search_no_tracks_results, query)
                    SearchFilter.ARTIST -> stringResource(R.string.search_no_artists_results, query)
                    SearchFilter.ALBUM -> stringResource(R.string.search_no_albums_results, query)
                    SearchFilter.PLAYLIST -> stringResource(R.string.search_no_playlists_results, query)
                }
                SearchEmptyState(text = emptyText)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTIST
                    val showTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS
                    val showAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUM
                    val showPlaylists = filter == SearchFilter.ALL || filter == SearchFilter.PLAYLIST

                    var hasPreviousSection = false

                    if (showArtists && matchingArtists.isNotEmpty()) {
                        searchArtistsSection(
                            artists = matchingArtists,
                            addTopSpacer = hasPreviousSection,
                            onArtistClick = onArtistClick
                        )
                        hasPreviousSection = true
                    }

                    if (showTracks && matchingTracks.isNotEmpty()) {
                        searchTracksSection(
                            tracks = matchingTracks,
                            addTopSpacer = hasPreviousSection,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            stats = stats,
                            favoriteTrackIds = favoriteTrackIds,
                            onTrackClick = onTrackClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onAddToFavoritesClick = onAddToFavoritesClick,
                            onAutoTagTrackClick = onAutoTagTrackClick,
                            onArtistClick = onArtistClick,
                            onTrackAlbumClick = onTrackAlbumClick
                        )
                        hasPreviousSection = true
                    }

                    if (showAlbums && matchingAlbums.isNotEmpty()) {
                        searchAlbumsSection(
                            albums = matchingAlbums,
                            addTopSpacer = hasPreviousSection,
                            onAlbumClick = onAlbumClick,
                            onAddAlbumToQueueClick = onAddAlbumToQueueClick,
                            onAutoTagAlbumClick = onAutoTagAlbumClick,
                            onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick,
                            onArtistClick = onArtistClick
                        )
                        hasPreviousSection = true
                    }

                    if (showPlaylists && matchingPlaylists.isNotEmpty()) {
                        searchPlaylistsSection(
                            playlists = matchingPlaylists,
                            addTopSpacer = hasPreviousSection,
                            onPlaylistClick = onPlaylistClick,
                            onDeletePlaylistClick = onDeletePlaylistClick,
                            onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
                            onChangePlaylistCoverClick = onChangePlaylistCoverClick,
                            onRemovePlaylistCoverClick = onRemovePlaylistCoverClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterSelectorRow(
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = SearchFilter.entries
    val rowListState = rememberLazyListState()

    LaunchedEffect(selectedFilter) {
        val targetIndex = filters.indexOf(selectedFilter)
        if (targetIndex >= 0) {
            rowListState.animateScrollToItem(targetIndex)
        }
    }

    LazyRow(
        state = rowListState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(filters, key = { _, filter -> filter.name }) { _, filter ->
            val isSelected = filter == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelResId)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp
                )
            )
        }
    }
}

@Composable
private fun SearchEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun LazyListScope.searchArtistsSection(
    artists: List<ArtistSearchResult>,
    addTopSpacer: Boolean,
    onArtistClick: ((String) -> Unit)?
) {
    item(key = "header_artists") {
        if (addTopSpacer) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.search_artists_found, artists.size),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    items(
        items = artists,
        key = { "artist_${it.name}" },
        contentType = { "search_artist_item" }
    ) { artist ->
        ArtistSearchListItem(
            artist = artist,
            onClick = { onArtistClick?.invoke(artist.name) }
        )
    }
}

private fun LazyListScope.searchTracksSection(
    tracks: List<AudioTrack>,
    addTopSpacer: Boolean,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    stats: Map<Long, TrackStats>,
    favoriteTrackIds: Set<Long>,
    onTrackClick: (AudioTrack) -> Unit,
    onAddToQueueClick: (AudioTrack) -> Unit,
    onAddToPlaylistClick: (AudioTrack) -> Unit,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)?,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)?,
    onArtistClick: ((String) -> Unit)?,
    onTrackAlbumClick: ((AudioTrack) -> Unit)?
) {
    item(key = "header_tracks") {
        if (addTopSpacer) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.search_tracks_found, tracks.size),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    items(
        items = tracks,
        key = { "track_${it.id}" },
        contentType = { "search_track_item" }
    ) { track ->
        SwipeToQueueTrackItem(
            track = track,
            onSwipeToQueue = { onAddToQueueClick(track) },
            key = "search_track_${track.id}"
        ) {
            TrackListItem(
                track = track,
                isSelected = currentTrack?.id == track.id,
                isPlaying = isPlaying && currentTrack?.id == track.id,
                onClick = { onTrackClick(track) },
                onAddToQueueClick = onAddToQueueClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddToFavoritesClick = onAddToFavoritesClick?.let { cb -> { cb(track) } },
                isFavorite = track.id in favoriteTrackIds,
                playCount = stats[track.id]?.playCount ?: 0,
                onAutoTagClick = onAutoTagTrackClick,
                onArtistClick = onArtistClick,
                onAlbumClick = onTrackAlbumClick
            )
        }
    }
}

private fun LazyListScope.searchAlbumsSection(
    albums: List<Album>,
    addTopSpacer: Boolean,
    onAlbumClick: (Album) -> Unit,
    onAddAlbumToQueueClick: ((Album) -> Unit)?,
    onAutoTagAlbumClick: ((Album) -> Unit)?,
    onAutoTagAlbumSeparationClick: ((Album) -> Unit)?,
    onArtistClick: ((String) -> Unit)?
) {
    item(key = "header_albums") {
        if (addTopSpacer) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.search_albums_found, albums.size),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    items(
        items = albums,
        key = { "album_${it.id}" },
        contentType = { "search_album_item" }
    ) { album ->
        AlbumListItem(
            album = album,
            onClick = { onAlbumClick(album) },
            onAddToQueueClick = onAddAlbumToQueueClick,
            onAutoTagAlbumClick = onAutoTagAlbumClick,
            onAutoTagAlbumSeparationClick = onAutoTagAlbumSeparationClick,
            onArtistClick = onArtistClick
        )
    }
}

private fun LazyListScope.searchPlaylistsSection(
    playlists: List<Playlist>,
    addTopSpacer: Boolean,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: ((Playlist) -> Unit)?,
    onChangePlaylistCoverClick: ((Playlist) -> Unit)?,
    onRemovePlaylistCoverClick: ((Playlist) -> Unit)?
) {
    item(key = "header_playlists") {
        if (addTopSpacer) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.search_playlists_found, playlists.size),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    items(
        items = playlists,
        key = { "playlist_${it.id}" },
        contentType = { "search_playlist_item" }
    ) { playlist ->
        PlaylistItem(
            playlist = playlist,
            onClick = { onPlaylistClick(playlist) },
            onDeleteClick = { onDeletePlaylistClick(playlist) },
            onAddToQueueClick = onAddPlaylistToQueueClick,
            onChangeCoverClick = if (!playlist.isFavorites) onChangePlaylistCoverClick else null,
            onRemoveCoverClick = if (!playlist.isFavorites) onRemovePlaylistCoverClick else null,
            compactSearchStyle = true
        )
    }
}

@Composable
private fun ArtistSearchListItem(
    artist: ArtistSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
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
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val photo = artist.photoUrl ?: artist.fallbackCoverUri
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val albumStr = if (artist.albumCount == 1) {
                    stringResource(R.string.artist_album_count_single)
                } else {
                    stringResource(R.string.artist_album_count_multiple, artist.albumCount)
                }
                val subtitle = if (artist.trackCount == 1) {
                    stringResource(R.string.artist_search_subtitle_single, artist.trackCount, albumStr)
                } else {
                    stringResource(R.string.artist_search_subtitle_multiple, artist.trackCount, albumStr)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
