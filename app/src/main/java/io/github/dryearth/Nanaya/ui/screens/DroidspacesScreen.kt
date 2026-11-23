package io.github.dryearth.Nanaya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.DroidspacesRepository
import io.github.dryearth.Nanaya.core.DsApp
import io.github.dryearth.Nanaya.core.DsContainer
import io.github.dryearth.Nanaya.core.DsProcess
import io.github.dryearth.Nanaya.core.DsService
import io.github.dryearth.Nanaya.core.DsSnapshot
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.core.mergeLive
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingConfirm(
    val title: String,
    val detail: String,
    val action: () -> Unit,
)

@Composable
fun DroidspacesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DsSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("process") }
    var initType by remember { mutableStateOf("unknown") }
    var processes by remember { mutableStateOf<List<DsProcess>>(emptyList()) }
    var apps by remember { mutableStateOf<List<DsApp>>(emptyList()) }
    var services by remember { mutableStateOf<List<DsService>>(emptyList()) }
    var panelLoading by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    val disks = remember { mutableStateMapOf<String, String>() }

    fun reload() {
        scope.launch {
            loading = true
            snapshot = withContext(Dispatchers.IO) { DroidspacesRepository.snapshot(context) }
            loading = false
        }
    }

    fun loadTab(container: DsContainer, kind: String) {
        expanded = container.name
        tab = kind
        if (!container.running) return
        panelLoading = true
        scope.launch {
            when (kind) {
                "app" -> apps = withContext(Dispatchers.IO) { DroidspacesRepository.apps(container.name) }
                "service" -> {
                    val (init, list) = withContext(Dispatchers.IO) {
                        DroidspacesRepository.services(container.name)
                    }
                    initType = init
                    services = list
                }
                else -> processes = withContext(Dispatchers.IO) { DroidspacesRepository.processes(container.name) }
            }
            panelLoading = false
        }
        if (container.rootfs.isNotBlank() && disks[container.name] == null) {
            scope.launch {
                disks[container.name] = withContext(Dispatchers.IO) {
                    DroidspacesRepository.diskUsage(container.rootfs)
                }
            }
        }
    }

    fun toggle(container: DsContainer) {
        if (expanded == container.name) {
            expanded = null
            return
        }
        expanded = container.name
        processes = emptyList()
        apps = emptyList()
        services = emptyList()
        initType = "unknown"
        if (container.running) loadTab(container, "process")
        else if (container.rootfs.isNotBlank() && disks[container.name] == null) {
            scope.launch {
                disks[container.name] = withContext(Dispatchers.IO) {
                    DroidspacesRepository.diskUsage(container.rootfs)
                }
            }
        }
    }

    fun operation(label: String, block: suspend () -> String?) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { it.message ?: "操作失败" }
            }
            busy = false
            message = error ?: "$label 完成"
            snapshot = withContext(Dispatchers.IO) { DroidspacesRepository.snapshot(context) }
            val target = snapshot?.containers?.firstOrNull { it.name == expanded }
            if (target != null && target.running) loadTab(target, tab)
        }
    }

    fun askConfirm(title: String, detail: String, action: () -> Unit) {
        confirm = PendingConfirm(title, detail, action)
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            val current = snapshot ?: continue
            if (!current.binaryFound) continue
            val (live, hostRam) = withContext(Dispatchers.IO) { DroidspacesRepository.liveStatus() }
            snapshot = current.copy(
                hostRamKb = if (hostRam > 0) hostRam else current.hostRamKb,
                containers = current.containers.map {
                    it.mergeLive(live[it.name] ?: live[it.folder])
                },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Droidspaces", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("容器进程、应用与系统服务", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Pill(
                when {
                    busy -> "处理中"
                    !Shell.isPrivileged() -> "需要 Root"
                    snapshot?.binaryFound == true -> "已就绪"
                    else -> "未检测"
                },
                selected = snapshot?.binaryFound == true && Shell.isPrivileged(),
                color = if (snapshot?.binaryFound == true && Shell.isPrivileged()) Glass.Mint else Glass.Amber,
            )
        }

        if (!Shell.isPrivileged()) {
            GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text(
                    "Droidspaces 需要 Root。当前没有可用的 Root 或无线 ADB。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Amber,
                )
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
        }

        if (snapshot?.binaryFound == false) {
            GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text(
                    "没有找到 ${DroidspacesRepository.BINARY}。先安装并初始化 Droidspaces，再回来刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Amber,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        "打开 Droidspaces",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = context.packageManager
                                .getLaunchIntentForPackage("com.droidspaces.app")
                            if (intent != null) context.startActivity(intent)
                        },
                    )
                    ActionButton(
                        "重新扫描",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = { reload() },
                    )
                }
            }
        }

        confirm?.let { pending ->
            GlassPanel(tint = Glass.Rose, corner = 14.dp, padding = PaddingValues(10.dp)) {
                Text(pending.title, style = MaterialTheme.typography.bodySmall, color = Glass.Rose)
                if (pending.detail.isNotBlank()) {
                    Text(
                        pending.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        "取消",
                        modifier = Modifier.weight(1f),
                        onClick = { confirm = null },
                    )
                    ActionButton(
                        "确认",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val action = pending.action
                            confirm = null
                            action()
                        },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val containers = snapshot?.containers.orEmpty()
            items(containers, key = { it.folder }) { container ->
                ContainerCard(
                    container = container,
                    hostRamKb = snapshot?.hostRamKb ?: 0L,
                    expanded = expanded == container.name,
                    tab = tab,
                    disk = disks[container.name].orEmpty(),
                    loading = panelLoading,
                    processes = processes,
                    apps = apps,
                    services = services,
                    initType = initType,
                    busy = busy,
                    onToggle = { toggle(container) },
                    onTab = { loadTab(container, it) },
                    onStart = {
                        operation("启动") { DroidspacesRepository.start(container.name) }
                    },
                    onStop = {
                        askConfirm("停止容器", container.name) {
                            operation("停止") { DroidspacesRepository.stop(container.name) }
                        }
                    },
                    onRestart = {
                        askConfirm("重启容器", container.name) {
                            operation("重启") { DroidspacesRepository.restart(container.name) }
                        }
                    },
                    onRefresh = { reload() },
                    onKillProcess = { process ->
                        askConfirm(
                            "结束进程",
                            "${process.name} (PID ${process.pid})",
                        ) {
                            operation("结束进程") {
                                DroidspacesRepository.killProcess(container.name, process.pid)
                            }
                        }
                    },
                    onStartApp = { app ->
                        operation("启动应用") {
                            DroidspacesRepository.startApp(container.name, app, container.x11, container.anland)
                        }
                    },
                    onStopApp = { app ->
                        askConfirm("关闭应用", app.name) {
                            operation("关闭应用") {
                                DroidspacesRepository.stopApp(container.name, app)
                            }
                        }
                    },
                    onServiceAction = { service, action ->
                        operation("服务 $action") {
                            DroidspacesRepository.serviceAction(container.name, initType, service.name, action)
                        }
                    },
                )
            }
            if (containers.isEmpty() && !loading && snapshot?.binaryFound == true) {
                item {
                    Text(
                        "没有扫描到已安装容器。先在 Droidspaces 里创建一个容器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
            }
            if (loading && containers.isEmpty()) {
                item {
                    Text("正在扫描容器…", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                "刷新容器",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = { reload() },
            )
            ActionButton(
                "刷新当前",
                enabled = !busy && expanded != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    val container = snapshot?.containers?.firstOrNull { it.name == expanded }
                    if (container != null) loadTab(container, tab) else reload()
                },
            )
        }
    }
}

@Composable
private fun ContainerCard(
    container: DsContainer,
    hostRamKb: Long,
    expanded: Boolean,
    tab: String,
    disk: String,
    loading: Boolean,
    processes: List<DsProcess>,
    apps: List<DsApp>,
    services: List<DsService>,
    initType: String,
    busy: Boolean,
    onToggle: () -> Unit,
    onTab: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onRefresh: () -> Unit,
    onKillProcess: (DsProcess) -> Unit,
    onStartApp: (DsApp) -> Unit,
    onStopApp: (DsApp) -> Unit,
    onServiceAction: (DsService, String) -> Unit,
) {
    GlassPanel(corner = 16.dp, padding = PaddingValues(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = null,
                tint = if (container.running) Glass.Mint else Glass.Faint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    container.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Glass.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    container.os.ifBlank { container.hostname },
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(
                    if (container.running) "运行中" else "已停止",
                    selected = container.running,
                    color = if (container.running) Glass.Mint else Glass.Faint,
                )
                if (container.running) {
                    Text(
                        "${cpuText(container)} · ${ramText(container, hostRamKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = Glass.Faint,
                modifier = Modifier.size(22.dp),
            )
        }

        if (!expanded) return@GlassPanel

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton(
                "启动",
                enabled = !busy && !container.running,
                modifier = Modifier.weight(1f),
                onClick = onStart,
            )
            ActionButton(
                "停止",
                enabled = !busy && container.running,
                modifier = Modifier.weight(1f),
                onClick = onStop,
            )
            ActionButton(
                "重启",
                enabled = !busy && container.running,
                modifier = Modifier.weight(1f),
                onClick = onRestart,
            )
        }

        Spacer(Modifier.height(8.dp))
        InfoRow("主机名", container.hostname)
        if (container.ip.isNotBlank()) InfoRow("IP", container.ip)
        if (container.uptime.isNotBlank()) InfoRow("运行时长", container.uptime)
        InfoRow("CPU", if (container.running) cpuText(container) else "未运行")
        InfoRow("内存", if (container.running) ramText(container, hostRamKb) else "未运行")
        if (disk.isNotBlank()) InfoRow("磁盘", disk)
        InfoRow("网络", container.netMode)
        val flags = buildList {
            if (container.hwAccess) add("硬件")
            if (container.gpu) add("GPU")
            if (container.x11) add("X11")
            if (container.anland) add("Anland")
        }
        InfoRow("直通", flags.joinToString(" / ").ifBlank { "未开启" })
        if (container.memoryLimit > 0) InfoRow("内存上限", formatBytes(container.memoryLimit))
        if (container.cpus.isNotBlank()) InfoRow("CPU 配额", container.cpus)
        if (container.pidsLimit > 0) InfoRow("进程上限", container.pidsLimit.toString())

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("进程", selected = tab == "process", color = Glass.Accent) { onTab("process") }
            Pill("应用", selected = tab == "app", color = Glass.Violet) { onTab("app") }
            Pill("服务", selected = tab == "service", color = Glass.Mint) { onTab("service") }
            Pill("刷新", color = Glass.Faint) { onRefresh() }
        }

        Spacer(Modifier.height(6.dp))
        if (!container.running) {
            Text(
                "容器已停止。启动后可以查看进程、应用和系统服务。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            return@GlassPanel
        }
        if (loading) {
            Text("正在读取…", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            return@GlassPanel
        }

        when (tab) {
            "app" -> {
                if (apps.isEmpty()) {
                    Text(
                        "没有找到 .desktop 应用。容器里需要安装桌面环境和应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                apps.take(80).forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.exec,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                ),
                                color = Glass.Faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "打开",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Accent,
                            modifier = Modifier.clickable { onStartApp(app) }.padding(horizontal = 5.dp),
                        )
                        Text(
                            "关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Rose,
                            modifier = Modifier.clickable { onStopApp(app) }.padding(horizontal = 5.dp),
                        )
                    }
                }
                Text(
                    "应用需要在容器的桌面会话中运行；部分程序没有 DISPLAY 会启动失败。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
            "service" -> {
                Text(
                    "init: $initType",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
                if (services.isEmpty()) {
                    Text(
                        "没有找到服务列表。可能是不支持的 init 系统。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                services.take(100).forEach { service ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                service.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                service.state,
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                        Text(
                            "启动",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Accent,
                            modifier = Modifier
                                .clickable { onServiceAction(service, "start") }
                                .padding(horizontal = 5.dp),
                        )
                        Text(
                            "停止",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Rose,
                            modifier = Modifier
                                .clickable { onServiceAction(service, "stop") }
                                .padding(horizontal = 5.dp),
                        )
                    }
                }
            }
            else -> {
                if (processes.isEmpty()) {
                    Text(
                        "没有读取到进程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                processes.take(60).forEach { process ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${process.name} · ${process.pid}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = Glass.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                process.args.ifBlank { process.user },
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                ),
                                color = Glass.Faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "结束",
                            style = MaterialTheme.typography.labelSmall,
                            color = Glass.Rose,
                            modifier = Modifier
                                .clickable { onKillProcess(process) }
                                .padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Glass.Faint,
            modifier = Modifier.width(64.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Glass.Text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun cpuText(container: DsContainer): String {
    val percent = if (container.cpuLimitPermill > 0) {
        container.cpuPermill * 100.0 / container.cpuLimitPermill
    } else {
        container.cpuPermill / 10.0
    }
    return String.format("%.1f%% CPU", percent.coerceIn(0.0, 100.0))
}

private fun ramText(container: DsContainer, hostRamKb: Long): String {
    if (container.ramUsedKb <= 0) return "—"
    val base = if (container.ramLimitKb > 0) container.ramLimitKb else hostRamKb
    val percent = if (base > 0) container.ramUsedKb * 100.0 / base else 0.0
    return "${formatKb(container.ramUsedKb)} (${String.format("%.0f", percent)}%)"
}

private fun formatKb(kb: Long): String {
    val mb = kb / 1024.0
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format("%.1f GB", gb) else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
}
