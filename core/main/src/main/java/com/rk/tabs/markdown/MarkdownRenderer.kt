package com.rk.tabs.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Modern Jetpack Compose UI Renderer for Markdown content.
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

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            RenderBlock(
                block = block,
                currentFile = currentFile,
                projectRoot = projectRoot,
                viewModel = viewModel,
                baseDirPath = baseDirPath,
                imageLoader = imageLoader,
            )
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
        is MarkdownBlock.Heading -> RenderHeading(block)
        is MarkdownBlock.Paragraph -> RenderParagraph(block.text, currentFile, projectRoot, viewModel)
        is MarkdownBlock.CodeBlock -> RenderCodeBlock(block)
        is MarkdownBlock.Alert -> RenderAlert(block, currentFile, projectRoot, viewModel, baseDirPath, imageLoader)
        is MarkdownBlock.Table -> RenderTable(block)
        is MarkdownBlock.Blockquote -> RenderBlockquote(block.text)
        is MarkdownBlock.ListItem -> RenderListItem(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.TaskItem -> RenderTaskItem(block, currentFile, projectRoot, viewModel)
        is MarkdownBlock.Image -> RenderImage(block, baseDirPath, imageLoader)
        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun RenderHeading(heading: MarkdownBlock.Heading) {
    val style =
        when (heading.level) {
            1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp)
            2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 23.sp)
            3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp)
            4 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            5 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

    val color =
        when (heading.level) {
            1 -> MaterialTheme.colorScheme.primary
            2 -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }

    Column(modifier = Modifier.fillMaxWidth().padding(top = if (heading.level <= 2) 8.dp else 4.dp)) {
        Text(text = heading.text, style = style, color = color)
        if (heading.level <= 2) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (heading.level == 1) 0.5f else 0.25f),
                thickness = if (heading.level == 1) 2.dp else 1.dp,
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

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                handleLinkClick(annotation.item, currentFile, projectRoot, viewModel, context)
            }
        },
    )
}

@Composable
private fun RenderCodeBlock(codeBlock: MarkdownBlock.CodeBlock) {
    var copied by remember { mutableStateOf(false) }

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
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = codeBlock.language.ifBlank { "TEXT" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )

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

            // Code Content
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = codeBlock.code,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
    val accentColor =
        when (alert.type) {
            AlertType.NOTE -> Color(0xFF2196F3)
            AlertType.TIP -> Color(0xFF4CAF50)
            AlertType.IMPORTANT -> Color(0xFF9C27B0)
            AlertType.WARNING -> Color(0xFFFF9800)
            AlertType.CAUTION -> Color(0xFFF44336)
        }

    val containerColor = accentColor.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            // Left colored accent border line
            Box(
                modifier =
                    Modifier.width(4.dp)
                        .height(IntrinsicSize.Max)
                        .background(accentColor, RoundedCornerShape(2.dp)),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Title Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (alert.type) {
                        AlertType.NOTE -> Icon(Icons.Default.Info, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                        AlertType.TIP -> Icon(painterResource(drawables.bolt), alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                        AlertType.IMPORTANT -> Icon(Icons.Default.Star, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                        AlertType.WARNING -> Icon(Icons.Default.Warning, alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                        AlertType.CAUTION -> Icon(painterResource(drawables.error), alert.title, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                    )
                }

                // Body content
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

@Composable
private fun RenderTable(table: MarkdownBlock.Table) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(2.dp)) {
                // Header Row
                Row(
                    modifier =
                        Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        ).padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    table.headers.forEachIndexed { colIdx, header ->
                        val align = table.alignments.getOrElse(colIdx) { TextAlign.Left }
                        Box(
                            modifier =
                                Modifier.widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Data Rows
                table.rows.forEachIndexed { rowIdx, row ->
                    val rowBg =
                        if (rowIdx % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)

                    Row(
                        modifier = Modifier.background(rowBg).padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { colIdx, cell ->
                            val align = table.alignments.getOrElse(colIdx) { TextAlign.Left }
                            Box(
                                modifier =
                                    Modifier.widthIn(min = 100.dp, max = 220.dp)
                                        .padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = cell,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = align,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (rowIdx < table.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderBlockquote(text: String) {
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

            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val indent = (item.depth * 16).dp
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
            modifier = Modifier.width(if (item.ordered) 28.dp else 16.dp),
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
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isChecked,
            onCheckedChange = null,
            modifier = Modifier.size(24.dp).padding(end = 6.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        RenderParagraph(task.text, currentFile, projectRoot, viewModel)
    }
}

@Composable
private fun RenderImage(
    image: MarkdownBlock.Image,
    baseDirPath: String?,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    val model = remember(image.url, baseDirPath) { resolveImageModel(image.url, baseDirPath, context) }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = model,
                contentDescription = image.alt,
                imageLoader = imageLoader,
                contentScale = ContentScale.Inside,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )

            if (image.alt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = image.alt,
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun resolveImageModel(rawUrl: String, baseDirPath: String?, context: Context): Any {
    val trimmed = rawUrl.trim()
    return when {
        trimmed.startsWith("data:") -> {
            try {
                val payload = trimmed.substringAfter("base64,")
                android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                trimmed
            }
        }
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("file://") -> File(trimmed.removePrefix("file://"))
        trimmed.startsWith("/") -> File(trimmed)
        else -> {
            try {
                val cleanUrl = trimmed.substringBefore('#').substringBefore('?')
                val decodedPath = URLDecoder.decode(cleanUrl, "UTF-8")
                if (baseDirPath != null) {
                    File(File(baseDirPath), decodedPath).canonicalFile
                } else {
                    trimmed
                }
            } catch (_: Exception) {
                trimmed
            }
        }
    }
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
            // Anchor link
        }

        else -> {
            DefaultScope.launch(Dispatchers.IO) {
                try {
                    val cleanUrl = url.substringBefore('#').substringBefore('?')
                    val decodedPath = URLDecoder.decode(cleanUrl, "UTF-8")

                    val targetFile: FileObject? =
                        when {
                            url.startsWith("/") -> FileWrapper(File(decodedPath))
                            url.startsWith("file://") -> FileWrapper(File(decodedPath.removePrefix("file://")))
                            else -> {
                                val parent = currentFile.getParentFile()
                                if (parent != null) {
                                    val parentFile = File(parent.getAbsolutePath())
                                    val resolved = File(parentFile, decodedPath).canonicalFile
                                    FileWrapper(resolved)
                                } else null
                            }
                        }

                    if (targetFile != null && targetFile.exists()) {
                        withContext(Dispatchers.Main) {
                            val tab =
                                TabRegistry.getTab(
                                    file = targetFile,
                                    projectRoot = projectRoot,
                                    viewModel = viewModel,
                                    readOnly = false,
                                    customTitle = null,
                                )
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
