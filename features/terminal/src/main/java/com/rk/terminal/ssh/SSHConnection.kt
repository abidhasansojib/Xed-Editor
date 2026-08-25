package com.rk.terminal.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Manages an active SSH connection and interactive PTY shell channel using JSch.
 * Uses direct channel.outputStream writing to ensure zero premature EOF signals
 * and indefinite long-term shell persistence.
 */
class SSHConnection(private val config: SSHConfig) {
    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var channelOutputStream: OutputStream? = null
    private var readerThread: Thread? = null
    private val isConnectedFlag = AtomicBoolean(false)

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
            session.setConfig(
                "server_host_key",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
            )
            session.setConfig(
                "PubkeyAcceptedAlgorithms",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
            )
            session.setConfig(
                "PubkeyAcceptedKeyTypes",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
            )
            session.setConfig(
                "cipher.s2c",
                "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes256-cbc,3des-cbc",
            )
            session.setConfig(
                "cipher.c2s",
                "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes256-cbc,3des-cbc",
            )
            session.setConfig(
                "kex",
                "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1",
            )
            session.setConfig("TCPKeepAlive", "yes")
            session.setConfig("KeepAlive", "yes")

            // Keep connection continuous
            session.timeout = 0
            session.serverAliveInterval = 10000
            session.serverAliveCountMax = 10

            session.connect(20000)
            jschSession = session

            val safeCols = if (cols < 20) 80 else cols
            val safeRows = if (rows < 5) 24 else rows

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(safeCols, safeRows, width, height)

            channel.setInputStream(null)
            val channelOut = channel.outputStream
            channelOutputStream = channelOut

            val inStream = channel.inputStream

            channel.connect(20000)
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
                                val chunk = buffer.copyOf(read)
                                onData(chunk, read)
                            }
                        }
                    } catch (e: Exception) {
                        if (isConnectedFlag.get()) {
                            disconnectReason = e.message ?: "Connection reset"
                        }
                    } finally {
                        if (isConnectedFlag.get()) {
                            isConnectedFlag.set(false)
                            cleanup()
                            onDisconnect(disconnectReason)
                        }
                    }
                }
        } catch (e: Exception) {
            isConnectedFlag.set(false)
            cleanup()
            onDisconnect(e.message ?: "SSH connection failed")
        }
    }

    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        if (!isConnectedFlag.get() || count <= 0) return
        try {
            channelOutputStream?.apply {
                write(data, offset, count)
                flush()
            }
        } catch (_: Exception) {}
    }

    fun write(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun resize(cols: Int, rows: Int, width: Int = 0, height: Int = 0) {
        val safeCols = if (cols < 20) 80 else cols
        val safeRows = if (rows < 5) 24 else rows
        try {
            if (isConnectedFlag.get() && shellChannel?.isConnected == true) {
                shellChannel?.setPtySize(safeCols, safeRows, width, height)
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun disconnect() {
        if (!isConnectedFlag.getAndSet(false)) return
        cleanup()
    }

    private fun cleanup() {
        try {
            channelOutputStream?.close()
        } catch (_: Exception) {}
        channelOutputStream = null

        try {
            shellChannel?.disconnect()
        } catch (_: Exception) {}
        shellChannel = null

        try {
            jschSession?.disconnect()
        } catch (_: Exception) {}
        jschSession = null

        readerThread?.interrupt()
        readerThread = null
    }

    companion object {
        suspend fun testConnection(config: SSHConfig): Result<String> =
            withContext(Dispatchers.IO) {
                if (!config.isConfigured()) {
                    return@withContext Result.failure(IllegalArgumentException("Host and username cannot be empty"))
                }

                var session: Session? = null
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
                    session.setConfig(
                        "server_host_key",
                        "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
                    )
                    session.setConfig(
                        "PubkeyAcceptedAlgorithms",
                        "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
                    )
                    session.setConfig(
                        "PubkeyAcceptedKeyTypes",
                        "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
                    )
                    session.setConfig(
                        "cipher.s2c",
                        "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes256-cbc,3des-cbc",
                    )
                    session.setConfig(
                        "cipher.c2s",
                        "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes256-cbc,3des-cbc",
                    )
                    session.setConfig(
                        "kex",
                        "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1",
                    )
                    session.timeout = 10000
                    session.connect(10000)

                    val serverVersion = session.serverVersion ?: "SSH-2.0"
                    Result.success("Connected successfully to ${config.host} ($serverVersion)")
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    try {
                        session?.disconnect()
                    } catch (_: Exception) {}
                }
            }
    }
}
