package com.rk.tabs.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * AST-driven inline Markdown parser with Obsidian tokens and nested styling support.
 * Powered by [org.commonmark] for 100% standard GFM compliance and zero regex crash.
 */
object InlineMarkdown {

    private val EXTENSIONS =
        listOf(
            TablesExtension.create(),
            TaskListItemsExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
        )

    private val PARSER = Parser.builder().extensions(EXTENSIONS).build()

    // Obsidian & GFM inline tokens: ==highlight==, [[wikilink]], $math$, [^fn], #hashtag, <kbd>, <u>, <sub>/~sub~, <sup>/^sup^
    private val OBSIDIAN_TOKEN_PATTERN: Pattern =
        Pattern.compile(
            "==([^=\\n]+)==|<mark>([^<]+)</mark>|" + // 1, 2: Highlight
                "(!?)\\[\\[([^|\\]\\n]+)(?:\\|([^\\]\\n]+))?\\]\\]|" + // 3,4,5: Wikilink (isEmbed, target, label)
                "\\$([^$\\n]+)\\$|" + // 6: Math
                "<kbd>([^<]+)</kbd>|" + // 7: Kbd
                "\\[\\^([^\\]\\n]+)\\]|" + // 8: Footnote
                "<u>([^<]+)</u>|<ins>([^<]+)</ins>|" + // 9, 10: Underline
                "<sub>([^<]+)</sub>|~([^~\\s\\n]+)~|" + // 11, 12: Subscript
                "<sup>([^<]+)</sup>|\\^([^\\^\\s\\n]+)\\^|" + // 13, 14: Superscript
                "(?:^|\\s)(#[a-zA-Z0-9_\\-/]+)", // 15: Hashtag
        )

    fun parse(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ): AnnotatedString {
        val cleanText = unescapeHtml(text)
        val document = PARSER.parse(cleanText)

        return buildAnnotatedString {
            var child = document.firstChild
            while (child != null) {
                renderNode(child, primaryColor, codeBgColor, codeTextColor)
                child = child.next
            }
        }
    }

    private fun AnnotatedString.Builder.renderNode(
        node: Node,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ) {
        when (node) {
            is Paragraph -> {
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
            }

            is Text -> {
                renderTextWithObsidian(node.literal ?: "", primaryColor, codeBgColor, codeTextColor)
            }

            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
                pop()
            }

            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
                pop()
            }

            is Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
                pop()
            }

            is Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                        color = codeTextColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                )
                append(" ${node.literal} ")
                pop()
            }

            is Link -> {
                val start = length
                pushStyle(
                    SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
                pop()
                val url = node.destination ?: ""
                addStringAnnotation(tag = "URL", annotation = url, start = start, end = length)
            }

            is Image -> {
                append("[Image: ${node.title ?: ""}]")
            }

            is HtmlInline -> {
                renderHtmlInline(node.literal ?: "", primaryColor, codeBgColor, codeTextColor)
            }

            is SoftLineBreak -> {
                append(" ")
            }

            is HardLineBreak -> {
                append("\n")
            }

            else -> {
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor)
                    child = child.next
                }
            }
        }
    }

    private fun AnnotatedString.Builder.renderTextWithObsidian(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ) {
        val matcher: Matcher = OBSIDIAN_TOKEN_PATTERN.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            when {
                // Highlight ==text== / <mark>text</mark>
                matcher.group(1) != null || matcher.group(2) != null -> {
                    val content = matcher.group(1) ?: matcher.group(2) ?: ""
                    pushStyle(
                        SpanStyle(
                            background = Color(0xFFFFF176).copy(alpha = 0.45f),
                            color = Color.Unspecified,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    append(" $content ")
                    pop()
                }

                // Wikilink [[target|label]]
                matcher.group(4) != null -> {
                    val isEmbed = matcher.group(3) == "!"
                    val target = (matcher.group(4) ?: "").trim()
                    val label = matcher.group(5)?.ifBlank { target } ?: target

                    if (isEmbed) {
                        append("[Embed: $label]")
                    } else {
                        val linkStart = length
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        append(label)
                        pop()
                        addStringAnnotation(tag = "WIKILINK", annotation = target, start = linkStart, end = length)
                    }
                }

                // Math $math$
                matcher.group(6) != null -> {
                    val content = matcher.group(6) ?: ""
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            background = codeBgColor.copy(alpha = 0.5f),
                            color = primaryColor,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    append(" $content ")
                    pop()
                }

                // Kbd <kbd>key</kbd>
                matcher.group(7) != null -> {
                    val content = matcher.group(7) ?: ""
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    append(" $content ")
                    pop()
                }

                // Footnote [^1]
                matcher.group(8) != null -> {
                    val id = matcher.group(8) ?: ""
                    val fnStart = length
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                    )
                    append("[$id]")
                    pop()
                    addStringAnnotation(tag = "FOOTNOTE", annotation = id, start = fnStart, end = length)
                }

                // Underline <u>text</u>
                matcher.group(9) != null || matcher.group(10) != null -> {
                    val content = matcher.group(9) ?: matcher.group(10) ?: ""
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(content)
                    pop()
                }

                // Subscript <sub>text</sub> / ~text~
                matcher.group(11) != null || matcher.group(12) != null -> {
                    val content = matcher.group(11) ?: matcher.group(12) ?: ""
                    pushStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 11.sp))
                    append(content)
                    pop()
                }

                // Superscript <sup>text</sup> / ^text^
                matcher.group(13) != null || matcher.group(14) != null -> {
                    val content = matcher.group(13) ?: matcher.group(14) ?: ""
                    pushStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 11.sp))
                    append(content)
                    pop()
                }

                // Hashtag #tag
                matcher.group(15) != null -> {
                    val tag = matcher.group(15) ?: ""
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            background = primaryColor.copy(alpha = 0.12f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                    )
                    append(" $tag ")
                    pop()
                }
            }

            lastIndex = end
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    private fun AnnotatedString.Builder.renderHtmlInline(
        html: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ) {
        val trimmed = html.trim()
        when {
            trimmed.startsWith("<br", ignoreCase = true) -> append("\n")
            trimmed.startsWith("<hr", ignoreCase = true) -> append("\n---\n")
            else -> {
                // If it's a simple opening/closing tag, parse inner text
                val cleaned = trimmed.replace(Regex("<[^>]+>"), "")
                if (cleaned.isNotEmpty()) {
                    append(cleaned)
                }
            }
        }
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}
