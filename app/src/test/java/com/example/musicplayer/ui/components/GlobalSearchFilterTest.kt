package com.example.musicplayer.ui.components

import com.example.musicplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchFilterTest {

    @Test
    fun searchFilterOrderMatchesUserRequirement() {
        val expectedOrder = listOf(
            SearchFilter.ALL,
            SearchFilter.TRACKS,
            SearchFilter.ARTIST,
            SearchFilter.ALBUM,
            SearchFilter.PLAYLIST
        )
        assertEquals(expectedOrder, SearchFilter.entries)
        assertEquals(SearchFilter.ALL, SearchFilter.entries.first())
    }

    @Test
    fun searchFilterLabelsMapToExpectedStringResources() {
        assertEquals(R.string.search_filter_all, SearchFilter.ALL.labelResId)
        assertEquals(R.string.search_filter_tracks, SearchFilter.TRACKS.labelResId)
        assertEquals(R.string.search_filter_artist, SearchFilter.ARTIST.labelResId)
        assertEquals(R.string.search_filter_album, SearchFilter.ALBUM.labelResId)
        assertEquals(R.string.search_filter_playlist, SearchFilter.PLAYLIST.labelResId)
    }
}
