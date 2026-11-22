package io.github.dryearth.Nanaya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.theme.Glass

@Composable
fun ToolsScreen(onBack: () -> Unit, onOpenRemote: () -> Unit, onOpenLocal: () -> Unit, onOpenUsb: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("工具", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("设备控制与调试", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        item {
            SectionHeader("ADB", "手机直接控制另一台 Android 设备")
        }
        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpenRemote) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhoneAndroid, null, tint = Glass.Accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("远程 ADB", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "通过无线调试配对并控制另一台设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpenUsb) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Usb, null, tint = Glass.Mint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("USB OTG ADB", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "用 OTG 线直接控制另一台设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpenLocal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = Glass.Ice)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("本机 ADB 终端", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "类终端交互，直接在窗口里输入 shell 命令",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        item {
            GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, null, tint = Glass.Amber)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "远程 ADB 会向目标设备申请调试授权；只连接你信任的设备。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Amber,
                    )
                }
            }
        }
    }
}
