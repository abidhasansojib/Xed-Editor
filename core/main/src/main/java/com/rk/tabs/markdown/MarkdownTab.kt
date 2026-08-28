package com.rk.tabs.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.session.MarkdownPreviewTabState
import com.rk.activities.main.session.TabState
import com.rk.file.FileObject
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern Jetpack Compose Markdown Preview Tab optimized for mobile displays.
 * Features:
 * - 100% native Compose Material 3 UI with zero webview overhead and instant offline rendering.
 * - Mobile-optimized layout: Responsive image scaling with tap-to-open, wrap-lines code blocks, and scrollable tables.
 * - Rich GitHub-Flavored Markdown (GFM) elements: Tables, Task Lists with interactive check/strikethrough, Blockquotes, Headings.
 * - Table of Contents (Outline) & Document stats (word count, reading time).
 * - Code Blocks with language badges, copy-to-clipboard button, and wrap toggle.
 * - GitHub-style Alert Callouts ([!NOTE], [!TIP], [!IMPORTANT], [!WARNING], [!CAUTION]).
 * - Universal image loading via Coil (Remote URLs, local/relative paths, SVGs, Base64 data URIs).
 * - Interactive link navigation (Chrome Custom Tabs for web, in-app redirection for .md and source files).
 */
class MarkdownTab(
    override val file: FileObject,
    var projectRoot: FileObject? = null,
    val viewModel: MainViewModel,
    val initialAnchor: String? = null,
) : Tab() {
    override val name: String = "Markdown preview"
    override val icon: ImageVector
        get() = XedIcons.Menu_book
    override var title: String by mutableStateOf(file.getName())
    override val showGlobalActions: Boolean = true

    private var showOutlineDialog by mutableStateOf(false)
    private var currentHeadings by mutableStateOf<List<MarkdownBlock.Heading>>(emptyList())
    private var docWordCount by mutableStateOf(0)
    private var docCharCount by mutableStateOf(0)

    override fun getState(): TabState {
        return MarkdownPreviewTabState(file, projectRoot)
    }

    @Composable
    override fun RowScope.Actions() {
        val scope = rememberCoroutineScope()

        IconButton(
            onClick = {
                val editorTab =
                    viewModel.editorManager.createEditorTab(
                        file = file,
                        projectRoot = projectRoot,
                        isReadOnly = false,
                    )
                viewModel.tabManager.replaceTab(this@MarkdownTab, editorTab)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(strings.edit_mode),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = {
                showOutlineDialog = true
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Outline & Stats",
            )
        }

        IconButton(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val content =
                        runCatching {
                            file.getInputStream().bufferedReader().use { it.readText() }
                        }.getOrNull()
                    if (content != null) {
                        withContext(Dispatchers.Main) {
                            ClipboardUtils.copyText("Markdown", content)
                            toast("Markdown copied to clipboard")
                        }
                    }
                }
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(drawables.copy),
                contentDescription = "Copy Markdown",
            )
        }

        IconButton(
            onClick = {
                refreshKey++
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(strings.refresh),
            )
        }
    }

    @Composable
    override fun Content() {
        var parsedBlocks by remember { mutableStateOf<List<MarkdownBlock>?>(null) }
        var baseDirPath by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val scrollController = remember(scrollState, coroutineScope) {
            MarkdownScrollController(scrollState, coroutineScope)
        }

        LaunchedEffect(currentHeadings) {
            scrollController.updateHeadings(currentHeadings)
        }

        LaunchedEffect(parsedBlocks, initialAnchor) {
            if (parsedBlocks != null && !initialAnchor.isNullOrBlank()) {
                kotlinx.coroutines.delay(150)
                scrollController.scrollToAnchor(initialAnchor)
            }
        }

        LaunchedEffect(file, refreshKey) {
            isLoading = true
            errorMessage = null
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        file.getInputStream().bufferedReader().use { it.readText() }
                    }
                val dirPath =
                    withContext(Dispatchers.IO) {
                        file.getParentFile()?.getAbsolutePath()
                    }
                baseDirPath = dirPath

                val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                docWordCount = words
                docCharCount = content.length

                val blocks =
                    withContext(Dispatchers.Default) {
                        MarkdownBlockParser.parse(content)
                    }
                parsedBlocks = blocks
                currentHeadings = blocks.filterIsInstance<MarkdownBlock.Heading>()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to read Markdown file"
            } finally {
                isLoading = false
            }
        }

        if (showOutlineDialog) {
            OutlineDialog(
                headings = currentHeadings,
                wordCount = docWordCount,
                charCount = docCharCount,
                onDismiss = { showOutlineDialog = false },
                onHeadingClick = { heading, index ->
                    scrollController.scrollToHeading(heading, index)
                },
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage ?: "Error reading Markdown file",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                parsedBlocks != null -> {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val canceled = event.changes.any { it.isConsumed }
                                            if (!canceled && event.changes.size >= 2) {
                                                val zoom = event.calculateZoom()
                                                val pan = event.calculatePan()

                                                val newScale = (scale * zoom).coerceIn(0.6f, 4.0f)
                                                scale = newScale

                                                if (newScale > 1.05f) {
                                                    offset += pan
                                                } else {
                                                    offset = Offset.Zero
                                                }

                                                event.changes.forEach { it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                        transformOrigin = TransformOrigin(0.5f, 0f),
                                    ),
                        ) {
                            SelectionContainer {
                                MarkdownView(
                                    blocks = parsedBlocks ?: emptyList(),
                                    currentFile = file,
                                    projectRoot = projectRoot,
                                    viewModel = viewModel,
                                    baseDirPath = baseDirPath,
                                    scrollController = scrollController,
                                    onAnchorClick = { anchor ->
                                        scrollController.scrollToAnchor(anchor)
                                    },
                                )
                            }
                        }

                        // Floating Zoom Indicator & Reset Button when zoomed
                        if (scale < 0.95f || scale > 1.05f) {
                            Surface(
                                modifier =
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            scale = 1f
                                            offset = Offset.Zero
                                        },
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 6.dp,
                                shadowElevation = 4.dp,
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "${(scale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OutlineDialog(
        headings: List<MarkdownBlock.Heading>,
        wordCount: Int,
        charCount: Int,
        onDismiss: () -> Unit,
        onHeadingClick: (MarkdownBlock.Heading, Int) -> Unit = { _, _ -> },
    ) {
        val readTimeMin = maxOf(1, (wordCount / 200))

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Document Outline",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Document Stats Card
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$wordCount words",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$charCount chars",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "~$readTimeMin min read",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (headings.isEmpty()) {
                        Text(
                            text = "No headings found in this document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(headings.size) { index ->
                                val heading = headings[index]
                                val indent = ((heading.level - 1) * 12).dp
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                onDismiss()
                                                onHeadingClick(heading, index)
                                            }
                                            .padding(start = indent, top = 6.dp, bottom = 6.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            text = "H${heading.level}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = heading.text,
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (heading.level <= 2) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            },
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarkdownTab) return false
        return file.getAbsolutePath() == other.file.getAbsolutePath()
    }

    override fun hashCode(): Int {
        return file.getAbsolutePath().hashCode()
    }
}
