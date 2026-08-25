package com.rk.droidspaces

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset

class DroidspacesFileObject(
    val containerName: String,
    val containerPath: String,
    private var isDirectoryFlag: Boolean = false,
    private var isFileFlag: Boolean = false,
    private var fileLength: Long = 0L,
    private var lastModifiedTime: Long = 0L,
) : FileObject, Serializable {

    override suspend fun listFiles(): List<FileObject> =
        withContext(Dispatchers.IO) {
            DroidspacesShell.listFiles(containerName, containerPath)
        }

    override fun isDirectory(): Boolean = isDirectoryFlag || containerPath == "/"

    override fun isFile(): Boolean = isFileFlag || (!isDirectoryFlag && containerPath != "/")

    override fun getName(): String {
        val clean = containerPath.trimEnd('/')
        return if (clean.isEmpty()) "/" else clean.substringAfterLast('/')
    }

    override fun getExtension(): String {
        val name = getName()
        return if (name.contains('.')) name.substringAfterLast('.') else ""
    }

    override suspend fun getParentFile(): FileObject? =
        withContext(Dispatchers.IO) {
            val clean = containerPath.trimEnd('/')
            if (clean.isEmpty() || clean == "/") return@withContext null
            val parentPath = clean.substringBeforeLast('/', "").ifEmpty { "/" }
            DroidspacesFileObject(containerName, parentPath, isDirectoryFlag = true)
        }

    override suspend fun exists(): Boolean =
        withContext(Dispatchers.IO) {
            DroidspacesShell.testPathExists(containerName, containerPath)
        }

    override suspend fun createNewFile(): Boolean =
        withContext(Dispatchers.IO) {
            val success = DroidspacesShell.touch(containerName, containerPath)
            if (success) {
                isFileFlag = true
                isDirectoryFlag = false
            }
            success
        }

    override suspend fun getCanonicalPath(): String = containerPath

    override suspend fun mkdir(): Boolean =
        withContext(Dispatchers.IO) {
            val success = DroidspacesShell.mkdir(containerName, containerPath)
            if (success) {
                isDirectoryFlag = true
                isFileFlag = false
            }
            success
        }

    override suspend fun mkdirs(): Boolean =
        withContext(Dispatchers.IO) {
            val success = DroidspacesShell.mkdirs(containerName, containerPath)
            if (success) {
                isDirectoryFlag = true
                isFileFlag = false
            }
            success
        }

    override suspend fun writeText(text: String) {
        writeText(text, Charsets.UTF_8)
    }

    override suspend fun writeText(content: String, charset: Charset): Boolean =
        withContext(Dispatchers.IO) {
            DroidspacesShell.writeText(containerName, containerPath, content, charset)
        }

    override suspend fun getInputStream(): InputStream =
        withContext(Dispatchers.IO) {
            DroidspacesShell.getInputStream(containerName, containerPath)
        }

    override suspend fun <R> useInputStream(block: suspend (InputStream) -> R): R =
        withContext(Dispatchers.IO) {
            getInputStream().use { block(it) }
        }

    override suspend fun getOutputStream(append: Boolean): OutputStream =
        withContext(Dispatchers.IO) {
            DroidspacesShell.getOutputStream(containerName, containerPath, append)
        }

    override fun getAbsolutePath(): String = "droidspaces://$containerName$containerPath"

    override suspend fun length(): Long =
        withContext(Dispatchers.IO) {
            if (fileLength > 0L) return@withContext fileLength
            val len = DroidspacesShell.getFileLength(containerName, containerPath)
            fileLength = len
            len
        }

    override suspend fun delete(): Boolean =
        withContext(Dispatchers.IO) {
            DroidspacesShell.remove(containerName, containerPath)
        }

    override suspend fun toUri(): Uri = Uri.parse("droidspaces://$containerName$containerPath")

    override suspend fun getMimeType(context: Context): String? {
        val ext = getExtension()
        if (ext.isNotEmpty()) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (mime != null) return mime
        }
        return "text/plain"
    }

    override suspend fun renameTo(string: String): Boolean =
        withContext(Dispatchers.IO) {
            DroidspacesShell.move(
                containerName = containerName,
                srcPath = containerPath,
                dstPath = containerPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "" } + "/$string",
            )
        }

    override suspend fun hasChild(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val childPath = if (containerPath == "/") "/$name" else "${containerPath.trimEnd('/')}/$name"
            DroidspacesShell.testPathExists(containerName, childPath)
        }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? =
        withContext(Dispatchers.IO) {
            val childPath = if (containerPath == "/") "/$name" else "${containerPath.trimEnd('/')}/$name"
            val success = if (createFile) {
                DroidspacesShell.touch(containerName, childPath)
            } else {
                DroidspacesShell.mkdirs(containerName, childPath)
            }
            if (success) {
                DroidspacesFileObject(
                    containerName = containerName,
                    containerPath = childPath,
                    isDirectoryFlag = !createFile,
                    isFileFlag = createFile,
                )
            } else null
        }

    override fun canWrite(): Boolean = true

    override fun canRead(): Boolean = true

    override fun canExecute(): Boolean = true

    override suspend fun lastModified(): Long? =
        withContext(Dispatchers.IO) {
            if (lastModifiedTime > 0L) return@withContext lastModifiedTime
            val time = DroidspacesShell.getLastModified(containerName, containerPath)
            lastModifiedTime = time
            time
        }

    override suspend fun getChild(name: String): FileObject? =
        withContext(Dispatchers.IO) {
            val childPath = if (containerPath == "/") "/$name" else "${containerPath.trimEnd('/')}/$name"
            if (DroidspacesShell.testPathExists(containerName, childPath)) {
                val isDir = DroidspacesShell.isDirectory(containerName, childPath)
                DroidspacesFileObject(
                    containerName = containerName,
                    containerPath = childPath,
                    isDirectoryFlag = isDir,
                    isFileFlag = !isDir,
                )
            } else null
        }

    override suspend fun readText(): String = readText(Charsets.UTF_8)

    override suspend fun readText(charset: Charset): String =
        withContext(Dispatchers.IO) {
            DroidspacesShell.readText(containerName, containerPath, charset)
        }

    override fun isSymlink(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DroidspacesFileObject) return false
        return containerName == other.containerName &&
            containerPath.trimEnd('/') == other.containerPath.trimEnd('/')
    }

    override fun hashCode(): Int {
        var result = containerName.hashCode()
        result = 31 * result + containerPath.trimEnd('/').hashCode()
        return result
    }

    override fun toString(): String = getAbsolutePath()
}
