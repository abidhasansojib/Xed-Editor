package com.rk.terminal

import android.content.Context
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.rk.droidspaces.DroidspacesConstants
import com.rk.droidspaces.DroidspacesManager
import com.rk.settings.Settings
import com.rk.utils.application
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Global singleton managing continuous Droidspaces Terminal sessions across Activity lifecycle.
 * Prevents duplicating sessions when reopening the terminal from 3-dot menu or home page.
 */
object DroidspacesTerminalSessionManager {
    private val sessions = LinkedHashMap<String, TerminalSession>()
    private val sessionUsers = ConcurrentHashMap<String, String>()
    private val sessionContainers = ConcurrentHashMap<String, String>()

    private val _sessionList = MutableStateFlow<List<String>>(emptyList())
    val sessionList: StateFlow<List<String>> = _sessionList.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    fun hasActiveSessions(): Boolean = sessions.isNotEmpty()

    fun getSession(sessionId: String): TerminalSession? = sessions[sessionId]

    fun findSessionId(session: TerminalSession): String? = sessions.entries.firstOrNull { it.value == session }?.key

    fun getCurrentSession(): TerminalSession? {
        val id = currentSessionId.value
        return if (id.isNotEmpty()) sessions[id] else sessions.values.firstOrNull()
    }

    fun isAndroidRootSession(sessionId: String): Boolean =
        sessionUsers[sessionId] == DroidspacesConstants.ANDROID_ROOT_USER ||
            sessionContainers[sessionId] == DroidspacesConstants.ANDROID_CONTAINER_NAME

    private fun buildSessionEnv(user: String): Array<String> {
        val env = mutableListOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "HOME=${if (user == "root") "/root" else "/home/$user"}",
            "PATH=${DroidspacesConstants.INSTALL_PATH}:/sbin:/system/bin:/system/xbin:${System.getenv("PATH") ?: ""}",
        )
        System.getenv("EXTERNAL_STORAGE")?.let { env.add("EXTERNAL_STORAGE=$it") }
        listOf(
            "ANDROID_ART_ROOT", "ANDROID_DATA", "ANDROID_I18N_ROOT",
            "ANDROID_ROOT", "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT",
        ).forEach { key ->
            System.getenv(key)?.let { env.add("$key=$it") }
        }
        return env.toTypedArray()
    }

    private fun buildAndroidRootEnv(): Array<String> {
        val env = mutableListOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "HOME=/sdcard",
            "PATH=/sbin:/system/bin:/system/xbin:${DroidspacesConstants.INSTALL_PATH}:${System.getenv("PATH") ?: ""}",
        )
        System.getenv("EXTERNAL_STORAGE")?.let { env.add("EXTERNAL_STORAGE=$it") }
        listOf(
            "ANDROID_ART_ROOT", "ANDROID_DATA", "ANDROID_I18N_ROOT",
            "ANDROID_ROOT", "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT",
        ).forEach { key ->
            System.getenv(key)?.let { env.add("$key=$it") }
        }
        return env.toTypedArray()
    }

    fun getOrCreateSession(
        context: Context,
        client: TerminalSessionClient,
        requestedId: String? = null,
        containerName: String? = null,
        user: String? = null,
        initialCommand: String? = null,
    ): TerminalSession {
        val isAndroidRoot = user == DroidspacesConstants.ANDROID_ROOT_USER || containerName == DroidspacesConstants.ANDROID_CONTAINER_NAME

        val effectiveContainer = if (isAndroidRoot) {
            DroidspacesConstants.ANDROID_CONTAINER_NAME
        } else {
            containerName ?: Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
        }
        val effectiveUser = if (isAndroidRoot) {
            DroidspacesConstants.ANDROID_ROOT_USER
        } else {
            user ?: Settings.droidspaces_terminal_default_user.ifBlank { "root" }
        }

        val matchingSessionId = if (requestedId != null) {
            requestedId
        } else {
            val curId = currentSessionId.value
            if (curId.isNotEmpty() && sessions.containsKey(curId)) {
                val curIsAndroid = isAndroidRootSession(curId)
                val curContainer = sessionContainers[curId] ?: ""
                if (curIsAndroid == isAndroidRoot && (isAndroidRoot || curContainer.equals(effectiveContainer, ignoreCase = true))) {
                    curId
                } else {
                    sessions.keys.firstOrNull { sid ->
                        val isAnd = isAndroidRootSession(sid)
                        val cont = sessionContainers[sid] ?: ""
                        isAnd == isAndroidRoot && (isAndroidRoot || cont.equals(effectiveContainer, ignoreCase = true))
                    }
                }
            } else {
                sessions.keys.firstOrNull { sid ->
                    val isAnd = isAndroidRootSession(sid)
                    val cont = sessionContainers[sid] ?: ""
                    isAnd == isAndroidRoot && (isAndroidRoot || cont.equals(effectiveContainer, ignoreCase = true))
                }
            }
        }

        val id = matchingSessionId ?: run {
            val prefix = if (isAndroidRoot) "Android Root" else effectiveUser
            var index = 1
            var candidate = "$prefix #$index"
            while (sessions.containsKey(candidate)) {
                index++
                candidate = "$prefix #$index"
            }
            candidate
        }

        sessions[id]?.let { existingSession ->
            existingSession.updateTerminalSessionClient(client)
            _currentSessionId.value = id
            if (!initialCommand.isNullOrBlank()) {
                runCommandInSession(id, initialCommand, delayMs = 150L)
            }
            return existingSession
        }

        val session = if (isAndroidRoot) {
            TerminalSession(
                "/system/bin/sh",
                "/sdcard",
                arrayOf("/system/bin/sh", "-c", "su || /system/bin/sh"),
                buildAndroidRootEnv(),
                Settings.terminal_scrollback_buffer,
                client,
            )
        } else {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = effectiveContainer.replace("'", "'\\''")
            val userArg = if (effectiveUser.isNotBlank()) " $effectiveUser" else ""
            val shArg = "su -c '$bin --name=\"$escapedName\" enter$userArg'"

            TerminalSession(
                "/system/bin/sh",
                "/sdcard",
                arrayOf("/system/bin/sh", "-c", shArg),
                buildSessionEnv(effectiveUser),
                Settings.terminal_scrollback_buffer,
                client,
            )
        }

        sessions[id] = session
        sessionUsers[id] = effectiveUser
        sessionContainers[id] = effectiveContainer

        updateSessionList()
        _currentSessionId.value = id

        SessionService.start(context)

        if (!initialCommand.isNullOrBlank()) {
            runCommandInSession(id, initialCommand, delayMs = 400L)
        }

        return session
    }

    fun createNewTabSession(
        context: Context,
        client: TerminalSessionClient,
        containerName: String? = null,
        user: String? = null,
        initialCommand: String? = null,
    ): TerminalSession {
        val isAndroidRoot = user == DroidspacesConstants.ANDROID_ROOT_USER || containerName == DroidspacesConstants.ANDROID_CONTAINER_NAME

        val effectiveContainer = if (isAndroidRoot) {
            DroidspacesConstants.ANDROID_CONTAINER_NAME
        } else {
            containerName ?: Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
        }
        val effectiveUser = if (isAndroidRoot) {
            DroidspacesConstants.ANDROID_ROOT_USER
        } else {
            user ?: Settings.droidspaces_terminal_default_user.ifBlank { "root" }
        }

        val prefix = if (isAndroidRoot) "Android Root" else effectiveUser
        var index = 1
        var newId = "$prefix #$index"
        while (sessions.containsKey(newId)) {
            index++
            newId = "$prefix #$index"
        }

        val session = if (isAndroidRoot) {
            TerminalSession(
                "/system/bin/sh",
                "/sdcard",
                arrayOf("/system/bin/sh", "-c", "su || /system/bin/sh"),
                buildAndroidRootEnv(),
                Settings.terminal_scrollback_buffer,
                client,
            )
        } else {
            val bin = DroidspacesManager.getDroidspacesBinary()
            val escapedName = effectiveContainer.replace("'", "'\\''")
            val userArg = if (effectiveUser.isNotBlank()) " $effectiveUser" else ""
            val shArg = "su -c '$bin --name=\"$escapedName\" enter$userArg'"

            TerminalSession(
                "/system/bin/sh",
                "/sdcard",
                arrayOf("/system/bin/sh", "-c", shArg),
                buildSessionEnv(effectiveUser),
                Settings.terminal_scrollback_buffer,
                client,
            )
        }

        sessions[newId] = session
        sessionUsers[newId] = effectiveUser
        sessionContainers[newId] = effectiveContainer

        updateSessionList()
        _currentSessionId.value = newId

        SessionService.update(context)

        if (!initialCommand.isNullOrBlank()) {
            runCommandInSession(newId, initialCommand, delayMs = 400L)
        }

        return session
    }

    fun runCommandInSession(sessionId: String, command: String, delayMs: Long = 200L) {
        val session = sessions[sessionId] ?: return
        if (delayMs > 0L) {
            Thread {
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {}
                session.write(command + "\n")
            }.start()
        } else {
            session.write(command + "\n")
        }
    }

    fun runCommandInCurrentSession(command: String, delayMs: Long = 200L) {
        val id = _currentSessionId.value
        if (id.isNotEmpty()) {
            runCommandInSession(id, command, delayMs)
        } else {
            val firstSession = sessions.values.firstOrNull() ?: return
            if (delayMs > 0L) {
                Thread {
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {}
                    firstSession.write(command + "\n")
                }.start()
            } else {
                firstSession.write(command + "\n")
            }
        }
    }

    fun switchSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            _currentSessionId.value = sessionId
        }
    }

    fun renameSession(oldId: String, newId: String): Boolean {
        if (oldId == newId || newId.isBlank() || sessions.containsKey(newId)) return false
        val session = sessions.remove(oldId) ?: return false
        val u = sessionUsers.remove(oldId) ?: "root"
        val c = sessionContainers.remove(oldId) ?: "Ubuntu"

        sessions[newId] = session
        sessionUsers[newId] = u
        sessionContainers[newId] = c

        if (_currentSessionId.value == oldId) {
            _currentSessionId.value = newId
        }

        updateSessionList()
        application?.let { SessionService.update(it) }
        return true
    }

    fun removeSession(sessionId: String) {
        sessionUsers.remove(sessionId)
        sessionContainers.remove(sessionId)

        val session = sessions.remove(sessionId)
        session?.finishIfRunning()

        updateSessionList()

        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = sessions.keys.firstOrNull() ?: ""
        }

        val app = application
        if (app != null) {
            if (sessions.isEmpty()) {
                SessionService.stop(app)
            } else {
                SessionService.update(app)
            }
        }
    }

    fun terminateSession(sessionId: String) = removeSession(sessionId)

    fun terminateAll() {
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        sessionUsers.clear()
        sessionContainers.clear()

        updateSessionList()
        _currentSessionId.value = ""

        application?.let { SessionService.stop(it) }
    }

    private fun updateSessionList() {
        val list = sessions.keys.toList()
        runOnUiThread {
            _sessionList.value = list
        }
    }
}
