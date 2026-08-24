package com.rk.tabs.markdown

import androidx.compose.ui.text.style.TextAlign

/**
 * Robust GFM Markdown parser converting raw text into a hierarchy of [MarkdownBlock]s.
 */
object MarkdownBlockParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Skip blank lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 2. Fenced Code Block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                val lang = trimmed.removePrefix(fence).trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    val codeLine = lines[i]
                    if (codeLine.trim().startsWith(fence)) {
                        i++
                        break
                    }
                    codeLines.add(codeLine)
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
                continue
            }

            // 3. GitHub-style Alert Callout or Standard Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val rawQuote = lines[i].trim().removePrefix(">").trimStart()
                    quoteLines.add(rawQuote)
                    i++
                }

                val firstLine = quoteLines.firstOrNull() ?: ""
                val alertMatch = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]\\s*(.*)$", RegexOption.IGNORE_CASE).find(firstLine)

                if (alertMatch != null) {
                    val typeStr = alertMatch.groupValues[1]
                    val alertType = AlertType.fromTag(typeStr) ?: AlertType.NOTE
                    val inlineTitle = alertMatch.groupValues[2].trim()
                    val title = if (inlineTitle.isNotEmpty()) inlineTitle else alertType.label

                    val bodyText = quoteLines.drop(1).joinToString("\n")
                    val subBlocks = if (bodyText.isNotBlank()) parse(bodyText) else emptyList()
                    blocks.add(MarkdownBlock.Alert(type = alertType, title = title, content = subBlocks))
                } else {
                    val quoteText = quoteLines.joinToString("\n")
                    blocks.add(MarkdownBlock.Blockquote(quoteText))
                }
                continue
            }

            // 4. ATX Headings (# to ######)
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Heading(level, title))
                i++
                continue
            }

            // 5. Horizontal Rule
            if (trimmed.matches(Regex("^(?:-{3,}|\\*{3,}|_{3,})$"))) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 6. GFM Table
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && isTableDivider(lines[i + 1])) {
                val headerRow = parseTableRow(trimmed)
                val dividerRow = lines[i + 1].trim()
                val alignments = parseTableAlignments(dividerRow)
                val rows = mutableListOf<List<String>>()
                i += 2

                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    rows.add(parseTableRow(lines[i].trim()))
                    i++
                }

                blocks.add(MarkdownBlock.Table(headers = headerRow, alignments = alignments, rows = rows))
                continue
            }

            // 7. Task Item (- [ ] or - [x])
            val taskMatch = Regex("^[-*+]\\s+\\[([ xX])\\]\\s+(.+)$").find(trimmed)
            if (taskMatch != null) {
                val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                val text = taskMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.TaskItem(isChecked, text))
                i++
                continue
            }

            // 8. Ordered List Item
            val orderedMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(trimmed)
            if (orderedMatch != null) {
                val index = orderedMatch.groupValues[1].toIntOrNull() ?: 1
                val text = orderedMatch.groupValues[2].trim()
                val indent = line.takeWhile { it == ' ' }.length / 2
                blocks.add(MarkdownBlock.ListItem(ordered = true, index = index, text = text, depth = indent))
                i++
                continue
            }

            // 9. Unordered List Item
            val unorderedMatch = Regex("^[-*+]\\s+(.+)$").find(trimmed)
            if (unorderedMatch != null) {
                val text = unorderedMatch.groupValues[1].trim()
                val indent = line.takeWhile { it == ' ' }.length / 2
                blocks.add(MarkdownBlock.ListItem(ordered = false, index = 0, text = text, depth = indent))
                i++
                continue
            }

            // 10. Standalone Image Block: ![alt](url)
            val imgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                blocks.add(MarkdownBlock.Image(alt, url))
                i++
                continue
            }

            // 11. Setext Headings (underlined with === or ---)
            if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.matches(Regex("^={2,}$"))) {
                    blocks.add(MarkdownBlock.Heading(level = 1, text = trimmed))
                    i += 2
                    continue
                } else if (nextLine.matches(Regex("^-{2,}$"))) {
                    blocks.add(MarkdownBlock.Heading(level = 2, text = trimmed))
                    i += 2
                    continue
                }
            }

            // 12. Regular Paragraph
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size) {
                val curr = lines[i].trim()
                if (curr.isEmpty() ||
                    curr.startsWith("#") ||
                    curr.startsWith(">") ||
                    curr.startsWith("```") ||
                    curr.startsWith("~~~") ||
                    (curr.startsWith("|") && curr.endsWith("|")) ||
                    curr.matches(Regex("^(?:-{3,}|\\*{3,}|_{3,})$")) ||
                    Regex("^[-*+]\\s+").containsMatchIn(curr) ||
                    Regex("^\\d+\\.\\s+").containsMatchIn(curr) ||
                    Regex("^!\\[(.*?)\\]\\((.*?)\\)$").matches(curr)
                ) {
                    break
                }
                paragraphLines.add(lines[i])
                i++
            }

            if (paragraphLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
            }
        }

        return blocks
    }

    private fun isTableDivider(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false
        val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return cells.isNotEmpty() && cells.all { it.matches(Regex("^:?-+:?$")) }
    }

    private fun parseTableRow(line: String): List<String> {
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }

    private fun parseTableAlignments(dividerLine: String): List<TextAlign> {
        return parseTableRow(dividerLine).map { cell ->
            val left = cell.startsWith(":")
            val right = cell.endsWith(":")
            when {
                left && right -> TextAlign.Center
                right -> TextAlign.Right
                else -> TextAlign.Left
            }
        }
    }
}
