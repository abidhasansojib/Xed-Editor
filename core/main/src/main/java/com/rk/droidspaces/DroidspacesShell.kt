package com.rk.droidspaces

import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object DroidspacesShell {

    suspend fun runCommand(cmd: String, timeoutMs: Long = 10000): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", cmd).start()
                val stdoutReader = process.inputStream.bufferedReader()
                val stderrReader = process.errorStream.bufferedReader()

                val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    return@withContext CommandResult(-1, "", "Command timed out after ${timeoutMs}ms")
                }

                val stdout = stdoutReader.readText()
                val stderr = stderrReader.readText()
                CommandResult(process.exitValue(), stdout, stderr)
            } catch (e: Exception) {
                CommandResult(-1, "", e.localizedMessage ?: e.message ?: "Execution failed")
            }
        }

    suspend fun runInContainer(
        containerName: String,
        cmd: String,
        user: String? = null,
        timeoutMs: Long = 10000,
    ): CommandResult {
        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = containerName.replace("'", "'\\''")
        val userFlag = if (!user.isNullOrBlank()) "-u '${user.replace("'", "'\\''")}' " else ""
        val fullCmd = "'$bin' --name='$escapedName' ${userFlag}run $cmd"
        return runCommand(fullCmd, timeoutMs)
    }

    suspend fun testPathExists(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run test -e '$escapedPath' && echo 1 || echo 0"
            val result = runCommand(cmd, timeoutMs = 5000)
            result.isSuccess && result.stdout.trim().startsWith("1")
        }

    suspend fun isDirectory(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            if (path == "/" || path.isEmpty()) return@withContext true
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run test -d '$escapedPath' && echo 1 || echo 0"
            val result = runCommand(cmd, timeoutMs = 5000)
            result.isSuccess && result.stdout.trim().startsWith("1")
        }

    suspend fun getFileLength(containerName: String, path: String): Long =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run stat -c %s '$escapedPath' 2>/dev/null || '$bin' --name='$escapedName' run wc -c < '$escapedPath' 2>/dev/null || echo 0"
            val result = runCommand(cmd, timeoutMs = 5000)
            result.stdout.lines().firstOrNull { it.isNotBlank() }?.trim()?.toLongOrNull() ?: 0L
        }

    suspend fun getLastModified(containerName: String, path: String): Long =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run stat -c %Y '$escapedPath' 2>/dev/null || echo 0"
            val result = runCommand(cmd, timeoutMs = 5000)
            val sec = result.stdout.trim().toLongOrNull() ?: 0L
            sec * 1000L
        }

    suspend fun listFiles(containerName: String, path: String): List<FileObject> =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val cleanPath = path.trimEnd('/').ifEmpty { "/" }
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = cleanPath.replace("'", "'\\''")

            // Execute single-command stat to fetch file name, size, type, and timestamp in ONE process
            val statCmd = if (cleanPath == "/") {
                "'$bin' --name='$escapedName' run stat -c '%s|%Y|%F|%n' /* /.[!.]* 2>/dev/null"
            } else {
                "'$bin' --name='$escapedName' run stat -c '%s|%Y|%F|%n' '$escapedPath'/* '$escapedPath'/.[!.]* 2>/dev/null"
            }

            val statResult = runCommand(statCmd, timeoutMs = 8000)
            val items = mutableListOf<FileObject>()

            if (statResult.isSuccess && statResult.outLines.isNotEmpty()) {
                for (line in statResult.outLines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val parts = trimmed.split("|")
                    if (parts.size >= 4) {
                        val size = parts[0].trim().toLongOrNull() ?: 0L
                        val timestampSec = parts[1].trim().toLongOrNull() ?: 0L
                        val isDir = parts[2].contains("directory", ignoreCase = true)
                        val itemPath = parts.subList(3, parts.size).joinToString("|").trim()

                        val name = itemPath.substringAfterLast('/')
                        if (name.isEmpty() || name == "." || name == "..") continue

                        items.add(
                            DroidspacesFileObject(
                                containerName = containerName,
                                containerPath = itemPath,
                                isDirectoryFlag = isDir,
                                isFileFlag = !isDir,
                                fileLength = if (isDir) 0L else size,
                                lastModifiedTime = timestampSec * 1000L,
                            )
                        )
                    }
                }
            }

            // Fallback to ls -1Ap if stat returned no items or failed
            if (items.isEmpty()) {
                val lsCmd = "'$bin' --name='$escapedName' run ls -1Ap '$escapedPath' 2>/dev/null"
                val lsResult = runCommand(lsCmd, timeoutMs = 8000)
                if (lsResult.isSuccess) {
                    for (line in lsResult.outLines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed == "./" || trimmed == "../" || trimmed == "." || trimmed == "..") {
                            continue
                        }
                        val isDir = trimmed.endsWith('/')
                        val name = if (isDir) trimmed.removeSuffix("/") else trimmed
                        val childPath = if (cleanPath == "/") "/$name" else "$cleanPath/$name"
                        items.add(
                            DroidspacesFileObject(
                                containerName = containerName,
                                containerPath = childPath,
                                isDirectoryFlag = isDir,
                                isFileFlag = !isDir,
                            )
                        )
                    }
                }
            }

            items.sortedWith(
                compareBy(
                    { !it.isDirectory() },
                    { it.getName().lowercase() },
                )
            )
        }

    suspend fun readText(containerName: String, path: String, charset: Charset = Charsets.UTF_8): String =
        withContext(Dispatchers.IO) {
            val stream = getInputStream(containerName, path)
            stream.bufferedReader(charset).use { it.readText() }
        }

    suspend fun writeText(
        containerName: String,
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                getOutputStream(containerName, path, append = false).use { out ->
                    out.write(content.toByteArray(charset))
                    out.flush()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    fun getInputStream(containerName: String, path: String): InputStream {
        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = containerName.replace("'", "'\\''")
        val escapedPath = path.replace("'", "'\\''")
        val process = ProcessBuilder("su", "-c", "'$bin' --name='$escapedName' run cat '$escapedPath'").start()
        return process.inputStream
    }

    fun getOutputStream(containerName: String, path: String, append: Boolean = false): OutputStream {
        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = containerName.replace("'", "'\\''")
        val escapedPath = path.replace("'", "'\\''")
        val op = if (append) ">>" else ">"
        val process = ProcessBuilder("su", "-c", "'$bin' --name='$escapedName' run cat $op '$escapedPath'").start()
        return process.outputStream
    }

    suspend fun touch(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run touch '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    suspend fun mkdir(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run mkdir '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    suspend fun mkdirs(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run mkdir -p '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    suspend fun remove(containerName: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run rm -rf '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    suspend fun move(containerName: String, srcPath: String, dstPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedSrc = srcPath.replace("'", "'\\''")
            val escapedDst = dstPath.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run mv '$escapedSrc' '$escapedDst'"
            val result = runCommand(cmd)
            result.isSuccess
        }
}
