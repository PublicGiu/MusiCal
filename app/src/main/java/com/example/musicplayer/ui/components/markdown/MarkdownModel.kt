package com.example.musicplayer.ui.components.markdown

/**
 * Rappresenta un blocco semantico di un documento Markdown.
 */
sealed interface MarkdownBlock {
    /**
     * Intestazione (da H1 a H6).
     */
    data class Header(val level: Int, val content: String) : MarkdownBlock

    /**
     * Paragrafo di testo normale con supporto a formattazione inline.
     */
    data class Paragraph(val content: String) : MarkdownBlock

    /**
     * Elemento di un elenco puntato (- o * o +) con livello di indentazione.
     */
    data class BulletItem(val indentLevel: Int, val content: String) : MarkdownBlock

    /**
     * Elemento di un elenco numerato (es. "1.", "2.").
     */
    data class OrderedItem(val number: String, val content: String) : MarkdownBlock

    /**
     * Blocco di codice delimitato da triple backtick.
     */
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock

    /**
     * Citazione a margine (>).
     */
    data class Blockquote(val content: String) : MarkdownBlock

    /**
     * Separatore orizzontale (--- o ***).
     */
    data object HorizontalRule : MarkdownBlock
}
