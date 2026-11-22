package io.github.dryearth.Nanaya.ui.screens

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
    var output by remember { mutableStateOf("") }
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

    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        val result = AdbManager.runRemote(cmd, 20_000)
        if (result == null) {
            "失败：${AdbManager.lastRunError().ifBlank { "未连接或没有输出" }}"
        } else {
            result.output.ifBlank { "(无输出)" } + "\n[exit ${result.code}]"
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
        busy = true
        scope.launch {
            output = "$ ${text}\n" + exec(text) + "\n\n" + output
            busy = false
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
            output = "$ $action $pkg\n" + exec(cmd) + "\n\n" + output
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

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
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
            Field("目标 IP", host, "例如 192.168.1.23") { host = it }
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
                                busy = false
                                message = "配对成功；请填写连接端口再点「只连接」"
                            } else {
                                val error = withContext(Dispatchers.IO) {
                                    AdbManager.connectRemote(context, host.trim(), target)
                                }
                                connected = AdbManager.remoteConnected()
                                busy = false
                                message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
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

        if (devices.isNotEmpty()) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("命令行", selected = mode == "shell", color = Glass.Accent) { mode = "shell" }
            Pill("应用", selected = mode == "apps", color = Glass.Violet) {
                mode = "apps"
                loadApps()
            }
            Pill("文件", selected = mode == "files", color = Glass.Ice) {
                mode = "files"
                loadFiles()
            }
        }

        when (mode) {
            "apps" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("刷新应用", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { loadApps() })
                    ActionButton("安装 APK", enabled = connected && !busy, modifier = Modifier.weight(1f), onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) })
                }
                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(8.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(packages) { pkg ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pkg,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = Glass.Text,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Pill("停止", color = Glass.Amber) {
                                    performAppAction(pkg, "停止", "am force-stop $pkg")
                                }
                                Spacer(Modifier.width(4.dp))
                                Pill("卸载", color = Glass.Rose) {
                                    performAppAction(pkg, "卸载", "pm uninstall --user 0 $pkg")
                                }
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
                GlassPanel(modifier = Modifier.weight(1f), corner = 16.dp, padding = PaddingValues(8.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (entry.directory) {
                                        remoteDir = if (remoteDir == "/") "/${entry.name}" else "$remoteDir/${entry.name}"
                                        loadFiles()
                                    } else {
                                        remotePath = if (remoteDir == "/") "/${entry.name}" else "$remoteDir/${entry.name}"
                                        downloadPicker.launch(entry.name)
                                    }
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (entry.directory) "📁 ${entry.name}" else "📄 ${entry.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry.directory) Glass.Accent else Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                    SelectionContainer {
                        Text(
                            output.ifBlank { "连接目标设备后，可以在这里执行远程 shell 命令。" },
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = Glass.Muted,
                            ),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        )
                    }
                }

                GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Glass.Accent))
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
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton("执行", accent = true, enabled = !busy && command.isNotBlank() && connected, onClick = { runCommand(command) })
                    }
                    Spacer(Modifier.height(6.dp))
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
