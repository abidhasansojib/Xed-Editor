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
 * High-performance parser that converts inline Markdown formatting into an [AnnotatedString].
 */
object InlineMarkdown {

    private val INLINE_TOKEN_REGEX =
        Regex(
            """(?x)
            (?<bolditalic>\*\*\*(.+?)\*\*\*|___(.+?)___)|
            (?<bold>\*\*(.+?)\*\*|__(.+?)__)|
            (?<italic>\*(.+?)\*|_(.+?)_)|
            (?<strike>~~(.+?)~~)|
            (?<code>`([^`]+)`)|
            (?<link>\[([^\]]+)\]\(([^)]+)\))|
            (?<imglink>!\[([^\]]*)\]\(([^)]+)\))
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
                // Append text before this match
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
                        val content = match.groupValues[4].ifEmpty { match.groupValues[5] }
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(content)
                        pop()
                    }

                    match.groups["italic"] != null -> {
                        val content = match.groupValues[6].ifEmpty { match.groupValues[7] }
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                    }

                    match.groups["strike"] != null -> {
                        val content = match.groupValues[8]
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(content)
                        pop()
                    }

                    match.groups["code"] != null -> {
                        val content = match.groupValues[9]
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

                    match.groups["link"] != null -> {
                        val linkText = match.groupValues[10]
                        val linkUrl = match.groupValues[11]
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
                        val alt = match.groupValues[12]
                        append("[Image: $alt]")
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
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}
