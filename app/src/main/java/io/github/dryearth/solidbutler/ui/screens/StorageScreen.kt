package io.github.dryearth.solidbutler.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.bytes
import io.github.dryearth.solidbutler.core.safePct
import io.github.dryearth.solidbutler.data.DuplicateGroup
import io.github.dryearth.solidbutler.data.Slice
import io.github.dryearth.solidbutler.data.StorageCategory
import io.github.dryearth.solidbutler.data.StorageItem
import io.github.dryearth.solidbutler.data.StorageReport
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Meter
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.Ring
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun StorageScreen(vm: ButlerViewModel) {
    val state by vm.storage.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<StorageCategory?>(null) }
    ToastEffect(state.toast) { vm.clearStorageToast() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StorageHero(state.report, state.scanning, onScan = { vm.scanStorage() })
        }
        if (state.scanning) {
            item { ScanProgress(state.stage, state.progress) }
        }
        state.error?.let { err ->
            item { ErrorPanel(err) }
        }
        val report = state.report
        if (report != null) {
            item { TreemapCard(report) }
            item { ActionRow(vm, report) }
            item {
                SectionHeader(
                    "分类",
                    "点开查看具体文件，再决定删什么",
                )
            }
            items(report.categories) { cat ->
                CategoryRow(
                    cat = cat,
                    cleaning = state.cleaning,
                    onOpen = { selected = cat },
                    onClean = { vm.deleteCategory(cat) },
                )
            }
            if (report.appCaches.isNotEmpty()) {
                item {
                    SectionHeader(
                        "应用缓存",
                        "StorageStats 官方统计，只清 cache 目录",
                        trailing = {
                            Text(
                                "清理前 8 个",
                                style = MaterialTheme.typography.labelLarge,
                                color = Glass.Accent,
                                modifier = Modifier.clickable { vm.cleanTopCaches() },
                            )
                        },
                    )
                }
                items(report.appCaches.take(12)) { cache ->
                    GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cache.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    cache.pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Faint,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                cache.bytes.bytes(),
                                style = MaterialTheme.typography.labelLarge,
                                color = Glass.Mint,
                            )
                            Spacer(Modifier.width(8.dp))
                            Pill("清理", onClick = { vm.cleanAppCaches(listOf(cache.pkg), cache.label) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        } else if (!state.scanning) {
            item {
                GlassPanel {
                    Text(
                        "深度扫描会遍历共享存储，只读取文件名、大小与时间；重复文件在本机做哈希比对。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    ActionButton(
                        "开始深度扫描",
                        Icons.Rounded.Search,
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.scanStorage() },
                    )
                }
            }
        }
    }

    selected?.let { cat ->
        CategoryDialog(
            cat = cat,
            duplicates = state.report?.duplicates.orEmpty(),
            onDelete = {
                vm.deleteCategory(cat)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun StorageHero(report: StorageReport?, scanning: Boolean, onScan: () -> Unit) {
    GlassPanel(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("共享存储", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (report == null) "未扫描" else "已用 ${report.used.bytes()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (report == null) "先扫描一次" else "剩余 ${report.free.bytes()} · ${report.total.bytes()} 总计",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    if (scanning) "扫描中…" else "重新扫描",
                    Icons.Rounded.Search,
                    enabled = !scanning,
                    onClick = onScan,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                val used = if (report != null) safePct(report.used, report.total) else 0f
                Ring(used, Modifier.fillMaxSize(), color = Glass.Accent, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (report != null) "${report.free.bytes()}" else "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.Text,
                        )
                        Text("可用", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanProgress(stage: String, progress: Float) {
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("扫描中", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
            Spacer(Modifier.weight(1f))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Glass.Accent)
        }
        Spacer(Modifier.height(8.dp))
        Meter(progress, color = Glass.Accent)
        Spacer(Modifier.height(6.dp))
        Text(stage, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
    }
}

@Composable
private fun ErrorPanel(text: String) {
    GlassPanel(tint = Glass.Rose) {
        Text("扫描失败", style = MaterialTheme.typography.titleMedium, color = Glass.Rose)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
    }
}

@Composable
private fun TreemapCard(report: StorageReport) {
    GlassPanel {
        SectionHeader(
            "空间分布",
            "按顶层目录与文件类型",
        )
        Spacer(Modifier.height(12.dp))
        Treemap(report.dirSlices, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            report.dirSlices.take(4).forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(treemapColors[i % treemapColors.size])
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${s.label} ${s.bytes.bytes()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "文件类型：" + report.typeSlices.take(4).joinToString(" · ") { "${it.label} ${it.bytes.bytes()}" },
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
        )
    }
}

private val treemapColors = listOf(
    Color(0xFF8FA9E8), Color(0xFF7CC7B4), Color(0xFFD9B777), Color(0xFF9C8FD0),
    Color(0xFF7FB6CE), Color(0xFFC98F8F), Color(0xFF8D9AAE), Color(0xFFB7C58A),
)

@Composable
private fun Treemap(slices: List<Slice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1)
    Canvas(modifier) {
        val rects = ArrayList<Triple<Slice, androidx.compose.ui.geometry.Rect, Color>>()
        sliceRects(slices, 0f, 0f, size.width, size.height, 0, 0, rects)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(220, 255, 255, 255)
            textSize = 10.sp.toPx()
        }
        rects.forEach { (slice, rect, color) ->
            drawRect(color.copy(alpha = 0.35f), topLeft = rect.topLeft, size = rect.size)
            drawRect(color.copy(alpha = 0.55f), topLeft = rect.topLeft, size = Size(rect.width, 1.dp.toPx()))
            if (rect.width > 56.dp.toPx() && rect.height > 30.dp.toPx()) {
                drawContext.canvas.nativeCanvas.drawText(
                    slice.label.take(8),
                    rect.left + 6.dp.toPx(),
                    rect.top + 13.dp.toPx(),
                    paint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    slice.bytes.bytes(),
                    rect.left + 6.dp.toPx(),
                    rect.top + 25.dp.toPx(),
                    paint,
                )
            }
        }
        // gap lines
        rects.forEach { (_, rect, _) ->
            drawRect(Color(0x66000000), topLeft = rect.topLeft, size = Size(rect.width, 1f))
        }
        if (total <= 0) return@Canvas
    }
}

private fun sliceRects(
    slices: List<Slice>,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    start: Int,
    colorOffset: Int,
    out: MutableList<Triple<Slice, androidx.compose.ui.geometry.Rect, Color>>,
) {
    if (start >= slices.size || w <= 0 || h <= 0) return
    if (start == slices.size - 1) {
        out.add(
            Triple(
                slices[start],
                androidx.compose.ui.geometry.Rect(x, y, x + w, y + h),
                treemapColors[colorOffset % treemapColors.size],
            )
        )
        return
    }
    val remaining = slices.subList(start, slices.size).sumOf { it.bytes }.coerceAtLeast(1)
    var acc = 0L
    var split = start
    while (split < slices.size - 1 && acc + slices[split].bytes <= remaining / 2) {
        acc += slices[split].bytes
        split++
    }
    if (split == start) split = start + 1
    val first = slices.subList(start, split)
    val second = slices.subList(split, slices.size)
    val ratio = first.sumOf { it.bytes }.toFloat() / remaining.toFloat()
    if (w >= h) {
        val w1 = w * ratio
        sliceRects(first, x, y, w1, h, 0, colorOffset, out)
        sliceRects(second, x + w1, y, w - w1, h, 0, colorOffset + first.size, out)
    } else {
        val h1 = h * ratio
        sliceRects(first, x, y, w, h1, 0, colorOffset, out)
        sliceRects(second, x, y + h1, w, h - h1, 0, colorOffset + first.size, out)
    }
}

@Composable
private fun ActionRow(vm: ButlerViewModel, report: StorageReport) {
    val reclaim = report.categories
        .filter { it.id in setOf("cache", "apk", "log", "thumb", "old_download", "dup") }
        .sumOf { it.bytes }
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("可回收", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(reclaim.bytes(), style = MaterialTheme.typography.headlineMedium, color = Glass.Mint)
                Text(
                    "缓存、残留包、日志、缩略图、旧下载与重复项",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            ActionButton(
                "清缓存",
                Icons.Rounded.CleaningServices,
                accent = true,
                onClick = { vm.cleanTopCaches() },
            )
        }
        Spacer(Modifier.height(10.dp))
        ActionButton(
            "让系统回收全部应用缓存（pm trim-caches）",
            Icons.Rounded.DeleteSweep,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.trimAllCaches() },
        )
    }
}

@Composable
private fun CategoryRow(
    cat: StorageCategory,
    cleaning: Boolean,
    onOpen: () -> Unit,
    onClean: () -> Unit,
) {
    GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cat.title, style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                Text(
                    cat.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (cat.bytes > 0) cat.bytes.bytes() else "${cat.count} 项",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cat.bytes > 0) Glass.Text else Glass.Muted,
                )
                if (cat.count > 0 && cat.bytes > 0) {
                    Text("${cat.count} 项", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                }
            }
            if (cat.cleanable && cat.count > 0 && cat.id != "dup") {
                Spacer(Modifier.width(10.dp))
                Pill(
                    "清理",
                    selected = true,
                    color = Glass.Mint,
                    onClick = onClean,
                )
            }
        }
        if (cat.id == "dup" && cat.count > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "点开查看 ${cat.count} 组重复文件，保留每组第一份",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Accent,
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    cat: StorageCategory,
    duplicates: List<DuplicateGroup>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            corner = 22.dp,
            padding = PaddingValues(18.dp),
        ) {
            Text(cat.title, style = MaterialTheme.typography.titleLarge, color = Glass.Text)
            Spacer(Modifier.height(4.dp))
            Text(cat.subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            Spacer(Modifier.height(12.dp))
            if (cat.id == "dup") {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(duplicates) { g ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(Color(0x07FFFFFF))
                                .padding(10.dp),
                        ) {
                            Text(
                                "${g.size.bytes()} × ${g.files.size} 份",
                                style = MaterialTheme.typography.labelLarge,
                                color = Glass.Text,
                            )
                            g.files.take(3).forEach {
                                Text(
                                    it.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (g.files.size > 3) {
                                Text("…还有 ${g.files.size - 3} 个", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cat.items) { item ->
                        ItemRow(item)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (confirm) {
                Text(
                    if (cat.id == "dup") "将删除每组中除第一份以外的 ${cat.count} 个文件，不可恢复。"
                    else "将删除 ${cat.count} 项，不可恢复。确认？",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Rose,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("取消", modifier = Modifier.weight(1f), onClick = { confirm = false })
                    ActionButton(
                        "确认删除",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = onDelete,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("关闭", modifier = Modifier.weight(1f), onClick = onDismiss)
                    if (cat.count > 0 && cat.id != "large") {
                        ActionButton(
                            "删除 ${if (cat.bytes > 0) cat.bytes.bytes() else "${cat.count} 项"}",
                            accent = true,
                            modifier = Modifier.weight(1f),
                            onClick = { confirm = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: StorageItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Color(0x07FFFFFF))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.path,
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (item.bytes > 0) item.bytes.bytes() else "0 B",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Muted,
        )
    }
}
