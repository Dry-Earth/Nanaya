package io.github.dryearth.solidbutler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import io.github.dryearth.solidbutler.AppActivity
import io.github.dryearth.solidbutler.R
import io.github.dryearth.solidbutler.core.AdbDiscovery
import io.github.dryearth.solidbutler.core.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground pairing helper: discovers the on-device ADB pairing port and
 * presents an inline "enter pairing code" notification, so the user does not
 * have to switch back to the app after opening Wireless debugging.
 */
class AdbPairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var host: String? = null
    private var port: Int? = null
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        when (command.action) {
            ACTION_SUBMIT -> {
                val code = RemoteInput.getResultsFromIntent(command)
                    ?.getCharSequence(KEY_CODE)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                submitCode(code)
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!running) {
            running = true
            startScan()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scope.coroutineContext.cancel()
        super.onDestroy()
    }

    private fun startScan() {
        val scanning = buildNotification(
            title = "正在扫描无线调试",
            text = "请保持系统「无线调试」配对页面打开",
            ongoing = true,
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, scanning, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, scanning)
        }
        AdbDiscovery.discoverPairing(this, 30_000) { foundHost, foundPort ->
            if (!running) return@discoverPairing
            host = foundHost
            port = foundPort
            if (foundHost == null || foundPort == null) {
                showNotification(
                    buildNotification(
                        title = "没有找到配对端口",
                        text = "请打开系统无线调试的配对弹窗，或回到应用手动配对",
                        ongoing = false,
                        contentIntent = appIntent(),
                    ),
                )
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return@discoverPairing
            }
            showNotification(
                buildNotification(
                    title = "已找到配对端口",
                    text = "$foundHost:$foundPort · 在通知里输入 6 位配对码",
                    ongoing = false,
                    contentIntent = appIntent(),
                    withCodeInput = true,
                ),
            )
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun submitCode(code: String) {
        val foundHost = host
        val foundPort = port
        if (code.length != 6 || code.any { !it.isDigit() }) {
            showNotification(
                buildNotification(
                    title = "配对码格式不对",
                    text = "请输入 6 位数字配对码",
                    ongoing = false,
                    contentIntent = appIntent(),
                    withCodeInput = true,
                ),
            )
            return
        }
        if (foundHost == null || foundPort == null) {
            showNotification(
                buildNotification(
                    title = "缺少配对端口",
                    text = "请回到应用重新扫描",
                    ongoing = false,
                    contentIntent = appIntent(),
                ),
            )
            return
        }
        showNotification(
            buildNotification(
                title = "正在配对无线 ADB",
                text = "$foundHost:$foundPort",
                ongoing = true,
            ),
        )
        scope.launch {
            val pairError = AdbManager.pairDevice(this@AdbPairingService, foundHost, foundPort, code)
            if (pairError != null) {
                finish(
                    title = "配对失败",
                    text = pairError,
                )
                return@launch
            }
            val connectError = AdbManager.connectDevice(this@AdbPairingService, 8_000)
            finish(
                title = if (connectError == null) "无线 ADB 已连接" else "配对成功，连接失败",
                text = connectError ?: "可以回到 Nanaya 继续使用",
            )
        }
    }

    private fun finish(title: String, text: String) {
        showNotification(
            buildNotification(
                title = title,
                text = text,
                ongoing = false,
                contentIntent = appIntent(),
            ),
        )
        stopForeground(STOP_FOREGROUND_DETACH)
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 8_000)
    }

    private fun showNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        contentIntent: PendingIntent? = null,
        withCodeInput: Boolean = false,
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (withCodeInput) {
            val remoteInput = RemoteInput.Builder(KEY_CODE)
                .setLabel("6 位配对码")
                .build()
            val submit = PendingIntent.getService(
                this,
                10,
                Intent(this, AdbPairingService::class.java).setAction(ACTION_SUBMIT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "输入配对码",
                    submit,
                ).addRemoteInput(remoteInput).build(),
            )
        }
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                PendingIntent.getService(
                    this,
                    11,
                    Intent(this, AdbPairingService::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        return builder.build()
    }

    private fun appIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        12,
        Intent(this, AppActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "无线 ADB 配对", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 12020
        private const val KEY_CODE = "adb_pairing_code"
        private const val ACTION_START = "io.github.dryearth.solidbutler.ADB_PAIR_START"
        private const val ACTION_SUBMIT = "io.github.dryearth.solidbutler.ADB_PAIR_SUBMIT"
        private const val ACTION_CANCEL = "io.github.dryearth.solidbutler.ADB_PAIR_CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
