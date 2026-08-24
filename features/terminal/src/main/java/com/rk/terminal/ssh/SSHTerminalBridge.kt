package com.rk.terminal.ssh

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges an interactive SSH connection with a Termux [TerminalSession] and its [com.termux.terminal.TerminalEmulator].
 */
class SSHTerminalBridge(
    val context: Context,
    val session: TerminalSession,
    val sessionClient: TerminalSessionClient,
    val sessionId: String,
    var initialCommand: String? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val active = AtomicBoolean(false)
    private var sshConnection: SSHConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    val isConnected: Boolean
        get() = active.get() && (sshConnection?.isConnected == true)

    fun appendToEmulator(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        appendToEmulator(bytes, bytes.size)
    }

    fun appendToEmulator(data: ByteArray, count: Int) {
        val copy = if (data.size == count) data.copyOf() else data.copyOf(count)
        mainHandler.post {
            try {
                session.emulator?.append(copy, copy.size)
                sessionClient.onTextChanged(session)
            } catch (_: Exception) {}
        }
    }

    fun start(cols: Int = 80, rows: Int = 24, width: Int = 0, height: Int = 0) {
        if (active.getAndSet(true)) return

        val config = SSHConfig.loadFromSettings()

        if (!config.isConfigured()) {
            appendToEmulator(
                "\r\n\u001B[1;33m[SSH Terminal Mode Active]\u001B[0m\r\n" +
                    "\u001B[0;31mSSH Host or Username is not configured.\u001B[0m\r\n" +
                    "\u001B[0;37mPlease go to Settings -> Terminal to configure your SSH credentials.\u001B[0m\r\n\r\n",
            )
            return
        }

        appendToEmulator(
            "\r\n\u001B[1;36mConnecting to ${config.username}@${config.host}:${config.port}...\u001B[0m\r\n",
        )

        val connection = SSHConnection(config)
        sshConnection = connection

        scope.launch {
            connection.connect(
                cols = cols,
                rows = rows,
                width = width,
                height = height,
                onData = { buffer, count ->
                    appendToEmulator(buffer, count)
                },
                onDisconnect = { reason ->
                    val msg =
                        if (reason != null) {
                            "\r\n\u001B[1;31m[SSH Connection error: $reason]\u001B[0m\r\n" +
                                "\u001B[0;33mCheck SSH credentials in Settings -> Terminal.\u001B[0m\r\n\r\n"
                        } else {
                            "\r\n\u001B[1;33m[SSH Session closed]\u001B[0m\r\n"
                        }
                    appendToEmulator(msg)
                    active.set(false)
                },
            )

            if (connection.isConnected) {
                appendToEmulator("\u001B[1;32mConnected to ${config.host}!\u001B[0m\r\n\r\n")

                val cmd = initialCommand
                if (!cmd.isNullOrBlank()) {
                    connection.write(cmd + "\n")
                    initialCommand = null
                }
            }
        }
    }

    fun resize(cols: Int, rows: Int, width: Int = 0, height: Int = 0) {
        sshConnection?.resize(cols, rows, width, height)
    }

    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        sshConnection?.write(data, offset, count)
    }

    fun write(text: String) {
        sshConnection?.write(text)
    }

    fun disconnect() {
        active.set(false)
        sshConnection?.disconnect()
        sshConnection = null
    }
}

object SSHTerminalBridgeRegistry {
    private val bridges = ConcurrentHashMap<String, SSHTerminalBridge>()

    fun register(sessionId: String, bridge: SSHTerminalBridge) {
        bridges[sessionId] = bridge
    }

    fun getBridge(sessionId: String): SSHTerminalBridge? {
        return bridges[sessionId]
    }

    fun getBridgeForSession(session: TerminalSession): SSHTerminalBridge? {
        return bridges.values.find { it.session == session }
    }

    fun remove(sessionId: String): SSHTerminalBridge? {
        val bridge = bridges.remove(sessionId)
        bridge?.disconnect()
        return bridge
    }

    fun removeAll() {
        bridges.values.forEach { it.disconnect() }
        bridges.clear()
    }
}
