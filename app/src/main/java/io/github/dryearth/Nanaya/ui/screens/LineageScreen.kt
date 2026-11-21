package io.github.dryearth.Nanaya.ui.screens

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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.core.LineageHardware
import io.github.dryearth.Nanaya.core.LineageHardwareFeature
import io.github.dryearth.Nanaya.core.LineageHealth
import io.github.dryearth.Nanaya.data.KernelInfoRepository
import io.github.dryearth.Nanaya.data.KernelItem
import io.github.dryearth.Nanaya.data.PerformanceRepository
import io.github.dryearth.Nanaya.data.PerfSnapshot
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.ui.components.EmptyHint
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.theme.Glass
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FeatureState(val feature: LineageHardwareFeature, val enabled: Boolean)

@Composable
fun LineageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var features by remember { mutableStateOf<List<FeatureState>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<PerfSnapshot?>(null) }
    var charging by remember { mutableStateOf(LineageHealth.read()) }
    var kernel by remember { mutableStateOf<List<KernelItem>>(emptyList()) }

    val services = remember {
        listOf(
            "lineagehealth" to "充电控制 / 快充",
            "lineagehardware" to "显示与触控特性",
            "livedisplay" to "LiveDisplay 色彩",
            "trust" to "Trust 安全提示",
            "profile" to "系统情景模式",
        ).map { it.first to (it.second to hasService(it.first)) }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            LineageHardware.supported().map { f ->
                FeatureState(f, if (f.toggleable) LineageHardware.get(f.bit) else false)
            }
        }
        features = loaded
        snapshot = withContext(Dispatchers.IO) { PerformanceRepository(context).snapshot() }
        charging = withContext(Dispatchers.IO) { LineageHealth.read() }
        kernel = withContext(Dispatchers.IO) { KernelInfoRepository(context).detect() }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = { onBack() })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("系统接口与硬件", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text(
                        "只陈列这台设备实际提供的接口与硬件",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
        }

        item {
            GlassPanel(tint = Glass.Accent) {
                SectionHeader("硬件与系统")
                Spacer(Modifier.height(6.dp))
                val snap = snapshot
                HardwareRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                HardwareRow("系统", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
                if (!snap?.socModel.isNullOrBlank()) HardwareRow("SoC", snap!!.socModel)
                if (!snap?.gpuName.isNullOrBlank()) HardwareRow("GPU", snap!!.gpuName!!)
                if (!snap?.npuName.isNullOrBlank()) HardwareRow("NPU", snap!!.npuName!!)
                if ((snap?.ramTotal ?: 0) > 0) HardwareRow("内存", snap!!.ramTotal.bytes())
                if ((snap?.storageTotal ?: 0) > 0) HardwareRow("存储", snap!!.storageTotal.bytes())
                if (!snap?.glEsVersion.isNullOrBlank()) HardwareRow("OpenGL ES", snap!!.glEsVersion!!)
                if (!snap?.vulkanVersion.isNullOrBlank()) HardwareRow("Vulkan", snap!!.vulkanVersion!!)
                if (charging.supported) {
                    HardwareRow("充电控制", "支持 · 当前上限 ${charging.limit}%")
                    if (charging.fastSupported && charging.fastModes.isNotEmpty()) {
                        HardwareRow("快充", charging.fastModes.joinToString(" / ") { LineageHealth.fastModeLabel(it) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "只列出检测到的项；缺失的接口不会显示空开关。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }

        if (kernel.isNotEmpty()) {
            item {
                GlassPanel(tint = Glass.Mint) {
                    SectionHeader("内核与系统", "只读检测，不修改任何参数")
                    Spacer(Modifier.height(6.dp))
                    kernel.forEach { item -> HardwareRow(item.label, item.value) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "KernelSU/Magisk、SELinux、ZRAM、cgroups、Verified Boot 等状态来自系统节点；需要 Root 的项在无权限时会自动省略。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                    )
                }
            }
        }

        item {
            GlassPanel(tint = Glass.Accent) {
                SectionHeader("已探测到的服务")
                Spacer(Modifier.height(8.dp))
                val connected = services.filter { it.second.second }
                if (connected.isEmpty()) {
                    Text(
                        "没有检测到 LineageOS 专用服务；本页只展示实际存在的接口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                connected.forEach { (name, info) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            info.first,
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Faint,
                            modifier = Modifier.weight(1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Pill("已连接", selected = true, color = Glass.Mint)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "lineagehealth 用于充电上限与快充；lineagehardware 用于下列开关。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }

        item {
            SectionHeader(
                "硬件特性",
                if (loading) "读取中…" else "只显示这台设备真正支持的项",
            )
        }

        if (!loading && features.isEmpty()) {
            item {
                GlassPanel {
                    Text(
                        "没有可用的 lineagehardware 特性。可能是非 LineageOS ROM，或该机型没有注册这些节点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
        }

        items(features.size) { index ->
            val state = features[index]
            val f = state.feature
            GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(f.description, style = MaterialTheme.typography.labelSmall, color = Glass.Muted)
                    }
                    if (f.toggleable) {
                        Pill(
                            if (state.enabled) "已开启" else "已关闭",
                            selected = state.enabled,
                            color = if (state.enabled) Glass.Mint else Glass.Faint,
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        LineageHardware.set(f.bit, !state.enabled)
                                    }
                                    if (ok) {
                                        features = features.map {
                                            if (it.feature.bit == f.bit) it.copy(enabled = !state.enabled) else it
                                        }
                                    } else {
                                        toast = "这台设备拒绝了 ${f.label} 的写入"
                                    }
                                }
                            },
                        )
                    } else {
                        Pill("系统设置", color = Glass.Faint)
                    }
                }
            }
        }

        item {
            GlassPanel {
                Text(
                    "这些开关来自 ILineageHardwareService.get/set(feature, enable)。部分特性（色彩校准、显示模式）只有系统设置能改，这里只做能力展示。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }
        toast?.let { text ->
            item {
                GlassPanel(tint = Glass.Amber) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Amber)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HardwareRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint, modifier = Modifier.width(74.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Glass.Text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun hasService(name: String): Boolean = runCatching {
    val sm = Class.forName("android.os.ServiceManager")
    val getService = sm.getDeclaredMethod("getService", String::class.java)
    getService.invoke(null, name) != null
}.getOrDefault(false)
