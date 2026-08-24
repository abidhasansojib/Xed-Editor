package com.rk.terminal.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Manages an active SSH connection and interactive PTY shell channel using JSch.
 */
class SSHConnection(private val config: SSHConfig) {
    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private val isConnectedFlag = AtomicBoolean(false)
    private var readerThread: Thread? = null

    val isConnected: Boolean
        get() = isConnectedFlag.get() && (shellChannel?.isConnected == true)

    @Synchronized
    fun connect(
        cols: Int = 80,
        rows: Int = 24,
        width: Int = 0,
        height: Int = 0,
        onData: (ByteArray, Int) -> Unit,
        onDisconnect: (String?) -> Unit,
    ) {
        if (isConnectedFlag.get()) return

        try {
            val jsch = JSch()

            if (config.authType == "key" && config.privateKey.isNotBlank()) {
                val keyBytes = config.privateKey.trim().toByteArray(StandardCharsets.UTF_8)
                val passphraseBytes =
                    if (config.passphrase.isNotEmpty()) {
                        config.passphrase.toByteArray(StandardCharsets.UTF_8)
                    } else null

                jsch.addIdentity("xed_key", keyBytes, null, passphraseBytes)
            }

            val session = jsch.getSession(config.username, config.host, config.port)
            if (config.authType == "password" && config.password.isNotEmpty()) {
                session.setPassword(config.password)
            }

            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig(
                "PreferredAuthentications",
                if (config.authType == "key") "publickey,keyboard-interactive,password"
                else "password,keyboard-interactive,publickey",
            )
            session.timeout = 20000

            session.connect(15000)
            jschSession = session

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(cols, rows, width, height)
            channel.setEnv("TERM", "xterm-256color")
            channel.setEnv("COLORTERM", "truecolor")
            channel.setEnv("LANG", "C.UTF-8")

            val inStream: InputStream = channel.inputStream
            outputStream = channel.outputStream

            channel.connect(15000)
            shellChannel = channel
            isConnectedFlag.set(true)

            readerThread =
                thread(name = "SSH-Reader-${config.host}", isDaemon = true) {
                    val buffer = ByteArray(4096)
                    var disconnectReason: String? = null
                    try {
                        while (isConnectedFlag.get() && channel.isConnected) {
                            val read = inStream.read(buffer)
                            if (read == -1) break
                            if (read > 0) {
                                onData(buffer, read)
                            }
                        }
                    } catch (e: Exception) {
                        if (isConnectedFlag.get()) {
                            disconnectReason = e.message ?: "Connection reset"
                        }
                    } finally {
                        isConnectedFlag.set(false)
                        cleanup()
                        onDisconnect(disconnectReason)
                    }
                }
        } catch (e: Exception) {
            isConnectedFlag.set(false)
            cleanup()
            onDisconnect(e.message ?: "SSH connection failed")
        }
    }

    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        if (!isConnected) return
        try {
            outputStream?.let { out ->
                out.write(data, offset, count)
                out.flush()
            }
        } catch (_: Exception) {
            disconnect()
        }
    }

    fun write(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun resize(cols: Int, rows: Int, width: Int = 0, height: Int = 0) {
        if (!isConnected) return
        try {
            shellChannel?.setPtySize(cols, rows, width, height)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun disconnect() {
        isConnectedFlag.set(false)
        cleanup()
    }

    private fun cleanup() {
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null

        try {
            shellChannel?.disconnect()
        } catch (_: Exception) {}
        shellChannel = null

        try {
            jschSession?.disconnect()
        } catch (_: Exception) {}
        jschSession = null
    }

    companion object {
        suspend fun testConnection(config: SSHConfig): Result<String> =
            withContext(Dispatchers.IO) {
                if (!config.isConfigured()) {
                    return@withContext Result.failure(IllegalArgumentException("Host and username cannot be empty"))
                }

                var session: Session? = null
                var channel: ChannelExec? = null
                try {
                    val jsch = JSch()

                    if (config.authType == "key" && config.privateKey.isNotBlank()) {
                        val keyBytes = config.privateKey.trim().toByteArray(StandardCharsets.UTF_8)
                        val passphraseBytes =
                            if (config.passphrase.isNotEmpty()) {
                                config.passphrase.toByteArray(StandardCharsets.UTF_8)
                            } else null

                        jsch.addIdentity("xed_test_key", keyBytes, null, passphraseBytes)
                    }

                    session = jsch.getSession(config.username, config.host, config.port)
                    if (config.authType == "password" && config.password.isNotEmpty()) {
                        session.setPassword(config.password)
                    }

                    session.setConfig("StrictHostKeyChecking", "no")
                    session.setConfig(
                        "PreferredAuthentications",
                        if (config.authType == "key") "publickey,keyboard-interactive,password"
                        else "password,keyboard-interactive,publickey",
                    )
                    session.timeout = 10000
                    session.connect(10000)

                    channel = session.openChannel("exec") as ChannelExec
                    channel.setCommand("uname -a || echo ok")
                    channel.connect(10000)

                    val output = channel.inputStream.bufferedReader().use { it.readText().trim() }
                    val banner = if (output.isNotBlank()) output.take(80) else "Connected"

                    Result.success("Success: $banner")
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    try {
                        channel?.disconnect()
                    } catch (_: Exception) {}
                    try {
                        session?.disconnect()
                    } catch (_: Exception) {}
                }
            }
    }
}
