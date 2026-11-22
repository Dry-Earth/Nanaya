package io.github.dryearth.Nanaya.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val META_FILE = "plugins.json"
private const val MAX_PLUGIN_BYTES = 4 * 1024 * 1024

/**
 * Installed MusicFree-style plugins live in filesDir/musicfree/plugins.
 * Metadata is kept in plugins.json next to them.
 */
object MusicPluginStore {
    private val lock = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun pluginsDir(context: Context): File =
        File(context.filesDir, "musicfree/plugins").apply { mkdirs() }

    fun fileFor(context: Context, plugin: InstalledPlugin): File =
        File(pluginsDir(context), plugin.fileName)

    fun list(context: Context): List<InstalledPlugin> = synchronized(this) {
        val array = readMeta(context)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val fileName = obj.optString("file")
                if (fileName.isBlank()) continue
                if (!File(pluginsDir(context), fileName).exists()) continue
                add(
                    InstalledPlugin(
                        id = obj.optString("id"),
                        platform = obj.optString("platform"),
                        version = obj.optString("version"),
                        enabled = obj.optBoolean("enabled", true),
                        fileName = fileName,
                        sourceUrl = obj.optString("url").ifBlank { null },
                    ),
                )
            }
        }
    }

    suspend fun installFromUrl(context: Context, url: String): InstalledPlugin {
        val source = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "Nanaya/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载失败：HTTP ${response.code}")
                val body = response.body ?: error("下载失败：响应为空")
                if (body.contentLength() > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
                body.string()
            }
        }
        return installSource(context, source, url.trim())
    }

    suspend fun installFromUri(context: Context, uri: Uri): InstalledPlugin {
        val source = withContext(Dispatchers.IO) {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
                String(bytes, Charsets.UTF_8)
            } ?: error("无法读取所选文件")
        }
        return installSource(context, source, null)
    }

    suspend fun installSource(context: Context, rawSource: String, sourceUrl: String?): InstalledPlugin {
        val source = rawSource.trim()
        require(source.isNotEmpty()) { "插件内容为空" }
        require(!source.startsWith("{")) { "暂不支持订阅 JSON，请安装单个 .js 插件文件" }
        val meta = MusicFreeEngine.inspectPlugin(context, source)
        return lock.withLock {
            val dir = pluginsDir(context)
            val id = UUID.randomUUID().toString().replace("-", "").take(12)
            val safePlatform = meta.platform.replace(Regex("[^A-Za-z0-9._-]"), "_").take(32)
                .ifBlank { "plugin" }
            val fileName = "$safePlatform-$id.js"
            File(dir, fileName).writeText(source, Charsets.UTF_8)

            val array = readMeta(context)
            val next = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("platform") == meta.platform) {
                    val old = obj.optString("file")
                    if (old.isNotBlank()) runCatching { File(dir, old).delete() }
                    continue
                }
                next.put(obj)
            }
            next.put(
                JSONObject()
                    .put("id", id)
                    .put("platform", meta.platform)
                    .put("version", meta.version)
                    .put("enabled", true)
                    .put("file", fileName)
                    .apply { if (!sourceUrl.isNullOrBlank()) put("url", sourceUrl) },
            )
            writeMeta(context, next)
            MusicFreeEngine.reload(context)
            InstalledPlugin(
                id = id,
                platform = meta.platform,
                version = meta.version,
                enabled = true,
                fileName = fileName,
                sourceUrl = sourceUrl,
            )
        }
    }

    suspend fun setEnabled(context: Context, id: String, enabled: Boolean) {
        lock.withLock {
            val array = readMeta(context)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("id") == id) obj.put("enabled", enabled)
            }
            writeMeta(context, array)
        }
        MusicFreeEngine.reload(context)
    }

    suspend fun remove(context: Context, id: String) {
        lock.withLock {
            val dir = pluginsDir(context)
            val array = readMeta(context)
            val next = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("id") == id) {
                    val fileName = obj.optString("file")
                    if (fileName.isNotBlank()) runCatching { File(dir, fileName).delete() }
                    continue
                }
                next.put(obj)
            }
            writeMeta(context, next)
        }
        MusicFreeEngine.reload(context)
    }

    fun enabledSources(context: Context): List<Pair<InstalledPlugin, String>> = buildList {
        for (plugin in list(context)) {
            if (!plugin.enabled) continue
            runCatching {
                val text = File(pluginsDir(context), plugin.fileName).readText(Charsets.UTF_8)
                add(plugin to text)
            }
        }
    }

    private fun readMeta(context: Context): JSONArray {
        val file = File(pluginsDir(context), META_FILE)
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONArray())
    }

    private fun writeMeta(context: Context, array: JSONArray) {
        val file = File(pluginsDir(context), META_FILE)
        val tmp = File(file.parentFile, "$META_FILE.tmp")
        tmp.writeText(array.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
