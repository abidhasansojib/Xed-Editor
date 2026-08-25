package com.rk.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.terminal.virtualkeys.VirtualKeysView.IVirtualKeysView
import com.termux.terminal.TerminalSession

class VirtualKeyClient(val session: TerminalSession) : IVirtualKeysView {
    override fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) {
        val rawKey = buttonInfo?.key
        if (rawKey.isNullOrEmpty()) {
            return
        }
        val upperKey = rawKey.trim().uppercase()
        when (upperKey) {
            "ESC", "ESCAPE" -> session.write("\u001B")
            "TAB" -> session.write("\u0009")
            "HOME" -> session.write("\u001B[H")
            "END" -> session.write("\u001B[F")
            "UP" -> session.write("\u001B[A")
            "DOWN" -> session.write("\u001B[B")
            "LEFT" -> session.write("\u001B[D")
            "RIGHT" -> session.write("\u001B[C")
            "PGUP", "PAGEUP" -> session.write("\u001B[5~")
            "PGDN", "PAGEDOWN" -> session.write("\u001B[6~")
            "ENTER" -> session.write("\r")
            "BKSP", "BACKSPACE" -> session.write("\u007F")
            "DEL", "DELETE" -> session.write("\u001B[3~")
            "INS", "INSERT" -> session.write("\u001B[2~")
            "CLEAR" -> session.write("\u000C")
            "F1" -> session.write("\u001BOP")
            "F2" -> session.write("\u001BOQ")
            "F3" -> session.write("\u001BOR")
            "F4" -> session.write("\u001BOS")
            "F5" -> session.write("\u001B[15~")
            "F6" -> session.write("\u001B[17~")
            "F7" -> session.write("\u001B[18~")
            "F8" -> session.write("\u001B[19~")
            "F9" -> session.write("\u001B[20~")
            "F10" -> session.write("\u001B[21~")
            "F11" -> session.write("\u001B[23~")
            "F12" -> session.write("\u001B[24~")
            else -> session.write(buttonInfo.key)
        }
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean {
        return false
    }
}
