package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class LyricsInstrumentalAndSyncTest {

    private lateinit var repository: LyricsRepository
    private val mockContext = mock(Context::class.java)

    @Before
    fun setup() {
        repository = LyricsRepository(mockContext)
    }

    @Test
    fun parseLyrics_instrumentalTag_detectedCorrectly() {
        val raw = "[instrumental:true]\n"
        val parsed = repository.parseLyrics(1L, raw)

        assertTrue(parsed.isInstrumental)
        assertFalse(parsed.isSynced)
        assertTrue(parsed.lines.isEmpty())
    }

    @Test
    fun parseLyrics_authorInstrumentalTag_detectedCorrectly() {
        val raw = """
            [ti:Song Title]
            [au: Instrumental]
        """.trimIndent()
        val parsed = repository.parseLyrics(2L, raw)

        assertTrue(parsed.isInstrumental)
    }

    @Test
    fun parseLyrics_lineWithParenthesesInstrumental_detectedCorrectly() {
        val raw = "(Strumentale)"
        val parsed = repository.parseLyrics(3L, raw)

        assertTrue(parsed.isInstrumental)
    }

    @Test
    fun parseLyrics_regularSyncedLyrics_isNotInstrumental() {
        val raw = """
            [00:10.00] First sung line
            [00:15.50] Second sung line
        """.trimIndent()
        val parsed = repository.parseLyrics(4L, raw)

        assertFalse(parsed.isInstrumental)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("First sung line", parsed.lines[0].text)
        assertEquals(10000L, parsed.lines[0].timeMs)
    }

    @Test
    fun audioTrack_isInstrumental_heuristicDetection() {
        val baseTrack = AudioTrack(
            id = 10L,
            title = "Clair de Lune",
            artist = "Debussy",
            duration = 300000L,
            uri = mock(Uri::class.java),
            genre = "Classical"
        )

        assertFalse(baseTrack.isInstrumental())

        val instrumentalTitleTrack = baseTrack.copy(title = "Nuvole Bianche (Instrumental)")
        assertTrue(instrumentalTitleTrack.isInstrumental())

        val strumentaleTitleTrack = baseTrack.copy(title = "Canzone d'Autunno [Strumentale]")
        assertTrue(strumentaleTitleTrack.isInstrumental())

        val instrumentalGenreTrack = baseTrack.copy(genre = "Instrumental")
        assertTrue(instrumentalGenreTrack.isInstrumental())

        val strumentaleGenreTrack = baseTrack.copy(genre = "Strumentale")
        assertTrue(strumentaleGenreTrack.isInstrumental())

        val instrumentalFileNameTrack = baseTrack.copy(displayName = "track_01 (Instrumental).mp3")
        assertTrue(instrumentalFileNameTrack.isInstrumental())
    }
}
