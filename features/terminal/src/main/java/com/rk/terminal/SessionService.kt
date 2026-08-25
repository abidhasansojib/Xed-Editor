package com.rk.terminal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.rk.activities.terminal.Terminal
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

/**
 * Foreground Service for Droidspaces Terminal sessions.
 * Keeps CPU, Wi-Fi, and network sockets active via PARTIAL_WAKE_LOCK when app is minimized,
 * and displays an ongoing notification.
 */
class SessionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout", "Wakelock")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Xed::TerminalSessionService",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                DroidspacesTerminalSessionManager.terminateAll()
                Terminal.instance?.let { act ->
                    act.runOnUiThread {
                        act.finishAndRemoveTask()
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps Droidspaces terminal active in the background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, Terminal::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP
        }
        val exitPendingIntent = PendingIntent.getService(
            this,
            1,
            exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val count = DroidspacesTerminalSessionManager.sessionList.value.size
        val countText = if (count <= 1) "1 session running" else "$count sessions running"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xed Terminal")
            .setContentText("Droidspaces Terminal active ($countText)")
            .setSmallIcon(drawables.terminal)
            .setContentIntent(openPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    strings.exit.getString(),
                    exitPendingIntent,
                ).build(),
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = buildNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    companion object {
        private const val CHANNEL_ID = "xed_terminal_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.rk.terminal.ACTION_STOP"
        const val ACTION_UPDATE = "com.rk.terminal.ACTION_UPDATE"

        fun start(context: Context) {
            try {
                val intent = Intent(context, SessionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun update(context: Context) {
            try {
                val intent = Intent(context, SessionService::class.java).apply {
                    action = ACTION_UPDATE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SessionService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
