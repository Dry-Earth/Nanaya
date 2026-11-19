package io.github.dryearth.solidbutler.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow

/** Bridge between the system speech UI and the overlay window. */
object OverlayVoiceBus {
    val result = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val listening = MutableStateFlow(false)
    val status = MutableStateFlow<String?>(null)

    fun reset() {
        result.value = null
        error.value = null
        status.value = null
    }
}

/**
 * Binds the external RecognitionService configured in Android settings
 * (for example Marmalade STT) directly. No speech Activity or system popup
 * is launched, so the overlay stays visible.
 */
class OverlayVoiceActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        OverlayVoiceBus.reset()
        OverlayVoiceBus.listening.value = true
        OverlayVoiceBus.status.value = "正在聆听…"
        sendBroadcast(Intent(AssistOverlayService.ACTION_VOICE_START).setPackage(packageName))

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            OverlayVoiceBus.error.value = "麦克风未授权"
            finish()
            return
        }

        val created = runCatching { SystemSpeechRecognizer.create(this) }.getOrNull()
        if (created == null) {
            OverlayVoiceBus.error.value = "没有可用的系统语音识别服务"
            finish()
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                OverlayVoiceBus.status.value = "正在聆听…"
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                OverlayVoiceBus.listening.value = false
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
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (text.isNullOrBlank()) {
                    OverlayVoiceBus.error.value = "没有识别到文字"
                } else {
                    OverlayVoiceBus.result.value = text
                }
                finish()
            }

            override fun onError(error: Int) {
                if (delivered) return
                delivered = true
                OverlayVoiceBus.listening.value = false
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
                finish()
            }
        })
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
        )
    }

    override fun onDestroy() {
        delivered = true
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
        OverlayVoiceBus.listening.value = false
        sendBroadcast(Intent(AssistOverlayService.ACTION_VOICE_END).setPackage(packageName))
        super.onDestroy()
    }
}
