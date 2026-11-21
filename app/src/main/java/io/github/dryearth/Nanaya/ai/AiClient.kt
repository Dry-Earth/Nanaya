package io.github.dryearth.Nanaya.ai

import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.data.ChatMessage
import io.github.dryearth.Nanaya.data.ChatImageStore
import io.github.dryearth.Nanaya.data.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

/**
 * OpenAI-compatible chat client. Only the endpoint the user enters is contacted;
 * the API key stays in app-private storage.
 */
class AiClient(private val prefs: Prefs) {

    suspend fun complete(system: String, history: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val key = prefs.aiApiKey.trim()
            if (key.isBlank()) throw AiException("还没有配置云端模型：设置 → AI 管家，填入接口地址、模型名和 API Key。")
            val base = prefs.aiBaseUrl.trim().trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", system))
            val recent = history.filter { it.role != ChatRole.SYSTEM }.takeLast(14)
            val imageIds = recent.filter { it.imageSendPath != null }.takeLast(2).map { it.id }.toSet()
            recent.forEach { m ->
                val role = if (m.role == ChatRole.USER) "user" else "assistant"
                val image = if (m.id in imageIds) ChatImageStore.base64(m.imageSendPath) else null
                val content: Any = if (image != null) {
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", m.text))
                        .put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image"))
                        )
                } else {
                    m.text
                }
                messages.put(JSONObject().put("role", role).put("content", content))
            }

            val model = prefs.aiModel.trim().ifBlank { "gpt-4o-mini" }
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put("messages", messages)
            if (prefs.aiThinking != "auto" && (model.contains("qwen", true) || model.contains("deepseek", true))) {
                body.put("enable_thinking", prefs.aiThinking == "on")
            }
            if (prefs.aiEffort != "auto" && (
                    model.contains("o1", true) || model.contains("o3", true) ||
                        model.contains("o4", true) || model.contains("gpt-5", true)
                    )
            ) {
                body.put("reasoning_effort", prefs.aiEffort)
            }

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream ?: return@withContext "", Charsets.UTF_8))
                    .use { it.readText() }
                if (code !in 200..299) {
                    throw AiException("云端返回 $code：${text.take(400)}")
                }
                val json = JSONObject(text)
                json.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw AiException("云端返回了空内容")
            } finally {
                conn.disconnect()
            }
        }

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException("模型列表返回 $code：${text.take(200)}")
            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            }.sorted()
        } finally {
            conn.disconnect()
        }
    }

    fun configured(): Boolean = prefs.aiApiKey.isNotBlank()

    fun describe(): String =
        if (configured()) "${prefs.aiModel} @ ${prefs.aiBaseUrl}" else "未配置"
}
