package com.example.musicplayer.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesParserTest {

    @Test
    fun parse_withHtmlCommentTags_extractsBothLanguages() {
        val markdown = """
            <!-- it -->
            ## Novità v0.2.1
            - Corretto bug nel lettore
            - Supporto multilingua
            <!-- /it -->

            <!-- en -->
            ## What's New v0.2.1
            - Fixed player bug
            - Multilingual support
            <!-- /en -->
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertTrue(parsed.hasBothLanguages)
        assertNotNull(parsed.italianNotes)
        assertNotNull(parsed.englishNotes)
        assertTrue(parsed.italianNotes!!.contains("Corretto bug nel lettore"))
        assertTrue(parsed.englishNotes!!.contains("Fixed player bug"))
        assertEquals(parsed.italianNotes, parsed.getNotes(isEnglish = false))
        assertEquals(parsed.englishNotes, parsed.getNotes(isEnglish = true))
    }

    @Test
    fun parse_withMarkdownHeaders_emojiAndLanguageNames_extractsBoth() {
        val markdown = """
            # 🚀 MusiCal v0.2.1

            ## 🇬🇧 English
            ### What's New
            - Seamless language switching
            - Fixed 1-second black screen

            ---

            ## 🇮🇹 Italiano
            ### Novità
            - Cambio lingua immediato
            - Risolta la schermata nera di un secondo
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertTrue(parsed.hasBothLanguages)
        assertNotNull(parsed.italianNotes)
        assertNotNull(parsed.englishNotes)
        assertTrue(parsed.englishNotes!!.contains("Seamless language switching"))
        assertFalse(parsed.englishNotes!!.contains("## 🇬🇧 English"))
        assertTrue(parsed.italianNotes!!.contains("Cambio lingua immediato"))
        assertFalse(parsed.italianNotes!!.contains("## 🇮🇹 Italiano"))
    }

    @Test
    fun parse_withSimpleHeaders_caseInsensitive() {
        val markdown = """
            ## english
            - New feature A
            - Bugfix B

            ## italiano
            - Nuova funzionalità A
            - Correzione bug B
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertTrue(parsed.hasBothLanguages)
        assertEquals("- New feature A\n- Bugfix B", parsed.englishNotes)
        assertEquals("- Nuova funzionalità A\n- Correzione bug B", parsed.italianNotes)
    }

    @Test
    fun parse_withBracketMarkers() {
        val markdown = """
            [IT]
            ### Novità
            - Aggiornata interfaccia

            [EN]
            ### What's New
            - Updated UI
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertTrue(parsed.hasBothLanguages)
        assertTrue(parsed.italianNotes!!.contains("Aggiornata interfaccia"))
        assertTrue(parsed.englishNotes!!.contains("Updated UI"))
    }

    @Test
    fun parse_monolingualItalian_detectedCorrectly() {
        val markdown = """
            ## Novità di questa versione
            - Corretto un bug critico nella coda di riproduzione
            - Risolto problema con i brani preferiti
            - Miglioramenti alle prestazioni
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertFalse(parsed.hasBothLanguages)
        assertNotNull(parsed.italianNotes)
        assertNull(parsed.englishNotes)
        assertEquals(markdown.trim(), parsed.italianNotes)
        // If English is requested but not available, it gracefully returns Italian
        assertEquals(markdown.trim(), parsed.getNotes(isEnglish = true))
    }

    @Test
    fun parse_monolingualEnglish_detectedCorrectly() {
        val markdown = """
            ## What's New
            - Fixed playback issue when disconnecting Bluetooth
            - Added playlist cover customization
            - UI improvements
        """.trimIndent()

        val parsed = ReleaseNotesParser.parse(markdown)

        assertFalse(parsed.hasBothLanguages)
        assertNull(parsed.italianNotes)
        assertNotNull(parsed.englishNotes)
        assertEquals(markdown.trim(), parsed.englishNotes)
        // If Italian is requested but not available, it gracefully returns English
        assertEquals(markdown.trim(), parsed.getNotes(isEnglish = false))
    }

    @Test
    fun parse_emptyOrBlank_returnsEmpty() {
        val empty = ReleaseNotesParser.parse("")
        assertFalse(empty.hasBothLanguages)
        assertNull(empty.italianNotes)
        assertNull(empty.englishNotes)
        assertEquals("", empty.raw)

        val blank = ReleaseNotesParser.parse("   \n\t  ")
        assertFalse(blank.hasBothLanguages)
        assertNull(blank.italianNotes)
        assertNull(blank.englishNotes)
    }

    @Test
    fun appUpdateInfo_getReleaseNotesForLanguage() {
        val info = AppUpdateInfo(
            latestVersion = "0.2.1",
            releaseName = "v0.2.1",
            releaseNotes = "Raw notes",
            releaseNotesIt = "Note italiane",
            releaseNotesEn = "English notes",
            htmlUrl = "https://github.com/PublicGiu/MusiCal",
            apkDownloadUrl = null,
            isUpdateAvailable = true
        )

        assertTrue(info.hasBothLanguages)
        assertEquals("English notes", info.getReleaseNotesForLanguage(isEnglish = true))
        assertEquals("Note italiane", info.getReleaseNotesForLanguage(isEnglish = false))
    }
}
