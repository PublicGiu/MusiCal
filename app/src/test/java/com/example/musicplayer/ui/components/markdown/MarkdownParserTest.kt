package com.example.musicplayer.ui.components.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private val testStyle = MarkdownInlineStyle(
        primaryColor = Color.Blue,
        onSurfaceColor = Color.Black,
        codeBackgroundColor = Color.LightGray,
        codeTextColor = Color.Red
    )

    @Test
    fun parseMarkdown_headers_parsesLevelsCorrectly() {
        val markdown = """
            # Titolo 1
            ## Titolo 2
            ### Titolo 3
            #### Titolo 4
        """.trimIndent()

        val blocks = MarkdownParser.parseMarkdown(markdown)

        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Header(1, "Titolo 1"), blocks[0])
        assertEquals(MarkdownBlock.Header(2, "Titolo 2"), blocks[1])
        assertEquals(MarkdownBlock.Header(3, "Titolo 3"), blocks[2])
        assertEquals(MarkdownBlock.Header(4, "Titolo 4"), blocks[3])
    }

    @Test
    fun parseMarkdown_horizontalRule_parsedCorrectly() {
        val markdown = """
            Paragrafo prima
            ---
            Paragrafo dopo
        """.trimIndent()

        val blocks = MarkdownParser.parseMarkdown(markdown)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertEquals(MarkdownBlock.HorizontalRule, blocks[1])
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    @Test
    fun parseMarkdown_bulletAndOrderedLists_parsedWithIndentation() {
        val markdown = """
            - Primo punto
            * Secondo punto
              - Sotto punto indentato
            1. Passo uno
            2. Passo due
        """.trimIndent()

        val blocks = MarkdownParser.parseMarkdown(markdown)

        assertEquals(5, blocks.size)
        assertEquals(MarkdownBlock.BulletItem(0, "Primo punto"), blocks[0])
        assertEquals(MarkdownBlock.BulletItem(0, "Secondo punto"), blocks[1])
        assertEquals(MarkdownBlock.BulletItem(1, "Sotto punto indentato"), blocks[2])
        assertEquals(MarkdownBlock.OrderedItem("1", "Passo uno"), blocks[3])
        assertEquals(MarkdownBlock.OrderedItem("2", "Passo due"), blocks[4])
    }

    @Test
    fun parseMarkdown_codeBlock_capturesLanguageAndMultilineCode() {
        val markdown = """
            ```kotlin
            val a = 1
            val b = 2
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parseMarkdown(markdown)

        assertEquals(1, blocks.size)
        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("val a = 1\nval b = 2", codeBlock.code)
    }

    @Test
    fun parseMarkdown_blockquote_parsedCorrectly() {
        val markdown = "> Questa è una citazione importante"
        val blocks = MarkdownParser.parseMarkdown(markdown)

        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Blockquote("Questa è una citazione importante"), blocks[0])
    }

    @Test
    fun parseMarkdown_realReleaseNotesFromUserScreenshot_parsesAccurately() {
        val releaseNotes = """
            ## 🚀 Novità di MusiCal v0.1.14

            Questa release è dedicata all'ottimizzazione architetturale, alla robustezza della compilazione release (R8 / ProGuard), alla sicurezza del codice e all'armonizzazione dei client API e delle risorse dell'interfaccia utente:

            ### 🏗️ 1. Decomposizione Modulare dell'Architettura (Coordinatori Specializzati)
            Il ViewModel principale (`MusicViewModel`) è stato decongestionato e rifattorizzato
        """.trimIndent()

        val blocks = MarkdownParser.parseMarkdown(releaseNotes)

        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Header(2, "🚀 Novità di MusiCal v0.1.14"), blocks[0])
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertEquals(
            MarkdownBlock.Header(3, "🏗️ 1. Decomposizione Modulare dell'Architettura (Coordinatori Specializzati)"),
            blocks[2]
        )
        val lastParagraph = blocks[3] as MarkdownBlock.Paragraph
        assertEquals("Il ViewModel principale (`MusicViewModel`) è stato decongestionato e rifattorizzato", lastParagraph.content)
    }

    @Test
    fun parseInlineMarkdown_replacesTagsWithProperSpans() {
        val text = "Test **grassetto**, *corsivo*, `codice` e ~~barrato~~"
        val annotated = MarkdownParser.parseInlineMarkdown(text, testStyle)

        assertEquals("Test grassetto, corsivo, codice e barrato", annotated.text)
        assertFalse(annotated.text.contains("**"))
        assertFalse(annotated.text.contains("`"))
        assertFalse(annotated.text.contains("~~"))
        assertEquals(4, annotated.spanStyles.size)
    }

    @Test
    fun parseInlineMarkdown_links_extractedAndFormatted() {
        val text = "Consulta il repository su [GitHub](https://github.com/project) per i dettagli."
        val annotated = MarkdownParser.parseInlineMarkdown(text, testStyle)

        assertEquals("Consulta il repository su GitHub per i dettagli.", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun parseInlineMarkdown_emptyAndPlain_handledGracefully() {
        val empty = MarkdownParser.parseInlineMarkdown("", testStyle)
        assertEquals("", empty.text)

        val plain = MarkdownParser.parseInlineMarkdown("Solo testo normale", testStyle)
        assertEquals("Solo testo normale", plain.text)
        assertTrue(plain.spanStyles.isEmpty())
    }
}
