package com.rk.search.index

import android.content.Context
import androidx.room.withTransaction
import com.rk.file.FileObject
import com.rk.search.SearchViewModel
import com.rk.search.utils.GlobExcluder
import com.rk.search.utils.SearchUtils
import com.rk.settings.Settings
import com.rk.utils.logDebug
import com.rk.utils.logError
import com.rk.utils.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.ArrayDeque

/** Manages indexing for a single project with robust lifecycle and memory management. */
class ProjectIndexer(
    private val context: Context,
    private val projectRoot: FileObject,
    private val excluder: GlobExcluder,
    private val onIndexingStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val viewModelScope: CoroutineScope,
) {
    companion object {
        private const val DB_BATCH_SIZE = 500
        private const val MAX_CHUNK_SIZE = 1_000_000
        private const val MAX_DIRECTORY_DEPTH = 64
    }

    private var indexingJob: Job? = null

    /**
     * Starts full indexing of the project. Cancels any previous indexing job first.
     */
    suspend fun startIndexing() {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
                    val pathsToKeep = mutableSetOf<String>()
                    val codeLineBuffer = mutableListOf<CodeLine>()
                    val fileMetaBuffer = mutableListOf<FileMeta>()

                    indexProjectIterative(
                        root = projectRoot,
                        indexedFiles = indexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineBuffer = codeLineBuffer,
                        fileMetaBuffer = fileMetaBuffer,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                    )

                    finalizeIndex(
                        database = database,
                        indexedFiles = indexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                        remainingCodeLines = codeLineBuffer,
                        remainingFileMetas = fileMetaBuffer,
                    )

                    logDebug("Indexing completed for $projectRoot")
                } catch (e: CancellationException) {
                    logDebug("Indexing cancelled for $projectRoot")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during indexing")
                    onError("Indexing failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }
    }

    /** Incremental sync of a specific file or directory. Only re-indexes changed files under the given path. */
    suspend fun syncFile(file: FileObject) {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val allIndexedFiles = fileMetaDao.getAll().associateBy { it.path }

                    // Only consider files under the changed path
                    val targetPrefix = file.getAbsolutePath()
                    val relevantIndexedFiles =
                        if (file == projectRoot) {
                            allIndexedFiles
                        } else {
                            allIndexedFiles.filter { it.key == targetPrefix || it.key.startsWith("$targetPrefix/") }
                        }

                    val pathsToKeep = mutableSetOf<String>()
                    val codeLineBuffer = mutableListOf<CodeLine>()
                    val fileMetaBuffer = mutableListOf<FileMeta>()

                    if (file.isDirectory()) {
                        indexProjectIterative(
                            root = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineBuffer = codeLineBuffer,
                            fileMetaBuffer = fileMetaBuffer,
                            codeLineDao = codeLineDao,
                            fileMetaDao = fileMetaDao,
                        )
                    } else {
                        indexSingleFile(
                            file = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineBuffer = codeLineBuffer,
                            fileMetaBuffer = fileMetaBuffer,
                            codeLineDao = codeLineDao,
                            fileMetaDao = fileMetaDao,
                        )
                    }

                    finalizeIndex(
                        database = database,
                        indexedFiles = relevantIndexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                        remainingCodeLines = codeLineBuffer,
                        remainingFileMetas = fileMetaBuffer,
                    )

                    logDebug("Sync completed for $file")
                } catch (e: CancellationException) {
                    logDebug("Sync cancelled for $file")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during file sync")
                    onError("Sync failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }

        indexingJob?.join()
    }

    /** Cancels any ongoing indexing operation and waits for it to complete. */
    suspend fun cancelIndexing() {
        indexingJob?.cancelAndJoin()
        indexingJob = null
        onIndexingStateChanged(false)
    }

    /** Closes the database and cleans up resources. Does NOT delete the database file. */
    fun closeDatabase() {
        try {
            IndexDatabase.closeInstance(projectRoot)
            logDebug("Closed index database for $projectRoot")
        } catch (e: Exception) {
            logError(e, "Error closing database")
        }
    }

    private suspend fun attemptDatabaseRecovery() {
        return withContext(Dispatchers.IO) {
            try {
                logWarn("Attempting database recovery by deleting corrupt database")
                IndexDatabase.removeDatabase(context, projectRoot)
                onError("Index was corrupted and has been rebuilt. Please try your search again.")
            } catch (e: Exception) {
                logError(e, "Failed to recover database")
            }
        }
    }

    /** Gets current indexing statistics. */
    suspend fun getStats(): SearchViewModel.IndexingStats {
        return withContext(Dispatchers.IO) {
            try {
                val database = IndexDatabase.getDatabase(context, projectRoot)
                val totalFiles = database.fileMetaDao().getCount()
                val databaseSize = IndexDatabase.getDatabaseSize(context, projectRoot)
                SearchViewModel.IndexingStats(totalFiles, databaseSize)
            } catch (e: Exception) {
                logError(e, "Error getting indexing stats")
                SearchViewModel.IndexingStats(0, 0)
            }
        }
    }

    private data class DirTask(val dir: FileObject, val depth: Int, val isParentHidden: Boolean)

    /** Iterative, stack-safe traversal with cycle and depth detection. */
    private suspend fun indexProjectIterative(
        root: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineBuffer: MutableList<CodeLine>,
        fileMetaBuffer: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
    ) {
        val queue = ArrayDeque<DirTask>()
        val visitedDirs = HashSet<String>()

        val rootPath = root.getAbsolutePath()
        visitedDirs.add(rootPath)
        queue.add(DirTask(root, 0, root.getName().startsWith(".")))

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (currentDir, currentDepth, isParentHidden) = queue.removeFirst()

            if (currentDepth > MAX_DIRECTORY_DEPTH) {
                logWarn("Skipping directory exceeding max depth: ${currentDir.getAbsolutePath()}")
                continue
            }

            val childFiles = try {
                currentDir.listFiles()
            } catch (e: Exception) {
                logError(e, "Failed to list children for ${currentDir.getAbsolutePath()}")
                emptyList()
            }

            for (file in childFiles) {
                currentCoroutineContext().ensureActive()

                val path = file.getAbsolutePath()
                val fileName = file.getName()
                val isHidden = fileName.startsWith(".") || isParentHidden

                if (isHidden && !Settings.show_hidden_files_search) continue
                if (excluder.isExcluded(path)) continue

                val isDir = file.isDirectory()

                if (isDir) {
                    if (!visitedDirs.contains(path)) {
                        visitedDirs.add(path)
                        queue.add(DirTask(file, currentDepth + 1, isHidden))
                    }
                } else {
                    indexSingleFile(
                        file = file,
                        indexedFiles = indexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineBuffer = codeLineBuffer,
                        fileMetaBuffer = fileMetaBuffer,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                    )
                }
            }
        }
    }

    private suspend fun indexSingleFile(
        file: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineBuffer: MutableList<CodeLine>,
        fileMetaBuffer: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
    ) {
        try {
            val path = file.getAbsolutePath()
            val lastModified = file.lastModified() ?: 0L
            val fileLength = file.length()

            val indexedFile = indexedFiles[path]
            val isFileModified =
                indexedFile == null || indexedFile.lastModified != lastModified || indexedFile.size != fileLength

            if (!isFileModified) {
                pathsToKeep += path
                return
            }

            pathsToKeep += path

            // Delete old code lines for this file if it was previously indexed and modified
            if (indexedFile != null) {
                codeLineDao.deleteByPath(path)
            }

            fileMetaBuffer.add(
                FileMeta(path = path, fileName = file.getName(), lastModified = lastModified, size = fileLength)
            )

            // Flush file meta buffer in manageable batches to prevent memory spikes
            if (fileMetaBuffer.size >= DB_BATCH_SIZE) {
                fileMetaDao.insertAll(fileMetaBuffer)
                fileMetaBuffer.clear()
            }

            if (!SearchUtils.isFileSearchable(file)) return

            val charset = Charset.forName(Settings.encoding)
            file.useInputStream { inputStream ->
                inputStream.bufferedReader(charset).useLines { lineSequence ->
                    lineSequence.forEachIndexed { lineIndex, line ->
                        currentCoroutineContext().ensureActive()

                        if (line.length <= MAX_CHUNK_SIZE) {
                            codeLineBuffer.add(
                                CodeLine(
                                    content = line,
                                    path = path,
                                    lineNumber = lineIndex,
                                    chunkStart = 0,
                                )
                            )
                            if (codeLineBuffer.size >= DB_BATCH_SIZE) {
                                codeLineDao.insertAll(codeLineBuffer)
                                codeLineBuffer.clear()
                            }
                        } else {
                            val chunks = line.chunked(MAX_CHUNK_SIZE)
                            chunks.forEachIndexed { chunkIndex, chunk ->
                                codeLineBuffer.add(
                                    CodeLine(
                                        content = chunk,
                                        path = path,
                                        lineNumber = lineIndex,
                                        chunkStart = chunkIndex * MAX_CHUNK_SIZE,
                                    )
                                )

                                if (codeLineBuffer.size >= DB_BATCH_SIZE) {
                                    codeLineDao.insertAll(codeLineBuffer)
                                    codeLineBuffer.clear()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error indexing file: ${file.getAbsolutePath()}")
        }
    }

    private suspend fun finalizeIndex(
        database: IndexDatabase,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
        remainingCodeLines: MutableList<CodeLine>,
        remainingFileMetas: MutableList<FileMeta>,
    ) {
        return withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()

                // Flush any remaining buffers
                if (remainingCodeLines.isNotEmpty()) {
                    for (chunk in remainingCodeLines.chunked(DB_BATCH_SIZE)) {
                        codeLineDao.insertAll(chunk)
                    }
                    remainingCodeLines.clear()
                }

                if (remainingFileMetas.isNotEmpty()) {
                    for (chunk in remainingFileMetas.chunked(DB_BATCH_SIZE)) {
                        fileMetaDao.insertAll(chunk)
                    }
                    remainingFileMetas.clear()
                }

                // Batch delete removed files
                val deletedPaths = (indexedFiles.keys - pathsToKeep).toList()
                if (deletedPaths.isNotEmpty()) {
                    database.withTransaction {
                        for (chunk in deletedPaths.chunked(250)) {
                            codeLineDao.deleteByPaths(chunk)
                            fileMetaDao.deleteByPaths(chunk)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e, "Error finalizing index")
                throw e
            }
        }
    }
}
