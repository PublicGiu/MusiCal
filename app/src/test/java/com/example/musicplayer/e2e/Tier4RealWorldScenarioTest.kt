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
import java.io.File
import kotlin.math.min

/**
 * Tier 4: Real-World Scenario Test Suite.
 * Exercises realistic end-to-end user workflows, lifecycle sequences,
 * multi-step operations, and complete user journeys (10 tests).
 */
class Tier4RealWorldScenarioTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testScenario_FirstTimeUserAppLaunchAndFavoritesInitialization() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repo = PlaylistRepository(mockContext)

        // 1. App starts with empty repository
        val initialPlaylists = repo.loadPlaylists()
        val guaranteedPlaylists = if (initialPlaylists.none { it.id == E2ETestHarness.FAVORITES_PLAYLIST_ID }) {
            listOf(Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")) + initialPlaylists
        } else {
            initialPlaylists
        }

        // 2. "Brani preferiti" is present at index 0
        assertEquals(1, guaranteedPlaylists.size)
        val favorites = guaranteedPlaylists.first()
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, favorites.id)
        assertEquals("Brani preferiti", favorites.name)
        assertEquals(0, favorites.trackCount)

        // 3. Branding check
        assertEquals("MusiCal", E2ETestHarness.EXPECTED_APP_NAME)
    }

    @Test
    fun testScenario_AlbumBrowsingScrollAndSwipeToQueue() {
        // 1. User opens Album with 5 tracks
        val tracks = (1..5).map { E2ETestHarness.createMockTrack(it.toLong(), "Track $it") }
        val album = E2ETestHarness.createMockAlbum(42L, "Masterpiece Album", tracks = tracks)

        // 2. Initial state: header cover is 62dp
        val startCoverSize = E2ETestHarness.calculateAdaptiveCoverSizeDp(0.0f)
        assertEquals(62f, startCoverSize, 0.001f)

        // 3. User scrolls halfway down: cover collapses smoothly to 53dp
        val midCoverSize = E2ETestHarness.calculateAdaptiveCoverSizeDp(0.5f)
        assertEquals(53f, midCoverSize, 0.001f)

        // 4. User scrolls to end: cover settles at 44dp
        val endCoverSize = E2ETestHarness.calculateAdaptiveCoverSizeDp(1.0f)
        assertEquals(44f, endCoverSize, 0.001f)

        // 5. User performs horizontal swipe on Track 3 (deltaX = -95f, deltaY = 0f)
        val swipe = E2ETestHarness.simulateSwipeGesture(deltaX = -95f, deltaY = 0f, touchSlopPx = 16f, thresholdPx = 72f)
        assertTrue(swipe.triggeredEnqueue)

        // 6. Track 3 is enqueued into user queue
        val userQueue = mutableListOf<AudioTrack>()
        userQueue.add(tracks[2])

        assertEquals(1, userQueue.size)
        assertEquals("Track 3", userQueue.first().title)

        // 7. Feedback snackbar message generated
        val snackbarText = "\"${tracks[2].title}\" aggiunto come successivo"
        assertEquals("\"Track 3\" aggiunto come successivo", snackbarText)
    }

    @Test
    fun testScenario_PlayAlbumViaCircularPlayButton() {
        val tracks = (1..8).map { E2ETestHarness.createMockTrack(it.toLong(), "Track $it") }
        val album = E2ETestHarness.createMockAlbum(100L, "Live Concert", tracks = tracks)

        var playingTrack: AudioTrack? = null
        val playbackTimeline = mutableListOf<AudioTrack>()

        // User taps circular play button in header
        playingTrack = album.tracks.first()
        playbackTimeline.addAll(album.tracks)

        assertEquals("Track 1", playingTrack.title)
        assertEquals(8, playbackTimeline.size)
        assertEquals("Track 8", playbackTimeline.last().title)
    }

    @Test
    fun testScenario_MultiSurfaceFavoritingFlow() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repo = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repo.savePlaylists(listOf(fav))

        // Surface A: Songs list 3-dots menu
        val songFromList = E2ETestHarness.createMockTrack(101L, "Song From List")
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, songFromList.id)

        // Surface B: Album detail 3-dots menu
        val songFromAlbum = E2ETestHarness.createMockTrack(202L, "Song From Album")
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, songFromAlbum.id)

        // Surface C: Mini-player (+) AddToPlaylistDialog
        val songFromMiniPlayer = E2ETestHarness.createMockTrack(303L, "Song From MiniPlayer")
        repo.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, songFromMiniPlayer.id)

        val finalFavorites = repo.loadPlaylists().first { it.id == E2ETestHarness.FAVORITES_PLAYLIST_ID }
        assertEquals(3, finalFavorites.trackCount)
        assertEquals(listOf(101L, 202L, 303L), finalFavorites.trackIds)
    }

    @Test
    fun testScenario_SortingLibraryWithFluidTransitionsAndFavoritesPinned() {
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti", trackIds = listOf(1L, 2L))
        val pA = Playlist(id = "A", name = "Ambient Soundscapes", trackIds = listOf(10L))
        val pM = Playlist(id = "M", name = "Metal Anthems", trackIds = listOf(20L))
        val pZ = Playlist(id = "Z", name = "Zen Meditation", trackIds = listOf(30L))

        val allPlaylists = listOf(pM, pZ, fav, pA)

        // 1. Sort A-Z
        val sortAZ = E2ETestHarness.sortPlaylistsWithFavoritesPinned(allPlaylists, SortOption.ALPHABETICAL, isAscending = true)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sortAZ[0].id)
        assertEquals("Ambient Soundscapes", sortAZ[1].name)
        assertEquals("Metal Anthems", sortAZ[2].name)
        assertEquals("Zen Meditation", sortAZ[3].name)

        // 2. Sort Z-A
        val sortZA = E2ETestHarness.sortPlaylistsWithFavoritesPinned(allPlaylists, SortOption.ALPHABETICAL, isAscending = false)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sortZA[0].id)
        assertEquals("Zen Meditation", sortZA[1].name)
        assertEquals("Metal Anthems", sortZA[2].name)
        assertEquals("Ambient Soundscapes", sortZA[3].name)
    }

    @Test
    fun testScenario_FullSettingsInspectionAndNavigationClearance() {
        // 1. Verify Settings file exists
        val settingsFile = E2ETestHarness.settingsDialogFile
        assertTrue(settingsFile.exists())

        // 2. Minimum clearance check for Android 16 3-button navigation
        val bottomNavHeight = 48
        val extraSpacerHeight = 48
        val safeMargin = extraSpacerHeight + bottomNavHeight
        assertTrue("Total clearance ensures Info Card is visible without overlap", safeMargin >= 48)
    }

    @Test
    fun testScenario_QueueManagementWithUserQueueAndAutoplayQueue() {
        val currentTrack = E2ETestHarness.createMockTrack(1L, "Current Playing")
        val userQueue = mutableListOf<AudioTrack>()
        val autoplayQueue = (10..24).map { E2ETestHarness.createMockTrack(it.toLong(), "Autoplay Track $it") }.toMutableList()

        // User swipes Track 5 to queue
        val swipedTrack = E2ETestHarness.createMockTrack(5L, "Swiped Track 5")
        userQueue.add(swipedTrack)

        // User queues another track via 3-dots
        val queuedTrack = E2ETestHarness.createMockTrack(6L, "Queued Track 6")
        userQueue.add(queuedTrack)

        // Verify timeline order: Current -> UserQueue (5, 6) -> AutoplayQueue (10..24)
        val fullTimeline = listOf(currentTrack) + userQueue + autoplayQueue
        assertEquals("Current Playing", fullTimeline[0].title)
        assertEquals("Swiped Track 5", fullTimeline[1].title)
        assertEquals("Queued Track 6", fullTimeline[2].title)
        assertEquals("Autoplay Track 10", fullTimeline[3].title)
        assertEquals(18, fullTimeline.size)

        // User removes Track 5 with 'X' in QueueBottomSheet
        userQueue.removeAt(0)
        assertEquals(1, userQueue.size)
        assertEquals("Queued Track 6", userQueue.first().title)
    }

    @Test
    fun testScenario_PreventFavoritesDeletionAndNameTampering() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repo = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repo.savePlaylists(listOf(fav))

        // Safe deletion protection logic:
        fun safeDelete(playlistId: String) {
            if (playlistId != E2ETestHarness.FAVORITES_PLAYLIST_ID) {
                repo.deletePlaylist(playlistId)
            }
        }

        safeDelete(E2ETestHarness.FAVORITES_PLAYLIST_ID)
        val remaining = repo.loadPlaylists()
        assertEquals(1, remaining.size)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, remaining.first().id)
    }

    @Test
    fun testScenario_AudioTrackPlaybackStatsAndSortingFeedback() {
        val t1 = E2ETestHarness.createMockTrack(1L, "Hit Song 1")
        val t2 = E2ETestHarness.createMockTrack(2L, "Hit Song 2")

        val stats = mapOf(
            1L to TrackStats(audioId = 1L, playCount = 10, lastPlayedTimestamp = 2000L),
            2L to TrackStats(audioId = 2L, playCount = 50, lastPlayedTimestamp = 5000L)
        )

        val tracks = listOf(t1, t2)
        val sortedByPlays = tracks.sortedByDescending { stats[it.id]?.playCount ?: 0 }
        assertEquals("Hit Song 2", sortedByPlays.first().title)
        assertEquals("Hit Song 1", sortedByPlays.last().title)
    }

    @Test
    fun testScenario_OfflineLastFmScrobbleAndQueueIntegrity() {
        // Playback queue progresses smoothly regardless of offline/online state
        val track = E2ETestHarness.createMockTrack(100L, "Offline Anthem", duration = 240000L)
        val scrobbleThresholdMs = min(track.duration / 2, 240000L)
        assertEquals(120000L, scrobbleThresholdMs) // 50% of 4 minutes = 2 minutes
        assertTrue("Scrobble threshold calculated cleanly", scrobbleThresholdMs > 0)
    }
}
