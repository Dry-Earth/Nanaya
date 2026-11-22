package io.github.dryearth.Nanaya.music

import android.content.Context
import android.util.Base64
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Runs MusicFree plugin files in an embedded QuickJS instance.
 *
 * libs.js (cheerio / crypto-js / dayjs / he / qs / big-integer) and runtime.js
 * are bundled in assets. Kotlin only provides the network bridge.
 */
object MusicFreeEngine {
    private const val TAG = "MusicFree"
    private const val PLUGIN_TIMEOUT_MS = 45_000L
    private const val MAX_MEMORY = 192L * 1024 * 1024

    private val mutex = Mutex()
    private var engine: QuickJs? = null
    private var loadedSignature: String? = null
    private val pluginErrors = ConcurrentHashMap<String, String>()
    private val loadedPlatforms = mutableSetOf<String>()
    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    fun log(line: String) {
        if (line.isBlank()) return
        _debugLog.update { (it + line.replace('\n', ' ').take(260)).takeLast(80) }
    }

    @Volatile
    private var libsSource: String? = null

    @Volatile
    private var runtimeSource: String? = null

    private val cookieJar = MemoryCookieJar()

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .followRedirects(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    fun errors(): Map<String, String> = pluginErrors.toMap()

    fun platforms(): Set<String> = synchronized(this) { loadedPlatforms.toSet() }

    suspend fun reload(context: Context) {
        mutex.withLock { closeLocked() }
    }

    suspend fun ensure(context: Context) {
        withContext(Dispatchers.Default) {
            mutex.withLock { ensureLocked(context) }
        }
    }

    suspend fun inspectPlugin(context: Context, source: String): ParsedPluginMeta =
        withContext(Dispatchers.Default) {
            val q = QuickJs.create(Dispatchers.Default)
            try {
                q.memoryLimit = MAX_MEMORY
                q.maxStackSize = 2L * 1024 * 1024
                q.evaluationTimeoutMillis = PLUGIN_TIMEOUT_MS
                installBindings(q)
                q.evaluate<Any?>(runtime(context), filename = "runtime.js")
                q.evaluate<Any?>(libs(context), filename = "libs.js")
                val metaJson = q.evaluate<String>(
                    pluginWrapper("inspect", source),
                    filename = "plugin-inspect.js",
                )
                parseMeta(metaJson)
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                throw IllegalArgumentException(message.substringBefore('\n').take(300), e)
            } finally {
                runCatching { q.close() }
            }
        }

    suspend fun search(
        context: Context,
        platform: String,
        query: String,
        page: Int = 1,
        type: String = "music",
    ): List<MusicTrack> = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureLocked(context)
            val q = engine ?: return@withLock emptyList()
            val code = "__mfSearch(${JSONObject.quote(platform)}, ${JSONObject.quote(query)}, $page, ${JSONObject.quote(type)})"
            val json = withTimeout(PLUGIN_TIMEOUT_MS) {
                q.evaluate<String>(code, filename = "search.js")
            }
            val array = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull() ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    parseTrack(platform, item)?.let { add(it) }
                }
            }
            log("搜索 $platform: ${tracks.size}/${array.length()} 条 (${json.length}B)")
            if (array.length() == 0) log("返回片段 ${json.take(220)}")
            tracks
        }
    }

    suspend fun mediaSource(
        context: Context,
        platform: String,
        track: MusicTrack,
        quality: String = "standard",
    ): MusicSourceInfo? = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureLocked(context)
            val q = engine ?: return@withLock null
            val code = "__mfMedia(${JSONObject.quote(platform)}, ${JSONObject.quote(track.rawJson)}, ${JSONObject.quote(quality)})"
            val json = withTimeout(PLUGIN_TIMEOUT_MS) {
                q.evaluate<String>(code, filename = "media.js")
            }
            parseSource(json)
        }
    }

    suspend fun lyric(
        context: Context,
        platform: String,
        track: MusicTrack,
    ): String? = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureLocked(context)
            val q = engine ?: return@withLock null
            val code = "__mfLyric(${JSONObject.quote(platform)}, ${JSONObject.quote(track.rawJson)})"
            val json = withTimeout(PLUGIN_TIMEOUT_MS) {
                q.evaluate<String>(code, filename = "lyric.js")
            }
            runCatching { JSONObject(json).optString("rawLrc") }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun ensureLocked(context: Context) {
        val plugins = MusicPluginStore.enabledSources(context)
        val signature = plugins.joinToString("|") { "${it.first.fileName}:${it.first.platform}" }
        if (engine != null && signature == loadedSignature) return
        closeLocked()
        loadedSignature = signature
        pluginErrors.clear()
        loadedPlatforms.clear()
        if (plugins.isEmpty()) return

        val q = QuickJs.create(Dispatchers.Default)
        try {
            q.memoryLimit = MAX_MEMORY
            q.maxStackSize = 2L * 1024 * 1024
            q.evaluationTimeoutMillis = PLUGIN_TIMEOUT_MS
            installBindings(q)
            q.evaluate<Any?>(runtime(context), filename = "runtime.js")
            q.evaluate<Any?>(libs(context), filename = "libs.js")
            for ((meta, source) in plugins) {
                try {
                    val metaJson = q.evaluate<String>(
                        pluginWrapper(meta.id, source),
                        filename = "plugin-${meta.id}.js",
                    )
                    val parsed = parseMeta(metaJson)
                    synchronized(this) { loadedPlatforms.add(parsed.platform) }
                } catch (e: Exception) {
                    val message = (e.message ?: e.toString()).substringBefore('\n').take(240)
                    pluginErrors[meta.platform.ifBlank { meta.fileName }] = message
                    log("加载失败 ${meta.platform}: $message")
                    Log.w(TAG, "plugin ${meta.platform} failed: $message")
                }
            }
            engine = q
        } catch (e: Exception) {
            pluginErrors["runtime"] = (e.message ?: e.toString()).take(240)
            runCatching { q.close() }
        }
    }

    private fun closeLocked() {
        runCatching { engine?.close() }
        engine = null
        loadedSignature = null
        pluginErrors.clear()
        synchronized(this) { loadedPlatforms.clear() }
    }

    private fun installBindings(q: QuickJs) {
        q.function<Any?>("__log") { args ->
            val line = args.joinToString(" ") { it?.toString() ?: "null" }
            Log.d(TAG, line)
            log("JS $line")
            null
        }
        q.asyncFunction<String>("__http") { args ->
            httpRequest(args.firstOrNull()?.toString().orEmpty())
        }
        q.asyncFunction<Any?>("__sleep") { args ->
            val ms = (args.firstOrNull() as? Number)?.toLong() ?: 0L
            delay(ms.coerceIn(0L, 60_000L))
            null
        }
    }

    private fun pluginWrapper(id: String, source: String): String = buildString {
        append("(function(){var module={exports:{}};var exports=module.exports;\n")
        append("(function(require,__musicfree_require,module,exports,console,env,URL,process){\n")
        append(source)
        append("\n})(__mfRequire,__mfRequire,module,exports,console,__mfEnv,globalThis.URL,__mfProcess);\n")
        append("return __mfAdd(")
        append(JSONObject.quote(id))
        append(", module.exports);\n})()")
    }

    private fun parseMeta(json: String): ParsedPluginMeta {
        val obj = JSONObject(json)
        val platform = obj.optString("platform")
        require(platform.isNotBlank()) { "插件缺少 platform" }
        val supported = obj.optJSONArray("supported") ?: JSONArray()
        return ParsedPluginMeta(
            platform = platform,
            version = obj.optString("version").ifBlank { "0.0.0" },
            supported = buildList {
                for (i in 0 until supported.length()) add(supported.optString(i))
            },
        )
    }

    private fun libs(context: Context): String =
        libsSource ?: context.assets.open("musicfree/libs.js").bufferedReader().use { it.readText() }
            .also { libsSource = it }

    private fun runtime(context: Context): String =
        runtimeSource ?: context.assets.open("musicfree/runtime.js").bufferedReader().use { it.readText() }
            .also { runtimeSource = it }

    private suspend fun httpRequest(json: String): String = withContext(Dispatchers.IO) {
        try {
            val request = JSONObject(json)
            val url = request.optString("url").trim()
            if (url.isBlank()) return@withContext errorJson("URL 为空")
            val method = request.optString("method", "GET").uppercase()
            val headerObj = request.optJSONObject("headers")
            val builder = okhttp3.Headers.Builder()
            var contentType: String? = null
            var explicitCookie: String? = null
            if (headerObj != null) {
                val keys = headerObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.equals("cookie", true)) {
                        explicitCookie = headerObj.optString(key)
                        continue
                    }
                    if (key.equals("host", true) ||
                        key.equals("content-length", true) ||
                        key.equals("connection", true) ||
                        key.equals("transfer-encoding", true) ||
                        key.equals("accept-encoding", true) ||
                        key.equals("expect", true) ||
                        key.equals("upgrade", true)
                    ) continue
                    val value = headerObj.optString(key)
                    if (key.equals("content-type", true)) contentType = value
                    runCatching { builder.add(key, value) }
                }
            }
            val httpUrl = runCatching { url.toHttpUrl() }.getOrNull()
                ?: return@withContext errorJson("URL 无效")
            val cookieHeader = explicitCookie?.takeIf { it.isNotBlank() }
                ?: cookieJar.loadForRequest(httpUrl).takeIf { it.isNotEmpty() }?.let { cookies ->
                    cookies.joinToString("; ") { "${it.name}=${it.value}" }
                }
            if (!cookieHeader.isNullOrBlank()) builder.add("Cookie", cookieHeader)
            if (builder.get("Accept") == null) builder.add("Accept", "*/*")
            if (builder.get("User-Agent") == null) {
                builder.add(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0 Mobile Safari/537.36",
                )
            }
            val bodyText = request.optString("body")
            val body = if (method == "GET" || method == "HEAD") {
                null
            } else {
                val mediaType = (contentType ?: guessContentType(bodyText))?.toMediaTypeOrNull()
                bodyText.toRequestBody(mediaType)
            }
            val call = Request.Builder()
                .url(url)
                .headers(builder.build())
                .method(method, body)
                .build()
            httpClient.newCall(call).execute().use { response ->
                runCatching {
                    val cookies = okhttp3.Cookie.parseAll(httpUrl, response.headers)
                    if (cookies.isNotEmpty()) cookieJar.saveFromResponse(httpUrl, cookies)
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val responseType = response.header("Content-Type").orEmpty().lowercase()
                val binary = request.optBoolean("binary", false)
                val out = JSONObject()
                out.put("status", response.code)
                out.put("statusText", response.message)
                out.put("binary", binary)
                out.put(
                    "body",
                    if (binary) Base64.encodeToString(bytes, Base64.NO_WRAP)
                    else String(bytes, Charsets.UTF_8),
                )
                val headerOut = JSONObject()
                for ((key, values) in response.headers.toMultimap()) {
                    if (values.size == 1) headerOut.put(key, values[0])
                    else headerOut.put(key, JSONArray(values))
                }
                out.put("headers", headerOut)
                log("HTTP $method ${url.take(140)} -> ${response.code} (${bytes.size}B, ${responseType}, binary=$binary)")
                out.toString()
            }
        } catch (e: Exception) {
            log("HTTP 失败：${e.message ?: e.toString()}")
            errorJson(e.message ?: e.toString())
        }
    }

    private fun guessContentType(body: String): String? {
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "application/json; charset=utf-8"
            trimmed.contains('=') -> "application/x-www-form-urlencoded; charset=utf-8"
            else -> null
        }
    }

    private fun errorJson(message: String): String =
        JSONObject().put("error", message).toString()

    private class MemoryCookieJar : CookieJar {
        private val store = HashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(store) {
                for (cookie in cookies) {
                    val list = store.getOrPut(cookie.domain) { mutableListOf() }
                    list.removeAll { it.name == cookie.name && it.path == cookie.path }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
            store.entries
                .filter { url.host.endsWith(it.key.removePrefix(".")) }
                .flatMap { it.value }
                .filter { it.matches(url) }
        }
    }
}
