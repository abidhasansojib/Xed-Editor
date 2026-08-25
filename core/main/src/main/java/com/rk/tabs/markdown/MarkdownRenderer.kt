package com.rk.tabs.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.DefaultScope
import com.rk.activities.main.MainViewModel
import com.rk.droidspaces.DroidspacesFileObject
import com.rk.droidspaces.DroidspacesShell
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.tabs.base.TabRegistry
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

/**
 * Modern Jetpack Compose UI Renderer for Obsidian & GFM Markdown.
 */
@Composable
fun MarkdownView(
    blocks: List<MarkdownBlock>,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    baseDirPath: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader =
        remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(SvgDecoder.Factory())
                }
                .crossfade(true)
                .build()
        }

    val regularBlocks = remember(blocks) { blocks.filter { it !is MarkdownBlock.Footnote } }
    val footnotes = remember(blocks) { blocks.filterIsInstance<MarkdownBlock.Footnote>() }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        regularBlocks.forEach { block ->
            RenderBlock(
                block = block,
                currentFile = currentFile,
                projectRoot = projectRoot,
                viewModel = viewModel,
                baseDirPath = baseDirPath,
                imageLoader = imageLoader,
            )
        }

        if (footnotes.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "Footnotes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            footnotes.forEach { fn ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "[${fn.id}]: ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                    )
                    RenderParagraph(fn.text, currentFile, projectRoot, viewModel)
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: MarkdownBlock,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    baseDirPath: String?,
    imageLoader: ImageLoader,
) {
    when (block) {
        is MarkdownBlock.Heading -> RenderHeading(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.Paragraph -> RenderParagraph(block.text, currentFile, projectRoot, viewModel)
        is MarkdownBlock.CodeBlock -> RenderCodeBlock(block)
        is MarkdownBlock.Alert -> RenderAlert(block, currentFile, projectRoot, viewModel, baseDirPath, imageLoader)
        is MarkdownBlock.Table -> RenderTable(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.Blockquote -> RenderBlockquote(block.text, currentFile, projectRoot, viewModel)
        is MarkdownBlock.ListItem -> RenderListItem(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.TaskItem -> RenderTaskItem(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.Image -> RenderImage(block, currentFile, projectRoot, viewModel, baseDirPath, imageLoader)
        is MarkdownBlock.MathBlock -> RenderMathBlock(block)
        is MarkdownBlock.Footnote -> {}
        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RenderHeading(
    heading: MarkdownBlock.Heading,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
) {
    val style =
        when (heading.level) {
            1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
            4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            5 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

    val primaryColor =
        when (heading.level) {
            1 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }

    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    val annotated =
        remember(heading.text, primaryColor, codeBgColor) {
            InlineMarkdown.parse(
                text = heading.text,
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                codeTextColor = codeTextColor,
            )
        }

    Column(modifier = Modifier.fillMaxWidth().padding(top = if (heading.level <= 2) 6.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (heading.level == 1 || heading.level == 2) {
                Box(
                    modifier =
                        Modifier.width(3.5.dp)
                            .height(if (heading.level == 1) 22.dp else 18.dp)
                            .background(
                                color = if (heading.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            ClickableText(
                text = annotated,
                style = style.copy(color = primaryColor),
                onClick = { offset ->
                    annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        handleLinkClick(annotation.item, currentFile, projectRoot, viewModel, context)
                        return@ClickableText
                    }
                    annotated.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        handleWikilinkClick(annotation.item, currentFile, projectRoot, viewModel, context)
                        return@ClickableText
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (heading.level <= 2) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (heading.level == 1) 0.4f else 0.2f),
                thickness = if (heading.level == 1) 1.5.dp else 1.dp,
            )
        }
    }
}

@Composable
private fun RenderParagraph(
    text: String,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    isStrikethrough: Boolean = false,
    isSubdued: Boolean = false,
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val annotated =
        remember(text, primaryColor, codeBgColor) {
            InlineMarkdown.parse(
                text = text,
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                codeTextColor = codeTextColor,
            )
        }

    val textColor =
        if (isSubdued) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurface

    val textDecoration =
        if (isStrikethrough) androidx.compose.ui.text.style.TextDecoration.LineThrough
        else androidx.compose.ui.text.style.TextDecoration.None

    ClickableText(
        text = annotated,
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                lineHeight = 23.sp,
                textDecoration = textDecoration,
            ),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                handleLinkClick(annotation.item, currentFile, projectRoot, viewModel, context)
                return@ClickableText
            }
            annotated.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset).firstOrNull()?.let { annotation ->
                handleWikilinkClick(annotation.item, currentFile, projectRoot, viewModel, context)
                return@ClickableText
            }
        },
    )
}

@Composable
private fun RenderCodeBlock(codeBlock: MarkdownBlock.CodeBlock) {
    var copied by remember { mutableStateOf(false) }
    var wrapCode by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column {
            // Header Bar
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = codeBlock.language.ifBlank { "CODE" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Wrap lines toggle button for mobile display
                    IconButton(
                        onClick = { wrapCode = !wrapCode },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(
                            text = if (wrapCode) "⇄" else "↵",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (wrapCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            ClipboardUtils.copyText("Code", codeBlock.code)
                            copied = true
                            toast("Code copied to clipboard")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        if (copied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Copied",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                painter = painterResource(drawables.copy),
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Code Content
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(if (!wrapCode) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = codeBlock.code,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RenderMathBlock(mathBlock: MarkdownBlock.MathBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "MATH / EQUATION",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mathBlock.expression,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RenderAlert(
    alert: MarkdownBlock.Alert,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    baseDirPath: String?,
    imageLoader: ImageLoader,
) {
    var isExpanded by remember { mutableStateOf(!alert.defaultFolded) }

    val accentColor =
        when (alert.type) {
            AlertType.NOTE, AlertType.INFO, AlertType.TODO -> Color(0xFF0288D1)
            AlertType.TIP, AlertType.HINT, AlertType.IMPORTANT -> Color(0xFF00897B)
            AlertType.SUCCESS, AlertType.CHECK, AlertType.DONE -> Color(0xFF43A047)
            AlertType.QUESTION, AlertType.HELP, AlertType.FAQ -> Color(0xFFFB8C00)
            AlertType.WARNING, AlertType.CAUTION, AlertType.ATTENTION -> Color(0xFFE64A19)
            AlertType.FAILURE, AlertType.FAIL, AlertType.MISSING -> Color(0xFFD32F2F)
            AlertType.DANGER, AlertType.ERROR, AlertType.BUG -> Color(0xFFC2185B)
            AlertType.EXAMPLE -> Color(0xFF7B1FA2)
            AlertType.QUOTE, AlertType.CITE -> Color(0xFF757575)
        }

    val containerColor = accentColor.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row (Clickable if foldable)
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(if (alert.isFoldable) Modifier.clickable { isExpanded = !isExpanded } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left colored accent border line
                Box(
                    modifier =
                        Modifier.width(4.dp)
                            .height(20.dp)
                            .background(accentColor, RoundedCornerShape(2.dp)),
                )

                Spacer(modifier = Modifier.width(8.dp))

                when (alert.type) {
                    AlertType.NOTE, AlertType.INFO, AlertType.TODO -> Icon(Icons.Default.Info, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    AlertType.TIP, AlertType.HINT -> Icon(painterResource(drawables.bolt), alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    AlertType.IMPORTANT -> Icon(Icons.Default.Star, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    AlertType.SUCCESS, AlertType.CHECK, AlertType.DONE -> Icon(Icons.Default.Check, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    AlertType.WARNING, AlertType.CAUTION, AlertType.ATTENTION -> Icon(Icons.Default.Warning, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    else -> Icon(painterResource(drawables.error), alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    modifier = Modifier.weight(1f),
                )

                if (alert.isFoldable) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = "Toggle callout",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Body content with animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alert.content.forEach { subBlock ->
                        RenderBlock(
                            block = subBlock,
                            currentFile = currentFile,
                            projectRoot = projectRoot,
                            viewModel = viewModel,
                            baseDirPath = baseDirPath,
                            imageLoader = imageLoader,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderTable(
    table: MarkdownBlock.Table,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(1.dp)) {
                // Header Row
                Row(
                    modifier =
                        Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        ).padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    table.headers.forEachIndexed { colIdx, header ->
                        val align = table.alignments.getOrElse(colIdx) { TextAlign.Left }
                        val annotatedHeader =
                            remember(header, primaryColor, codeBgColor) {
                                InlineMarkdown.parse(header, primaryColor, codeBgColor, codeTextColor)
                            }
                        Box(
                            modifier =
                                Modifier.widthIn(min = 80.dp, max = 280.dp)
                                    .padding(horizontal = 8.dp),
                        ) {
                            ClickableText(
                                text = annotatedHeader,
                                style =
                                    MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = align,
                                        fontSize = 13.5.sp,
                                    ),
                                onClick = { offset ->
                                    annotatedHeader.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let {
                                        handleLinkClick(it.item, currentFile, projectRoot, viewModel, context)
                                    }
                                    annotatedHeader.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset).firstOrNull()?.let {
                                        handleWikilinkClick(it.item, currentFile, projectRoot, viewModel, context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Data Rows with zebra striping for mobile
                table.rows.forEachIndexed { rowIdx, row ->
                    val rowBg =
                        if (rowIdx % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)

                    Row(
                        modifier = Modifier.background(rowBg).padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { colIdx, cell ->
                            val align = table.alignments.getOrElse(colIdx) { TextAlign.Left }
                            val annotatedCell =
                                remember(cell, primaryColor, codeBgColor) {
                                    InlineMarkdown.parse(cell, primaryColor, codeBgColor, codeTextColor)
                                }
                            Box(
                                modifier =
                                    Modifier.widthIn(min = 80.dp, max = 280.dp)
                                        .padding(horizontal = 8.dp),
                            ) {
                                ClickableText(
                                    text = annotatedCell,
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = align,
                                            fontSize = 13.5.sp,
                                        ),
                                    onClick = { offset ->
                                        annotatedCell.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let {
                                            handleLinkClick(it.item, currentFile, projectRoot, viewModel, context)
                                        }
                                        annotatedCell.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset).firstOrNull()?.let {
                                            handleWikilinkClick(it.item, currentFile, projectRoot, viewModel, context)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (rowIdx < table.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderBlockquote(
    text: String,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
            Box(
                modifier =
                    Modifier.width(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )

            Spacer(modifier = Modifier.width(10.dp))

            RenderParagraph(text, currentFile, projectRoot, viewModel)
        }
    }
}

@Composable
private fun RenderListItem(
    item: MarkdownBlock.ListItem,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
) {
    val indent = (item.depth * 14).dp
    val bullet =
        if (item.ordered) "${item.index}."
        else when (item.depth % 3) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }

    Row(modifier = Modifier.padding(start = indent, top = 2.dp, bottom = 2.dp)) {
        Text(
            text = bullet,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.width(if (item.ordered) 26.dp else 16.dp),
        )
        RenderParagraph(item.text, currentFile, projectRoot, viewModel)
    }
}

@Composable
private fun RenderTaskItem(
    task: MarkdownBlock.TaskItem,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
) {
    var isChecked by remember(task.isChecked) { mutableStateOf(task.isChecked) }
    val indent = (task.depth * 14).dp

    Row(
        modifier = Modifier.padding(start = indent, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { checked ->
                isChecked = checked
            },
            modifier = Modifier.size(22.dp).padding(end = 4.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        RenderParagraph(
            text = task.text,
            currentFile = currentFile,
            projectRoot = projectRoot,
            viewModel = viewModel,
            isStrikethrough = isChecked,
            isSubdued = isChecked,
        )
    }
}

@Composable
private fun RenderImage(
    image: MarkdownBlock.Image,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    baseDirPath: String?,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    var imageModel by remember { mutableStateOf<Any?>(null) }
    var resolvedFileObject by remember { mutableStateOf<FileObject?>(null) }
    var isLoadingModel by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(image.url, currentFile, baseDirPath) {
        isLoadingModel = true
        loadFailed = false
        try {
            val resolved = resolveImageModelAsync(image.url, currentFile, baseDirPath, context)
            imageModel = resolved.first
            resolvedFileObject = resolved.second
            if (resolved.first == null) {
                loadFailed = true
            }
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            isLoadingModel = false
        }
    }

    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (resolvedFileObject != null) {
                        val imageTab = TabRegistry.getTab(
                            file = resolvedFileObject!!,
                            projectRoot = projectRoot,
                            viewModel = viewModel,
                            readOnly = true,
                            customTitle = null,
                        )
                        viewModel.tabManager.addTab(imageTab, switchToTab = true)
                    } else if (imageModel is File && (imageModel as File).exists()) {
                        val imageTab = TabRegistry.getTab(
                            file = FileWrapper(imageModel as File),
                            projectRoot = projectRoot,
                            viewModel = viewModel,
                            readOnly = true,
                            customTitle = null,
                        )
                        viewModel.tabManager.addTab(imageTab, switchToTab = true)
                    } else if (image.url.startsWith("http://") || image.url.startsWith("https://")) {
                        try {
                            val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
                            customTabsIntent.launchUrl(context, Uri.parse(image.url))
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(image.url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLoadingModel) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (loadFailed || imageModel == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(drawables.error),
                        contentDescription = "Image not found",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (image.alt.isNotBlank()) image.alt else "Image not found",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = image.url,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    contentDescription = image.alt,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    onError = { loadFailed = true },
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 60.dp, max = 360.dp)
                            .clip(RoundedCornerShape(8.dp)),
                )

                if (image.alt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = image.alt,
                        style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private suspend fun resolveImageModelAsync(
    rawUrl: String,
    currentFile: FileObject,
    baseDirPath: String?,
    context: Context,
): Pair<Any?, FileObject?> = withContext(Dispatchers.IO) {
    val trimmed = rawUrl.trim()
    when {
        trimmed.startsWith("data:") -> {
            try {
                val payload = trimmed.substringAfter("base64,")
                Pair(android.util.Base64.decode(payload, android.util.Base64.DEFAULT), null)
            } catch (_: Exception) {
                Pair(trimmed, null)
            }
        }
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
            Pair(trimmed, null)
        }
        trimmed.startsWith("file://", ignoreCase = true) -> {
            val f = File(trimmed.removePrefix("file://").removePrefix("file:"))
            if (f.exists()) Pair(f, FileWrapper(f)) else Pair(null, null)
        }
        currentFile is DroidspacesFileObject -> {
            val containerName = currentFile.containerName
            val currentParentPath = currentFile.containerPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

            val cleanUrl = trimmed.substringBefore('#').substringBefore('?')
            val decodedRelPath = runCatching { URLDecoder.decode(cleanUrl, "UTF-8") }.getOrDefault(cleanUrl)

            val candidateRelPaths = listOf(
                decodedRelPath,
                "assets/$decodedRelPath",
                "attachments/$decodedRelPath",
                "images/$decodedRelPath",
                "img/$decodedRelPath",
                "docs/$decodedRelPath",
                "doc/$decodedRelPath",
            )

            for (rel in candidateRelPaths) {
                val fullPath = normalizeContainerPath(currentParentPath, rel)
                if (DroidspacesShell.testPathExists(containerName, fullPath)) {
                    try {
                        val bytes = DroidspacesShell.getInputStream(containerName, fullPath).use {
                            it.readBytes()
                        }
                        if (bytes.isNotEmpty()) {
                            val targetFileObject = DroidspacesFileObject(
                                containerName = containerName,
                                containerPath = fullPath,
                                isFileFlag = true,
                                fileLength = bytes.size.toLong(),
                            )
                            return@withContext Pair(bytes, targetFileObject)
                        }
                    } catch (_: Exception) {}
                }
            }
            Pair(null, null)
        }
        trimmed.startsWith("/") -> {
            val f = File(trimmed)
            if (f.exists()) Pair(f, FileWrapper(f)) else Pair(null, null)
        }
        else -> {
            val cleanUrl = trimmed.substringBefore('#').substringBefore('?')
            val decodedRelPath = runCatching { URLDecoder.decode(cleanUrl, "UTF-8") }.getOrDefault(cleanUrl)

            if (baseDirPath != null) {
                val base = File(baseDirPath)
                val direct = File(base, decodedRelPath).canonicalFile
                if (direct.exists()) return@withContext Pair(direct, FileWrapper(direct))

                val assetsCandidate = File(File(base, "assets"), decodedRelPath).canonicalFile
                if (assetsCandidate.exists()) return@withContext Pair(assetsCandidate, FileWrapper(assetsCandidate))

                val attachmentsCandidate = File(File(base, "attachments"), decodedRelPath).canonicalFile
                if (attachmentsCandidate.exists()) return@withContext Pair(attachmentsCandidate, FileWrapper(attachmentsCandidate))

                val imagesCandidate = File(File(base, "images"), decodedRelPath).canonicalFile
                if (imagesCandidate.exists()) return@withContext Pair(imagesCandidate, FileWrapper(imagesCandidate))

                val parent = base.parentFile
                if (parent != null) {
                    val parentDirect = File(parent, decodedRelPath).canonicalFile
                    if (parentDirect.exists()) return@withContext Pair(parentDirect, FileWrapper(parentDirect))
                }

                if (direct.exists()) return@withContext Pair(direct, FileWrapper(direct))
            }
            Pair(null, null)
        }
    }
}

private fun normalizeContainerPath(parentDir: String, relativePath: String): String {
    val raw = if (relativePath.startsWith("/")) {
        relativePath
    } else if (parentDir == "/") {
        "/$relativePath"
    } else {
        "$parentDir/$relativePath"
    }

    val segments = raw.split("/").filter { it.isNotEmpty() }
    val stack = mutableListOf<String>()
    for (seg in segments) {
        if (seg == ".") continue
        if (seg == "..") {
            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        } else {
            stack.add(seg)
        }
    }
    return "/" + stack.joinToString("/")
}

private fun handleLinkClick(
    rawUrl: String,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    context: Context,
) {
    val url = rawUrl.trim()

    when {
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
                customTabsIntent.launchUrl(context, Uri.parse(url))
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    toast("Could not open URL: $url")
                }
            }
        }

        url.startsWith("mailto:", ignoreCase = true) || url.startsWith("tel:", ignoreCase = true) -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (_: Exception) {
                toast("Could not open link: $url")
            }
        }

        url.startsWith("#") -> {
            // Heading anchor
        }

        else -> {
            DefaultScope.launch(Dispatchers.IO) {
                try {
                    val cleanUrl = url.substringBefore('#').substringBefore('?')
                    val decodedPath = URLDecoder.decode(cleanUrl, "UTF-8")

                    if (currentFile is DroidspacesFileObject) {
                        val containerName = currentFile.containerName
                        val currentParentPath = currentFile.containerPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
                        val fullPath = normalizeContainerPath(currentParentPath, decodedPath)
                        if (DroidspacesShell.testPathExists(containerName, fullPath)) {
                            val targetObj = DroidspacesFileObject(containerName, fullPath)
                            withContext(Dispatchers.Main) {
                                val isMd = fullPath.endsWith(".md", ignoreCase = true) ||
                                    fullPath.endsWith(".markdown", ignoreCase = true)
                                val tab =
                                    if (isMd) {
                                        MarkdownTab(
                                            file = targetObj,
                                            projectRoot = projectRoot,
                                            viewModel = viewModel,
                                        )
                                    } else {
                                        TabRegistry.getTab(
                                            file = targetObj,
                                            projectRoot = projectRoot,
                                            viewModel = viewModel,
                                            readOnly = false,
                                            customTitle = null,
                                        )
                                    }
                                viewModel.tabManager.addTab(tab, switchToTab = true)
                            }
                            return@launch
                        }
                    }

                    var targetFile: File? = null

                    when {
                        url.startsWith("file://", ignoreCase = true) -> {
                            val path = decodedPath.removePrefix("file://").removePrefix("file:")
                            val f = File(path)
                            if (f.exists()) targetFile = f
                        }
                        url.startsWith("/") -> {
                            val f = File(decodedPath)
                            if (f.exists()) targetFile = f
                        }
                        else -> {
                            // 1. Check relative to current file directory
                            val parent = currentFile.getParentFile()
                            if (parent != null) {
                                val candidate = File(File(parent.getAbsolutePath()), decodedPath).canonicalFile
                                if (candidate.exists()) {
                                    targetFile = candidate
                                }
                            }

                            // 2. Check relative to project root
                            if (targetFile == null && projectRoot != null) {
                                val candidate = File(File(projectRoot.getAbsolutePath()), decodedPath).canonicalFile
                                if (candidate.exists()) {
                                    targetFile = candidate
                                }
                            }

                            // 3. Search project directory tree by filename
                            if (targetFile == null && projectRoot != null) {
                                val fileName = File(decodedPath).name
                                val found = File(projectRoot.getAbsolutePath()).walk().firstOrNull {
                                    it.isFile && it.name.equals(fileName, ignoreCase = true)
                                }
                                if (found != null) {
                                    targetFile = found
                                }
                            }
                        }
                    }

                    if (targetFile != null && targetFile.exists()) {
                        withContext(Dispatchers.Main) {
                            val isMd = targetFile.name.endsWith(".md", ignoreCase = true) ||
                                targetFile.name.endsWith(".markdown", ignoreCase = true)
                            val tab =
                                if (isMd) {
                                    MarkdownTab(
                                        file = FileWrapper(targetFile),
                                        projectRoot = projectRoot,
                                        viewModel = viewModel,
                                    )
                                } else {
                                    TabRegistry.getTab(
                                        file = FileWrapper(targetFile),
                                        projectRoot = projectRoot,
                                        viewModel = viewModel,
                                        readOnly = false,
                                        customTitle = null,
                                    )
                                }
                            viewModel.tabManager.addTab(tab, switchToTab = true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            toast("File not found: $decodedPath")
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        toast("Failed to open link: $url")
                    }
                }
            }
        }
    }
}

private fun handleWikilinkClick(
    targetName: String,
    currentFile: FileObject,
    projectRoot: FileObject?,
    viewModel: MainViewModel,
    context: Context,
) {
    val cleanName = targetName.trim().substringBefore('#')
    DefaultScope.launch(Dispatchers.IO) {
        try {
            if (currentFile is DroidspacesFileObject) {
                val containerName = currentFile.containerName
                val currentParentPath = currentFile.containerPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
                val candidatePaths = listOf(
                    normalizeContainerPath(currentParentPath, cleanName),
                    normalizeContainerPath(currentParentPath, "$cleanName.md"),
                    normalizeContainerPath(currentParentPath, "$cleanName.markdown"),
                )
                for (p in candidatePaths) {
                    if (DroidspacesShell.testPathExists(containerName, p)) {
                        val targetObj = DroidspacesFileObject(containerName, p)
                        withContext(Dispatchers.Main) {
                            val isMd = p.endsWith(".md", ignoreCase = true) || p.endsWith(".markdown", ignoreCase = true)
                            val tab = if (isMd) {
                                MarkdownTab(
                                    file = targetObj,
                                    projectRoot = projectRoot,
                                    viewModel = viewModel,
                                )
                            } else {
                                TabRegistry.getTab(
                                    file = targetObj,
                                    projectRoot = projectRoot,
                                    viewModel = viewModel,
                                    readOnly = false,
                                    customTitle = null,
                                )
                            }
                            viewModel.tabManager.addTab(tab, switchToTab = true)
                        }
                        return@launch
                    }
                }
            }

            var foundFile: File? = null

            // 1. Check parent folder
            val parentPath = currentFile.getParentFile()?.getAbsolutePath()
            if (parentPath != null) {
                val parentDir = File(parentPath)
                val direct = File(parentDir, cleanName)
                val directMd = File(parentDir, "$cleanName.md")
                if (direct.exists()) foundFile = direct
                else if (directMd.exists()) foundFile = directMd
            }

            // 2. Check project root if not found
            if (foundFile == null && projectRoot != null) {
                val rootFile = File(projectRoot.getAbsolutePath())
                foundFile = rootFile.walk().firstOrNull { it.isFile && (it.name.equals(cleanName, ignoreCase = true) || it.name.equals("$cleanName.md", ignoreCase = true)) }
            }

            if (foundFile != null && foundFile.exists()) {
                withContext(Dispatchers.Main) {
                    val isMd = foundFile.name.endsWith(".md", ignoreCase = true) ||
                        foundFile.name.endsWith(".markdown", ignoreCase = true)
                    val tab =
                        if (isMd) {
                            MarkdownTab(
                                file = FileWrapper(foundFile),
                                projectRoot = projectRoot,
                                viewModel = viewModel,
                            )
                        } else {
                            TabRegistry.getTab(
                                file = FileWrapper(foundFile),
                                projectRoot = projectRoot,
                                viewModel = viewModel,
                                readOnly = false,
                                customTitle = null,
                            )
                        }
                    viewModel.tabManager.addTab(tab, switchToTab = true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    toast("Note not found: $cleanName")
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                toast("Could not open note: $cleanName")
            }
        }
    }
}
