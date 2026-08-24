package com.rk.tabs.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * High-performance parser converting inline Markdown and standard HTML formatting into an [AnnotatedString].
 */
object InlineMarkdown {

    private val INLINE_TOKEN_REGEX =
        Regex(
            """(?x)
            (?<bolditalic>\*\*\*(.+?)\*\*\*|___(.+?)___)|
            (?<bold>\*\*(.+?)\*\*|__(.+?)__|<b>(.+?)</b>|<strong>(.+?)</strong>)|
            (?<italic>\*(.+?)\*|_(.+?)_|<i>(.+?)</i>|<em>(.+?)</em>)|
            (?<strike>~~(.+?)~~|<s>(.+?)</s>|<strike>(.+?)</strike>|<del>(.+?)</del>)|
            (?<under><u>(.+?)</u>|<ins>(.+?)</ins>)|
            (?<code>`([^`]+)`|<code>([^<]+)</code>)|
            (?<kbd><kbd>([^<]+)</kbd>)|
            (?<mark><mark>([^<]+)</mark>)|
            (?<link>\[([^\]]+)\]\(([^)]+)\)|<a\s+href=["']([^"']+)["']>([^<]+)</a>)|
            (?<imglink>!\[([^\]]*)\]\(([^)]+)\))|
            (?<br><br\s*/?>)
            """,
        )

    fun parse(
        text: String,
        primaryColor: Color,
        codeBgColor: Color,
        codeTextColor: Color,
    ): AnnotatedString {
        val cleanText = unescapeHtml(text)
        return buildAnnotatedString {
            var lastIndex = 0

            INLINE_TOKEN_REGEX.findAll(cleanText).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(cleanText.substring(lastIndex, match.range.first))
                }

                when {
                    match.groups["bolditalic"] != null -> {
                        val content = match.groupValues[2].ifEmpty { match.groupValues[3] }
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                    }

                    match.groups["bold"] != null -> {
                        val content =
                            listOf(match.groupValues[4], match.groupValues[5], match.groupValues[6], match.groupValues[7])
                                .firstOrNull { it.isNotEmpty() } ?: ""
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(content)
                        pop()
                    }

                    match.groups["italic"] != null -> {
                        val content =
                            listOf(match.groupValues[8], match.groupValues[9], match.groupValues[10], match.groupValues[11])
                                .firstOrNull { it.isNotEmpty() } ?: ""
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                    }

                    match.groups["strike"] != null -> {
                        val content =
                            listOf(match.groupValues[12], match.groupValues[13], match.groupValues[14], match.groupValues[15])
                                .firstOrNull { it.isNotEmpty() } ?: ""
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(content)
                        pop()
                    }

                    match.groups["under"] != null -> {
                        val content =
                            listOf(match.groupValues[16], match.groupValues[17]).firstOrNull { it.isNotEmpty() } ?: ""
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(content)
                        pop()
                    }

                    match.groups["code"] != null -> {
                        val content = match.groupValues[18].ifEmpty { match.groupValues[19] }
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                color = codeTextColor,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        append(" $content ")
                        pop()
                    }

                    match.groups["kbd"] != null -> {
                        val content = match.groupValues[20]
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

                    match.groups["mark"] != null -> {
                        val content = match.groupValues[21]
                        pushStyle(
                            SpanStyle(
                                background = Color(0xFFFFEB3B).copy(alpha = 0.4f),
                                color = Color.Unspecified,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        append(content)
                        pop()
                    }

                    match.groups["link"] != null -> {
                        val linkText = match.groupValues[22].ifEmpty { match.groupValues[25] }
                        val linkUrl = match.groupValues[23].ifEmpty { match.groupValues[24] }
                        val start = length
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        append(linkText)
                        pop()
                        addStringAnnotation(tag = "URL", annotation = linkUrl, start = start, end = length)
                    }

                    match.groups["imglink"] != null -> {
                        val alt = match.groupValues[26]
                        append("[Image: $alt]")
                    }

                    match.groups["br"] != null -> {
                        append("\n")
                    }
                }

                lastIndex = match.range.last + 1
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
