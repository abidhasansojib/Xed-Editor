package com.rk.tabs.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Obsidian & GitHub compliant inline parser converting rich Markdown and HTML tokens to an [AnnotatedString].
 * Uses standard ICU-compatible Pattern matching with zero named-group nesting for 100% Android compatibility.
 */
object InlineMarkdown {

    private val TOKEN_PATTERN: Pattern =
        Pattern.compile(
            "(\\*\\*\\*|___)(.+?)\\1|" + // 1, 2: BoldItalic
                "(\\*\\*|__)(.+?)\\3|<b>(.+?)</b>|<strong>(.+?)</strong>|" + // 3,4,5,6: Bold
                "(\\*|_)(.+?)\\7|<i>(.+?)</i>|<em>(.+?)</em>|" + // 7,8,9,10: Italic
                "==([^=\\n]+)==|<mark>([^<]+)</mark>|" + // 11, 12: Highlight
                "~~(.+?)~~|<s>(.+?)</s>|<strike>(.+?)</strike>|<del>(.+?)</del>|" + // 13,14,15,16: Strike
                "<u>(.+?)</u>|<ins>(.+?)</ins>|" + // 17, 18: Underline
                "(!?)\\[\\[([^|\\]\\n]+)(?:\\|([^\\]\\n]+))?\\]\\]|" + // 19,20,21: Wikilink (isEmbed, target, label)
                "`([^`\\n]+)`|<code>([^<]+)</code>|" + // 22, 23: Code
                "\\$([^$\\n]+)\\$|" + // 24: Math
                "<kbd>([^<]+)</kbd>|" + // 25: Kbd
                "\\[\\^([^\\]\\n]+)\\]|" + // 26: Footnote
                "(?:^|\\s)(#[a-zA-Z0-9_\\-/]+)|" + // 27: Hashtag
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)|" + // 28, 29: ImgLink
                "\\[([^\\]]+)\\]\\(([^)]+)\\)|<a\\s+href=[\"']([^\"']+)[\"']>([^<]+)</a>|" + // 30,31,32,33: Link
                "<br\\s*/?>", // BR
        )

    fun parse(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ): AnnotatedString {
        val cleanText = unescapeHtml(text)
        val matcher: Matcher = TOKEN_PATTERN.matcher(cleanText)

        return buildAnnotatedString {
            var lastIndex = 0

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()

                if (start > lastIndex) {
                    append(cleanText.substring(lastIndex, start))
                }

                when {
                    // Bold Italic
                    matcher.group(2) != null -> {
                        val content = matcher.group(2) ?: ""
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                    }

                    // Bold
                    matcher.group(4) != null || matcher.group(5) != null || matcher.group(6) != null -> {
                        val content = matcher.group(4) ?: matcher.group(5) ?: matcher.group(6) ?: ""
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(content)
                        pop()
                    }

                    // Italic
                    matcher.group(8) != null || matcher.group(9) != null || matcher.group(10) != null -> {
                        val content = matcher.group(8) ?: matcher.group(9) ?: matcher.group(10) ?: ""
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                    }

                    // Highlight (==text== / <mark>text</mark>)
                    matcher.group(11) != null || matcher.group(12) != null -> {
                        val content = matcher.group(11) ?: matcher.group(12) ?: ""
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

                    // Strikethrough
                    matcher.group(13) != null || matcher.group(14) != null || matcher.group(15) != null || matcher.group(16) != null -> {
                        val content = matcher.group(13) ?: matcher.group(14) ?: matcher.group(15) ?: matcher.group(16) ?: ""
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(content)
                        pop()
                    }

                    // Underline
                    matcher.group(17) != null || matcher.group(18) != null -> {
                        val content = matcher.group(17) ?: matcher.group(18) ?: ""
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(content)
                        pop()
                    }

                    // Obsidian Wikilink [[target|label]]
                    matcher.group(20) != null -> {
                        val isEmbed = matcher.group(19) == "!"
                        val target = (matcher.group(20) ?: "").trim()
                        val customLabel = matcher.group(21)?.ifBlank { target } ?: target

                        if (isEmbed) {
                            append("[Embed: $customLabel]")
                        } else {
                            val linkStart = length
                            pushStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            append(customLabel)
                            pop()
                            addStringAnnotation(tag = "WIKILINK", annotation = target, start = linkStart, end = length)
                        }
                    }

                    // Inline Code
                    matcher.group(22) != null || matcher.group(23) != null -> {
                        val content = matcher.group(22) ?: matcher.group(23) ?: ""
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                color = codeTextColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            ),
                        )
                        append(" $content ")
                        pop()
                    }

                    // Inline Math ($math$)
                    matcher.group(24) != null -> {
                        val content = matcher.group(24) ?: ""
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

                    // Kbd (<kbd>key</kbd>)
                    matcher.group(25) != null -> {
                        val content = matcher.group(25) ?: ""
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

                    // Footnote Reference [^1]
                    matcher.group(26) != null -> {
                        val id = matcher.group(26) ?: ""
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

                    // Hashtag #tag
                    matcher.group(27) != null -> {
                        val tag = matcher.group(27) ?: ""
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

                    // Image Link ![alt](url)
                    matcher.group(28) != null || matcher.group(29) != null -> {
                        val alt = matcher.group(28) ?: ""
                        append("[Image: $alt]")
                    }

                    // Standard Link [text](url) or <a href="url">text</a>
                    matcher.group(30) != null || matcher.group(32) != null -> {
                        val linkText = matcher.group(30) ?: matcher.group(33) ?: ""
                        val linkUrl = matcher.group(31) ?: matcher.group(32) ?: ""
                        val urlStart = length
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        append(linkText)
                        pop()
                        addStringAnnotation(tag = "URL", annotation = linkUrl, start = urlStart, end = length)
                    }

                    // <br>
                    matcher.group(0).startsWith("<br", ignoreCase = true) -> {
                        append("\n")
                    }
                }

                lastIndex = end
            }

            if (lastIndex < cleanText.length) {
                append(cleanText.substring(lastIndex))
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
