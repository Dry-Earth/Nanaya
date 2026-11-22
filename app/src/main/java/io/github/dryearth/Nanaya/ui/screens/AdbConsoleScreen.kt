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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdbConsoleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDangerous by remember { mutableStateOf<String?>(null) }
    val backend = Shell.backend()
    val privileged = Shell.isPrivileged()

    fun execute(cmd: String) {
        val text = cmd.trim()
        if (text.isBlank() || busy) return
        if (dangerousCommand(text)) {
            pendingDangerous = text
            return
        }
        busy = true
        command = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                Shell.runAs(Shell.backend(), text, 20_000)
            }
            output = buildString {
                if (isNotEmpty()) append("\n")
                append("$ ")
                append(text)
                append("\n")
                append(result.text.ifBlank { "(无输出)" })
                append("\n[exit ")
                append(result.code)
                append("]\n")
            } + output
            history = (listOf(text) + history.filter { it != text }).take(20)
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text("ADB 命令行", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text(
                    if (privileged) "当前后端：${Shell.backendLabel()}" else "无线 ADB / Root 未连接，只能执行普通应用命令",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (privileged) Glass.Mint else Glass.Amber,
                )
            }
            Pill(
                if (busy) "执行中" else Shell.backendLabel(),
                selected = privileged,
                color = if (privileged) Glass.Mint else Glass.Amber,
            )
        }

        if (!privileged) {
            GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text(
                    "先连接无线 ADB 或 Root，才能执行 pm、appops、settings、dumpsys 等命令。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Amber,
                )
            }
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(12.dp),
        ) {
            SelectionContainer {
                Text(
                    output.ifBlank { "输入命令后执行。\n\n命令模式不支持交互式程序或长期运行的 logcat。" },
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
                Text(
                    "$",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Glass.Accent,
                    ),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                BasicTextField(
                    value = command,
                    onValueChange = { command = it },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Glass.Text,
                    ),
                    cursorBrush = SolidColor(Glass.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { execute(command) }),
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    if (busy) "执行中" else "执行",
                    accent = true,
                    enabled = !busy && command.isNotBlank(),
                    onClick = { execute(command) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("id", "getprop ro.product.model", "pm list packages -3", "dumpsys battery").forEach { preset ->
                Pill(preset, color = Glass.Ice) {
                    command = preset
                    execute(preset)
                }
            }
        }

        if (history.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("最近", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(
                    history.take(3).joinToString("  "),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = Glass.Faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "清空",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Rose,
                    modifier = Modifier.padding(horizontal = 4.dp).then(
                        Modifier.background(Color.Transparent),
                    ),
                )
            }
        }

        pendingDangerous?.let { cmd ->
            GlassPanel(tint = Glass.Rose, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text(
                    "这条命令可能修改系统或删除数据：",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Rose,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    cmd,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = Glass.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("取消", modifier = Modifier.weight(1f), onClick = { pendingDangerous = null })
                    ActionButton(
                        "仍然执行",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = cmd
                            pendingDangerous = null
                            busy = true
                            command = ""
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    Shell.runAs(Shell.backend(), target, 20_000)
                                }
                                output = buildString {
                                    if (isNotEmpty()) append("\n")
                                    append("$ "); append(target); append("\n")
                                    append(result.text.ifBlank { "(无输出)" })
                                    append("\n[exit "); append(result.code); append("]\n")
                                } + output
                                history = (listOf(target) + history.filter { it != target }).take(20)
                                busy = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun dangerousCommand(cmd: String): Boolean {
    val lower = cmd.lowercase()
    val patterns = listOf(
        "rm ", "rm -", "dd ", "mkfs", "reboot", "shutdown", "pm uninstall", "pm clear",
        "settings put", "appops set", "mount ", "chmod ", "chown ", "flash ",
    )
    return patterns.any { lower.contains(it) }
}
