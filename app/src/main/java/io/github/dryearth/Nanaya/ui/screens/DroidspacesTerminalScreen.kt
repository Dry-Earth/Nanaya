package io.github.dryearth.Nanaya.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                output = session.output,
                connected = session.connected,
                onWrite = { text -> session.write(text) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            "点击终端区域即可输入；返回列表后会话仍在，按钮会显示「回到终端」。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}
