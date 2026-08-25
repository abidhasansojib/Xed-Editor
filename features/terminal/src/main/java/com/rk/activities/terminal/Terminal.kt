package com.rk.activities.terminal

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rk.activities.settings.SettingsRoutes
import com.rk.settings.Settings
import com.rk.terminal.SSHTerminalSessionManager
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.TerminalScreen
import com.rk.terminal.ssh.SSHConfig
import com.rk.terminal.ssh.SSHTerminalBridgeRegistry
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.XedTheme
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

class Terminal : ComponentActivity() {
    var terminalView = WeakReference<TerminalView>(null)
    var virtualKeysView = WeakReference<VirtualKeysView>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()

        val initialCommand = intent.getStringExtra("initial_command")

        setContent {
            XedTheme {
                TerminalScreen(terminalActivity = this@Terminal, initialCommand = initialCommand)
            }
        }
    }

    fun changeSession(sessionId: String) {
        val view = terminalView.get() ?: return
        val client = TerminalBackEnd()
        val session = SSHTerminalSessionManager.getOrCreateSession(this, client, sessionId)

        session.updateTerminalSessionClient(client)
        view.attachSession(session)
        view.setTerminalViewClient(client)

        val bridge = SSHTerminalBridgeRegistry.getBridge(sessionId)
        if (bridge != null && !bridge.isConnected) {
            bridge.start(view.mEmulator?.mColumns ?: 80, view.mEmulator?.mRows ?: 24)
        }

        view.post {
            view.keepScreenOn = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }

        virtualKeysView.get()?.apply {
            virtualKeysViewClient = VirtualKeysListener(view.mTermSession)
        }

        SSHTerminalSessionManager.switchSession(sessionId)
    }

    override fun onResume() {
        super.onResume()
        terminalView.get()?.apply {
            val session = SSHTerminalSessionManager.getCurrentSession()
            if (session != null && mTermSession != session) {
                attachSession(session)
            }
            onScreenUpdated()
            requestFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Settings.terminate_sessions_on_exit) {
            SSHTerminalSessionManager.terminateAllSessions()
        }
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        var instance: Terminal? = null
    }
}
