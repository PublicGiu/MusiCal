package com.example.musicplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AlbumDuplicateTracksTest {

    private val dummyUri = mock(Uri::class.java)

    private fun createTrack(id: Long, title: String, duration: Long, trackNumber: Int = 1): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Test Artist",
            duration = duration,
            uri = dummyUri,
            album = "Test Album",
            displayName = "$title.mp3",
            relativePath = "Music/Test/",
            trackNumber = trackNumber
        )
    }

    @Test
    fun testFindDuplicateTrackGroups_noDuplicates() {
        val tracks = listOf(
            createTrack(1L, "Song One", 200_000L),
            createTrack(2L, "Song Two", 210_000L),
            createTrack(3L, "Song Three", 180_000L)
        )
        val album = Album(1L, "Test Album", "Test Artist", null, tracks)
        val duplicates = album.findDuplicateTrackGroups()
        assertTrue("No duplicates should be found", duplicates.isEmpty())
    }

    @Test
    fun testFindDuplicateTrackGroups_exactDuplicates() {
        val tracks = listOf(
            createTrack(1L, "Song One", 200_000L),
            createTrack(2L, "Song One", 200_000L),
            createTrack(3L, "Song Two", 180_000L)
        )
        val album = Album(1L, "Test Album", "Test Artist", null, tracks)
        val duplicates = album.findDuplicateTrackGroups()
        assertEquals(1, duplicates.size)
        assertEquals("Song One", duplicates[0].canonicalTitle)
        assertEquals(2, duplicates[0].tracks.size)
        assertEquals(listOf(1L, 2L), duplicates[0].tracks.map { it.id })
    }

    @Test
    fun testFindDuplicateTrackGroups_normalizedTitleAndDurationTolerance() {
        val tracks = listOf(
            createTrack(1L, "Song One", 200_000L),
            createTrack(2L, "Song One (Remastered)", 202_000L), // Difference is 2s <= 5s
            createTrack(3L, "Song Two", 180_000L)
        )
        val album = Album(1L, "Test Album", "Test Artist", null, tracks)
        val duplicates = album.findDuplicateTrackGroups()
        assertEquals(1, duplicates.size)
        assertEquals(2, duplicates[0].tracks.size)
    }

    @Test
    fun testFindDuplicateTrackGroups_differentDurationsNotDuplicate() {
        // Same title but one is a 3-minute radio edit and one is a 7-minute extended version
        val tracks = listOf(
            createTrack(1L, "Song One", 180_000L),
            createTrack(2L, "Song One", 420_000L)
        )
        val album = Album(1L, "Test Album", "Test Artist", null, tracks)
        val duplicates = album.findDuplicateTrackGroups()
        assertTrue("Tracks with different durations (>5s) should not be flagged as duplicates", duplicates.isEmpty())
    }

    @Test
    fun testFindDuplicateTrackGroups_multipleDuplicateGroups() {
        val tracks = listOf(
            createTrack(1L, "Alpha", 200_000L),
            createTrack(2L, "Alpha", 201_000L),
            createTrack(3L, "Beta", 150_000L),
            createTrack(4L, "Beta [Explicit]", 150_000L),
            createTrack(5L, "Gamma", 300_000L)
        )
        val album = Album(1L, "Test Album", "Test Artist", null, tracks)
        val duplicates = album.findDuplicateTrackGroups()
        assertEquals(2, duplicates.size)
        assertEquals("Alpha", duplicates[0].canonicalTitle)
        assertEquals("Beta", duplicates[1].canonicalTitle)
    }
}
