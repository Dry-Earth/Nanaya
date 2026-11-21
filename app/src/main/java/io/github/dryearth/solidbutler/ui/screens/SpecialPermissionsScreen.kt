package io.github.dryearth.solidbutler.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.data.AppRepository
import io.github.dryearth.solidbutler.data.SpecialGroup
import io.github.dryearth.solidbutler.data.SpecialPermissionsRepository
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassBackButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.theme.Glass
import io.github.dryearth.solidbutler.proxy.ProxyStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpecialPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember {
        SpecialPermissionsRepository(context, AppRepository(context, Prefs(context)))
    }
    var loading by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<SpecialGroup>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var proxyRunning by remember { mutableStateOf(false) }
    var proxyBusy by remember { mutableStateOf(false) }
    var proxyMessage by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            groups = withContext(Dispatchers.IO) { repo.scan() }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
        proxyRunning = withContext(Dispatchers.IO) { ProxyStarter.running() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = { onBack() })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("特殊权限", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("比运行时权限更危险的一组授权", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        item {
            GlassPanel(tint = Glass.Amber) {
                Text(
                    "悬浮窗、无障碍、通知监听、设备管理器这类权限不会出现在普通权限列表里。能通过 AppOps 收回的，我直接收回；不能的会跳到系统设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }

        item {
            GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku 兼容代理", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        Text(
                            if (proxyRunning) "shell 进程在线 · 兼容层开发中" else "未启动 · 当前阶段只验证进程启动",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (proxyRunning) Glass.Mint else Glass.Muted,
                        )
                    }
                    Pill(
                        if (proxyBusy) "处理中" else if (proxyRunning) "在线" else "未启动",
                        selected = proxyRunning,
                        color = if (proxyRunning) Glass.Mint else Glass.Faint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前只启动 shell 进程，尚未分发 Shizuku Binder。下一步才是授权、Binder 兼容和调用监管。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        "启动代理",
                        accent = true,
                        enabled = !proxyBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            proxyBusy = true
                            proxyMessage = "正在启动 shell 进程…"
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { ProxyStarter.start(context) }
                                proxyRunning = withContext(Dispatchers.IO) { ProxyStarter.running() }
                                proxyBusy = false
                                proxyMessage = error ?: if (proxyRunning) "代理进程已启动" else "命令已执行，但没有检测到进程"
                            }
                        },
                    )
                    ActionButton(
                        "停止",
                        enabled = !proxyBusy && proxyRunning,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            proxyBusy = true
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { ProxyStarter.stop() }
                                proxyRunning = withContext(Dispatchers.IO) { ProxyStarter.running() }
                                proxyBusy = false
                                proxyMessage = error ?: "代理进程已停止"
                            }
                        },
                    )
                }
                proxyMessage?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            message,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (proxyRunning) Glass.Mint else Glass.Amber,
                            maxLines = 14,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ActionButton(
                        "复制日志",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("Nanaya proxy log", message),
                            )
                            toast = "日志已复制"
                        },
                    )
                }
            }
        }

        if (loading) {
            item { EmptyHint("正在扫描…") }
        } else if (groups.isEmpty()) {
            item { EmptyHint("没有发现特殊权限") }
        } else {
            groups.forEach { group ->
                item(key = group.id) {
                    val open = group.id in expanded
                    GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = {
                        expanded = if (open) expanded - group.id else expanded + group.id
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.title, style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                                Text(
                                    group.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Pill("${group.apps.size}", selected = group.apps.isNotEmpty(), color = Glass.Amber)
                            Spacer(Modifier.width(6.dp))
                            Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge, color = Glass.Faint)
                        }
                        if (open) {
                            Spacer(Modifier.height(8.dp))
                            group.apps.take(30).forEach { app ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(app.pkg, 28.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Glass.Text,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (group.revocable) {
                                        Pill("收回", color = Glass.Rose, onClick = {
                                            scope.launch {
                                                val (ok, msg) = withContext(Dispatchers.IO) {
                                                    repo.revoke(group.id, app.pkg)
                                                }
                                                toast = if (ok) "已收回 ${app.label} 的${group.title}" else msg
                                                if (ok) load()
                                            }
                                        })
                                    }
                                }
                            }
                            if (!group.revocable) {
                                Spacer(Modifier.height(6.dp))
                                ActionButton(
                                    "去系统设置",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openSettings(context, group.id) },
                                )
                            }
                        }
                    }
                }
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

private fun openSettings(context: android.content.Context, groupId: String) {
    val intent = when (groupId) {
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "notification" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        "admin" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        "install" -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        "write" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        "vpn" -> Intent(Settings.ACTION_VPN_SETTINGS)
        else -> Intent(Settings.ACTION_SETTINGS)
    }
    runCatching { context.startActivity(intent) }
}
