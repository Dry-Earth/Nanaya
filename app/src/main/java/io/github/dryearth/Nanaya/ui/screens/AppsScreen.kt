package io.github.dryearth.Nanaya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.Nanaya.AppFilter
import io.github.dryearth.Nanaya.ButlerViewModel
import io.github.dryearth.Nanaya.data.AppDetail
import io.github.dryearth.Nanaya.data.AppEntry
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.data.BootComponent
import io.github.dryearth.Nanaya.data.PermissionRow
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.AppIcon
import io.github.dryearth.Nanaya.ui.components.EmptyHint
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.LocalGlassBackdrop
import io.github.dryearth.Nanaya.ui.components.statusBarInset
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.components.ToastEffect
import io.github.dryearth.Nanaya.ui.theme.Glass
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: ButlerViewModel, onOpenAutostart: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val state by vm.apps.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { vm.clearAppsToast() }
    val bootState by vm.autostart.collectAsStateWithLifecycle()
    ToastEffect(bootState.toast) { vm.clearAutostartToast() }
    var mode by remember { mutableStateOf(0) }
    LaunchedEffect(mode) {
        if (mode == 1) vm.loadAutostart()
    }
    val bootFiltered = remember(
        bootState.items,
        bootState.query,
        bootState.onlyUser,
        bootState.onlyEnabled,
    ) {
        bootState.items.filter { item ->
            val q = bootState.query.trim().lowercase(Locale.getDefault())
            val match = q.isBlank() ||
                item.app.label.lowercase(Locale.getDefault()).contains(q) ||
                item.app.pkg.lowercase(Locale.getDefault()).contains(q)
            val user = !bootState.onlyUser || !item.app.system
            val enabled = !bootState.onlyEnabled || item.components.any { it.enabled }
            match && user && enabled
        }
    }

    val filtered = remember(state.apps, state.query, state.filter) {
        state.apps.filter { app ->
            val q = state.query.trim().lowercase(Locale.getDefault())
            val matches = q.isBlank() || app.label.lowercase(Locale.getDefault()).contains(q) ||
                app.pkg.lowercase(Locale.getDefault()).contains(q)
            matches && when (state.filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.system
                AppFilter.SYSTEM -> app.system
                AppFilter.FROZEN -> app.frozen
            }
        }
    }

    val listBackdrop = rememberLayerBackdrop {
        drawRect(Glass.Ink)
        drawContent()
    }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(listBackdrop)) {
    val topInset = statusBarInset()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = topInset + 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AppsModePills(mode) { mode = it }
        }
        if (mode == 0) {
        item {
            GlassPanel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Glass.Faint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text("搜索应用或包名", style = MaterialTheme.typography.bodyMedium, color = Glass.Faint)
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = vm::setAppQuery,
                            textStyle = TextStyle(color = Glass.Text, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                            cursorBrush = SolidColor(Glass.Accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清除",
                            tint = Glass.Faint,
                            modifier = Modifier.size(18.dp).clickable { vm.setAppQuery("") },
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppFilter.entries.forEach { f ->
                    Pill(
                        text = when (f) {
                            AppFilter.ALL -> "全部 ${state.apps.size}"
                            AppFilter.USER -> "用户"
                            AppFilter.SYSTEM -> "系统"
                            AppFilter.FROZEN -> "已冻结"
                        },
                        selected = state.filter == f,
                        onClick = { vm.setAppFilter(f) },
                    )
                }
            }
        }
        item {
            GlassPanel(
                corner = 16.dp,
                padding = PaddingValues(14.dp),
                tint = Glass.Amber,
                onClick = onOpenAutostart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自启与后台管理", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "开机接收器、链式唤醒、待机分组与后台限制",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        item {
            GlassPanel(
                corner = 16.dp,
                padding = PaddingValues(14.dp),
                onClick = onOpenDiagnostics,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("崩溃与 ANR", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            "最近崩溃、启动耗时与最后一次 ANR",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                }
            }
        }
        if (!caps.privileged) {
            item {
                GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Text(
                        "冻结、限制后台、收回权限需要 Root 或无线 ADB。当前为只读浏览。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Amber,
                    )
                }
            }
        }
        if (state.loading) {
            item { EmptyHint("正在读取应用列表…") }
        } else if (filtered.isEmpty()) {
            item { EmptyHint("没有匹配的应用") }
        } else {
            items(filtered, key = { it.pkg }) { app ->
                AppRow(app, state.busy == app.pkg) { vm.openApp(app.pkg) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        } else {
            item {
                GlassPanel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Glass.Faint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (bootState.query.isEmpty()) {
                                Text("搜索自启应用或包名", style = MaterialTheme.typography.bodyMedium, color = Glass.Faint)
                            }
                            BasicTextField(
                                value = bootState.query,
                                onValueChange = vm::setAutostartQuery,
                                textStyle = TextStyle(color = Glass.Text, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                cursorBrush = SolidColor(Glass.Accent),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (bootState.query.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清除",
                                tint = Glass.Faint,
                                modifier = Modifier.size(18.dp).clickable { vm.setAutostartQuery("") },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        "仅用户应用",
                        selected = bootState.onlyUser,
                        color = Glass.Accent,
                        onClick = { vm.setAutostartOnlyUser(!bootState.onlyUser) },
                    )
                    Pill(
                        "仅看已启用",
                        selected = bootState.onlyEnabled,
                        color = Glass.Amber,
                        onClick = { vm.setAutostartOnlyEnabled(!bootState.onlyEnabled) },
                    )
                }
            }
            if (bootState.loading) {
                item { EmptyHint("正在读取自启组件…") }
            } else if (bootFiltered.isEmpty()) {
                item { EmptyHint("没有匹配的自启项") }
            } else {
                items(bootFiltered, key = { it.app.pkg }) { item ->
                    AutostartCard(vm, item, bootState.busy)
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
    }

    if (state.detail != null) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { vm.closeApp() },
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            corner = 30.dp,
            bottomCorner = 0.dp,
            padding = PaddingValues(0.dp),
            strongGlass = true,
            backdropOverride = listBackdrop,
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop) {
                AppDetailSheet(
                    vm = vm,
                    detail = state.detail!!,
                    privileged = caps.privileged,
                    backdrop = listBackdrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    }
}

@Composable
private fun AppsModePills(mode: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("应用", selected = mode == 0, color = Glass.Accent) { onChange(0) }
        Pill("开机自启", selected = mode == 1, color = Glass.Amber) { onChange(1) }
    }
}

@Composable
private fun AppRow(app: AppEntry, busy: Boolean, onClick: () -> Unit) {
    GlassPanel(
        corner = 16.dp,
        padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (app.frozen) Glass.Muted else Glass.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (app.system) {
                        Spacer(Modifier.width(6.dp))
                        Text("系统", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                    }
                }
                Text(
                    buildString {
                        append(app.pkg)
                        if (app.frozen) append(" · 已冻结")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Glass.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
            }
        }
    }
}

@Composable
private fun AppDetailSheet(
    vm: ButlerViewModel,
    detail: AppDetail,
    privileged: Boolean,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val security by vm.security.collectAsStateWithLifecycle()
    val blocked = security.firewallBlocked.contains(detail.app.uid)
    val app = detail.app

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text(
                        "${app.pkg} · v${app.version} · target ${app.targetSdk}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x0FFFFFFF))
                        .clickable { vm.closeApp() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Glass.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    if (app.frozen) "解冻" else "冻结",
                    enabled = privileged,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!app.frozen) confirm = "冻结后应用会从桌面消失，推送与后台停止。可随时解冻。" to {
                            vm.freeze(app.pkg, true)
                        } else vm.freeze(app.pkg, false)
                    },
                )
                ActionButton(
                    if (detail.backgroundRestricted) "恢复后台" else "限制后台",
                    enabled = privileged,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.restrictBackground(app.pkg, app.uid, !detail.backgroundRestricted) },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    if (detail.wakeLockRestricted) "恢复唤醒" else "拦截唤醒锁",
                    enabled = privileged,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.restrictWakeLock(app.pkg, !detail.wakeLockRestricted) },
                )
                ActionButton(
                    if (detail.batteryUnrestricted) "移出电池白名单" else "加入电池白名单",
                    enabled = privileged,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setBatteryUnrestricted(app.pkg, !detail.batteryUnrestricted) },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    if (detail.locked) "关闭应用锁" else "应用锁",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.toggleLock(app.pkg, !detail.locked) },
                )
                ActionButton(
                    if (blocked) "恢复联网" else "切断联网",
                    enabled = privileged,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.toggleFirewall(app.pkg, app.uid, !blocked) },
                )
            }
        }
        item {
            GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (detail.backgroundRestricted) "后台受限" else "后台允许", selected = detail.backgroundRestricted, color = Glass.Mint)
                    Pill(if (blocked) "断网" else "联网", selected = blocked, color = Glass.Rose)
                    if (detail.whitelisted) Pill("受保护", selected = true, color = Glass.Accent)
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    if (detail.whitelisted) "移出清理保护名单" else "加入清理保护名单",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.toggleWhitelist(app.pkg, !detail.whitelisted) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "受保护的应用不会被批量清缓存；模型、离线资源或重要设置建议加入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
        }

        item {
            GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text("24 小时流量", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                if (detail.wifiBytes >= 0 || detail.cellBytes >= 0) {
                    Text(
                        "Wi-Fi ${if (detail.wifiBytes >= 0) detail.wifiBytes.bytes() else "—"} · " +
                            "移动数据 ${if (detail.cellBytes >= 0) detail.cellBytes.bytes() else "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text,
                    )
                } else {
                    Text(
                        "授予「使用情况访问」后显示 NetworkStats 统计",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
        }

        item {
            SectionHeader("权限", "危险权限优先，可直接收回")
            Spacer(Modifier.height(8.dp))
            if (detail.permissions.none { it.dangerous }) {
                Text("没有危险权限", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
            }
        }
        items(detail.permissions.filter { it.dangerous }) { p ->
            PermissionLine(p) { vm.revokePermission(app.pkg, p.name) }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader("自启动组件", "关闭单个 Receiver，避免整包冻结")
            Spacer(Modifier.height(8.dp))
            if (detail.bootComponents.isEmpty()) {
                Text("没有注册开机/网络变化接收器", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
            }
        }
        items(detail.bootComponents) { c ->
            BootLine(c) { enabled -> vm.toggleBoot(app.pkg, c.component, enabled) }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader(
                "追踪 SDK",
                if (detail.trackers.isEmpty()) "尚未扫描" else detail.trackers.joinToString("、"),
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                "离线扫描此应用的追踪器",
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.scanTrackers(app.pkg, onlyUser = false) },
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            ActionButton(
                "为当前用户卸载（可恢复）",
                enabled = privileged,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    confirm = "使用 pm uninstall --user 0，仅对当前用户移除，系统分区不变。确认？" to {
                        vm.uninstallForUser(app.pkg)
                    }
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirm?.let { (text, action) ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable { confirm = null },
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                corner = 22.dp,
                strongGlass = true,
                backdropOverride = backdrop,
                padding = PaddingValues(18.dp),
            ) {
                Text("确认操作", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("取消", modifier = Modifier.weight(1f), onClick = { confirm = null })
                    ActionButton(
                        "确认",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = { confirm = null; action() },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun PermissionLine(p: PermissionRow, onRevoke: () -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        corner = 12.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        strongGlass = true,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                Text(
                    p.name.substringAfterLast('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 1,
                )
            }
            if (p.granted) {
                Pill("已授予", selected = true, color = Glass.Amber, onClick = onRevoke)
            } else {
                Pill("未授予", color = Glass.Faint)
            }
        }
    }
}

@Composable
private fun BootLine(c: BootComponent, onToggle: (Boolean) -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        corner = 12.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        strongGlass = true,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.shortName, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                Text(
                    c.actions.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 1,
                )
            }
            Pill(
                if (c.enabled) "已启用" else "已禁用",
                selected = c.enabled,
                color = if (c.enabled) Glass.Amber else Glass.Mint,
                onClick = { onToggle(!c.enabled) },
            )
        }
    }
}
