package com.rk.activities.terminal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rk.settings.Settings
import com.rk.terminal.DroidspacesTerminalSessionManager
import com.rk.terminal.SessionService
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.TerminalScreen
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.XedTheme
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

class Terminal : ComponentActivity() {
    var terminalView = WeakReference<TerminalView>(null)
    var virtualKeysView = WeakReference<VirtualKeysView>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                SessionService.update(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
        val session = DroidspacesTerminalSessionManager.getOrCreateSession(this, client, sessionId)

        session.updateTerminalSessionClient(client)
        view.attachSession(session)
        view.setTerminalViewClient(client)

        view.post {
            view.keepScreenOn = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }

        virtualKeysView.get()?.apply {
            virtualKeysViewClient = VirtualKeysListener(view.mTermSession)
        }

        DroidspacesTerminalSessionManager.switchSession(sessionId)
        SessionService.update(this)
    }

    override fun onResume() {
        super.onResume()
        terminalView.get()?.apply {
            val session = DroidspacesTerminalSessionManager.getCurrentSession()
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
            DroidspacesTerminalSessionManager.terminateAll()
        }
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        var instance: Terminal? = null
    }
}
