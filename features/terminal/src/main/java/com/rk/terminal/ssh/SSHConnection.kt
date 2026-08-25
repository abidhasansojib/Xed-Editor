package com.rk.terminal.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe queue-based InputStream for JSch ChannelShell.
 * Holds typed characters indefinitely without timing out, throwing "read end dead",
 * or sending premature EOF to remote bash shell.
 */
class SSHQueueInputStream : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var currentBuffer: ByteArray? = null
    private var currentPos = 0
    @Volatile private var isClosed = false

    fun push(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        if (isClosed || count <= 0) return
        val copy = data.copyOfRange(offset, offset + count)
        queue.offer(copy)
    }

    fun push(text: String) {
        push(text.toByteArray(StandardCharsets.UTF_8))
    }

    override fun read(): Int {
        val b = ByteArray(1)
        val r = read(b, 0, 1)
        return if (r <= 0) -1 else (b[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isClosed) return -1
        if (len == 0) return 0

        while (currentBuffer == null || currentPos >= currentBuffer!!.size) {
            if (isClosed) return -1
            try {
                val next = queue.poll(300, TimeUnit.MILLISECONDS)
                if (next != null) {
                    if (next.isEmpty()) {
                        // EOF marker
                        isClosed = true
                        return -1
                    }
                    currentBuffer = next
                    currentPos = 0
                    break
                }
            } catch (_: InterruptedException) {
                if (isClosed) return -1
            }
        }

        val buf = currentBuffer ?: return -1
        val available = buf.size - currentPos
        val bytesToCopy = minOf(len, available)
        System.arraycopy(buf, currentPos, b, off, bytesToCopy)
        currentPos += bytesToCopy
        if (currentPos >= buf.size) {
            currentBuffer = null
            currentPos = 0
        }
        return bytesToCopy
    }

    override fun close() {
        isClosed = true
        queue.clear()
        queue.offer(ByteArray(0))
    }
}

/**
 * Manages an active SSH connection and interactive PTY shell channel using JSch.
 * Uses an unbounded non-blocking queue stream to prevent premature EOF and session disconnection.
 */
class SSHConnection(private val config: SSHConfig) {
    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var sshInputQueue: SSHQueueInputStream? = null
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
            session.serverAliveInterval = 15000
            session.serverAliveCountMax = 6

            session.connect(20000)
            jschSession = session

            val safeCols = if (cols < 20) 80 else cols
            val safeRows = if (rows < 5) 24 else rows

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(safeCols, safeRows, width, height)

            val inputQueue = SSHQueueInputStream()
            sshInputQueue = inputQueue
            channel.setInputStream(inputQueue, false)

            channel.setOutputStream(object : OutputStream() {
                override fun write(b: Int) {
                    onData(byteArrayOf(b.toByte()), 1)
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (len > 0) {
                        val copy = b.copyOfRange(off, off + len)
                        onData(copy, len)
                    }
                }
                override fun flush() {}
                override fun close() {
                    if (isConnectedFlag.get()) {
                        isConnectedFlag.set(false)
                        cleanup()
                        onDisconnect(null)
                    }
                }
            }, false)

            channel.connect(20000)
            shellChannel = channel
            isConnectedFlag.set(true)
        } catch (e: Exception) {
            isConnectedFlag.set(false)
            cleanup()
            onDisconnect(e.message ?: "SSH connection failed")
        }
    }

    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        sshInputQueue?.push(data, offset, count)
    }

    fun write(text: String) {
        sshInputQueue?.push(text)
    }

    fun resize(cols: Int, rows: Int, width: Int = 0, height: Int = 0) {
        val safeCols = if (cols < 20) 80 else cols
        val safeRows = if (rows < 5) 24 else rows
        try {
            shellChannel?.setPtySize(safeCols, safeRows, width, height)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun disconnect() {
        isConnectedFlag.set(false)
        cleanup()
    }

    private fun cleanup() {
        try {
            sshInputQueue?.close()
        } catch (_: Exception) {}
        sshInputQueue = null

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
