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
import io.github.dryearth.solidbutler.core.ago
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.data.TrashEntry
import io.github.dryearth.solidbutler.data.TrashRepository
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TrashRepository(context) }
    var entries by remember { mutableStateOf<List<TrashEntry>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        entries = withContext(Dispatchers.IO) {
            repo.purgeOld(7)
            repo.list()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(0x0FFFFFFF)).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("回收站", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text(
                    "用户文件删除后在这里保留 7 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            if (entries.isNotEmpty()) {
                Text(
                    "清空",
                    style = MaterialTheme.typography.labelLarge,
                    color = Glass.Rose,
                    modifier = Modifier.clickable {
                        repo.purgeAll()
                        version++
                    },
                )
            }
        }

        if (entries.isEmpty()) {
            EmptyHint("回收站是空的")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "共 ${entries.size} 项 · ${entries.sumOf { it.bytes }.bytes()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                    )
                }
                items(entries, key = { it.id }) { e ->
                    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    e.original,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Faint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${e.bytes.bytes()} · ${e.deletedAt.ago()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Muted,
                                )
                            }
                            Pill("恢复", selected = true, color = Glass.Mint, onClick = {
                                repo.restore(e)
                                version++
                            })
                            Spacer(Modifier.width(6.dp))
                            Pill("永久删", color = Glass.Rose, onClick = {
                                repo.purge(e)
                                version++
                            })
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
