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
import io.github.dryearth.Nanaya.data.DiagnosticsRepository
import io.github.dryearth.Nanaya.data.Diagnostics
import io.github.dryearth.Nanaya.ui.components.AppIcon
import io.github.dryearth.Nanaya.ui.components.EmptyHint
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.components.SectionHeader
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { DiagnosticsRepository(context) }
    var data by remember { mutableStateOf<Diagnostics?>(null) }

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { repo.load() }
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
                    Text("崩溃与 ANR", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("只能读取系统日志，不伪造统计", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        val d = data
        if (d == null) {
            item { EmptyHint("读取日志…") }
        } else if (!d.available) {
            item {
                GlassPanel(tint = Glass.Amber) {
                    Text(d.anr, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        } else {
            item { SectionHeader("最近崩溃", "logcat -b crash") }
            if (d.crashes.isEmpty()) {
                item { EmptyHint("最近没有崩溃记录") }
            } else {
                items(d.crashes) { crash ->
                    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(crash.pkg, 30.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    crash.pkg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(crash.time, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                            }
                            Pill("崩溃", selected = true, color = Glass.Rose)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            crash.excerpt,
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Muted,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            item { SectionHeader("启动耗时", "ActivityTaskManager Displayed") }
            if (d.startups.isEmpty()) {
                item { EmptyHint("最近没有启动记录") }
            } else {
                items(d.startups) { s ->
                    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(s.pkg, 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                s.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${s.ms} ms",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (s.ms > 1200) Glass.Amber else Glass.Mint,
                            )
                        }
                    }
                }
            }

            item { SectionHeader("最后一次 ANR", "dumpsys activity lastanr") }
            item {
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                    if (d.anr.isBlank()) {
                        Text("没有 ANR 记录", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
                    } else {
                        Text(
                            d.anr,
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Muted,
                            maxLines = 20,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
