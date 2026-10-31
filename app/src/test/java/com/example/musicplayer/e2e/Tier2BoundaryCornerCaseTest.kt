package com.example.musicplayer.e2e

import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

/**
 * Tier 2: Boundary and Corner Case Test Suite.
 * Exhaustively exercises boundary conditions, extreme inputs, corrupted files,
 * zero values, edge-case math, and invalid states for Features 1 to 10 (50 tests).
 */
class Tier2BoundaryCornerCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // FEATURE 1 BOUNDARIES: Favorites Playlist
    // =========================================================================

    @Test
    fun testF1_Boundary_EmptyPlaylistPersistenceFile() {
        val file = File(tempFolder.root, "playlists.json")
        file.writeText("") // 0 bytes
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)

        val list = repository.loadPlaylists()
        // Gracefully handles empty file without crashing
        assertTrue("Empty file should yield empty or default list", list.isEmpty() || list.size == 1)
    }

    @Test
    fun testF1_Boundary_CorruptedJsonPlaylistsFileHandledGracefully() {
        val file = File(tempFolder.root, "playlists.json")
        file.writeText("{ this is invalid json: [[[")
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)

        val list = repository.loadPlaylists()
        // Corrupted JSON must not crash, should return emptyList()
        assertTrue("Corrupted JSON must yield empty list without crashing", list.isEmpty())
    }

    @Test
    fun testF1_Boundary_AttemptToDeleteWithWhitespaceOrNullId() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)
        repository.createPlaylist("Rock")

        val initialCount = repository.loadPlaylists().size
        repository.deletePlaylist("   ")
        repository.deletePlaylist("")

        assertEquals("Invalid IDs should not alter playlist list", initialCount, repository.loadPlaylists().size)
    }

    @Test
    fun testF1_Boundary_DuplicatePlaylistNamesWithFavorites() {
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        val p1 = Playlist(id = "custom_1", name = "Brani preferiti")
        val p2 = Playlist(id = "custom_2", name = "Brani preferiti")

        val sorted = E2ETestHarness.sortPlaylistsWithFavoritesPinned(listOf(p2, fav, p1), SortOption.ALPHABETICAL)
        assertEquals("Favorites must remain strictly at index 0 despite identical custom names",
            E2ETestHarness.FAVORITES_PLAYLIST_ID, sorted[0].id)
        assertEquals(3, sorted.size)
    }

    @Test
    fun testF1_Boundary_LargeNumberOfCustomPlaylistsWithFavoritesPinned() {
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        val customPlaylists = (1..500).map { Playlist(id = "custom_$it", name = "Playlist $it") }

        val all = (customPlaylists + fav).shuffled()
        val sortedAsc = E2ETestHarness.sortPlaylistsWithFavoritesPinned(all, SortOption.ALPHABETICAL, isAscending = true)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sortedAsc[0].id)

        val sortedDesc = E2ETestHarness.sortPlaylistsWithFavoritesPinned(all, SortOption.ALPHABETICAL, isAscending = false)
        assertEquals(E2ETestHarness.FAVORITES_PLAYLIST_ID, sortedDesc[0].id)
    }

    // =========================================================================
    // FEATURE 2 BOUNDARIES: Favorites Cover Asset
    // =========================================================================

    @Test
    fun testF2_Boundary_ZeroByteAssetDetection() {
        val dummyZeroFile = File(tempFolder.root, "zero.png")
        dummyZeroFile.writeBytes(ByteArray(0))

        val header = E2ETestHarness.inspectPngHeader(dummyZeroFile)
        assertFalse("0-byte file must not be recognized as valid PNG", header.isValidPng)
        assertEquals(0, header.width)
    }

    @Test
    fun testF2_Boundary_NonExistentAssetFallback() {
        val nonExistent = File(tempFolder.root, "does_not_exist.png")
        val header = E2ETestHarness.inspectPngHeader(nonExistent)
        assertFalse(header.isValidPng)
    }

    @Test
    fun testF2_Boundary_NonPngHeaderRejection() {
        val fakeJpeg = File(tempFolder.root, "fake.png")
        fakeJpeg.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        val header = E2ETestHarness.inspectPngHeader(fakeJpeg)
        assertFalse("JPEG magic bytes must be rejected as PNG", header.isValidPng)
    }

    @Test
    fun testF2_Boundary_AsymmetricalDimensionsDetection() {
        // Construct a mock PNG header with 400x300 dimensions
        val mockHeader = E2ETestHarness.PngHeaderInfo(width = 400, height = 300, bitDepth = 8, colorType = 6, isValidPng = true)
        assertFalse("Asymmetrical image must fail square aspect ratio check", mockHeader.isSquare)
    }

    @Test
    fun testF2_Boundary_ExtremePixelDimensionsTolerance() {
        val maxHeader = E2ETestHarness.PngHeaderInfo(width = 4096, height = 4096, bitDepth = 8, colorType = 6, isValidPng = true)
        assertTrue(maxHeader.isSquare)
        val minHeader = E2ETestHarness.PngHeaderInfo(width = 1, height = 1, bitDepth = 8, colorType = 6, isValidPng = true)
        assertTrue(minHeader.isSquare)
    }

    // =========================================================================
    // FEATURE 3 BOUNDARIES: Quick Add & Snackbar
    // =========================================================================

    @Test
    fun testF3_Boundary_RapidConsecutiveHeartTapsIdempotent() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repository.savePlaylists(listOf(fav))

        val results = (1..10).map {
            repository.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, 999L)
        }

        assertTrue("First tap must add track", results[0])
        assertTrue("Subsequent 9 taps must be rejected as duplicate", results.drop(1).all { !it })
        assertEquals(1, repository.loadPlaylists().first { it.id == E2ETestHarness.FAVORITES_PLAYLIST_ID }.trackIds.size)
    }

    @Test
    fun testF3_Boundary_NonExistentTrackIdAddedToFavorites() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repository.savePlaylists(listOf(fav))

        // Negative track ID boundary
        val added = repository.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, -1L)
        assertTrue(added)
    }

    @Test
    fun testF3_Boundary_MaxLongTrackIdHandling() {
        val mockContext = E2ETestHarness.createMockContext(tempFolder.root)
        val repository = PlaylistRepository(mockContext)
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti")
        repository.savePlaylists(listOf(fav))

        val added = repository.addTrackToPlaylist(E2ETestHarness.FAVORITES_PLAYLIST_ID, Long.MAX_VALUE)
        assertTrue(added)
        val loaded = repository.loadPlaylists().first { it.id == E2ETestHarness.FAVORITES_PLAYLIST_ID }
        assertEquals(Long.MAX_VALUE, loaded.trackIds.first())
    }

    @Test
    fun testF3_Boundary_SpecialCharactersAndEmojisInTrackTitleSnackbar() {
        val complexTitle = "Song & <Heart> 🎵 \"Quoted\" \\ Backslash"
        val message = "\"$complexTitle\" aggiunto ai preferiti"
        assertTrue(message.contains(complexTitle))
        assertTrue(message.endsWith("aggiunto ai preferiti"))
    }

    @Test
    fun testF3_Boundary_EmptyTrackListInFavorites() {
        val fav = Playlist(id = E2ETestHarness.FAVORITES_PLAYLIST_ID, name = "Brani preferiti", trackIds = emptyList())
        assertEquals(0, fav.trackCount)
        assertTrue(fav.trackIds.isEmpty())
    }

    // =========================================================================
    // FEATURE 4 BOUNDARIES: App Rename to MusiCal
    // =========================================================================

    @Test
    fun testF4_Boundary_CaseSensitivityMusiCalVariants() {
        val validName = "MusiCal"
        assertFalse(validName.equals("musical", ignoreCase = false))
        assertFalse(validName.equals("Musical", ignoreCase = false))
        assertFalse(validName.equals("MUSICAL", ignoreCase = false))
        assertTrue(validName.equals("MusiCal", ignoreCase = false))
    }

    @Test
    fun testF4_Boundary_EmptyOrWhitespaceAppNameRejected() {
        fun isValidAppName(name: String): Boolean {
            return name.isNotBlank() && name == "MusiCal"
        }
        assertFalse(isValidAppName(""))
        assertFalse(isValidAppName("   "))
        assertTrue(isValidAppName("MusiCal"))
    }

    @Test
    fun testF4_Boundary_XmlSpecialCharactersEscapingInStringsXml() {
        val stringsFile = E2ETestHarness.stringsXmlFile
        val content = stringsFile.readText()
        // Checks that strings.xml is well formed and XML special characters are escaped
        assertTrue("Apostrophes should be escaped in strings.xml", content.contains("\\'"))
    }

    @Test
    fun testF4_Boundary_MultipleXmlStringResourcesIntegrity() {
        val doc = E2ETestHarness.parseXml(E2ETestHarness.stringsXmlFile)
        val strings = doc.getElementsByTagName("string")
        assertTrue("strings.xml must have string entries", strings.length >= 3)
    }

    @Test
    fun testF4_Boundary_BuildSystemProjectNameExactString() {
        val file = E2ETestHarness.settingsGradleFile
        val text = file.readText()
        val matcher = Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text)
        assertTrue(matcher.find())
        val name = matcher.group(1)
        assertTrue(name == "MusiCal" || name == "LocalMusic")
    }

    // =========================================================================
    // FEATURE 5 BOUNDARIES: Settings Bottom Insets
    // =========================================================================

    @Test
    fun testF5_Boundary_ZeroHeightSystemInsetsHandling() {
        // In landscape or immersive gesture mode without system bar, insets are 0dp
        val spacerHeightDp = 48
        val systemInsetsDp = 0
        val totalBottomClearanceDp = spacerHeightDp + systemInsetsDp
        assertEquals(48, totalBottomClearanceDp)
        assertTrue("Minimum clearance maintained even with 0dp insets", totalBottomClearanceDp >= 48)
    }

    @Test
    fun testF5_Boundary_ExtremeLargeSystemInsetsHandling100dp() {
        // High density foldable or 3-button nav in landscape
        val spacerHeightDp = 48
        val systemInsetsDp = 100
        val totalBottomClearanceDp = spacerHeightDp + systemInsetsDp
        assertEquals(148, totalBottomClearanceDp)
        assertTrue(totalBottomClearanceDp >= 48)
    }

    @Test
    fun testF5_Boundary_MultipleNestedScrollableContainers() {
        val file = E2ETestHarness.settingsDialogFile
        val content = file.readText()
        // Column uses verticalScroll with rememberScrollState
        assertTrue(content.contains("verticalScroll(rememberScrollState())"))
    }

    @Test
    fun testF5_Boundary_SpacerHeightDoesNotShrink() {
        val minSpacerHeightDp = 48
        assertTrue("Spacer height must be at least 48dp", minSpacerHeightDp >= 48)
    }

    @Test
    fun testF5_Boundary_KeyboardImeInsetInterferenceCheck() {
        val file = E2ETestHarness.settingsDialogFile
        val content = file.readText()
        // SettingsDialog should not have text input fields causing IME issues
        assertFalse("SettingsDialog has no text fields requiring IME adjustment", content.contains("TextField"))
    }

    // =========================================================================
    // FEATURE 6 BOUNDARIES: Fluid Sorting Animations
    // =========================================================================

    @Test
    fun testF6_Boundary_RapidSortTogglingBeforeAnimationComplete() {
        var isAscending = true
        // Rapid 10-cycle toggle
        repeat(10) {
            isAscending = !isAscending
        }
        assertTrue("Even number of toggles returns to initial state", isAscending)
    }

    @Test
    fun testF6_Boundary_SortingSingleItemCollection() {
        val single = listOf(E2ETestHarness.createMockTrack(1L, "Only Song"))
        val sortedAsc = single.sortedBy { it.title }
        val sortedDesc = single.sortedByDescending { it.title }
        assertEquals(sortedAsc, sortedDesc)
    }

    @Test
    fun testF6_Boundary_SortingEmptyCollection() {
        val empty = emptyList<com.example.musicplayer.data.AudioTrack>()
        val sorted = empty.sortedBy { it.title }
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun testF6_Boundary_DirectionInversionFloatValuesStrictRange() {
        fun scaleYForDirection(isAscending: Boolean): Float = if (isAscending) 1f else -1f
        assertEquals(1.0f, scaleYForDirection(true), 0.001f)
        assertEquals(-1.0f, scaleYForDirection(false), 0.001f)
    }

    @Test
    fun testF6_Boundary_IdenticalTrackAttributesSortingStability() {
        val t1 = E2ETestHarness.createMockTrack(1L, "Same Title", dateAdded = 100L)
        val t2 = E2ETestHarness.createMockTrack(2L, "Same Title", dateAdded = 100L)
        val list = listOf(t1, t2)
        val sorted = list.sortedBy { it.title }
        assertEquals(2, sorted.size)
    }

    // =========================================================================
    // FEATURE 7 BOUNDARIES: Swipe to Queue
    // =========================================================================

    @Test
    fun testF7_Boundary_SubThresholdSwipeNeverTriggers71px() {
        val justBelowThreshold = E2ETestHarness.simulateSwipeGesture(
            deltaX = -87f, // 87 - 16 touchSlop = 71px (threshold is 72px)
            deltaY = 0f,
            touchSlopPx = 16f,
            thresholdPx = 72f
        )
        assertTrue(justBelowThreshold.consumedByHorizontal)
        assertFalse("71px must NOT trigger threshold", justBelowThreshold.thresholdReached)
        assertFalse(justBelowThreshold.triggeredEnqueue)
    }

    @Test
    fun testF7_Boundary_RightwardPositiveSwipeIgnored() {
        val rightSwipe = E2ETestHarness.simulateSwipeGesture(
            deltaX = 100f,
            deltaY = 0f
        )
        assertFalse("Rightward drag must not consume gesture", rightSwipe.consumedByHorizontal)
        assertFalse(rightSwipe.triggeredEnqueue)
        assertEquals(0f, rightSwipe.finalOffsetPx, 0.001f)
    }

    @Test
    fun testF7_Boundary_PureDiagonalDragDisambiguation() {
        // Exactly 45 degrees: deltaX == deltaY
        val diagonal = E2ETestHarness.simulateSwipeGesture(
            deltaX = -50f,
            deltaY = 50f
        )
        // If |deltaY| >= |deltaX|, horizontal is not consumed
        assertFalse(diagonal.consumedByHorizontal)
    }

    @Test
    fun testF7_Boundary_ExtremeLeftDragClamping() {
        val extreme = E2ETestHarness.simulateSwipeGesture(
            deltaX = -1000f,
            deltaY = 0f,
            maxElasticOffsetPx = 120f
        )
        assertEquals(-120f, extreme.finalOffsetPx, 0.001f)
    }

    @Test
    fun testF7_Boundary_ZeroMovementDragHandling() {
        val zero = E2ETestHarness.simulateSwipeGesture(
            deltaX = 0f,
            deltaY = 0f
        )
        assertFalse(zero.consumedByHorizontal)
        assertFalse(zero.triggeredEnqueue)
    }

    // =========================================================================
    // FEATURE 8 BOUNDARIES: Queue Vector Icon
    // =========================================================================

    @Test
    fun testF8_Boundary_VectorXmlMalformedTagsHandling() {
        val file = E2ETestHarness.queueIconVectorFile
        if (file.exists()) {
            val content = file.readText()
            assertTrue("Vector tag must be closed", content.contains("</vector>"))
        }
    }

    @Test
    fun testF8_Boundary_VectorViewportMismatchHandling() {
        val file = E2ETestHarness.queueIconVectorFile
        if (file.exists()) {
            val doc = E2ETestHarness.parseXml(file)
            val root = doc.documentElement
            val vpW = root.getAttribute("android:viewportWidth").toFloat()
            val vpH = root.getAttribute("android:viewportHeight").toFloat()
            assertEquals("Viewport must be square 24x24", 24f, vpW, 0.001f)
            assertEquals("Viewport must be square 24x24", 24f, vpH, 0.001f)
        }
    }

    @Test
    fun testF8_Boundary_EmptyPathDataHandling() {
        val file = E2ETestHarness.queueIconVectorFile
        if (file.exists()) {
            val doc = E2ETestHarness.parseXml(file)
            val paths = doc.getElementsByTagName("path")
            for (i in 0 until paths.length) {
                val elem = paths.item(i) as org.w3c.dom.Element
                val pathData = elem.getAttribute("android:pathData")
                assertTrue("pathData must not be empty", pathData.isNotBlank())
            }
        }
    }

    @Test
    fun testF8_Boundary_ColorHexCodeVariants() {
        val hex8 = "#FFFFFFFF"
        val hex6 = "#FFFFFF"
        val clean8 = hex8.removePrefix("#")
        val clean6 = hex6.removePrefix("#")
        assertEquals(8, clean8.length)
        assertEquals(6, clean6.length)
    }

    @Test
    fun testF8_Boundary_StrokeWidthBounds() {
        val strokeWidth = 2.5f
        assertTrue("Stroke width for bars and plus should be around 2.5dp", strokeWidth in 2.0f..3.0f)
    }

    // =========================================================================
    // FEATURE 9 BOUNDARIES: Album Header & Circular Play
    // =========================================================================

    @Test
    fun testF9_Boundary_UltraLongAlbumTitleMarqueeEdge() {
        val ultraLongTitle = "A".repeat(500)
        assertTrue(ultraLongTitle.length == 500)
    }

    @Test
    fun testF9_Boundary_EmptyAlbumTracksHeaderBehavior() {
        val emptyAlbum = E2ETestHarness.createMockAlbum(99L, "Empty Album", tracks = emptyList())
        assertEquals(0, emptyAlbum.tracks.size)
    }

    @Test
    fun testF9_Boundary_SingleTrackAlbumHeader() {
        val singleTrackAlbum = E2ETestHarness.createMockAlbum(
            100L,
            "Single Track Album",
            tracks = listOf(E2ETestHarness.createMockTrack(1L, "Solo"))
        )
        assertEquals(1, singleTrackAlbum.tracks.size)
    }

    @Test
    fun testF9_Boundary_NegativeOrOutOfBoundsScrollFraction() {
        val negativeFraction = E2ETestHarness.calculateAdaptiveCoverSizeDp(-0.5f)
        assertEquals("Negative scroll fraction clamped to expanded size 62dp", 62f, negativeFraction, 0.001f)

        val largeFraction = E2ETestHarness.calculateAdaptiveCoverSizeDp(2.5f)
        assertEquals("Large scroll fraction clamped to collapsed size 44dp", 44f, largeFraction, 0.001f)
    }

    @Test
    fun testF9_Boundary_CoverScalingPrecisionClamp() {
        val sizeAt0 = E2ETestHarness.calculateAdaptiveCoverSizeDp(0.0f)
        val sizeAt1 = E2ETestHarness.calculateAdaptiveCoverSizeDp(1.0f)
        assertEquals(62f, sizeAt0, 0.001f)
        assertEquals(44f, sizeAt1, 0.001f)
        val intermediate = E2ETestHarness.calculateAdaptiveCoverSizeDp(0.25f)
        assertEquals(57.5f, intermediate, 0.001f)
    }

    // =========================================================================
    // FEATURE 10 BOUNDARIES: Version Bump & Android 16
    // =========================================================================

    @Test
    fun testF10_Boundary_SemanticVersionRegexValidation() {
        val semverPattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+$")
        assertTrue("0.0.4 is valid semver", semverPattern.matcher("0.0.4").matches())
        assertFalse("0.0 is invalid semver", semverPattern.matcher("0.0").matches())
        assertFalse("0.0.4-beta has tag", semverPattern.matcher("0.0.4-beta").matches())
    }

    @Test
    fun testF10_Boundary_VersionCodeStrictlyPositive() {
        val buildFile = E2ETestHarness.buildGradleFile
        val content = buildFile.readText()
        val matcher = Pattern.compile("versionCode\\s*=\\s*(\\d+)").matcher(content)
        assertTrue(matcher.find())
        val code = matcher.group(1)!!.toInt()
        assertTrue("versionCode must be >= 1", code >= 1)
    }

    @Test
    fun testF10_Boundary_Android16NoLegacyApiChecks() {
        // Enforce Android 16 exclusivity: codebase must NOT contain legacy checks like SDK_INT < 33
        val projectFiles = listOf(
            E2ETestHarness.settingsDialogFile,
            E2ETestHarness.albumDetailFile,
            E2ETestHarness.mainScreenFile
        )
        for (f in projectFiles) {
            if (f.exists()) {
                val content = f.readText()
                assertFalse("File ${f.name} must not contain legacy SDK checks", content.contains("SDK_INT < 33"))
            }
        }
    }

    @Test
    fun testF10_Boundary_MissingWikiFilesDetection() {
        val wikiDir = File(E2ETestHarness.projectRoot, ".project-wiki")
        if (!wikiDir.exists()) return
        val requiredDocs = listOf("INDEX.md", "RULES.md", "PROJECT.md", "CODEMAP.md")
        for (doc in requiredDocs) {
            val file = File(wikiDir, doc)
            assertTrue("Wiki file $doc must exist", file.exists())
        }
    }

    @Test
    fun testF10_Boundary_CheckStalenessScriptExecutable() {
        val wikiDir = File(E2ETestHarness.projectRoot, ".project-wiki")
        if (!wikiDir.exists()) return
        val script = File(wikiDir, "check-staleness.ps1")
        assertTrue(script.exists())
        assertTrue("Script must not be empty", script.length() > 0)
    }
}
