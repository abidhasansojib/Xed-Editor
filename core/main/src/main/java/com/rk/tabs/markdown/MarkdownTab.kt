package com.rk.tabs.markdown

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.session.MarkdownPreviewTabState
import com.rk.activities.main.session.TabState
import com.rk.file.FileObject
import com.rk.lsp.MarkdownImageProvider
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab displaying rendered Markdown content in Preview mode by default.
 * Provides seamless in-place switching to [EditorTab] for editing.
 */
class MarkdownTab(
    override val file: FileObject,
    var projectRoot: FileObject? = null,
    val viewModel: MainViewModel,
) : Tab() {
    override val name: String = "Markdown preview"
    override val icon: ImageVector? = null
    override var title: String by mutableStateOf(file.getName())
    override val showGlobalActions: Boolean = true

    private var refreshKey by mutableIntStateOf(0)

    init {
        MarkdownImageProvider.register()
    }

    override fun getState(): TabState {
        return MarkdownPreviewTabState(file, projectRoot)
    }

    @Composable
    override fun RowScope.Actions() {
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
            onClick = { refreshKey++ },
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
        var markdownText by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

        LaunchedEffect(file, refreshKey) {
            isLoading = true
            errorMessage = null
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        file.getInputStream().bufferedReader().use { it.readText() }
                    }
                markdownText = content
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to read file"
            } finally {
                isLoading = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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
                markdownText != null -> {
                    val scrollState = rememberScrollState()
                    var renderedSpanned by remember(markdownText, refreshKey) {
                        mutableStateOf<CharSequence?>(null)
                    }

                    LaunchedEffect(markdownText) {
                        val text = markdownText ?: ""
                        val cleanMd = removeUnsupportedHtmlTags(text)
                        try {
                            SimpleMarkdownRenderer.renderAsync(
                                cleanMd,
                                boldColor = primaryColor,
                                inlineCodeColor = primaryColor,
                                codeTypeface = Typeface.MONOSPACE,
                                linkColor = primaryColor,
                            ) { spanned ->
                                renderedSpanned = spanned
                            }
                        } catch (_: Exception) {
                            renderedSpanned = text
                        }
                    }

                    SelectionContainer(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    setTextColor(onSurfaceColor)
                                    textSize = 15f
                                    linksClickable = true
                                    movementMethod = LinkMovementMethod.getInstance()
                                }
                            },
                            update = { textView ->
                                textView.setTextColor(onSurfaceColor)
                                if (renderedSpanned != null) {
                                    textView.text = renderedSpanned
                                } else {
                                    textView.text = markdownText
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun removeUnsupportedHtmlTags(input: String): String {
        val pattern = Regex("<(?!img\\s)(?!/?(b|i|u|strong|em|p|br|code|pre|a))[^>]*>", RegexOption.IGNORE_CASE)
        return input.replace(pattern, "")
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
