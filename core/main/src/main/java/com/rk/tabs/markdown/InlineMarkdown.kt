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
 * AST-driven inline Markdown parser with Obsidian tokens, LaTeX math rendering,
 * Emoji shortcodes, Spoilers, CriticMarkup, and nested styling support.
 * Powered by [org.commonmark] for 100% standard GFM compliance.
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

    private data class MathPlaceholder(val isBlock: Boolean, val expression: String)

    private val BLOCK_MATH_REGEX = Regex("(?<!\\\\)\\$\\$([\\s\\S]+?)(?<!\\\\)\\$\\$")
    private val INLINE_MATH_REGEX = Regex("(?<!\\\\)\\$(?!\\s)([^$\\n]+?)(?<!\\s)(?<!\\\\)\\$")
    private val OBSIDIAN_COMMENT_REGEX = Regex("%%[\\s\\S]*?%%")
    private val SAMP_REGEX = Regex("<samp>|</samp>", RegexOption.IGNORE_CASE)
    private val VAR_REGEX = Regex("<var>|</var>", RegexOption.IGNORE_CASE)
    private val TAG_STRIP_REGEX = Regex("<[^>]+>")

    // Common GitHub & Markdown Emoji Shortcode Map
    private val EMOJI_MAP =
        mapOf(
            "rocket" to "🚀", "fire" to "🔥", "sparkles" to "✨", "tada" to "🎉",
            "smile" to "😄", "laughing" to "😆", "grinning" to "😀", "joy" to "😂",
            "heart" to "❤️", "star" to "⭐", "100" to "💯", "thumbsup" to "👍", "+1" to "👍",
            "thumbsdown" to "👎", "-1" to "👎", "check" to "✔️", "white_check_mark" to "✅",
            "heavy_check_mark" to "✔️", "x" to "❌", "cross_mark" to "❌", "warning" to "⚠️",
            "memo" to "📝", "pencil" to "✏️", "bulb" to "💡", "bug" to "🐛",
            "gear" to "⚙️", "wrench" to "🔧", "hammer" to "🔨", "package" to "📦",
            "lock" to "🔒", "unlock" to "🔓", "key" to "🔑", "link" to "🔗",
            "zap" to "⚡", "eyes" to "👀", "book" to "📖", "books" to "📚",
            "art" to "🎨", "computer" to "💻", "desktop_computer" to "🖥️", "phone" to "📱",
            "coffee" to "☕", "tea" to "🍵", "hourglass" to "⏳", "calendar" to "📅",
            "pin" to "📌", "pushpin" to "📌", "bell" to "🔔", "shield" to "🛡️",
            "chart_with_upwards_trend" to "📈", "chart_with_downwards_trend" to "📉",
            "globe_with_meridians" to "🌐", "earth_africa" to "🌍", "earth_americas" to "🌎",
            "earth_asia" to "🌏", "octocat" to "🐙", "robot" to "🤖", "wave" to "👋",
            "pray" to "🙏", "clap" to "👏", "point_right" to "👉", "point_left" to "👈",
            "point_up" to "👆", "point_down" to "👇", "ok_hand" to "👌", "raised_hands" to "🙌",
            "thinking" to "🤔", "nerd" to "🤓", "sunglasses" to "😎", "boom" to "💥",
            "collision" to "💥", "poop" to "💩", "alien" to "👽", "skull" to "💀",
        )

    // Obsidian & GFM inline tokens:
    // 1: \uE000MATH(\d+)\uE000
    // 2, 3: Highlight ==text== / <mark>text</mark>
    // 4, 5, 6: Wikilink (!?)[[target|label]]
    // 7, 8: Spoiler ||text|| / <spoiler>text</spoiler>
    // 9: Kbd <kbd>text</kbd>
    // 10: Footnote [^fn]
    // 11, 12: Underline <u> / <ins> / ++inserted++
    // 13, 14: Subscript <sub> / ~text~
    // 15, 16: Superscript <sup> / ^text^
    // 17, 18: Inline Diff {-deleted-} / --deleted--
    // 19: Emoji :emoji_name:
    // 20: Hashtag #hashtag
    // 21: Mention @user
    private val OBSIDIAN_TOKEN_PATTERN: Pattern =
        Pattern.compile(
            "\\uE000MATH(\\d+)\\uE000|" + // 1: Math placeholder
                "==([^=\\n]+)==|<mark>([^<]+)</mark>|" + // 2, 3: Highlight
                "(!?)\\[\\[([^|\\]\\n]+)(?:\\|([^\\]\\n]+))?\\]\\]|" + // 4,5,6: Wikilink (isEmbed, target, label)
                "\\|\\|([^|\\n]+)\\|\\||<spoiler>([^<]+)</spoiler>|" + // 7, 8: Spoiler
                "<kbd>([^<]+)</kbd>|" + // 9: Kbd
                "\\[\\^([^\\]\\n]+)\\]|" + // 10: Footnote
                "<u>([^<]+)</u>|<ins>([^<]+)</ins>|\\+\\+([^+]+)\\+\\+|" + // 11, 12, 13: Underline / Inserted
                "<sub>([^<]+)</sub>|~([^~\\s\\n]+)~|" + // 14, 15: Subscript
                "<sup>([^<]+)</sup>|\\^([^\\^\\s\\n]+)\\^|" + // 16, 17: Superscript
                "\\{-([^}]+)-\\}|--([^-\\s\\n]+)--|" + // 18, 19: Deleted / CriticMarkup
                ":([a-zA-Z0-9_+-]+):|" + // 20: Emoji shortcode
                "(?:^|\\s)(#[a-zA-Z0-9_\\-/]+)|" + // 21: Hashtag
                "(?:^|\\s)(@[a-zA-Z0-9_\\-]+)", // 22: Mention
        )

    private data class CacheKey(
        val text: String,
        val primaryColor: ULong,
        val codeBgColor: ULong,
        val codeTextColor: ULong,
    )

    private val PARSE_CACHE =
        object : java.util.LinkedHashMap<CacheKey, AnnotatedString>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, AnnotatedString>?): Boolean {
                return size > 600
            }
        }

    fun parse(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ): AnnotatedString {
        val key = CacheKey(text, primaryColor.value, codeBgColor.value, codeTextColor.value)
        synchronized(PARSE_CACHE) {
            val cached = PARSE_CACHE[key]
            if (cached != null) return cached
        }

        val result = parseInternal(text, primaryColor, codeBgColor, codeTextColor)

        synchronized(PARSE_CACHE) {
            PARSE_CACHE[key] = result
        }
        return result
    }

    private fun parseInternal(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ): AnnotatedString {
        val mathList = mutableListOf<MathPlaceholder>()
        var textWithPlaceholders = text

        // Strip Obsidian comments %% ... %%
        textWithPlaceholders = textWithPlaceholders.replace(OBSIDIAN_COMMENT_REGEX, "")

        // Extract display math $$...$$ first
        textWithPlaceholders =
            BLOCK_MATH_REGEX.replace(textWithPlaceholders) { matchResult ->
                val idx = mathList.size
                mathList.add(MathPlaceholder(isBlock = true, expression = matchResult.groupValues[1]))
                "\uE000MATH${idx}\uE000"
            }

        // Extract inline math $...$
        textWithPlaceholders =
            INLINE_MATH_REGEX.replace(textWithPlaceholders) { matchResult ->
                val idx = mathList.size
                mathList.add(MathPlaceholder(isBlock = false, expression = matchResult.groupValues[1]))
                "\uE000MATH${idx}\uE000"
            }

        val cleanText = unescapeHtml(textWithPlaceholders)
        val document = PARSER.parse(cleanText)

        return buildAnnotatedString {
            var child = document.firstChild
            while (child != null) {
                renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
                child = child.next
            }
        }
    }

    private fun AnnotatedString.Builder.renderNode(
        node: Node,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
        mathList: List<MathPlaceholder>,
    ) {
        when (node) {
            is Paragraph -> {
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
                    child = child.next
                }
            }

            is Text -> {
                renderTextWithObsidian(node.literal ?: "", primaryColor, codeBgColor, codeTextColor, mathList)
            }

            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
                    child = child.next
                }
                pop()
            }

            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
                    child = child.next
                }
                pop()
            }

            is Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                var child = node.firstChild
                while (child != null) {
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
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
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
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
                    renderNode(child, primaryColor, codeBgColor, codeTextColor, mathList)
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
        mathList: List<MathPlaceholder>,
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
                // 1: Math placeholder \uE000MATH(\d+)\uE000
                matcher.group(1) != null -> {
                    val idx = matcher.group(1)?.toIntOrNull()
                    if (idx != null && idx in mathList.indices) {
                        val mathEntry = mathList[idx]
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = if (mathEntry.isBlock) codeBgColor.copy(alpha = 0.35f) else codeBgColor.copy(alpha = 0.25f),
                                color = primaryColor,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        append(" ")
                        LaTeXParser.renderTo(this, mathEntry.expression, primaryColor)
                        append(" ")
                        pop()
                    }
                }

                // 2, 3: Highlight ==text== / <mark>text</mark>
                matcher.group(2) != null || matcher.group(3) != null -> {
                    val content = matcher.group(2) ?: matcher.group(3) ?: ""
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

                // 4, 5, 6: Wikilink [[target|label]]
                matcher.group(5) != null -> {
                    val isEmbed = matcher.group(4) == "!"
                    val target = (matcher.group(5) ?: "").trim()
                    val label = matcher.group(6)?.ifBlank { target } ?: target

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

                // 7, 8: Spoiler ||text|| / <spoiler>text</spoiler>
                matcher.group(7) != null || matcher.group(8) != null -> {
                    val content = matcher.group(7) ?: matcher.group(8) ?: ""
                    pushStyle(
                        SpanStyle(
                            background = codeTextColor.copy(alpha = 0.85f),
                            color = codeBgColor,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    append(" $content ")
                    pop()
                }

                // 9: Kbd <kbd>key</kbd>
                matcher.group(9) != null -> {
                    val content = matcher.group(9) ?: ""
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

                // 10: Footnote [^1]
                matcher.group(10) != null -> {
                    val id = matcher.group(10) ?: ""
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

                // 11, 12, 13: Underline <u>text</u>, <ins>text</ins>, ++inserted++
                matcher.group(11) != null || matcher.group(12) != null || matcher.group(13) != null -> {
                    val content = matcher.group(11) ?: matcher.group(12) ?: matcher.group(13) ?: ""
                    val isInserted = matcher.group(13) != null
                    pushStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            background = if (isInserted) Color(0xFF43A047).copy(alpha = 0.15f) else Color.Transparent,
                            color = if (isInserted) Color(0xFF2E7D32) else Color.Unspecified,
                        ),
                    )
                    append(content)
                    pop()
                }

                // 14, 15: Subscript <sub>text</sub> / ~text~
                matcher.group(14) != null || matcher.group(15) != null -> {
                    val content = matcher.group(14) ?: matcher.group(15) ?: ""
                    pushStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 11.sp))
                    append(content)
                    pop()
                }

                // 16, 17: Superscript <sup>text</sup> / ^text^
                matcher.group(16) != null || matcher.group(17) != null -> {
                    val content = matcher.group(16) ?: matcher.group(17) ?: ""
                    pushStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 11.sp))
                    append(content)
                    pop()
                }

                // 18, 19: Deleted / CriticMarkup {-deleted-} / --deleted--
                matcher.group(18) != null || matcher.group(19) != null -> {
                    val content = matcher.group(18) ?: matcher.group(19) ?: ""
                    pushStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            background = Color(0xFFE53935).copy(alpha = 0.12f),
                            color = Color(0xFFC62828),
                        ),
                    )
                    append(content)
                    pop()
                }

                // 20: Emoji shortcode :emoji:
                matcher.group(20) != null -> {
                    val emojiKey = (matcher.group(20) ?: "").lowercase()
                    val emoji = EMOJI_MAP[emojiKey]
                    if (emoji != null) {
                        append(emoji)
                    } else {
                        append(":$emojiKey:")
                    }
                }

                // 21: Hashtag #tag
                matcher.group(21) != null -> {
                    val tag = matcher.group(21) ?: ""
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

                // 22: Mention @user
                matcher.group(22) != null -> {
                    val mention = matcher.group(22) ?: ""
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            background = primaryColor.copy(alpha = 0.16f),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    append(" $mention ")
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
            trimmed.startsWith("<samp>", ignoreCase = true) -> {
                val cleaned = trimmed.replace(SAMP_REGEX, "")
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor.copy(alpha = 0.5f)))
                append(" $cleaned ")
                pop()
            }
            trimmed.startsWith("<var>", ignoreCase = true) -> {
                val cleaned = trimmed.replace(VAR_REGEX, "")
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = primaryColor))
                append(cleaned)
                pop()
            }
            trimmed.startsWith("<abbr", ignoreCase = true) -> {
                val cleaned = trimmed.replace(TAG_STRIP_REGEX, "")
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
                append(cleaned)
                pop()
            }
            else -> {
                val cleaned = trimmed.replace(TAG_STRIP_REGEX, "")
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
