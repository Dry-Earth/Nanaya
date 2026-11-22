package io.github.dryearth.Nanaya.ui.screens

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.AdbManager
import io.github.dryearth.Nanaya.core.AdbScanResult
import io.github.dryearth.Nanaya.core.LanAdbScanner
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SavedRemote(val host: String, val pairPort: String, val connectPort: String)

private data class RemoteEntry(val name: String, val directory: Boolean)

private data class RemoteShellEntry(val label: String, val result: String, val code: Int)

@Composable
fun RemoteAdbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var host by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(AdbManager.remoteConnected()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("shell") }
    var command by remember { mutableStateOf("") }
    val shellEntries = remember { mutableStateListOf<RemoteShellEntry>() }
    val shellListState = rememberLazyListState()
    val shellFocus = remember { FocusRequester() }
    val shellTap = remember { MutableInteractionSource() }
    var remotePath by remember { mutableStateOf("/data/local/tmp/nanaya-file") }
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var packages by remember { mutableStateOf<List<String>>(emptyList()) }
    var remoteDir by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var devices by remember {
        mutableStateOf(
            prefs.remoteAdbDevices.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size >= 3) SavedRemote(parts[0], parts[1], parts[2]) else null
            },
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<AdbScanResult>>(emptyList()) }
    var scanFinished by remember { mutableStateOf(false) }
    val deviceInfo = remember { mutableStateMapOf<String, String>() }
    var infoBusy by remember { mutableStateOf(false) }
    var showReboot by remember { mutableStateOf(false) }
    var showDpi by remember { mutableStateOf(false) }
    var dpiInput by remember { mutableStateOf("") }
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var connectionExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shellEntries.size) {
        if (shellEntries.isNotEmpty()) shellListState.animateScrollToItem(0)
    }

    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        val result = AdbManager.runRemote(cmd, 20_000)
        if (result == null) {
            "失败：${AdbManager.lastRunError().ifBlank { "未连接或没有输出" }}"
        } else {
            result.output.ifBlank { "(无输出)" } + "\n[exit ${result.code}]"
        }
    }

    suspend fun shellEntry(label: String, cmd: String): RemoteShellEntry = withContext(Dispatchers.IO) {
        val result = AdbManager.runRemote(cmd, 20_000)
        if (result == null) {
            RemoteShellEntry(
                label,
                "失败：${AdbManager.lastRunError().ifBlank { "未连接或没有输出" }}",
                -1,
            )
        } else {
            RemoteShellEntry(label, result.output.ifBlank { "(无输出)" }, result.code)
        }
    }

    fun rememberDevice() {
        if (host.isBlank()) return
        val entry = SavedRemote(host.trim(), pairPort.trim(), connectPort.trim())
        devices = (listOf(entry) + devices.filter { it.host != entry.host }).take(10)
        prefs.remoteAdbDevices = devices.joinToString("\n") {
            "${it.host}|${it.pairPort}|${it.connectPort}"
        }
    }

    fun useDevice(device: SavedRemote) {
        host = device.host
        pairPort = device.pairPort
        connectPort = device.connectPort
        code = ""
    }

    fun runCommand(cmd: String) {
        val text = cmd.trim()
        if (text.isBlank() || busy) return
        if (!connected) {
            message = "先连接设备"
            return
        }
        busy = true
        command = ""
        scope.launch {
            shellEntries += shellEntry(text, text)
            busy = false
        }
    }

    fun captureShot() {
        if (!connected || busy) return
        busy = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { AdbManager.captureRemoteScreenshot() }
            if (bytes != null) {
                screenshot = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                message = "截图已获取"
            } else {
                message = "截图失败：${AdbManager.lastRunError()}"
            }
            busy = false
        }
    }

    fun runAction(label: String, cmd: String) {
        if (!connected || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AdbManager.runRemote(cmd, 10_000) }
            busy = false
            message = if (result == null) {
                "$label 失败：${AdbManager.lastRunError().ifBlank { "设备没有响应" }}"
            } else {
                "$label 完成"
            }
        }
    }

    fun rebootTo(cmd: String, label: String) {
        showReboot = false
        if (!connected || busy) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { AdbManager.runRemote(cmd, 5_000) }
            busy = false
            connected = false
            message = "已发送$label，设备会断开连接"
        }
    }

    fun applyDpi(reset: Boolean) {
        val value = dpiInput.trim().toIntOrNull()
        if (!reset && (value == null || value !in 120..960)) {
            message = "DPI 请输入 120–960 之间的数值"
            return
        }
        showDpi = false
        runAction(if (reset) "恢复 DPI" else "设置 DPI", if (reset) "wm density reset" else "wm density $value")
    }

    fun loadLogcat() {
        if (!connected || busy) return
        busy = true
        scope.launch {
            shellEntries += shellEntry("logcat -d -t 400", "logcat -d -t 400 2>&1")
            busy = false
            mode = "shell"
        }
    }

    fun loadProcesses() {
        if (!connected || busy) return
        busy = true
        scope.launch {
            shellEntries += shellEntry(
                "ps · 进程列表",
                "ps -A -o PID,USER,NAME,%CPU,%MEM --sort=-%CPU 2>/dev/null | head -30 || ps -A | head -30",
            )
            busy = false
            mode = "shell"
        }
    }

    fun deleteRemote(path: String) {
        pendingDelete = null
        if (!connected || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AdbManager.runRemote("rm -rf \"$path\"", 10_000)
            }
            busy = false
            message = if (result == null) "删除失败：${AdbManager.lastRunError()}" else "已删除 $path"
            loadFiles()
        }
    }

    fun loadInfo() {
        if (!connected) {
            message = "先连接设备"
            return
        }
        infoBusy = true
        scope.launch {
            val raw = exec(
                "echo MODEL=\$(getprop ro.product.model);" +
                    "echo BRAND=\$(getprop ro.product.brand);" +
                    "echo RELEASE=\$(getprop ro.build.version.release);" +
                    "echo SDK=\$(getprop ro.build.version.sdk);" +
                    "echo ABI=\$(getprop ro.product.cpu.abi);" +
                    "echo SOC=\$(getprop ro.soc.model);" +
                    "echo SIZE=\$(wm size | head -1);" +
                    "echo DENSITY=\$(wm density | head -1);" +
                    "echo MEM=\$(grep MemTotal /proc/meminfo);" +
                    "echo DISK=\$(df -h /data | tail -1);" +
                    "echo BATTERY=\$(dumpsys battery | grep -E 'level|status' | tr '\\n' ' ');" +
                    "echo IP=\$(ip -f inet addr show 2>/dev/null | awk '/inet /{print \$2}' | grep -v 127.0.0.1 | head -1)",
            )
            val parsed = raw.lineSequence()
                .mapNotNull { line ->
                    val sep = line.indexOf('=')
                    if (sep > 0) line.substring(0, sep).trim() to line.substring(sep + 1).trim() else null
                }
                .toMap()
            deviceInfo.clear()
            deviceInfo.putAll(parsed)
            infoBusy = false
        }
    }

    fun startScan() {
        if (scanning) return
        scanning = true
        scanFinished = false
        scanResults = emptyList()
        scope.launch {
            val found = withContext(Dispatchers.IO) { LanAdbScanner.scan(context) }
            scanResults = found
            scanning = false
            scanFinished = true
            if (found.isEmpty()) {
                message = "没有发现设备：确认同一 Wi-Fi/热点，且目标已开启 ADB 调试"
            }
        }
    }

    fun connectScanned(result: AdbScanResult) {
        host = result.host
        if (result.kind == AdbScanResult.Kind.PAIRING) {
            pairPort = result.port.toString()
            connectPort = ""
            message = "已填入 ${result.host} 的配对端口，请输入目标屏幕上的 6 位配对码"
            return
        }
        pairPort = ""
        connectPort = result.port.toString()
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                AdbManager.connectRemote(context, result.host, result.port)
            }
            connected = AdbManager.remoteConnected()
            busy = false
            message = if (error == null) "已连接 ${result.host}:${result.port}" else "连接失败：$error"
            if (error == null) {
                connectionExpanded = false
                scanResults = emptyList()
                scanFinished = false
                rememberDevice()
            }
        }
    }

    LaunchedEffect(mode, connected) {
        if (mode == "device" && connected && deviceInfo.isEmpty() && !infoBusy) {
            loadInfo()
        }
    }

    fun loadApps() {
        if (!connected) return
        busy = true
        scope.launch {
            packages = exec("pm list packages -3")
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:") }
                .sorted()
                .toList()
            busy = false
            message = "已加载 ${packages.size} 个第三方应用"
        }
    }

    fun loadFiles() {
        if (!connected) return
        busy = true
        scope.launch {
            val raw = exec("ls -1p \"$remoteDir\" 2>&1")
            entries = raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "." && it != ".." && !it.startsWith("[exit") && !it.startsWith("失败") }
                .map { RemoteEntry(it.removeSuffix("/"), it.endsWith("/")) }
                .toList()
            busy = false
        }
    }

    fun performAppAction(pkg: String, action: String, cmd: String) {
        busy = true
        scope.launch {
            shellEntries += shellEntry("$action $pkg", cmd)
            message = "$action：$pkg"
            busy = false
            loadApps()
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull() ?: "nanaya-upload.bin"
            val target = if (mode == "files") "$remoteDir/$name" else remotePath
            val error = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    AdbManager.pushRemote(input, target)
                } ?: "无法读取所选文件"
            }
            busy = false
            message = error ?: "已上传到 $target"
            if (error == null && mode == "files") loadFiles()
        }
    }

    val downloadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    AdbManager.pullRemote(remotePath, stream)
                } ?: "无法写入本地文件"
            }
            busy = false
            message = error ?: "文件已下载"
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val target = "/data/local/tmp/nanaya-install.apk"
            val pushError = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    AdbManager.pushRemote(input, target)
                } ?: "无法读取 APK"
            }
            if (pushError != null) {
                busy = false
                message = "APK 上传失败：$pushError"
                return@launch
            }
            message = exec("pm install -r $target").take(300)
            busy = false
        }
    }

    fun exportApk(pkg: String) {
        selectedPkg = null
        if (!connected || busy) return
        busy = true
        scope.launch {
            val raw = withContext(Dispatchers.IO) { AdbManager.runRemote("pm path $pkg", 8_000) }
            val path = raw?.output
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("package:") }
                ?.removePrefix("package:")
            busy = false
            if (path.isNullOrBlank()) {
                message = "没有找到 $pkg 的 APK 路径"
            } else {
                remotePath = path
                downloadPicker.launch("$pkg.apk")
            }
        }
    }

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
                Text("远程 ADB", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("手机控制另一台 Android 设备", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Pill(
                if (busy) "处理中" else if (connected) "已连接" else "未连接",
                selected = connected,
                color = if (connected) Glass.Mint else Glass.Faint,
            )
        }

        GlassPanel(corner = 16.dp, padding = PaddingValues(12.dp)) {
            if (connected && !connectionExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("已连接", style = MaterialTheme.typography.labelSmall, color = Glass.Mint)
                        Text(
                            "$host:${connectPort.ifBlank { "?" }}",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = Glass.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "连接设置",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Accent,
                        modifier = Modifier.clickable { connectionExpanded = true },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "断开",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Rose,
                        modifier = Modifier.clickable {
                            AdbManager.disconnectRemote()
                            connected = false
                            connectionExpanded = false
                            message = "已断开"
                        },
                    )
                }
            } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f)) {
                    Field("目标 IP", host, "例如 192.168.1.23") { host = it }
                }
                ActionButton(
                    if (scanning) "扫描中…" else "扫描",
                    enabled = !scanning && !busy,
                    modifier = Modifier.padding(bottom = 1.dp),
                    onClick = { startScan() },
                )
            }
            Text(
                "扫描同一 Wi-Fi 下的车机 / 电视 / 平板，需已开启 ADB 调试",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
            Text(
                if (showHelp) "收起连接帮助" else "车机 / 平板连接帮助",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Accent,
                modifier = Modifier
                    .clickable { showHelp = !showHelp }
                    .padding(top = 4.dp),
            )
            if (showHelp) {
                Text(
                    "车机 / 电视：设置 → 关于 → 连续点版本号进入开发者模式，再打开 USB 调试或 ADB 调试；" +
                        "部分车型需要工厂密码。开启后一般监听 5555，扫描或直接输入 IP 即可。\n" +
                        "平板 / 手机（Android 11+）：打开「无线调试」，在扫描结果里点「配对」并输入屏幕上的 6 位配对码，" +
                        "成功后会自动通过 mDNS 找到连接端口。\n" +
                        "扫描不到：确认在同一 Wi-Fi，关闭路由器的 AP 隔离；也可以先连车机自带热点再扫描。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("配对端口", pairPort, "5 位", numeric = true) { pairPort = it }
                }
                Box(Modifier.weight(1f)) {
                    Field("配对码", code, "6 位", numeric = true) { code = it }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("连接端口", connectPort, "连接端口", numeric = true) { connectPort = it }
                }
                ActionButton("保存设备", enabled = host.isNotBlank(), modifier = Modifier.padding(top = 16.dp), onClick = { rememberDevice() })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("配对并连接", accent = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    val port = pairPort.trim().toIntOrNull()
                    if (host.isBlank() || port == null || code.trim().length != 6) {
                        message = "请填写目标 IP、配对端口和 6 位配对码"
                    } else {
                        busy = true
                        scope.launch {
                            val pairError = withContext(Dispatchers.IO) {
                                AdbManager.pairRemote(context, host.trim(), port, code.trim())
                            }
                            if (pairError != null) {
                                busy = false
                                message = "配对失败：$pairError"
                                return@launch
                            }
                            rememberDevice()
                            val target = connectPort.trim().toIntOrNull()
                            if (target == null) {
                                message = "配对成功，正在查找无线调试端口…"
                                val discovered = withContext(Dispatchers.IO) {
                                    LanAdbScanner.findWirelessPort(context, host.trim())
                                }
                                if (discovered == null) {
                                    busy = false
                                    message = "配对成功；没有自动发现端口，请填写连接端口后点「只连接」"
                                } else {
                                    connectPort = discovered.toString()
                                    val error = withContext(Dispatchers.IO) {
                                        AdbManager.connectRemote(context, host.trim(), discovered)
                                    }
                                    connected = AdbManager.remoteConnected()
                                    busy = false
                                    message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
                                    if (error == null) connectionExpanded = false
                                }
                            } else {
                                val error = withContext(Dispatchers.IO) {
                                    AdbManager.connectRemote(context, host.trim(), target)
                                }
                                connected = AdbManager.remoteConnected()
                                busy = false
                                message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
                                if (error == null) connectionExpanded = false
                            }
                        }
                    }
                })
                ActionButton("只连接", enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    val port = connectPort.trim().toIntOrNull()
                    if (host.isBlank() || port == null) {
                        message = "请填写目标 IP 和连接端口"
                    } else {
                        busy = true
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                AdbManager.connectRemote(context, host.trim(), port)
                            }
                            connected = AdbManager.remoteConnected()
                            busy = false
                            message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
                            if (error == null) connectionExpanded = false
                        }
                    }
                })
            }
            if (connected) {
                Spacer(Modifier.height(6.dp))
                ActionButton("断开", modifier = Modifier.fillMaxWidth(), onClick = {
                    AdbManager.disconnectRemote()
                    connected = false
                    message = "已断开"
                })
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = if (connected) Glass.Mint else Glass.Amber)
            }
            }
        }

        if (!connected && (scanning || scanFinished || scanResults.isNotEmpty())) {
            GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "局域网设备",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                        modifier = Modifier.weight(1f),
                    )
                    if (scanning) {
                        Text("扫描中…", style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
                    }
                }
                scanResults.forEach { result ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { connectScanned(result) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                result.name.ifBlank { result.host },
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (result.kind) {
                                    AdbScanResult.Kind.WIRELESS -> "无线调试 · ${result.host}:${result.port}"
                                    AdbScanResult.Kind.PAIRING -> "需要配对 · ${result.host}:${result.port}"
                                    AdbScanResult.Kind.LAN -> "ADB 5555 · ${result.host}:${result.port}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                        Text(
                            if (result.kind == AdbScanResult.Kind.PAIRING) "配对" else "连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Accent,
                        )
                    }
                }
                if (scanFinished && scanResults.isEmpty()) {
                    Text(
                        "没有发现设备。可手动输入目标 IP；部分路由器开启了 AP 隔离，会阻止扫描。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Amber,
                    )
                }
            }
        }

        if (devices.isNotEmpty() && (!connected || connectionExpanded)) {
            GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                Text("已保存设备", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                devices.forEach { device ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${device.host}:${device.connectPort.ifBlank { "?" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Text,
                            modifier = Modifier.weight(1f).clickable { useDevice(device) },
                        )
                        Text("使用", style = MaterialTheme.typography.labelSmall, color = Glass.Accent, modifier = Modifier.clickable { useDevice(device) })
                        Spacer(Modifier.width(8.dp))
                        Text("删除", style = MaterialTheme.typography.labelSmall, color = Glass.Rose, modifier = Modifier.clickable {
                            devices = devices - device
                            prefs.remoteAdbDevices = devices.joinToString("\n") { "${it.host}|${it.pairPort}|${it.connectPort}" }
                        })
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("设备", selected = mode == "device", color = Glass.Mint) {
                mode = "device"
                selectedPkg = null
                if (connected && deviceInfo.isEmpty()) loadInfo()
            }
            Pill("命令行", selected = mode == "shell", color = Glass.Accent) {
                mode = "shell"
                selectedPkg = null
            }
            Pill("应用", selected = mode == "apps", color = Glass.Violet) {
                mode = "apps"
                selectedPkg = null
                loadApps()
            }
            Pill("文件", selected = mode == "files", color = Glass.Ice) {
                mode = "files"
                selectedPkg = null
                loadFiles()
            }
        }

        when (mode) {
            "device" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(
                        "刷新信息",
                        enabled = connected && !infoBusy,
                        modifier = Modifier.weight(1f),
                        onClick = { loadInfo() },
                    )
                    ActionButton(
                        "截图",
                        enabled = connected && !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { captureShot() },
                    )
                    ActionButton(
                        "日志",
                        enabled = connected && !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { loadLogcat() },
                    )
                }

                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(12.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        val shot = screenshot
                        if (shot != null) {
                            Image(
                                shot,
                                contentDescription = "远程截图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (infoBusy && deviceInfo.isEmpty()) {
                            Text("正在读取设备信息…", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                        } else if (deviceInfo.isEmpty()) {
                            Text(
                                if (connected) "点「刷新信息」读取设备信息" else "连接设备后显示型号、系统、屏幕、内存等信息",
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Muted,
                            )
                        } else {
                            val rows = listOf(
                                "品牌" to "BRAND", "型号" to "MODEL", "系统" to "RELEASE", "SDK" to "SDK",
                                "SoC" to "SOC", "架构" to "ABI", "屏幕" to "SIZE", "密度" to "DENSITY",
                                "内存" to "MEM", "存储" to "DISK", "电池" to "BATTERY", "网络" to "IP",
                            )
                            rows.forEach { (label, key) ->
                                val value = deviceInfo[key].orEmpty()
                                if (value.isNotBlank()) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Glass.Faint,
                                            modifier = Modifier.width(52.dp),
                                        )
                                        Text(
                                            value
                                                .replace("Physical size: ", "")
                                                .replace("Physical density: ", ""),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                            ),
                                            color = Glass.Text,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "屏幕尺寸、DPI 与重启都需要 ADB shell 权限；误改 DPI 可用「恢复默认」修复。",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Pill("重启", color = Glass.Rose) { showReboot = !showReboot }
                    Pill("DPI", color = Glass.Ice) { showDpi = !showDpi }
                    Pill("进程", color = Glass.Violet) { loadProcesses() }
                    Pill("息屏", color = Glass.Amber) { runAction("息屏", "input keyevent 223") }
                    Pill("亮屏", color = Glass.Mint) { runAction("亮屏", "input keyevent 224") }
                }

                if (showReboot) {
                    GlassPanel(tint = Glass.Rose, corner = 14.dp, padding = PaddingValues(10.dp)) {
                        Text(
                            "选择重启方式（设备会立即断开）",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Rose,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Pill("正常", color = Glass.Text) { rebootTo("reboot", "重启") }
                            Pill("Recovery", color = Glass.Amber) { rebootTo("reboot recovery", "进入 Recovery") }
                            Pill("Fastboot", color = Glass.Ice) { rebootTo("reboot bootloader", "进入 Bootloader") }
                            Pill("关机", color = Glass.Rose) { rebootTo("reboot -p", "关机") }
                        }
                    }
                }

                if (showDpi) {
                    GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                        Field("屏幕 DPI", dpiInput, "例如 320", numeric = true) { dpiInput = it }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActionButton(
                                "应用",
                                accent = true,
                                modifier = Modifier.weight(1f),
                                onClick = { applyDpi(false) },
                            )
                            ActionButton(
                                "恢复默认",
                                modifier = Modifier.weight(1f),
                                onClick = { applyDpi(true) },
                            )
                            ActionButton(
                                "取消",
                                modifier = Modifier.weight(1f),
                                onClick = { showDpi = false },
                            )
                        }
                    }
                }
            }
            "apps" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("刷新应用", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { loadApps() })
                    ActionButton("安装 APK", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) })
                }
                selectedPkg?.let { pkg ->
                    GlassPanel(corner = 12.dp, padding = PaddingValues(10.dp)) {
                        Text(
                            "操作：$pkg",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = Glass.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Pill("启动", color = Glass.Mint) {
                                selectedPkg = null
                                performAppAction(
                                    pkg,
                                    "启动",
                                    "monkey -p $pkg -c android.intent.category.LAUNCHER 1",
                                )
                            }
                            Pill("停止", color = Glass.Amber) {
                                selectedPkg = null
                                performAppAction(pkg, "停止", "am force-stop $pkg")
                            }
                            Pill("清除数据", color = Glass.Rose) {
                                selectedPkg = null
                                performAppAction(pkg, "清除数据", "pm clear $pkg")
                            }
                            Pill("停用", color = Glass.Ice) {
                                selectedPkg = null
                                performAppAction(pkg, "停用", "pm disable-user --user 0 $pkg")
                            }
                            Pill("启用", color = Glass.Mint) {
                                selectedPkg = null
                                performAppAction(pkg, "启用", "pm enable $pkg")
                            }
                            Pill("导出 APK", color = Glass.Ice) { exportApk(pkg) }
                            Pill("卸载", color = Glass.Rose) {
                                selectedPkg = null
                                performAppAction(pkg, "卸载", "pm uninstall --user 0 $pkg")
                            }
                        }
                    }
                }
                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(8.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(packages) { pkg ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPkg = if (selectedPkg == pkg) null else pkg }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    pkg,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = if (selectedPkg == pkg) Glass.Accent else Glass.Text,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("›", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                            }
                        }
                    }
                }
            }
            "files" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("上级", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = {
                        remoteDir = remoteDir.substringBeforeLast('/', "")
                        if (remoteDir.isBlank()) remoteDir = "/"
                        loadFiles()
                    })
                    ActionButton("刷新", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { loadFiles() })
                    ActionButton("上传", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { uploadPicker.launch(arrayOf("*/*")) })
                }
                Field("远程目录", remoteDir, "/sdcard") { remoteDir = it }
                pendingDelete?.let { path ->
                    GlassPanel(tint = Glass.Rose, corner = 14.dp, padding = PaddingValues(10.dp)) {
                        Text(
                            "确认删除目标设备上的这个路径？目录会递归删除。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Rose,
                        )
                        Text(
                            path,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = Glass.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("取消", modifier = Modifier.weight(1f), onClick = { pendingDelete = null })
                            ActionButton("删除", accent = true, modifier = Modifier.weight(1f), onClick = { deleteRemote(path) })
                        }
                    }
                }
                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(8.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            val path = if (remoteDir == "/") "/${entry.name}" else "$remoteDir/${entry.name}"
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (entry.directory) {
                                        remoteDir = path
                                        loadFiles()
                                    } else {
                                        remotePath = path
                                        downloadPicker.launch(entry.name)
                                    }
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (entry.directory) "📁 ${entry.name}" else "📄 ${entry.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry.directory) Glass.Accent else Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Rose,
                                    modifier = Modifier
                                        .clickable { pendingDelete = path }
                                        .padding(horizontal = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("设备信息", color = Glass.Ice) {
                        runCommand("id; getprop ro.product.model; getprop ro.build.version.release; wm size; dumpsys battery | head -12")
                    }
                    Pill("应用列表", color = Glass.Violet) { runCommand("pm list packages -3") }
                    Pill("截图", color = Glass.Mint) {
                        captureShot()
                    }
                }

                screenshot?.let { image ->
                    GlassPanel(corner = 14.dp, padding = PaddingValues(0.dp)) {
                        Image(
                            image,
                            contentDescription = "远程截图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        )
                    }
                }

                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(12.dp)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = shellTap,
                                indication = null,
                            ) { shellFocus.requestFocus() },
                    ) {
                        LazyColumn(
                            state = shellListState,
                            reverseLayout = true,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (shellEntries.isEmpty()) {
                                item {
                                    Text(
                                        "连接目标设备后，直接在下方输入，回车执行远程 shell 命令。",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 18.sp,
                                            color = Glass.Muted,
                                        ),
                                    )
                                }
                            } else {
                                items(count = shellEntries.size) { index ->
                                    val entry = shellEntries[shellEntries.lastIndex - index]
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                "$ ${entry.label}",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp,
                                                    color = Glass.Accent,
                                                ),
                                            )
                                            Text(
                                                entry.result,
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    lineHeight = 17.sp,
                                                    color = Glass.Text,
                                                ),
                                            )
                                            Text(
                                                "[exit ${entry.code}]",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = Glass.Faint,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = Glass.Accent,
                                ),
                            )
                            Spacer(Modifier.width(4.dp))
                            BasicTextField(
                                value = command,
                                onValueChange = { command = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Glass.Text,
                                ),
                                cursorBrush = SolidColor(Glass.Accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { runCommand(command) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(shellFocus)
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type == KeyEventType.KeyDown &&
                                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                        ) {
                                            runCommand(command)
                                            true
                                        } else {
                                            false
                                        }
                                    },
                            )
                        }
                    }
                }

                GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionButton("上传", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { uploadPicker.launch(arrayOf("*/*")) })
                        ActionButton("下载", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { downloadPicker.launch(remotePath.substringAfterLast('/')) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x0AFFFFFF))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
            }
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(128)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Glass.Text,
                    fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default,
                ),
                cursorBrush = SolidColor(Glass.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
