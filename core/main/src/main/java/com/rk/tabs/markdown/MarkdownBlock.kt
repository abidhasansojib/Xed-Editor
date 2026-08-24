package com.rk.tabs.markdown

import androidx.compose.ui.text.style.TextAlign

/**
 * Supported GitHub-style alert callout types.
 */
enum class AlertType(val label: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution");

    companion object {
        fun fromTag(tag: String): AlertType? {
            return when (tag.uppercase()) {
                "NOTE" -> NOTE
                "TIP" -> TIP
                "IMPORTANT" -> IMPORTANT
                "WARNING" -> WARNING
                "CAUTION" -> CAUTION
                else -> null
            }
        }
    }
}

/**
 * Structured Markdown AST blocks for modern Jetpack Compose rendering.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class Alert(val type: AlertType, val title: String, val content: List<MarkdownBlock>) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TextAlign>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
    data class ListItem(val ordered: Boolean, val index: Int, val text: String, val depth: Int) : MarkdownBlock
    data class TaskItem(val isChecked: Boolean, val text: String) : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
}
