package com.example.musicplayer.data.tagger.sanitizer

import android.net.Uri
import com.example.musicplayer.data.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class QuerySanitizerTest {

    @Test
    fun `cleanRawString preserves song titles starting with numbers`() {
        assertEquals("21 Guns", QuerySanitizer.cleanRawString("21 Guns"))
        assertEquals("7 Rings", QuerySanitizer.cleanRawString("7 Rings"))
        assertEquals("99 Problems", QuerySanitizer.cleanRawString("99 Problems"))
        assertEquals("505", QuerySanitizer.cleanRawString("505"))
        assertEquals("1979", QuerySanitizer.cleanRawString("1979"))
    }

    @Test
    fun `cleanRawString strips explicit track number prefixes and file extensions`() {
        assertEquals("21 Guns", QuerySanitizer.cleanRawString("01 - 21 Guns.mp3"))
        assertEquals("7 Rings", QuerySanitizer.cleanRawString("04. 7 Rings.flac"))
        assertEquals("Brano", QuerySanitizer.cleanRawString("01 Brano (Official Video).mp3"))
        assertEquals("Brano", QuerySanitizer.cleanRawString("1-05 Brano 320kbps.mp3"))
    }

    @Test
    fun `titlesMatch handles accents and feat suffixes without false positives on short words`() {
        assertTrue(QuerySanitizer.titlesMatch("Perché", "Perche"))
        assertTrue(QuerySanitizer.titlesMatch("Brivido (feat. Guè)", "Brivido"))
        assertTrue(QuerySanitizer.titlesMatch("Bohemian Rhapsody (Remastered 2011)", "Bohemian Rhapsody"))

        // Prevent false positives between distinct songs sharing a short word
        assertFalse(QuerySanitizer.titlesMatch("Love", "Hard To Love"))
        assertFalse(QuerySanitizer.titlesMatch("Blu", "Blu Celeste"))
        assertFalse(QuerySanitizer.titlesMatch("Solo", "Non Sono Solo"))
    }

    @Test
    fun `artistsMatch and hasRicherArtistCredits preserve featuring and collab credits`() {
        assertTrue(QuerySanitizer.artistsMatch("Marracash feat. Mahmood", "Marracash"))
        assertTrue(QuerySanitizer.artistsMatch("Salmo & Noyz Narcos", "Salmo"))
        assertFalse(QuerySanitizer.artistsMatch("Marracash", "Lazza"))

        assertTrue(QuerySanitizer.hasRicherArtistCredits("Marracash feat. Mahmood", "Marracash"))
        assertTrue(QuerySanitizer.hasRicherArtistCredits("Salmo & Noyz Narcos", "Salmo"))
        assertFalse(QuerySanitizer.hasRicherArtistCredits("Marracash", "Marracash"))
    }

    @Test
    fun `isTitleUnknownOrDirty distinguishes clean titles from raw filenames`() {
        assertFalse(QuerySanitizer.isTitleUnknownOrDirty("21 Guns", "01 - 21 Guns.mp3", "Green Day"))
        assertFalse(QuerySanitizer.isTitleUnknownOrDirty("Brivido (feat. Guè)", "Brivido.mp3", "Marracash"))

        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Sconosciuto", "01 - Song.mp3", "Artist"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Track 01", "Track 01.mp3", "Artist"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("01 - Song.mp3", "01 - Song.mp3", "Artist"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Marracash - Brivido", "Marracash - Brivido.mp3", "Marracash"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Marracash – Brivido", "Brivido.mp3", "Marracash"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Tananai - Tango", "Tango (Official Video).mp3", "Artista sconosciuto"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Guè - Brivido", "track01.mp3", "Guè Pequeno"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("The Weeknd - Blinding Lights", "song.mp3", "Weeknd"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Lazza - Cenere", "cenere.mp3", "Lazza - Topic"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Cenere - Lazza", "cenere.mp3", "Lazza"))

        // Titoli autentici con trattino di versione/parte non devono essere scambiati per "Artista - Canzone"
        assertFalse(QuerySanitizer.isTitleUnknownOrDirty("Shine On You Crazy Diamond - Part I", "01 - Shine On You Crazy Diamond - Part I.mp3", "Pink Floyd"))
        assertFalse(QuerySanitizer.isTitleUnknownOrDirty("Hotel California - Acoustic", "Hotel California - Acoustic.mp3", "Artista sconosciuto"))
    }

    @Test
    fun `getEffectiveSearchTerms extracts clean title and artist from Artist - Song title formats`() {
        val mockUri = Mockito.mock(Uri::class.java)

        val unknownArtistTrack = AudioTrack(
            id = 1L,
            title = "Pinguini Tattici Nucleari - Ricordi",
            artist = "Artista sconosciuto",
            duration = 200000L,
            uri = mockUri,
            displayName = "Pinguini Tattici Nucleari - Ricordi (Official Video).mp3"
        )
        assertEquals(
            "Pinguini Tattici Nucleari" to "Ricordi",
            QuerySanitizer.getEffectiveSearchTerms(unknownArtistTrack)
        )

        val enDashTrack = AudioTrack(
            id = 2L,
            title = "Ne-Yo – So Sick",
            artist = "Ne-Yo",
            duration = 207000L,
            uri = mockUri,
            displayName = "01.mp3"
        )
        assertEquals(
            "Ne-Yo" to "So Sick",
            QuerySanitizer.getEffectiveSearchTerms(enDashTrack)
        )

        val topicChannelTrack = AudioTrack(
            id = 3L,
            title = "Lazza - Cenere",
            artist = "Lazza - Topic",
            duration = 208000L,
            uri = mockUri,
            displayName = "cenere.mp3"
        )
        assertEquals(
            "Lazza" to "Cenere",
            QuerySanitizer.getEffectiveSearchTerms(topicChannelTrack)
        )

        val reversedTrack = AudioTrack(
            id = 4L,
            title = "Cenere - Lazza",
            artist = "Lazza",
            duration = 208000L,
            uri = mockUri,
            displayName = "cenere.mp3"
        )
        assertEquals(
            "Lazza" to "Cenere",
            QuerySanitizer.getEffectiveSearchTerms(reversedTrack)
        )

        // Verifica che un titolo con suffisso "- Part I" resti integro
        assertNull(QuerySanitizer.extractFallbackArtistAndTitle("Shine On You Crazy Diamond - Part I"))
    }

    @Test
    fun `extractPrimaryAlbumArtist returns strictly the first artist for collaborations`() {
        assertEquals("Marracash", QuerySanitizer.extractPrimaryAlbumArtist("Marracash feat. Guè"))
        assertEquals("Lazza", QuerySanitizer.extractPrimaryAlbumArtist("Lazza, Sfera Ebbasta"))
        assertEquals("Drake", QuerySanitizer.extractPrimaryAlbumArtist("Drake & 21 Savage"))
        assertEquals("Salmo", QuerySanitizer.extractPrimaryAlbumArtist("Salmo x Noyz Narcos"))
        assertEquals("Fedez", QuerySanitizer.extractPrimaryAlbumArtist("Fedez con J-Ax"))
        assertEquals("Lazza", QuerySanitizer.extractPrimaryAlbumArtist("Lazza (feat. Shiva) - Topic"))
        assertEquals("", QuerySanitizer.extractPrimaryAlbumArtist("Artista sconosciuto"))
    }

    @Test
    fun `protected comma artists like Tyler The Creator remain intact across search and album artist extraction`() {
        assertEquals("Tyler, The Creator", QuerySanitizer.extractPrimaryArtistForSearch("Tyler, The Creator"))
        assertEquals("Tyler, The Creator", QuerySanitizer.extractPrimaryAlbumArtist("Tyler, The Creator"))
        assertEquals("Tyler, The Creator", QuerySanitizer.extractPrimaryAlbumArtist("Tyler, The Creator feat. Kali Uchis"))
        assertEquals("Tyler, The Creator", QuerySanitizer.extractPrimaryAlbumArtist("Tyler, The Creator, Lil Wayne"))
        assertEquals("Tyler, The Creator", QuerySanitizer.extractPrimaryAlbumArtist("Tyler, The Creator & A\$AP Rocky"))
        assertEquals("Earth, Wind & Fire", QuerySanitizer.extractPrimaryAlbumArtist("Earth, Wind & Fire"))

        assertTrue(QuerySanitizer.artistsMatch("Tyler, The Creator", "Tyler, The Creator feat. Playboi Carti"))
        assertTrue(QuerySanitizer.artistsMatch("Kali Uchis feat. Tyler, The Creator", "Tyler, The Creator"))
        assertFalse(QuerySanitizer.artistsMatch("Tyler, The Creator", "The Creator"))
        assertFalse(QuerySanitizer.hasRicherArtistCredits("Tyler, The Creator", "Tyler, The Creator"))
        assertTrue(QuerySanitizer.hasRicherArtistCredits("Tyler, The Creator feat. Kali Uchis", "Tyler, The Creator"))

        // Zero regression on normal comma-separated artist lists (including second artists starting with "The ")
        assertEquals("Lazza", QuerySanitizer.extractPrimaryAlbumArtist("Lazza, Sfera Ebbasta"))
        assertEquals("Future", QuerySanitizer.extractPrimaryAlbumArtist("Future, The Weeknd"))
    }

    @Test
    fun `unreleased noise tags are stripped cleanly from dirty titles and detected as explicit markers`() {
        assertTrue(QuerySanitizer.hasExplicitUnreleasedMarker("Playboi Carti - Molly (Unreleased).mp3"))
        assertTrue(QuerySanitizer.hasExplicitUnreleasedMarker("Cancun [Leak]"))
        assertFalse(QuerySanitizer.hasExplicitUnreleasedMarker("Magnolia"))

        assertEquals("Molly", QuerySanitizer.cleanRawString("01 - Molly (Unreleased).mp3"))
        assertTrue(QuerySanitizer.isTitleUnknownOrDirty("Molly [Unreleased]", "Molly [Unreleased].mp3", "Playboi Carti"))
    }
}
