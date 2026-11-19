package io.github.dryearth.solidbutler.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.BuildConfig
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.core.AdbManager
import io.github.dryearth.solidbutler.core.Capabilities
import io.github.dryearth.solidbutler.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun SettingsScreen(
    vm: ButlerViewModel,
    caps: Capabilities,
    onClose: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenLineage: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    ToastEffect(chat.toast) { vm.clearChatToast() }
    var bgStyle by remember { mutableStateOf(appearance.style) }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            bgStyle = "image"
            vm.setBackground("image", uri.toString())
        }
    }
    var iconStyle by remember { mutableStateOf(vm.prefs.iconStyle) }
    var lockEnabled by remember { mutableStateOf(vm.prefs.appLockEnabled) }
    var pinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var adbExpanded by rememberSaveable { mutableStateOf(false) }
    var adbConnected by remember { mutableStateOf(AdbManager.connected()) }
    var adbBusy by remember { mutableStateOf(false) }
    var adbHost by rememberSaveable { mutableStateOf(localWifiIp(context) ?: "127.0.0.1") }
    var adbPort by rememberSaveable { mutableStateOf("") }
    var adbCode by rememberSaveable { mutableStateOf("") }
    var adbConnectPort by rememberSaveable { mutableStateOf("") }
    var adbMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x0FFFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("设置", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("权限、模型与本地数据", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Pill("v${BuildConfig.VERSION_NAME}", color = Glass.Faint)
        }

        SectionHeader("系统接口与硬件", "只展示这台设备实际提供的项")
        GlassPanel(onClick = onOpenLineage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接口与硬件清单", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    Text(
                        "lineagehealth / lineagehardware 与 SoC、GPU、NPU、内存等检测结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
            }
        }

        SectionHeader("通知渠道", "按渠道屏蔽营销通知")
        GlassPanel(onClick = onOpenNotifications) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("通知管理", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    Text(
                        "需要通知使用权；按渠道取消，不修改应用本身",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
            }
        }

        SectionHeader("清理保护", "白名单应用不会被批量清缓存")
        GlassPanel {
            val protectedCount = vm.prefs.whitelist().size
            Text(
                "已保护 $protectedCount 个应用",
                style = MaterialTheme.typography.titleMedium,
                color = Glass.Text,
            )
            Text(
                "在「应用 → 应用详情」里加入或移出。模型文件、离线资源、重要设置建议加入保护。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
        }

        SectionHeader("自动化", "本地触发规则")
        GlassPanel(onClick = onOpenAutomation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动任务", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    Text(
                        "时间、电量、充电、锁屏、Wi-Fi、开机触发；可让 AI 创建",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
            }
        }

        SectionHeader("AI 管家", "模型、语音识别与语音合成")
        GlassPanel(onClick = onOpenAiSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI 管家设置", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    Text(
                        "配置云端模型、系统语音识别与 TTS 引擎",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Glass.Faint)
            }
        }

        SectionHeader("应用图标", "切换后桌面可能需重启")
        GlassPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("猫耳", selected = iconStyle == "xi", color = Glass.Violet) {
                    iconStyle = "xi"; vm.prefs.iconStyle = "xi"; applyIcon(context, "IconXi")
                }
                Pill("红瞳", selected = iconStyle == "red", color = Glass.Rose) {
                    iconStyle = "red"; vm.prefs.iconStyle = "red"; applyIcon(context, "IconRed")
                }
                Pill("狐耳", selected = iconStyle == "fox", color = Glass.Mint) {
                    iconStyle = "fox"; vm.prefs.iconStyle = "fox"; applyIcon(context, "IconFox")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "红耳/狐耳为代码绘制的矢量图标；Android 不允许运行时替换应用抽屉图标。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("外观", "星夜 / 极简 / 自定义背景")
        GlassPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("星夜", selected = bgStyle == "stars", color = Glass.Violet) {
                    bgStyle = "stars"; vm.setBackground("stars")
                }
                Pill("极简", selected = bgStyle == "plain", color = Glass.Accent) {
                    bgStyle = "plain"; vm.setBackground("plain")
                }
                Pill("自定义图片", selected = bgStyle == "image", color = Glass.Mint) {
                    bgStyle = "image"
                    imagePicker.launch(arrayOf("image/*"))
                }
            }
            if (bgStyle == "image") {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "选择背景图片",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "自定义图片会以低透明度模糊背景显示；不申请相册权限，只读取你选中的这一张。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("特权与访问", "所有深度操作都走系统自带命令")
        GlassPanel {
            SettingRow(
                "Root",
                if (caps.root) "已授权，可写内核参数、iptables" else "未检测到 su",
                if (caps.root) "可用" else "不可用",
                caps.root,
            )
            DividerLine()
            SettingRow(
                "所有文件访问",
                if (caps.allFiles) "已授予 MANAGE_EXTERNAL_STORAGE" else "深度扫描需要此权限",
                if (caps.allFiles) "已授予" else "去开启",
                caps.allFiles,
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            }
            DividerLine()
            SettingRow(
                "使用情况访问",
                "应用缓存、耗电排行与前台时间",
                if (caps.usageAccess) "已授予" else "去开启",
                caps.usageAccess,
            ) {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
            DividerLine()
            SettingRow(
                "无障碍服务",
                "应用锁需要读取当前前台包名，不读屏幕内容",
                if (caps.accessibility) "已开启" else "去开启",
                caps.accessibility,
            ) {
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            DividerLine()
            SettingRow(
                "电池优化白名单",
                if (caps.batteryUnrestricted) "已加入白名单，不会被 Doze 冻结" else "未加入，后台任务可能被系统冻结",
                if (caps.batteryUnrestricted) "已加入" else "去设置",
                caps.batteryUnrestricted,
            ) {
                openFirst(
                    context,
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent().setComponent(
                        android.content.ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings\$HighPowerApplicationsActivity",
                        )
                    ),
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(
                "重新检测权限",
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.redetectPrivileges() },
            )
        }

        SectionHeader("无线 ADB", "用系统无线调试在机内配对")
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (adbConnected) "已连接 adbd" else "未连接",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (adbConnected) Glass.Mint else Glass.Text,
                    )
                    Text(
                        if (adbConnected) "深度操作会优先走无线 ADB" else "权限与 Root 类似，但由系统无线调试授权",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (adbBusy) "处理中" else if (adbConnected) "在线" else "离线",
                    selected = adbConnected,
                    color = if (adbConnected) Glass.Mint else Glass.Faint,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "步骤：开发者选项 → 无线调试 → 使用配对码配对设备，把屏幕上的地址、端口和 6 位配对码填到下面。配对成功后点「连接」。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "打开无线调试",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        openFirst(
                            context,
                            Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                        )
                    },
                )
                ActionButton(
                    if (adbExpanded) "收起配对" else "配对 / 连接",
                    accent = !adbConnected,
                    modifier = Modifier.weight(1f),
                    onClick = { adbExpanded = !adbExpanded },
                )
            }
            if (adbExpanded) {
                Spacer(Modifier.height(10.dp))
                AdbField("配对地址", adbHost, "127.0.0.1 或屏幕上显示的 IP") { adbHost = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        AdbField("配对端口", adbPort, "配对弹窗里的端口", numeric = true) { adbPort = it }
                    }
                    Box(Modifier.weight(1f)) {
                        AdbField("配对码", adbCode, "6 位数字", numeric = true) { adbCode = it }
                    }
                }
                Spacer(Modifier.height(6.dp))
                AdbField(
                    "连接端口（可选）",
                    adbConnectPort,
                    "无线调试首页 IP:端口 里的端口",
                    numeric = true,
                ) { adbConnectPort = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        "配对并连接",
                        accent = true,
                        enabled = !adbBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val port = adbPort.trim().toIntOrNull()
                            if (port == null || adbCode.trim().length < 6) {
                                Toast.makeText(context, "请填写正确的端口和 6 位配对码", Toast.LENGTH_SHORT).show()
                            } else {
                                adbBusy = true
                                adbMessage = "正在配对…"
                                scope.launch {
                                    val host = adbHost.trim().ifBlank { "127.0.0.1" }
                                    val pairError = withContext(Dispatchers.IO) {
                                        AdbManager.pairDevice(context, host, port, adbCode.trim())
                                    }
                                    if (pairError != null) {
                                        adbBusy = false
                                        val message = "配对失败：$pairError"
                                        adbMessage = message
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    adbMessage = "配对成功，正在连接…"
                                    val connectPort = adbConnectPort.trim().toIntOrNull()
                                    val connectError = withContext(Dispatchers.IO) {
                                        if (connectPort != null) {
                                            AdbManager.connectDirect(context, host, connectPort)
                                        } else {
                                            AdbManager.connectDevice(context, 8000)
                                        }
                                    }
                                    adbBusy = false
                                    adbConnected = AdbManager.connected()
                                    if (connectError == null) {
                                        adbMessage = "配对并连接成功"
                                        Toast.makeText(context, "配对并连接成功", Toast.LENGTH_SHORT).show()
                                        vm.refreshCapabilities(force = true)
                                        adbExpanded = false
                                    } else {
                                        val message = "配对成功，但连接失败：$connectError。请关闭系统配对弹窗，把无线调试首页的端口填到「连接端口」，再点「只连接」。"
                                        adbMessage = message
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                    )
                    ActionButton(
                        "只连接",
                        enabled = !adbBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            adbBusy = true
                            adbMessage = "正在连接…"
                            scope.launch {
                                val host = adbHost.trim().ifBlank { "127.0.0.1" }
                                val connectPort = adbConnectPort.trim().toIntOrNull()
                                val error = withContext(Dispatchers.IO) {
                                    if (connectPort != null) {
                                        AdbManager.connectDirect(context, host, connectPort)
                                    } else {
                                        AdbManager.connectDevice(context, 8000)
                                    }
                                }
                                adbBusy = false
                                adbConnected = AdbManager.connected()
                                if (error == null) {
                                    adbMessage = "无线 ADB 已连接"
                                    Toast.makeText(context, "无线 ADB 已连接", Toast.LENGTH_SHORT).show()
                                    vm.refreshCapabilities(force = true)
                                } else {
                                    val message = "连接失败：$error"
                                    adbMessage = message
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "「配对端口」填配对弹窗里的端口；「连接端口」填无线调试首页「IP 地址和端口」里的端口，可留空由 mDNS 自动发现。配对成功后先关闭系统配对弹窗。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
                adbMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (adbConnected) Glass.Mint else Glass.Amber,
                    )
                }
            }
            if (adbConnected) {
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "测试无线 ADB",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        adbBusy = true
                        adbMessage = "正在测试…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { Shell.run("id", 6_000) }
                            adbBusy = false
                            adbMessage = if (result.ok) {
                                "ADB 正常：${result.text.take(120)}"
                            } else {
                                "ADB 测试失败：${result.text.take(180)}"
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "断开无线 ADB",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AdbManager.disconnectDevice()
                        adbConnected = false
                        vm.refreshCapabilities(force = true)
                    },
                )
            }
        }

        SectionHeader("应用锁", "本地 PIN · 不上传")
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (lockEnabled) "已开启" else "未开启",
                        style = MaterialTheme.typography.titleMedium,
                        color = Glass.Text,
                    )
                    Text(
                        if (vm.prefs.hasPin()) "已设置 PIN" else "需要先设置 PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (lockEnabled) "关闭" else "开启",
                    selected = lockEnabled,
                    color = Glass.Mint,
                    onClick = {
                        if (lockEnabled) {
                            vm.prefs.appLockEnabled = false
                            lockEnabled = false
                        } else if (vm.prefs.hasPin()) {
                            vm.prefs.appLockEnabled = true
                            lockEnabled = true
                        } else {
                            pinDialog = true
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    if (vm.prefs.hasPin()) "修改 PIN" else "设置 PIN",
                    modifier = Modifier.weight(1f),
                    onClick = { pinDialog = true },
                )
                ActionButton(
                    "清除 PIN",
                    modifier = Modifier.weight(1f),
                    enabled = vm.prefs.hasPin(),
                    onClick = { vm.prefs.clearPin(); lockEnabled = false },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "在应用详情里把应用加入锁列表。开启后，切到该应用会显示密码遮罩。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("防火墙", "Root + 内核 netfilter")
        GlassPanel {
            SettingRow(
                "开机重新应用规则",
                "只恢复你保存过的 UID 列表",
                if (vm.prefs.firewallPersist) "开" else "关",
                vm.prefs.firewallPersist,
            ) {
                vm.prefs.firewallPersist = !vm.prefs.firewallPersist
            }
            DividerLine()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "清空所有规则",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.clearFirewall() },
                )
            }
        }

        SectionHeader("关于", "汐 · Solid Glass 风格")
        GlassPanel {
            Text(
                "包名：${BuildConfig.APPLICATION_ID}\n版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "接口依据：LineageOS 24.0 的 lineagehealth（充电控制/快充）、LineageSettings、AppOps、StorageStatsManager、UsageStatsManager、NetworkStats、PackageManager、VpnService、iptables。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本应用不包含广告、不上传文件、不做云同步。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pinDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { pinDialog = false },
        ) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), opaque = true, padding = PaddingValues(18.dp)) {
                Text(if (vm.prefs.hasPin()) "修改 PIN" else "设置 PIN", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Spacer(Modifier.height(10.dp))
                GlassField("新 PIN（4–8 位数字）", pinInput, "••••", password = true, number = true) {
                    pinInput = it.filter { c -> c.isDigit() }.take(8)
                }
                Spacer(Modifier.height(8.dp))
                GlassField("再输一次", pinConfirm, "••••", password = true, number = true) {
                    pinConfirm = it.filter { c -> c.isDigit() }.take(8)
                }
                pinError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Glass.Rose)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("取消", modifier = Modifier.weight(1f), onClick = { pinDialog = false })
                    ActionButton(
                        "保存",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                pinInput.length < 4 -> pinError = "至少 4 位"
                                pinInput != pinConfirm -> pinError = "两次输入不一致"
                                else -> {
                                    vm.prefs.setPin(pinInput)
                                    vm.prefs.appLockEnabled = true
                                    lockEnabled = true
                                    pinInput = ""; pinConfirm = ""; pinError = null; pinDialog = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun applyIcon(context: android.content.Context, alias: String) {
    val pm = context.packageManager
    val names = listOf("IconXi", "IconRed", "IconFox")
    val chosen = ComponentName(context.packageName, "${context.packageName}.$alias")
    runCatching {
        pm.setComponentEnabledSetting(
            chosen,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        names.filter { it != alias }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.$it"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Toast.makeText(context, "图标已切换，桌面可能需要刷新", Toast.LENGTH_SHORT).show()
    }
}

private fun localWifiIp(context: android.content.Context): String? = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    for (network in cm.allNetworks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
        val props = cm.getLinkProperties(network) ?: continue
        val ip = props.linkAddresses
            .mapNotNull { it.address.hostAddress }
            .firstOrNull { it.contains('.') && !it.startsWith("127.") && !it.startsWith("169.254.") }
        if (ip != null) return@runCatching ip
    }
    null
}.getOrNull()

private fun openFirst(context: android.content.Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // try the next fallback
        }
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

@Composable
private fun AdbField(
    label: String,
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x07FFFFFF))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Glass.Text),
                cursorBrush = SolidColor(Glass.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    action: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
        }
        Pill(
            action,
            selected = active,
            color = if (active) Glass.Mint else Glass.Accent,
            onClick = onClick,
        )
    }
}

@Composable
private fun DividerLine() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0AFFFFFF)))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GlassField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    number: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x07FFFFFF))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Glass.Text),
                cursorBrush = SolidColor(Glass.Accent),
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        number -> KeyboardType.Number
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
