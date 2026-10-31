package com.example.musicplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class SortOptionTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun track(id: Long, title: String, dateAdded: Long = 0L): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Artist",
            duration = 180000L,
            uri = dummyUri,
            dateAdded = dateAdded
        )
    }

    private fun album(id: Long, title: String, tracks: List<AudioTrack>): Album {
        return Album(
            id = id,
            title = title,
            artist = "Artist",
            albumArtUri = dummyUri,
            tracks = tracks
        )
    }

    private fun playlist(id: String, name: String, trackIds: List<Long>): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackIds = trackIds
        )
    }

    @Test
    fun testAlphabeticalSortingTracks() {
        val tracks = listOf(
            track(1, "zebra"),
            track(2, "Apple"),
            track(3, "banana")
        )

        val asc = tracks.sortTracks(SortOption.ALPHABETICAL, isAscending = true)
        assertEquals("Apple", asc[0].title)
        assertEquals("banana", asc[1].title)
        assertEquals("zebra", asc[2].title)

        val desc = tracks.sortTracks(SortOption.ALPHABETICAL, isAscending = false)
        assertEquals("zebra", desc[0].title)
        assertEquals("banana", desc[1].title)
        assertEquals("Apple", desc[2].title)
    }

    @Test
    fun testRecentlyAddedSortingTracks() {
        val tracks = listOf(
            track(1, "Old", dateAdded = 1000L),
            track(2, "Newest", dateAdded = 3000L),
            track(3, "Medium", dateAdded = 2000L)
        )

        val asc = tracks.sortTracks(SortOption.RECENTLY_ADDED, isAscending = true)
        assertEquals("Newest", asc[0].title)
        assertEquals("Medium", asc[1].title)
        assertEquals("Old", asc[2].title)

        val desc = tracks.sortTracks(SortOption.RECENTLY_ADDED, isAscending = false)
        assertEquals("Old", desc[0].title)
        assertEquals("Medium", desc[1].title)
        assertEquals("Newest", desc[2].title)
    }

    @Test
    fun testMostPlayedSortingTracks() {
        val tracks = listOf(
            track(1, "Least Played"),
            track(2, "Most Played"),
            track(3, "Average Played")
        )

        val stats = mapOf(
            1L to TrackStats(1L, playCount = 2, lastPlayedTimestamp = 100L),
            2L to TrackStats(2L, playCount = 50, lastPlayedTimestamp = 200L),
            3L to TrackStats(3L, playCount = 15, lastPlayedTimestamp = 300L)
        )

        val asc = tracks.sortTracks(SortOption.MOST_PLAYED, stats = stats, isAscending = true)
        assertEquals("Most Played", asc[0].title)
        assertEquals("Average Played", asc[1].title)
        assertEquals("Least Played", asc[2].title)
    }

    @Test
    fun testRecentlyPlayedSortingTracks() {
        val tracks = listOf(
            track(1, "Played long ago"),
            track(2, "Played just now"),
            track(3, "Never played")
        )

        val stats = mapOf(
            1L to TrackStats(1L, playCount = 1, lastPlayedTimestamp = 1000L),
            2L to TrackStats(2L, playCount = 1, lastPlayedTimestamp = 5000L)
        )

        val asc = tracks.sortTracks(SortOption.RECENTLY_PLAYED, stats = stats, isAscending = true)
        assertEquals("Played just now", asc[0].title)
        assertEquals("Played long ago", asc[1].title)
        assertEquals("Never played", asc[2].title)
    }

    @Test
    fun testSortAlbums() {
        val t1 = track(1, "Song A", dateAdded = 1000L)
        val t2 = track(2, "Song B", dateAdded = 5000L)
        val albumZ = album(10L, "Z Album", listOf(t1))
        val albumA = album(20L, "A Album", listOf(t2))

        val sortedAsc = listOf(albumZ, albumA).sortAlbums(SortOption.ALPHABETICAL, isAscending = true)
        assertEquals("A Album", sortedAsc[0].title)
        assertEquals("Z Album", sortedAsc[1].title)

        val stats = mapOf(
            1L to TrackStats(1L, playCount = 100),
            2L to TrackStats(2L, playCount = 10)
        )
        val sortedByPlays = listOf(albumZ, albumA).sortAlbums(SortOption.MOST_PLAYED, stats = stats, isAscending = true)
        assertEquals("Z Album", sortedByPlays[0].title)
        assertEquals("A Album", sortedByPlays[1].title)
    }

    @Test
    fun testSortPlaylists() {
        val p1 = playlist("1", "Rock", listOf(1L))
        val p2 = playlist("2", "Acoustic", listOf(2L))

        val sortedAsc = listOf(p1, p2).sortPlaylists(SortOption.ALPHABETICAL, isAscending = true)
        assertEquals("Acoustic", sortedAsc[0].name)
        assertEquals("Rock", sortedAsc[1].name)
    }

    @Test
    fun testSortPlaylistsWithFavoritesPinnedAlphabetical() {
        val fav = playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME, listOf(1L))
        val pRock = playlist("1", "Rock", listOf(2L))
        val pPop = playlist("2", "Pop", listOf(3L))
        val pAcoustic = playlist("3", "Acoustic", listOf(4L))
        val playlists = listOf(pRock, pPop, fav, pAcoustic)

        val asc = playlists.sortPlaylists(SortOption.ALPHABETICAL, isAscending = true)
        assertEquals(FAVORITES_PLAYLIST_ID, asc[0].id)
        assertEquals("Acoustic", asc[1].name)
        assertEquals("Pop", asc[2].name)
        assertEquals("Rock", asc[3].name)

        val desc = playlists.sortPlaylists(SortOption.ALPHABETICAL, isAscending = false)
        assertEquals(FAVORITES_PLAYLIST_ID, desc[0].id)
        assertEquals("Rock", desc[1].name)
        assertEquals("Pop", desc[2].name)
        assertEquals("Acoustic", desc[3].name)
    }

    @Test
    fun testSortPlaylistsWithFavoritesPinnedAllSortOptions() {
        val fav = playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME, listOf(1L))
        val p1 = playlist("1", "Rock", listOf(2L))
        val p2 = playlist("2", "Acoustic", listOf(3L))
        val playlists = listOf(p1, p2, fav)

        val stats = mapOf(
            1L to TrackStats(1L, playCount = 5, lastPlayedTimestamp = 100L),
            2L to TrackStats(2L, playCount = 20, lastPlayedTimestamp = 500L),
            3L to TrackStats(3L, playCount = 10, lastPlayedTimestamp = 300L)
        )

        for (option in SortOption.entries) {
            for (direction in listOf(true, false)) {
                val sorted = playlists.sortPlaylists(option, stats = stats, isAscending = direction)
                assertEquals("Favorites must be pinned at index 0 for $option (ascending=$direction)",
                    FAVORITES_PLAYLIST_ID, sorted[0].id)
                assertEquals(3, sorted.size)
            }
        }
    }

    @Test
    fun testPlaylistIsFavoritesProperty() {
        val fav = Playlist(id = FAVORITES_PLAYLIST_ID, name = FAVORITES_PLAYLIST_NAME)
        val custom = Playlist(id = "custom_id", name = "My Playlist")

        org.junit.Assert.assertTrue(fav.isFavorites)
        org.junit.Assert.assertFalse(custom.isFavorites)
        assertEquals("favorites_default", FAVORITES_PLAYLIST_ID)
        assertEquals("Brani preferiti", FAVORITES_PLAYLIST_NAME)
    }

    @Test
    fun testSortOptionOrder() {
        val expectedOrder = listOf(
            SortOption.RECENTLY_PLAYED,
            SortOption.MOST_PLAYED,
            SortOption.RECENTLY_ADDED,
            SortOption.ALPHABETICAL
        )
        assertEquals(expectedOrder, SortOption.entries)
    }

    @Test
    fun testSortOptionLabels() {
        assertEquals("Ascoltati di recente", SortOption.RECENTLY_PLAYED.label)
        assertEquals("Più ascoltati", SortOption.MOST_PLAYED.label)
        assertEquals("Aggiunti di recente", SortOption.RECENTLY_ADDED.label)
        assertEquals("Alfabetico (A-Z)", SortOption.ALPHABETICAL.label)

        // Verifica etichette dinamiche crescente vs decrescente
        assertEquals("Alfabetico (A-Z)", SortOption.ALPHABETICAL.getLabel(isAscending = true))
        assertEquals("Alfabetico (Z-A)", SortOption.ALPHABETICAL.getLabel(isAscending = false))
        assertEquals("Ascoltati di recente", SortOption.RECENTLY_PLAYED.getLabel(isAscending = false))
        assertEquals("Più ascoltati", SortOption.MOST_PLAYED.getLabel(isAscending = false))
        assertEquals("Aggiunti di recente", SortOption.RECENTLY_ADDED.getLabel(isAscending = false))
    }
}
