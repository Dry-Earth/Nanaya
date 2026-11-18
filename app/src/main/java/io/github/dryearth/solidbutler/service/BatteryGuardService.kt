package io.github.dryearth.solidbutler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import io.github.dryearth.solidbutler.core.LineageHealth
import io.github.dryearth.solidbutler.core.Prefs
import java.util.Locale

/**
 * Long-running, low-importance guard for the rules the user asked for:
 * - 25% low battery voice reminder (once per discharge cycle)
 * - 95% full battery voice reminder (once per charge cycle)
 * - optional "stop charging at N%" rule via the official lineagehealth service
 */
class BatteryGuardService : Service() {

    private lateinit var prefs: Prefs
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var limitApplied = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBattery(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("正在守护电量与充电"))
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale.SIMPLIFIED_CHINESE }
                ttsReady = true
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun handleBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        if (pct > 30) prefs.lowNotified = false
        if (pct < 90 || !charging) prefs.fullNotified = false

        if (pct <= 25 && !charging && !prefs.lowNotified && prefs.lowBatteryVoice) {
            prefs.lowNotified = true
            speak("电量只剩 $pct%，记得给手机充电。")
            updateNotification("电量偏低 · $pct%")
        }
        if (pct >= 95 && charging && !prefs.fullNotified && prefs.fullBatteryVoice) {
            prefs.fullNotified = true
            speak("电量已经充到 $pct%，可以拔掉充电器了。")
            updateNotification("电量充足 · $pct%")
        }

        // Charge-limit rule writes the same official lineagehealth setting as the UI.
        if (prefs.chargeLimitRuleEnabled && charging) {
            val limit = prefs.chargeLimitRule.coerceIn(70, 100)
            if (pct >= limit && !limitApplied) {
                val ok = runCatching {
                    LineageHealth.setLimit(limit)
                    LineageHealth.setMode(3)
                    LineageHealth.setEnabled(true)
                }.getOrDefault(false)
                if (ok) {
                    limitApplied = true
                    speak("电量已达到 $limit%，已开启限充。")
                    updateNotification("限充已开启 · $limit%")
                }
            }
        } else if (!charging) {
            limitApplied = false
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guard-${System.currentTimeMillis()}")
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "电池守护",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "低电量与充满提醒、限充规则"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("汐 · 电池守护")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(text))
        }
    }

    companion object {
        private const val CHANNEL_ID = "xi_battery_guard"
        private const val NOTIFICATION_ID = 12001

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, BatteryGuardService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BatteryGuardService::class.java)) }
        }
    }
}
