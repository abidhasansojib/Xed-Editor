package com.rk.tabs.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * Modern Jetpack Compose Markdown Preview Tab.
 * Features:
 * - Pure Compose Material 3 UI.
 * - Rich GitHub-Flavored Markdown (GFM) elements: Tables, Task Lists, Blockquotes, Headings with divider underlines.
 * - Modern Code Blocks with language badges, copy-to-clipboard button, and monospace formatting.
 * - GitHub-style Alert Callouts ([!NOTE], [!TIP], [!IMPORTANT], [!WARNING], [!CAUTION]).
 * - Universal image loading via Coil (Remote URLs, local/relative paths, SVGs, Base64 data URIs).
 * - Interactive link navigation (Chrome Custom Tabs for web, in-app redirection for .md and source files).
 */
class MarkdownTab(
    override val file: FileObject,
    var projectRoot: FileObject? = null,
    val viewModel: MainViewModel,
) : Tab() {
    override val name: String = "Markdown preview"
    override val icon: ImageVector
        get() = XedIcons.Menu_book
    override var title: String by mutableStateOf(file.getName())
    override val showGlobalActions: Boolean = true

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
                val blocks =
                    withContext(Dispatchers.Default) {
                        MarkdownBlockParser.parse(content)
                    }
                parsedBlocks = blocks
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to read Markdown file"
            } finally {
                isLoading = false
            }
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
                    val scrollState = rememberScrollState()
                    SelectionContainer(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        MarkdownView(
                            blocks = parsedBlocks ?: emptyList(),
                            currentFile = file,
                            projectRoot = projectRoot,
                            viewModel = viewModel,
                            baseDirPath = baseDirPath,
                        )
                    }
                }
            }
        }
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
