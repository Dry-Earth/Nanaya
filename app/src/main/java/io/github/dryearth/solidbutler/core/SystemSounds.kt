package io.github.dryearth.solidbutler.core

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** Plays a system notification/ringtone URI chosen by the user. */
object SystemSounds {
    private val handler = Handler(Looper.getMainLooper())
    private var current: Ringtone? = null

    fun play(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        runCatching {
            current?.let { runCatching { it.stop() } }
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, Uri.parse(uriString))
                ?: return
            current = ringtone
            ringtone.isLooping = false
            ringtone.play()
            // Do not let a long ringtone cover the wake greeting.
            handler.postDelayed({
                if (current === ringtone) {
                    runCatching { ringtone.stop() }
                    current = null
                }
            }, 900)
        }
    }
}
