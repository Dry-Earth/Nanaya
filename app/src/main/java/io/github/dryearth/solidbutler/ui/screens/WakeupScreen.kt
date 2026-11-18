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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.data.AppRepository
import io.github.dryearth.solidbutler.data.WakeupRepository
import io.github.dryearth.solidbutler.data.WakeupSource
import io.github.dryearth.solidbutler.ui.components.AppIcon
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WakeupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { WakeupRepository(context, AppRepository(context, Prefs(context))) }
    var items by remember { mutableStateOf<List<WakeupSource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { repo.scan() }
        loading = false
    }

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
                    Text("唤醒与任务", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("dumpsys jobscheduler / alarm 的粗粒度统计", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }
        item {
            GlassPanel(tint = Glass.Amber) {
                Text(
                    "这里不是完整唤醒链，而是把 JobScheduler 与 AlarmManager 中出现的包名分别计数。数字越大，越可能在后台被系统唤醒。要彻底限制，请到自启页设置待机分组或后台限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }
        if (loading) {
            item { EmptyHint("读取 jobscheduler / alarm…") }
        } else if (items.isEmpty()) {
            item { EmptyHint("需要 Shizuku 或 Root 才能读取系统调度信息") }
        } else {
            items(items, key = { it.pkg }) { s ->
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(s.pkg, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(s.pkg, style = MaterialTheme.typography.labelSmall, color = Glass.Faint, maxLines = 1)
                        }
                        Pill("Job ${s.jobs}", color = Glass.Accent)
                        Spacer(Modifier.width(5.dp))
                        Pill("Alarm ${s.alarms}", color = Glass.Amber)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
