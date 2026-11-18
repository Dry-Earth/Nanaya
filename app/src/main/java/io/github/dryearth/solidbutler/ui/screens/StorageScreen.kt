package io.github.dryearth.solidbutler.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.window.DialogProperties
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
fun StorageScreen(vm: ButlerViewModel, onOpenTrash: () -> Unit) {
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
                GlassPanel(corner = 14.dp, padding = PaddingValues(12.dp), onClick = onOpenTrash) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("回收站", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                            Text(
                                "用户文件删除后可在这里恢复，保留 7 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Muted,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
                    }
                }
            }
            item { SafetyNote() }
            item {
                SectionHeader(
                    "分类",
                    "缓存类可直接清；用户文件必须逐项选择",
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
                val protectedSet = vm.prefs.whitelist()
                items(report.appCaches.take(14)) { cache ->
                    val protected = cache.pkg in protectedSet
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
                                color = if (protected) Glass.Faint else Glass.Mint,
                            )
                            Spacer(Modifier.width(8.dp))
                            if (protected) {
                                Pill("已保护", color = Glass.Faint)
                            } else {
                                Pill("清理", onClick = { vm.cleanAppCaches(listOf(cache.pkg), cache.label) })
                            }
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
            cleaning = state.cleaning,
            onDelete = { items, groups ->
                vm.deleteSelectedFiles(cat, items, groups)
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
private fun SafetyNote() {
    GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
        Text("清理规则", style = MaterialTheme.typography.labelLarge, color = Glass.Amber)
        Spacer(Modifier.height(4.dp))
        Text(
            "缓存 / 空目录 / 缩略图 / 日志：可以直接清。\n" +
                "安装包 / 下载 / 重复文件 / 卸载残留：必须逐项勾选，再点一次确认。\n" +
                "应用数据（账号、设置、模型）：这里没有清理入口。",
            style = MaterialTheme.typography.bodySmall,
            color = Glass.Muted,
        )
    }
}

@Composable
private fun ActionRow(vm: ButlerViewModel, report: StorageReport) {
    val safe = report.categories
        .filter { it.id in setOf("cache", "log", "thumb", "empty") }
        .sumOf { it.bytes }
    val userFiles = report.categories.filter { it.requiresConfirm }.sumOf { it.bytes }
    val protected = vm.prefs.whitelist().size
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("安全可清理", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(safe.bytes(), style = MaterialTheme.typography.headlineMedium, color = Glass.Mint)
                Text("缓存、日志、缩略图、空目录", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("用户文件", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Text(userFiles.bytes(), style = MaterialTheme.typography.headlineMedium, color = Glass.Amber)
                Text("需要你逐项确认", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "保护名单：$protected 个应用。应用数据不会在存储清理里删除。",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
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
    val userFile = cat.requiresConfirm
    GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.title, style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    if (userFile) {
                        Spacer(Modifier.width(6.dp))
                        Text("用户文件", style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
                    }
                }
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
                    color = if (userFile) Glass.Amber else Glass.Text,
                )
                if (cat.count > 0 && cat.bytes > 0) {
                    Text("${cat.count} 项", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                }
            }
            Spacer(Modifier.width(10.dp))
            when {
                !cat.cleanable -> Pill("只读", color = Glass.Faint)
                userFile && cat.count > 0 -> Pill(
                    "查看",
                    selected = true,
                    color = Glass.Amber,
                    onClick = onOpen,
                )
                cat.cleanable && cat.count > 0 -> Pill(
                    if (cleaning) "清理中" else "清理",
                    selected = true,
                    color = Glass.Mint,
                    onClick = onClean,
                )
                else -> Pill("无", color = Glass.Faint)
            }
        }
        if (userFile && cat.count > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                cat.warning ?: "需要逐项选择并二次确认",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Amber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    cat: StorageCategory,
    duplicates: List<DuplicateGroup>,
    cleaning: Boolean,
    onDelete: (List<StorageItem>, List<DuplicateGroup>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPaths by remember(cat.id) { mutableStateOf(setOf<String>()) }
    var selectedGroups by remember(cat.id) { mutableStateOf(setOf<Int>()) }
    var confirm by remember(cat.id) { mutableStateOf(false) }
    val isDup = cat.id == "dup"
    val selectedItems = cat.items.filter { it.path.isNotBlank() && it.path in selectedPaths }
    val selectedDupGroups = duplicates.filterIndexed { i, _ -> i in selectedGroups }
    val selectedCount = if (isDup) selectedDupGroups.sumOf { it.files.size - 1 } else selectedItems.size
    val selectedBytes = if (isDup) {
        selectedDupGroups.sumOf { it.size * (it.files.size - 1) }
    } else selectedItems.sumOf { it.bytes }
    val allSelected = if (isDup) selectedGroups.size == duplicates.size && duplicates.isNotEmpty()
    else cat.items.count { it.path.isNotBlank() } > 0 && selectedPaths.size == cat.items.count { it.path.isNotBlank() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dimAmount = 0.82f)) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            opaque = true,
            corner = 22.dp,
            padding = PaddingValues(18.dp),
        ) {
            Text(cat.title, style = MaterialTheme.typography.titleLarge, color = Glass.Text)
            Spacer(Modifier.height(4.dp))
            Text(cat.subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            if (cat.warning != null) {
                Spacer(Modifier.height(8.dp))
                Text(cat.warning, style = MaterialTheme.typography.bodySmall, color = Glass.Amber)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isDup) "${duplicates.size} 组重复文件" else "${cat.items.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    modifier = Modifier.weight(1f),
                )
                if (cat.cleanable && (isDup || cat.items.any { it.path.isNotBlank() })) {
                    Text(
                        if (allSelected) "清空选择" else "全选",
                        style = MaterialTheme.typography.labelLarge,
                        color = Glass.Accent,
                        modifier = Modifier.clickable {
                            if (allSelected) {
                                selectedPaths = emptySet(); selectedGroups = emptySet()
                            } else {
                                selectedPaths = cat.items.filter { it.path.isNotBlank() }.map { it.path }.toSet()
                                selectedGroups = duplicates.indices.toSet()
                            }
                            confirm = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isDup) {
                    items(duplicates.size) { index ->
                        val g = duplicates[index]
                        SelectRow(
                            title = "${g.size.bytes()} × ${g.files.size} 份",
                            subtitle = g.files.joinToString("\n") { it.substringAfterLast('/') }.take(180),
                            selected = index in selectedGroups,
                        ) {
                            selectedGroups = if (index in selectedGroups) selectedGroups - index else selectedGroups + index
                            confirm = false
                        }
                    }
                } else {
                    items(cat.items) { item ->
                        if (item.path.isBlank()) {
                            ItemRow(item)
                        } else {
                            SelectRow(
                                title = item.label,
                                subtitle = item.path,
                                selected = item.path in selectedPaths,
                            ) {
                                selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path
                                else selectedPaths + item.path
                                confirm = false
                            }
                        }
                    }
                }
            }
            if (!cat.cleanable) {
                Spacer(Modifier.height(8.dp))
                Text("这个分类只用于查看，不提供清理入口。", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
            }
            Spacer(Modifier.height(12.dp))
            if (selectedCount > 0 && cat.cleanable) {
                Text(
                    "已选 $selectedCount 项 · ${selectedBytes.bytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (confirm) {
                Text(
                    "删除后不可恢复。确认删除选中的 $selectedCount 项？",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Rose,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("关闭", modifier = Modifier.weight(1f), onClick = onDismiss)
                if (cat.cleanable && selectedCount > 0) {
                    if (!confirm && cat.requiresConfirm) {
                        ActionButton(
                            "删除选中",
                            accent = true,
                            enabled = !cleaning,
                            modifier = Modifier.weight(1f),
                            onClick = { confirm = true },
                        )
                    } else {
                        ActionButton(
                            if (cat.requiresConfirm) "确认删除" else "删除选中",
                            accent = true,
                            enabled = !cleaning,
                            modifier = Modifier.weight(1f),
                            onClick = { onDelete(selectedItems, selectedDupGroups) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (selected) Glass.Accent.copy(alpha = 0.12f) else Color(0x07FFFFFF))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) Glass.Accent else Color.Transparent)
                .border(1.dp, if (selected) Glass.Accent else Glass.Faint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0B1220))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
