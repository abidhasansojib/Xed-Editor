package com.rk.terminal
 
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.activities.terminal.Terminal
import com.rk.settings.Settings
import com.rk.settings.terminal.TerminalCursorStyle
import com.rk.terminal.ssh.SSHTerminalBridgeRegistry
import com.rk.terminal.virtualkeys.SpecialButton
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class TerminalBackEnd : TerminalViewClient, TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.isNotBlank()) {
            if (Settings.use_ssh_terminal && session != null) {
                val bridge = SSHTerminalBridgeRegistry.getBridgeForSession(session)
                if (bridge != null && bridge.isConnected) {
                    bridge.write(clip)
                    return
                }
            }
            val emulator = terminalView.get()?.mEmulator ?: return
            emulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int {
        return when (Settings.terminal_cursor_style) {
            TerminalCursorStyle.BAR.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
            TerminalCursorStyle.UNDERLINE.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        }
    }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag.toString(), message.toString())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.toString(), message.toString())
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.toString(), message.toString())
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.toString(), message.toString())
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.toString(), message.toString())
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.printStackTrace()
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        terminalView.get()?.setTextSize(fontScale.toInt())
        return fontScale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showSoftInput()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return true
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (Settings.use_ssh_terminal) {
            val bridge = SSHTerminalBridgeRegistry.getBridgeForSession(session)
            if (bridge != null && bridge.isConnected) {
                val escapeSeq =
                    when (keyCode) {
                        KeyEvent.KEYCODE_ENTER -> "\r"
                        KeyEvent.KEYCODE_DEL -> "\u007F"
                        KeyEvent.KEYCODE_TAB -> "\t"
                        KeyEvent.KEYCODE_ESCAPE -> "\u001B"
                        KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D"
                        KeyEvent.KEYCODE_MOVE_HOME -> "\u001B[H"
                        KeyEvent.KEYCODE_MOVE_END -> "\u001B[F"
                        KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~"
                        KeyEvent.KEYCODE_PAGE_DOWN -> "\u001B[6~"
                        KeyEvent.KEYCODE_FORWARD_DEL -> "\u001B[3~"
                        else -> null
                    }
                if (escapeSeq != null) {
                    bridge.write(escapeSeq)
                    return true
                }
                if (e.unicodeChar != 0 && !Character.isISOControl(e.unicodeChar)) {
                    bridge.write(Character.toString(e.unicodeChar))
                    return true
                }
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val activity = Terminal.instance ?: return false
            val sessionBinder = activity.sessionBinder?.get() ?: return false
            sessionBinder.terminateSession(sessionBinder.getService().currentSession.value)
            if (sessionBinder.getService().sessionList.value.isEmpty()) {
                activity.finish()
            } else {
                activity.changeSession(sessionBinder.getService().sessionList.value.first())
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    // keys
    override fun readControlKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, true)
        return state != null && state
    }

    override fun readAltKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, true)
        return state != null && state
    }

    override fun readShiftKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, true)
        return state != null && state
    }

    override fun readFnKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, true)
        return state != null && state
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (Settings.use_ssh_terminal) {
            val bridge = SSHTerminalBridgeRegistry.getBridgeForSession(session)
            if (bridge != null && bridge.isConnected) {
                if (ctrlDown) {
                    val ctrlByte =
                        when (codePoint) {
                            in 97..122 -> (codePoint - 96).toByte() // a-z -> 1..26
                            in 65..90 -> (codePoint - 64).toByte() // A-Z -> 1..26
                            32 -> 0.toByte() // Ctrl+Space -> NUL
                            else -> null
                        }
                    if (ctrlByte != null) {
                        bridge.write(byteArrayOf(ctrlByte))
                        return true
                    }
                }
                val chars = Character.toChars(codePoint)
                bridge.write(String(chars))
                return true
            }
        }
        return false
    }

    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminalView.get()?.mEmulator != null) {
            terminalView.get()?.setTerminalCursorBlinkerState(start, true)
        }
    }

    private fun showSoftInput() {
        terminalView.get()?.requestFocus()
        terminalView.get()?.let { KeyboardUtils.showSoftInput(it) }
    }
}
