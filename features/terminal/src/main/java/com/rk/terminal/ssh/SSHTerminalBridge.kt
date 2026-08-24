package com.rk.terminal.ssh

import android.content.Context
import android.os.Handler
import com.rk.settings.Settings
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Bridges an interactive SSH connection with a Termux [TerminalSession] and its [TerminalEmulator].
 */
class SSHTerminalBridge(
    val context: Context,
    val session: TerminalSession,
    val sessionClient: TerminalSessionClient,
    val sessionId: String,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val active = AtomicBoolean(false)
    private var sshConnection: SSHConnection? = null
    private var writerThread: Thread? = null

    private var terminalQueueField: Field? = null
    private var mainHandlerField: Field? = null
    private var emulatorField: Field? = null

    init {
        try {
            terminalQueueField = TerminalSession::class.java.getDeclaredField("mTerminalToProcessIOQueue").apply {
                isAccessible = true
            }
            mainHandlerField = TerminalSession::class.java.getDeclaredField("mMainThreadHandler").apply {
                isAccessible = true
            }
            emulatorField = TerminalSession::class.java.getDeclaredField("mEmulator").apply {
                isAccessible = true
            }
        } catch (_: Exception) {}
    }

    private fun getEmulator(): TerminalEmulator? {
        return session.emulator ?: runCatching { emulatorField?.get(session) as? TerminalEmulator }.getOrNull()
    }

    private fun getMainHandler(): Handler? {
        return runCatching { mainHandlerField?.get(session) as? Handler }.getOrNull()
    }

    private fun appendToEmulator(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        appendToEmulator(bytes, bytes.size)
    }

    private fun appendToEmulator(data: ByteArray, count: Int) {
        val handler = getMainHandler()
        if (handler != null) {
            handler.post {
                getEmulator()?.append(data, count)
                sessionClient.onTextChanged(session)
            }
        } else {
            getEmulator()?.append(data, count)
            sessionClient.onTextChanged(session)
        }
    }

    fun start(cols: Int = 80, rows: Int = 24, width: Int = 0, height: Int = 0) {
        if (active.getAndSet(true)) return

        val config = SSHConfig.loadFromSettings()

        if (!config.isConfigured()) {
            appendToEmulator(
                "\r\n\u001B[1;33m[SSH Terminal Mode Active]\u001B[0m\r\n" +
                    "\u001B[0;31mSSH Host or Username is not configured.\u001B[0m\r\n" +
                    "\u001B[0;37mPlease go to Settings -> Terminal to configure your SSH credentials.\u001B[0m\r\n\r\n"
            )
            return
        }

        appendToEmulator(
            "\r\n\u001B[1;36mConnecting to ${config.username}@${config.host}:${config.port}...\u001B[0m\r\n"
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
                    val handler = getMainHandler()
                    handler?.post {
                        val msg =
                            if (reason != null) {
                                "\r\n\u001B[1;31m[SSH Connection error: $reason]\u001B[0m\r\n"
                            } else {
                                "\r\n\u001B[1;33m[SSH Session closed]\u001B[0m\r\n"
                            }
                        appendToEmulator(msg)
                        sessionClient.onSessionFinished(session)
                    }
                    active.set(false)
                },
            )

            if (connection.isConnected) {
                appendToEmulator("\u001B[1;32mConnected to ${config.host}!\u001B[0m\r\n\r\n")
            }
        }

        // Start writer thread to forward user keystrokes from terminal session queue to SSH
        writerThread =
            thread(name = "SSH-Writer-$sessionId", isDaemon = true) {
                val buf = ByteArray(4096)
                try {
                    val queue = terminalQueueField?.get(session)
                    if (queue != null) {
                        val readMethod = queue.javaClass.getMethod("read", ByteArray::class.java, Boolean::class.javaPrimitiveType)
                        while (active.get()) {
                            val bytesRead = readMethod.invoke(queue, buf, true) as? Int ?: -1
                            if (bytesRead == -1) break
                            if (bytesRead > 0) {
                                sshConnection?.write(buf, 0, bytesRead)
                            }
                        }
                    }
                } catch (_: Exception) {}
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
        writerThread?.interrupt()
        writerThread = null
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
