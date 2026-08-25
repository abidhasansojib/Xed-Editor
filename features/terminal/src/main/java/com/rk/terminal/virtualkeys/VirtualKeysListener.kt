package com.rk.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.terminal.TerminalBackEnd
import com.termux.terminal.TerminalSession

class VirtualKeysListener(val session: TerminalSession) : VirtualKeysView.IVirtualKeysView {

    override fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) {
        if (!session.isRunning) {
            TerminalBackEnd.handleSessionClose(session)
            return
        }

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
