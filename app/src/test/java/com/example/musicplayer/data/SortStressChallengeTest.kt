package com.example.musicplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Empirical Adversarial Stress Test Suite for Milestone 1 (R4 Sorting Animations and Logic).
 * Authored by Challenger M1-2.
 */
class SortStressChallengeTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun track(id: Long, title: String, dateAdded: Long = 0L): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            duration = 180000L,
            uri = dummyUri,
            dateAdded = dateAdded
        )
    }

    private fun album(id: Long, title: String, tracks: List<AudioTrack> = emptyList()): Album {
        return Album(
            id = id,
            title = title,
            artist = "Album Artist $id",
            albumArtUri = dummyUri,
            tracks = tracks
        )
    }

    private fun playlist(id: String, name: String, trackIds: List<Long> = emptyList()): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackIds = trackIds
        )
    }

    // =========================================================================
    // 1. EMPTY LIST SORTING STRESS
    // =========================================================================

    @Test
    fun testEmptyListSorting_AllOptionsAndDirections() {
        val emptyTracks = emptyList<AudioTrack>()
        val emptyAlbums = emptyList<Album>()
        val emptyPlaylists = emptyList<Playlist>()
        val emptyStats = emptyMap<Long, TrackStats>()

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val sortedTracks = emptyTracks.sortTracks(option, emptyStats, isAsc)
                assertTrue("Empty tracks must return empty list for $option (isAsc=$isAsc)", sortedTracks.isEmpty())

                val sortedAlbums = emptyAlbums.sortAlbums(option, emptyStats, isAsc)
                assertTrue("Empty albums must return empty list for $option (isAsc=$isAsc)", sortedAlbums.isEmpty())

                val sortedPlaylists = emptyPlaylists.sortPlaylists(option, emptyStats, isAsc)
                assertTrue("Empty playlists must return empty list for $option (isAsc=$isAsc)", sortedPlaylists.isEmpty())
            }
        }
    }

    // =========================================================================
    // 2. SINGLE-ITEM LIST SORTING STRESS
    // =========================================================================

    @Test
    fun testSingleItemListSorting_IdentityPreserved() {
        val singleTrack = listOf(track(42L, "Solo Track", dateAdded = 1000L))
        val singleAlbum = listOf(album(99L, "Solo Album", singleTrack))
        val singlePlaylist = listOf(playlist("p1", "Solo Playlist", listOf(42L)))

        val stats = mapOf(42L to TrackStats(audioId = 42L, playCount = 10, lastPlayedTimestamp = 5000L))

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val st = singleTrack.sortTracks(option, stats, isAsc)
                assertEquals(1, st.size)
                assertEquals(42L, st[0].id)

                val sa = singleAlbum.sortAlbums(option, stats, isAsc)
                assertEquals(1, sa.size)
                assertEquals(99L, sa[0].id)

                val sp = singlePlaylist.sortPlaylists(option, stats, isAsc)
                assertEquals(1, sp.size)
                assertEquals("p1", sp[0].id)
            }
        }
    }

    // =========================================================================
    // 3. ZERO-CHILDREN AGGREGATES (ALBUM / PLAYLIST WITH 0 TRACKS)
    // =========================================================================

    @Test
    fun testZeroChildrenAggregates_NoNoSuchElementException() {
        val emptyAlbum1 = album(1L, "Empty A", emptyList())
        val emptyAlbum2 = album(2L, "Empty B", emptyList())
        val albums = listOf(emptyAlbum1, emptyAlbum2)

        val emptyPlaylist1 = playlist("1", "Empty P1", emptyList())
        val emptyPlaylist2 = playlist("2", "Empty P2", emptyList())
        val playlists = listOf(emptyPlaylist1, emptyPlaylist2)

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                // Must not throw NoSuchElementException from maxOfOrNull
                val sortedAlbums = albums.sortAlbums(option, emptyMap(), isAsc)
                assertEquals(2, sortedAlbums.size)

                val sortedPlaylists = playlists.sortPlaylists(option, emptyMap(), isAsc)
                assertEquals(2, sortedPlaylists.size)
            }
        }
    }

    // =========================================================================
    // 4. RAPID REPEATED SORTING TOGGLING (JITTER STRESS)
    // =========================================================================

    @Test
    fun testRapidRepeatedSortingTogglingJitter_DeterministicAndReversible() {
        val tracks = (1..50).map { id ->
            track(id = id.toLong(), title = "Track ${100 - id}", dateAdded = id * 100L)
        }
        val stats = (1..50).associate { id ->
            id.toLong() to TrackStats(audioId = id.toLong(), playCount = id * 3, lastPlayedTimestamp = id * 500L)
        }

        for (option in SortOption.entries) {
            val ascRef = tracks.sortTracks(option, stats, isAscending = true)
            val descRef = tracks.sortTracks(option, stats, isAscending = false)

            // Rapid jitter: 1,000 toggles back and forth
            var isAscending = true
            var lastList: List<AudioTrack> = ascRef
            for (i in 1..1000) {
                isAscending = !isAscending
                val currentList = tracks.sortTracks(option, stats, isAscending = isAscending)
                if (isAscending) {
                    assertEquals("Ascending sort drift at iteration $i for $option", ascRef.map { it.id }, currentList.map { it.id })
                } else {
                    assertEquals("Descending sort drift at iteration $i for $option", descRef.map { it.id }, currentList.map { it.id })
                }
                lastList = currentList
            }
            assertNotNull(lastList)
        }
    }

    // =========================================================================
    // 5. LARGE LIST PERFORMANCE & ANR / JANK RISK (10,000 TRACKS)
    // =========================================================================

    @Test
    fun testLargeListSortingPerformance_10kTracksUnderBudget() {
        val rng = Random(12345)
        val largeTrackList = (1..10_000).map { i ->
            track(
                id = i.toLong(),
                title = "Song_${rng.nextInt(100_000)}_${i}",
                dateAdded = rng.nextLong(1_000_000L, 9_000_000L)
            )
        }
        val largeStats = (1..10_000).associate { i ->
            i.toLong() to TrackStats(
                audioId = i.toLong(),
                playCount = rng.nextInt(0, 1000),
                lastPlayedTimestamp = rng.nextLong(1_000_000L, 9_000_000L)
            )
        }

        for (option in SortOption.entries) {
            val elapsedMs = measureTimeMillis {
                val sorted = largeTrackList.sortTracks(option, largeStats, isAscending = true)
                assertEquals(10_000, sorted.size)
            }
            // Performance budget: sorting 10k tracks in memory on JVM must complete within 250ms
            assertTrue("Sorting 10k tracks for $option took too long: ${elapsedMs}ms", elapsedMs < 250)
        }
    }

    @Test
    fun testLargeAlbumListPerformance_2kAlbumsUnderBudget() {
        val rng = Random(54321)
        val albums = (1..2_000).map { albumId ->
            val trackCount = rng.nextInt(5, 15)
            val tracks = (1..trackCount).map { tIndex ->
                track(
                    id = (albumId * 100 + tIndex).toLong(),
                    title = "AlbumTrack_${albumId}_$tIndex",
                    dateAdded = rng.nextLong(1_000_000L, 9_000_000L)
                )
            }
            album(albumId.toLong(), "Album_${rng.nextInt(10_000)}", tracks)
        }
        val stats = albums.flatMap { it.tracks }.associate { t ->
            t.id to TrackStats(
                audioId = t.id,
                playCount = rng.nextInt(0, 500),
                lastPlayedTimestamp = rng.nextLong(1_000_000L, 9_000_000L)
            )
        }

        for (option in SortOption.entries) {
            val elapsedMs = measureTimeMillis {
                val sorted = albums.sortAlbums(option, stats, isAscending = true)
                assertEquals(2_000, sorted.size)
            }
            assertTrue("Sorting 2k albums for $option took too long: ${elapsedMs}ms", elapsedMs < 350)
        }
    }

    // =========================================================================
    // 6. KEY COLLISIONS AND STABILITY
    // =========================================================================

    @Test
    fun testKeyCollisions_EqualKeysPreserveSizeAndIntegrity() {
        // 500 tracks with IDENTICAL titles, identical dateAdded, identical stats
        val collisionTracks = (1..500).map { id ->
            track(id = id.toLong(), title = "Same Title", dateAdded = 1000L)
        }
        val collisionStats = (1..500).associate { id ->
            id.toLong() to TrackStats(audioId = id.toLong(), playCount = 0, lastPlayedTimestamp = 0L)
        }

        for (option in SortOption.entries) {
            val sortedAsc = collisionTracks.sortTracks(option, collisionStats, isAscending = true)
            assertEquals(500, sortedAsc.size)
            assertEquals(500, sortedAsc.map { it.id }.toSet().size) // No lost items

            val sortedDesc = collisionTracks.sortTracks(option, collisionStats, isAscending = false)
            assertEquals(500, sortedDesc.size)
            assertEquals(500, sortedDesc.map { it.id }.toSet().size)
        }
    }

    // =========================================================================
    // 7. CORRUPTED OR EXTREME VALUES
    // =========================================================================

    @Test
    fun testExtremeValues_MaxLongAndNegativeValues() {
        val t1 = track(1L, "Max Long Date", dateAdded = Long.MAX_VALUE)
        val t2 = track(2L, "Min Long Date", dateAdded = Long.MIN_VALUE)
        val t3 = track(3L, "Zero Date", dateAdded = 0L)

        val stats = mapOf(
            1L to TrackStats(audioId = 1L, playCount = Int.MAX_VALUE, lastPlayedTimestamp = Long.MAX_VALUE),
            2L to TrackStats(audioId = 2L, playCount = -50, lastPlayedTimestamp = Long.MIN_VALUE),
            3L to TrackStats(audioId = 3L, playCount = 0, lastPlayedTimestamp = 0L)
        )

        val sortedByPlays = listOf(t1, t2, t3).sortTracks(SortOption.MOST_PLAYED, stats, isAscending = true)
        assertEquals(1L, sortedByPlays[0].id) // Int.MAX_VALUE
        assertEquals(3L, sortedByPlays[1].id) // 0
        assertEquals(2L, sortedByPlays[2].id) // -50

        val sortedByRecent = listOf(t1, t2, t3).sortTracks(SortOption.RECENTLY_PLAYED, stats, isAscending = true)
        assertEquals(1L, sortedByRecent[0].id) // Long.MAX_VALUE
        assertEquals(3L, sortedByRecent[1].id) // 0
        assertEquals(2L, sortedByRecent[2].id) // Long.MIN_VALUE
    }

    // =========================================================================
    // 8. UNICODE, EMOJI, AND CASE-INSENSITIVE ALPHABETICAL STABILITY
    // =========================================================================

    @Test
    fun testAlphabeticalSorting_UnicodeAndAccents() {
        val t1 = track(1L, "à la claire fontaine")
        val t2 = track(2L, "Zebra")
        val t3 = track(3L, "Éléphant")
        val t4 = track(4L, "apple")
        val t5 = track(5L, "123 Number")
        val t6 = track(6L, "🎵 Music Note")

        val sortedAsc = listOf(t1, t2, t3, t4, t5, t6).sortTracks(SortOption.ALPHABETICAL, isAscending = true)
        assertEquals(6, sortedAsc.size)
        // Verify no crashes or indexing exceptions with Unicode or emojis
        val sortedDesc = listOf(t1, t2, t3, t4, t5, t6).sortTracks(SortOption.ALPHABETICAL, isAscending = false)
        assertEquals(6, sortedDesc.size)
        assertEquals(sortedAsc.reversed().map { it.id }, sortedDesc.map { it.id })
    }
}
