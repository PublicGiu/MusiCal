package com.example.musicplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicplayer.data.Album
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
    onAddAlbumToQueueClick: ((Album) -> Unit)? = null,
    onAddPlaylistToQueueClick: ((Playlist) -> Unit)? = null,
    onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null,
    favoriteTrackIds: Set<Long> = emptySet(),
    onChangePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onRemovePlaylistCoverClick: ((Playlist) -> Unit)? = null,
    onAutoTagTrackClick: ((AudioTrack) -> Unit)? = null
) {
    val totalResults = matchingTracks.size + matchingAlbums.size + matchingPlaylists.size

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
                    text = "Nessun risultato per \"$query\"",
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
            // Sezione 1: Brani trovati
            if (matchingTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Brani trovati (${matchingTracks.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(matchingTracks, key = { "track_${it.id}" }) { track ->
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
                        onAutoTagClick = onAutoTagTrackClick
                    )
                }
            }

            // Sezione 2: Album trovati
            if (matchingAlbums.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Album trovati (${matchingAlbums.size})",
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
                        text = "Playlist trovate (${matchingPlaylists.size})",
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
