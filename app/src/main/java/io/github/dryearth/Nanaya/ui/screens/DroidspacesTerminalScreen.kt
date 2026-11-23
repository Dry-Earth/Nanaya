package io.github.dryearth.Nanaya.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.core.DroidspacesTerminals
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.XtermTerminal
import io.github.dryearth.Nanaya.ui.theme.Glass

/**
 * Full ANSI terminal for a Droidspaces container, rendered by xterm.js.
 * The shell itself is the container's login shell via `droidspaces enter`.
 */
@Composable
fun DroidspacesTerminalScreen(
    containerName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val session = remember(containerName) {
        DroidspacesTerminals.getOrOpen(context, containerName)
    }
    val connected by session.connected.collectAsState()
    var focusSignal by remember { mutableIntStateOf(0) }
    var lastCols by remember { mutableIntStateOf(0) }
    var lastRows by remember { mutableIntStateOf(0) }

    // xterm usually reports its grid before the shell finishes starting, so
    // retry the initial PTY sizing once the session connects.
    LaunchedEffect(connected, lastCols, lastRows) {
        if (connected) session.applySize(lastCols, lastRows)
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
                    "容器终端 · ANSI / 光标 / 全屏 TUI",
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
            Pill("结束", color = Glass.Rose) {
                DroidspacesTerminals.close(containerName)
                onBack()
            }
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(4.dp),
        ) {
            XtermTerminal(
                frame = session.frame,
                connected = session.connected,
                onWrite = { text -> session.write(text) },
                modifier = Modifier.fillMaxSize(),
                focusSignal = focusSignal,
                onSize = { cols, rows ->
                    lastCols = cols
                    lastRows = rows
                    // The guest PTY is 0x0 until we tell it the grid; without
                    // this htop/top/vi draw an empty screen.
                    session.applySize(cols, rows)
                },
            )
        }

        @Composable
        fun key(label: String, color: androidx.compose.ui.graphics.Color, text: String) {
            Pill(label, color = color) { session.write(text) }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            key("Ctrl+C", Glass.Rose, "\u0003")
            key("Ctrl+D", Glass.Rose, "\u0004")
            key("Ctrl+Z", Glass.Rose, "\u001a")
            key("Ctrl+L", Glass.Faint, "\u000c")
            key("Ctrl+R", Glass.Accent, "\u0012")
            key("Ctrl+A", Glass.Muted, "\u0001")
            key("Ctrl+E", Glass.Muted, "\u0005")
            key("Ctrl+U", Glass.Muted, "\u0015")
            key("Ctrl+K", Glass.Muted, "\u000b")
            key("Ctrl+W", Glass.Muted, "\u0017")
            key("Ctrl+P", Glass.Muted, "\u0010")
            key("Ctrl+N", Glass.Muted, "\u000e")
            key("Tab", Glass.Accent, "\t")
            key("Esc", Glass.Muted, "\u001b")
            key("↑", Glass.Muted, "\u001b[A")
            key("↓", Glass.Muted, "\u001b[B")
            key("←", Glass.Muted, "\u001b[D")
            key("→", Glass.Muted, "\u001b[C")
            key("Home", Glass.Muted, "\u001b[H")
            key("End", Glass.Muted, "\u001b[F")
            key("PgUp", Glass.Muted, "\u001b[5~")
            key("PgDn", Glass.Muted, "\u001b[6~")
            key("Enter", Glass.Accent, "\r")
            key("清屏", Glass.Faint, "\u000c")
            Pill("键盘", color = Glass.Mint) { focusSignal++ }
            Pill("适配", color = Glass.Faint) {
                session.forceSize(lastCols, lastRows)
            }
        }

        Text(
            "点击终端区域即可输入；上方是快捷键工具栏。返回列表后会话仍在，按钮会显示「回到终端」。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}
