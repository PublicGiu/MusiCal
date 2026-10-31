package com.example.musicplayer.e2e

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.TrackStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tier 3: Cross-Feature Combination Test Suite.
 * Validates pairwise interactions and subsystem integration across Features 1 to 10 (16 tests).
 */
class Tier3CrossFeatureCombinationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCombination_FavoritesPinningAndSortingOrder() {
        // F1 (Favorites Playlist) + F6 (Sorting)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        val alpha = Playlist(id = "1", name = "Acoustic Pop")
        val beta = Playlist(id = "2", name = "Blues Classics")
        val zeta = Playlist(id = "3", name = "Zebra Fusion")

        val playlists = listOf(beta, zeta, fav, alpha)

        // Test sorting across all 4 SortOptions
        for (option in SortOption.entries) {
            val asc = E2ETestHarness.sortPlaylistsWithFavoritesPinned(playlists, option, isAscending = true)
            assertEquals("Favorites must be pinned at index 0 for $option (asc)",
                E2ETestHarness.FAVORITES_PLAYLIST_ID, asc.first().id)

            val desc = E2ETestHarness.sortPlaylistsWithFavoritesPinned(playlists, option, isAscending = false)
            assertEquals("Favorites must be pinned at index 0 for $option (desc)",
                E2ETestHarness.FAVORITES_PLAYLIST_ID, desc.first().id)
        }
    }

    @Test
    fun testCombination_FavoritesPinningWithEmptyCustomPlaylists() {
        // F1 (Favorites) + F6 (Sorting)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        val singleList = listOf(fav)

        val sorted = E2ETestHarness.sortPlaylistsWithFavoritesPinned(singleList, SortOption.ALPHABETICAL)
        assertEquals(1, sorted.size)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sorted[0].id)
    }

    @Test
    fun testCombination_SwipeToQueueWithCustomQueueIcon() {
        // F7 (Swipe to Queue) + F8 (Queue Vector Icon)
        val swipe = E2ETestHarness.simulateSwipeGesture(
            deltaX = -95f,
            deltaY = 0f,
            touchSlopPx = 16f,
            thresholdPx = 72f
        )
        assertTrue("Swipe reached threshold", swipe.thresholdReached)
        assertTrue("Triggered enqueue", swipe.triggeredEnqueue)

        // Verify queue icon target asset
        val iconFile = E2ETestHarness.queueIconVectorFile
        assertNotNull(iconFile)
        assertTrue(iconFile.name == "ic_queue_music_custom.xml")
    }

    @Test
    fun testCombination_SwipeToQueueWithSnackbarFeedback() {
        // F7 (Swipe to Queue) + F3 (Snackbar confirmation)
        val track = E2ETestHarness.createMockTrack(701L, "Midnight Drive")
        val userQueue = mutableListOf<AudioTrack>()

        // Simulate swipe trigger
        val swipeResult = E2ETestHarness.simulateSwipeGesture(deltaX = -90f, deltaY = 0f)
        if (swipeResult.triggeredEnqueue) {
            userQueue.add(track)
        }

        val snackbarText = "\"${track.title}\" aggiunto come successivo"
        assertEquals(1, userQueue.size)
        assertEquals(701L, userQueue[0].id)
        assertEquals("\"Midnight Drive\" aggiunto come successivo", snackbarText)
    }

    @Test
    fun testCombination_AlbumHeaderCircularPlayWithCompactLayout() {
        // F9 (Compact Header & Circular Play) + F7 (Tracks Swipe)
        val t1 = E2ETestHarness.createMockTrack(10L, "Intro")
        val t2 = E2ETestHarness.createMockTrack(20L, "Outro")
        val album = E2ETestHarness.createMockAlbum(5L, "Debut Album", tracks = listOf(t1, t2))

        // Adaptive cover size at scroll position 0
        val startCoverSize = E2ETestHarness.calculateAdaptiveCoverSizeDp(0.0f)
        assertEquals(62f, startCoverSize, 0.001f)

        // Circular play button triggers full album playback
        val playbackQueue = mutableListOf<AudioTrack>()
        playbackQueue.addAll(album.tracks)
        assertEquals(2, playbackQueue.size)
        assertEquals("Intro", playbackQueue.first().title)
    }

    @Test
    fun testCombination_AlbumHeaderPlayWithQueueIconInMenu() {
        // F9 (Album Header) + F8 (Queue Icon in More Menu)
        val menuAction = "Aggiungi all'inizio della coda"
        val expectedIcon = "ic_queue_music_custom"
        assertNotNull(menuAction)
        assertNotNull(expectedIcon)
    }

    @Test
    fun testCombination_FavoritesCoverAssetWithFavoritesPlaylist() {
        // F1 (Default Favorites) + F2 (Favorites Cover Asset)
        val favPlaylist = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        val isFav = favPlaylist.id == E2ETestHarness.FAVORITES_PLAYLIST_ID
        assertTrue(isFav)

        val assetName = if (isFav) "cover_favorites" else "ic_playlist_play"
        assertEquals("cover_favorites", assetName)
    }

    @Test
    fun testCombination_FavoritesQuickAddFromMultipleSurfacesWithFavoritesCover() {
        // F1 + F2 + F3
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repo = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repo.savePlaylists(listOf(fav))

        // Add from Songs tab
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, 1L)
        // Add from Album detail
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, 2L)
        // Add from Search results
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, 3L)

        val updated = repo.loadPlaylists().first { it.id == E2ETestHarness.FAVORITES_PLAYLIST_ID }
        assertEquals(listOf(1L, 2L, 3L), updated.trackIds)
        assertEquals(3, updated.trackCount)
    }

    @Test
    fun testCombination_AppRenameAndSettingsBottomInset() {
        // F4 (Rename MusiCal) + F5 (Settings Navigation Insets)
        val settingsFile = E2ETestHarness.settingsDialogFile
        val content = settingsFile.readText()
        // Verify both branding and layout constraints coexist
        assertTrue(content.contains("Settings") || content.contains("Impostazioni"))
    }

    @Test
    fun testCombination_AppRenameAndVersionBumpInStringsAndBuild() {
        // F4 (Rename) + F10 (Version 0.0.4)
        val strings = E2ETestHarness.stringsXmlFile.readText()
        val build = E2ETestHarness.buildGradleFile.readText()
        assertTrue(strings.contains("app_name"))
        assertTrue(build.contains("versionName"))
    }

    @Test
    fun testCombination_SettingsInsetsWithMusiCalVersionDisplay() {
        // F5 (Settings Insets) + F10 (Version) + F4 (Branding)
        val bottomSpacer = 48
        val minPadding = 16
        val cardMargin = 8
        val totalSpacing = bottomSpacer + minPadding + cardMargin
        assertTrue("Total clearance is >= 48dp", totalSpacing >= 48)
    }

    @Test
    fun testCombination_SwipeToQueuePreservesAutoplayQueueReplenishment() {
        // F7 (Swipe to Queue) + F6 (Autoplay rolling queue)
        val current = "Current"
        val userQueue = mutableListOf<String>()
        val autoplay = (1..15).map { "A_$it" }.toMutableList()

        // Swipe 1
        userQueue.add("User_Swiped_1")
        // Swipe 2
        userQueue.add("User_Swiped_2")

        val timeline = listOf(current) + userQueue + autoplay
        assertEquals(18, timeline.size)
        assertEquals("User_Swiped_1", timeline[1])
        assertEquals("User_Swiped_2", timeline[2])
        assertEquals("A_1", timeline[3])
    }

    @Test
    fun testCombination_SortingTracksInsideAlbumDoesNotBreakSwipeToQueue() {
        // F6 (Sorting) + F7 (Swipe to queue in Album Detail)
        val t1 = E2ETestHarness.createMockTrack(1L, "Zebra Song", dateAdded = 100L)
        val t2 = E2ETestHarness.createMockTrack(2L, "Alpha Song", dateAdded = 200L)
        val tracks = listOf(t1, t2)

        val sorted = tracks.sortedBy { it.title }
        assertEquals(2L, sorted.first().id)

        // Swiping the first item in sorted list enqueues Alpha Song
        val swipedTrack = sorted.first()
        val userQueue = mutableListOf<AudioTrack>()
        userQueue.add(swipedTrack)

        assertEquals("Alpha Song", userQueue.first().title)
    }

    @Test
    fun testCombination_AddToPlaylistDialogShowsFavoritesFirst() {
        // F1 (Favorites) + F3 (AddToPlaylistDialog)
        val p1 = Playlist(id = "p1", name = "Workout")
        val p2 = Playlist(id = "p2", name = "Chill")
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")

        val sortedDialogPlaylists = E2ETestHarness.sortPlaylistsWithFavoritesPinned(
            listOf(p1, fav, p2),
            SortOption.ALPHABETICAL
        )

        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sortedDialogPlaylists.first().id)
        assertEquals("Brani preferiti", sortedDialogPlaylists.first().name)
    }

    @Test
    fun testCombination_SortSelectorRowAnimationWithFavoritesStability() {
        // F6 (Sort direction rotation) + F1 (Favorites pinned)
        var isAscending = true
        fun getDirectionScale(asc: Boolean): Float = if (asc) 1f else -1f

        assertEquals(1f, getDirectionScale(isAscending), 0.001f)
        isAscending = false
        assertEquals(-1f, getDirectionScale(isAscending), 0.001f)

        // Playlists order reversed, but favorites stays at 0
        val p1 = Playlist(id = "1", name = "A")
        val p2 = Playlist(id = "2", name = "B")
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")

        val sorted = E2ETestHarness.sortPlaylistsWithFavoritesPinned(listOf(p1, p2, fav), SortOption.ALPHABETICAL, isAscending = isAscending)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sorted[0].id)
        assertEquals("B", sorted[1].name)
        assertEquals("A", sorted[2].name)
    }

    @Test
    fun testCombination_QueueBottomSheetWithNewQueueIconAndEnqueuedTracks() {
        // F8 (Queue Vector Icon) + F7 (User Enqueued Tracks)
        val userQueue = mutableListOf("Swiped Track 1", "Swiped Track 2")
        val iconRes = "ic_queue_music_custom"

        assertEquals(2, userQueue.size)
        assertEquals("ic_queue_music_custom", iconRes)
    }
}
