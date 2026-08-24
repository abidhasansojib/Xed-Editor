package com.rk.tabs.markdown

import androidx.compose.ui.text.style.TextAlign
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.util.regex.Pattern

/**
 * Standard AST-based Obsidian and GFM Markdown parser powered by [org.commonmark].
 */
object MarkdownBlockParser {

    private val EXTENSIONS =
        listOf(
            TablesExtension.create(),
            TaskListItemsExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
        )

    private val PARSER = Parser.builder().extensions(EXTENSIONS).build()

    private val HTML_IMG_PATTERN =
        Pattern.compile("<img\\s+[^>]*src=[\"']([^\"']+)[\"'][^>]*?(?:alt=[\"']([^\"']*)[\"'])?[^>]*>", Pattern.CASE_INSENSITIVE)

    fun parse(markdown: String): List<MarkdownBlock> {
        val document = PARSER.parse(markdown)
        val blocks = mutableListOf<MarkdownBlock>()
        var child: Node? = document.firstChild

        while (child != null) {
            parseNodeInto(child, blocks)
            child = child.next
        }

        return blocks
    }

    private fun parseNodeInto(node: Node, blocks: MutableList<MarkdownBlock>) {
        when (node) {
            is Heading -> {
                blocks.add(MarkdownBlock.Heading(level = node.level, text = renderInlineText(node)))
            }

            is FencedCodeBlock -> {
                val lang = (node.info ?: "").trim()
                val code = (node.literal ?: "").trimEnd()
                if (lang.equals("math", ignoreCase = true)) {
                    blocks.add(MarkdownBlock.MathBlock(expression = code))
                } else {
                    blocks.add(MarkdownBlock.CodeBlock(language = lang, code = code))
                }
            }

            is IndentedCodeBlock -> {
                blocks.add(MarkdownBlock.CodeBlock(language = "", code = (node.literal ?: "").trimEnd()))
            }

            is BlockQuote -> {
                blocks.add(parseBlockQuote(node))
            }

            is TableBlock -> {
                blocks.add(parseTable(node))
            }

            is BulletList -> {
                var item: Node? = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        val text = renderInlineText(item).trim()
                        val taskMatch = Regex("^\\[([ xX])\\]\\s*(.*)$").find(text)
                        if (taskMatch != null) {
                            val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                            val content = taskMatch.groupValues[2]
                            blocks.add(MarkdownBlock.TaskItem(isChecked = isChecked, text = content))
                        } else {
                            blocks.add(MarkdownBlock.ListItem(ordered = false, index = 0, text = text, depth = 0))
                        }
                    }
                    item = item.next
                }
            }

            is OrderedList -> {
                var item: Node? = node.firstChild
                var idx = node.startNumber
                while (item != null) {
                    if (item is ListItem) {
                        val text = renderInlineText(item).trim()
                        blocks.add(MarkdownBlock.ListItem(ordered = true, index = idx, text = text, depth = 0))
                        idx++
                    }
                    item = item.next
                }
            }

            is ThematicBreak -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }

            is Paragraph -> {
                val raw = renderInlineText(node).trim()

                // Check for block math: $$...$$
                val mathMatch = Regex("^\\$\\$([\\s\\S]*)\\$\\$$").find(raw)
                if (mathMatch != null) {
                    blocks.add(MarkdownBlock.MathBlock(expression = mathMatch.groupValues[1].trim()))
                    return
                }

                // Check for Footnote definition: [^1]: Text
                val footnoteMatch = Regex("^\\[\\^([^\\]]+)\\]:\\s*(.*)$").find(raw)
                if (footnoteMatch != null) {
                    blocks.add(
                        MarkdownBlock.Footnote(
                            id = footnoteMatch.groupValues[1],
                            text = footnoteMatch.groupValues[2],
                        ),
                    )
                    return
                }

                // Check if paragraph contains only a standalone image
                val first = node.firstChild
                if (first is Image && first.next == null) {
                    val alt = renderInlineText(first)
                    blocks.add(MarkdownBlock.Image(alt = alt, url = first.destination ?: ""))
                } else {
                    blocks.add(MarkdownBlock.Paragraph(text = raw))
                }
            }

            is HtmlBlock -> {
                val raw = (node.literal ?: "").trim()

                // Check for embedded <img> tags inside HTML blocks (e.g. <div align="center"><img src="..."></div>)
                val imgMatcher = HTML_IMG_PATTERN.matcher(raw)
                if (imgMatcher.find()) {
                    val src = imgMatcher.group(1) ?: ""
                    val alt = imgMatcher.group(2) ?: ""
                    blocks.add(MarkdownBlock.Image(alt = alt, url = src))
                } else {
                    // Check if inner markdown has ![alt](url)
                    val mdImgMatch = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)").find(raw)
                    if (mdImgMatch != null) {
                        val alt = mdImgMatch.groupValues[1]
                        val url = mdImgMatch.groupValues[2]
                        blocks.add(MarkdownBlock.Image(alt = alt, url = url))
                    } else {
                        val stripped = raw.replace(Regex("<div[^>]*>|</div>|<center>|</center>|<p[^>]*>|</p>", RegexOption.IGNORE_CASE), "").trim()
                        if (stripped.isNotEmpty()) {
                            blocks.add(MarkdownBlock.Paragraph(text = stripped))
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun parseBlockQuote(node: BlockQuote): MarkdownBlock {
        val rawText = renderInlineText(node).trim()
        val alertMatch =
            Regex("^\\[!([a-zA-Z0-9_-]+)([+-])?\\]\\s*([^\\n]*)(?:\\n([\\s\\S]*))?$", RegexOption.IGNORE_CASE)
                .find(rawText)

        if (alertMatch != null) {
            val typeStr = alertMatch.groupValues[1]
            val foldSymbol = alertMatch.groupValues[2]
            val inlineTitle = alertMatch.groupValues[3].trim()
            val body = alertMatch.groupValues[4].trim()

            val alertType = AlertType.fromTag(typeStr)
            val title = if (inlineTitle.isNotEmpty()) inlineTitle else alertType.defaultTitle
            val isFoldable = foldSymbol.isNotEmpty()
            val isDefaultFolded = foldSymbol == "-"

            val subBlocks = if (body.isNotEmpty()) parse(body) else emptyList()
            return MarkdownBlock.Alert(
                type = alertType,
                title = title,
                isFoldable = isFoldable,
                defaultFolded = isDefaultFolded,
                content = subBlocks,
            )
        }

        return MarkdownBlock.Blockquote(text = rawText)
    }

    private fun parseTable(tableBlock: TableBlock): MarkdownBlock {
        val headers = mutableListOf<String>()
        val alignments = mutableListOf<TextAlign>()
        val rows = mutableListOf<List<String>>()

        var section: Node? = tableBlock.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> {
                    var row: Node? = section.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            var cell: Node? = row.firstChild
                            while (cell != null) {
                                if (cell is TableCell) {
                                    headers.add(renderInlineText(cell))
                                    alignments.add(
                                        when (cell.alignment) {
                                            TableCell.Alignment.LEFT -> TextAlign.Left
                                            TableCell.Alignment.CENTER -> TextAlign.Center
                                            TableCell.Alignment.RIGHT -> TextAlign.Right
                                            else -> TextAlign.Left
                                        },
                                    )
                                }
                                cell = cell.next
                            }
                        }
                        row = row.next
                    }
                }

                is TableBody -> {
                    var row: Node? = section.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            val rowCells = mutableListOf<String>()
                            var cell: Node? = row.firstChild
                            while (cell != null) {
                                if (cell is TableCell) {
                                    rowCells.add(renderInlineText(cell))
                                }
                                cell = cell.next
                            }
                            rows.add(rowCells)
                        }
                        row = row.next
                    }
                }
            }
            section = section.next
        }

        return MarkdownBlock.Table(headers = headers, alignments = alignments, rows = rows)
    }

    fun renderInlineText(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild

        while (child != null) {
            when (child) {
                is Text -> sb.append(child.literal)
                is Code -> sb.append("`").append(child.literal).append("`")
                is Emphasis -> sb.append("*").append(renderInlineText(child)).append("*")
                is StrongEmphasis -> sb.append("**").append(renderInlineText(child)).append("**")
                is Strikethrough -> sb.append("~~").append(renderInlineText(child)).append("~~")
                is Link -> sb.append("[").append(renderInlineText(child)).append("](").append(child.destination ?: "").append(")")
                is Image -> sb.append("![").append(renderInlineText(child)).append("](").append(child.destination ?: "").append(")")
                is SoftLineBreak -> sb.append(" ")
                is HardLineBreak -> sb.append("\n")
                is HtmlInline -> sb.append(child.literal)
                is CustomNode -> sb.append(renderInlineText(child))
                is CustomBlock -> sb.append(renderInlineText(child))
                else -> sb.append(renderInlineText(child))
            }
            child = child.next
        }

        return sb.toString()
    }
}
