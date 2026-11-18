package io.github.dryearth.solidbutler.core

import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun Long.bytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = this.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (v >= 100 || i == 0) String.format(Locale.US, "%.0f %s", v, units[i])
    else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun Long.ago(): String {
    val diff = System.currentTimeMillis() - this
    if (diff < 60_000) return "刚刚"
    val m = diff / 60_000
    if (m < 60) return "${m} 分钟前"
    val h = m / 60
    if (h < 24) return "${h} 小时前"
    val d = h / 24
    if (d < 30) return "${d} 天前"
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(this))
}

fun Long.clock(): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(this))

fun Int.percentOf(total: Long): Float = if (total <= 0) 0f else (this.toFloat() / total.toFloat()).coerceIn(0f, 1f)

fun safePct(a: Long, b: Long): Float = if (b <= 0) 0f else (a.toDouble() / b.toDouble()).coerceIn(0.0, 1.0).toFloat()

fun StatFs.usedBytes(): Long = (blockCount * blockSize) - (availableBlocks * blockSize)

fun StatFs.totalBytes(): Long = blockCount * blockSize

fun StatFs.freeBytes(): Long = availableBlocks * blockSize

fun isExternalManager(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

fun Double.round1(): String = String.format(Locale.US, "%.1f", this)

fun Double.round0(): String = String.format(Locale.US, "%.0f", this)

fun String.shorten(max: Int = 42): String =
    if (length <= max) this else substring(0, max - 1) + "…"

fun nearlyEquals(a: Float, b: Float, tolerance: Float = 0.01f): Boolean = abs(a - b) < tolerance
