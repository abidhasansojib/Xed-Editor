package com.rk.droidspaces

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DroidspacesBinaryInstaller {

    private fun getBusyboxAssetName(): String {
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi.lowercase()) {
                "arm64-v8a" -> return "busybox-aarch64"
                "armeabi-v7a", "armeabi" -> return "busybox-armhf"
                "x86_64" -> return "busybox-x86_64"
                "x86" -> return "busybox-x86"
            }
        }
        return "busybox-aarch64"
    }

    suspend fun installBusybox(context: Context): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val assetName = getBusyboxAssetName()
                val tempFile = File(context.cacheDir, "busybox_temp")

                context.assets.open("binaries/$assetName").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                tempFile.setReadable(true, false)
                tempFile.setExecutable(true, false)

                val targetPath = DroidspacesConstants.DEFAULT_BUSYBOX_BINARY
                val installDir = DroidspacesConstants.INSTALL_PATH
                val tempPath = tempFile.absolutePath

                val cmd = "mkdir -p '$installDir' && cp '$tempPath' '$targetPath' && chmod 755 '$targetPath' && rm -f '$tempPath'"
                val result = DroidspacesShell.runCommand(cmd, timeoutMs = 10000)

                if (result.isSuccess) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to install BusyBox: ${result.stderr.ifBlank { result.stdout }}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
