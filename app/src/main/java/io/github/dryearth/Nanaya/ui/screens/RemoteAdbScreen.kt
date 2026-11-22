package io.github.dryearth.Nanaya.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.AdbManager
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteAdbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(AdbManager.remoteConnected()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    fun runCommand(cmd: String) {
        val text = cmd.trim()
        if (text.isBlank() || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AdbManager.runRemote(text, 20_000) }
            connected = AdbManager.remoteConnected()
            output = buildString {
                append("$ "); append(text); append("\n")
                if (result == null) {
                    append("失败：")
                    append(AdbManager.lastRunError().ifBlank { "未连接或没有输出" })
                } else {
                    append(result.output.ifBlank { "(无输出)" })
                    append("\n[exit "); append(result.code); append("]")
                }
                append("\n\n")
                append(output)
            }
            busy = false
        }
    }

    fun pairAndConnect() {
        val port = pairPort.trim().toIntOrNull()
        if (host.trim().isEmpty() || port == null || code.trim().length != 6) {
            message = "请填写目标 IP、配对端口和 6 位配对码"
            return
        }
        busy = true
        message = "正在配对目标设备…"
        scope.launch {
            val pairError = withContext(Dispatchers.IO) {
                AdbManager.pairRemote(context, host.trim(), port, code.trim())
            }
            if (pairError != null) {
                busy = false
                message = "配对失败：$pairError"
                return@launch
            }
            val targetPort = connectPort.trim().toIntOrNull()
            message = if (targetPort == null) {
                "配对成功；请填写目标无线调试首页的连接端口"
            } else {
                "配对成功，正在连接…"
            }
            if (targetPort != null) {
                val error = withContext(Dispatchers.IO) {
                    AdbManager.connectRemote(context, host.trim(), targetPort)
                }
                connected = AdbManager.remoteConnected()
                busy = false
                message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
            } else {
                busy = false
            }
        }
    }

    fun connectOnly() {
        val port = connectPort.trim().toIntOrNull()
        if (host.trim().isEmpty() || port == null) {
            message = "请填写目标 IP 和连接端口"
            return
        }
        busy = true
        message = "正在连接…"
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                AdbManager.connectRemote(context, host.trim(), port)
            }
            connected = AdbManager.remoteConnected()
            busy = false
            message = if (error == null) "远程 ADB 已连接" else "连接失败：$error"
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
            Field("连接端口", connectPort, "无线调试首页端口", numeric = true) { connectPort = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "配对并连接",
                    accent = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { pairAndConnect() },
                )
                ActionButton(
                    "只连接",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { connectOnly() },
                )
            }
            if (connected) {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "断开",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AdbManager.disconnectRemote()
                        connected = false
                        message = "已断开远程 ADB"
                    },
                )
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = if (connected) Glass.Mint else Glass.Amber)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("设备信息", color = Glass.Ice) {
                runCommand("id; getprop ro.product.model; getprop ro.build.version.release; wm size; dumpsys battery | head -12")
            }
            Pill("应用列表", color = Glass.Violet) {
                runCommand("pm list packages -3")
            }
            Pill("屏幕", color = Glass.Mint) {
                runCommand("wm size; wm density")
            }
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(12.dp),
        ) {
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
                ActionButton(
                    "执行",
                    accent = true,
                    enabled = !busy && command.isNotBlank() && connected,
                    onClick = { runCommand(command) },
                )
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
                onValueChange = { onChange(it.take(64)) },
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
