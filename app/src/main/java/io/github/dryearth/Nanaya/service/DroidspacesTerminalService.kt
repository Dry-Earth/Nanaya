package io.github.dryearth.Nanaya.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.dryearth.Nanaya.AppActivity
import io.github.dryearth.Nanaya.R
import io.github.dryearth.Nanaya.core.DroidspacesTerminals

/** Keeps Droidspaces terminal shells alive while Nanaya is in the background. */
class DroidspacesTerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Droidspaces 终端",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        val intent = Intent(this, AppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status_terminal)
            .setContentTitle("Droidspaces")
            .setContentText("容器终端会话运行中")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        DroidspacesTerminals.closeAll()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "droidspaces_terminal"
        private const val ID = 0x4E42

        fun start(context: Context?) {
            val ctx = context ?: return
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DroidspacesTerminalService::class.java))
            }
        }

        fun stop(context: Context?) {
            val ctx = context ?: return
            runCatching { ctx.stopService(Intent(ctx, DroidspacesTerminalService::class.java)) }
        }
    }
}
