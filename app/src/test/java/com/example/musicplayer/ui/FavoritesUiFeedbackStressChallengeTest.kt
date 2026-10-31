package com.example.musicplayer.ui

import android.content.Context
import com.example.musicplayer.data.FAVORITES_PLAYLIST_ID
import com.example.musicplayer.data.FAVORITES_PLAYLIST_NAME
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.sortPlaylists
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Stress Test Suite for Milestone 3 (UI & Feedback Focus).
 * Authored by Challenger M3-2.
 *
 * Verifies:
 * 1. Rapid repeated taps & high-concurrency race condition resistance for "Aggiungi ai brani preferiti".
 * 2. Elimination and non-deletability of "Brani preferiti" in list, detail, and repository.
 * 3. Menu action wiring and consistency across all 4 view surfaces (Songs, Album, Playlist, Search).
 * 4. MiniPlayer '+' button and AddToPlaylistDialog quick selection integration.
 * 5. Pinned favorites invariant across massive random playlist sorting permutations.
 */
class FavoritesUiFeedbackStressChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "app").exists() && current.parentFile != null) {
            current = current.parentFile!!
        }
        current
    }

    private fun createRepository(): PlaylistRepository {
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)
        return PlaylistRepository(mockContext)
    }

    // =========================================================================
    // 1. RAPID REPEATED TAPS & CONCURRENCY STRESS
    // =========================================================================

    @Test
    fun testRapidRepeatedTapsSingleTrack_ExactDeduplication() {
        val repository = createRepository()
        val executor = Executors.newFixedThreadPool(16)
        val numTaps = 100
        val trackId = 9999L
        val latch = CountDownLatch(1)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val futures = (1..numTaps).map {
            executor.submit(Callable {
                latch.await()
                val added = repository.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId)
                if (added) {
                    successCount.incrementAndGet()
                } else {
                    failureCount.incrementAndGet()
                }
            })
        }

        // Fire all 100 taps simultaneously
        latch.countDown()
        executor.shutdown()
        assertTrue("Executor must complete within 10 seconds", executor.awaitTermination(10, TimeUnit.SECONDS))

        // Exactly one tap should have succeeded and 99 should have been safely rejected
        assertEquals("Exactly 1 tap must succeed in adding the track", 1, successCount.get())
        assertEquals("99 taps must return false (already present)", 99, failureCount.get())

        val playlists = repository.loadPlaylists()
        val favorites = playlists.first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals("Favorites playlist must contain exactly 1 track", 1, favorites.trackIds.size)
        assertEquals("Track ID must match", trackId, favorites.trackIds[0])
    }

    @Test
    fun testBurstRepeatedTapsMultipleTracksConcurrently() {
        val repository = createRepository()
        val executor = Executors.newFixedThreadPool(20)
        val numTracks = 25
        val tapsPerTrack = 8 // Total 200 concurrent requests
        val latch = CountDownLatch(1)
        val totalSuccess = AtomicInteger(0)

        val tasks = mutableListOf<Callable<Unit>>()
        for (trackIndex in 1..numTracks) {
            val trackId = 1000L + trackIndex
            repeat(tapsPerTrack) {
                tasks.add(Callable {
                    latch.await()
                    if (repository.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId)) {
                        totalSuccess.incrementAndGet()
                    }
                })
            }
        }

        tasks.shuffle()
        tasks.forEach { executor.submit(it) }

        latch.countDown()
        executor.shutdown()
        assertTrue("Executor must terminate within 15 seconds", executor.awaitTermination(15, TimeUnit.SECONDS))

        assertEquals("Each distinct track should be added exactly once", numTracks, totalSuccess.get())

        val favorites = repository.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals("Favorites must contain exactly $numTracks tracks", numTracks, favorites.trackIds.size)
        val expectedSet = (1..numTracks).map { 1000L + it }.toSet()
        assertEquals("All expected tracks must be present", expectedSet, favorites.trackIds.toSet())
    }

    @Test
    fun testInterleavedRapidAddAndRemoveStress() {
        val repository = createRepository()
        val executor = Executors.newFixedThreadPool(16)
        val latch = CountDownLatch(1)
        val trackId = 5555L

        // Interleave 50 add and 50 remove calls in parallel
        val numOps = 50
        val tasks = mutableListOf<Callable<Unit>>()
        repeat(numOps) {
            tasks.add(Callable<Unit> {
                latch.await()
                repository.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, trackId)
                Unit
            })
            tasks.add(Callable<Unit> {
                latch.await()
                repository.removeTrackFromPlaylist(FAVORITES_PLAYLIST_ID, trackId)
                Unit
            })
        }
        tasks.shuffle()
        tasks.forEach { executor.submit(it) }

        latch.countDown()
        executor.shutdown()
        assertTrue("Executor must terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        // State must remain clean, no exceptions, trackIds either empty or contains trackId once
        val favorites = repository.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertTrue("Track count must be 0 or 1", favorites.trackIds.size in 0..1)
        if (favorites.trackIds.isNotEmpty()) {
            assertEquals(listOf(trackId), favorites.trackIds)
        }
    }

    // =========================================================================
    // 2. FAVORITES IMMUTABILITY & DELETION PREVENTION
    // =========================================================================

    @Test
    fun testDeleteFavoritesDirectCallAlwaysReturnsFalse() {
        val repository = createRepository()

        // Attempt deletion 10 times in a row
        repeat(10) {
            val deleted = repository.deletePlaylist(FAVORITES_PLAYLIST_ID)
            assertFalse("deletePlaylist must return false for FAVORITES_PLAYLIST_ID", deleted)
        }

        val all = repository.loadPlaylists()
        assertEquals(1, all.size)
        assertEquals(FAVORITES_PLAYLIST_ID, all[0].id)
        assertTrue(all[0].isFavorites)
    }

    @Test
    fun testFavoritesCannotBeDeletedEvenWithCustomPlaylists() {
        val repository = createRepository()
        val c1 = repository.createPlaylist("Custom 1")
        val c2 = repository.createPlaylist("Custom 2")

        assertEquals(3, repository.loadPlaylists().size)

        // Attempt deleting favorites
        assertFalse(repository.deletePlaylist(FAVORITES_PLAYLIST_ID))
        assertEquals(3, repository.loadPlaylists().size)

        // Delete custom 1
        assertTrue(repository.deletePlaylist(c1.id))
        val remaining = repository.loadPlaylists()
        assertEquals(2, remaining.size)
        assertEquals(FAVORITES_PLAYLIST_ID, remaining[0].id)
        assertEquals(c2.id, remaining[1].id)
    }

    // =========================================================================
    // 3. CODEBASE AST & TOUCHPOINT STATIC AUDIT
    // =========================================================================

    @Test
    fun testEliminaPlaylistSuppressedInPlaylistsView() {
        val playlistsViewFile = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/PlaylistsView.kt")
        assertTrue("PlaylistsView.kt must exist", playlistsViewFile.exists())
        val content = playlistsViewFile.readText()

        // 1. PlaylistItem must guard "Elimina playlist" with !playlist.isFavorites
        val guardRegex = Regex("""if\s*\(\s*!playlist\.isFavorites\s*\)\s*\{[\s\S]*?text\s*=\s*\"Elimina playlist\"""")
        assertTrue("PlaylistItem must guard 'Elimina playlist' with if (!playlist.isFavorites)", guardRegex.containsMatchIn(content))

        // 2. PlaylistDetailView must NOT contain "Elimina playlist"
        val detailViewSection = content.substringAfter("fun PlaylistDetailView").substringBefore("fun CreatePlaylistDialog")
        assertFalse("PlaylistDetailView must not offer 'Elimina playlist' menu option", detailViewSection.contains("Elimina playlist"))

        // 3. PlaylistDetailView must bind cover_favorites when isFavorites
        assertTrue("PlaylistDetailView must reference cover_favorites for header",
            detailViewSection.contains("R.drawable.cover_favorites"))

        // 4. PlaylistDetailView must suppress onAddToFavoritesClick when viewing Favorites
        assertTrue("PlaylistDetailView must suppress onAddToFavoritesClick when playlist.isFavorites",
            detailViewSection.contains("if (!playlist.isFavorites) onAddToFavoritesClick?.let { cb -> { cb(track) } } else null"))
    }

    @Test
    fun testAllFourSurfacesWireFavoritesQuickAction() {
        val uiDir = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui")

        // 1. TrackListItem.kt must declare onAddToFavoritesClick with FavoriteBorder icon
        val trackItemFile = File(uiDir, "components/TrackListItem.kt")
        val trackItemContent = trackItemFile.readText()
        assertTrue("TrackListItem must accept onAddToFavoritesClick", trackItemContent.contains("onAddToFavoritesClick: (() -> Unit)? = null"))
        assertTrue("TrackListItem must display 'Aggiungi ai brani preferiti'", trackItemContent.contains("Aggiungi ai brani preferiti"))
        assertTrue("TrackListItem must use FavoriteBorder icon", trackItemContent.contains("Icons.Default.FavoriteBorder"))

        // 2. AlbumDetailView.kt must accept and forward onAddToFavoritesClick
        val albumDetailFile = File(uiDir, "components/AlbumDetailView.kt")
        val albumDetailContent = albumDetailFile.readText()
        assertTrue("AlbumDetailView must declare onAddToFavoritesClick", albumDetailContent.contains("onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null"))
        assertTrue("AlbumDetailView must forward onAddToFavoritesClick to TrackListItem",
            albumDetailContent.contains("onAddToFavoritesClick = onAddToFavoritesClick?.let { cb -> { cb(track) } }"))

        // 3. GlobalSearchResultsView.kt must accept and forward onAddToFavoritesClick
        val searchViewFile = File(uiDir, "components/GlobalSearchResultsView.kt")
        val searchViewContent = searchViewFile.readText()
        assertTrue("GlobalSearchResultsView must declare onAddToFavoritesClick", searchViewContent.contains("onAddToFavoritesClick: ((AudioTrack) -> Unit)? = null"))
        assertTrue("GlobalSearchResultsView must forward onAddToFavoritesClick to TrackListItem",
            searchViewContent.contains("onAddToFavoritesClick = onAddToFavoritesClick?.let { cb -> { cb(track) } }"))

        // 4. MainScreen.kt must wire handleAddToFavorites across all 4 surfaces
        val mainScreenFile = File(uiDir, "MainScreen.kt")
        val mainScreenContent = mainScreenFile.readText()
        assertTrue("MainScreen must define handleAddToFavorites", mainScreenContent.contains("val handleAddToFavorites: (AudioTrack) -> Unit"))
        assertTrue("handleAddToFavorites must call addTrackToPlaylist for FAVORITES_PLAYLIST_ID",
            mainScreenContent.contains("viewModel.addTrackToPlaylist(Playlist.FAVORITES_PLAYLIST_ID, track.id)"))
        assertTrue("handleAddToFavorites must trigger Snackbar with aggiunto ai preferiti",
            mainScreenContent.contains("\\\"${'$'}{track.title}\\\" aggiunto ai preferiti"))

        // Surface wiring checks:
        assertTrue("MainScreen must wire onAddToFavoritesClick to Songs TrackListItem",
            mainScreenContent.contains("onAddToFavoritesClick = { handleAddToFavorites(track) }"))
        assertTrue("MainScreen must wire onAddToFavoritesClick to AlbumDetailView",
            mainScreenContent.contains("AlbumDetailView(") && mainScreenContent.contains("onAddToFavoritesClick = handleAddToFavorites"))
        assertTrue("MainScreen must wire onAddToFavoritesClick to PlaylistDetailView",
            mainScreenContent.contains("PlaylistDetailView(") && mainScreenContent.contains("onAddToFavoritesClick = handleAddToFavorites"))
        assertTrue("MainScreen must wire onAddToFavoritesClick to GlobalSearchResultsView",
            mainScreenContent.contains("GlobalSearchResultsView(") && mainScreenContent.contains("onAddToFavoritesClick = handleAddToFavorites"))
    }

    @Test
    fun testMiniPlayerPlusButtonAndAddToPlaylistDialogIntegration() {
        val playerBottomBarFile = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/PlayerBottomBar.kt")
        val bottomBarContent = playerBottomBarFile.readText()

        // 1. MiniPlayer must have + button with Icons.Default.Add
        assertTrue("PlayerBottomBar must contain + button invoking onAddToPlaylistClick",
            bottomBarContent.contains("Icons.Default.Add") &&
                    bottomBarContent.contains("contentDescription = \"Aggiungi a playlist\"") &&
                    bottomBarContent.contains("onAddToPlaylistClick?.invoke(it)"))

        // 2. AddToPlaylistDialog in PlaylistsView.kt
        val playlistsViewFile = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/PlaylistsView.kt")
        val playlistsViewContent = playlistsViewFile.readText()
        assertTrue("AddToPlaylistDialog must pin favorites at top",
            playlistsViewContent.contains("val favorites = playlists.filter { it.isFavorites }") &&
                    playlistsViewContent.contains("favorites + others"))
        assertTrue("AddToPlaylistDialog must render cover_favorites",
            playlistsViewContent.contains("painterResource(R.drawable.cover_favorites)"))
        assertTrue("AddToPlaylistDialog must render heart icon for favorites",
            playlistsViewContent.contains("Icons.Default.Favorite"))

        // 3. MainScreen routing when selecting Favorites from AddToPlaylistDialog
        val mainScreenFile = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/MainScreen.kt")
        val mainScreenContent = mainScreenFile.readText()
        val dialogRouteRegex = Regex("""AddToPlaylistDialog\([\s\S]*?if\s*\(\s*playlist\.id\s*==\s*Playlist\.FAVORITES_PLAYLIST_ID\s*\)\s*\{\s*handleAddToFavorites\(track\)""")
        assertTrue("Selecting Favorites in AddToPlaylistDialog must route to handleAddToFavorites", dialogRouteRegex.containsMatchIn(mainScreenContent))
    }

    // =========================================================================
    // 4. SORTING INVARIANT STRESS TEST
    // =========================================================================

    @Test
    fun testFavoritesAlwaysPinnedIndexZeroAcrossMassiveRandomPlaylists() {
        val fav = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME)
        val dummyStats = mapOf(
            1L to TrackStats(1L, playCount = 10, lastPlayedTimestamp = 1000L),
            2L to TrackStats(2L, playCount = 50, lastPlayedTimestamp = 2000L)
        )

        // Generate 50 custom playlists with random names and tracks
        val customPlaylists = (1..50).map { i ->
            val randomChar = ('A'..'Z').random()
            Playlist(
                id = "custom_$i",
                name = "$randomChar-Playlist-$i",
                trackIds = if (i % 2 == 0) listOf(1L) else listOf(2L)
            )
        }

        // Test with favorites at random index initially
        val mixedList = (customPlaylists.take(25) + listOf(fav) + customPlaylists.drop(25)).shuffled()

        for (option in SortOption.entries) {
            for (isAsc in listOf(true, false)) {
                val sorted = mixedList.sortPlaylists(option, stats = dummyStats, isAscending = isAsc)
                assertEquals("List size must be preserved", 51, sorted.size)
                assertEquals("Favorites MUST be at index 0 for $option (isAsc=$isAsc)", FAVORITES_PLAYLIST_ID, sorted[0].id)
                assertTrue("First item must report isFavorites=true", sorted[0].isFavorites)

                // Verify that no other item is favorites
                val favoritesCount = sorted.count { it.isFavorites }
                assertEquals("There must be exactly 1 favorites playlist", 1, favoritesCount)
            }
        }
    }
}
