package io.github.dryearth.solidbutler.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.core.safePct
import io.github.dryearth.solidbutler.data.MemProcess
import io.github.dryearth.solidbutler.data.PerfSnapshot
import io.github.dryearth.solidbutler.data.PerformanceRepository
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.KeyValueRow
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PerformanceRepository(context) }
    var snapshot by remember { mutableStateOf<PerfSnapshot?>(null) }
    var processes by remember { mutableStateOf<List<MemProcess>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshot = withContext(Dispatchers.IO) { repo.snapshot(includeProcesses = false) }
        processes = withContext(Dispatchers.IO) { repo.memoryProcesses() }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0x0FFFFFFF)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("内存详情", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("RAM · Swap · ZRAM · 每应用 PSS", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        val s = snapshot
        if (s == null) {
            item { EmptyHint("读取内存信息…") }
        } else {
            val swapUsed = (s.swapTotal - s.swapFree).coerceAtLeast(0)
            item {
                GlassPanel {
                    SectionHeader("物理内存", "${s.ramUsed.bytes()} / ${s.ramTotal.bytes()}")
                    Spacer(Modifier.height(8.dp))
                    Meter(safePct(s.ramUsed, s.ramTotal), color = Glass.Accent)
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("可用", s.ramAvailable.bytes())
                    KeyValueRow("Cached", s.cachedBytes.bytes())
                    KeyValueRow("Buffers", s.buffersBytes.bytes())
                }
            }
            item {
                GlassPanel {
                    SectionHeader("Swap", if (s.swapTotal > 0) "${swapUsed.bytes()} / ${s.swapTotal.bytes()}" else "未启用")
                    Spacer(Modifier.height(8.dp))
                    if (s.swapTotal > 0) Meter(safePct(swapUsed, s.swapTotal), color = Glass.Violet)
                }
            }
            item {
                GlassPanel {
                    SectionHeader("ZRAM", if (s.zramSize > 0) "压缩内存" else "未检测到")
                    if (s.zramSize > 0) {
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("设备大小", s.zramSize.bytes())
                        KeyValueRow("已压缩", s.zramUsed.bytes())
                        KeyValueRow("原始大小", s.zramOrig.bytes())
                        val ratio = if (s.zramUsed > 0) s.zramOrig.toFloat() / s.zramUsed.toFloat() else 0f
                        KeyValueRow("压缩比", if (ratio > 0) String.format(java.util.Locale.US, "%.2f×", ratio) else "—")
                        if (s.zramUsed > 0) {
                            Spacer(Modifier.height(6.dp))
                            Meter((s.zramUsed.toFloat() / s.zramSize.coerceAtLeast(1)).coerceIn(0f, 1f), color = Glass.Ice)
                        }
                    } else {
                        Text("内核没有暴露 zram0，或需要 Root 读取。", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
                    }
                }
            }
        }

        item {
            SectionHeader(
                "应用内存占用",
                if (processes.isEmpty()) "需要 Root 或无线 ADB 读取进程内存" else "内存占用 · 降序",
            )
        }
        if (loading) {
            item { EmptyHint("读取进程内存…") }
        } else if (processes.isEmpty()) {
            item { EmptyHint("没有可显示的进程内存") }
        } else {
            items(processes, key = { it.name }) { p ->
                val pkg = p.name.substringBefore(':')
                GlassPanel(corner = 12.dp, padding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pkg.contains('.')) AppIcon(pkg, 26.dp) else {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Color(0x0FFFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("›", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Pill((p.pssKb * 1024).bytes(), color = Glass.Accent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
