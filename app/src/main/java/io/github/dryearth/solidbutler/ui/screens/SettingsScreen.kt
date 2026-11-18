package io.github.dryearth.solidbutler.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import io.github.dryearth.solidbutler.core.Capabilities
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
    onChat: () -> Unit,
    onOpenLineage: () -> Unit,
) {
    val context = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    ToastEffect(chat.toast) { vm.clearChatToast() }

    var base by rememberSaveable { mutableStateOf(vm.prefs.aiBaseUrl) }
    var model by rememberSaveable { mutableStateOf(vm.prefs.aiModel) }
    var key by rememberSaveable { mutableStateOf(vm.prefs.aiApiKey) }
    var lockEnabled by remember { mutableStateOf(vm.prefs.appLockEnabled) }
    var pinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

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

        SectionHeader("LineageOS 接口", "直接读写系统服务")
        GlassPanel(onClick = onOpenLineage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("硬件与显示特性", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                    Text(
                        "lineagehardware 支持的触控、背光、色彩、防闪烁等开关",
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

        SectionHeader("AI 管家", "云端 OpenAI 兼容接口 · 不填写则完全离线")
        GlassPanel {
            GlassField("接口地址", base, "https://api.openai.com/v1") { base = it }
            Spacer(Modifier.height(10.dp))
            GlassField("模型名", model, "gpt-4o-mini") { model = it }
            Spacer(Modifier.height(10.dp))
            GlassField("API Key", key, "sk-…", password = true) { key = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "保存",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.saveAiConfig(base, key, model)
                    },
                )
                ActionButton(
                    "打开对话",
                    modifier = Modifier.weight(1f),
                    onClick = onChat,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "只发送你输入的文字与设备摘要。API Key 存在应用私有目录，不写日志。",
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
                "Shizuku",
                when {
                    caps.shizukuReady -> "已授权，以 shell 身份执行官方命令"
                    caps.shizukuInstalled -> "已安装，尚未授权"
                    else -> "未安装"
                },
                if (caps.shizukuReady) "可用" else "去授权",
                caps.shizukuReady,
            ) {
                if (caps.shizukuInstalled && !caps.shizukuReady) vm.requestShizuku()
            }
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
                "避免汐被系统冻结",
                "去设置",
                false,
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(
                "重新检测权限",
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.refreshCapabilities() },
            )
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
        androidx.compose.ui.window.Dialog(onDismissRequest = { pinDialog = false }) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
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
                        number -> KeyboardType.NumberPassword
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
