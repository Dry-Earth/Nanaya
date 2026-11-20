package io.github.dryearth.solidbutler.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.PerfUiState
import io.github.dryearth.solidbutler.core.Capabilities
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AmbientBackground
import io.github.dryearth.solidbutler.ui.components.LocalGlassBackdrop
import io.github.dryearth.solidbutler.ui.components.liquid.lens
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.screens.AppsScreen
import io.github.dryearth.solidbutler.ui.screens.AiSettingsScreen
import io.github.dryearth.solidbutler.ui.screens.AssistantScreen
import io.github.dryearth.solidbutler.ui.screens.AutomationScreen
import io.github.dryearth.solidbutler.ui.screens.AutostartScreen
import io.github.dryearth.solidbutler.ui.screens.DiagnosticsScreen
import io.github.dryearth.solidbutler.ui.screens.DomainFirewallScreen
import io.github.dryearth.solidbutler.ui.screens.LineageScreen
import io.github.dryearth.solidbutler.ui.screens.MemoryScreen
import io.github.dryearth.solidbutler.ui.screens.NotificationsScreen
import io.github.dryearth.solidbutler.ui.screens.RulesScreen
import io.github.dryearth.solidbutler.ui.screens.WakeupScreen
import io.github.dryearth.solidbutler.ui.screens.SpecialPermissionsScreen
import io.github.dryearth.solidbutler.ui.screens.HomeScreen
import io.github.dryearth.solidbutler.ui.screens.PerformanceScreen
import io.github.dryearth.solidbutler.ui.screens.SecurityScreen
import io.github.dryearth.solidbutler.ui.screens.SettingsScreen
import io.github.dryearth.solidbutler.ui.screens.StorageScreen
import io.github.dryearth.solidbutler.ui.theme.Glass

private data class NavItem(val id: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "总览", Icons.Rounded.GridView),
    NavItem("storage", "存储", Icons.Rounded.Storage),
    NavItem("apps", "应用", Icons.Rounded.Apps),
    NavItem("perf", "性能", Icons.Rounded.Speed),
    NavItem("security", "安全", Icons.Rounded.Shield),
)

@Composable
fun ButlerApp(
    vm: ButlerViewModel,
    startWithChat: Boolean = false,
    onStartChatConsumed: () -> Unit = {},
) {
    val caps by vm.caps.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("home") }
    var chat by rememberSaveable { mutableStateOf(startWithChat) }
    var autoVoice by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var aiSettings by rememberSaveable { mutableStateOf(false) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val nav by vm.navigate.collectAsStateWithLifecycle()
    val appsState by vm.apps.collectAsStateWithLifecycle()
    val detailOpen = appsState.detail != null
    val glassBackdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(Glass.Ink)
        drawContent()
    }

    LaunchedEffect(nav) {
        val target = nav ?: return@LaunchedEffect
        when (target) {
            "ai" -> chat = true
            else -> if (navItems.any { it.id == target }) tab = target
        }
        vm.consumeNavigation()
    }

    LaunchedEffect(startWithChat) {
        if (startWithChat) {
            chat = true
            onStartChatConsumed()
        }
    }

    var foreground by remember { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    vm.refreshCapabilities()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    vm.stopMonitoring()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val screenKey = when {
        chat -> "chat"
        settings -> "settings"
        aiSettings -> "ai"
        sub != null -> sub!!
        else -> tab
    }
    LaunchedEffect(screenKey, foreground) {
        if (foreground && (screenKey == "home" || screenKey == "perf")) {
            vm.startMonitoring()
        } else {
            vm.stopMonitoring()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
                AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured)
            }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                if (sub == "autostart") {
                    AutostartScreen(vm, onBack = { sub = null }, onOpenWakeup = { sub = "wakeup" })
                } else if (sub == "lineage") {
                    LineageScreen(onBack = { sub = null })
                } else if (sub == "special") {
                    SpecialPermissionsScreen(onBack = { sub = null })
                } else if (sub == "notifications") {
                    NotificationsScreen(onBack = { sub = null })
                } else if (sub == "wakeup") {
                    WakeupScreen(onBack = { sub = "autostart" })
                } else if (sub == "diagnostics") {
                    DiagnosticsScreen(onBack = { sub = null })
                } else if (sub == "domain") {
                    DomainFirewallScreen(onBack = { sub = null })
                } else if (sub == "rules") {
                    RulesScreen(vm, onBack = { sub = null })
                } else if (sub == "memory") {
                    MemoryScreen(onBack = { sub = null })
                } else if (sub == "automation") {
                    AutomationScreen(onBack = { sub = null })
                } else {
                    when (tab) {
                        "home" -> HomeScreen(
                            vm,
                            onOpenTab = { tab = it },
                            onChat = { chat = true },
                            onOpenAutostart = { sub = "autostart" },
                            onOpenMemory = { sub = "memory" },
                            onOpenAutomation = { sub = "automation" },
                        )
                        "storage" -> StorageScreen(vm)
                        "apps" -> AppsScreen(
                            vm,
                            onOpenAutostart = { sub = "autostart" },
                            onOpenDiagnostics = { sub = "diagnostics" },
                        )
                        "perf" -> PerformanceScreen(
                            vm,
                            onOpenRules = { sub = "rules" },
                            onOpenMemory = { sub = "memory" },
                        )
                        "security" -> SecurityScreen(
                            vm,
                            onOpenSpecial = { sub = "special" },
                            onOpenDomain = { sub = "domain" },
                        )
                    }
                }
            }
            }
        }
        if (sub == null && !detailOpen) {
            TopRefractionSeam(
                backdrop = contentBackdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = statusBarHeight),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            ) {
                TopBar(
                    caps = caps,
                    perf = perf,
                    aiConfigured = vm.aiConfigured,
                    onChat = { chat = true },
                    onSettings = { settings = true },
                )
            }
        }
        if (!detailOpen) {
            BottomBar(
                current = tab,
                onSelect = { sub = null; tab = it },
                backdrop = contentBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (chat) {
        Box(Modifier.fillMaxSize().background(Glass.Ink)) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured)
        }
            AssistantScreen(
                vm = vm,
                autoListen = autoVoice,
                onAutoListenConsumed = { autoVoice = false },
                onClose = { chat = false },
                onSettings = { chat = false; aiSettings = true },
            )
        }
    }
    if (settings) {
        Box(Modifier.fillMaxSize().background(Glass.Ink)) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured)
        }
            SettingsScreen(
                vm = vm,
                caps = caps,
                onClose = { settings = false },
                onOpenAiSettings = { settings = false; aiSettings = true },
                onOpenAutomation = { settings = false; sub = "automation" },
                onOpenLineage = { settings = false; chat = false; sub = "lineage" },
                onOpenNotifications = { settings = false; chat = false; sub = "notifications" },
            )
        }
    }
    if (aiSettings) {
        Box(Modifier.fillMaxSize().background(Glass.Ink)) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured)
        }
            AiSettingsScreen(
                vm = vm,
                onBack = { aiSettings = false },
                onChat = { aiSettings = false; chat = true },
            )
        }
    }
}

@Composable
private fun AiTitle(ready: Boolean) {
    if (!ready) {
        Text(
            "Nanaya",
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.5.sp),
            color = Glass.Text,
            fontWeight = FontWeight.Light,
        )
        return
    }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            phase = (phase + 0.04f) % 1f
        }
    }
    val colors = listOf(Glass.Accent, Glass.Violet, Glass.Mint, Glass.Amber, Glass.Accent)
    val brush = Brush.linearGradient(
        colors = colors,
        start = Offset(phase * 280f - 100f, 0f),
        end = Offset(phase * 280f + 120f, 54f),
    )
    Column {
        Box {
            // aurora wash behind the word
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = colors.map { it.copy(alpha = 0.10f) },
                        startX = phase * size.width - 40f,
                        endX = phase * size.width + 80f,
                    )
                )
            }
            Text(
                "Nanaya",
                style = TextStyle(
                    brush = brush,
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.5.sp,
                ),
            )
        }
        Canvas(Modifier.width(64.dp).height(7.dp)) {
            val path = Path()
            for (i in 0..80) {
                val t = i / 80f
                val x = size.width * t
                val y = size.height / 2f + kotlin.math.sin(t * 6.28318f + phase * 6.28318f) * 1.6.dp.toPx()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(
                    listOf(Glass.Accent, Glass.Violet, Glass.Mint, Glass.Amber)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

@Composable
private fun BreathingDot(ready: Boolean, active: Boolean) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ready) {
        if (ready) {
            while (true) {
                delay(80)
                phase = (phase + 0.05f) % 6.28318f
            }
        }
    }
    Canvas(Modifier.size(12.dp)) {
        val base = if (active) Glass.Mint else Glass.Amber
        if (ready) {
            drawCircle(
                base.copy(alpha = 0.16f + 0.10f * kotlin.math.abs(kotlin.math.sin(phase))),
                radius = 3.2.dp.toPx() + kotlin.math.abs(kotlin.math.sin(phase)) * 1.6.dp.toPx(),
            )
        }
        drawCircle(base, radius = 2.2.dp.toPx())
    }
}

@Composable
private fun TopBar(
    caps: Capabilities,
    perf: PerfUiState,
    aiConfigured: Boolean,
    onChat: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiTitle(aiConfigured)
                    Spacer(Modifier.width(8.dp))
                    BreathingDot(ready = aiConfigured, active = caps.privileged)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${caps.device} · ${caps.android}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
            IconButton(onClick = onChat) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 管家", tint = Glass.Accent)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = Glass.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val b = perf.snapshot.battery
            Pill(
                text = if (b.level > 0) "${b.level}% ${if (b.charging) "充电中" else "电量"}" else "读取中",
                selected = b.charging,
                color = if (b.charging) Glass.Mint else Glass.Accent,
            )
            if (perf.snapshot.ramTotal > 0) {
                val ramPct = (perf.snapshot.ramUsed.toDouble() / perf.snapshot.ramTotal.toDouble() * 100).toInt()
                Pill("内存 $ramPct%", color = Glass.Ice)
            }
            if (caps.root) {
                Pill("Root", selected = true, color = Glass.Mint)
            } else if (caps.adbConnected) {
                Pill("无线 ADB", selected = true, color = Glass.Accent)
            }
            if (aiConfigured) {
                Pill("AI 就绪", selected = true, color = Glass.Violet, onClick = onChat)
            }
        }
    }
}

@Composable
private fun TopRefractionSeam(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // A thin specular edge at the seam.
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.8.dp.toPx(),
                )
                // Fade the lens strip at both edges instead of a hard band.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        lens(
                            refractionHeight = 18.dp.toPx(),
                            refractionAmount = 14.dp.toPx(),
                        )
                    },
                ),
        )
    }
}

@Composable
private fun BottomBar(
    current: String,
    onSelect: (String) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            modifier = Modifier.height(66.dp),
            corner = 33.dp,
            padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            strongGlass = true,
            backdropOverride = backdrop,
        ) {
            Row(
                Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { item ->
                    val selected = current == item.id
                    Column(
                        Modifier
                            .width(64.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(item.id) }
                            .background(if (selected) Color(0x10FFFFFF) else Color.Transparent)
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected) Glass.Accent else Glass.Faint,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Glass.Text else Glass.Faint,
                        )
                    }
                }
            }
        }
    }
}
