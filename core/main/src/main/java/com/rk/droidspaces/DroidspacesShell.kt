package com.rk.droidspaces

import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ProcessOutputStream(private val process: Process) : OutputStream() {
    private val out = java.io.BufferedOutputStream(process.outputStream, 32768)
    private var isClosed = false

    override fun write(b: Int) {
        out.write(b)
    }

    override fun write(b: ByteArray) {
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        try {
            out.flush()
        } catch (_: Exception) {}
        try {
            out.close()
        } catch (_: Exception) {}

        try {
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw IOException("Write operation timed out")
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val stderr = process.errorStream.bufferedReader().readText().trim()
                val stdout = process.inputStream.bufferedReader().readText().trim()
                val errorMsg = stderr.ifEmpty { stdout }.ifEmpty { "Exit code $exitCode" }
                throw IOException("Write failed ($errorMsg)")
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw IOException("Write interrupted", e)
        }
    }
}

class ProcessInputStream(
    private val process: Process,
    input: InputStream = process.inputStream,
) : InputStream() {
    private val input: InputStream = if (input is java.io.BufferedInputStream) input else java.io.BufferedInputStream(input, 32768)

    override fun read(): Int = input.read()
    override fun read(b: ByteArray): Int = input.read(b)
    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
    override fun available(): Int = input.available()
    override fun skip(n: Long): Long = input.skip(n)

    override fun close() {
        try {
            input.close()
        } catch (_: Exception) {}
        try {
            process.destroy()
        } catch (_: Exception) {}
    }
}

object DroidspacesShell {

    suspend fun runCommand(cmd: String, timeoutMs: Long = 10000): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", cmd).start()
                val stdoutFuture = CompletableFuture.supplyAsync {
                    process.inputStream.bufferedReader().readText()
                }
                val stderrFuture = CompletableFuture.supplyAsync {
                    process.errorStream.bufferedReader().readText()
                }

                val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    return@withContext CommandResult(-1, "", "Command timed out after ${timeoutMs}ms")
                }

                val stdout = runCatching { stdoutFuture.get(1, TimeUnit.SECONDS) }.getOrDefault("")
                val stderr = runCatching { stderrFuture.get(1, TimeUnit.SECONDS) }.getOrDefault("")
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
        val cmd = "'$bin' --name='$escapedName' run cat '$escapedPath'"
        val process = ProcessBuilder("su", "-c", cmd).start()
        return ProcessInputStream(process)
    }

    fun getOutputStream(containerName: String, path: String, append: Boolean = false): OutputStream {
        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = containerName.replace("'", "'\\''")
        val escapedPath = path.replace("'", "'\\''")
        val op = if (append) ">>" else ">"
        val cmd = "'$bin' --name='$escapedName' run sh -c 'cat $op \"\$1\"' _ '$escapedPath'"
        val process = ProcessBuilder("su", "-c", cmd).start()
        return ProcessOutputStream(process)
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

    suspend fun copy(containerName: String, srcPath: String, dstPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedSrc = srcPath.replace("'", "'\\''")
            val escapedDst = dstPath.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run cp -r '$escapedSrc' '$escapedDst'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    data class DetailedStat(
        val size: Long,
        val octalMode: String,
        val symbolicMode: String,
        val owner: String,
        val group: String,
        val lastModified: Long,
        val fileType: String,
    )

    suspend fun getDetailedStat(containerName: String, path: String): DetailedStat? =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run stat -c '%s|%a|%A|%U|%G|%Y|%F' '$escapedPath'"
            val result = runCommand(cmd)
            if (result.isSuccess && result.stdout.isNotBlank()) {
                val parts = result.stdout.trim().split("|")
                if (parts.size >= 7) {
                    val size = parts[0].toLongOrNull() ?: 0L
                    val octal = parts[1]
                    val symbolic = parts[2]
                    val owner = parts[3]
                    val group = parts[4]
                    val lastMod = (parts[5].toLongOrNull() ?: 0L) * 1000L
                    val type = parts[6]
                    return@withContext DetailedStat(
                        size = size,
                        octalMode = octal,
                        symbolicMode = symbolic,
                        owner = owner,
                        group = group,
                        lastModified = lastMod,
                        fileType = type,
                    )
                }
            }
            null
        }

    suspend fun chmod(containerName: String, path: String, mode: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val escapedMode = mode.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run chmod '$escapedMode' '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }

    suspend fun chown(containerName: String, path: String, ownerGroup: String): Boolean =
        withContext(Dispatchers.IO) {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")
            val escapedOwner = ownerGroup.replace("'", "'\\''")
            val cmd = "'$bin' --name='$escapedName' run chown -R '$escapedOwner' '$escapedPath'"
            val result = runCommand(cmd)
            result.isSuccess
        }
}
