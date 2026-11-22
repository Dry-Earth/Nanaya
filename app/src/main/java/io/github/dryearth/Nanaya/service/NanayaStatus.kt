package io.github.dryearth.Nanaya.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.dryearth.Nanaya.AppActivity
import io.github.dryearth.Nanaya.R

/**
 * One ongoing, silent status notification that changes its icon with what Nanaya
 * is doing: foreground, assistant, music, scanning, security or performance.
 */
object NanayaStatus {
    enum class State { APP, AI, MUSIC, SCAN, SECURITY, PERFORMANCE }

    private const val CHANNEL_ID = "nanaya_status"
    private const val NOTIFICATION_ID = 0x4E41

    private var appContext: Context? = null
    private var foreground = false
    private var screenState = State.APP
    private var assistantActive = false
    private var scanning = false
    private var musicPlaying = false
    private var musicTitle: String? = null
    private var channelReady = false

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        ensureChannel()
    }

    fun start(context: Context) {
        attach(context)
        foreground = true
        render()
    }

    fun stop() {
        foreground = false
        render()
    }

    fun setScreen(state: State) {
        screenState = state
        render()
    }

    fun setAssistant(active: Boolean) {
        assistantActive = active
        render()
    }

    fun setScanning(active: Boolean) {
        scanning = active
        render()
    }

    fun setMusic(playing: Boolean, title: String?) {
        musicPlaying = playing
        musicTitle = title
        render()
    }

    private fun effectiveState(): State = when {
        assistantActive -> State.AI
        scanning -> State.SCAN
        musicPlaying -> State.MUSIC
        else -> screenState
    }

    private fun render() {
        val context = appContext ?: return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        val effective = effectiveState()
        val keep = foreground || assistantActive || scanning || musicPlaying
        if (!keep) {
            runCatching { manager.cancel(NOTIFICATION_ID) }
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconFor(effective))
            .setContentTitle("Nanaya")
            .setContentText(textFor(effective))
            .setContentIntent(launchIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (channelReady) return
        val context = appContext ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nanaya 实时状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在状态栏显示 Nanaya 当前状态"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        channelReady = true
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, AppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun iconFor(state: State): Int = when (state) {
        State.APP -> R.drawable.ic_status_app
        State.AI -> R.drawable.ic_status_ai
        State.MUSIC -> R.drawable.ic_status_music
        State.SCAN -> R.drawable.ic_status_scan
        State.SECURITY -> R.drawable.ic_status_security
        State.PERFORMANCE -> R.drawable.ic_status_performance
    }

    private fun textFor(state: State): String = when (state) {
        State.APP -> "正在运行"
        State.AI -> "数字助理已唤醒"
        State.MUSIC -> musicTitle?.takeIf { it.isNotBlank() }?.let { "正在播放：$it" } ?: "正在播放音乐"
        State.SCAN -> "正在扫描…"
        State.SECURITY -> "安全中心"
        State.PERFORMANCE -> "性能监控"
    }
}
