package com.rk.droidspaces

import java.io.Serializable

data class ContainerUser(
    val username: String,
    val homeDir: String,
    val uid: Int = 1000,
    val shell: String = "/bin/bash",
) : Serializable {
    val isRoot: Boolean
        get() = username == "root" || uid == 0

    val displayName: String
        get() = if (isRoot) "root (Administrator)" else "$username ($homeDir)"
}

enum class ContainerStatus {
    RUNNING,
    STOPPED,
    NOT_FOUND,
}

data class ContainerInfo(
    val name: String,
    val status: ContainerStatus,
    val pid: Int? = null,
    val rootfsPath: String? = null,
) : Serializable

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    val outLines: List<String>
        get() = stdout.lines().filter { it.isNotBlank() }
}
