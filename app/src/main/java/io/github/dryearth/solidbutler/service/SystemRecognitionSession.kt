package io.github.dryearth.solidbutler.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Binds the configured system RecognitionService directly (for example the
 * offline Marmalade engine on LineageOS). Unlike ACTION_RECOGNIZE_SPEECH this
 * does not open a second system speech window, so it works on devices whose
 * engine only exposes a RecognitionService.
 */
class SystemRecognitionSession(
    context: Context,
    private val onReady: () -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onPreview: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var stopped = false
    private var delivered = false

    fun start(language: String = "zh-CN") {
        val created = runCatching { SystemSpeechRecognizer.create(appContext) }.getOrNull()
        if (created == null) {
            onFailure("没有可用的系统语音识别服务，先在设置里配置识别引擎")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            if (language.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        }
        val started = runCatching { created.startListening(intent) }.isSuccess
        if (!started) {
            onFailure("无法启动系统语音识别")
            release()
        }
    }

    /** User tapped finish: stop capturing and wait for the final result. */
    fun finish() {
        if (delivered) return
        stopped = true
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        delivered = true
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
        onLevel(0f)
    }

    private fun release() {
        runCatching {
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
        }
        recognizer = null
        onLevel(0f)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onReady()

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (delivered) return
            first(partialResults)?.takeIf { it.isNotBlank() }?.let(onPreview)
        }

        override fun onResults(results: Bundle?) {
            if (delivered) return
            val text = first(results).orEmpty().trim()
            delivered = true
            release()
            if (text.isEmpty()) onFailure("没有听清，再试一次") else onResult(text)
        }

        override fun onError(error: Int) {
            if (delivered) return
            delivered = true
            release()
            onFailure(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，再试一次"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务网络错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "麦克风权限不足，请到 设置 → 权限与授权 开启"
                    SpeechRecognizer.ERROR_AUDIO -> "录音失败，请重试"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
                    SpeechRecognizer.ERROR_CLIENT ->
                        if (stopped) "没有听清，再试一次" else "识别客户端异常，请重试"
                    else -> "识别失败（$error）"
                },
            )
        }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
