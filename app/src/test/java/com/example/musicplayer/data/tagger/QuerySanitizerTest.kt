package com.example.musicplayer.data.tagger

import android.net.Uri
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.tagger.sanitizer.QuerySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class QuerySanitizerTest {

    @Test
    fun testCleanFileNameWithStandardDashPattern() {
        val fileName = "01 - Daft Punk feat. Pharrell - Get Lucky (Official Video) [320kbps].mp3"
        val result = QuerySanitizer.cleanFileName(fileName)
        assertEquals("Daft Punk feat. Pharrell", result.artist)
        assertEquals("Get Lucky", result.title)
    }

    @Test
    fun testCleanFileNameWithUnderscoresAndTrackNumbers() {
        val fileName = "02_Queen - Bohemian Rhapsody [HD Audio].flac"
        val result = QuerySanitizer.cleanFileName(fileName)
        assertEquals("Queen", result.artist)
        assertEquals("Bohemian Rhapsody", result.title)
    }

    @Test
    fun testCleanFileNameWithoutArtistDelimiter() {
        val fileName = "05 Song Without Artist (Visualizer).mp3"
        val result = QuerySanitizer.cleanFileName(fileName)
        assertEquals(null, result.artist)
        assertEquals("Song Without Artist", result.title)
    }

    @Test
    fun testNormalizeAlbumTitle() {
        assertEquals("Random Access Memories", QuerySanitizer.normalizeAlbumTitle("Random Access Memories (10th Anniversary Edition)"))
        assertEquals("Hybrid Theory", QuerySanitizer.normalizeAlbumTitle("Hybrid Theory [Deluxe Edition]"))
        assertEquals("Nevermind", QuerySanitizer.normalizeAlbumTitle("Nevermind (Remastered 2021)"))
        assertEquals("Parachutes", QuerySanitizer.normalizeAlbumTitle("Parachutes [Special Edition]"))
        assertEquals("Abbey Road", QuerySanitizer.normalizeAlbumTitle("Abbey Road"))
    }

    @Test
    fun testIsDurationMatching() {
        val actualMs = 248000L // 248 seconds
        assertTrue(QuerySanitizer.isDurationMatching(actualMs, 248, toleranceSec = 3))
        assertTrue(QuerySanitizer.isDurationMatching(actualMs, 250, toleranceSec = 3))
        assertTrue(QuerySanitizer.isDurationMatching(actualMs, 246, toleranceSec = 3))
        assertFalse(QuerySanitizer.isDurationMatching(actualMs, 255, toleranceSec = 3))
        assertFalse(QuerySanitizer.isDurationMatching(actualMs, 240, toleranceSec = 3))
    }

    @Test
    fun testGetEffectiveSearchTermsWithUnknownTags() {
        val mockUri = Mockito.mock(Uri::class.java)
        val track = AudioTrack(
            id = 1L,
            title = "Sconosciuto",
            artist = "Artista sconosciuto",
            duration = 180000L,
            uri = mockUri,
            displayName = "01 - Coldplay - Yellow (Official Audio).mp3"
        )

        val (artist, title) = QuerySanitizer.getEffectiveSearchTerms(track)
        assertEquals("Coldplay", artist)
        assertEquals("Yellow", title)
    }

    @Test
    fun testGetEffectiveSearchTermsWithValidTags() {
        val mockUri = Mockito.mock(Uri::class.java)
        val track = AudioTrack(
            id = 2L,
            title = "Clocks (Official Video)",
            artist = "Coldplay",
            duration = 300000L,
            uri = mockUri,
            displayName = "coldplay_clocks.mp3"
        )

        val (artist, title) = QuerySanitizer.getEffectiveSearchTerms(track)
        assertEquals("Coldplay", artist)
        assertEquals("Clocks", title)
    }

    @Test
    fun testExtractTrackNumberFromFileName() {
        assertEquals(1, QuerySanitizer.extractTrackNumberFromFileName("01 - Song.mp3"))
        assertEquals(4, QuerySanitizer.extractTrackNumberFromFileName("04. Harder Better Faster Stronger.flac"))
        assertEquals(12, QuerySanitizer.extractTrackNumberFromFileName("12_Track_Name.m4a"))
        assertEquals(5, QuerySanitizer.extractTrackNumberFromFileName("1-05 - Song.mp3"))
        assertEquals(null, QuerySanitizer.extractTrackNumberFromFileName("Song Without Number.mp3"))
    }

    @Test
    fun testTitlesMatch() {
        // Corrispondenza esatta e case-insensitive
        assertTrue(QuerySanitizer.titlesMatch("One More Time", "one more time"))
        // Variazioni con remaster e parentesi
        assertTrue(QuerySanitizer.titlesMatch("Harder, Better, Faster, Stronger", "Harder Better Faster Stronger (Remastered 2021)"))
        // Corrispondenza con feat
        assertTrue(QuerySanitizer.titlesMatch("Get Lucky", "Get Lucky (feat. Pharrell Williams)"))
        // Titoli completamente differenti
        assertFalse(QuerySanitizer.titlesMatch("Aerodynamic", "Digital Love"))
    }
}
