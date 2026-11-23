package io.github.dryearth.Nanaya.ui.screens

import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.DroidspacesTerminals
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass

@Composable
fun DroidspacesTerminalScreen(
    containerName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val session = remember(containerName) { DroidspacesTerminals.getOrOpen(context, containerName) }
    val output by session.output.collectAsState()
    val connected by session.connected.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasHardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS

    fun write(text: String) = session.write(text)

    fun submit() {
        write(input.text + "\n")
        input = TextFieldValue("")
    }

    LaunchedEffect(connected, hasHardwareKeyboard) {
        if (connected) {
            kotlinx.coroutines.delay(120)
            runCatching { focus.requestFocus() }
            if (hasHardwareKeyboard) {
                kotlinx.coroutines.delay(80)
                keyboard?.hide()
            }
        }
    }

    LaunchedEffect(connected, focused) {
        while (connected && focused) {
            kotlinx.coroutines.delay(530)
            blink = !blink
        }
        blink = true
    }

    LaunchedEffect(output, input.text) {
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
                    "Droidspaces 终端 · 返回后会话继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Pill(
                if (connected) "已连接" else "已断开",
                selected = connected,
                color = if (connected) Glass.Mint else Glass.Amber,
            )
            Spacer(Modifier.width(8.dp))
            Pill("键盘", color = Glass.Accent) {
                focus.requestFocus()
                keyboard?.show()
            }
            Spacer(Modifier.width(8.dp))
            Pill("结束", color = Glass.Rose) {
                DroidspacesTerminals.close(containerName)
                onBack()
            }
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(10.dp),
        ) {
            Column(
                Modifier.fillMaxSize().clickable { focus.requestFocus() },
            ) {
                val startIndex = input.selection.start.coerceIn(0, input.text.length)
                val cursor = if (connected && focused && blink) "▌" else ""
                val typed = input.text.substring(0, startIndex) + cursor + input.text.substring(startIndex)
                val display = when {
                    output.isBlank() && !connected -> "正在连接容器终端…"
                    output.isBlank() -> "$ $typed"
                    else -> output + typed
                }
                SelectionContainer(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Text(
                        display,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = Glass.Text,
                        ),
                        modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = false,
                    maxLines = 3,
                    enabled = connected,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 1.sp,
                        color = Color.Transparent,
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (event.isCtrlPressed) {
                                when (event.key) {
                                    Key.C -> {
                                        write("\u0003")
                                        input = TextFieldValue("")
                                        true
                                    }
                                    Key.D -> {
                                        write("\u0004")
                                        true
                                    }
                                    Key.L -> {
                                        write("\u000C")
                                        true
                                    }
                                    else -> false
                                }
                            } else if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                submit()
                                true
                            } else {
                                false
                            }
                        },
                )
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
            Pill("清屏", color = Glass.Faint) { write("\u000C") }
        }

        Text(
            if (hasHardwareKeyboard) {
                "检测到物理键盘，软键盘默认不弹出；点「键盘」可手动显示。直接点击终端区域即可输入。"
            } else {
                "直接在终端区域输入即可，没有独立输入框；长命令会自动换行。返回列表后会话仍在，按钮会显示「回到终端」。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}
