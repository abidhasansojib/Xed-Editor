package com.rk.lsp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.caverock.androidsvg.SVG
import com.rk.file.FileObject
import com.rk.utils.okHttpClient
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Enhanced image provider implementation for rendering images within Markdown content in the editor.
 *
 * Supports:
 * - **Data URIs:** Base64-encoded SVG and raster images (PNG, JPEG, WebP, GIF).
 * - **Remote URLs:** Network images (`http://`, `https://`) fetched asynchronously with OkHttp and cached.
 * - **Local Files:** Absolute paths (`/...`, `file://...`).
 * - **Relative Paths:** Resolved relative to [currentBaseDir].
 * - **SVG Images:** Scaled and rendered using AndroidSVG with bounds set properly.
 * - **Raster Images:** Decoded via [BitmapFactory] and constrained to a maximum width.
 */
class MarkdownImageProvider : SimpleMarkdownRenderer.ImageProvider {
    companion object {
        var currentBaseDir: FileObject? = null
        private val cache = LruCache<String, Drawable>(64)

        fun register() {
            SimpleMarkdownRenderer.globalImageProvider = MarkdownImageProvider()
        }

        fun clearCache() {
            cache.evictAll()
        }
    }

    override fun load(src: String): Drawable? {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return null

        val cacheKey = if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) {
            trimmed
        } else {
            "${currentBaseDir?.getAbsolutePath()}/$trimmed"
        }

        synchronized(cache) {
            val cached = cache.get(cacheKey)
            if (cached != null) return cached
        }

        val drawable =
            when {
                trimmed.startsWith("data:") -> loadDataUri(trimmed)
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> loadRemoteUrl(trimmed)
                trimmed.startsWith("file://") -> loadLocalFile(trimmed.removePrefix("file://"))
                trimmed.startsWith("/") -> loadLocalFile(trimmed)
                else -> loadRelativeFile(trimmed)
            }

        if (drawable != null) {
            synchronized(cache) {
                cache.put(cacheKey, drawable)
            }
        }

        return drawable
    }

    private fun loadDataUri(src: String): Drawable? {
        val mime = src.substringAfter("data:").substringBefore(";")
        val payload = src.substringAfter("base64,", "")
        if (payload.isEmpty()) return null

        val imageByteArray =
            try {
                Base64.decode(payload, Base64.DEFAULT)
            } catch (_: Exception) {
                return null
            }

        return if (mime == "image/svg+xml" || src.contains("image/svg+xml")) {
            loadSvg(imageByteArray)
        } else {
            loadRaster(imageByteArray)
        }
    }

    private fun loadRemoteUrl(url: String): Drawable? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val bytes = response.body.bytes()
            val contentType = response.header("Content-Type") ?: ""
            val isSvg = contentType.contains("svg", ignoreCase = true) || url.substringBefore('?').endsWith(".svg", ignoreCase = true)

            if (isSvg) {
                loadSvg(bytes)
            } else {
                loadRaster(bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadLocalFile(path: String): Drawable? {
        return try {
            val decodedPath = URLDecoder.decode(path, "UTF-8")
            val file = File(decodedPath)
            if (!file.exists() || !file.isFile) return null

            val bytes = file.readBytes()
            val isSvg = file.name.endsWith(".svg", ignoreCase = true)

            if (isSvg) {
                loadSvg(bytes)
            } else {
                loadRaster(bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadRelativeFile(relPath: String): Drawable? {
        val baseDir = currentBaseDir ?: return null
        return try {
            val cleanPath = relPath.substringBefore('#').substringBefore('?')
            val decodedPath = URLDecoder.decode(cleanPath, "UTF-8")
            val baseFile = File(baseDir.getAbsolutePath())
            val targetFile = File(baseFile, decodedPath).canonicalFile

            if (!targetFile.exists() || !targetFile.isFile) return null

            val bytes = targetFile.readBytes()
            val isSvg = targetFile.name.endsWith(".svg", ignoreCase = true)

            if (isSvg) {
                loadSvg(bytes)
            } else {
                loadRaster(bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSvg(imageByteArray: ByteArray, maxWidth: Int = 800): Drawable? {
        val svgText = String(imageByteArray, StandardCharsets.UTF_8)
        val svg =
            try {
                SVG.getFromString(svgText)
            } catch (_: Exception) {
                return null
            }

        val originalWidth = if (svg.documentWidth > 0) svg.documentWidth else 800f
        val originalHeight = if (svg.documentHeight > 0) svg.documentHeight else 600f

        val clampedWidth = originalWidth.coerceIn(100f, maxWidth.toFloat())
        val scale = clampedWidth / originalWidth
        val scaledWidth = clampedWidth.toInt().coerceAtLeast(1)
        val scaledHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = createBitmap(scaledWidth, scaledHeight)
        val canvas = Canvas(bitmap)

        canvas.scale(scale, scale)
        svg.renderToCanvas(canvas)

        return BitmapDrawable(bitmap).apply {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }
    }

    private fun loadRaster(imageByteArray: ByteArray, maxWidth: Int = 800): Drawable? {
        val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size) ?: return null
        val scaledBitmap = scaleIfNeeded(bitmap, maxWidth)
        return BitmapDrawable(scaledBitmap).apply {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }
    }

    private fun scaleIfNeeded(bmp: Bitmap, maxWidth: Int): Bitmap {
        val currentWidth = bmp.width
        if (currentWidth <= maxWidth) return bmp
        val ratio = maxWidth.toFloat() / currentWidth.toFloat()

        val newHeight = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return bmp.scale(maxWidth, newHeight)
    }
}
