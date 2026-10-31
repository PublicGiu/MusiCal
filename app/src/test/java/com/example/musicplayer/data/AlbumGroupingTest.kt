package com.example.musicplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class AlbumGroupingTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun createTrack(
        id: Long,
        title: String,
        albumId: Long,
        album: String,
        trackNumber: Int = 1,
        discNumber: Int = 1,
        duration: Long = 180000L
    ): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Test Artist",
            duration = duration,
            uri = dummyUri,
            albumId = albumId,
            album = album,
            trackNumber = trackNumber,
            discNumber = discNumber
        )
    }

    @Test
    fun testGroupTracksIntoAlbumsGroupsByAlbumId() {
        val tracks = listOf(
            createTrack(id = 1, title = "Song A1", albumId = 10, album = "Album Ten"),
            createTrack(id = 2, title = "Song B1", albumId = 20, album = "Album Twenty"),
            createTrack(id = 3, title = "Song A2", albumId = 10, album = "Album Ten")
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("Devono esserci esattamente 2 album distinti", 2, albums.size)
        val albumTen = albums.find { it.id == 10L }
        val albumTwenty = albums.find { it.id == 20L }

        assertEquals(2, albumTen?.trackCount)
        assertEquals(1, albumTwenty?.trackCount)
    }

    @Test
    fun testTrackOrderingWithinAlbumByDiscAndTrackNumber() {
        val tracks = listOf(
            createTrack(id = 1, title = "Track 2 Disc 1", albumId = 100, album = "Double Album", trackNumber = 2, discNumber = 1),
            createTrack(id = 2, title = "Track 1 Disc 2", albumId = 100, album = "Double Album", trackNumber = 1, discNumber = 2),
            createTrack(id = 3, title = "Track 1 Disc 1", albumId = 100, album = "Double Album", trackNumber = 1, discNumber = 1),
            createTrack(id = 4, title = "Track 2 Disc 2", albumId = 100, album = "Double Album", trackNumber = 2, discNumber = 2)
        )

        val albums = groupTracksIntoAlbums(tracks)
        val album = albums.first()

        assertEquals(4, album.tracks.size)
        // L'ordine atteso deve essere:
        // Disc 1 Track 1, Disc 1 Track 2, Disc 2 Track 1, Disc 2 Track 2
        assertEquals("Track 1 Disc 1", album.tracks[0].title)
        assertEquals("Track 2 Disc 1", album.tracks[1].title)
        assertEquals("Track 1 Disc 2", album.tracks[2].title)
        assertEquals("Track 2 Disc 2", album.tracks[3].title)
    }

    @Test
    fun testAlbumsOrderedAlphabetically() {
        val tracks = listOf(
            createTrack(id = 1, title = "Z Song", albumId = 1, album = "Zeta Album"),
            createTrack(id = 2, title = "A Song", albumId = 2, album = "Alpha Album"),
            createTrack(id = 3, title = "M Song", albumId = 3, album = "Beta Album")
        )

        val albums = groupTracksIntoAlbums(tracks)
        assertEquals(3, albums.size)
        assertEquals("Alpha Album", albums[0].title)
        assertEquals("Beta Album", albums[1].title)
        assertEquals("Zeta Album", albums[2].title)
    }

    @Test
    fun testFormattedDuration() {
        val trackZero = createTrack(1, "Zero", 1, "Album", duration = 0L)
        assertEquals("00:00", trackZero.formattedDuration())

        val trackShort = createTrack(2, "Short", 1, "Album", duration = 65000L)
        assertEquals("01:05", trackShort.formattedDuration())

        val trackLong = createTrack(3, "Long", 1, "Album", duration = 3665000L)
        assertEquals("1:01:05", trackLong.formattedDuration())
    }
}
