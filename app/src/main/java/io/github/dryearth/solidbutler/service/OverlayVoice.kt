package io.github.dryearth.solidbutler.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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
 * Uses the platform RecognizerIntent. This is also how Marmalade STT is
 * reached: the user selects it as Android's Voice input service.
 * The Activity is transparent; the real UI is the system/Marmalade sheet.
 */
class OverlayVoiceActivity : Activity() {

    private val requestCode = 71

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayVoiceBus.reset()
        OverlayVoiceBus.listening.value = true
        OverlayVoiceBus.status.value = "正在启动系统语音输入…"
        sendBroadcast(Intent(AssistOverlayService.ACTION_VOICE_START).setPackage(packageName))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说吧，汐在听")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(packageManager) == null) {
            OverlayVoiceBus.error.value = "系统没有可用的语音输入服务"
            finish()
            return
        }
        runCatching { startActivityForResult(intent, requestCode) }
            .onFailure {
                OverlayVoiceBus.error.value = "无法打开系统语音输入"
                finish()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return
        if (resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text.isNullOrBlank()) {
                OverlayVoiceBus.error.value = "没有识别到文字"
            } else {
                OverlayVoiceBus.result.value = text
            }
        } else {
            OverlayVoiceBus.error.value = "语音输入已取消"
        }
        OverlayVoiceBus.listening.value = false
        finish()
    }

    override fun onDestroy() {
        OverlayVoiceBus.listening.value = false
        sendBroadcast(Intent(AssistOverlayService.ACTION_VOICE_END).setPackage(packageName))
        super.onDestroy()
    }
}
