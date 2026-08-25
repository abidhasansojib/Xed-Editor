package com.rk.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.activities.terminal.Terminal
import com.rk.terminal.TerminalBackEnd
import com.termux.terminal.TerminalSession

class VirtualKeysListener(val session: TerminalSession) : VirtualKeysView.IVirtualKeysView {

    override fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) {
        if (!session.isRunning) {
            TerminalBackEnd.handleSessionClose(session)
            return
        }

        val rawKey = buttonInfo?.key ?: return
        val trimmed = rawKey.trim()
        val upperKey = trimmed.uppercase()

        val virtualKeysView = Terminal.instance?.virtualKeysView?.get()
        val ctrlActive = virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true) == true
        val altActive = virtualKeysView?.readSpecialButton(SpecialButton.ALT, true) == true
        val shiftActive = virtualKeysView?.readSpecialButton(SpecialButton.SHIFT, true) == true

        // Handle CTRL shortcuts like "CTRL+L", "CTRL+C", "CTRL+D", "CTRL+Z", "C-c", etc.
        if (upperKey.startsWith("CTRL+") || upperKey.startsWith("CTRL-") || upperKey.startsWith("C-")) {
            val subKey = trimmed.substringAfter('+').substringAfter('-')
            sendCtrlKey(subKey, altActive)
            return
        }

        // Handle ESC / ALT / META shortcuts like "ESC+*", "ESC+.", "ESC+D", "ESC-b", "ALT+*", "M-*", etc.
        if (upperKey.startsWith("ESC+") || upperKey.startsWith("ESC-") ||
            upperKey.startsWith("ALT+") || upperKey.startsWith("ALT-") ||
            upperKey.startsWith("A-") || upperKey.startsWith("M-") ||
            upperKey.startsWith("META+") || upperKey.startsWith("META-")) {
            val subKey = trimmed.substringAfter('+').substringAfter('-')
            sendAltKey(subKey)
            return
        }

        // Handle named keys and escape sequences
        val writeable: String? =
            when (upperKey) {
                "UP" -> if (ctrlActive) "\u001B[1;5A" else if (altActive) "\u001B[1;3A" else "\u001B[A"
                "DOWN" -> if (ctrlActive) "\u001B[1;5B" else if (altActive) "\u001B[1;3B" else "\u001B[B"
                "LEFT" -> if (ctrlActive) "\u001B[1;5D" else if (altActive) "\u001B[1;3D" else "\u001B[D"
                "RIGHT" -> if (ctrlActive) "\u001B[1;5C" else if (altActive) "\u001B[1;3C" else "\u001B[C"
                "ENTER" -> if (altActive) "\u001B\r" else "\r"
                "PGUP", "PAGEUP", "PAGE_UP" -> if (shiftActive) "\u001B[5;2~" else "\u001B[5~"
                "PGDN", "PAGEDOWN", "PAGE_DOWN" -> if (shiftActive) "\u001B[6;2~" else "\u001B[6~"
                "TAB" -> if (shiftActive) "\u001B[Z" else "\t"
                "HOME" -> if (ctrlActive) "\u001B[1;5H" else "\u001B[H"
                "END" -> if (ctrlActive) "\u001B[1;5F" else "\u001B[F"
                "ESC", "ESCAPE" -> "\u001B"
                "BKSP", "BACKSPACE" -> if (altActive) "\u001B\u007F" else if (ctrlActive) "\u0008" else "\u007F"
                "DEL", "DELETE" -> if (ctrlActive) "\u001B[3;5~" else if (altActive) "\u001B[3;3~" else "\u001B[3~"
                "INS", "INSERT" -> "\u001B[2~"
                "CLEAR" -> "\u000C" // Form feed (Ctrl+L) to clear terminal
                "F1" -> "\u001BOP"
                "F2" -> "\u001BOQ"
                "F3" -> "\u001BOR"
                "F4" -> "\u001BOS"
                "F5" -> "\u001B[15~"
                "F6" -> "\u001B[17~"
                "F7" -> "\u001B[18~"
                "F8" -> "\u001B[19~"
                "F9" -> "\u001B[20~"
                "F10" -> "\u001B[21~"
                "F11" -> "\u001B[23~"
                "F12" -> "\u001B[24~"
                else -> null
            }

        if (writeable != null) {
            session.write(writeable)
            return
        }

        // Apply active CTRL modifier to single character or key
        if (ctrlActive) {
            sendCtrlKey(rawKey, altActive)
            return
        }

        // Apply active ALT modifier (or ESC prefix)
        if (altActive) {
            sendAltKey(rawKey)
            return
        }

        val textToSend = if (shiftActive && rawKey.length == 1) rawKey.uppercase() else rawKey
        session.write(textToSend)
    }

    private fun sendCtrlKey(key: String, alt: Boolean = false) {
        if (key.isEmpty()) return
        val ch = key[0]
        val codePoint: Int =
            when {
                ch in 'a'..'z' -> ch - 'a' + 1
                ch in 'A'..'Z' -> ch - 'A' + 1
                ch == ' ' || ch == '@' || ch == '2' -> 0
                ch == '[' || ch == '3' -> 27
                ch == '\\' || ch == '4' -> 28
                ch == ']' || ch == '5' -> 29
                ch == '^' || ch == '6' -> 30
                ch == '_' || ch == '7' || ch == '/' || ch == '?' -> 31
                ch == '8' -> 127
                else -> ch.code
            }
        session.writeCodePoint(alt, codePoint)
    }

    private fun sendAltKey(key: String) {
        if (key.isEmpty()) return
        session.write("\u001B$key")
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?,
    ): Boolean {
        return false
    }
}
