package com.rk.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.activities.terminal.Terminal
import com.rk.settings.Settings
import com.rk.terminal.virtualkeys.SpecialButton
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class TerminalBackEnd : TerminalSessionClient, TerminalViewClient {

    override fun onTextChanged(changedSession: TerminalSession) {
        val activity = Terminal.instance ?: return
        activity.terminalView.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.isNotBlank()) {
            val activity = Terminal.instance ?: return
            val emulator = activity.terminalView.get()?.mEmulator ?: return
            emulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {
        val activity = Terminal.instance ?: return
        activity.terminalView.get()?.setTerminalCursorBlinkerState(state, true)
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun shouldSupportClipboardKeybindings(): Boolean {
        return Settings.terminal_clipboard_keybindings
    }

    override fun getTerminalCursorStyle(): Int {
        return when (Settings.terminal_cursor_style) {
            "underline" -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            "bar" -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
            else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        }
    }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: "Terminal", message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: "Terminal", message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: "Terminal", message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: "Terminal", message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: "Terminal", message ?: "")
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", message ?: "", e)
    }

    override fun onScale(scale: Float): Float {
        // Pinch zoom step scaling for terminal font size
        if (scale < 0.92f || scale > 1.08f) {
            val increase = scale > 1.0f
            val currentSize = Settings.terminal_font_size
            val newSize = if (increase) {
                (currentSize + 1).coerceAtMost(48)
            } else {
                (currentSize - 1).coerceAtLeast(8)
            }
            if (newSize != currentSize) {
                Settings.terminal_font_size = newSize
                val activity = Terminal.instance
                activity?.terminalView?.get()?.setTextSize(newSize)
            }
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val activity = Terminal.instance ?: return
        val session = activity.terminalView.get()?.mTermSession
        if (session != null && !session.isRunning) {
            handleSessionClose(session)
            return
        }
        showSoftInput()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (!session.isRunning) {
            handleSessionClose(session)
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean {
        val activity = Terminal.instance ?: return false
        val state = activity.virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, true)
        return state == true
    }

    override fun readAltKey(): Boolean {
        val activity = Terminal.instance ?: return false
        val state = activity.virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, true)
        return state == true
    }

    override fun readShiftKey(): Boolean {
        val activity = Terminal.instance ?: return false
        val state = activity.virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, true)
        return state == true
    }

    override fun readFnKey(): Boolean {
        val activity = Terminal.instance ?: return false
        val state = activity.virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, true)
        return state == true
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (!session.isRunning) {
            handleSessionClose(session)
            return true
        }
        return false
    }

    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        val activity = Terminal.instance ?: return
        if (activity.terminalView.get()?.mEmulator != null) {
            activity.terminalView.get()?.setTerminalCursorBlinkerState(start, true)
        }
    }

    private fun showSoftInput() {
        val activity = Terminal.instance ?: return
        val view = activity.terminalView.get() ?: return
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        KeyboardUtils.showSoftInput(view)
    }

    companion object {
        fun handleSessionClose(session: TerminalSession) {
            val activity = Terminal.instance ?: return
            activity.runOnUiThread {
                val sessionId = DroidspacesTerminalSessionManager.findSessionId(session)
                if (sessionId != null) {
                    val sessionList = DroidspacesTerminalSessionManager.sessionList.value
                    val isSelected = sessionId == DroidspacesTerminalSessionManager.currentSessionId.value
                    if (isSelected) {
                        val index = sessionList.indexOf(sessionId)
                        val sessionBefore = sessionList.getOrNull(index - 1)
                        val sessionAfter = sessionList.getOrNull(index + 1)
                        val neighborSession = sessionBefore ?: sessionAfter
                        if (neighborSession != null) {
                            activity.changeSession(neighborSession)
                        }
                    }
                    DroidspacesTerminalSessionManager.removeSession(sessionId)
                }

                if (DroidspacesTerminalSessionManager.sessionList.value.isEmpty()) {
                    activity.finish()
                }
            }
        }
    }
}
