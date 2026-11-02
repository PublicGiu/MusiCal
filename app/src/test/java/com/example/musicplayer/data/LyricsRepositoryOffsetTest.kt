package com.example.musicplayer.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class LyricsRepositoryOffsetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: LyricsRepository

    @Before
    fun setup() {
        context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tempFolder.root)
        repository = LyricsRepository(context)
    }

    @Test
    fun testAdjustOffsetForward() {
        val trackId = 101L
        val lyricsDir = File(tempFolder.root, "lyrics")
        lyricsDir.mkdirs()
        val lrcFile = File(lyricsDir, "$trackId.lrc")
        lrcFile.writeText("[00:01.00]Hello world\n[00:05.50]Second line\n")

        val adjusted = repository.adjustOffset(trackId, 500L)

        assertNotNull(adjusted)
        assertTrue(adjusted!!.isSynced)
        assertEquals(2, adjusted.lines.size)
        // 1000ms + 500ms = 1500ms
        assertEquals(1500L, adjusted.lines[0].timeMs)
        assertEquals("Hello world", adjusted.lines[0].text)
        // 5500ms + 500ms = 6000ms
        assertEquals(6000L, adjusted.lines[1].timeMs)
        assertEquals("Second line", adjusted.lines[1].text)
    }

    @Test
    fun testAdjustOffsetBackwardWithCoerceAtLeastZero() {
        val trackId = 102L
        val lyricsDir = File(tempFolder.root, "lyrics")
        lyricsDir.mkdirs()
        val lrcFile = File(lyricsDir, "$trackId.lrc")
        lrcFile.writeText("[00:00.30]First line\n[00:02.00]Second line\n")

        // Spostiamo indietro di 500ms: 300ms - 500ms deve diventare 0ms
        val adjusted = repository.adjustOffset(trackId, -500L)

        assertNotNull(adjusted)
        assertEquals(0L, adjusted!!.lines[0].timeMs)
        // 2000ms - 500ms = 1500ms
        assertEquals(1500L, adjusted.lines[1].timeMs)
    }
}
