package com.rk.terminal

import android.content.Context
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.rk.settings.Settings
import com.rk.terminal.ssh.SSHTerminalBridge
import com.rk.terminal.ssh.SSHTerminalBridgeRegistry
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Global singleton managing continuous SSH Terminal sessions across Activity lifecycle.
 * Prevents duplicating sessions when reopening the terminal from 3-dot menu or home page.
 */
object SSHTerminalSessionManager {
    private val sessions = LinkedHashMap<String, TerminalSession>()
    private val bridges = ConcurrentHashMap<String, SSHTerminalBridge>()

    private val _sessionList = MutableStateFlow<List<String>>(emptyList())
    val sessionList: StateFlow<List<String>> = _sessionList.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    fun hasActiveSessions(): Boolean = sessions.isNotEmpty()

    fun getSession(sessionId: String): TerminalSession? = sessions[sessionId]

    fun getCurrentSession(): TerminalSession? {
        val id = currentSessionId.value
        return if (id.isNotEmpty()) sessions[id] else sessions.values.firstOrNull()
    }

    fun getOrCreateSession(
        context: Context,
        client: TerminalSessionClient,
        requestedId: String? = null,
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
            bridges[id]?.let { bridge ->
                bridge.sessionClient = client
                if (!bridge.isConnected) {
                    bridge.start(
                        existingSession.emulator?.mColumns ?: 80,
                        existingSession.emulator?.mRows ?: 24,
                    )
                }
            }
            _currentSessionId.value = id
            return existingSession
        }

        // Create new TerminalSession
        val session = TerminalSession(
            Settings.terminal_scrollback_buffer,
            client,
        )

        val bridge = SSHTerminalBridge(
            context = context.applicationContext,
            session = session,
            sessionClient = client,
            sessionId = id,
            initialCommand = initialCommand,
        )

        // Set custom writer so typing from soft keyboard and virtual keys forwards to SSH!
        session.setCustomWriter(object : TerminalSession.TerminalSessionWriter {
            override fun write(data: ByteArray, offset: Int, count: Int) {
                bridge.write(data, offset, count)
            }

            override fun onResize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
                bridge.resize(columns, rows, cellWidthPixels, cellHeightPixels)
            }
        })

        bridges[id] = bridge
        SSHTerminalBridgeRegistry.register(id, bridge)
        sessions[id] = session

        updateSessionList()
        _currentSessionId.value = id

        bridge.start(
            cols = session.emulator?.mColumns ?: 80,
            rows = session.emulator?.mRows ?: 24,
        )

        return session
    }

    fun createNewTabSession(
        context: Context,
        client: TerminalSessionClient,
        initialCommand: String? = null,
    ): TerminalSession {
        var index = 1
        var newId = "main #$index"
        while (sessions.containsKey(newId)) {
            index++
            newId = "main #$index"
        }

        val session = TerminalSession(
            Settings.terminal_scrollback_buffer,
            client,
        )

        val bridge = SSHTerminalBridge(
            context = context.applicationContext,
            session = session,
            sessionClient = client,
            sessionId = newId,
            initialCommand = initialCommand,
        )

        session.setCustomWriter(object : TerminalSession.TerminalSessionWriter {
            override fun write(data: ByteArray, offset: Int, count: Int) {
                bridge.write(data, offset, count)
            }

            override fun onResize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
                bridge.resize(columns, rows, cellWidthPixels, cellHeightPixels)
            }
        })

        bridges[newId] = bridge
        SSHTerminalBridgeRegistry.register(newId, bridge)
        sessions[newId] = session

        updateSessionList()
        _currentSessionId.value = newId

        bridge.start(
            cols = session.emulator?.mColumns ?: 80,
            rows = session.emulator?.mRows ?: 24,
        )

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
        val bridge = bridges.remove(oldId)

        sessions[newId] = session
        if (bridge != null) {
            SSHTerminalBridgeRegistry.remove(oldId)
            bridges[newId] = bridge
            SSHTerminalBridgeRegistry.register(newId, bridge)
        }

        if (_currentSessionId.value == oldId) {
            _currentSessionId.value = newId
        }

        updateSessionList()
        return true
    }

    fun removeSession(sessionId: String) {
        val bridge = bridges.remove(sessionId)
        bridge?.disconnect()
        SSHTerminalBridgeRegistry.remove(sessionId)

        val session = sessions.remove(sessionId)
        session?.finishIfRunning()

        updateSessionList()

        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = sessions.keys.firstOrNull() ?: ""
        }
    }

    fun terminateSession(sessionId: String) = removeSession(sessionId)

    fun terminateAll() {
        bridges.values.forEach { it.disconnect() }
        bridges.clear()
        SSHTerminalBridgeRegistry.removeAll()

        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()

        updateSessionList()
        _currentSessionId.value = ""
    }

    private fun updateSessionList() {
        val list = sessions.keys.toList()
        runOnUiThread {
            _sessionList.value = list
        }
    }
}
