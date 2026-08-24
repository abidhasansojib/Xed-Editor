package com.rk.tabs.markdown

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.DefaultScope
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.session.MarkdownPreviewTabState
import com.rk.activities.main.session.TabState
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.lsp.MarkdownImageProvider
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import com.rk.utils.toast
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

/**
 * Tab displaying rendered Markdown content in Preview mode.
 * Supports:
 * - Native rich markdown formatting (headings, code blocks, lists, quotes, tables).
 * - Full image loading (remote URLs, local paths, relative paths, SVGs, data URIs).
 * - Interactive link redirection to linked Markdown files, local files, and web URLs via in-app browser/Custom Tabs.
 * - In-place seamless switching between Preview and Edit modes.
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
            onClick = {
                MarkdownImageProvider.clearCache()
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
        val context = LocalContext.current
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
                errorMessage = e.message ?: "Failed to read Markdown file"
            } finally {
                isLoading = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
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

                    LaunchedEffect(markdownText, refreshKey) {
                        val text = markdownText ?: ""
                        val cleanMd = removeUnsupportedHtmlTags(text)
                        try {
                            val parentDir = withContext(Dispatchers.IO) { file.getParentFile() }
                            MarkdownImageProvider.currentBaseDir = parentDir
                            val spanned =
                                withContext(Dispatchers.IO) {
                                    val result =
                                        SimpleMarkdownRenderer.renderAsync(
                                            cleanMd,
                                            boldColor = primaryColor,
                                            inlineCodeColor = primaryColor,
                                            codeTypeface = Typeface.MONOSPACE,
                                            linkColor = primaryColor,
                                        )
                                    processMarkdownLinks(
                                        spanned = result,
                                        currentFile = file,
                                        projectRoot = projectRoot,
                                        viewModel = viewModel,
                                        context = context,
                                    )
                                }
                            renderedSpanned = spanned
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

    private fun processMarkdownLinks(
        spanned: Spanned,
        currentFile: FileObject,
        projectRoot: FileObject?,
        viewModel: MainViewModel,
        context: Context,
    ): Spanned {
        val spannable = SpannableStringBuilder(spanned)
        val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)

        for (urlSpan in urlSpans) {
            val start = spannable.getSpanStart(urlSpan)
            val end = spannable.getSpanEnd(urlSpan)
            val flags = spannable.getSpanFlags(urlSpan)
            val url = urlSpan.url

            spannable.removeSpan(urlSpan)
            val customSpan =
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        handleMarkdownLink(
                            rawUrl = url,
                            currentFile = currentFile,
                            projectRoot = projectRoot,
                            viewModel = viewModel,
                            context = context,
                        )
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = true
                    }
                }
            spannable.setSpan(customSpan, start, end, flags)
        }

        return spannable
    }

    private fun handleMarkdownLink(
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
                    val customTabsIntent =
                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
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
                // Anchor link within document
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
                                    } else {
                                        null
                                    }
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

    private fun removeUnsupportedHtmlTags(input: String): String {
        val protectedCodeRegex = Regex("(?s)(```.*?```|~~~.*?~~~|`[^`]*`|<code>.*?</code>)")
        val unsupportedHtmlRegex =
            Regex("(?is)<(?!/?(?:br|h[1-6]|blockquote|strong|em|code|pre|li|a|ul|ol|p|img|hr|table|tr|th|td|tbody|thead)\\b)[^>]*>")

        val result = StringBuilder()
        var lastIndex = 0

        protectedCodeRegex.findAll(input).forEach { match ->
            val before = input.substring(lastIndex, match.range.first)
            result.append(before.replace(unsupportedHtmlRegex, ""))
            result.append(match.value)
            lastIndex = match.range.last + 1
        }

        if (lastIndex < input.length) {
            result.append(input.substring(lastIndex).replace(unsupportedHtmlRegex, ""))
        }

        return result.toString()
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
