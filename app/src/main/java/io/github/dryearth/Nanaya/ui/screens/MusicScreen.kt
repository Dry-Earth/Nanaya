package io.github.dryearth.Nanaya.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.music.InstalledPlugin
import io.github.dryearth.Nanaya.music.MusicFreeEngine
import io.github.dryearth.Nanaya.music.MusicPlayback
import io.github.dryearth.Nanaya.music.MusicPluginStore
import io.github.dryearth.Nanaya.music.MusicRepository
import io.github.dryearth.Nanaya.music.MusicTrack
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("search") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var plugins by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var pluginUrl by remember { mutableStateOf("") }
    var pluginBusy by remember { mutableStateOf(false) }
    var pluginErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadedPlatforms by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<InstalledPlugin?>(null) }

    val nowPlaying by MusicPlayback.nowPlaying.collectAsState()
    val playing by MusicPlayback.isPlaying.collectAsState()
    val playError by MusicPlayback.error.collectAsState()

    fun refreshPlugins() {
        scope.launch {
            plugins = withContext(Dispatchers.IO) { MusicPluginStore.list(context) }
            withContext(Dispatchers.IO) { MusicFreeEngine.ensure(context) }
            pluginErrors = MusicFreeEngine.errors()
            loadedPlatforms = MusicFreeEngine.platforms()
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isBlank() || busy) return
        busy = true
        message = null
        scope.launch {
            val found = runCatching { MusicRepository.searchAll(context, q, 40) }
                .getOrElse {
                    message = "搜索失败：${it.message}"
                    emptyList()
                }
            results = found
            searched = true
            busy = false
            if (found.isEmpty() && message == null) {
                val error = MusicRepository.lastErrors.value.entries.firstOrNull()
                message = if (error != null) {
                    "搜索没有结果：${error.key}：${error.value}"
                } else {
                    "没有找到「$q」。插件返回了空结果，可以换个关键词。"
                }
            }
        }
    }

    fun play(track: MusicTrack) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val outcome = runCatching { MusicRepository.playTrack(context, track) }
                .getOrElse { io.github.dryearth.Nanaya.music.PlayOutcome(false, "播放失败：${it.message}") }
            message = outcome.message
            busy = false
        }
    }

    fun playRandom() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val outcome = runCatching { MusicRepository.playRandom(context) }
                .getOrElse { io.github.dryearth.Nanaya.music.PlayOutcome(false, "播放失败：${it.message}") }
            message = outcome.message
            busy = false
        }
    }

    fun installUrl() {
        val url = pluginUrl.trim()
        if (url.isBlank() || pluginBusy) return
        pluginBusy = true
        message = null
        scope.launch {
            runCatching { MusicPluginStore.installFromUrl(context, url) }
                .onSuccess {
                    pluginUrl = ""
                    message = "已安装 ${it.platform} ${it.version}"
                }
                .onFailure { message = "安装失败：${it.message}" }
            plugins = withContext(Dispatchers.IO) { MusicPluginStore.list(context) }
            withContext(Dispatchers.IO) { MusicFreeEngine.ensure(context) }
            pluginErrors = MusicFreeEngine.errors()
            loadedPlatforms = MusicFreeEngine.platforms()
            pluginBusy = false
        }
    }

    fun testPlugins() {
        if (busy || pluginBusy) return
        busy = true
        message = null
        scope.launch {
            val found = runCatching { MusicRepository.searchAll(context, "稻香", 8) }
                .getOrElse { emptyList() }
            val errors = MusicRepository.lastErrors.value
            message = buildString {
                append("测试搜索「稻香」：${found.size} 个结果")
                if (errors.isNotEmpty()) {
                    append("；")
                    append(
                        errors.entries.take(2).joinToString("；") {
                            "${it.key}：${it.value.take(100)}"
                        },
                    )
                }
            }
            busy = false
        }
    }

    fun installUri(uri: android.net.Uri?) {
        if (uri == null || pluginBusy) return
        pluginBusy = true
        message = null
        scope.launch {
            runCatching { MusicPluginStore.installFromUri(context, uri) }
                .onSuccess { message = "已安装 ${it.platform} ${it.version}" }
                .onFailure { message = "安装失败：${it.message}" }
            plugins = withContext(Dispatchers.IO) { MusicPluginStore.list(context) }
            withContext(Dispatchers.IO) { MusicFreeEngine.ensure(context) }
            pluginErrors = MusicFreeEngine.errors()
            loadedPlatforms = MusicFreeEngine.platforms()
            pluginBusy = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        installUri(uri)
    }

    LaunchedEffect(Unit) { refreshPlugins() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("音乐", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("插件音源 · 语音点歌", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Pill(
                if (plugins.isEmpty()) "无插件" else "${plugins.count { it.enabled }} 个音源",
                selected = plugins.any { it.enabled },
                color = if (plugins.any { it.enabled }) Glass.Mint else Glass.Amber,
            )
        }

        nowPlaying?.let { track ->
            GlassPanel(corner = 16.dp, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Glass.Violet, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = Glass.Accent,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { MusicPlayback.toggle() }
                            .padding(3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = "停止",
                        tint = Glass.Faint,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { MusicPlayback.stop() }
                            .padding(2.dp),
                    )
                }
                playError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Glass.Rose)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("搜索", selected = tab == "search", color = Glass.Accent) { tab = "search" }
            Pill("插件", selected = tab == "plugins", color = Glass.Violet) {
                tab = "plugins"
                refreshPlugins()
            }
            Spacer(Modifier.weight(1f))
            Pill("停止", color = Glass.Faint) { MusicPlayback.stop() }
        }

        when (tab) {
            "plugins" -> {
                GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                    Text("安装 MusicFree 音源插件", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                    Spacer(Modifier.height(4.dp))
                    BasicTextField(
                        value = pluginUrl,
                        onValueChange = { pluginUrl = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Glass.Text,
                        ),
                        cursorBrush = SolidColor(Glass.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { installUrl() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (pluginUrl.isEmpty()) {
                                Text(
                                    "https://.../plugin.js",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Glass.Faint,
                                    ),
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionButton(
                            if (pluginBusy) "处理中" else "从 URL 安装",
                            accent = true,
                            enabled = !pluginBusy && pluginUrl.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            onClick = { installUrl() },
                        )
                        ActionButton(
                            "从文件安装",
                            enabled = !pluginBusy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "application/javascript",
                                        "text/javascript",
                                        "application/octet-stream",
                                        "*/*",
                                    ),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "插件是第三方 JavaScript，运行时拥有网络访问权限；只安装可信来源。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Amber,
                    )
                    Spacer(Modifier.height(6.dp))
                    ActionButton(
                        if (busy) "测试中…" else "测试已启用插件（搜索「稻香」）",
                        enabled = !busy && !pluginBusy && plugins.any { it.enabled },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { testPlugins() },
                    )
                }

                confirmDelete?.let { plugin ->
                    GlassPanel(tint = Glass.Rose, corner = 14.dp, padding = PaddingValues(10.dp)) {
                        Text(
                            "删除音源插件「${plugin.platform}」？",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Rose,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("取消", modifier = Modifier.weight(1f), onClick = { confirmDelete = null })
                            ActionButton(
                                "删除",
                                accent = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val target = plugin
                                    confirmDelete = null
                                    scope.launch {
                                        MusicPluginStore.remove(context, target.id)
                                        plugins = withContext(Dispatchers.IO) { MusicPluginStore.list(context) }
                                        withContext(Dispatchers.IO) { MusicFreeEngine.ensure(context) }
                                        pluginErrors = MusicFreeEngine.errors()
                                        loadedPlatforms = MusicFreeEngine.platforms()
                                        message = "已删除 ${target.platform}"
                                    }
                                },
                            )
                        }
                    }
                }

                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(10.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(plugins, key = { it.id }) { plugin ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        plugin.platform,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Glass.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val loaded = plugin.platform in loadedPlatforms
                                    Text(
                                        when {
                                            !plugin.enabled -> "v${plugin.version} · 已停用"
                                            loaded -> "v${plugin.version} · 运行中"
                                            else -> "v${plugin.version} · 未加载"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            !plugin.enabled -> Glass.Faint
                                            loaded -> Glass.Mint
                                            else -> Glass.Rose
                                        },
                                    )
                                }
                                Text(
                                    if (plugin.enabled) "停用" else "启用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Accent,
                                    modifier = Modifier
                                        .clickable(enabled = !pluginBusy) {
                                            scope.launch {
                                                MusicPluginStore.setEnabled(context, plugin.id, !plugin.enabled)
                                                plugins = withContext(Dispatchers.IO) { MusicPluginStore.list(context) }
                                                withContext(Dispatchers.IO) { MusicFreeEngine.ensure(context) }
                                                pluginErrors = MusicFreeEngine.errors()
                                                loadedPlatforms = MusicFreeEngine.platforms()
                                            }
                                        }
                                        .padding(horizontal = 6.dp),
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Rose,
                                    modifier = Modifier
                                        .clickable { confirmDelete = plugin }
                                        .padding(horizontal = 4.dp),
                                )
                            }
                            pluginErrors[plugin.platform]?.let { error ->
                                Text(
                                    "加载失败：$error",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Rose,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (plugins.isEmpty()) {
                            item {
                                Text(
                                    "还没有插件。支持直接安装 MusicFree 的 .js 插件文件；插件本身不随 Nanaya 分发。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.Muted,
                                )
                            }
                        }
                        pluginErrors["runtime"]?.let { error ->
                            item {
                                Text(
                                    "运行环境错误：$error",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Rose,
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Glass.Text),
                            cursorBrush = SolidColor(Glass.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索歌曲或歌手",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Glass.Faint,
                                    )
                                }
                                inner()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        ActionButton(
                            if (busy) "处理中" else "搜索",
                            accent = true,
                            enabled = !busy && query.isNotBlank(),
                            onClick = { search() },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("随便放一首", color = Glass.Mint) { playRandom() }
                        Pill("清空结果", color = Glass.Faint) {
                            results = emptyList()
                            searched = false
                        }
                    }
                }

                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(8.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "${it.platform}:${it.id}:${it.title}" }) { track ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { play(track) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Glass.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        track.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (track.durationMs > 0) {
                                    Text(
                                        formatDuration(track.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "播放",
                                    tint = Glass.Accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (results.isEmpty()) {
                            item {
                                Text(
                                    if (busy) "正在搜索…"
                                    else if (searched) "没有结果。"
                                    else "搜索一首歌，或者回到助手直接说「播放稻香」。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.Muted,
                                )
                            }
                        }
                    }
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
        }
        if (searched && results.isNotEmpty() && tab == "search") {
            Text(
                "点击结果立即播放；播放需要联网，音源由插件提供。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
