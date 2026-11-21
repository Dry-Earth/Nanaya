package io.github.dryearth.Nanaya.core

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech

/** Applies the system TTS rate and pitch. The engine keeps its default voice. */
object TtsDefaults {
    fun apply(context: Context, engine: TextToSpeech) {
        runCatching {
            val cr = context.contentResolver
            val rate = Settings.Secure.getInt(cr, "tts_default_rate", 100)
            val pitch = Settings.Secure.getInt(cr, "tts_default_pitch", 100)
            engine.setSpeechRate((rate / 100f).coerceIn(0.5f, 2f))
            engine.setPitch((pitch / 100f).coerceIn(0.5f, 2f))
        }
    }
}
