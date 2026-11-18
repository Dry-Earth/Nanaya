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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.data.AppEntry
import io.github.dryearth.solidbutler.service.BatteryGuardService
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.theme.Glass
import java.util.Locale

@Composable
fun RulesScreen(vm: ButlerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val appsState by vm.apps.collectAsStateWithLifecycle()
    var screenRule by remember { mutableStateOf(prefs.screenOffRule) }
    var wifiRule by remember { mutableStateOf(prefs.wifiRule) }
    var screenPkg by remember { mutableStateOf(prefs.screenOffPkg) }
    var wifiPkg by remember { mutableStateOf(prefs.wifiPkg) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (appsState.apps.isEmpty()) vm.refreshApps()
        if (prefs.batteryGuardEnabled) BatteryGuardService.start(context)
    }

    fun labelOf(pkg: String): String =
        appsState.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg.ifBlank { "未选择" }

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
                    Text("条件规则", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("锁屏、Wi-Fi 场景下的后台限制", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        item {
            GlassPanel(tint = Glass.Amber) {
                Text(
                    "规则由电池守护服务执行，需要服务开启；实际限制通过 AppOps 完成，因此需要 Shizuku 或 Root。低电量/充满语音和自动限充在「性能 → 电池规则」里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }

        item {
            GlassPanel {
                SectionHeader("锁屏后限制", "屏幕熄灭时限制所选应用后台，亮屏恢复")
                Spacer(Modifier.height(10.dp))
                RuleRow(
                    label = "启用",
                    checked = screenRule,
                ) {
                    screenRule = it
                    prefs.screenOffRule = it
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        labelOf(screenPkg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ActionButton("选择应用", onClick = { pickFor = "screen" })
                }
            }
        }

        item {
            GlassPanel {
                SectionHeader("连接 Wi-Fi 时限制", "连上 Wi-Fi 后限制所选应用后台，断开恢复")
                Spacer(Modifier.height(10.dp))
                RuleRow(
                    label = "启用",
                    checked = wifiRule,
                ) {
                    wifiRule = it
                    prefs.wifiRule = it
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        labelOf(wifiPkg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ActionButton("选择应用", onClick = { pickFor = "wifi" })
                }
            }
        }

        item {
            GlassPanel {
                Text(
                    "提示：这些规则会与系统省电、推送并行生效。需要及时接收消息的应用不要设为限制对象。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    pickFor?.let { kind ->
        val filtered = appsState.apps.filter {
            !it.system && it.enabled && (
                query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
                )
        }
        Dialog(onDismissRequest = { pickFor = null }) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Text(
                    if (kind == "screen") "选择锁屏限制的应用" else "选择 Wi-Fi 限制的应用",
                    style = MaterialTheme.typography.titleLarge,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(Color(0x07FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (query.isEmpty()) {
                        Text("搜索应用", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Glass.Text),
                        cursorBrush = SolidColor(Glass.Accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.pkg }) { app: AppEntry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (kind == "screen") {
                                        screenPkg = app.pkg
                                        prefs.screenOffPkg = app.pkg
                                    } else {
                                        wifiPkg = app.pkg
                                        prefs.wifiPkg = app.pkg
                                    }
                                    pickFor = null
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 30.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
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
