package io.github.dryearth.solidbutler.ui.screens

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

private data class EngineOption(val pkg: String, val label: String)

@Composable
fun AiSettingsScreen(
    vm: ButlerViewModel,
    onBack: () -> Unit,
    onChat: () -> Unit,
) {
    val context = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    ToastEffect(chat.toast) { vm.clearChatToast() }

    val pm = context.packageManager
    val sttOptions = remember {
        runCatching {
            pm.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
                .map { EngineOption(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
        }.getOrDefault(emptyList())
    }
    val ttsOptions = remember {
        runCatching {
            pm.queryIntentServices(Intent("android.intent.action.TTS_SERVICE"), 0)
                .map { EngineOption(it.serviceInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
        }.getOrDefault(emptyList())
    }

    var base by rememberSaveable { mutableStateOf(vm.prefs.aiBaseUrl) }
    var model by rememberSaveable { mutableStateOf(vm.prefs.aiModel) }
    var key by rememberSaveable { mutableStateOf(vm.prefs.aiApiKey) }
    var sttPkg by rememberSaveable { mutableStateOf(vm.prefs.sttPackage) }
    var ttsPkg by rememberSaveable { mutableStateOf(vm.prefs.ttsEngine) }
    var speak by rememberSaveable { mutableStateOf(vm.prefs.aiSpeakReplies) }
    var picker by remember { mutableStateOf<String?>(null) }
    val roleManager = context.getSystemService(RoleManager::class.java)
    val role = "android.app.role.ASSISTANT"
    val roleAvailable = runCatching { roleManager?.isRoleAvailable(role) == true }.getOrDefault(false)
    var roleVersion by remember { mutableIntStateOf(0) }
    val roleHeld = remember(roleVersion) {
        runCatching { roleManager?.isRoleHeld(role) == true }.getOrDefault(false)
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { roleVersion++ }

    fun requestAssistantRole() {
        if (roleAvailable && roleManager != null) {
            runCatching { roleLauncher.launch(roleManager.createRequestRoleIntent(role)) }
        } else {
            val intents = listOf(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    .putExtra("android.provider.extra.DEFAULT_APPS_CATEGORY", "android.app.role.ASSISTANT"),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                Intent().setComponent(
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$ManageAssistActivity",
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$DefaultAssistSettingsActivity",
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$ManageDefaultAppsSettingsActivity",
                    )
                ),
                Intent("com.android.settings.action.SETTINGS_SEARCH")
                    .putExtra("query", "数字助理"),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
            )
            for (i in intents) {
                try {
                    context.startActivity(i)
                    return
                } catch (_: Exception) {
                    // try next
                }
            }
        }
    }

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
                Modifier.size(34.dp).clip(CircleShape).background(Color(0x0FFFFFFF)).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AI 管家设置", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("模型、语音识别与语音合成", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
        }

        SectionHeader("模型", "OpenAI 兼容接口 · 不填写则离线")
        GlassPanel {
            AiField("接口地址", base, "https://api.openai.com/v1") { base = it }
            Spacer(Modifier.height(10.dp))
            AiField("模型名", model, "gpt-4o-mini") { model = it }
            Spacer(Modifier.height(10.dp))
            AiField("API Key", key, "sk-…", password = true) { key = it }
            Spacer(Modifier.height(8.dp))
            Text(
                "只发送对话文字与设备摘要，不上传文件。Key 存在应用私有目录。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("语音", "使用系统识别与系统 TTS")
        GlassPanel {
            EngineRow(
                title = "语音转文字",
                value = sttOptions.firstOrNull { it.pkg == sttPkg }?.label ?: "系统默认",
            ) { picker = "stt" }
            Spacer(Modifier.height(8.dp))
            EngineRow(
                title = "文字转语音",
                value = ttsOptions.firstOrNull { it.pkg == ttsPkg }?.label ?: "系统默认",
            ) { picker = "tts" }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "自动朗读回复",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Glass.Text,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    if (speak) "开" else "关",
                    selected = speak,
                    color = if (speak) Glass.Mint else Glass.Faint,
                    onClick = { speak = !speak },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "语音识别需要系统识别服务与麦克风权限；TTS 需要系统语音引擎。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("可调用工具", "模型可以直接执行或先征求确认")
        GlassPanel {
            Text(
                "设备状态、亮度、息屏时间、媒体音量、响铃/勿扰、打开系统设置、启动应用；" +
                    "以及原有的存储扫描/清理、冻结与后台限制、权限收回、充电上限、自启组件、待机分组、防火墙、域名过滤、LineageOS 硬件特性。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "涉及删除、冻结、断网的动作仍会先弹出确认卡片。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("系统数字助理", "长按 Home / 电源键唤起汐")
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (roleHeld) "汐当前是默认数字助理" else "尚未设为默认数字助理",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text,
                    )
                    Text(
                        if (roleAvailable) {
                            "通过系统 RoleManager 请求 ASSISTANT 角色"
                        } else {
                            "此 ROM 没有 ASSISTANT 角色；会打开系统的语音输入/默认应用设置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (roleHeld) "已设置" else "设置",
                    selected = roleHeld,
                    color = if (roleHeld) Glass.Mint else Glass.Accent,
                    onClick = { requestAssistantRole() },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "手动路径：设置 → 应用 → 默认应用 → 数字助理（部分 LineageOS 版本叫「助理与语音输入」）。如果按钮打不开，可在系统设置搜索「数字助理」。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("悬浮助手", "数字助理以小窗显示，不切走当前应用")
        GlassPanel {
            val canOverlay = Settings.canDrawOverlays(context)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (canOverlay) "悬浮窗权限已授予" else "需要「显示在其他应用上层」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Text,
                    )
                    Text(
                        "开启后，数字助理会以液态玻璃小窗浮在当前应用上方；关闭时退回全屏对话。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (canOverlay) "已开启" else "去开启",
                    selected = canOverlay,
                    color = if (canOverlay) Glass.Mint else Glass.Accent,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    },
                )
            }
        }

        GlassPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "保存",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.saveAiConfig(base, key, model)
                        vm.saveVoiceConfig(sttPkg, ttsPkg, speak)
                    },
                )
                ActionButton(
                    "打开对话",
                    modifier = Modifier.weight(1f),
                    onClick = onChat,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    picker?.let { type ->
        val options = if (type == "stt") sttOptions else ttsOptions
        Dialog(onDismissRequest = { picker = null }) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), opaque = true, padding = PaddingValues(16.dp)) {
                Text(
                    if (type == "stt") "选择语音识别引擎" else "选择语音合成引擎",
                    style = MaterialTheme.typography.titleLarge,
                    color = Glass.Text,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        EngineChoice("系统默认", "") {
                            if (type == "stt") sttPkg = "" else ttsPkg = ""
                            picker = null
                        }
                    }
                    items(options, key = { it.pkg }) { opt ->
                        EngineChoice(opt.label, opt.pkg) {
                            if (type == "stt") sttPkg = opt.pkg else ttsPkg = opt.pkg
                            picker = null
                        }
                    }
                }
                if (options.isEmpty()) {
                    Text(
                        "没有检测到可选引擎",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Faint,
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineRow(title: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Pill("选择", color = Glass.Accent, onClick = onClick)
    }
}

@Composable
private fun EngineChoice(label: String, pkg: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
            if (pkg.isNotBlank()) {
                Text(pkg, style = MaterialTheme.typography.labelSmall, color = Glass.Faint, maxLines = 1)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Glass.Faint)
    }
}

@Composable
private fun AiField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
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
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
