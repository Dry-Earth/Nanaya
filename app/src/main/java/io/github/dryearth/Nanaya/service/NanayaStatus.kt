package io.github.dryearth.Nanaya.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.dryearth.Nanaya.AppActivity
import io.github.dryearth.Nanaya.R

/**
 * One ongoing, silent status notification that changes its icon with what Nanaya
 * is doing: foreground, background, assistant, music, scanning, cleaning,
 * security, performance, or executing a root/ADB command.
 */
object NanayaStatus {
    enum class State { APP, BACKGROUND, AI, MUSIC, SCAN, CLEANING, COMMAND, SECURITY, PERFORMANCE }

    private const val CHANNEL_ID = "nanaya_status"
    private const val NOTIFICATION_ID = 0x4E41

    private var appContext: Context? = null
    private var started = false
    private var foreground = false
    private var screenState = State.APP
    private var assistantActive = false
    private var scanning = false
    private var cleaning = false
    private var musicPlaying = false
    private var musicTitle: String? = null
    private var commandDepth = 0
    private var channelReady = false
    private var perfSummary: String? = null
    private var lastRenderedState: State? = null

    private val glyphFallbacks = mapOf(
        "𓆩♡𓆪" to "♡",
        "☭" to "✦",
        "♻" to "↻",
        "☯" to "◉",
    )

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        ensureChannel()
        if (!started) {
            started = true
            render()
        }
    }

    fun start(context: Context) {
        attach(context)
        started = true
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

    fun setCleaning(active: Boolean) {
        cleaning = active
        render()
    }

    fun beginCommand() {
        commandDepth++
        render()
    }

    fun endCommand() {
        commandDepth = (commandDepth - 1).coerceAtLeast(0)
        render()
    }

    fun setMusic(playing: Boolean, title: String?) {
        musicPlaying = playing
        musicTitle = title
        render()
    }

    /** Compact CPU / GPU / RAM / temperature line for the performance screen. */
    fun setPerfSummary(summary: String?) {
        if (perfSummary == summary) return
        perfSummary = summary
        render()
    }

    private fun effectiveState(): State = when {
        assistantActive -> State.AI
        commandDepth > 0 -> State.COMMAND
        cleaning -> State.CLEANING
        scanning -> State.SCAN
        musicPlaying -> State.MUSIC
        !foreground -> State.BACKGROUND
        else -> screenState
    }

    private fun render() {
        val context = appContext ?: return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        if (!started) {
            runCatching { manager.cancel(NOTIFICATION_ID) }
            lastRenderedState = null
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val effective = effectiveState()
        if (lastRenderedState != effective) {
            // Some OEMs cache the small icon of a notification id; cancel first so
            // the status bar icon actually changes between states.
            runCatching { manager.cancel(NOTIFICATION_ID) }
            lastRenderedState = effective
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconFor(context, effective))
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

    private fun iconFor(context: Context, state: State): IconCompat = when (state) {
        State.APP -> IconCompat.createWithResource(context, R.drawable.ic_status_app)
        State.BACKGROUND -> glyphIcon(context, "☭")
        State.AI -> birdHeartIcon(context)
        State.MUSIC -> IconCompat.createWithResource(context, R.drawable.ic_status_music)
        State.SCAN -> IconCompat.createWithResource(context, R.drawable.ic_status_scan)
        State.CLEANING -> glyphIcon(context, "♻")
        State.COMMAND -> glyphIcon(context, "☯")
        State.SECURITY -> IconCompat.createWithResource(context, R.drawable.ic_status_security)
        State.PERFORMANCE -> IconCompat.createWithResource(context, R.drawable.ic_status_performance)
    }

    /**
     * 𓆩♡𓆪 needs two fonts: the Egyptian hieroglyphs come from a bundled Noto
     * font, while the white heart uses the system sans-serif fallback.
     */
    private fun birdHeartIcon(context: Context): IconCompat {
        val density = context.resources.displayMetrics.density
        val size = (48f * density).toInt().coerceIn(48, 192)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val latin = Typeface.create("sans-serif", Typeface.NORMAL)
        val egypt = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/NotoSansEgyptianHieroglyphs-Regular.ttf")
        }.getOrNull() ?: latin
        val parts = listOf(
            "𓆩" to egypt,
            "♡" to latin,
            "𓆪" to egypt,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            textSize = size * 0.58f
        }
        val widths = parts.map { (text, typeface) ->
            paint.typeface = typeface
            paint.measureText(text)
        }
        val total = widths.sum()
        var x = (size - total) / 2f
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        parts.forEachIndexed { index, (text, typeface) ->
            paint.typeface = typeface
            canvas.drawText(text, x, baseline, paint)
            x += widths[index]
        }
        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * Android notification small icons must be drawables, so render the requested
     * Unicode glyph into a transparent bitmap. Falls back to a simpler glyph when
     * the system font does not contain it.
     */
    private fun glyphIcon(context: Context, preferred: String): IconCompat {
        val glyph = supportedGlyph(preferred)
        val density = context.resources.displayMetrics.density
        val size = (48f * density).toInt().coerceIn(48, 192)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.82f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph, size / 2f, baseline, paint)
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun supportedGlyph(preferred: String): String {
        val fallback = glyphFallbacks[preferred] ?: "✦"
        if (Build.VERSION.SDK_INT < 23) return fallback
        val paint = Paint()
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val first = preferred.substring(0, preferred.offsetByCodePoints(0, 1))
        return if (runCatching { paint.hasGlyph(first) }.getOrDefault(false)) preferred else fallback
    }

    private fun textFor(state: State): String = when (state) {
        State.APP -> "正在运行"
        State.BACKGROUND -> "在后台运行"
        State.AI -> "数字助理已唤醒"
        State.MUSIC -> musicTitle?.takeIf { it.isNotBlank() }?.let { "正在播放：$it" } ?: "正在播放音乐"
        State.SCAN -> "正在扫描…"
        State.CLEANING -> "正在清理文件…"
        State.COMMAND -> "正在执行 root / ADB 命令…"
        State.SECURITY -> "安全中心"
        State.PERFORMANCE -> perfSummary?.takeIf { it.isNotBlank() } ?: "性能监控"
    }
}
