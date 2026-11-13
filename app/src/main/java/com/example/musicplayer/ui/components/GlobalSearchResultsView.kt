package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onAddPlaylistToQueueClick: ((Playlist) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    onChangePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onRemovePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    val totalResults = matchingArtists.size + matchingTracks.size + matchingAlbums.size + matchingPlaylists.size

    if (totalResults == 0) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.search_no_results, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            // Sezione 1: Artisti trovati
            if (matchingArtists.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.search_artists_found, matchingArtists.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchingArtists, key = { "artist_${it.name}" }) { artist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onArtistClick?.invoke(artist.name) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
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
                                    .size(52.dp)
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
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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

            // Sezione 2: Brani trovati
            if (matchingTracks.isNotEmpty()) {
                item {
                    if (matchingArtists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = stringResource(R.string.search_tracks_found, matchingTracks.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchingTracks, key = { "track_${it.id}" }) { track ->
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
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }

            // Sezione 2: Album trovati
            if (matchingAlbums.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.search_albums_found, matchingAlbums.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchingAlbums, key = { "album_${it.id}" }) { album ->
                    AlbumGridItem(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onAddToQueueClick = onAddAlbumToQueueClick
                    )
                }
            }

            // Sezione 3: Playlist trovate
            if (matchingPlaylists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.search_playlists_found, matchingPlaylists.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchingPlaylists, key = { "playlist_${it.id}" }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDeleteClick = { onDeletePlaylistClick(playlist) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        onAddToQueueClick = onAddPlaylistToQueueClick,
                        onChangeCoverClick = if (!playlist.isFavorites) onChangePlaylistCoverClick else null,
                        onRemoveCoverClick = if (!playlist.isFavorites) onRemovePlaylistCoverClick else null
                    )
                }
            }
        }
    }
}
