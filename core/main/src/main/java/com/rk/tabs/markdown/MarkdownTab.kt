package com.rk.tabs.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.viewinterop.AndroidView
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.DefaultScope
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.session.MarkdownPreviewTabState
import com.rk.activities.main.session.TabState
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

/**
 * Advanced GitHub-identical Markdown Preview Tab.
 * Renders standard GitHub-Flavored Markdown with:
 * - Official GitHub Markdown CSS (Dark and Light themes)
 * - GitHub Alerts (> [!NOTE], > [!TIP], > [!IMPORTANT], > [!WARNING], > [!CAUTION]) with official Octicons
 * - Syntax highlighting with language headers and one-tap "Copy" buttons
 * - GFM Tables with zebra striping and borders
 * - Task list checkboxes
 * - KaTeX math rendering ($math$ and $$math$$)
 * - Mermaid live diagrams
 * - Collapsible <details><summary> sections
 * - Relative and local image loading directly from device storage
 * - Smart link navigation (Chrome Custom Tabs for web, in-app tabs for code and .md files)
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

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content() {
        var htmlContent by remember { mutableStateOf<String?>(null) }
        var parentDirFile by remember { mutableStateOf<File?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        val isDark = isSystemInDarkTheme()

        LaunchedEffect(file, refreshKey, isDark) {
            isLoading = true
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        file.getInputStream().bufferedReader().use { it.readText() }
                    }
                val parentDir =
                    withContext(Dispatchers.IO) {
                        file.getParentFile()?.getAbsolutePath()?.let { File(it) }
                    }
                parentDirFile = parentDir
                val baseDirUrl = parentDir?.toURI()?.toString() ?: "file:///"

                val html =
                    withContext(Dispatchers.Default) {
                        GitHubMarkdownTemplate.generateHtml(
                            markdownContent = content,
                            isDark = isDark,
                            baseDirUrl = baseDirUrl,
                        )
                    }
                htmlContent = html
            } catch (e: Exception) {
                htmlContent = "<html><body><h3>Error reading file: ${e.message}</h3></body></html>"
            } finally {
                isLoading = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            if (htmlContent != null) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            webViewClient = createMarkdownWebViewClient(ctx, parentDirFile, projectRoot, viewModel, file)
                        }
                    },
                    update = { webView ->
                        htmlContent?.let { html ->
                            val baseDirUrl = parentDirFile?.toURI()?.toString() ?: "file:///"
                            webView.loadDataWithBaseURL(baseDirUrl, html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    private fun createMarkdownWebViewClient(
        context: Context,
        parentDir: File?,
        projectRoot: FileObject?,
        viewModel: MainViewModel,
        currentFile: FileObject,
    ): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val urlString = uri.toString()

                if (urlString.startsWith("http://", ignoreCase = true) || urlString.startsWith("https://", ignoreCase = true)) {
                    try {
                        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                        customTabs.launchUrl(context, uri)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            toast("Could not open link")
                        }
                    }
                    return true
                }

                if (urlString.startsWith("mailto:", ignoreCase = true) || urlString.startsWith("tel:", ignoreCase = true)) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        toast("Could not open link")
                    }
                    return true
                }

                // Handle local / relative file links
                DefaultScope.launch(Dispatchers.IO) {
                    try {
                        val cleanPath = URLDecoder.decode(uri.path ?: "", "UTF-8")
                        val targetFile =
                            when {
                                urlString.startsWith("file://") -> File(URLDecoder.decode(urlString.removePrefix("file://"), "UTF-8"))
                                urlString.startsWith("/") -> File(cleanPath)
                                parentDir != null -> File(parentDir, cleanPath).canonicalFile
                                else -> null
                            }

                        if (targetFile != null && targetFile.exists()) {
                            val targetFileObject = FileWrapper(targetFile)
                            withContext(Dispatchers.Main) {
                                val tab =
                                    TabRegistry.getTab(
                                        file = targetFileObject,
                                        projectRoot = projectRoot,
                                        viewModel = viewModel,
                                        readOnly = false,
                                        customTitle = null,
                                    )
                                viewModel.tabManager.addTab(tab, switchToTab = true)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                toast("File not found: ${targetFile?.name ?: urlString}")
                            }
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            toast("Failed to open link: $urlString")
                        }
                    }
                }

                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return null
                if (uri.scheme == "file") {
                    try {
                        val path = URLDecoder.decode(uri.path ?: "", "UTF-8")
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val mime = getMimeTypeForPath(file.name)
                            return WebResourceResponse(mime, "UTF-8", FileInputStream(file))
                        }
                    } catch (_: Exception) {}
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun getMimeTypeForPath(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
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
