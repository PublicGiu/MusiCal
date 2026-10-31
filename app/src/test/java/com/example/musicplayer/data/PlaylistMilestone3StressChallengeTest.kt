package com.example.musicplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Empirical Adversarial Stress Test Suite for Milestone 3 (R1 Playlists & Favorites).
 * Authored by Challenger M3-1.
 *
 * Focus areas:
 * 1. Corrupted JSON file variations (malformed syntax, non-array root, non-numeric track IDs, binary garbage).
 * 2. Zero-byte and whitespace files handling and default synthesis.
 * 3. Missing favorites ID recovery, favorites reordering to index 0, and duplicate favorites deduplication.
 * 4. High-concurrency and rapid consecutive deletion attempts targeting favorites and custom playlists.
 * 5. Edge case track IDs (Long.MAX_VALUE, Long.MIN_VALUE, -1L, 0L) and duplicate addition prevention.
 * 6. Exhaustive sorting permutations and invariant verification for favorites at index 0.
 */
class PlaylistMilestone3StressChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): Pair<PlaylistRepository, File> {
        val root = tempFolder.newFolder()
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(root)
        val file = File(root, "playlists.json")
        return Pair(PlaylistRepository(mockContext), file)
    }

    // =========================================================================
    // SECTION 1: CORRUPTED JSON & ZERO-BYTE FILE RESILIENCE
    // =========================================================================

    @Test
    fun testCorruptedJson_MalformedSyntax_ReturnsEmptyListWithoutCrashing() {
        val (repo, file) = createRepository()
        file.writeText("{ this is invalid json syntax ::: [[[]]")

        val result = repo.loadPlaylists()
        assertNotNull(result)
        assertTrue("Corrupted JSON must yield emptyList() gracefully", result.isEmpty())

        // Ensure repository can still create new playlists after corruption
        val created = repo.createPlaylist("Post-Corruption Playlist")
        assertNotNull(created)
        assertEquals("Post-Corruption Playlist", created.name)
    }

    @Test
    fun testCorruptedJson_TruncatedPayload_HandledGracefully() {
        val (repo, file) = createRepository()
        file.writeText("""[{"id": "fav", "name": "Brani prefer""")

        val result = repo.loadPlaylists()
        assertNotNull(result)
        assertTrue("Truncated JSON must return emptyList() without crashing", result.isEmpty())
    }

    @Test
    fun testCorruptedJson_RootIsJsonObjectInsteadOfArray_HandledGracefully() {
        val (repo, file) = createRepository()
        file.writeText("""{"id": "$FAVORITES_PLAYLIST_ID", "name": "$FAVORITES_PLAYLIST_NAME"}""")

        val result = repo.loadPlaylists()
        assertNotNull(result)
        assertTrue("Object root instead of array must yield emptyList()", result.isEmpty())
    }

    @Test
    fun testCorruptedJson_ArrayContainsNonNumericTrackIds_HandledGracefully() {
        val (repo, file) = createRepository()
        file.writeText("""[{"id": "p1", "name": "Test", "trackIds": ["not_a_number", null, true]}]""")

        val result = repo.loadPlaylists()
        assertNotNull(result)
        assertTrue("Array with invalid track types must be handled gracefully", result.isEmpty())
    }

    @Test
    fun testCorruptedJson_BinaryGarbage_HandledGracefully() {
        val (repo, file) = createRepository()
        val randomBytes = ByteArray(256) { Random.nextInt().toByte() }
        FileOutputStream(file).use { it.write(randomBytes) }

        val result = repo.loadPlaylists()
        assertNotNull(result)
        assertTrue("Binary garbage in file must return emptyList() without throwing", result.isEmpty())
    }

    @Test
    fun testZeroByteFile_SynthesizesDefaultFavoritesAtPosition0() {
        val (repo, file) = createRepository()
        file.writeText("") // exactly 0 bytes

        val result = repo.loadPlaylists()
        assertEquals(1, result.size)
        assertEquals(FAVORITES_PLAYLIST_ID, result[0].id)
        assertEquals(FAVORITES_PLAYLIST_NAME, result[0].name)
        assertTrue(result[0].trackIds.isEmpty())
        assertTrue(result[0].isFavorites)
    }

    @Test
    fun testWhitespaceOnlyFile_SynthesizesDefaultFavoritesAtPosition0() {
        val (repo, file) = createRepository()
        file.writeText("  \t \n \r\n  \t  ")

        val result = repo.loadPlaylists()
        assertEquals(1, result.size)
        assertEquals(FAVORITES_PLAYLIST_ID, result[0].id)
        assertEquals(FAVORITES_PLAYLIST_NAME, result[0].name)
        assertTrue(result[0].isFavorites)
    }

    // =========================================================================
    // SECTION 2: MISSING FAVORITES ID & INVARIANT RECOVERY
    // =========================================================================

    @Test
    fun testMissingFavoritesId_AutoSynthesizedAndPinnedAtIndex0() {
        val (repo, _) = createRepository()

        // Manually save playlists without favorites
        val p1 = Playlist(id = "p1", name = "Rock")
        val p2 = Playlist(id = "p2", name = "Jazz")
        val p3 = Playlist(id = "p3", name = "Classical")
        repo.savePlaylists(listOf(p1, p2, p3))

        val loaded = repo.loadPlaylists()
        assertEquals(4, loaded.size)
        assertEquals(FAVORITES_PLAYLIST_ID, loaded[0].id)
        assertEquals(FAVORITES_PLAYLIST_NAME, loaded[0].name)
        assertTrue(loaded[0].isFavorites)
        assertEquals("p1", loaded[1].id)
        assertEquals("p2", loaded[2].id)
        assertEquals("p3", loaded[3].id)
    }

    @Test
    fun testFavoritesAtMiddleOrEnd_ReorderedToStrictIndex0() {
        val (repo, _) = createRepository()

        val p1 = Playlist(id = "p1", name = "A")
        val p2 = Playlist(id = "p2", name = "B")
        val fav = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME, trackIds = listOf(99L))
        val p3 = Playlist(id = "p3", name = "C")

        // Save with favorites at index 2
        repo.savePlaylists(listOf(p1, p2, fav, p3))

        val loaded = repo.loadPlaylists()
        assertEquals(4, loaded.size)
        assertEquals(FAVORITES_PLAYLIST_ID, loaded[0].id)
        assertEquals(listOf(99L), loaded[0].trackIds)
        assertEquals("p1", loaded[1].id)
        assertEquals("p2", loaded[2].id)
        assertEquals("p3", loaded[3].id)
    }

    @Test
    fun testDuplicateFavoritesInRawFile_DeduplicatedToOneAtPosition0() {
        val (repo, _) = createRepository()

        val fav1 = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME, trackIds = listOf(1L, 2L))
        val fav2 = Playlist(id = FAVORITES_PLAYLIST_ID, name = "Duplicate Fav", trackIds = listOf(3L))
        val p1 = Playlist(id = "custom_p", name = "Custom")

        // Raw save of duplicate favorites IDs
        repo.savePlaylists(listOf(fav1, p1, fav2))

        val loaded = repo.loadPlaylists()
        assertEquals("Duplicate favorites must be deduplicated", 2, loaded.size)
        assertEquals(FAVORITES_PLAYLIST_ID, loaded[0].id)
        assertEquals(listOf(1L, 2L), loaded[0].trackIds)
        assertEquals("custom_p", loaded[1].id)
    }

    // =========================================================================
    // SECTION 3: RAPID CONSECUTIVE & CONCURRENT DELETIONS
    // =========================================================================

    @Test
    fun testRapidConsecutiveDeletionOfFavorites_AlwaysRejectedAndIntact() {
        val (repo, _) = createRepository()

        // Attempt to delete favorites 500 times consecutively
        for (i in 1..500) {
            val result = repo.deletePlaylist(FAVORITES_PLAYLIST_ID)
            assertFalse("deletePlaylist must return false for favorites ID on iteration $i", result)
        }

        val all = repo.loadPlaylists()
        assertEquals(1, all.size)
        assertEquals(FAVORITES_PLAYLIST_ID, all[0].id)
    }

    @Test
    fun testDeleteInvalidOrEmptyIds_ReturnsFalseAndPreservesPlaylists() {
        val (repo, _) = createRepository()
        repo.createPlaylist("Valid Playlist")

        val invalidIds = listOf("", "   ", "\t\n", "non_existent_uuid_12345", "!!!", "null")
        for (id in invalidIds) {
            val deleted = repo.deletePlaylist(id)
            assertFalse("Deletion of invalid ID '$id' must return false", deleted)
        }

        val all = repo.loadPlaylists()
        assertEquals(2, all.size)
        assertEquals(FAVORITES_PLAYLIST_ID, all[0].id)
        assertEquals("Valid Playlist", all[1].name)
    }

    @Test
    fun testConcurrentDeletions_FavoritesNeverDeletedUnderLoad() {
        val (repo, _) = createRepository()

        // Create 20 custom playlists
        val customPlaylists = (1..20).map { repo.createPlaylist("Playlist $it") }

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val deleteFavoritesAttempts = AtomicInteger(0)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    // Each thread tries to delete favorites and a subset of custom playlists
                    val deletedFav = repo.deletePlaylist(FAVORITES_PLAYLIST_ID)
                    if (deletedFav) deleteFavoritesAttempts.incrementAndGet()

                    val target = customPlaylists[t % customPlaylists.size]
                    repo.deletePlaylist(target.id)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Threads timed out", latch.await(10, TimeUnit.SECONDS))
        assertEquals("Favorites was deleted concurrently!", 0, deleteFavoritesAttempts.get())

        val remaining = repo.loadPlaylists()
        assertTrue("Remaining list must at least contain favorites", remaining.isNotEmpty())
        assertEquals("Favorites must be at index 0 after concurrent deletions", FAVORITES_PLAYLIST_ID, remaining[0].id)
    }

    // =========================================================================
    // SECTION 4: EDGE CASE TRACK IDS & DUPLICATE ADDITION PREVENTION
    // =========================================================================

    @Test
    fun testEdgeCaseTrackIds_LongMaxMinAndNegative_PreservedThroughPersistence() {
        val (repo, _) = createRepository()

        val edgeIds = listOf(
            Long.MAX_VALUE,      // 9223372036854775807L
            Long.MIN_VALUE + 1,  // Large negative long
            -1L,                 // Common sentinel value
            0L,                  // Zero
            100_000_000_000L     // Large positive value
        )

        for (id in edgeIds) {
            val added = repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, id)
            assertTrue("Should successfully add track ID $id to favorites", added)
        }

        // Reload fresh from disk and assert all values are identical without precision loss
        val reloaded = repo.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals("All edge case track IDs must be preserved exactly", edgeIds, reloaded.trackIds)
    }

    @Test
    fun testDuplicateAdditionToFavorites_Rejected() {
        val (repo, _) = createRepository()

        val trackId = 12345L
        assertTrue("First addition must return true", repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId))
        assertFalse("Second addition of same track must return false", repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId))
        assertFalse("Third addition of same track must return false", repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId))

        val fav = repo.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals(listOf(trackId), fav.trackIds)
    }

    @Test
    fun testConcurrentTrackAdditions_PreventsDuplicates() {
        val (repo, _) = createRepository()

        val trackId = 9999L
        val threadCount = 15
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            Thread {
                try {
                    if (repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId)) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent additions timed out", latch.await(10, TimeUnit.SECONDS))
        assertEquals("Exactly one thread should have succeeded in adding the track", 1, successCount.get())

        val fav = repo.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals("Favorites should have exactly 1 instance of the track", listOf(trackId), fav.trackIds)
    }

    @Test
    fun testRemoveNonExistentTrack_IsNoOpWithoutError() {
        val (repo, _) = createRepository()
        repo.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, 100L)

        // Remove track that does not exist
        repo.removeTrackFromPlaylist(FAVORITES_PLAYLIST_ID, 999L)

        val fav = repo.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals(listOf(100L), fav.trackIds)
    }

    // =========================================================================
    // SECTION 5: COMPREHENSIVE SORT PERMUTATIONS & FAVORITES PINNING INVARIANT
    // =========================================================================

    @Test
    fun testSortPermutations_AllOptionsAndDirections_FavoritesStrictlyAtPosition0() {
        val fav = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME, trackIds = listOf(1L, 2L, 3L))

        // Create 20 diverse custom playlists with different names, track counts, and stats
        val customList = (1..20).map { i ->
            val name = when (i % 5) {
                0 -> "Zebra Playlist $i"
                1 -> "Acoustic Vibes $i"
                2 -> "Rock Anthems $i"
                3 -> "Éléphant Pop $i"
                else -> "123 Numbered $i"
            }
            val trackCount = (i * 3) % 11
            val trackIds = (1..trackCount).map { it.toLong() * 100 + i }
            Playlist(id = "p_$i", name = name, trackIds = trackIds)
        }

        val allPlaylists = listOf(customList.take(5), listOf(fav), customList.drop(5)).flatten()

        val mockStats = (1..200).associate { id ->
            id.toLong() to TrackStats(
                audioId = id.toLong(),
                playCount = (id * 7) % 50,
                lastPlayedTimestamp = (id * 1000L)
            )
        }

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val sorted = allPlaylists.sortPlaylists(option, mockStats, isAscending = isAsc)

                assertEquals("Total playlist count must be preserved for $option (isAsc=$isAsc)",
                    allPlaylists.size, sorted.size)
                assertEquals("Favorites must be invariant at index 0 for $option (isAsc=$isAsc)",
                    FAVORITES_PLAYLIST_ID, sorted[0].id)
                assertTrue("First playlist must have isFavorites == true", sorted[0].isFavorites)

                // Verify non-favorites are sorted
                val nonFav = sorted.drop(1)
                assertFalse("Non-favorites must not contain favorites ID",
                    nonFav.any { it.id == FAVORITES_PLAYLIST_ID })

                if (option == SortOption.ALPHABETICAL) {
                    val names = nonFav.map { it.name.lowercase() }
                    val expected = if (isAsc) names.sorted() else names.sortedDescending()
                    assertEquals("Alphabetical ordering mismatch for isAsc=$isAsc", expected, names)
                }
            }
        }
    }

    @Test
    fun testSortPermutations_SingleFavoritesOnly_RemainsAtPosition0() {
        val favOnly = listOf(Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME))

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val sorted = favOnly.sortPlaylists(option, emptyMap(), isAscending = isAsc)
                assertEquals(1, sorted.size)
                assertEquals(FAVORITES_PLAYLIST_ID, sorted[0].id)
                assertTrue(sorted[0].isFavorites)
            }
        }
    }

    @Test
    fun testSortPermutations_WithoutFavorites_SortsGracefullyWithoutNPE() {
        val customOnly = listOf(
            Playlist(id = "1", name = "Zebra"),
            Playlist(id = "2", name = "Alpha")
        )

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val sorted = customOnly.sortPlaylists(option, emptyMap(), isAscending = isAsc)
                assertEquals(2, sorted.size)
                if (option == SortOption.ALPHABETICAL) {
                    if (isAsc) {
                        assertEquals("Alpha", sorted[0].name)
                        assertEquals("Zebra", sorted[1].name)
                    } else {
                        assertEquals("Zebra", sorted[0].name)
                        assertEquals("Alpha", sorted[1].name)
                    }
                }
            }
        }
    }

    @Test
    fun testSortPermutations_FavoritesAtVaryingInitialPositions() {
        val fav = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME)
        val p1 = Playlist(id = "1", name = "Beta")
        val p2 = Playlist(id = "2", name = "Charlie")
        val p3 = Playlist(id = "3", name = "Alpha")

        // Test favorites at initial index 0, 1, 2, 3
        val permutations = listOf(
            listOf(fav, p1, p2, p3),
            listOf(p1, fav, p2, p3),
            listOf(p1, p2, fav, p3),
            listOf(p1, p2, p3, fav)
        )

        for ((idx, list) in permutations.withIndex()) {
            for (option in SortOption.entries) {
                for (isAsc in listOf(true, false)) {
                    val sorted = list.sortPlaylists(option, emptyMap(), isAscending = isAsc)
                    assertEquals("Initial position $idx failed for $option (isAsc=$isAsc)",
                        FAVORITES_PLAYLIST_ID, sorted[0].id)
                    assertEquals(4, sorted.size)
                }
            }
        }
    }
}
