package com.example.musicplayer.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class StatsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): StatsRepository {
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)
        return StatsRepository(mockContext)
    }

    @Test
    fun testLoadStatsReturnsEmptyWhenFileNotExists() {
        val repository = createRepository()
        val stats = repository.loadStats()
        assertTrue(stats.isEmpty())
    }

    @Test
    fun testSaveAndLoadStats() {
        val repository = createRepository()
        val statsMap = mapOf(
            1L to TrackStats(audioId = 1L, playCount = 10, lastPlayedTimestamp = 123456789L),
            2L to TrackStats(audioId = 2L, playCount = 3, lastPlayedTimestamp = 987654321L)
        )

        repository.saveStats(statsMap)
        val loaded = repository.loadStats()

        assertEquals(2, loaded.size)
        assertEquals(10, loaded[1L]?.playCount)
        assertEquals(123456789L, loaded[1L]?.lastPlayedTimestamp)
        assertEquals(3, loaded[2L]?.playCount)
        assertEquals(987654321L, loaded[2L]?.lastPlayedTimestamp)
    }

    @Test
    fun testRecordTrackPlayedIncrementsPlayCount() {
        val repository = createRepository()

        // Prima riproduzione
        repository.recordTrackPlayed(100L)
        val firstStats = repository.loadStats()
        assertEquals(1, firstStats[100L]?.playCount)
        assertTrue((firstStats[100L]?.lastPlayedTimestamp ?: 0L) > 0L)

        // Seconda riproduzione
        val updated = repository.recordTrackPlayed(100L)
        assertEquals(2, updated.playCount)
        val secondStats = repository.loadStats()
        assertEquals(2, secondStats[100L]?.playCount)
    }
}
