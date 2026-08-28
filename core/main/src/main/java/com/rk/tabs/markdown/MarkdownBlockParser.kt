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
        var rawText = markdown
        val blocks = mutableListOf<MarkdownBlock>()
        val seenSlugs = mutableMapOf<String, Int>()

        // Strip Obsidian comments %% ... %%
        rawText = rawText.replace(Regex("%%[\\s\\S]*?%%"), "")

        // Check for YAML Frontmatter at the start of document: --- ... ---
        val frontmatterMatch = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---\\r?\\n").find(rawText)
        if (frontmatterMatch != null) {
            val fmBody = frontmatterMatch.groupValues[1]
            val data = parseYamlFrontmatter(fmBody)
            blocks.add(MarkdownBlock.Frontmatter(data = data, raw = fmBody.trim()))
            rawText = rawText.substring(frontmatterMatch.range.last + 1)
        }

        val document = PARSER.parse(rawText)
        var child: Node? = document.firstChild

        while (child != null) {
            parseNodeInto(child, blocks, seenSlugs)
            child = child.next
        }

        return blocks
    }

    private fun parseYamlFrontmatter(yaml: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        yaml.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx != -1) {
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim().trim('"', '\'')
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun parseNodeInto(
        node: Node,
        blocks: MutableList<MarkdownBlock>,
        seenSlugs: MutableMap<String, Int> = mutableMapOf(),
    ) {
        when (node) {
            is Heading -> {
                val rawText = renderInlineText(node).trim()
                val explicitIdMatch = Regex("\\{#[^}]+\\}").find(rawText)
                val explicitId = explicitIdMatch?.value?.removePrefix("{#")?.removeSuffix("}")?.trim()

                val htmlAnchorMatch =
                    Regex("<a\\s+(?:id|name)=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE).find(rawText)
                val htmlAnchor = htmlAnchorMatch?.groupValues?.get(1)?.trim()

                val cleanText =
                    rawText
                        .replace(Regex("\\{#[^}]+\\}"), "")
                        .replace(Regex("<a\\s+[^>]*>.*?</a>|<a\\s+[^>]*/>|<a\\s+[^>]*>", RegexOption.IGNORE_CASE), "")
                        .trim()

                val baseSlug = MarkdownScrollController.slugify(cleanText)
                val count = seenSlugs.getOrDefault(baseSlug, 0)
                seenSlugs[baseSlug] = count + 1
                val autoSlug = if (count == 0) baseSlug else "$baseSlug-$count"

                val primaryId = explicitId ?: htmlAnchor ?: autoSlug
                val aliases = mutableListOf<String>()
                if (autoSlug.isNotBlank()) aliases.add(autoSlug)
                if (baseSlug.isNotBlank() && baseSlug != autoSlug) aliases.add(baseSlug)
                if (!explicitId.isNullOrBlank()) aliases.add(explicitId)
                if (!htmlAnchor.isNullOrBlank()) aliases.add(htmlAnchor)

                blocks.add(
                    MarkdownBlock.Heading(
                        level = node.level,
                        text = cleanText,
                        id = primaryId,
                        anchorAliases = aliases.distinct(),
                    ),
                )
            }

            is FencedCodeBlock -> {
                val lang = (node.info ?: "").trim()
                val code = (node.literal ?: "").trimEnd()
                if (lang.equals("math", ignoreCase = true) || lang.equals("latex", ignoreCase = true) || lang.equals("tex", ignoreCase = true)) {
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
                parseBulletList(node, blocks, depth = 0)
            }

            is OrderedList -> {
                parseOrderedList(node, blocks, depth = 0)
            }

            is ThematicBreak -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }

            is Paragraph -> {
                val raw = renderInlineText(node).trim()

                // Check for block math: $$...$$
                val mathMatch = Regex("^\\$\\$([\\s\\S]*?)\\$\\$$").find(raw)
                if (mathMatch != null && !mathMatch.groupValues[1].contains("$$")) {
                    blocks.add(MarkdownBlock.MathBlock(expression = mathMatch.groupValues[1].trim()))
                    return
                }

                // Check for Definition List: Term\n: Definition
                val lines = raw.lines()
                if (lines.size >= 2 && lines.drop(1).any { it.trimStart().startsWith(":") }) {
                    val defItems = parseDefinitionList(raw)
                    if (defItems.isNotEmpty()) {
                        blocks.add(MarkdownBlock.DefinitionList(items = defItems))
                        return
                    }
                }

                // Check for Obsidian image embed: ![[image.png]] or ![[image.png|alt]]
                val obsidianImgMatch = Regex("^!\\[\\[([^|\\]]+)(?:\\|([^\\]]+))?\\]\\]$").find(raw)
                if (obsidianImgMatch != null) {
                    val imgUrl = obsidianImgMatch.groupValues[1].trim()
                    val alt = obsidianImgMatch.groupValues[2].trim()
                    blocks.add(MarkdownBlock.Image(alt = alt, url = imgUrl))
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
                    val anchorMatches =
                        Regex("<(?:a|span)\\s+(?:id|name)=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(raw)
                    val anchors = anchorMatches.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
                    blocks.add(MarkdownBlock.Paragraph(text = raw, anchors = anchors))
                }
            }

            is HtmlBlock -> {
                val raw = (node.literal ?: "").trim()

                // Check for <details> ... </details>
                val detailsMatch =
                    Regex("<details(?:\\s+open)?>([\\s\\S]*?)</details>", RegexOption.IGNORE_CASE).find(raw)
                if (detailsMatch != null) {
                    val isOpen = raw.contains("open", ignoreCase = true)
                    val inner = detailsMatch.groupValues[1].trim()

                    val summaryMatch = Regex("<summary>([\\s\\S]*?)</summary>", RegexOption.IGNORE_CASE).find(inner)
                    val summary = summaryMatch?.groupValues?.get(1)?.trim() ?: "Details"
                    val contentBody =
                        if (summaryMatch != null) {
                            inner.substring(0, summaryMatch.range.first) + inner.substring(summaryMatch.range.last + 1)
                        } else {
                            inner
                        }.trim()

                    val subBlocks = if (contentBody.isNotEmpty()) parse(contentBody) else emptyList()
                    blocks.add(MarkdownBlock.Details(summary = summary, isOpen = isOpen, content = subBlocks))
                    return
                }

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
                        val anchorMatches =
                            Regex("<(?:a|span)\\s+(?:id|name)=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(raw)
                        val anchors = anchorMatches.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

                        val stripped = raw.replace(Regex("<div[^>]*>|</div>|<center>|</center>|<p[^>]*>|</p>", RegexOption.IGNORE_CASE), "").trim()
                        if (stripped.isNotEmpty()) {
                            blocks.add(MarkdownBlock.Paragraph(text = stripped, anchors = anchors))
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun parseDefinitionList(raw: String): List<DefinitionItem> {
        val items = mutableListOf<DefinitionItem>()
        val lines = raw.lines()
        var currentTerm: String? = null
        val currentDefs = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(":")) {
                val def = trimmed.removePrefix(":").trim()
                if (def.isNotEmpty()) {
                    currentDefs.add(def)
                }
            } else if (trimmed.isNotEmpty()) {
                if (currentTerm != null && currentDefs.isNotEmpty()) {
                    items.add(DefinitionItem(currentTerm, currentDefs.toList()))
                    currentDefs.clear()
                }
                currentTerm = trimmed
            }
        }
        if (currentTerm != null && currentDefs.isNotEmpty()) {
            items.add(DefinitionItem(currentTerm, currentDefs.toList()))
        }
        return items
    }

    private fun parseBulletList(list: BulletList, blocks: MutableList<MarkdownBlock>, depth: Int) {
        var item: Node? = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                parseListItem(item, blocks, ordered = false, index = 0, depth = depth)
            }
            item = item.next
        }
    }

    private fun parseOrderedList(list: OrderedList, blocks: MutableList<MarkdownBlock>, depth: Int) {
        var item: Node? = list.firstChild
        var idx = list.startNumber
        while (item != null) {
            if (item is ListItem) {
                parseListItem(item, blocks, ordered = true, index = idx, depth = depth)
                idx++
            }
            item = item.next
        }
    }

    private fun parseListItem(
        item: ListItem,
        blocks: MutableList<MarkdownBlock>,
        ordered: Boolean,
        index: Int,
        depth: Int,
    ) {
        var child = item.firstChild
        val inlineParts = mutableListOf<String>()
        val nestedLists = mutableListOf<Node>()

        while (child != null) {
            when (child) {
                is Paragraph -> inlineParts.add(renderInlineText(child).trim())
                is BulletList, is OrderedList -> nestedLists.add(child)
                else -> inlineParts.add(renderInlineText(child).trim())
            }
            child = child.next
        }

        val text = inlineParts.joinToString(" ").trim()
        val taskMatch = Regex("^\\[([ xX])\\]\\s*(.*)$").find(text)
        if (taskMatch != null) {
            val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            val content = taskMatch.groupValues[2]
            blocks.add(MarkdownBlock.TaskItem(isChecked = isChecked, text = content, depth = depth))
        } else {
            blocks.add(MarkdownBlock.ListItem(ordered = ordered, index = index, text = text, depth = depth))
        }

        for (nested in nestedLists) {
            when (nested) {
                is BulletList -> parseBulletList(nested, blocks, depth = depth + 1)
                is OrderedList -> parseOrderedList(nested, blocks, depth = depth + 1)
            }
        }
    }

    private fun slugify(text: String): String {
        return MarkdownScrollController.slugify(text)
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

        val anchorMatches =
            Regex("<(?:a|span)\\s+(?:id|name)=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(rawText)
        val anchors = anchorMatches.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

        return MarkdownBlock.Blockquote(text = rawText, anchors = anchors)
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
