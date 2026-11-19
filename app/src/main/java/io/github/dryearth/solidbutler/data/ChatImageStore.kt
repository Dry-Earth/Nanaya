package io.github.dryearth.solidbutler.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File

/** Disk-backed chat images: original, model-sized send copy and a small thumbnail. */
object ChatImageStore {
    data class Saved(
        val originalPath: String,
        val sendPath: String,
        val thumbPath: String,
    )

    fun save(context: Context, uri: Uri): Saved? = runCatching {
        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val id = System.nanoTime()
        val original = File(dir, "original_$id")
        context.contentResolver.openInputStream(uri)?.use { input ->
            original.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, bounds)
        val maxSend = 1536
        var sample = 1
        while (bounds.outWidth / sample > maxSend || bounds.outHeight / sample > maxSend) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            original.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val scale = minOf(1f, maxSend.toFloat() / maxOf(decoded.width, decoded.height))
        val sendBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else decoded
        val send = File(dir, "send_$id.jpg")
        send.outputStream().use { sendBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }

        val thumbScale = minOf(1f, 320f / maxOf(sendBitmap.width, sendBitmap.height))
        val thumbBitmap = Bitmap.createScaledBitmap(
            sendBitmap,
            (sendBitmap.width * thumbScale).toInt().coerceAtLeast(1),
            (sendBitmap.height * thumbScale).toInt().coerceAtLeast(1),
            true,
        )
        val thumb = File(dir, "thumb_$id.jpg")
        thumb.outputStream().use { thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }

        if (sendBitmap !== decoded) decoded.recycle()
        if (thumbBitmap !== sendBitmap) sendBitmap.recycle()
        thumbBitmap.recycle()
        Saved(original.absolutePath, send.absolutePath, thumb.absolutePath)
    }.getOrNull()

    fun base64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun delete(paths: Collection<String?>) {
        paths.filterNotNull().forEach { runCatching { File(it).delete() } }
    }

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, "chat_images").deleteRecursively()
        }
    }
}
