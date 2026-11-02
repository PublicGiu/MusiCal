package com.example.musicplayer.data.tagger

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.LyricsRepository
import com.example.musicplayer.data.tagger.model.DeezerAlbumDetail
import com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo
import com.example.musicplayer.data.tagger.model.DeezerTrackDetail
import com.example.musicplayer.data.tagger.repository.MetadataEnricherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DeezerAndLrclibParsingTest {

    @Test
    fun testDeezerAlbumDetailYearExtraction() {
        val albumDetail = DeezerAlbumDetail(
            id = 12345L,
            title = "Random Access Memories",
            albumArtist = "Daft Punk",
            releaseDate = "2013-05-17",
            recordType = "album",
            genre = "Electronic",
            coverXl = "https://example.com/cover_xl.jpg",
            tracks = emptyList()
        )

        assertEquals(2013, albumDetail.releaseYear)
        assertEquals("Daft Punk", albumDetail.albumArtist)
    }

    @Test
    fun testDeezerTrackDetailModel() {
        val detail = DeezerTrackDetail(
            id = 3135556L,
            title = "Harder, Better, Faster, Stronger",
            titleShort = "Harder, Better, Faster, Stronger",
            trackPosition = 4,
            diskNumber = 1,
            duration = 224L,
            releaseDate = "2001-03-12",
            albumId = 302127L,
            albumTitle = "Discovery",
            artistName = "Daft Punk",
            coverXl = "https://example.com/cover.jpg"
        )

        assertEquals(4, detail.trackPosition)
        assertEquals(1, detail.diskNumber)
        assertEquals("Discovery", detail.albumTitle)
        assertEquals("Daft Punk", detail.artistName)
    }

    @Test
    fun testAlbumTracksAlignment() {
        val repo = MetadataEnricherRepository()
        val mockUri = Mockito.mock(android.net.Uri::class.java)

        // Tracce locali con posizione non impostata o errata
        val localTracks = listOf(
            AudioTrack(id = 101L, title = "Aerodynamic", artist = "Daft Punk", duration = 210000L, album = "Discovery", trackNumber = 0, uri = mockUri, displayName = "02 - Aerodynamic.mp3"),
            AudioTrack(id = 102L, title = "Harder, Better, Faster, Stronger", artist = "Daft Punk", duration = 224000L, album = "Discovery", trackNumber = 99, uri = mockUri, displayName = "daft_punk_hbbf.mp3"),
            AudioTrack(id = 103L, title = "One More Time", artist = "Daft Punk", duration = 320000L, album = "Discovery", trackNumber = 1, uri = mockUri, displayName = "01 - One More Time.mp3")
        )

        // Tracklist ufficiale Deezer per Discovery
        val officialDeezerTracks = listOf(
            DeezerAlbumTrackInfo(id = 1L, title = "One More Time", trackPosition = 1, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 2L, title = "Aerodynamic", trackPosition = 2, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 3L, title = "Digital Love", trackPosition = 3, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 4L, title = "Harder, Better, Faster, Stronger", trackPosition = 4, diskNumber = 1)
        )

        val aligned = repo.alignAlbumTracks(localTracks, officialDeezerTracks)

        assertEquals(3, aligned.size)
        // Aerodynamic deve essere associata alla traccia 2
        assertEquals(2, aligned[101L]?.trackPosition)
        // Harder Better deve essere associata alla traccia 4
        assertEquals(4, aligned[102L]?.trackPosition)
        // One More Time deve essere associata alla traccia 1
        assertEquals(1, aligned[103L]?.trackPosition)
    }

    @Test
    fun testLyricsParsingWithTimestamps() {
        val mockContext = Mockito.mock(android.content.Context::class.java)
        val lyricsRepo = LyricsRepository(mockContext)

        val sampleLrc = """
            [00:15.50] Like the legend of the phoenix
            [00:19.20] All ends with beginnings
            [00:23.00] What keeps the planet spinning
        """.trimIndent()

        val parsed = lyricsRepo.parseLyrics(1L, sampleLrc)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(15500L, parsed.lines[0].timeMs)
        assertEquals("Like the legend of the phoenix", parsed.lines[0].text)
        assertEquals(19200L, parsed.lines[1].timeMs)
        assertEquals(23000L, parsed.lines[2].timeMs)
    }
}
