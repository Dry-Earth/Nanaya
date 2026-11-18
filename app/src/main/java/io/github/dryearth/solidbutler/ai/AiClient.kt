package io.github.dryearth.solidbutler.ai

import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.data.ChatMessage
import io.github.dryearth.solidbutler.data.ChatRole
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
            history.filter { it.role != ChatRole.SYSTEM }.takeLast(14).forEach { m ->
                val role = if (m.role == ChatRole.USER) "user" else "assistant"
                messages.put(JSONObject().put("role", role).put("content", m.text))
            }

            val body = JSONObject()
                .put("model", prefs.aiModel.trim().ifBlank { "gpt-4o-mini" })
                .put("temperature", 0.2)
                .put("messages", messages)

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

    fun configured(): Boolean = prefs.aiApiKey.isNotBlank()

    fun describe(): String =
        if (configured()) "${prefs.aiModel} @ ${prefs.aiBaseUrl}" else "未配置"
}
