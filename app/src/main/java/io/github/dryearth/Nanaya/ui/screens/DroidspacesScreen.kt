package io.github.dryearth.Nanaya.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.core.DroidspacesDesktop
import io.github.dryearth.Nanaya.core.DroidspacesRepository
import io.github.dryearth.Nanaya.core.DroidspacesTerminals
import io.github.dryearth.Nanaya.core.DsApp
import io.github.dryearth.Nanaya.core.DsContainer
import io.github.dryearth.Nanaya.core.DsProcess
import io.github.dryearth.Nanaya.core.DsService
import io.github.dryearth.Nanaya.core.DsSnapshot
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.core.mergeLive
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.FlowingRefreshButton
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
    var logText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var processes by remember { mutableStateOf<List<DsProcess>>(emptyList()) }
    var apps by remember { mutableStateOf<List<DsApp>>(emptyList()) }
    var services by remember { mutableStateOf<List<DsService>>(emptyList()) }
    var panelLoading by remember { mutableStateOf(false) }
    var expandedPids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    val disks = remember { mutableStateMapOf<String, String>() }
    var terminalFor by remember { mutableStateOf<DsContainer?>(null) }
    var commandFor by remember { mutableStateOf<String?>(null) }
    val activeTerminals by DroidspacesTerminals.active.collectAsState()

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
        filter = ""
        if (!container.running) return
        panelLoading = true
        scope.launch {
            when (kind) {
                "app" -> {
                    val loadedApps = withContext(Dispatchers.IO) {
                        DroidspacesRepository.apps(container.name)
                    }
                    val loadedProcesses = withContext(Dispatchers.IO) {
                        DroidspacesRepository.processes(container.name)
                    }
                    apps = loadedApps
                    processes = loadedProcesses
                }
                "log" -> logText = withContext(Dispatchers.IO) {
                    DroidspacesRepository.logs(container.name)
                }
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
        expandedPids = setOf("1")
        filter = ""
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

    BackHandler(enabled = terminalFor != null) { terminalFor = null }
    terminalFor?.let { container ->
        DroidspacesTerminalScreen(
            containerName = container.name,
            containerPid = container.pid,
            rootfs = container.rootfs,
            onBack = { terminalFor = null },
        )
        return
    }

    BackHandler(enabled = commandFor != null) { commandFor = null }
    commandFor?.let { name ->
        DroidspacesCommandScreen(containerName = name, onBack = { commandFor = null })
        return
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
            FlowingRefreshButton(
                refreshing = loading,
                onClick = { reload() },
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(6.dp))
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
                    loading = panelLoading || (loading && expanded == container.name),
                    processes = processes,
                    apps = apps,
                    services = services,
                    initType = initType,
                    logText = logText,
                    busy = busy,
                    onToggle = { toggle(container) },
                    onTab = { loadTab(container, it) },
                    onStart = {
                        operation("启动") { DroidspacesRepository.start(container.name) }
                    },
                    onStop = {
                        askConfirm("停止容器", container.name) {
                            DroidspacesTerminals.close(container.name)
                            operation("停止") { DroidspacesRepository.stop(container.name) }
                        }
                    },
                    onRestart = {
                        askConfirm("重启容器", container.name) {
                            DroidspacesTerminals.close(container.name)
                            operation("重启") { DroidspacesRepository.restart(container.name) }
                        }
                    },
                    onRefresh = {
                        reload()
                        if (container.running) loadTab(container, tab)
                    },
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
                        if (action == "stop" && essentialService(service.name)) {
                            askConfirm("停止系统服务", service.name) {
                                operation("停止服务") {
                                    DroidspacesRepository.serviceAction(container.name, initType, service.name, action)
                                }
                            }
                        } else {
                            operation("服务 $action") {
                                DroidspacesRepository.serviceAction(container.name, initType, service.name, action)
                            }
                        }
                    },
                    expandedPids = expandedPids,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onTogglePid = { pid ->
                        expandedPids = if (pid in expandedPids) expandedPids - pid else expandedPids + pid
                    },
                    onOpenTerminal = { terminalFor = container },
                    onOpenCommand = { commandFor = container.name },
                    onOpenDesktop = {
                        message = DroidspacesDesktop.open(context, container) ?: "已打开桌面"
                    },
                    hasTerminal = container.name in activeTerminals,
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
    logText: String,
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
    onOpenTerminal: () -> Unit,
    onOpenCommand: () -> Unit,
    onOpenDesktop: () -> Unit,
    hasTerminal: Boolean,
    expandedPids: Set<String>,
    onTogglePid: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
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
            if (!container.running) {
                ActionButton(
                    "启动",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onStart,
                )
            } else {
                ActionButton(
                    "停止",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                )
                ActionButton(
                    "重启",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onRestart,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (container.running && (container.x11 || container.anlandSock.isNotBlank())) {
                ActionButton(
                    "打开桌面",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDesktop,
                )
            }
            ActionButton(
                if (hasTerminal) "回到终端" else "终端",
                enabled = container.running,
                modifier = Modifier.weight(1f),
                onClick = onOpenTerminal,
            )
            ActionButton(
                "命令",
                enabled = container.running,
                modifier = Modifier.weight(1f),
                onClick = onOpenCommand,
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
            Pill("日志", selected = tab == "log", color = Glass.Ice) { onTab("log") }
            FlowingRefreshButton(
                refreshing = loading,
                onClick = onRefresh,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = filter,
            onValueChange = onFilterChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Glass.Text,
            ),
            cursorBrush = SolidColor(Glass.Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (filter.isEmpty()) {
                    Text(
                        "筛选进程 / 应用 / 服务",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Faint,
                    )
                }
                inner()
            },
        )

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
                apps.filter {
                    filter.isBlank() || it.name.contains(filter, true) || it.exec.contains(filter, true)
                }.take(80).forEach { app ->
                    val usage = appUsage(app, processes)
                    val running = usage.running
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    app.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (running) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (usage.rssKb > 0) "运行中 · ${formatKb(usage.rssKb)}" else "运行中",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Mint,
                                    )
                                }
                                if (app.systemApp) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                    )
                                }
                            }
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
                        if (running) {
                            Text(
                                "终止",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Rose,
                                modifier = Modifier.clickable { onStopApp(app) }.padding(horizontal = 5.dp),
                            )
                        } else {
                            Text(
                                "打开",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Accent,
                                modifier = Modifier.clickable { onStartApp(app) }.padding(horizontal = 5.dp),
                            )
                        }
                    }
                }
                Text(
                    "应用需要在容器的桌面会话中运行；部分程序没有 DISPLAY 会启动失败。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
            "log" -> {
                SelectionContainer {
                    Text(
                        logText.ifBlank { "没有读取到日志。" },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            lineHeight = 14.sp,
                            color = Glass.Muted,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                services.filter {
                    filter.isBlank() || it.name.contains(filter, true) || it.state.contains(filter, true)
                }.take(100).forEach { service ->
                    val state = service.state.lowercase()
                    val running = state.contains("active") || state.contains("running") ||
                        state.contains("started")
                    val essential = essentialService(service.name)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    service.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (essential) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                    )
                                }
                            }
                            Text(
                                service.state,
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                        if (running) {
                            Text(
                                "停止",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Rose,
                                modifier = Modifier
                                    .clickable { onServiceAction(service, "stop") }
                                    .padding(horizontal = 5.dp),
                            )
                        } else {
                            Text(
                                "启用",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Accent,
                                modifier = Modifier
                                    .clickable { onServiceAction(service, "start") }
                                    .padding(horizontal = 5.dp),
                            )
                        }
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
                val processRows = if (filter.isBlank()) {
                    visibleProcessTree(processes, expandedPids)
                } else {
                    processes
                        .filter { it.name.contains(filter, true) || it.args.contains(filter, true) }
                        .map { Triple(it, 0, false) }
                }
                processRows.take(150).forEach { node ->
                    val process = node.first
                    val depth = node.second
                    val hasChildren = node.third
                    val essential = essentialProcess(process)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (hasChildren) {
                                        if (process.pid in expandedPids) "▾" else "▸"
                                    } else {
                                        " "
                                    },
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = Glass.Accent,
                                    modifier = Modifier
                                        .clickable(enabled = hasChildren) { onTogglePid(process.pid) }
                                        .padding(end = 3.dp),
                                )
                                Text(
                                    buildString {
                                        repeat(depth) { append("  ") }
                                        append(process.name)
                                        append(" · ")
                                        append(process.pid)
                                    },
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = Glass.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (process.rssKb > 0) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        formatKb(process.rssKb),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                    )
                                }
                                if (essential) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Glass.Faint,
                                    )
                                }
                            }
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
                        if (!essential) {
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

private data class AppUsage(val running: Boolean, val rssKb: Long)

private fun appUsage(app: DsApp, processes: List<DsProcess>): AppUsage {
    val exec = app.exec.replace(Regex("%[a-zA-Z]"), "").trim()
    val skip = setOf(
        "env", "sh", "bash", "dash", "flatpak", "snap", "gtk-launch",
        "dbus-run-session", "systemd-run", "run", "start", "exec",
    )
    val tokens = exec.split(Regex("\\s+"))
        .map { it.trim('"', '\'') }
        .filter { it.isNotBlank() && it !in skip && !it.contains('=') }
        .map { it.substringAfterLast('/') }
        .filter { it.length >= 3 }
    val needles = (tokens + app.desktopId + app.name)
        .map { it.lowercase() }
        .filter { it.length >= 3 }
        .distinct()
    if (needles.isEmpty()) return AppUsage(false, 0L)
    val matched = processes.filter { process ->
        val name = process.name.lowercase()
        val args = process.args.lowercase()
        needles.any { needle ->
            name == needle || args.contains(needle)
        }
    }
    return AppUsage(matched.isNotEmpty(), matched.sumOf { it.rssKb })
}

private fun essentialProcess(process: DsProcess): Boolean {
    if (process.pid == "1" || process.pid == "2") return true
    val name = process.name.lowercase()
    if (name.startsWith("[")) return true
    return name in setOf(
        "systemd", "init", "kthreadd", "systemd-journald", "systemd-udevd",
        "systemd-logind", "dbus-daemon", "dbus-broker", "sshd", "cron",
        "rsyslogd", "agetty", "login", "seatd", "elogind",
    )
}

private fun essentialService(name: String): Boolean {
    val unit = name.substringBefore('@').lowercase()
    return unit.startsWith("systemd-") ||
        unit.startsWith("dbus") ||
        unit.startsWith("polkit") ||
        unit.startsWith("udev") ||
        unit in setOf(
            "systemd", "init", "networkmanager", "wpa_supplicant", "connman",
            "sshd", "cron", "rsyslog", "elogind", "seatd", "getty",
        )
}

private fun visibleProcessTree(
    processes: List<DsProcess>,
    expanded: Set<String>,
): List<Triple<DsProcess, Int, Boolean>> {
    if (processes.isEmpty()) return emptyList()
    val byPid = processes.associateBy { it.pid }
    val children = processes.groupBy { it.ppid }
    val visited = mutableSetOf<String>()
    val result = mutableListOf<Triple<DsProcess, Int, Boolean>>()
    fun walk(process: DsProcess, depth: Int) {
        if (!visited.add(process.pid)) return
        val childList = children[process.pid]
            .orEmpty()
            .sortedBy { it.pid.toIntOrNull() ?: 0 }
        result += Triple(process, depth, childList.isNotEmpty())
        if (process.pid in expanded) {
            childList.forEach { walk(it, depth + 1) }
        }
    }
    processes
        .filter { it.ppid !in byPid || it.ppid == it.pid }
        .sortedBy { it.pid.toIntOrNull() ?: 0 }
        .forEach { walk(it, 0) }
    processes.filter { it.pid !in visited }.forEach { walk(it, 0) }
    return result
}
