package com.rk.terminal.ssh

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Custom [TerminalSession] subclass for interactive SSH terminal sessions.
 * Intercepts all keyboard and input writes from TerminalView and routes them directly to [SSHTerminalBridge].
 */
class SSHTerminalSession(
    shellPath: String,
    cwd: String,
    args: Array<String>,
    env: Array<String>,
    transcriptRows: Int,
    client: TerminalSessionClient,
) : TerminalSession(shellPath, cwd, args, env, transcriptRows, client) {

    var bridge: SSHTerminalBridge? = null

    override fun write(data: ByteArray, offset: Int, count: Int) {
        val b = bridge
        if (b != null && b.isConnected) {
            b.write(data, offset, count)
        } else {
            super.write(data, offset, count)
        }
    }

    override fun write(data: String) {
        val b = bridge
        if (b != null && b.isConnected) {
            b.write(data)
        } else {
            super.write(data)
        }
    }
}
