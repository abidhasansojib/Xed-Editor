package com.rk.icons.pack

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.rk.activities.settings.SettingsActivity
import com.rk.extension.model.PackageCache
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createDirIfNot
import com.rk.file.localDir
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.dialogRes
import com.rk.utils.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class IconPackEntry(
    val id: String,
    val manifest: IconPackManifest,
    val size: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

val currentIconPack = mutableStateOf<LocalIconPack?>(null)
val iconPackDir = localDir().child("icon_pack").also { it.createDirIfNot() }

class IconPackManager(private val context: Application) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    private val _localIconPacks = MutableStateFlow<Map<String, LocalIconPack>>(emptyMap())
    val localIconPacks: StateFlow<Map<String, LocalIconPack>> = _localIconPacks.asStateFlow()

    fun isInstalled(id: String) = localIconPacks.value.containsKey(id)

    fun getIconPackPackage(id: String): IconPackPackage? {
        return localIconPacks.value[id]
    }

    private suspend fun calcSize(dir: File): Long {
        return FileOperations.calculateContent(FileWrapper(dir)).totalSize
    }

    private fun resolveCache(dir: File): PackageCache {
        val cacheFile = dir.resolve("cache.json")

        if (!cacheFile.exists() || !cacheFile.isFile) {
            return PackageCache()
        }

        return runCatching {
            json.decodeFromString<PackageCache>(cacheFile.readText())
        }.getOrElse {
            PackageCache()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validateIconPack(dir: File): IconPackManifest? {
        val iconPackJson = dir.resolve("manifest.json")
        if (!iconPackJson.exists()) {
            dialogRes(
                SettingsActivity.instance,
                strings.icon_pack_install_failed.getString(),
                strings.manifest_missing.getString(),
                cancelable = false,
            )
            return null
        }
        val iconPackManifest = runCatching {
            json.decodeFromString<IconPackManifest>(iconPackJson.readText())
        }.getOrElse { e ->
            if (e is MissingFieldException) {
                val fields = e.missingFields.joinToString("\n") { "• $it" }
                dialogRes(
                    SettingsActivity.instance,
                    strings.icon_pack_install_failed.getString(),
                    strings.manifest_missing_fields.getFilledString(fields),
                    cancelable = false,
                )
                return null
            }
            dialogRes(
                SettingsActivity.instance,
                strings.icon_pack_install_failed.getString(),
                e.localizedMessage ?: strings.unknown_err.getString(),
                cancelable = false,
            )
            return null
        }

        return iconPackManifest
    }

    fun uninstallIconPack(iconPackId: String) {
        val iconPack = localIconPacks.value[iconPackId] ?: return
        File(iconPack.installPath).deleteRecursively()
        _localIconPacks.update { it - iconPackId }
    }

    suspend fun indexLocalPacks() = mutex.withLock {
        val newLocal = mutableMapOf<String, LocalIconPack>()
        withContext(Dispatchers.IO) {
            iconPackDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val manifestJson = dir.resolve("manifest.json")
                    if (manifestJson.exists()) {
                        runCatching {
                            val iconPackManifest = json.decodeFromString<IconPackManifest>(manifestJson.readText())
                            val cache = resolveCache(dir)
                            val size = cache.size ?: calcSize(dir).also { writeCache(dir, cache.copy(size = it)) }

                            val iconPack =
                                LocalIconPack(
                                    manifest = iconPackManifest,
                                    installPath = dir.absolutePath,
                                    createdAt = cache.createdAt,
                                    updatedAt = cache.updatedAt,
                                    initSize = size,
                                )
                            newLocal[iconPackManifest.id] = iconPack
                        }.onFailure {
                            logError(it, "Failed to index local icon pack")
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            _localIconPacks.value = newLocal
        }

        if (Settings.icon_pack.isNotEmpty()) {
            currentIconPack.value = localIconPacks.value[Settings.icon_pack]
        }
    }

    private fun writeCache(dir: File, cache: PackageCache) {
        runCatching {
            dir.resolve("cache.json").writeText(json.encodeToString(PackageCache.serializer(), cache))
        }
    }
}
