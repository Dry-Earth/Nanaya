package io.github.dryearth.Nanaya.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dryearth.Nanaya.ButlerViewModel
import io.github.dryearth.Nanaya.PerfUiState
import io.github.dryearth.Nanaya.core.Capabilities
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.AmbientBackground
import io.github.dryearth.Nanaya.ui.components.HieroglyphMark
import io.github.dryearth.Nanaya.ui.components.LocalGlassBackdrop
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.liquid.lens
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.service.NanayaStatus
import io.github.dryearth.Nanaya.ui.screens.AppsScreen
import io.github.dryearth.Nanaya.ui.screens.AiSettingsScreen
import io.github.dryearth.Nanaya.ui.screens.AssistantScreen
import io.github.dryearth.Nanaya.ui.screens.AutomationScreen
import io.github.dryearth.Nanaya.ui.screens.AutostartScreen
import io.github.dryearth.Nanaya.ui.screens.DiagnosticsScreen
import io.github.dryearth.Nanaya.ui.screens.DomainFirewallScreen
import io.github.dryearth.Nanaya.ui.screens.LineageScreen
import io.github.dryearth.Nanaya.ui.screens.MemoryScreen
import io.github.dryearth.Nanaya.ui.screens.MusicScreen
import io.github.dryearth.Nanaya.ui.screens.NotificationsScreen
import io.github.dryearth.Nanaya.ui.screens.RulesScreen
import io.github.dryearth.Nanaya.ui.screens.WakeupScreen
import io.github.dryearth.Nanaya.ui.screens.SpecialPermissionsScreen
import io.github.dryearth.Nanaya.ui.screens.HomeScreen
import io.github.dryearth.Nanaya.ui.screens.ToolsScreen
import io.github.dryearth.Nanaya.ui.screens.RemoteAdbScreen
import io.github.dryearth.Nanaya.ui.screens.AdbConsoleScreen
import io.github.dryearth.Nanaya.ui.screens.UsbAdbScreen
import io.github.dryearth.Nanaya.ui.screens.PerformanceScreen
import io.github.dryearth.Nanaya.ui.screens.SecurityScreen
import io.github.dryearth.Nanaya.ui.screens.SettingsScreen
import io.github.dryearth.Nanaya.ui.screens.StorageScreen
import io.github.dryearth.Nanaya.ui.theme.Glass

private data class NavItem(val id: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "总览", Icons.Rounded.GridView),
    NavItem("storage", "存储", Icons.Rounded.Storage),
    NavItem("apps", "应用", Icons.Rounded.Apps),
    NavItem("perf", "性能", Icons.Rounded.Speed),
    NavItem("security", "安全", Icons.Rounded.Shield),
    NavItem("tools", "工具", Icons.Rounded.Build),
)

private val settingsItem = NavItem("settings", "设置", Icons.Rounded.Settings)

@Composable
fun ButlerApp(
    vm: ButlerViewModel,
    startWithChat: Boolean = false,
    onStartChatConsumed: () -> Unit = {},
) {
    val caps by vm.caps.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("home") }
    var homeScroll by remember { mutableFloatStateOf(0f) }
    var chat by rememberSaveable { mutableStateOf(startWithChat) }
    var autoVoice by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var aiSettings by rememberSaveable { mutableStateOf(false) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var backTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val nav by vm.navigate.collectAsStateWithLifecycle()
    val appsState by vm.apps.collectAsStateWithLifecycle()
    val storageState by vm.storage.collectAsStateWithLifecycle()
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
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    NanayaStatus.start(context)
                    vm.refreshCapabilities()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    NanayaStatus.stop()
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
    LaunchedEffect(storageState.cleaning) {
        NanayaStatus.setCleaning(storageState.cleaning)
    }
    LaunchedEffect(screenKey, tab, sub, chat, settings, aiSettings) {
        NanayaStatus.setAssistant(chat)
        NanayaStatus.setScreen(
            when {
                settings || aiSettings -> NanayaStatus.State.APP
                tab == "security" -> NanayaStatus.State.SECURITY
                tab == "perf" -> NanayaStatus.State.PERFORMANCE
                sub == "special" || sub == "domain" || sub == "lineage" || sub == "notifications" ->
                    NanayaStatus.State.SECURITY
                sub == "rules" || sub == "memory" || sub == "diagnostics" ->
                    NanayaStatus.State.PERFORMANCE
                else -> NanayaStatus.State.APP
            },
        )
    }

    fun closeSub() {
        if (sub == "wakeup") {
            sub = "autostart"
            return
        }
        sub = null
        if (backTarget == "settings") {
            settings = true
            backTarget = null
        }
    }

    fun closeAiSettings() {
        aiSettings = false
        when (backTarget) {
            "settings" -> settings = true
            "chat" -> chat = true
        }
        backTarget = null
    }

    BackHandler(
        enabled = detailOpen || aiSettings || settings || chat || sub != null || tab != "home",
    ) {
        when {
            detailOpen -> vm.closeApp()
            aiSettings -> closeAiSettings()
            settings -> {
                settings = false
                backTarget = null
            }
            chat -> chat = false
            sub != null -> closeSub()
            tab != "home" -> tab = "home"
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
                AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured, appearance.soften)
            }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                if (sub == "remoteadb") {
                    RemoteAdbScreen(onBack = { closeSub() })
                } else if (sub == "usbotg") {
                    UsbAdbScreen(onBack = { closeSub() })
                } else if (sub == "adbconsole") {
                    AdbConsoleScreen(onBack = { closeSub() })
                } else if (sub == "autostart") {
                    AutostartScreen(vm, onBack = { closeSub() }, onOpenWakeup = { sub = "wakeup" })
                } else if (sub == "lineage") {
                    LineageScreen(onBack = { closeSub() })
                } else if (sub == "special") {
                    SpecialPermissionsScreen(onBack = { closeSub() })
                } else if (sub == "notifications") {
                    NotificationsScreen(onBack = { closeSub() })
                } else if (sub == "wakeup") {
                    WakeupScreen(onBack = { sub = "autostart" })
                } else if (sub == "diagnostics") {
                    DiagnosticsScreen(onBack = { closeSub() })
                } else if (sub == "domain") {
                    DomainFirewallScreen(onBack = { closeSub() })
                } else if (sub == "rules") {
                    RulesScreen(vm, onBack = { closeSub() })
                } else if (sub == "memory") {
                    MemoryScreen(onBack = { closeSub() })
                } else if (sub == "automation") {
                    AutomationScreen(onBack = { closeSub() })
                } else if (sub == "music") {
                    MusicScreen(onBack = { closeSub() })
                } else {
                    when (tab) {
                        "home" -> HomeScreen(
                            vm,
                            onOpenTab = { tab = it },
                            onChat = { chat = true },
                            onOpenAutostart = { sub = "autostart" },
                            onOpenMemory = { sub = "memory" },
                            onOpenAutomation = { sub = "automation" },
                            onScrollProgress = { homeScroll = it },
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
                        "tools" -> ToolsScreen(
                            onBack = { tab = "home" },
                            onOpenRemote = { sub = "remoteadb" },
                            onOpenLocal = { sub = "adbconsole" },
                            onOpenUsb = { sub = "usbotg" },
                            onOpenMusic = { sub = "music" },
                        )
                    }
                }
            }
            }
        }
        if (sub == null && !detailOpen && vm.aiConfigured) {
            AiStatusLight(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = statusBarHeight),
            )
        }
        if (sub == null && !detailOpen && tab == "home") {
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
                    scrollProgress = homeScroll,
                    onChat = { chat = true },
                )
            }
        } else if (sub == null && !detailOpen) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AiGlassButton(
                    backdrop = contentBackdrop,
                    onClick = { chat = true },
                )
            }
        }
        if (sub == null && !detailOpen) {
            BottomBar(
                current = tab,
                onSelect = { sub = null; tab = it },
                onOpenSettings = { settings = true; backTarget = null },
                backdrop = contentBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (chat) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured, appearance.soften)
        }
            AssistantScreen(
                vm = vm,
                autoListen = autoVoice,
                onAutoListenConsumed = { autoVoice = false },
                onClose = { chat = false },
                onSettings = { chat = false; aiSettings = true; backTarget = "chat" },
            )
        }
    }
    if (settings) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured, appearance.soften)
        }
            SettingsScreen(
                vm = vm,
                caps = caps,
                onClose = { settings = false; backTarget = null },
                onOpenAiSettings = { settings = false; aiSettings = true; backTarget = "settings" },
                onOpenAutomation = { settings = false; sub = "automation"; backTarget = "settings" },
                onOpenLineage = { settings = false; chat = false; sub = "lineage"; backTarget = "settings" },
                onOpenNotifications = { settings = false; chat = false; sub = "notifications"; backTarget = "settings" },
            )
        }
    }
    if (aiSettings) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            AmbientBackground(appearance.style, appearance.uri, vm.aiConfigured, appearance.soften)
        }
            AiSettingsScreen(
                vm = vm,
                onBack = { closeAiSettings() },
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
private fun FlowingAiIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ai-icon-flow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shift",
    )
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val side = size.minDimension
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF67E8F9),
                            Color(0xFF818CF8),
                            Color(0xFFE879F9),
                            Color(0xFF6EE7B7),
                            Color(0xFF67E8F9),
                        ),
                        start = Offset(-side + shift * side * 2f, 0f),
                        end = Offset(shift * side * 2f, side),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    ) {
        HieroglyphMark(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            contentDescription = "AI 管家",
        )
    }
}

@Composable
private fun AiGlassButton(backdrop: LayerBackdrop, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier
            .size(40.dp)
            .shadow(
                6.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(6.dp.toPx(), 6.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                    )
                },
                onDrawSurface = { drawRect(Color(0x08FFFFFF)) },
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x88FFFFFF), Color(0x22FFFFFF), Color(0x0DFFFFFF)),
                ),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FlowingAiIcon(Modifier.size(28.dp))
    }
}

@Composable
private fun AiStatusLight(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ai-status-light")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "sweep",
    )
    val breath by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            tween(2100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Canvas(modifier) {
        val w = size.width
        val lineY = 1.6.dp.toPx()
        // Faint continuous rail.
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.12f to Glass.Accent.copy(alpha = 0.10f * breath),
                0.5f to Glass.Violet.copy(alpha = 0.16f * breath),
                0.88f to Glass.Accent.copy(alpha = 0.10f * breath),
                1f to Color.Transparent,
            ),
            start = Offset(0f, lineY),
            end = Offset(w, lineY),
            strokeWidth = 1.dp.toPx(),
        )
        // Travelling highlight.
        val head = -0.25f * w + sweep * 1.5f * w
        val tail = head - 0.42f * w
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Glass.Accent.copy(alpha = 0.22f * breath),
                    Color(0xCCBFD6FF).copy(alpha = 0.92f * breath),
                    Glass.Violet.copy(alpha = 0.40f * breath),
                    Color.Transparent,
                ),
                startX = tail,
                endX = head,
            ),
            start = Offset(tail, lineY),
            end = Offset(head, lineY),
            strokeWidth = 1.6.dp.toPx(),
        )
        // Soft halo around the travelling head.
        val halo = Brush.radialGradient(
            colors = listOf(
                Color(0x66BFD6FF).copy(alpha = breath),
                Glass.Accent.copy(alpha = 0.18f * breath),
                Color.Transparent,
            ),
            center = Offset(head, lineY),
            radius = 26.dp.toPx(),
        )
        drawCircle(
            brush = halo,
            radius = 26.dp.toPx(),
            center = Offset(head, lineY),
        )
    }
}

@Composable
private fun BarItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(38.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(if (selected) Color(0x10FFFFFF) else Color.Transparent)
            .padding(vertical = 4.dp),
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

@Composable
private fun TopBar(
    caps: Capabilities,
    perf: PerfUiState,
    aiConfigured: Boolean,
    scrollProgress: Float,
    onChat: () -> Unit,
) {
    val progress = scrollProgress.coerceIn(0f, 1f)
    val raw = (progress / 0.42f).coerceIn(0f, 1f)
    val eased = raw * raw * (3f - 2f * raw)

    Box(Modifier.fillMaxWidth().height(104.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .graphicsLayer {
                    alpha = 1f - eased
                    translationY = -eased * 6.dp.toPx()
                }
                .then(
                    if (eased > 0.01f) Modifier.blur((eased * 10).dp) else Modifier,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
            ) {
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

        IconButton(
            onClick = onChat,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp),
        ) {
            FlowingAiIcon(modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun BottomBar(
    current: String,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            modifier = Modifier.height(56.dp),
            corner = 28.dp,
            padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            strongGlass = true,
            backdropOverride = backdrop,
        ) {
            Row(
                Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { item ->
                    BarItem(item, selected = current == item.id) { onSelect(item.id) }
                }
                BarItem(settingsItem, selected = false, onClick = onOpenSettings)
            }
        }
    }
}
