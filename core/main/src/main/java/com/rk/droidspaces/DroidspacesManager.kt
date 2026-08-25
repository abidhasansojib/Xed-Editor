package com.rk.droidspaces

import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object DroidspacesManager {

    private val userCache = ConcurrentHashMap<String, List<ContainerUser>>()

    suspend fun checkRootAccess(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val result = DroidspacesShell.runCommand("id", timeoutMs = 4000)
                result.isSuccess && result.stdout.contains("uid=0")
            } catch (_: Exception) {
                false
            }
        }

    fun getDroidspacesBinary(): String {
        val configured = Settings.droidspaces_binary_path.trim()
        return if (configured.isNotEmpty()) configured else DroidspacesConstants.DEFAULT_DROIDSPACES_BINARY
    }

    suspend fun isDroidspacesInstalled(): Boolean =
        withContext(Dispatchers.IO) {
            val bin = getDroidspacesBinary()
            val result = DroidspacesShell.runCommand("test -x '$bin' && echo 1 || echo 0", timeoutMs = 4000)
            result.isSuccess && result.stdout.trim().startsWith("1")
        }

    suspend fun isBusyboxInstalled(): Boolean =
        withContext(Dispatchers.IO) {
            val result = DroidspacesShell.runCommand(
                "test -x '${DroidspacesConstants.DEFAULT_BUSYBOX_BINARY}' && echo 1 || echo 0",
                timeoutMs = 4000,
            )
            result.isSuccess && result.stdout.trim().startsWith("1")
        }

    suspend fun listContainers(): List<String> =
        withContext(Dispatchers.IO) {
            val containers = mutableSetOf<String>()
            // Scan container directories
            val result = DroidspacesShell.runCommand(
                "ls -d ${DroidspacesConstants.CONTAINERS_BASE_PATH}/*/ 2>/dev/null",
                timeoutMs = 5000,
            )
            if (result.isSuccess) {
                for (line in result.outLines) {
                    val name = line.trim().removeSuffix("/").substringAfterLast("/")
                    if (name.isNotEmpty()) {
                        containers.add(name)
                    }
                }
            }

            // Also check droidspaces show command
            val bin = getDroidspacesBinary()
            val showResult = DroidspacesShell.runCommand("'$bin' show 2>/dev/null", timeoutMs = 5000)
            if (showResult.isSuccess) {
                for (line in showResult.outLines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("NAME") && !trimmed.startsWith("-")) {
                        val name = trimmed.split(Regex("\\s+")).firstOrNull()
                        if (!name.isNullOrBlank()) {
                            containers.add(name)
                        }
                    }
                }
            }

            if (containers.isEmpty()) {
                containers.add(Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME })
            }
            containers.toList().sorted()
        }

    suspend fun getContainerStatus(containerName: String): ContainerStatus =
        withContext(Dispatchers.IO) {
            val bin = getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")
            val result = DroidspacesShell.runCommand("'$bin' --name='$escapedName' pid 2>/dev/null", timeoutMs = 4000)
            if (result.isSuccess) {
                val pid = result.stdout.trim().toIntOrNull()
                if (pid != null && pid > 0) {
                    return@withContext ContainerStatus.RUNNING
                }
            }
            val exists = DroidspacesShell.runCommand(
                "test -d '${DroidspacesConstants.CONTAINERS_BASE_PATH}/$escapedName' && echo 1 || echo 0",
                timeoutMs = 4000,
            )
            if (exists.isSuccess && exists.stdout.trim().startsWith("1")) {
                ContainerStatus.STOPPED
            } else {
                ContainerStatus.NOT_FOUND
            }
        }

    suspend fun getContainerUsers(containerName: String, useCache: Boolean = true): List<ContainerUser> =
        withContext(Dispatchers.IO) {
            if (useCache && userCache.containsKey(containerName)) {
                return@withContext userCache[containerName]!!
            }

            val defaultRoot = ContainerUser(
                username = "root",
                homeDir = "/root",
                uid = 0,
                shell = "/bin/bash",
            )

            val users = mutableListOf<ContainerUser>()
            users.add(defaultRoot)

            val bin = getDroidspacesBinary()
            val escapedName = containerName.replace("'", "'\\''")

            // Try reading /etc/passwd via droidspaces run first
            var passwdResult = DroidspacesShell.runCommand(
                "'$bin' --name='$escapedName' run cat /etc/passwd 2>/dev/null",
                timeoutMs = 5000,
            )

            // If that fails, try reading directly from container directory
            if (!passwdResult.isSuccess || passwdResult.stdout.isBlank()) {
                passwdResult = DroidspacesShell.runCommand(
                    "cat '${DroidspacesConstants.CONTAINERS_BASE_PATH}/$escapedName/rootfs/etc/passwd' 2>/dev/null",
                    timeoutMs = 5000,
                )
            }

            if (passwdResult.isSuccess && passwdResult.stdout.isNotBlank()) {
                val nonInteractiveShells = setOf(
                    "/bin/false",
                    "/usr/sbin/nologin",
                    "/sbin/nologin",
                    "/bin/sync",
                    "/dev/null",
                )

                for (line in passwdResult.outLines) {
                    val parts = line.split(":")
                    if (parts.size >= 6) {
                        val name = parts[0].trim()
                        val uid = parts[2].trim().toIntOrNull() ?: 1000
                        val home = parts[5].trim()
                        val shell = if (parts.size >= 7) parts[6].trim() else "/bin/bash"

                        if (name == "root") continue
                        if (name.startsWith("nixbld")) continue
                        if (nonInteractiveShells.contains(shell)) continue
                        if (uid == 65534) continue // nobody

                        if (uid >= 1000 || home.startsWith("/home/")) {
                            users.add(
                                ContainerUser(
                                    username = name,
                                    homeDir = home.ifEmpty { "/home/$name" },
                                    uid = uid,
                                    shell = shell,
                                )
                            )
                        }
                    }
                }
            }

            val distinctUsers = users.distinctBy { it.username }
            userCache[containerName] = distinctUsers
            distinctUsers
        }

    fun clearUserCache(containerName: String? = null) {
        if (containerName != null) {
            userCache.remove(containerName)
        } else {
            userCache.clear()
        }
    }

    suspend fun getUserHome(containerName: String, username: String): String {
        if (username == "root" || username.isBlank()) return "/root"
        val users = getContainerUsers(containerName, useCache = true)
        val match = users.firstOrNull { it.username == username }
        return match?.homeDir ?: "/home/$username"
    }

    suspend fun testContainer(containerName: String): Result<String> =
        withContext(Dispatchers.IO) {
            val rootOk = checkRootAccess()
            if (!rootOk) {
                return@withContext Result.failure(Exception("Root access (su) is not available or was denied."))
            }

            val isInstalled = isDroidspacesInstalled()
            if (!isInstalled) {
                return@withContext Result.failure(
                    Exception("Droidspaces binary not found at '${getDroidspacesBinary()}'.")
                )
            }

            val result = DroidspacesShell.runInContainer(
                containerName = containerName,
                cmd = "uname -a && uptime 2>/dev/null",
                timeoutMs = 8000,
            )

            if (result.isSuccess) {
                Result.success("Container '$containerName' is online and responding!\n\n${result.stdout.trim()}")
            } else {
                val errorMsg = result.stderr.ifBlank { result.stdout }.ifBlank { "Exit code ${result.exitCode}" }
                Result.failure(Exception("Failed to run command in container '$containerName': $errorMsg"))
            }
        }
}
