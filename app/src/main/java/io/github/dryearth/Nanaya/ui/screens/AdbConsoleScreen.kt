package io.github.dryearth.Nanaya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

private data class TerminalEntry(val command: String, val result: String, val code: Int)

@Composable
fun AdbConsoleScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    var history by remember { mutableStateOf(listOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDangerous by remember { mutableStateOf<String?>(null) }
    val privileged = Shell.isPrivileged()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val promptInteraction = remember { MutableInteractionSource() }

    fun perform(text: String) {
        busy = true
        command = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                Shell.runAs(Shell.backend(), text, 20_000)
            }
            entries += TerminalEntry(text, result.text, result.code)
            history = (listOf(text) + history.filter { it != text }).take(20)
            busy = false
        }
    }

    fun dispatch(cmd: String) {
        val text = cmd.trim()
        if (text.isBlank() || busy) return
        if (dangerousCommand(text)) {
            pendingDangerous = text
            return
        }
        perform(text)
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
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
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text("ADB 终端", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
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

        // Terminal: transcript plus an inline prompt, no separate input box.
        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = promptInteraction,
                        indication = null,
                    ) { focusRequester.requestFocus() },
            ) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                "Nanaya ADB 终端\n在下方输入命令，回车执行。\n命令模式不支持交互式程序或长期运行的 logcat。",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp,
                                    color = Glass.Muted,
                                ),
                            )
                        }
                    } else {
                        items(count = entries.size) { index ->
                            val entry = entries[entries.lastIndex - index]
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "$ ${entry.command}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = Glass.Accent,
                                        ),
                                    )
                                    Text(
                                        entry.result.ifBlank { "(无输出)" },
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
                    Spacer(Modifier.padding(horizontal = 3.dp))
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
                        keyboardActions = KeyboardActions(onSend = { dispatch(command) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    dispatch(command)
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
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("id", "getprop ro.product.model", "pm list packages -3", "dumpsys battery").forEach { preset ->
                Pill(preset, color = Glass.Ice) {
                    dispatch(preset)
                }
            }
        }

        if (history.isNotEmpty() || entries.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier
                        .clickable {
                            entries.clear()
                            history = emptyList()
                        }
                        .padding(horizontal = 4.dp),
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
                            perform(target)
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
