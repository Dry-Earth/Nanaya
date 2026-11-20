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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.data.PrivacyApp
import io.github.dryearth.solidbutler.data.RiskFinding
import io.github.dryearth.solidbutler.data.RiskLevel
import io.github.dryearth.solidbutler.data.SecurityReport
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.statusBarInset
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.Ring
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun SecurityScreen(vm: ButlerViewModel, onOpenSpecial: () -> Unit, onOpenDomain: () -> Unit) {
    val state by vm.security.collectAsStateWithLifecycle()
    val appsState by vm.apps.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    var privacyDialog by remember { mutableStateOf<PrivacyApp?>(null) }
    ToastEffect(state.toast) { vm.clearSecurityToast() }

    val report = state.report
    val high = report.risks.count { it.level == RiskLevel.HIGH }
    val medium = report.risks.count { it.level == RiskLevel.MEDIUM }
    val score = (100 - high * 12 - medium * 4 - report.trackerHits.size * 2 - report.privacyApps.size / 4)
        .coerceIn(0, 100)

    val topInset = statusBarInset()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = topInset + 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScorePanel(score, report, high) }
        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpenSpecial) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("特殊权限", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "悬浮窗、无障碍、通知监听、设备管理器、安装未知应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }

        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpenDomain) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("域名过滤", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "VpnService 只接管 DNS，拦截广告/追踪域名，不用 Root",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.dryearth.solidbutler.ui.components.ActionButton(
                    "刷新审计",
                    Icons.Rounded.Refresh,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.refreshSecurity() },
                )
                io.github.dryearth.solidbutler.ui.components.ActionButton(
                    "扫描追踪器",
                    Icons.Rounded.Security,
                    accent = true,
                    enabled = !state.scanning,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.scanTrackers() },
                )
            }
        }
        if (state.scanning) {
            item {
                GlassPanel(tint = Glass.Amber) {
                    Text("扫描中", style = MaterialTheme.typography.titleMedium, color = Glass.Amber)
                    Spacer(Modifier.height(4.dp))
                    Text(state.progress, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                    Spacer(Modifier.height(8.dp))
                    Meter(0.5f, color = Glass.Amber)
                }
            }
        }
        if (report.activeCaptures.isNotEmpty()) {
            item {
                GlassPanel(tint = Glass.Rose) {
                    SectionHeader("正在使用敏感权限", "来自系统的实时状态")
                    Spacer(Modifier.height(8.dp))
                    report.activeCaptures.forEach {
                        Text(
                            "${it.kind} · ${it.pkg}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Rose,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                "敏感权限",
                "点开可逐项收回（AppOps 与 Privacy Guard 同源）",
            )
        }
        if (report.privacyApps.isEmpty()) {
            item { EmptyHint("授予使用情况访问后可聚合展示") }
        } else {
            items(report.privacyApps.take(12)) { app ->
                PrivacyRow(app) { privacyDialog = app }
            }
        }

        item {
            SectionHeader(
                "追踪 SDK",
                if (report.trackerHits.isEmpty()) "尚未扫描 · 离线读取 APK 的 DEX 字符串"
                else "${report.trackerHits.size} 个应用命中",
            )
        }
        if (report.trackerHits.isEmpty()) {
            item { EmptyHint("没有扫描结果。建议对用户应用执行一次离线扫描。") }
        } else {
            items(report.trackerHits.take(14)) { hit ->
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(hit.pkg, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hit.label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                            Text(
                                hit.trackers.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Amber,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Pill("${hit.trackers.size} 个", color = Glass.Amber)
                    }
                }
            }
        }

        item {
            SectionHeader("本地风险规则", "侧载、targetSdk、权限组合、无障碍")
        }
        if (report.risks.isEmpty()) {
            item { EmptyHint("没有命中已知的高风险组合") }
        } else {
            items(report.risks.take(14)) { risk -> RiskRow(risk) }
        }

        item {
            SectionHeader(
                "联网控制",
                if (state.rootFirewall) "Root + iptables 独立链 BUTLER_FW" else "需要 Root 与 iptables",
                trailing = {
                    if (state.firewallBlocked.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = "清空",
                            tint = Glass.Rose,
                            modifier = Modifier.size(18.dp).clickable { vm.clearFirewall() },
                        )
                    }
                },
            )
        }
        if (!state.rootFirewall) {
            item {
                GlassPanel {
                    Text(
                        "当前没有可用的内核防火墙。没有 Root 时，可在「应用详情」里用 AppOps 限制后台，或使用系统的联网控制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
        } else {
            val internetApps = appsState.apps.filter {
                it.enabled && "android.permission.INTERNET" in it.permissions
            }
            items(internetApps.take(20), key = { "fw-${it.pkg}" }) { app ->
                val blocked = state.firewallBlocked.contains(app.uid)
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.pkg, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (blocked) "已阻断所有网络" else "允许联网",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (blocked) Glass.Rose else Glass.Faint,
                            )
                        }
                        Pill(
                            if (blocked) "已断网" else "联网中",
                            selected = blocked,
                            color = Glass.Rose,
                            onClick = { vm.toggleFirewall(app.pkg, app.uid, !blocked) },
                        )
                    }
                }
            }
        }

        item {
            GlassPanel {
                Text(
                    "说明：相机/麦克风的实时占用来自 dumpsys；应用历史调用记录属于系统隐私面板的数据，普通应用无法读取，这里不伪造。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    privacyDialog?.let { app ->
        PrivacyDialog(app, onRevoke = { p -> vm.revokePermission(app.pkg, p) }, onDismiss = { privacyDialog = null })
    }
}

@Composable
private fun ScorePanel(score: Int, report: SecurityReport, high: Int) {
    GlassPanel(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Ring(score / 100f, Modifier.fillMaxSize(), color = if (score >= 80) Glass.Mint else if (score >= 60) Glass.Amber else Glass.Rose, stroke = 8.dp) {
                    Text("$score", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        high > 0 -> "有需要处理的高风险项"
                        score >= 80 -> "没有明显风险"
                        else -> "建议做一次权限审计"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "本地规则评分，不是杀毒引擎",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("敏感 ${report.privacyApps.size}", color = Glass.Ice)
                    Pill("追踪 ${report.trackerHits.size}", color = Glass.Amber)
                    Pill("风险 ${report.risks.size}", color = Glass.Rose)
                }
            }
        }
    }
}

@Composable
private fun PrivacyRow(app: PrivacyApp, onClick: () -> Unit) {
    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                Text(
                    app.permissions.joinToString(" · ") { permLabel(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = Glass.Faint)
        }
    }
}

@Composable
private fun RiskRow(risk: RiskFinding) {
    val color = when (risk.level) {
        RiskLevel.HIGH -> Glass.Rose
        RiskLevel.MEDIUM -> Glass.Amber
        RiskLevel.LOW -> Glass.Muted
    }
    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp), tint = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(risk.pkg, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(risk.label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                risk.reasons.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
            Pill(
                when (risk.level) {
                    RiskLevel.HIGH -> "高"
                    RiskLevel.MEDIUM -> "中"
                    RiskLevel.LOW -> "低"
                },
                selected = true,
                color = color,
            )
        }
    }
}

@Composable
private fun PrivacyDialog(app: PrivacyApp, onRevoke: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            opaque = true,
            padding = PaddingValues(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                    Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(app.permissions) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x07FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            permLabel(p),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Glass.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Pill("收回", selected = true, color = Glass.Rose, onClick = { onRevoke(p) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            io.github.dryearth.solidbutler.ui.components.ActionButton(
                "关闭",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

private fun permLabel(name: String): String = when (name) {
    "android.permission.CAMERA" -> "相机"
    "android.permission.RECORD_AUDIO" -> "麦克风"
    "android.permission.ACCESS_FINE_LOCATION" -> "精确位置"
    "android.permission.ACCESS_COARSE_LOCATION" -> "大致位置"
    "android.permission.ACCESS_BACKGROUND_LOCATION" -> "后台位置"
    "android.permission.READ_CONTACTS" -> "联系人"
    "android.permission.READ_SMS", "android.permission.SEND_SMS" -> "短信"
    "android.permission.READ_CALL_LOG" -> "通话记录"
    "android.permission.CALL_PHONE" -> "拨号"
    "android.permission.READ_PHONE_STATE" -> "电话状态"
    "android.permission.BODY_SENSORS" -> "身体传感器"
    "android.permission.READ_CALENDAR" -> "日历"
    else -> name.substringAfterLast('.')
}
