package com.rk.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.terminal.ssh.SSHTerminalBridgeRegistry
import com.termux.terminal.TerminalSession

class VirtualKeysListener(val session: TerminalSession) : VirtualKeysView.IVirtualKeysView {

    override fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) {
        val key = buttonInfo?.key ?: return
        val writeable: String =
            when (key) {
                "UP" -> "\u001B[A"
                "DOWN" -> "\u001B[B"
                "LEFT" -> "\u001B[D"
                "RIGHT" -> "\u001B[C"
                "ENTER" -> "\r"
                "PGUP" -> "\u001B[5~"
                "PGDN" -> "\u001B[6~"
                "TAB" -> "\t"
                "HOME" -> "\u001B[H"
                "END" -> "\u001B[F"
                "ESC" -> "\u001B"
                "BKSP" -> "\u007F"
                "DEL" -> "\u001B[3~"
                "INS" -> "\u001B[2~"
                else -> key
            }

        val bridge = SSHTerminalBridgeRegistry.getBridgeForSession(session)
        if (bridge != null) {
            if (!bridge.isConnected && key == "ENTER") {
                val emulator = session.emulator
                bridge.reconnect(emulator?.mColumns ?: 80, emulator?.mRows ?: 24)
                return
            }
            bridge.write(writeable)
            return
        }

        session.write(writeable)
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean {
        return false
    }
}
