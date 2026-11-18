package io.github.dryearth.solidbutler.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.Capabilities
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.AmbientBackground
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.screens.AppsScreen
import io.github.dryearth.solidbutler.ui.screens.AssistantScreen
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
fun ButlerApp(vm: ButlerViewModel) {
    val caps by vm.caps.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("home") }
    var chat by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val nav by vm.navigate.collectAsStateWithLifecycle()

    LaunchedEffect(nav) {
        val target = nav ?: return@LaunchedEffect
        when (target) {
            "ai" -> chat = true
            else -> if (navItems.any { it.id == target }) tab = target
        }
        vm.consumeNavigation()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.refreshCapabilities()
                    vm.startMonitoring()
                }
                Lifecycle.Event.ON_STOP -> vm.stopMonitoring()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                caps = caps,
                aiConfigured = vm.aiConfigured,
                onChat = { chat = true },
                onSettings = { settings = true },
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    "home" -> HomeScreen(vm, onOpenTab = { tab = it }, onChat = { chat = true })
                    "storage" -> StorageScreen(vm)
                    "apps" -> AppsScreen(vm)
                    "perf" -> PerformanceScreen(vm)
                    "security" -> SecurityScreen(vm)
                }
            }
            BottomBar(current = tab, onSelect = { tab = it })
        }
    }

    if (chat) {
        Box(Modifier.fillMaxSize().background(Glass.Ink)) {
            AmbientBackground()
            AssistantScreen(vm, onClose = { chat = false }, onSettings = { settings = true })
        }
    }
    if (settings) {
        Box(Modifier.fillMaxSize().background(Glass.Ink)) {
            AmbientBackground()
            SettingsScreen(
                vm = vm,
                caps = caps,
                onClose = { settings = false },
                onChat = { settings = false; chat = true },
            )
        }
    }
}

@Composable
private fun TopBar(
    caps: Capabilities,
    aiConfigured: Boolean,
    onChat: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "守岸人",
                        style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.5.sp),
                        color = Glass.Text,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (caps.privileged) Glass.Mint else Glass.Amber)
                    )
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
            Pill(
                text = if (caps.root) "Root" else "无 Root",
                selected = caps.root,
                color = Glass.Mint,
            )
            Pill(
                text = when {
                    caps.shizukuReady -> "Shizuku"
                    caps.shizukuInstalled -> "Shizuku 待授权"
                    else -> "无 Shizuku"
                },
                selected = caps.shizukuReady,
                color = Glass.Accent,
                onClick = onSettings,
            )
            if (caps.lineageHealth) {
                Pill("充电面板", selected = true, color = Glass.Ice)
            }
            Pill(
                text = if (aiConfigured) "云端 AI" else "AI 未配置",
                selected = aiConfigured,
                color = Glass.Violet,
                onClick = onChat,
            )
        }
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            corner = 24.dp,
            padding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { item ->
                    val selected = current == item.id
                    Column(
                        Modifier
                            .weight(1f)
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
