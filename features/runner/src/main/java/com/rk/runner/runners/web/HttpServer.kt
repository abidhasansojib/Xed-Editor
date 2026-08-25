package com.rk.runner.runners.web

import android.content.Context
import com.rk.file.FileObject
import com.rk.file.resolve
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.isDarkTheme
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

            if (uri == "/__xed_internal__/devtools.js") {
                return@runBlocking serveDevToolsScript()
            }

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

            // 1. Mobile viewport injection (ensures mobile layout instead of desktop scaling)
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

            // 2. Offline DevTools injection
            if (Settings.inject_eruda) {
                val darkTheme = isDarkTheme(context)
                val theme = if (Settings.amoled && darkTheme) "amoled" else if (darkTheme) "dark" else "light"
                val devToolsScript = "\n<script src=\"/__xed_internal__/devtools.js\" data-theme=\"$theme\"></script>\n"

                modifiedHtml = if (modifiedHtml.contains("</head>", ignoreCase = true)) {
                    modifiedHtml.replaceFirst("(?i)</head>".toRegex(), "$devToolsScript</head>")
                } else if (modifiedHtml.contains("</body>", ignoreCase = true)) {
                    modifiedHtml.replaceFirst("(?i)</body>".toRegex(), "$devToolsScript</body>")
                } else {
                    modifiedHtml + devToolsScript
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

    private fun serveDevToolsScript(): Response {
        val script = """
            (function() {
                if (window.__xed_devtools_initialized) return;
                window.__xed_devtools_initialized = true;

                const currentScript = document.currentScript;
                const theme = (currentScript && currentScript.getAttribute('data-theme')) || 'dark';
                const isDark = theme === 'dark' || theme === 'amoled';
                const isAmoled = theme === 'amoled';

                const bg = isAmoled ? '#000000' : (isDark ? '#1e1e1e' : '#f8f9fa');
                const headerBg = isAmoled ? '#111111' : (isDark ? '#2d2d2d' : '#e9ecef');
                const text = isDark ? '#e0e0e0' : '#212529';
                const border = isDark ? '#3e3e3e' : '#dee2e6';
                const logBg = isAmoled ? '#0a0a0a' : (isDark ? '#252526' : '#ffffff');

                const container = document.createElement('div');
                container.id = '__xed_devtools_container';
                container.style.cssText = 'position:fixed;bottom:16px;right:16px;z-index:999999;font-family:monospace;font-size:13px;';

                const btn = document.createElement('button');
                btn.innerText = '⚙ Console';
                btn.style.cssText = 'background:#3b82f6;color:#ffffff;border:none;border-radius:20px;padding:8px 14px;font-weight:bold;box-shadow:0 4px 12px rgba(0,0,0,0.3);cursor:pointer;outline:none;touch-action:manipulation;';

                const panel = document.createElement('div');
                panel.style.cssText = 'display:none;position:fixed;bottom:0;left:0;right:0;height:45vh;background:'+bg+';color:'+text+';border-top:2px solid '+border+';box-shadow:0 -4px 16px rgba(0,0,0,0.4);display:none;flex-direction:column;z-index:999999;';

                const topBar = document.createElement('div');
                topBar.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:8px 12px;background:'+headerBg+';border-bottom:1px solid '+border+';user-select:none;';
                topBar.innerHTML = '<span style="font-weight:bold;">⚡ Xed DevTools</span>';

                const actions = document.createElement('div');
                const clearBtn = document.createElement('button');
                clearBtn.innerText = 'Clear';
                clearBtn.style.cssText = 'background:transparent;border:1px solid '+border+';color:'+text+';border-radius:4px;padding:2px 8px;margin-right:8px;cursor:pointer;';
                clearBtn.onclick = () => { logsList.innerHTML = ''; };

                const closeBtn = document.createElement('button');
                closeBtn.innerText = '✕';
                closeBtn.style.cssText = 'background:transparent;border:none;color:'+text+';font-size:16px;font-weight:bold;cursor:pointer;padding:0 6px;';
                closeBtn.onclick = () => { panel.style.display = 'none'; };

                actions.appendChild(clearBtn);
                actions.appendChild(closeBtn);
                topBar.appendChild(actions);

                const logsList = document.createElement('div');
                logsList.style.cssText = 'flex:1;overflow-y:auto;padding:8px;background:'+logBg+';word-break:break-all;';

                const inputBar = document.createElement('div');
                inputBar.style.cssText = 'display:flex;border-top:1px solid '+border+';background:'+headerBg+';';
                const input = document.createElement('input');
                input.placeholder = 'Eval JavaScript...';
                input.style.cssText = 'flex:1;background:transparent;border:none;color:'+text+';padding:8px 12px;font-family:monospace;outline:none;';
                
                input.onkeydown = (e) => {
                    if (e.key === 'Enter' && input.value.trim()) {
                        const code = input.value.trim();
                        input.value = '';
                        addLog('input', [code]);
                        try {
                            const res = eval(code);
                            addLog('return', [res]);
                        } catch(err) {
                            addLog('error', [err]);
                        }
                    }
                };

                inputBar.appendChild(input);
                panel.appendChild(topBar);
                panel.appendChild(logsList);
                panel.appendChild(inputBar);

                btn.onclick = () => {
                    panel.style.display = panel.style.display === 'none' ? 'flex' : 'none';
                };

                container.appendChild(btn);

                function addLog(type, args) {
                    const row = document.createElement('div');
                    row.style.cssText = 'padding:4px 0;border-bottom:1px solid '+(isDark ? '#333' : '#f0f0f0')+';line-height:1.4;';
                    let color = text;
                    let prefix = '';
                    if (type === 'warn') { color = '#f59e0b'; prefix = '⚠️ '; }
                    else if (type === 'error') { color = '#ef4444'; prefix = '❌ '; }
                    else if (type === 'info') { color = '#3b82f6'; prefix = 'ℹ️ '; }
                    else if (type === 'input') { color = '#10b981'; prefix = '> '; }
                    else if (type === 'return') { color = '#8b5cf6'; prefix = '< '; }

                    row.style.color = color;
                    const formatted = args.map(a => {
                        if (typeof a === 'object') {
                            try { return JSON.stringify(a, null, 2); } catch (_) { return String(a); }
                        }
                        return String(a);
                    }).join(' ');

                    row.innerText = prefix + formatted;
                    logsList.appendChild(row);
                    logsList.scrollTop = logsList.scrollHeight;
                }

                const origLog = console.log;
                const origWarn = console.warn;
                const origError = console.error;
                const origInfo = console.info;

                console.log = function(...args) { addLog('log', args); origLog.apply(console, args); };
                console.warn = function(...args) { addLog('warn', args); origWarn.apply(console, args); };
                console.error = function(...args) { addLog('error', args); origError.apply(console, args); };
                console.info = function(...args) { addLog('info', args); origInfo.apply(console, args); };

                window.addEventListener('error', function(e) {
                    addLog('error', [e.message + ' at ' + e.filename + ':' + e.lineno]);
                });

                window.addEventListener('unhandledrejection', function(e) {
                    addLog('error', ['Unhandled Promise Rejection: ' + e.reason]);
                });

                function appendUI() {
                    if (document.body) {
                        document.body.appendChild(container);
                        document.body.appendChild(panel);
                    } else {
                        document.addEventListener('DOMContentLoaded', appendUI);
                    }
                }
                appendUI();
            })();
        """.trimIndent()

        val response = newFixedLengthResponse(Status.OK, "application/javascript; charset=utf-8", script)
        addCorsAndCacheHeaders(response)
        return response
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
