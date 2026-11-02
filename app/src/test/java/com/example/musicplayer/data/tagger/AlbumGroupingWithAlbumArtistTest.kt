package com.example.musicplayer.data.tagger

import android.net.Uri
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.groupTracksIntoAlbums
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class AlbumGroupingWithAlbumArtistTest {

    @Test
    fun testAlbumTracksWithDifferentTrackArtistsUseAlbumArtist() {
        val mockUri = Mockito.mock(Uri::class.java)

        val track1 = AudioTrack(
            id = 1L,
            title = "Get Lucky",
            artist = "Daft Punk feat. Pharrell Williams",
            duration = 248000L,
            uri = mockUri,
            albumId = 100L,
            album = "Random Access Memories",
            albumArtist = "Daft Punk",
            trackNumber = 8,
            discNumber = 1
        )

        val track2 = AudioTrack(
            id = 2L,
            title = "Instant Crush",
            artist = "Daft Punk feat. Julian Casablancas",
            duration = 337000L,
            uri = mockUri,
            albumId = 100L,
            album = "Random Access Memories",
            albumArtist = "Daft Punk",
            trackNumber = 5,
            discNumber = 1
        )

        val albums = groupTracksIntoAlbums(listOf(track1, track2))
        assertEquals(1, albums.size)
        val album = albums.first()
        assertEquals("Random Access Memories", album.title)
        assertEquals("Daft Punk", album.artist)
        assertEquals(2, album.trackCount)
        // Verifichiamo che siano ordinati per numero di traccia
        assertEquals("Instant Crush", album.tracks[0].title)
        assertEquals("Get Lucky", album.tracks[1].title)
    }
}
