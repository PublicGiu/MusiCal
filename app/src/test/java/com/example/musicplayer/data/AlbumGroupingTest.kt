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
        artist: String = "Test Artist",
        albumArtist: String = "",
        trackNumber: Int = 1,
        discNumber: Int = 1,
        duration: Long = 180000L
    ): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
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

    @Test
    fun testMediaStoreAlbumIdCollisionSeparatesDistinctAlbums() {
        // Due brani con lo stesso albumId assegnato da MediaStore ma con titoli di album diversi
        val tracks = listOf(
            createTrack(id = 1, title = "Come Together", albumId = 99, album = "Abbey Road", artist = "The Beatles"),
            createTrack(id = 2, title = "Billie Jean", albumId = 99, album = "Thriller", artist = "Michael Jackson")
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("I brani devono essere separati in 2 album distinti", 2, albums.size)
        val abbeyRoad = albums.find { it.title.equals("Abbey Road", ignoreCase = true) }
        val thriller = albums.find { it.title.equals("Thriller", ignoreCase = true) }

        assertEquals(1, abbeyRoad?.trackCount)
        assertEquals(1, thriller?.trackCount)
    }

    @Test
    fun testGenericAlbumNamesSeparatedByArtist() {
        // Brani con tag album generico ("Sconosciuto") ma di artisti diversi
        val tracks = listOf(
            createTrack(id = 1, title = "Bohemian Rhapsody", albumId = 1, album = "Sconosciuto", artist = "Queen"),
            createTrack(id = 2, title = "Beat It", albumId = 1, album = "Sconosciuto", artist = "Michael Jackson"),
            createTrack(id = 3, title = "Radio Ga Ga", albumId = 1, album = "Sconosciuto", artist = "Queen")
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("Non devono essere ammassati in un unico album sconosciuto: separati per artista", 2, albums.size)
        val queenAlbum = albums.find { it.artist.contains("Queen", ignoreCase = true) }
        val mjAlbum = albums.find { it.artist.contains("Michael Jackson", ignoreCase = true) }

        assertEquals(2, queenAlbum?.trackCount)
        assertEquals(1, mjAlbum?.trackCount)
    }

    @Test
    fun testSameAlbumNameDifferentArtistsSeparated() {
        // Stesso titolo album ("Greatest Hits") ma artisti diversi e non compilation
        val tracks = listOf(
            createTrack(id = 1, title = "Under Pressure", albumId = 50, album = "Greatest Hits", artist = "Queen"),
            createTrack(id = 2, title = "All The Small Things", albumId = 51, album = "Greatest Hits", artist = "Blink-182")
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("Due Greatest Hits di artisti diversi devono essere 2 album separati", 2, albums.size)
        val queenAlbum = albums.find { it.artist.contains("Queen", ignoreCase = true) }
        val blinkAlbum = albums.find { it.artist.contains("Blink-182", ignoreCase = true) }

        assertEquals(1, queenAlbum?.trackCount)
        assertEquals(1, blinkAlbum?.trackCount)
    }

    @Test
    fun testCompilationTracksRemainGrouped() {
        // Tracce con album uguale e albumArtist "Various Artists" ma singoli artisti diversi
        val tracks = listOf(
            createTrack(id = 1, title = "You Never Can Tell", albumId = 70, album = "Pulp Fiction", artist = "Chuck Berry", albumArtist = "Various Artists"),
            createTrack(id = 2, title = "Girl, You'll Be a Woman Soon", albumId = 70, album = "Pulp Fiction", artist = "Urge Overkill", albumArtist = "Various Artists")
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("La compilation Various Artists deve restare 1 unico album", 1, albums.size)
        assertEquals("Pulp Fiction", albums[0].title)
        assertEquals(2, albums[0].trackCount)
    }

    @Test
    fun testAlbumIsMixedOrGenericDetection() {
        val mixedAlbum = Album(
            id = 1L,
            title = "Sconosciuto",
            artist = "Artisti Vari",
            albumArtUri = null,
            tracks = listOf(
                createTrack(1, "Song 1", 1L, "Album A", artist = "Artist 1"),
                createTrack(2, "Song 2", 1L, "Album B", artist = "Artist 2")
            )
        )
        assertTrue("Album con tracce miste deve essere rilevato come mixed", mixedAlbum.isMixedOrGeneric())

        val pureAlbum = Album(
            id = 2L,
            title = "A Night at the Opera",
            artist = "Queen",
            albumArtUri = null,
            tracks = listOf(
                createTrack(1, "Bohemian Rhapsody", 2L, "A Night at the Opera", artist = "Queen"),
                createTrack(2, "Love of My Life", 2L, "A Night at the Opera", artist = "Queen")
            )
        )
        org.junit.Assert.assertFalse("Album puro non deve essere mixed", pureAlbum.isMixedOrGeneric())

        val albumWithFeatures = Album(
            id = 3L,
            title = "PENSIERI CATTIVI",
            artist = "Macello",
            albumArtUri = null,
            tracks = listOf(
                createTrack(1, "UCCIDERMI/DROGA", 3L, "PENSIERI CATTIVI", artist = "Macello"),
                createTrack(2, "BENITO E CLARA + ALBANO E ROMINA", 3L, "PENSIERI CATTIVI", artist = "Macello + Albano e Romina"),
                createTrack(3, "CRACK AL ROCKET", 3L, "PENSIERI CATTIVI", artist = "Macello feat. Paky")
            )
        )
        org.junit.Assert.assertFalse("Album con tracce unificate e collaborazioni/feat non deve essere rilevato come mixed", albumWithFeatures.isMixedOrGeneric())
    }
}
