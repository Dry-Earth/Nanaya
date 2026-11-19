package io.github.dryearth.solidbutler.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Bridge between the speech recognizer and the overlay window. */
object OverlayVoiceBus {
    val result = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val listening = MutableStateFlow(false)
    val status = MutableStateFlow<String?>(null)
    /** Normalized 0..1 microphone level, used by the wake glow. */
    val level = MutableStateFlow(0f)

    fun reset() {
        result.value = null
        error.value = null
        status.value = null
        level.value = 0f
    }
}

/**
 * Binds the configured external RecognitionService (for example Marmalade)
 * directly from the overlay service. No Activity is involved, so there is no
 * secondary window/transition and no system speech sheet.
 */
object OverlaySpeech {
    private var recognizer: SpeechRecognizer? = null
    private var delivered = false

    fun start(context: Context) {
        stop()
        OverlayVoiceBus.reset()
        OverlayVoiceBus.listening.value = true
        OverlayVoiceBus.status.value = "正在聆听…"

        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail("麦克风未授权")
            return
        }

        val created = runCatching { SystemSpeechRecognizer.create(app) }.getOrNull()
        if (created == null) {
            fail("没有可用的系统语音识别服务")
            return
        }
        recognizer = created
        delivered = false
        created.setRecognitionListener(listener)
        val started = runCatching {
            created.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
                },
            )
        }.isSuccess
        if (!started) fail("无法启动语音识别")
    }

    fun stop() {
        delivered = true
        release()
        OverlayVoiceBus.listening.value = false
        OverlayVoiceBus.level.value = 0f
        OverlayVoiceBus.status.value = null
    }

    private fun fail(message: String) {
        OverlayVoiceBus.error.value = message
        OverlayVoiceBus.listening.value = false
        OverlayVoiceBus.status.value = null
        release()
    }

    private fun release() {
        val current = recognizer ?: return
        recognizer = null
        current.setRecognitionListener(null)
        runCatching { current.cancel() }
        runCatching { current.destroy() }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            OverlayVoiceBus.status.value = "正在聆听…"
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            OverlayVoiceBus.level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            OverlayVoiceBus.listening.value = false
            OverlayVoiceBus.level.value = 0f
            OverlayVoiceBus.status.value = "正在识别…"
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?.let { OverlayVoiceBus.status.value = it }
        }

        override fun onResults(results: Bundle?) {
            if (delivered) return
            delivered = true
            OverlayVoiceBus.listening.value = false
            OverlayVoiceBus.level.value = 0f
            OverlayVoiceBus.status.value = null
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()
            if (text.isNullOrBlank()) {
                OverlayVoiceBus.error.value = "没有识别到文字"
            } else {
                OverlayVoiceBus.result.value = text
            }
            release()
        }

        override fun onError(error: Int) {
            if (delivered) return
            delivered = true
            OverlayVoiceBus.listening.value = false
            OverlayVoiceBus.level.value = 0f
            OverlayVoiceBus.status.value = null
            OverlayVoiceBus.error.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，再试一次"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务网络错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
                SpeechRecognizer.ERROR_CLIENT -> "识别客户端异常，请重试"
                else -> "识别失败：$error"
            }
            release()
        }
    }
}
