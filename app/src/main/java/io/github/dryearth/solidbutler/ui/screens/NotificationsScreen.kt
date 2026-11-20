package io.github.dryearth.solidbutler.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.service.NotificationGuardService
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassBackButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ChannelRow(
    val pkg: String,
    val label: String,
    val channelId: String,
    val channelName: String,
    val importance: Int,
    val blocked: Boolean,
    val activeKeys: List<String>,
)

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val enabled = remember { listenerEnabled(context) }
    var rows by remember { mutableStateOf<List<ChannelRow>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(version, enabled) {
        loading = true
        rows = withContext(Dispatchers.IO) {
            val service = NotificationGuardService.instance ?: return@withContext emptyList()
            val active = service.active()
            val pm = context.packageManager
            active.groupBy { it.packageName }.flatMap { (pkg, list) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                val byChannel = list.groupBy { it.notification.channelId ?: "" }
                val channels = service.channelsFor(pkg)
                val ids = (channels.map { it.id } + byChannel.keys).distinct()
                ids.map { id ->
                    val ch = channels.firstOrNull { it.id == id }
                    ChannelRow(
                        pkg = pkg,
                        label = label,
                        channelId = id,
                        channelName = ch?.name?.toString() ?: id.ifBlank { "默认渠道" },
                        importance = ch?.importance ?: 0,
                        blocked = prefs.blockedChannels().contains("$pkg|$id"),
                        activeKeys = byChannel[id].orEmpty().map { it.key },
                    )
                }
            }.sortedWith(compareBy<ChannelRow> { it.label }.thenBy { it.channelName })
        }
        loading = false
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
                    Text("通知渠道", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("按渠道屏蔽营销通知", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        if (!enabled) {
            item {
                GlassPanel(tint = Glass.Amber) {
                    Text(
                        "通知渠道管理需要「通知使用权」。Android 不允许普通应用直接修改别的应用的渠道开关，所以这里用监听服务取消你屏蔽的渠道通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        "去开启通知使用权",
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                    )
                }
            }
        } else {
            item {
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("当前有通知的渠道", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                            Text(
                                "屏蔽只影响之后的通知，不会改应用本身的设置。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Muted,
                            )
                        }
                        Pill("${rows.size}", color = Glass.Accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        "批量屏蔽营销类渠道",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val words = listOf("推广", "活动", "营销", "优惠", "推荐", "福利", "促销", "广告")
                            val targets = rows.filter { r ->
                                words.any { r.channelName.contains(it) }
                            }
                            targets.forEach {
                                prefs.setChannelBlocked(it.pkg, it.channelId, true)
                                it.activeKeys.forEach { key -> NotificationGuardService.instance?.cancelKey(key) }
                            }
                            version++
                        },
                    )
                }
            }

            if (loading) {
                item { EmptyHint("读取通知渠道…") }
            } else if (rows.isEmpty()) {
                item { EmptyHint("当前没有活动通知；让应用发一条通知后再来管理") }
            } else {
                items(rows, key = { "${it.pkg}|${it.channelId}" }) { row ->
                    val importance = when {
                        row.importance >= 4 -> "高"
                        row.importance == 3 -> "默认"
                        row.importance == 2 -> "低"
                        else -> "无"
                    }
                    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(row.pkg, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.channelName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${row.label} · 重要度 $importance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Faint,
                                    maxLines = 1,
                                )
                            }
                            Pill(
                                if (row.blocked) "已屏蔽" else "正常",
                                selected = row.blocked,
                                color = Glass.Rose,
                                onClick = {
                                    val next = !row.blocked
                                    prefs.setChannelBlocked(row.pkg, row.channelId, next)
                                    if (next) row.activeKeys.forEach { key ->
                                        NotificationGuardService.instance?.cancelKey(key)
                                    }
                                    version++
                                },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun listenerEnabled(context: android.content.Context): Boolean = runCatching {
    Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    )?.contains(context.packageName) == true
}.getOrDefault(false)
