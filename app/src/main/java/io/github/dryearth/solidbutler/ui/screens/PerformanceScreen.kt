package io.github.dryearth.solidbutler.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.PerfUiState
import io.github.dryearth.solidbutler.core.ChargingState
import io.github.dryearth.solidbutler.core.LineageHealth
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.core.safePct
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.BigNumber
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.KeyValueRow
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.Sparkline
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.components.formatDuration
import io.github.dryearth.solidbutler.ui.components.humanRate
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun PerformanceScreen(vm: ButlerViewModel) {
    val perf by vm.perf.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    ToastEffect(perf.toast) { vm.clearPerfToast() }

    LaunchedEffect(Unit) { vm.refreshDrain() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LivePanel(perf) }
        item { CoresPanel(perf) }
        item { TemperaturePanel(perf) }
        item { ChargingPanel(vm, perf) }
        item { BatteryPanel(perf) }
        item { MemoryPanel(perf, onTrim = { vm.killBackground() }) }
        item { PresetPanel(vm, perf, caps.root) }
        item {
            SectionHeader(
                "24 小时耗电",
                "UsageStats + cpuinfo",
                trailing = {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        tint = Glass.Accent,
                        modifier = Modifier.size(18.dp).clickable { vm.refreshDrain() },
                    )
                },
            )
        }
        if (perf.drain.isEmpty()) {
            item { EmptyHint("授予「使用情况访问」后显示") }
        } else {
            items(perf.drain.take(12)) { d ->
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "前台 ${formatDuration(d.foregroundMs)}" +
                                    if (d.cpuPct > 0f) " · CPU ${d.cpuPct.toInt()}%" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                        Pill(
                            "限制后台",
                            color = Glass.Amber,
                            onClick = { vm.restrictBackground(d.pkg, d.uid, true) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun LivePanel(perf: PerfUiState) {
    GlassPanel(padding = PaddingValues(18.dp)) {
        SectionHeader(
            "实时性能",
            "2 秒刷新 · /proc/stat 与 sysfs",
            trailing = {
                Pill(if (perf.monitoring) "采集中" else "已暂停", selected = perf.monitoring, color = Glass.Mint)
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(
                perf.snapshot.cpu.loadPct.toInt().toString(),
                "%",
                "CPU 总负载",
                color = Glass.Ice,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "↓ ${humanRate(perf.snapshot.rxBps)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
                Text(
                    "↑ ${humanRate(perf.snapshot.txBps)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
                Text(
                    "GPU ${if (perf.snapshot.gpuLoad >= 0) "${perf.snapshot.gpuLoad}%" else "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(perf.cpuHistory, Modifier.fillMaxWidth().height(56.dp), color = Glass.Ice, maxOverride = 100f)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                "RAM ${safePct(perf.snapshot.ramUsed, perf.snapshot.ramTotal).let { (it * 100).toInt() }}%",
                selected = true,
                color = Glass.Accent,
            )
            Pill(
                "可用 ${perf.snapshot.ramAvailable.bytes()}",
                color = Glass.Mint,
            )
            if (perf.snapshot.gpuFreqMhz > 0) {
                Pill("GPU ${perf.snapshot.gpuFreqMhz} MHz", color = Glass.Violet)
            }
        }
    }
}

@Composable
private fun CoresPanel(perf: PerfUiState) {
    val cpu = perf.snapshot.cpu
    if (cpu.cores == 0) return
    GlassPanel {
        SectionHeader("CPU 核心", "governor: ${cpu.governor.ifBlank { "未知" }}")
        Spacer(Modifier.height(12.dp))
        for (i in 0 until cpu.cores) {
            val freq = cpu.freqsKhz.getOrNull(i) ?: -1
            val max = cpu.maxFreqsKhz.getOrNull(i) ?: -1
            val load = cpu.perCore.getOrNull(i) ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CPU$i",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    modifier = Modifier.width(38.dp),
                )
                Meter(
                    load / 100f,
                    color = if (load > 80) Glass.Amber else Glass.Ice,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (freq > 0) "${(freq / 1000)}/${(max / 1000).coerceAtLeast(0)} MHz" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Muted,
                    modifier = Modifier.width(96.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TemperaturePanel(perf: PerfUiState) {
    if (perf.snapshot.temps.isEmpty()) return
    GlassPanel {
        SectionHeader("温度", "thermal_zone")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perf.snapshot.temps.take(4).forEach { t ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                ) {
                    Text(
                        "${t.celsius.toInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            t.celsius >= 60 -> Glass.Rose
                            t.celsius >= 45 -> Glass.Amber
                            else -> Glass.Text
                        },
                    )
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                }
            }
        }
    }
}

@Composable
private fun ChargingPanel(vm: ButlerViewModel, perf: PerfUiState) {
    val charging = perf.charging
    val context = LocalContext.current
    GlassPanel {
        SectionHeader(
            "充电控制",
            if (charging.supported) "LineageOS lineagehealth 官方接口 · 无需 Root" else "本机未提供该能力",
            trailing = {
                if (charging.supported) {
                    Pill(
                        if (charging.enabled) "已开启" else "已关闭",
                        selected = charging.enabled,
                        color = Glass.Mint,
                        onClick = { vm.setChargingEnabled(!charging.enabled) },
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (!charging.supported) {
            Text(
                charging.error ?: "设备内核或 vendor.lineage.health HAL 未实现充电控制。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            return@GlassPanel
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = Glass.Mint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "上限 ${charging.limit}%",
                style = MaterialTheme.typography.titleMedium,
                color = Glass.Text,
            )
            Spacer(Modifier.weight(1f))
            Text(
                perf.snapshot.battery.status,
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
        }
        var limit by remember(charging.limit) { mutableFloatStateOf(charging.limit.toFloat()) }
        Slider(
            value = limit,
            onValueChange = { limit = it },
            onValueChangeFinished = { vm.setChargingLimit(limit.toInt()) },
            valueRange = 70f..100f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = Glass.Accent,
                activeTrackColor = Glass.Accent.copy(alpha = 0.7f),
                inactiveTrackColor = Color(0x14FFFFFF),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                "自动",
                selected = charging.mode == ChargingState.MODE_AUTO,
                onClick = { vm.setChargingMode(ChargingState.MODE_AUTO) },
            )
            Pill(
                "定时",
                selected = charging.mode == ChargingState.MODE_MANUAL,
                onClick = { vm.setChargingMode(ChargingState.MODE_MANUAL) },
            )
            Pill(
                "限值",
                selected = charging.mode == ChargingState.MODE_LIMIT,
                onClick = { vm.setChargingMode(ChargingState.MODE_LIMIT) },
            )
        }
        if (charging.mode == ChargingState.MODE_MANUAL) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeButton("开始 ${secToTime(charging.startTime)}", Modifier.weight(1f)) { sec ->
                    vm.setChargingWindow(sec, charging.targetTime)
                }
                TimeButton("目标 ${secToTime(charging.targetTime)}", Modifier.weight(1f)) { sec ->
                    vm.setChargingWindow(charging.startTime, sec)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "定时模式会计算充电速度，在目标时间前充到上限。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        if (charging.fastSupported && charging.fastModes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("快充模式", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                charging.fastModes.forEach { m ->
                    Pill(
                        LineageHealth.fastModeLabel(m),
                        selected = charging.fastMode == m,
                        color = Glass.Ice,
                        onClick = { vm.setFastCharge(m) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val parts = label.substringAfter(' ').split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    ActionButton(
        label,
        modifier = modifier,
        onClick = {
            TimePickerDialog(context, { _, hh, mm -> onPick(hh * 3600 + mm * 60) }, h, m, true).show()
        },
    )
}

private fun secToTime(sec: Int): String =
    "%02d:%02d".format((sec / 3600) % 24, (sec % 3600) / 60)

@Composable
private fun BatteryPanel(perf: PerfUiState) {
    val b = perf.snapshot.battery
    GlassPanel {
        SectionHeader("电池健康", "${b.technology.ifBlank { "未知" }} · ${b.health}")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(
                b.level.toString(),
                "%",
                "当前电量",
                color = if (b.charging) Glass.Mint else Glass.Text,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${b.tempC}℃", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                Text("${b.voltageMv} mV", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (perf.history.isNotEmpty()) {
            Sparkline(
                perf.history.map { it.second.toFloat() },
                Modifier.fillMaxWidth().height(40.dp),
                color = Glass.Mint,
                maxOverride = 100f,
            )
            Spacer(Modifier.height(6.dp))
        }
        KeyValueRow("循环次数", if (b.cycles >= 0) "${b.cycles} 次" else "系统未提供")
        KeyValueRow("充电计数", if (b.chargeCounterUah > 0) "${b.chargeCounterUah / 1000} mAh" else "—")
        KeyValueRow("瞬时电流", if (b.currentUa != 0L) "${b.currentUa / 1000} mA" else "—")
        KeyValueRow("供电", b.plugged)
    }
}

@Composable
private fun MemoryPanel(perf: PerfUiState, onTrim: () -> Unit) {
    val used = perf.snapshot.ramUsed
    val total = perf.snapshot.ramTotal
    GlassPanel {
        SectionHeader(
            "内存",
            "Android 会主动利用空闲内存，不必追求清空",
            trailing = {
                Icon(Icons.Rounded.Memory, contentDescription = null, tint = Glass.Muted, modifier = Modifier.size(18.dp))
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(used.bytes(), "", "已用", color = Glass.Accent)
            Spacer(Modifier.weight(1f))
            Text("共 ${total.bytes()}", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Meter(safePct(used, total), color = Glass.Accent)
        Spacer(Modifier.height(12.dp))
        Sparkline(perf.ramHistory, Modifier.fillMaxWidth().height(34.dp), color = Glass.Accent, maxOverride = 100f)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                "温和回收后台",
                Icons.Rounded.Refresh,
                modifier = Modifier.weight(1f),
                onClick = onTrim,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "am kill-all 只结束可回收的后台进程，前台、输入法、音乐与系统关键进程不受影响。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}

@Composable
private fun PresetPanel(vm: ButlerViewModel, perf: PerfUiState, root: Boolean) {
    GlassPanel {
        SectionHeader(
            "性能模式",
            if (root) "写入 cpufreq governor" else "需要 Root 才能写入内核参数",
            trailing = {
                Pill(perf.governor.ifBlank { "未读取" }, selected = true, color = Glass.Violet)
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perf.presets.forEach { preset ->
                val selected = preset.governors.any { it == perf.governor }
                GlassPreset(
                    title = preset.title,
                    subtitle = preset.governors.firstOrNull() ?: "不可用",
                    selected = selected,
                    enabled = root && preset.governors.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = { vm.applyPreset(preset) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "省电/均衡/性能只改 governor，不改电压、不锁频。重启后由系统恢复默认。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}

@Composable
private fun GlassPreset(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Glass.Violet.copy(alpha = 0.14f) else Color(0x07FFFFFF))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Glass.Text else Glass.Faint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Glass.Violet else Glass.Faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
