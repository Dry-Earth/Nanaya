package io.github.dryearth.Nanaya.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.Nanaya.ButlerViewModel
import io.github.dryearth.Nanaya.PerfUiState
import io.github.dryearth.Nanaya.core.ChargingState
import io.github.dryearth.Nanaya.core.LineageHealth
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.service.BatteryGuardService
import io.github.dryearth.Nanaya.core.safePct
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.BigNumber
import io.github.dryearth.Nanaya.ui.components.EmptyHint
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.statusBarInset
import io.github.dryearth.Nanaya.ui.components.KeyValueRow
import io.github.dryearth.Nanaya.ui.components.Meter
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.components.Sparkline
import io.github.dryearth.Nanaya.ui.components.ToastEffect
import io.github.dryearth.Nanaya.ui.components.formatDuration
import io.github.dryearth.Nanaya.ui.components.humanRate
import io.github.dryearth.Nanaya.ui.theme.Glass

@Composable
fun PerformanceScreen(vm: ButlerViewModel, onOpenRules: () -> Unit = {}, onOpenMemory: () -> Unit = {}) {
    val perf by vm.perf.collectAsStateWithLifecycle()
    ToastEffect(perf.toast) { vm.clearPerfToast() }

    LaunchedEffect(Unit) {
        vm.startMonitoring()
        vm.refreshDrain()
    }

    val topInset = statusBarInset()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = topInset + 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LivePanel(perf, vm) }
        item { CoresPanel(perf) }
        item { GpuPanel(perf) }
        item { NpuPanel(perf) }
        item { TemperaturePanel(perf) }
        if (perf.charging.supported) {
            item { ChargingPanel(vm, perf) }
        }
        item { BatteryPanel(perf) }
        item { BatteryRulesCard(perf.charging.supported) }
        item {
            GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp), onClick = onOpenRules) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("条件规则", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "锁屏后限制后台、连接 Wi-Fi 时限制",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        item { MemoryPanel(perf, onTrim = { vm.killBackground() }, onOpenMemory = onOpenMemory) }
        item { ZramCard(perf) }
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
private fun LivePanel(perf: PerfUiState, vm: ButlerViewModel) {
    GlassPanel(padding = PaddingValues(18.dp)) {
        SectionHeader(
            "实时性能",
            "实时刷新 · /proc/stat 与 sysfs",
            trailing = {
                Pill(if (perf.monitoring) "采集中" else "已暂停", selected = perf.monitoring, color = Glass.Mint)
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    if (perf.lastSampleAt > 0L) {
                        append("最后 ")
                        append(
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                                .format(java.util.Date(perf.lastSampleAt))
                        )
                    } else {
                        append("尚未采样")
                    }
                    if (perf.readChannel.isNotBlank()) append(" · ${perf.readChannel}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        if (!perf.sampleError.isNullOrBlank()) {
            Text(
                perf.sampleError!!,
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Rose,
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1000 to "1 秒", 2000 to "2 秒", 3000 to "3 秒", 5000 to "5 秒").forEach { (ms, label) ->
                Pill(
                    label,
                    selected = perf.intervalMs == ms,
                    color = Glass.Accent,
                    onClick = { vm.setPerfInterval(ms) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "当前页面实时刷新；离开性能页或 App 切到后台会自动暂停。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
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
            if (perf.snapshot.refreshRateHz > 0f) {
                Pill("${perf.snapshot.refreshRateHz.toInt()} Hz", color = Glass.Mint)
            }
            if (perf.snapshot.npuLoad >= 0) {
                Pill("NPU ${perf.snapshot.npuLoad}%", color = Glass.Ice)
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
private fun GpuPanel(perf: PerfUiState) {
    val s = perf.snapshot
    if (s.gpuLoad < 0 && s.gpuFreqMhz <= 0 && s.gpuMaxMhz <= 0 && s.gpuName.isBlank()) return
    GlassPanel {
        SectionHeader("GPU", s.gpuName.ifBlank { "图形处理器" })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(
                if (s.gpuLoad >= 0) s.gpuLoad.toString() else "—",
                if (s.gpuLoad >= 0) "%" else "",
                "GPU 负载",
                color = Glass.Violet,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (s.gpuFreqMhz > 0) "${s.gpuFreqMhz} MHz" else "频率未知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Glass.Text,
                )
                if (s.gpuMaxMhz > 0) {
                    Text(
                        "最高 ${s.gpuMaxMhz} MHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                    )
                }
            }
        }
        if (s.gpuLoad >= 0) {
            Spacer(Modifier.height(10.dp))
            Meter(s.gpuLoad / 100f, color = Glass.Violet)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.glEsVersion.isNotBlank()) Pill("GLES ${s.glEsVersion}", color = Glass.Ice)
            if (s.vulkanVersion.isNotBlank()) Pill("Vulkan ${s.vulkanVersion}", color = Glass.Mint)
            if (s.socModel.isNotBlank()) Pill(s.socModel, color = Glass.Faint)
        }
    }
}

@Composable
private fun TemperaturePanel(perf: PerfUiState) {
    if (perf.snapshot.temps.isEmpty()) return
    GlassPanel {
        SectionHeader("温度", "thermal_zone")
        Spacer(Modifier.height(12.dp))
        perf.snapshot.temps.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
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
                        if (t.source.isNotBlank()) {
                            Text(
                                t.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NpuPanel(perf: PerfUiState) {
    val npu = perf.snapshot
    GlassPanel {
        SectionHeader(
            "NPU / 加速器",
            npu.npuName.ifBlank { "未检测到加速器节点" },
            trailing = {
                if (npu.npuFreqMhz > 0) Pill("${npu.npuFreqMhz} MHz", color = Glass.Violet)
            },
        )
        Spacer(Modifier.height(12.dp))
        if (npu.npuLoad >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${npu.npuLoad}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Violet,
                )
                Spacer(Modifier.width(12.dp))
                Meter(npu.npuLoad / 100f, color = Glass.Violet, modifier = Modifier.weight(1f))
            }
        } else {
            Text(
                "Android 没有统一的 NPU 占用接口；当前只读取到型号，占用率由厂商驱动决定是否开放。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("频率", if (npu.npuFreqMhz > 0) "${npu.npuFreqMhz} MHz" else "—")
        Text(
            "相机、语音和本地模型会调用 NPU；这里不写任何参数。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
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
        KeyValueRow(
            if (b.charging) "充电功率" else "放电功率",
            if (b.powerW > 0f) String.format(java.util.Locale.US, "%.1f W", b.powerW) else "—",
            color = if (b.charging) Glass.Mint else Glass.Muted,
        )
        val capacity = if (b.chargeCounterUah > 0 && b.level > 0) {
            b.chargeCounterUah * 100 / b.level / 1000
        } else -1L
        KeyValueRow("满充估算", if (capacity > 0) "$capacity mAh" else "系统未提供")
        KeyValueRow("循环次数", if (b.cycles >= 0) "${b.cycles} 次" else "系统未提供")
        KeyValueRow("充电计数", if (b.chargeCounterUah > 0) "${b.chargeCounterUah / 1000} mAh" else "—")
        KeyValueRow("瞬时电流", if (b.currentUa != 0) "${b.currentUa / 1000} mA" else "—")
        KeyValueRow("供电", b.plugged)
        if (perf.tempHistory.size > 2) {
            Spacer(Modifier.height(8.dp))
            Text("电池温度 · 实时", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            Sparkline(
                perf.tempHistory,
                Modifier.fillMaxWidth().height(30.dp),
                color = Glass.Amber,
            )
        }
        if (perf.currentHistory.size > 2) {
            Spacer(Modifier.height(6.dp))
            Text("电流绝对值 · mA", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            Sparkline(
                perf.currentHistory,
                Modifier.fillMaxWidth().height(30.dp),
                color = Glass.Ice,
            )
        }
        if (perf.history.size > 2) {
            Spacer(Modifier.height(8.dp))
            Text("电池温度趋势", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            Sparkline(
                perf.history.map { it.third.toFloat() },
                Modifier.fillMaxWidth().height(30.dp),
                color = Glass.Amber,
            )
            Text(
                "每 30 分钟采样一次，共 ${perf.history.size} 个样本",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
    }
}

@Composable
private fun BatteryRulesCard(chargingSupported: Boolean) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var guard by remember { mutableStateOf(prefs.batteryGuardEnabled) }
    var low by remember { mutableStateOf(prefs.lowBatteryVoice) }
    var full by remember { mutableStateOf(prefs.fullBatteryVoice) }
    var rule by remember { mutableStateOf(prefs.chargeLimitRuleEnabled) }
    var limit by remember { mutableFloatStateOf(prefs.chargeLimitRule.toFloat()) }

    GlassPanel {
        SectionHeader(
            "电池规则",
            "25% / 95% 语音提醒，可选自动限充",
        )
        Spacer(Modifier.height(10.dp))
        RuleToggle("电池守护服务", guard) {
            guard = it
            prefs.batteryGuardEnabled = it
            if (it) {
                BatteryGuardService.start(context)
                Toast.makeText(context, "守护服务已启动，会显示一条低优先级通知", Toast.LENGTH_SHORT).show()
            } else {
                BatteryGuardService.stop(context)
            }
        }
        RuleToggle("电量 25% 语音提醒", low) {
            low = it
            prefs.lowBatteryVoice = it
        }
        RuleToggle("充电到 95% 语音提醒", full) {
            full = it
            prefs.fullBatteryVoice = it
        }
        if (chargingSupported) {
            RuleToggle("达到上限自动限充", rule) {
                rule = it
                prefs.chargeLimitRuleEnabled = it
            }
        } else {
            Text(
                "系统未提供充电控制接口，已隐藏上限与自动限充选项。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        if (rule && chargingSupported) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上限 ${limit.toInt()}%", style = MaterialTheme.typography.bodySmall, color = Glass.Text)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = limit,
                    onValueChange = { limit = it },
                    onValueChangeFinished = { prefs.chargeLimitRule = limit.toInt() },
                    valueRange = 70f..100f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = Glass.Accent,
                        activeTrackColor = Glass.Accent.copy(alpha = 0.7f),
                        inactiveTrackColor = Color(0x14FFFFFF),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "达到上限时调用 lineagehealth 开启限充；拔掉充电器后自动解除规则状态。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "语音使用系统 TTS；服务需要常驻通知（低优先级），锁屏/后台也能触发。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}

@Composable
private fun RuleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text, modifier = Modifier.weight(1f))
        Pill(
            if (checked) "开" else "关",
            selected = checked,
            color = if (checked) Glass.Mint else Glass.Faint,
            onClick = { onChange(!checked) },
        )
    }
}

@Composable
private fun ZramCard(perf: PerfUiState) {
    val s = perf.snapshot
    if (s.zramSize <= 0 && s.swapTotal <= 0) return
    val swapUsed = (s.swapTotal - s.swapFree).coerceAtLeast(0)
    GlassPanel {
        SectionHeader("压缩内存", "ZRAM / Swap")
        Spacer(Modifier.height(10.dp))
        if (s.zramSize > 0) {
            KeyValueRow("ZRAM 设备", s.zramSize.bytes())
            KeyValueRow("已压缩", s.zramUsed.bytes())
            KeyValueRow("原始数据", s.zramOrig.bytes())
            val ratio = if (s.zramUsed > 0) s.zramOrig.toFloat() / s.zramUsed.toFloat() else 0f
            KeyValueRow("压缩比", if (ratio > 0) String.format(java.util.Locale.US, "%.2f×", ratio) else "—")
        }
        if (s.swapTotal > 0) {
            KeyValueRow("Swap", "${swapUsed.bytes()} / ${s.swapTotal.bytes()}")
        }
    }
}

@Composable
private fun MemoryPanel(perf: PerfUiState, onTrim: () -> Unit, onOpenMemory: () -> Unit) {
    val used = perf.snapshot.ramUsed
    val total = perf.snapshot.ramTotal
    GlassPanel(onClick = onOpenMemory) {
        SectionHeader(
            "内存",
            "点击查看每个应用的 PSS 占用",
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
            ActionButton(
                "应用占用",
                modifier = Modifier.weight(1f),
                onClick = onOpenMemory,
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

