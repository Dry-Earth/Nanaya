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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.data.AutostartApp
import io.github.dryearth.solidbutler.data.BootComponent
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass
import java.util.Locale

@Composable
fun AutostartScreen(vm: ButlerViewModel, onBack: () -> Unit) {
    val state by vm.autostart.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { vm.clearAutostartToast() }

    LaunchedEffect(Unit) { vm.loadAutostart() }

    val filtered = remember(state.items, state.query, state.onlyUser, state.onlyEnabled) {
        state.items.filter { item ->
            val q = state.query.trim().lowercase(Locale.getDefault())
            val match = q.isBlank() || item.app.label.lowercase(Locale.getDefault()).contains(q) ||
                item.app.pkg.lowercase(Locale.getDefault()).contains(q)
            val user = !state.onlyUser || !item.app.system
            val enabled = !state.onlyEnabled || item.components.any { it.enabled }
            match && user && enabled
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x0FFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("自启与后台", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text(
                    "${state.items.size} 个应用注册了开机/网络接收器 · 关闭单个组件比整包冻结更精准",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                    maxLines = 2,
                )
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill(
                "仅用户应用",
                selected = state.onlyUser,
                color = Glass.Accent,
                onClick = { vm.setAutostartOnlyUser(!state.onlyUser) },
            )
            Pill(
                "仅看已启用",
                selected = state.onlyEnabled,
                color = Glass.Amber,
                onClick = { vm.setAutostartOnlyEnabled(!state.onlyEnabled) },
            )
        }

        GlassPanel(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Glass.Faint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text("搜索应用或包名", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = vm::setAutostartQuery,
                        textStyle = TextStyle(color = Glass.Text, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        cursorBrush = SolidColor(Glass.Accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (state.loading) {
            EmptyHint("正在读取自启组件…")
        } else if (filtered.isEmpty()) {
            EmptyHint("没有匹配的自启项")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.app.pkg }) { item ->
                    AutostartCard(vm, item, state.busy)
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun AutostartCard(vm: ButlerViewModel, item: AutostartApp, busy: String?) {
    var expanded by remember(item.app.pkg) { mutableStateOf(false) }
    val enabledCount = item.components.count { it.enabled }
    val isBusy = busy == item.app.pkg || item.components.any { busy == it.component }

    GlassPanel(corner = 16.dp, padding = PaddingValues(13.dp), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.app.pkg, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Glass.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.components.size} 个组件 · $enabledCount 启用" +
                        if (item.backgroundRestricted) " · 后台受限" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.backgroundRestricted) Glass.Mint else Glass.Faint,
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Glass.Accent)
            } else {
                Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge, color = Glass.Faint)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            item.components.forEach { comp ->
                ComponentRow(comp, busy == comp.component) { enabled ->
                    vm.toggleAutostartComponent(item.app, comp, enabled)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    if (item.backgroundRestricted) "恢复后台" else "限制后台",
                    selected = item.backgroundRestricted,
                    color = Glass.Mint,
                    onClick = {
                        vm.setAutostartBackground(item.app, !item.backgroundRestricted)
                    },
                )
                Pill(
                    "待机受限",
                    color = Glass.Amber,
                    onClick = { vm.setAutostartStandby(item.app, "restricted") },
                )
                Pill(
                    "恢复待机",
                    color = Glass.Faint,
                    onClick = { vm.setAutostartStandby(item.app, "frequent") },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "待机分组通过 am set-standby-bucket 设置，会限制任务、闹钟与后台网络。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
    }
}

@Composable
private fun ComponentRow(comp: BootComponent, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x06FFFFFF))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                comp.shortName,
                style = MaterialTheme.typography.bodySmall,
                color = if (comp.enabled) Glass.Text else Glass.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                comp.actions.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Glass.Accent)
        } else {
            Pill(
                if (comp.enabled) "已启用" else "已禁用",
                selected = comp.enabled,
                color = if (comp.enabled) Glass.Amber else Glass.Mint,
                onClick = { onToggle(!comp.enabled) },
            )
        }
    }
}
