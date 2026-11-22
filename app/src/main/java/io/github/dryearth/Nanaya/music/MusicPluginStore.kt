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
 * Installed plugin files live in filesDir/musicplugins/plugins.
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

    fun pluginsDir(context: Context): File {
        val target = File(context.filesDir, "musicplugins/plugins")
        if (!target.exists()) {
            // Copy plugins from any older single "plugins" directory without
            // hard-coding the previous folder name.
            val legacy = context.filesDir.listFiles()
                ?.firstOrNull { dir ->
                    dir.isDirectory &&
                        dir.name != "musicplugins" &&
                        File(dir, "plugins").isDirectory &&
                        File(dir, "plugins").listFiles()?.any { it.extension == "js" } == true
                }
            if (legacy != null) {
                runCatching {
                    File(legacy, "plugins").copyRecursively(target, overwrite = false)
                }
            }
        }
        target.mkdirs()
        return target
    }

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

    suspend fun installFromUrl(context: Context, url: String): InstallSummary {
        val text = downloadText(url)
        return installContent(context, text, url.trim())
    }

    suspend fun installFromUri(context: Context, uri: Uri): InstallSummary {
        val source = withContext(Dispatchers.IO) {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
                String(bytes, Charsets.UTF_8)
            } ?: error("无法读取所选文件")
        }
        return installContent(context, source, null)
    }

    private suspend fun installContent(context: Context, rawText: String, sourceUrl: String?): InstallSummary {
        val text = rawText.trim().removePrefix("\uFEFF")
        if (text.startsWith("{")) {
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: error("订阅 JSON 无法解析")
            val plugins = json.optJSONArray("plugins")
                ?: error("这个 JSON 里没有 plugins 列表")
            val installed = mutableListOf<InstalledPlugin>()
            var failed = 0
            for (i in 0 until minOf(plugins.length(), 12)) {
                val item = plugins.optJSONObject(i)
                val url = item?.optString("url")?.trim().orEmpty()
                if (url.isBlank()) {
                    failed++
                    continue
                }
                runCatching {
                    installSource(context, downloadText(url), url)
                }.onSuccess { installed += it }
                    .onFailure { failed++ }
            }
            if (installed.isEmpty()) {
                error("订阅里的插件都安装失败了（$failed 个）")
            }
            return InstallSummary(installed, failed)
        }
        return InstallSummary(listOf(installSource(context, text, sourceUrl)), 0)
    }

    private suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", "Nanaya/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败：HTTP ${response.code}")
            val body = response.body ?: error("下载失败：响应为空")
            if (body.contentLength() > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
            val bytes = body.bytes()
            if (bytes.size > MAX_PLUGIN_BYTES) error("插件文件过大（> 4 MB）")
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun installSource(context: Context, rawSource: String, sourceUrl: String?): InstalledPlugin {
        val source = rawSource.trim()
        require(source.isNotEmpty()) { "插件内容为空" }
        require(!source.startsWith("{")) { "暂不支持订阅 JSON，请安装单个 .js 插件文件" }
        val meta = MusicPluginEngine.inspectPlugin(context, source)
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
            MusicPluginEngine.reload(context)
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
        MusicPluginEngine.reload(context)
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
        MusicPluginEngine.reload(context)
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
