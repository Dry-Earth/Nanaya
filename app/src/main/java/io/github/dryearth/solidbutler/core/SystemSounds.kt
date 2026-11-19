package io.github.dryearth.solidbutler.core

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Plays a system notification/ringtone URI chosen by the user. */
object SystemSounds {
    fun play(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        runCatching {
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, Uri.parse(uriString))
                ?: return
            ringtone.isLooping = false
            ringtone.play()
        }
    }
}
