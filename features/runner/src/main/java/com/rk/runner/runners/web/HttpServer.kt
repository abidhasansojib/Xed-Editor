package com.rk.runner.runners.web

import android.content.Context
import com.rk.file.FileObject
import com.rk.file.resolve
import com.rk.resources.getString
import com.rk.resources.strings
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.runBlocking
import java.net.URLConnection

class HttpServer(
    val context: Context,
    port: Int,
    val root: FileObject,
    val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null,
) : NanoHTTPD(port) {
    init {
        start()
    }

    override fun useGzipWhenAccepted(r: Response?): Boolean = false

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val uri = session?.uri ?: "/"

            if (root.isFile()) {
                if (!root.exists()) return@runBlocking notFoundError()
                return@runBlocking serveFile(root)
            }

            var file = root.resolve(uri) ?: return@runBlocking notFoundError()
            if (file.isDirectory()) {
                file = file.getChild("index.html") ?: return@runBlocking notFoundError()
            }

            // Hook override
            serveHook?.invoke(file, session!!)?.let {
                return@runBlocking it
            }

            if (!file.exists()) return@runBlocking notFoundError()

            return@runBlocking serveFile(file)
        }
    }

    private suspend fun serveFile(file: FileObject): Response {
        val mime = getMimeType(file.getName())
        val isHtml = mime.startsWith("text/html")

        return if (isHtml) {
            serveHtmlFile(file, mime)
        } else {
            serveStaticFile(file, mime)
        }
    }

    private suspend fun serveHtmlFile(file: FileObject, mime: String): Response {
        return try {
            val html = file.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            var modifiedHtml = html

            // Mobile viewport injection (ensures mobile layout instead of desktop scaling)
            if (!modifiedHtml.contains("name=\"viewport\"", ignoreCase = true) &&
                !modifiedHtml.contains("name='viewport'", ignoreCase = true)) {
                val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">"
                modifiedHtml = if (modifiedHtml.contains("<head>", ignoreCase = true)) {
                    modifiedHtml.replaceFirst("(?i)<head>".toRegex(), "<head>\n    $viewportMeta")
                } else if (modifiedHtml.contains("<html>", ignoreCase = true)) {
                    modifiedHtml.replaceFirst("(?i)<html>".toRegex(), "<html>\n<head>$viewportMeta</head>")
                } else {
                    "$viewportMeta\n$modifiedHtml"
                }
            }

            val response = newFixedLengthResponse(Status.OK, mime, modifiedHtml)
            addCorsAndCacheHeaders(response)
            response
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private suspend fun serveStaticFile(file: FileObject, mime: String): Response {
        return try {
            val response = newFixedLengthResponse(
                Status.OK,
                mime,
                file.getInputStream(),
                file.length(),
            )
            addCorsAndCacheHeaders(response)
            response
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private fun addCorsAndCacheHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs", "cjs" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "wasm" -> "application/wasm"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain; charset=utf-8"
            "xml" -> "text/xml; charset=utf-8"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    private fun notFoundError(): Response =
        newFixedLengthResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "404 Not found")

    private fun forbiddenError(file: FileObject): Response =
        newFixedLengthResponse(Status.FORBIDDEN, "text/plain; charset=utf-8", "403 Forbidden: Cannot read file ${file.getName()}")

    private fun internalError(e: Exception): Response =
        newFixedLengthResponse(
            Status.INTERNAL_ERROR,
            "text/plain; charset=utf-8",
            "500 Internal server error: ${e.localizedMessage ?: strings.unknown.getString()}",
        )
}
