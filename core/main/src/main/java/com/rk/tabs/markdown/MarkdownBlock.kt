package com.rk.tabs.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * Obsidian & GitHub callout types with matching color palettes and labels.
 */
enum class AlertType(val label: String, val defaultTitle: String) {
    NOTE("Note", "Note"),
    INFO("Info", "Info"),
    TODO("Todo", "Todo"),
    TIP("Tip", "Tip"),
    HINT("Hint", "Hint"),
    IMPORTANT("Important", "Important"),
    SUCCESS("Success", "Success"),
    CHECK("Check", "Done"),
    DONE("Done", "Done"),
    QUESTION("Question", "Question"),
    HELP("Help", "Help"),
    FAQ("FAQ", "FAQ"),
    WARNING("Warning", "Warning"),
    CAUTION("Caution", "Caution"),
    ATTENTION("Attention", "Attention"),
    FAILURE("Failure", "Failure"),
    FAIL("Fail", "Fail"),
    MISSING("Missing", "Missing"),
    DANGER("Danger", "Danger"),
    ERROR("Error", "Error"),
    BUG("Bug", "Bug"),
    EXAMPLE("Example", "Example"),
    QUOTE("Quote", "Quote"),
    CITE("Cite", "Cite");

    companion object {
        fun fromTag(rawTag: String): AlertType {
            val tag = rawTag.trim().uppercase()
            return when (tag) {
                "NOTE" -> NOTE
                "INFO" -> INFO
                "TODO" -> TODO
                "TIP" -> TIP
                "HINT" -> HINT
                "IMPORTANT" -> IMPORTANT
                "SUCCESS" -> SUCCESS
                "CHECK" -> CHECK
                "DONE" -> DONE
                "QUESTION" -> QUESTION
                "HELP" -> HELP
                "FAQ" -> FAQ
                "WARNING" -> WARNING
                "CAUTION" -> CAUTION
                "ATTENTION" -> ATTENTION
                "FAILURE" -> FAILURE
                "FAIL" -> FAIL
                "MISSING" -> MISSING
                "DANGER" -> DANGER
                "ERROR" -> ERROR
                "BUG" -> BUG
                "EXAMPLE" -> EXAMPLE
                "QUOTE" -> QUOTE
                "CITE" -> CITE
                else -> NOTE
            }
        }
    }
}

/**
 * Structured Markdown AST blocks supporting Obsidian & GitHub Flavored Markdown.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String, val id: String = "") : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class Alert(
        val type: AlertType,
        val title: String,
        val isFoldable: Boolean = false,
        val defaultFolded: Boolean = false,
        val content: List<MarkdownBlock>,
    ) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TextAlign>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
    data class ListItem(val ordered: Boolean, val index: Int, val text: String, val depth: Int = 0) : MarkdownBlock
    data class TaskItem(val isChecked: Boolean, val text: String, val depth: Int = 0) : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
    data class MathBlock(val expression: String) : MarkdownBlock
    data class Footnote(val id: String, val text: String) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
}
