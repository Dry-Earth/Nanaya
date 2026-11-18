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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.Capabilities
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.core.safePct
import io.github.dryearth.solidbutler.data.PerfSnapshot
import io.github.dryearth.solidbutler.data.SecurityReport
import io.github.dryearth.solidbutler.data.StorageReport
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.BigNumber
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.KeyValueRow
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.Ring
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.Sparkline
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun HomeScreen(vm: ButlerViewModel, onOpenTab: (String) -> Unit, onChat: () -> Unit) {
    val caps by vm.caps.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val security by vm.security.collectAsStateWithLifecycle()

    ToastEffect(perf.toast) { vm.clearPerfToast() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(caps, storage.report, perf.snapshot) }
        item {
            QuickActions(
                onScan = { vm.scanStorage(); onOpenTab("storage") },
                onClean = { vm.cleanTopCaches() },
                onSecurity = { vm.refreshSecurity(); onOpenTab("security") },
                onChat = onChat,
            )
        }
        item { AssistantCard(onChat) }
        item {
            StorageCard(
                report = storage.report,
                scanning = storage.scanning,
                onScan = { vm.scanStorage() },
                onOpen = { onOpenTab("storage") },
            )
        }
        item {
            BatteryCard(
                perf = perf,
                cpuHistory = perf.cpuHistory,
                onOpen = { onOpenTab("perf") },
            )
        }
        item {
            PrivacyCard(
                report = security.report,
                onOpen = { onOpenTab("security") },
            )
        }
        item {
            Text(
                "汐 · 本机分析，不上传文件。AI 对话仅发送你输入的文字与设备摘要。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Faint,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Hero(caps: Capabilities, report: StorageReport?, snapshot: PerfSnapshot) {
    GlassPanel(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "设备状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !caps.allFiles -> "等待授权存储"
                        report == null -> "还没有分析过存储"
                        snapshot.battery.level > 0 -> "运行平稳"
                        else -> "正在读取状态"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(caps.backend, selected = caps.privileged, color = Glass.Mint)
                    Pill(
                        if (caps.usageAccess) "使用情况已授权" else "使用情况未授权",
                        selected = caps.usageAccess,
                        color = Glass.Accent,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (report != null) {
                    val reclaim = reclaimable(report)
                    Text(
                        "可回收 ${reclaim.bytes()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Mint,
                    )
                    Text(
                        "共扫描 ${report.fileCount} 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                } else {
                    Text(
                        "先做一次深度扫描，才能给出可解释的清理建议。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                val usedPct = if (report != null) safePct(report.used, report.total) else 0f
                Ring(usedPct, Modifier.fillMaxSize(), color = Glass.Accent, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (report != null) "${(usedPct * 100).toInt()}%" else "—",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Glass.Text,
                        )
                        Text(
                            "已用",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Faint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(
    onScan: () -> Unit,
    onClean: () -> Unit,
    onSecurity: () -> Unit,
    onChat: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction("扫描", Icons.Rounded.Search, Glass.Accent, Modifier.weight(1f), onScan)
        QuickAction("清理", Icons.Rounded.CleaningServices, Glass.Mint, Modifier.weight(1f), onClean)
        QuickAction("体检", Icons.Rounded.Shield, Glass.Ice, Modifier.weight(1f), onSecurity)
        QuickAction("管家", Icons.Rounded.AutoAwesome, Glass.Violet, Modifier.weight(1f), onChat)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        corner = 16.dp,
        padding = PaddingValues(vertical = 12.dp),
        onClick = onClick,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun AssistantCard(onChat: () -> Unit) {
    GlassPanel(
        tint = Glass.Violet,
        padding = PaddingValues(18.dp),
        onClick = onChat,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Glass.Violet.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Glass.Violet, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("问汐", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Text(
                    "「哪些应用在后台偷跑？」「充电限制到 80」",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
        }
    }
}

@Composable
private fun StorageCard(
    report: StorageReport?,
    scanning: Boolean,
    onScan: () -> Unit,
    onOpen: () -> Unit,
) {
    GlassPanel(onClick = onOpen) {
        SectionHeader(
            "存储",
            if (report == null) "尚未扫描" else "剩余 ${report.free.bytes()} / ${report.total.bytes()}",
            trailing = {
                Text(
                    if (scanning) "扫描中" else "查看",
                    style = MaterialTheme.typography.labelLarge,
                    color = Glass.Accent,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        if (report == null) {
            Text(
                "深度扫描缓存、残留 APK、日志、空文件夹、旧下载与重复文件。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                if (scanning) "扫描中…" else "开始扫描",
                Icons.Rounded.Search,
                accent = true,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
                onClick = onScan,
            )
        } else {
            Meter(safePct(report.used, report.total), color = Glass.Accent)
            Spacer(Modifier.height(14.dp))
            report.categories
                .filter { it.bytes > 0 || it.count > 0 }
                .sortedByDescending { it.bytes }
                .take(3)
                .forEach {
                    KeyValueRow(it.title, if (it.bytes > 0) it.bytes.bytes() else "${it.count} 项")
                }
        }
    }
}

@Composable
private fun BatteryCard(perf: io.github.dryearth.solidbutler.PerfUiState, cpuHistory: List<Float>, onOpen: () -> Unit) {
    GlassPanel(onClick = onOpen) {
        SectionHeader(
            "电池与性能",
            "实时读取系统接口",
            trailing = {
                Text("详情", style = MaterialTheme.typography.labelLarge, color = Glass.Accent)
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(
                perf.snapshot.battery.level.toString(),
                "%",
                "电池",
                color = when {
                    perf.snapshot.battery.level <= 15 -> Glass.Rose
                    perf.snapshot.battery.charging -> Glass.Mint
                    else -> Glass.Text
                },
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (perf.charging.supported) "上限 ${perf.charging.limit}%" else "无充电控制",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (perf.charging.enabled) Glass.Accent else Glass.Muted,
                )
                Text(
                    perf.snapshot.battery.status + " · ${perf.snapshot.battery.tempC}℃",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(cpuHistory, Modifier.fillMaxWidth().height(34.dp), color = Glass.Ice, maxOverride = 100f)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("CPU ${(perf.snapshot.cpu.loadPct).toInt()}%", selected = true, color = Glass.Ice)
            Pill("RAM ${safePct(perf.snapshot.ramUsed, perf.snapshot.ramTotal).let { (it * 100).toInt() }}%", color = Glass.Accent)
            if (perf.snapshot.gpuLoad >= 0) Pill("GPU ${perf.snapshot.gpuLoad}%", color = Glass.Mint)
        }
    }
}

@Composable
private fun PrivacyCard(report: SecurityReport, onOpen: () -> Unit) {
    val high = report.risks.count { it.level == io.github.dryearth.solidbutler.data.RiskLevel.HIGH }
    GlassPanel(onClick = onOpen) {
        SectionHeader(
            "安全与隐私",
            "权限、追踪器与本地风险规则",
            trailing = { Text("查看", style = MaterialTheme.typography.labelLarge, color = Glass.Accent) },
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("敏感应用", report.privacyApps.size.toString(), Glass.Ice, Modifier.weight(1f))
            MiniStat("高风险", high.toString(), if (high > 0) Glass.Rose else Glass.Mint, Modifier.weight(1f))
            MiniStat("追踪命中", report.trackerHits.size.toString(), Glass.Amber, Modifier.weight(1f))
        }
        if (report.activeCaptures.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "正在使用：${report.activeCaptures.joinToString { "${it.kind} · ${it.pkg.substringAfterLast('.')}" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Amber,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
    }
}

private fun reclaimable(report: StorageReport): Long =
    report.categories.filter { it.id in setOf("cache", "apk", "log", "thumb", "old_download", "dup", "empty") }
        .sumOf { it.bytes }
