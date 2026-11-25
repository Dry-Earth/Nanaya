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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.core.safePct
import io.github.dryearth.Nanaya.data.HtopProcess
import io.github.dryearth.Nanaya.data.HtopSnapshot
import io.github.dryearth.Nanaya.data.PerformanceRepository
import io.github.dryearth.Nanaya.ui.components.EmptyHint
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Meter
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class HtopSort { CPU, MEM }

@Composable
fun HtopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PerformanceRepository(context) }
    var snapshot by remember { mutableStateOf<HtopSnapshot?>(null) }
    var sort by remember { mutableStateOf(HtopSort.CPU) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val next = withContext(Dispatchers.IO) { repo.htopSnapshot() }
            snapshot = next
            delay(2_000)
        }
    }

    val data = snapshot
    val processes = remember(data, sort) {
        val list = data?.processes.orEmpty()
        when (sort) {
            HtopSort.CPU -> list.sortedByDescending { it.cpuPct }
            HtopSort.MEM -> list.sortedByDescending { it.rssKb }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("系统监视器", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text(
                        "htop 风格 · 2 秒刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
        }

        if (data == null) {
            item { EmptyHint("读取系统状态…") }
        } else {
            item { HtopSummary(data) }
            item {
                SectionHeader(
                    "进程",
                    "共 ${data.tasks} 个 · ${if (data.privileged) "Root/ADB" else "普通权限"}",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        "按 CPU",
                        selected = sort == HtopSort.CPU,
                        color = Glass.Ice,
                        onClick = { sort = HtopSort.CPU },
                    )
                    Pill(
                        "按内存",
                        selected = sort == HtopSort.MEM,
                        color = Glass.Accent,
                        onClick = { sort = HtopSort.MEM },
                    )
                }
            }
            item { HtopTableHeader() }
            if (processes.isEmpty()) {
                item {
                    EmptyHint(
                        if (data.privileged) "没有读到进程"
                        else "查看全部进程需要 Root 或无线 ADB",
                    )
                }
            } else {
                items(processes, key = { it.pid }) { process ->
                    HtopProcessRow(process)
                }
            }
            item {
                Text(
                    "CPU 来自 dumpsys cpuinfo（5 秒缓存），RSS 来自 ps；普通权限只能看到本应用可见的进程。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HtopSummary(data: HtopSnapshot) {
    val cpuPct = data.cpu.loadPct
    val ramPct = safePct(data.ramUsed, data.ramTotal)
    val swapUsed = (data.swapTotal - data.swapFree).coerceAtLeast(0)
    GlassPanel {
        SectionHeader(
            "系统",
            String.format(
                Locale.US,
                "load %.2f %.2f %.2f · uptime %s",
                data.load1,
                data.load5,
                data.load15,
                formatUptime(data.uptimeSec),
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${cpuPct.toInt()}",
                style = MaterialTheme.typography.headlineLarge,
                color = if (cpuPct > 85) Glass.Rose else Glass.Ice,
            )
            Text(
                "% CPU",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
                modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "任务 ${data.tasks}",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Meter(cpuPct / 100f, color = if (cpuPct > 85) Glass.Rose else Glass.Ice)

        if (data.cpu.perCore.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            data.cpu.perCore.chunked(4).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { indexInRow, load ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CPU${rowIndex * 4 + indexInRow}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                            Spacer(Modifier.height(3.dp))
                            Meter(
                                (load / 100f).coerceIn(0f, 1f),
                                color = if (load > 80) Glass.Amber else Glass.Mint,
                                height = 5.dp,
                            )
                            Text(
                                "${load.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Muted,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("内存", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            Spacer(Modifier.weight(1f))
            Text(
                "${data.ramUsed.bytes()} / ${data.ramTotal.bytes()} · ${(ramPct * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Muted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Meter(ramPct, color = Glass.Accent)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Swap ${swapUsed.bytes()} / ${data.swapTotal.bytes()}")
                if (data.zramSize > 0) {
                    append(" · ZRAM ${data.zramUsed.bytes()} / ${data.zramSize.bytes()}")
                }
                append(" · 可用 ${data.ramAvailable.bytes()}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}

@Composable
private fun HtopTableHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("PID", 54.dp)
        HeaderCell("USER", 66.dp)
        HeaderCell("CPU%", 46.dp, TextAlign.End)
        HeaderCell("MEM%", 46.dp, TextAlign.End)
        HeaderCell("RSS", 62.dp, TextAlign.End)
        Text(
            "COMMAND",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Glass.Faint,
        fontFamily = FontFamily.Monospace,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun HtopProcessRow(process: HtopProcess) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(process.pid.toString(), 54.dp, Glass.Faint)
        Cell(process.user.ifBlank { "-" }, 66.dp, Glass.Muted)
        Cell(
            String.format(Locale.US, "%.1f", process.cpuPct),
            46.dp,
            when {
                process.cpuPct >= 50f -> Glass.Rose
                process.cpuPct >= 20f -> Glass.Amber
                else -> Glass.Text
            },
            TextAlign.End,
        )
        Cell(
            String.format(Locale.US, "%.1f", process.memPct),
            46.dp,
            if (process.memPct >= 10f) Glass.Amber else Glass.Muted,
            TextAlign.End,
        )
        Cell(rssLabel(process.rssKb), 62.dp, Glass.Muted, TextAlign.End)
        Text(
            process.name,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Glass.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Cell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

private fun rssLabel(kb: Long): String = when {
    kb >= 1024L * 1024L -> String.format(Locale.US, "%.1fG", kb / 1024f / 1024f)
    kb >= 1024L -> String.format(Locale.US, "%.0fM", kb / 1024f)
    else -> "${kb}K"
}

private fun formatUptime(totalSec: Long): String {
    val days = totalSec / 86_400
    val hours = (totalSec % 86_400) / 3_600
    val minutes = (totalSec % 3_600) / 60
    return if (days > 0) {
        String.format(Locale.US, "%dd %02d:%02d", days, hours, minutes)
    } else {
        String.format(Locale.US, "%02d:%02d", hours, minutes)
    }
}
