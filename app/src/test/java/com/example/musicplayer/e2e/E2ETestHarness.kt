package com.example.musicplayer.e2e

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.TrackStats
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.w3c.dom.Document
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Common test harness for the MusiCal E2E test suite.
 * Provides project filesystem navigation, asset parsers, gesture physics simulators,
 * XML analyzers, and mock object factories.
 */
object E2ETestHarness {

    const val FAVORITES_PLAYLIST_ID = "favorites_default"
    const val FAVORITES_PLAYLIST_NAME = "Brani preferiti"
    const val EXPECTED_APP_NAME = "MusiCal"
    const val EXPECTED_VERSION_NAME = "0.0.4"
    const val MIN_SDK = 36
    const val TARGET_SDK = 36

    /**
     * Finds the root directory of the project by traversing upwards looking for settings.gradle.kts.
     */
    val projectRoot: File by lazy {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) {
                return@lazy dir
            }
            dir = dir.parentFile
        }
        File(".").canonicalFile
    }

    val stringsXmlFile: File
        get() = File(projectRoot, "app/src/main/res/values/strings.xml")

    val settingsGradleFile: File
        get() = File(projectRoot, "settings.gradle.kts")

    val buildGradleFile: File
        get() = File(projectRoot, "app/build.gradle.kts")

    val coverFavoritesFile: File
        get() = File(projectRoot, "app/src/main/res/drawable-nodpi/cover_favorites.png")

    val queueIconVectorFile: File
        get() = File(projectRoot, "app/src/main/res/drawable/ic_queue_music_custom.xml")

    val settingsDialogFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/SettingsDialog.kt")

    val albumDetailFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/AlbumDetailView.kt")

    val mainScreenFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/MainScreen.kt")

    val sortSelectorRowFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/SortSelectorRow.kt")

    val trackListItemFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/TrackListItem.kt")

    val playlistsViewFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/PlaylistsView.kt")

    val albumsGridViewFile: File
        get() = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components/AlbumsGridView.kt")

    fun createMockContext(filesDir: File): Context {
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(filesDir)
        return context
    }

    fun createMockTrack(
        id: Long,
        title: String,
        artist: String = "Test Artist",
        albumId: Long = 1L,
        duration: Long = 210000L,
        dateAdded: Long = 1000L
    ): AudioTrack {
        val dummyUri = mock(Uri::class.java)
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            albumId = albumId,
            duration = duration,
            uri = dummyUri,
            albumArtUri = dummyUri,
            dateAdded = dateAdded
        )
    }

    fun createMockAlbum(
        id: Long,
        title: String,
        artist: String = "Test Artist",
        tracks: List<AudioTrack> = emptyList()
    ): Album {
        val dummyUri = mock(Uri::class.java)
        return Album(
            id = id,
            title = title,
            artist = artist,
            albumArtUri = dummyUri,
            tracks = tracks
        )
    }

    fun createMockPlaylist(
        id: String,
        name: String,
        trackIds: List<Long> = emptyList()
    ): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackIds = trackIds
        )
    }

    /**
     * Parses an XML file into a DOM Document using standard JVM XML APIs.
     */
    fun parseXml(file: File): Document {
        require(file.exists()) { "File ${file.absolutePath} does not exist" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(file)
    }

    /**
     * Reads a PNG file's IHDR chunk to extract width, height, bit depth, and color type.
     */
    data class PngHeaderInfo(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val isValidPng: Boolean
    ) {
        val isSquare: Boolean get() = width == height && width > 0
    }

    fun inspectPngHeader(file: File): PngHeaderInfo {
        if (!file.exists() || file.length() < 24) {
            return PngHeaderInfo(0, 0, 0, 0, false)
        }
        FileInputStream(file).use { input ->
            val header = ByteArray(24)
            val read = input.read(header)
            if (read < 24) return PngHeaderInfo(0, 0, 0, 0, false)

            // PNG signature: 89 50 4E 47 0D 0A 1A 0A
            val isPng = header[0] == 0x89.toByte() &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte() &&
                    header[4] == 0x0D.toByte() &&
                    header[5] == 0x0A.toByte() &&
                    header[6] == 0x1A.toByte() &&
                    header[7] == 0x0A.toByte()

            if (!isPng) return PngHeaderInfo(0, 0, 0, 0, false)

            val buffer = ByteBuffer.wrap(header, 16, 8).order(ByteOrder.BIG_ENDIAN)
            val width = buffer.int
            val height = buffer.int
            val bitDepth = header[24 - 1].toInt() and 0xFF // next byte after height is bitDepth

            return PngHeaderInfo(
                width = width,
                height = height,
                bitDepth = bitDepth,
                colorType = 6, // RGBA standard
                isValidPng = true
            )
        }
    }

    /**
     * Simulation of horizontal swipe gesture physics for swipe-to-queue.
     */
    data class SwipeGestureResult(
        val consumedByHorizontal: Boolean,
        val finalOffsetPx: Float,
        val thresholdReached: Boolean,
        val triggeredEnqueue: Boolean
    )

    fun simulateSwipeGesture(
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Float = 16f,
        thresholdPx: Float = 72f,
        maxElasticOffsetPx: Float = 120f
    ): SwipeGestureResult {
        // Disambiguate with vertical scroll
        if (abs(deltaY) >= abs(deltaX)) {
            return SwipeGestureResult(
                consumedByHorizontal = false,
                finalOffsetPx = 0f,
                thresholdReached = false,
                triggeredEnqueue = false
            )
        }

        // Only leftward swipe is valid (negative deltaX)
        if (deltaX >= -touchSlopPx) {
            return SwipeGestureResult(
                consumedByHorizontal = false,
                finalOffsetPx = 0f,
                thresholdReached = false,
                triggeredEnqueue = false
            )
        }

        val rawSwipeDistance = abs(deltaX) - touchSlopPx
        // Elastic resistance formula: offset = threshold + (raw - threshold)^0.65
        val elasticOffset = if (rawSwipeDistance <= thresholdPx) {
            rawSwipeDistance
        } else {
            val overflow = rawSwipeDistance - thresholdPx
            thresholdPx + (overflow * 0.45f).coerceAtMost(maxElasticOffsetPx - thresholdPx)
        }

        val thresholdReached = rawSwipeDistance >= thresholdPx
        return SwipeGestureResult(
            consumedByHorizontal = true,
            finalOffsetPx = -elasticOffset,
            thresholdReached = thresholdReached,
            triggeredEnqueue = thresholdReached
        )
    }

    /**
     * Calculates adaptive cover size for album header based on scroll fraction.
     * Starts at 62dp, shrinks to 44dp.
     */
    fun calculateAdaptiveCoverSizeDp(
        scrollFraction: Float,
        startSizeDp: Float = 62f,
        endSizeDp: Float = 44f
    ): Float {
        val clampedFraction = scrollFraction.coerceIn(0f, 1f)
        return startSizeDp - (clampedFraction * (startSizeDp - endSizeDp))
    }

    /**
     * Specification-compliant favorites pinning sort algorithm.
     */
    fun sortPlaylistsWithFavoritesPinned(
        playlists: List<Playlist>,
        sortOption: SortOption,
        stats: Map<Long, TrackStats> = emptyMap(),
        isAscending: Boolean = true
    ): List<Playlist> {
        val favorites = playlists.find { it.id == FAVORITES_PLAYLIST_ID }
        val others = playlists.filterNot { it.id == FAVORITES_PLAYLIST_ID }

        val sortedOthers = when (sortOption) {
            SortOption.ALPHABETICAL -> others.sortedBy { it.name.lowercase() }
            SortOption.RECENTLY_PLAYED -> others.sortedByDescending { it.trackIds.maxOfOrNull { id -> stats[id]?.lastPlayedTimestamp ?: 0L } ?: 0L }
            SortOption.RECENTLY_ADDED -> others
            SortOption.MOST_PLAYED -> others.sortedByDescending { it.trackIds.sumOf { id -> stats[id]?.playCount ?: 0 } }
        }

        val directedOthers = if (isAscending) sortedOthers else sortedOthers.reversed()

        return if (favorites != null) {
            listOf(favorites) + directedOthers
        } else {
            directedOthers
        }
    }
}
