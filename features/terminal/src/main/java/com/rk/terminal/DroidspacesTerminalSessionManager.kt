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

    fun getOrCreateSession(
        context: Context,
        client: TerminalSessionClient,
        requestedId: String? = null,
        containerName: String? = null,
        user: String? = null,
        initialCommand: String? = null,
    ): TerminalSession {
        val id = requestedId ?: if (sessions.isNotEmpty()) {
            if (currentSessionId.value.isNotEmpty() && sessions.containsKey(currentSessionId.value)) {
                currentSessionId.value
            } else {
                sessions.keys.first()
            }
        } else {
            "main #1"
        }

        sessions[id]?.let { existingSession ->
            existingSession.updateTerminalSessionClient(client)
            _currentSessionId.value = id
            return existingSession
        }

        val effectiveContainer = containerName ?: Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
        val effectiveUser = user ?: Settings.droidspaces_terminal_default_user.ifBlank { "root" }

        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = effectiveContainer.replace("'", "'\\''")
        val userArg = if (effectiveUser.isNotBlank()) " $effectiveUser" else ""
        val shArg = "su -c '$bin --name=\"$escapedName\" enter$userArg'"

        val session = TerminalSession(
            "/system/bin/sh",
            "/sdcard",
            arrayOf("/system/bin/sh", "-c", shArg),
            buildSessionEnv(effectiveUser),
            Settings.terminal_scrollback_buffer,
            client,
        )

        sessions[id] = session
        sessionUsers[id] = effectiveUser
        sessionContainers[id] = effectiveContainer

        updateSessionList()
        _currentSessionId.value = id

        SessionService.start(context)

        if (!initialCommand.isNullOrBlank()) {
            session.write(initialCommand + "\n")
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
        val effectiveContainer = containerName ?: Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
        val effectiveUser = user ?: Settings.droidspaces_terminal_default_user.ifBlank { "root" }

        var index = 1
        var newId = "$effectiveUser #$index"
        while (sessions.containsKey(newId)) {
            index++
            newId = "$effectiveUser #$index"
        }

        val bin = DroidspacesManager.getDroidspacesBinary()
        val escapedName = effectiveContainer.replace("'", "'\\''")
        val userArg = if (effectiveUser.isNotBlank()) " $effectiveUser" else ""
        val shArg = "su -c '$bin --name=\"$escapedName\" enter$userArg'"

        val session = TerminalSession(
            "/system/bin/sh",
            "/sdcard",
            arrayOf("/system/bin/sh", "-c", shArg),
            buildSessionEnv(effectiveUser),
            Settings.terminal_scrollback_buffer,
            client,
        )

        sessions[newId] = session
        sessionUsers[newId] = effectiveUser
        sessionContainers[newId] = effectiveContainer

        updateSessionList()
        _currentSessionId.value = newId

        SessionService.update(context)

        if (!initialCommand.isNullOrBlank()) {
            session.write(initialCommand + "\n")
        }

        return session
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
