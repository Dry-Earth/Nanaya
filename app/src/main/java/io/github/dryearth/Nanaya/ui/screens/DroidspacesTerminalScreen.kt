package io.github.dryearth.Nanaya.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.DroidspacesRepository
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private val ANSI_CSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val ANSI_OSC = Regex("\u001B\\][^\u0007]*(\u0007|\u001B\\\\)")

private fun stripTerminalNoise(value: String): String =
    value.replace(ANSI_OSC, "").replace(ANSI_CSI, "").replace("\r", "")

@Composable
fun DroidspacesTerminalScreen(
    containerName: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var process by remember { mutableStateOf<Process?>(null) }
    var output by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }

    fun write(text: String) {
        val p = process ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                p.outputStream.write(text.toByteArray(Charsets.UTF_8))
                p.outputStream.flush()
            }
        }
    }

    fun submit() {
        write(command + "\n")
        command = ""
    }

    LaunchedEffect(containerName) {
        val p = withContext(Dispatchers.IO) { DroidspacesRepository.openTerminal(containerName) }
        if (p == null) {
            output = "无法启动容器终端。\nDroidspaces 终端需要 Root。"
            return@LaunchedEffect
        }
        process = p
        connected = true
        try {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                val input: InputStream = p.inputStream
                while (isActive) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val text = stripTerminalNoise(String(buffer, 0, read, Charsets.UTF_8))
                    if (text.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            output = (output + text).takeLast(120_000)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                output += "\n[终端错误：${e.message ?: "读取失败"}]"
            }
        } finally {
            withContext(Dispatchers.Main) {
                connected = false
                output += "\n[会话结束]"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { process?.destroy() }
        }
    }

    LaunchedEffect(connected) {
        if (connected) {
            kotlinx.coroutines.delay(120)
            runCatching { focus.requestFocus() }
        }
    }

    LaunchedEffect(output) {
        scroll.scrollTo(scroll.maxValue)
    }

    BackHandler { onBack() }

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
                Text(
                    containerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Text,
                )
                Text(
                    "Droidspaces 终端 · 直接在容器内输入",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Pill(
                if (connected) "已连接" else "已断开",
                selected = connected,
                color = if (connected) Glass.Mint else Glass.Amber,
            )
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(10.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable { focus.requestFocus() },
            ) {
                SelectionContainer(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Text(
                        output.ifBlank { "正在连接容器终端…" },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = Glass.Text,
                        ),
                        modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Glass.Accent,
                        ),
                    )
                    Spacer(Modifier.width(5.dp))
                    BasicTextField(
                        value = command,
                        onValueChange = { command = it },
                        singleLine = true,
                        enabled = connected,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Glass.Text,
                        ),
                        cursorBrush = SolidColor(Glass.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    submit()
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("Ctrl+C", color = Glass.Rose) { write("\u0003") }
            Pill("Tab", color = Glass.Accent) { write("\t") }
            Pill("↑", color = Glass.Muted) { write("\u001B[A") }
            Pill("↓", color = Glass.Muted) { write("\u001B[B") }
            Pill("Esc", color = Glass.Muted) { write("\u001B") }
            Pill("清屏", color = Glass.Faint) {
                output = ""
                write("\u000C")
            }
        }

        Text(
            "提示：容器内的登录 shell 可能需要几秒才出现。长按输出可复制。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}
