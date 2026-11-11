package com.example.musicplayer.ui

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.sortTracks
import com.example.musicplayer.ui.components.extractAlphabeticalHeader
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import kotlin.math.roundToInt

class FastScrollbarTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun track(id: Long, title: String): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Artist",
            duration = 180000L,
            uri = dummyUri
        )
    }

    @Test
    fun testExtractAlphabeticalHeader_standardLetters() {
        assertEquals("A", extractAlphabeticalHeader("Apple"))
        assertEquals("B", extractAlphabeticalHeader("banana"))
        assertEquals("Z", extractAlphabeticalHeader("zebra"))
        assertEquals("M", extractAlphabeticalHeader("Master of Puppets"))
    }

    @Test
    fun testExtractAlphabeticalHeader_accentedLetters() {
        assertEquals("E", extractAlphabeticalHeader("Édith Piaf"))
        assertEquals("A", extractAlphabeticalHeader("à la folie"))
        assertEquals("U", extractAlphabeticalHeader("Übermensch"))
        assertEquals("O", extractAlphabeticalHeader("Öctober"))
        assertEquals("I", extractAlphabeticalHeader("ìtaca"))
    }

    @Test
    fun testExtractAlphabeticalHeader_leadingQuotesAndPunctuation() {
        assertEquals("H", extractAlphabeticalHeader("\"Heroes\""))
        assertEquals("D", extractAlphabeticalHeader("(Don't Fear) The Reaper"))
        assertEquals("N", extractAlphabeticalHeader("«Nel blu dipinto di blu»"))
        assertEquals("R", extractAlphabeticalHeader("'Round Midnight"))
        assertEquals("T", extractAlphabeticalHeader("[The Dark Side of the Moon]"))
    }

    @Test
    fun testExtractAlphabeticalHeader_numbersAndSymbols() {
        assertEquals("#", extractAlphabeticalHeader("1984"))
        assertEquals("#", extractAlphabeticalHeader("007 Sound"))
        assertEquals("#", extractAlphabeticalHeader("21 Guns"))
        assertEquals("#", extractAlphabeticalHeader("$$$ Cash"))
        assertEquals("#", extractAlphabeticalHeader("!!! Check"))
        assertEquals("#", extractAlphabeticalHeader("--- Dash"))
    }

    @Test
    fun testExtractAlphabeticalHeader_emptyOrWhitespace() {
        assertEquals("#", extractAlphabeticalHeader(""))
        assertEquals("#", extractAlphabeticalHeader("   "))
        assertEquals("#", extractAlphabeticalHeader("\t\n"))
    }

    @Test
    fun testAlphabeticalSortingWithHeaders_ascendingAndDescending() {
        val tracks = listOf(
            track(1, "Zebra"),
            track(2, "Apple"),
            track(3, "Banana"),
            track(4, "1984")
        )

        // A-Z
        val asc = tracks.sortTracks(SortOption.ALPHABETICAL, isAscending = true)
        val ascHeaders = asc.map { extractAlphabeticalHeader(it.title) }
        assertEquals("#", ascHeaders[0]) // 1984
        assertEquals("A", ascHeaders[1]) // Apple
        assertEquals("B", ascHeaders[2]) // Banana
        assertEquals("Z", ascHeaders[3]) // Zebra

        // Z-A
        val desc = tracks.sortTracks(SortOption.ALPHABETICAL, isAscending = false)
        val descHeaders = desc.map { extractAlphabeticalHeader(it.title) }
        assertEquals("Z", descHeaders[0]) // Zebra
        assertEquals("B", descHeaders[1]) // Banana
        assertEquals("A", descHeaders[2]) // Apple
        assertEquals("#", descHeaders[3]) // 1984
    }

    @Test
    fun testFastScrollProgressToIndexMapping() {
        val totalItems = 100

        fun progressToIndex(progress: Float, items: Int): Int {
            if (items <= 1) return 0
            return (progress * (items - 1)).roundToInt().coerceIn(0, items - 1)
        }

        assertEquals(0, progressToIndex(0.0f, totalItems))
        assertEquals(49, progressToIndex(0.495f, totalItems))
        assertEquals(50, progressToIndex(0.5f, totalItems))
        assertEquals(99, progressToIndex(1.0f, totalItems))
        assertEquals(0, progressToIndex(-0.2f, totalItems))
        assertEquals(99, progressToIndex(1.5f, totalItems))

        // Boundary: 1 item
        assertEquals(0, progressToIndex(0.5f, 1))
        assertEquals(0, progressToIndex(1.0f, 1))

        // Boundary: 0 items
        assertEquals(0, progressToIndex(0.5f, 0))
    }

    @Test
    fun testThumbHitTestingLogic() {
        val currentThumbOffsetPx = 200f
        val thumbHeightPx = 80f
        val thumbMarginPx = 24f // ~12dp in px

        val thumbTop = currentThumbOffsetPx - thumbMarginPx
        val thumbBottom = currentThumbOffsetPx + thumbHeightPx + thumbMarginPx

        fun isTouchOnThumb(touchY: Float): Boolean {
            return touchY in thumbTop..thumbBottom
        }

        // Tocco esattamente al centro del pollice
        org.junit.Assert.assertTrue(isTouchOnThumb(240f))

        // Tocco all'inizio e alla fine del pollice
        org.junit.Assert.assertTrue(isTouchOnThumb(200f))
        org.junit.Assert.assertTrue(isTouchOnThumb(280f))

        // Tocco entro la tolleranza (margin)
        org.junit.Assert.assertTrue(isTouchOnThumb(180f))
        org.junit.Assert.assertTrue(isTouchOnThumb(300f))

        // Tocco FUORI dal pollice (es. icona dei tre puntini o freccia a 50px o 500px)
        org.junit.Assert.assertFalse(isTouchOnThumb(50f))
        org.junit.Assert.assertFalse(isTouchOnThumb(150f))
        org.junit.Assert.assertFalse(isTouchOnThumb(350f))
        org.junit.Assert.assertFalse(isTouchOnThumb(600f))
    }

    @Test
    fun testFastScrollbarInteractivityCondition() {
        fun isInteractive(isVisible: Boolean, thumbHeightPx: Float, totalItems: Int): Boolean {
            return isVisible && thumbHeightPx > 0f && totalItems > 1
        }

        // Quando la barra è scomparsa (inattività) -> MAI interattiva
        org.junit.Assert.assertFalse(isInteractive(isVisible = false, thumbHeightPx = 80f, totalItems = 50))

        // Quando la barra è visibile e ci sono molti elementi -> Interattiva
        org.junit.Assert.assertTrue(isInteractive(isVisible = true, thumbHeightPx = 80f, totalItems = 50))

        // Quando la lista sta interamente nello schermo (thumbHeightPx <= 0f) -> NON interattiva
        org.junit.Assert.assertFalse(isInteractive(isVisible = true, thumbHeightPx = 0f, totalItems = 5))

        // Con 1 solo elemento -> NON interattiva
        org.junit.Assert.assertFalse(isInteractive(isVisible = true, thumbHeightPx = 80f, totalItems = 1))
    }
}
