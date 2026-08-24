package com.rk.terminal.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Thread-safe unbounded InputStream backed by a blocking queue.
 * Completely immune to Java's PipedInputStream thread-affinity ("Read/Write end dead") crashes.
 */
class QueueInputStream : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var currentBuffer: ByteArray? = null
    private var currentPos = 0
    private val closed = AtomicBoolean(false)

    fun enqueue(data: ByteArray) {
        if (!closed.get() && data.isNotEmpty()) {
            val copy = data.copyOf()
            queue.put(copy)
        }
    }

    override fun read(): Int {
        val b = ByteArray(1)
        val n = read(b, 0, 1)
        return if (n == -1) -1 else (b[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (closed.get() && queue.isEmpty() && currentBuffer == null) return -1

        while (currentBuffer == null || currentPos >= (currentBuffer?.size ?: 0)) {
            if (closed.get()) return -1
            val next = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
            currentBuffer = next
            currentPos = 0
        }

        val buf = currentBuffer ?: return -1
        val available = buf.size - currentPos
        val toRead = minOf(available, len)
        System.arraycopy(buf, currentPos, b, off, toRead)
        currentPos += toRead

        if (currentPos >= buf.size) {
            currentBuffer = null
            currentPos = 0
        }

        return toRead
    }

    override fun close() {
        closed.set(true)
    }
}

/**
 * Manages an active SSH connection and interactive PTY shell channel using JSch.
 */
class SSHConnection(private val config: SSHConfig) {
    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var queueIn: QueueInputStream? = null
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
            session.timeout = 20000

            session.connect(15000)
            jschSession = session

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(cols, rows, width, height)

            val qin = QueueInputStream()
            queueIn = qin
            channel.setInputStream(qin)

            val inStream: InputStream = channel.inputStream

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
        val chunk = if (offset == 0 && count == data.size) data else data.copyOfRange(offset, offset + count)
        queueIn?.enqueue(chunk)
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
            queueIn?.close()
        } catch (_: Exception) {}
        queueIn = null

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
                    session.setConfig(
                        "server_host_key",
                        "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa",
                    )
                    session.setConfig(
                        "PubkeyAcceptedAlgorithms",
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
