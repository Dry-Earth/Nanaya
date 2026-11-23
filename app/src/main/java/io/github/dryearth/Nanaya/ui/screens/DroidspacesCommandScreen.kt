package io.github.dryearth.Nanaya.ui.screens

import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.DroidspacesRepository
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class CommandEntry(
    val command: String,
    val output: String,
    val code: Int,
    val millis: Long,
)

/**
 * One-off command runner backed by `droidspaces --name=<container> run '<cmd>'`.
 * No PTY: a good fit for queries and short jobs, while the terminal handles
 * interactive full-screen programs.
 */
@Composable
fun DroidspacesCommandScreen(
    containerName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<CommandEntry>>(emptyList()) }

    fun run() {
        val text = command.trim()
        if (text.isEmpty() || busy) return
        busy = true
        val started = System.currentTimeMillis()
        scope.launch {
            val result = DroidspacesRepository.runCommand(containerName, text)
            val elapsed = System.currentTimeMillis() - started
            val output = buildString {
                append(result.out.trimEnd())
                if (result.err.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(result.err.trimEnd())
                }
                if (isBlank()) append("(无输出)")
            }.take(120_000)
            entries = (listOf(CommandEntry(text, output, result.code, elapsed)) + entries)
                .take(30)
            busy = false
            runCatching { listState.animateScrollToItem(0) }
        }
    }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focus.requestFocus() }
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
                    maxLines = 1,
                )
                Text(
                    "一次性命令 · droidspaces run",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Pill(
                if (busy) "运行中" else "就绪",
                selected = !busy,
                color = if (busy) Glass.Amber else Glass.Mint,
            )
        }

        GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
            Text(
                "droidspaces --name=$containerName run '<命令>'",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (command.isEmpty()) {
                        Text(
                            "输入命令，例如 uname -a",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Glass.Faint,
                            ),
                        )
                    }
                    BasicTextField(
                        value = command,
                        onValueChange = { command = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Glass.Text,
                        ),
                        cursorBrush = SolidColor(Glass.Accent),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { run() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                    if (event.isShiftPressed) {
                                        false
                                    } else {
                                        run()
                                        true
                                    }
                                } else {
                                    false
                                }
                            },
                    )
                }
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    if (busy) "运行中" else "运行",
                    accent = true,
                    enabled = !busy && command.isNotBlank(),
                    onClick = { run() },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("清空输出", color = Glass.Faint) { entries = emptyList() }
            Pill("复制最新输出", color = Glass.Mint) {
                val text = entries.firstOrNull()?.output
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "还没有输出", Toast.LENGTH_SHORT).show()
                } else {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    manager?.setPrimaryClip(ClipData.newPlainText("终端输出", text))
                    Toast.makeText(context, "已复制 ${text.length} 字", Toast.LENGTH_SHORT).show()
                }
            }
            Pill("清空输入", color = Glass.Faint) { command = "" }
        }

        if (entries.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (busy) "正在容器里执行…" else "执行结果会显示在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Faint,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    val color = when {
                        entry.code == 0 -> Glass.Text
                        entry.code == 124 -> Glass.Amber
                        else -> Glass.Rose
                    }
                    GlassPanel(corner = 12.dp, padding = PaddingValues(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$ ${entry.command}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Glass.Accent,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "exit ${entry.code} · ${entry.millis} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                entry.output,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = color,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Text(
            "run 不分配 PTY，适合一次性命令，最长等待 120 秒。htop、vi 这类程序请用「终端」。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}
