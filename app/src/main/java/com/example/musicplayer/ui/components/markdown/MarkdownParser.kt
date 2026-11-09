package com.example.musicplayer.ui.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

/**
 * Parametri di colore per la formattazione inline del testo Markdown.
 */
data class MarkdownInlineStyle(
    val primaryColor: Color,
    val onSurfaceColor: Color,
    val codeBackgroundColor: Color,
    val codeTextColor: Color
)

object MarkdownParser {

    private val HR_REGEX = Regex("^(?:---|\\*\\*\\*|___)\\s*$")
    private val HEADER_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val BULLET_REGEX = Regex("^(\\s*)([*+-])\\s+(.+)$")
    private val ORDERED_REGEX = Regex("^(\\s*)(\\d+)[.)]\\s+(.+)$")
    private val BLOCKQUOTE_REGEX = Regex("^>\\s*(.*)$")

    private val TOKEN_PATTERN = Regex(
        "(`[^`\n]+`)" +
        "|(\\[[^\\]]+\\]\\([^)]+\\))" +
        "|(\\*{3}[^*\\n]+\\*{3})" +
        "|(\\*{2}[^*\\n]+\\*{2})" +
        "|(__[^_\\n]+__)" +
        "|(\\*[^*\\n]+\\*)" +
        "|((?<=^|[\\s(])_[^_\\n]+_(?=$|[\\s).,!?;:]))" +
        "|(~~[^~\\n]+~~)"
    )

    /**
     * Esegue il parsing del testo grezzo Markdown in una sequenza strutturata di [MarkdownBlock].
     */
    fun parseMarkdown(text: String): List<MarkdownBlock> {
        val lines = text.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeBlock = false
        var codeLanguage: String? = null
        val codeBuffer = mutableListOf<String>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val combined = paragraphBuffer.joinToString(" ").trim()
                if (combined.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(combined))
                }
                paragraphBuffer.clear()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()

            if (inCodeBlock) {
                if (line.trim() == "```") {
                    inCodeBlock = false
                    blocks.add(
                        MarkdownBlock.CodeBlock(
                            language = codeLanguage,
                            code = codeBuffer.joinToString("\n")
                        )
                    )
                    codeBuffer.clear()
                    codeLanguage = null
                } else {
                    codeBuffer.add(rawLine)
                }
                continue
            }

            if (line.trim().startsWith("```")) {
                flushParagraph()
                inCodeBlock = true
                val lang = line.trim().removePrefix("```").trim()
                codeLanguage = lang.ifEmpty { null }
                codeBuffer.clear()
                continue
            }

            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }

            val headerMatch = HEADER_REGEX.matchEntire(trimmed)
            if (headerMatch != null) {
                flushParagraph()
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Header(level, content))
                continue
            }

            if (HR_REGEX.matches(trimmed)) {
                flushParagraph()
                blocks.add(MarkdownBlock.HorizontalRule)
                continue
            }

            val quoteMatch = BLOCKQUOTE_REGEX.matchEntire(trimmed)
            if (quoteMatch != null) {
                flushParagraph()
                blocks.add(MarkdownBlock.Blockquote(quoteMatch.groupValues[1].trim()))
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                val indentSpaces = bulletMatch.groupValues[1].length
                val indentLevel = (indentSpaces / 2).coerceIn(0, 4)
                val content = bulletMatch.groupValues[3].trim()
                blocks.add(MarkdownBlock.BulletItem(indentLevel, content))
                continue
            }

            val orderedMatch = ORDERED_REGEX.matchEntire(line)
            if (orderedMatch != null) {
                flushParagraph()
                val number = orderedMatch.groupValues[2]
                val content = orderedMatch.groupValues[3].trim()
                blocks.add(MarkdownBlock.OrderedItem(number, content))
                continue
            }

            // Linea di testo normale: accumula nel paragrafo
            paragraphBuffer.add(trimmed)
        }

        flushParagraph()

        // Se un blocco di codice non è stato chiuso, salvalo comunque
        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(
                MarkdownBlock.CodeBlock(
                    language = codeLanguage,
                    code = codeBuffer.joinToString("\n")
                )
            )
        }

        return blocks
    }

    /**
     * Converte una porzione di testo con formattazione inline Markdown in un [AnnotatedString]
     * compatibile con Jetpack Compose Material 3.
     */
    fun parseInlineMarkdown(text: String, style: MarkdownInlineStyle): AnnotatedString {
        return buildAnnotatedString {
            var lastIndex = 0

            for (match in TOKEN_PATTERN.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }

                val value = match.value

                when {
                    value.startsWith("`") && value.endsWith("`") && value.length >= 2 -> {
                        val code = value.removeSurrounding("`")
                        val spanStart = length
                        append(code)
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = style.codeBackgroundColor,
                                color = style.codeTextColor,
                                fontSize = 0.9.em
                            ),
                            spanStart,
                            length
                        )
                    }

                    value.startsWith("[") && value.contains("](") && value.endsWith(")") -> {
                        val label = value.substringAfter("[").substringBefore("](")
                        val url = value.substringAfter("](").removeSuffix(")")
                        val spanStart = length
                        append(label)
                        addStyle(
                            SpanStyle(
                                color = style.primaryColor,
                                textDecoration = TextDecoration.Underline
                            ),
                            spanStart,
                            length
                        )
                        try {
                            addLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = style.primaryColor,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                ),
                                spanStart,
                                length
                            )
                        } catch (_: Throwable) {
                            // Fallback per ambienti test o versioni prive di LinkAnnotation
                        }
                    }

                    value.startsWith("***") && value.endsWith("***") && value.length >= 6 -> {
                        val inner = value.removeSurrounding("***")
                        val spanStart = length
                        append(inner)
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            spanStart,
                            length
                        )
                    }

                    (value.startsWith("**") && value.endsWith("**") && value.length >= 4) ||
                    (value.startsWith("__") && value.endsWith("__") && value.length >= 4) -> {
                        val inner = if (value.startsWith("**")) value.removeSurrounding("**") else value.removeSurrounding("__")
                        val spanStart = length
                        append(inner)
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            spanStart,
                            length
                        )
                    }

                    (value.startsWith("*") && value.endsWith("*") && value.length >= 2) ||
                    (value.startsWith("_") && value.endsWith("_") && value.length >= 2) -> {
                        val inner = if (value.startsWith("*")) value.removeSurrounding("*") else value.removeSurrounding("_")
                        val spanStart = length
                        append(inner)
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            spanStart,
                            length
                        )
                    }

                    value.startsWith("~~") && value.endsWith("~~") && value.length >= 4 -> {
                        val inner = value.removeSurrounding("~~")
                        val spanStart = length
                        append(inner)
                        addStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                            spanStart,
                            length
                        )
                    }

                    else -> {
                        append(value)
                    }
                }

                lastIndex = end
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}
