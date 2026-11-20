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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.Capabilities
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.core.safePct
import io.github.dryearth.solidbutler.data.PerfSnapshot
import io.github.dryearth.solidbutler.data.ProcLoad
import io.github.dryearth.solidbutler.data.SecurityReport
import io.github.dryearth.solidbutler.data.StorageReport
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.Ring
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.components.statusBarInset
import io.github.dryearth.solidbutler.ui.theme.Glass
import java.util.Locale

@Composable
fun HomeScreen(
    vm: ButlerViewModel,
    onOpenTab: (String) -> Unit,
    onChat: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenAutomation: () -> Unit,
) {
    val caps by vm.caps.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val security by vm.security.collectAsStateWithLifecycle()

    ToastEffect(perf.toast) { vm.clearPerfToast() }
    val topInset = statusBarInset()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = topInset + 84.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MemoryCard(perf.snapshot, onTrim = { vm.killBackground() }) }
        item { GpuCard(perf.snapshot) }
        item { ProcessCard(perf.snapshot, onManage = onOpenMemory) }
        item { CpuCard(perf.snapshot) }
        item { BatteryTiles(perf.snapshot) }
        item {
            QuickEntries(
                onChat = onChat,
                onAutostart = onOpenAutostart,
                onStorage = { onOpenTab("storage") },
                onSecurity = { onOpenTab("security") },
                onAutomation = onOpenAutomation,
            )
        }
        item { SummaryCard(storage.report, security.report, caps, onOpenTab) }
        item {
            Text(
                "汐 · 本机读取，不上传文件。NPU/进程占用取决于厂商驱动与 Root/无线 ADB。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Faint,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

// ---------------- Memory ----------------

@Composable
private fun MemoryCard(snapshot: PerfSnapshot, onTrim: () -> Unit) {
    val total = snapshot.ramTotal
    val used = snapshot.ramUsed
    val pct = safePct(used, total)
    val swapUsed = (snapshot.swapTotal - snapshot.swapFree).coerceAtLeast(0)
    val swapPct = safePct(swapUsed, snapshot.swapTotal)

    GlassPanel(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(98.dp), contentAlignment = Alignment.Center) {
                Ring(pct, Modifier.fillMaxSize(), color = Glass.Accent, stroke = 9.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(pct * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Glass.Text,
                        )
                        Text("Memory", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                MemoryBar("物理内存", used, total, pct, Glass.Accent)
                Spacer(Modifier.height(10.dp))
                MemoryBar("交换分区", swapUsed, snapshot.swapTotal, swapPct, Glass.Violet)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x0FFFFFFF))
                    .clickable { onTrim() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "回收后台", tint = Glass.Mint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "已用 ${used.bytes()} / ${total.bytes()}",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Muted,
            )
            Text(
                "可用 ${snapshot.ramAvailable.bytes()} · Swap ${swapUsed.bytes()}",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
    }
}

@Composable
private fun MemoryBar(label: String, used: Long, total: Long, pct: Float, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Muted)
            Text(
                if (total > 0) "${(pct * 100).toInt()}%  ${used.bytes()}" else "未启用",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Text,
            )
        }
        Spacer(Modifier.height(5.dp))
        Meter(pct, color = color, height = 7.dp)
    }
}

// ---------------- GPU ----------------

@Composable
private fun GpuCard(snapshot: PerfSnapshot) {
    val gpuPct = snapshot.gpuLoad.coerceIn(0, 100) / 100f
    GlassPanel(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                Ring(gpuPct, Modifier.fillMaxSize(), color = Glass.Violet, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GPU", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                        Text(
                            if (snapshot.gpuLoad >= 0) "${snapshot.gpuLoad}%" else "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.Text,
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (snapshot.gpuFreqMhz > 0) "${snapshot.gpuFreqMhz} MHz" else "频率未知",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Text,
                )
                Text(
                    "负载 ${if (snapshot.gpuLoad >= 0) "${snapshot.gpuLoad}%" else "厂商未开放"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    snapshot.gpuName.ifBlank { "GPU 型号未读取" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (snapshot.glEsVersion.isNotBlank()) append("OpenGL ES ${snapshot.glEsVersion}")
                        if (snapshot.vulkanVersion.isNotBlank()) {
                            if (isNotBlank()) append(" · ")
                            append("Vulkan ${snapshot.vulkanVersion}")
                        }
                    }.ifBlank { "图形接口未知" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }
    }
}

// ---------------- Processes ----------------

@Composable
private fun ProcessCard(snapshot: PerfSnapshot, onManage: () -> Unit) {
    GlassPanel(padding = PaddingValues(14.dp)) {
        SectionHeader(
            "进程与内存",
            if (snapshot.processes.isEmpty()) "需要 Root 或无线 ADB 读取 dumpsys" else "按 CPU/内存排序 · 10 秒刷新",
            trailing = {
                Text(
                    "内存详情",
                    style = MaterialTheme.typography.labelLarge,
                    color = Glass.Accent,
                    modifier = Modifier.clickable { onManage() },
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        if (snapshot.processes.isEmpty()) {
            Text(
                "没有可显示的进程占用。授予 Root 或无线 ADB 后，这里会列出 top CPU 进程。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Faint,
            )
            return@GlassPanel
        }
        snapshot.processes.take(6).forEach { p ->
            ProcessRow(p)
        }
    }
}

@Composable
private fun ProcessRow(proc: ProcLoad) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (proc.name.contains('.')) {
            AppIcon(proc.name, 24.dp)
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x0FFFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text("›", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            proc.name,
            style = MaterialTheme.typography.bodySmall,
            color = Glass.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (proc.pssKb > 0) bytesShort(proc.pssKb * 1024)
            else String.format(Locale.US, "%.1f%%", proc.pct),
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Muted,
        )
        Spacer(Modifier.width(8.dp))
        Meter(
            if (proc.pssKb > 0) (proc.pssKb / 1024f / 512f).coerceIn(0f, 1f)
            else (proc.pct / 40f).coerceIn(0f, 1f),
            color = Glass.Accent,
            height = 4.dp,
            modifier = Modifier.width(44.dp),
        )
    }
}

// ---------------- CPU ----------------

@Composable
private fun CpuCard(snapshot: PerfSnapshot) {
    val cpu = snapshot.cpu
    if (cpu.cores == 0) return
    GlassPanel(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.socModel.ifBlank { "CPU" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Text,
                )
                Text(
                    "${cpu.cores} 核 · 负载 ${cpu.loadPct.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Pill(cpu.governor.ifBlank { "governor 未知" }, color = Glass.Violet)
        }
        Spacer(Modifier.height(12.dp))
        val rows = (0 until cpu.cores).chunked(4)
        rows.forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                indices.forEach { i ->
                    CoreTile(
                        index = i,
                        load = cpu.perCore.getOrNull(i) ?: 0f,
                        freq = cpu.freqsKhz.getOrNull(i) ?: -1,
                        min = cpu.minFreqsKhz.getOrNull(i) ?: -1,
                        max = cpu.maxFreqsKhz.getOrNull(i) ?: -1,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - indices.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CoreTile(
    index: Int,
    load: Float,
    freq: Long,
    min: Long,
    max: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x07FFFFFF))
            .padding(8.dp),
    ) {
        Text(
            "${load.toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = when {
                load > 80 -> Glass.Amber
                load > 40 -> Glass.Text
                else -> Glass.Muted
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (load / 20f > i) Glass.Accent else Color(0x14FFFFFF)
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (freq > 0) "${freq / 1000}MHz" else "—",
            style = MaterialTheme.typography.labelLarge,
            color = Glass.Text,
        )
        Text(
            if (min > 0 && max > 0) "${min / 1000}~${max / 1000}MHz" else "CPU$index",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
            maxLines = 1,
        )
    }
}

// ---------------- Battery tiles ----------------

@Composable
private fun BatteryTiles(snapshot: PerfSnapshot) {
    val b = snapshot.battery
    val maxTemp = snapshot.temps.maxByOrNull { it.celsius }?.celsius ?: b.tempC
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                icon = Icons.Rounded.Bolt,
                value = if (b.powerW > 0f) String.format(Locale.US, "%s%.2f W", if (b.charging) "+" else "-", b.powerW) else "—",
                label = if (b.charging) "充电功率" else "放电功率",
                color = if (b.charging) Glass.Mint else Glass.Amber,
                modifier = Modifier.weight(1f),
            )
            Tile(
                icon = Icons.Rounded.PowerSettingsNew,
                value = "${b.level}%",
                label = "电池 · ${String.format(Locale.US, "%.2f V", b.voltageMv / 1000f)}",
                color = if (b.level <= 15) Glass.Rose else Glass.Text,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                icon = Icons.Rounded.Thermostat,
                value = if (maxTemp > 0f) String.format(Locale.US, "%.1f℃", maxTemp) else "—",
                label = snapshot.temps.maxByOrNull { it.celsius }?.label ?: "温度",
                color = when {
                    maxTemp >= 60 -> Glass.Rose
                    maxTemp >= 45 -> Glass.Amber
                    else -> Glass.Text
                },
                modifier = Modifier.weight(1f),
            )
            Tile(
                icon = Icons.Rounded.Schedule,
                value = uptimeText(),
                label = "已开机",
                color = Glass.Text,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                icon = Icons.Rounded.Memory,
                value = if (b.cycles >= 0) "${b.cycles}" else "—",
                label = if (b.cycles >= 0) "充电循环" else "循环次数未开放",
                color = Glass.Muted,
                modifier = Modifier.weight(1f),
            )
            Tile(
                icon = Icons.Rounded.Storage,
                value = snapshot.storageTotal.let { if (it > 0) bytesShort(snapshot.storageTotal - snapshot.storageUsed) else "—" },
                label = "可用存储",
                color = Glass.Text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, corner = 16.dp, padding = PaddingValues(12.dp)) {
        Icon(icon, contentDescription = null, tint = Glass.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(7.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint, maxLines = 1)
    }
}

// ---------------- Entries ----------------

@Composable
private fun QuickEntries(
    onChat: () -> Unit,
    onAutostart: () -> Unit,
    onStorage: () -> Unit,
    onSecurity: () -> Unit,
    onAutomation: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Entry("管家", Icons.Rounded.AutoAwesome, Glass.Violet, Modifier.weight(1f), onChat)
        Entry("自启", Icons.Rounded.PowerSettingsNew, Glass.Amber, Modifier.weight(1f), onAutostart)
        Entry("自动化", Icons.Rounded.AutoMode, Glass.Accent, Modifier.weight(1f), onAutomation)
        Entry("存储", Icons.Rounded.Storage, Glass.Mint, Modifier.weight(1f), onStorage)
        Entry("安全", Icons.Rounded.Security, Glass.Ice, Modifier.weight(1f), onSecurity)
    }
}

@Composable
private fun Entry(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassPanel(modifier = modifier, corner = 16.dp, padding = PaddingValues(vertical = 12.dp), onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// ---------------- Summary ----------------

@Composable
private fun SummaryCard(
    storage: StorageReport?,
    security: SecurityReport,
    caps: Capabilities,
    onOpenTab: (String) -> Unit,
) {
    val safe = storage?.categories
        ?.filter { it.id in setOf("cache", "log", "thumb", "empty") }
        ?.sumOf { it.bytes } ?: 0L
    val high = security.risks.count { it.level == io.github.dryearth.solidbutler.data.RiskLevel.HIGH }
    GlassPanel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onOpenTab("storage") }) {
                Text("存储", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(
                    if (storage != null) "可清理 ${safe.bytes()}" else "尚未扫描",
                    style = MaterialTheme.typography.titleMedium,
                    color = Glass.Mint,
                )
                Text(
                    if (storage != null) "剩余 ${storage.free.bytes()}" else "点击去扫描",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Muted,
                )
            }
            Box(Modifier.width(1.dp).height(38.dp).background(Color(0x14FFFFFF)))
            Column(Modifier.weight(1f).padding(start = 14.dp).clickable { onOpenTab("security") }) {
                Text("隐私", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(
                    if (high > 0) "$high 个高风险" else "未见高风险",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (high > 0) Glass.Rose else Glass.Text,
                )
                Text(
                    "${security.privacyApps.size} 个敏感权限应用 · ${caps.backend}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Muted,
                )
            }
        }
    }
}

// ---------------- helpers ----------------

private fun uptimeText(): String {
    val sec = android.os.SystemClock.elapsedRealtime() / 1000
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}天 ${h}小时" else "${h}小时 ${m}分"
}

private fun bytesShort(v: Long): String = v.bytes()
