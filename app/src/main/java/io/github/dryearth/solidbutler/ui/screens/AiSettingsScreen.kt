package io.github.dryearth.solidbutler.ui.screens

import android.content.ComponentName
import android.media.RingtoneManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import io.github.dryearth.solidbutler.service.AssistOverlayService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.ai.AiClient
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
    var greetings by rememberSaveable { mutableStateOf(vm.prefs.wakeGreetings) }
    var wakeSound by rememberSaveable { mutableStateOf(vm.prefs.wakeSoundUri) }
    var exitSound by rememberSaveable { mutableStateOf(vm.prefs.exitSoundUri) }
    var soundTarget by remember { mutableStateOf("wake") }
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val value = uri?.toString() ?: ""
            if (soundTarget == "wake") wakeSound = value else exitSound = value
        }
    }
    fun pickSound(target: String, existing: String) {
        soundTarget = target
        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            if (existing.isNotBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
            }
        }
        runCatching { soundPicker.launch(picker) }
    }
    var personaName by rememberSaveable { mutableStateOf(vm.prefs.personaName) }
    var personaPrompt by rememberSaveable { mutableStateOf(vm.prefs.personaPrompt) }
    var userTitle by rememberSaveable { mutableStateOf(vm.prefs.userTitle) }
    var picker by remember { mutableStateOf<String?>(null) }
    var overlayFirst by remember { mutableStateOf(vm.prefs.overlayAssistant) }
    val aiClient = remember { AiClient(vm.prefs) }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(vm.prefs.aiThinking) }
    var effort by remember { mutableStateOf(vm.prefs.aiEffort) }

    fun openAssistantSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .putExtra("android.provider.extra.DEFAULT_APPS_CATEGORY", "android.app.role.ASSISTANT"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$ManageAssistActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DefaultAssistSettingsActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$ManageDefaultAppsSettingsActivity")),
            Intent("com.android.settings.action.SETTINGS_SEARCH").putExtra("query", "数字助理"),
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
            Spacer(Modifier.height(6.dp))
            ActionButton(
                if (loadingModels) "拉取中…" else "拉取模型列表",
                modifier = Modifier.fillMaxWidth(),
                enabled = !loadingModels,
                onClick = {
                    if (base.isBlank() || key.isBlank()) {
                        Toast.makeText(context, "先填接口地址和 API Key", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            loadingModels = true
                            runCatching { aiClient.listModels(base, key) }
                                .onSuccess {
                                    models = it
                                    showModels = true
                                    if (it.isEmpty()) Toast.makeText(context, "接口没有返回模型", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "拉取失败", Toast.LENGTH_LONG).show()
                                }
                            loadingModels = false
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            AiField("API Key", key, "sk-…", password = true) { key = it }
            Spacer(Modifier.height(10.dp))
            val supportsThinking = listOf("o1", "o3", "o4", "gpt-5", "deepseek-r1", "qwq", "qwen3").any {
                model.contains(it, true)
            }
            val supportsEffort = listOf("o1", "o3", "o4", "gpt-5").any { model.contains(it, true) }
            if (supportsThinking || supportsEffort) {
                Text("思考与强度（按模型识别）", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Pill("自动", selected = thinking == "auto", color = Glass.Violet) { thinking = "auto" }
                    Pill("思考开", selected = thinking == "on", color = Glass.Violet) { thinking = "on" }
                    Pill("思考关", selected = thinking == "off", color = Glass.Violet) { thinking = "off" }
                }
                if (supportsEffort) {
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Pill("低", selected = effort == "low", color = Glass.Accent) { effort = "low" }
                        Pill("中", selected = effort == "medium", color = Glass.Accent) { effort = "medium" }
                        Pill("高", selected = effort == "high", color = Glass.Accent) { effort = "high" }
                    }
                }
            } else {
                Text(
                    "当前模型未识别到思考模式；仍可正常对话。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "文字和图片会发送到你配置的云端模型；图片需要模型支持视觉（如 gpt-4o）。Key 存在应用私有目录。",
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
            Spacer(Modifier.height(8.dp))
            EngineRow(
                title = "唤醒音效",
                value = soundLabel(context, wakeSound),
            ) { pickSound("wake", wakeSound) }
            Spacer(Modifier.height(8.dp))
            EngineRow(
                title = "退出音效",
                value = soundLabel(context, exitSound),
            ) { pickSound("exit", exitSound) }
            Spacer(Modifier.height(4.dp))
            Text(
                "从系统提示音里选择；留空则不播放。唤醒音效在悬浮助手出现时播放。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
            val marmalade = remember {
                runCatching { context.packageManager.getLaunchIntentForPackage("app.marmalade.stt") }.getOrNull()
            }
            if (marmalade != null) {
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    "打开 Marmalade",
                    accent = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { runCatching { context.startActivity(marmalade) } },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "使用 Marmalade STT：手动到「设置 → 系统 → 语言与地区 → 语音 → 语音输入」把它设为识别服务；汐会直接绑定它的 RecognitionService，不弹出识别面板。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )

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
            Spacer(Modifier.height(10.dp))
            AiField(
                "唤醒开场白",
                greetings,
                "我在\n有什么吩咐\n我在，需要我做什么",
                singleLine = false,
            ) { greetings = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "每行一句，唤醒时随机播放；留空则不播。语速、音调跟随系统 TTS。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "语音识别需要系统识别服务与麦克风权限；TTS 需要系统语音引擎。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("人设", "名字、性格与对你的称呼")
        GlassPanel {
            AiField("名字", personaName, "汐") { personaName = it }
            Spacer(Modifier.height(8.dp))
            AiField("称呼你", userTitle, "你") { userTitle = it }
            Spacer(Modifier.height(8.dp))
            AiField(
                "人设描述",
                personaPrompt,
                "例如：冷静、简短、像守岸人一样克制；不要卖萌",
                singleLine = false,
            ) { personaPrompt = it }
            Spacer(Modifier.height(6.dp))
            Text(
                "人设会注入系统提示词；不会改变应用名称和图标。",
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

        SectionHeader("系统数字助理", "在系统默认应用页手动选择汐")
        GlassPanel {
            Text(
                "汐使用 ACTION_ASSIST 的无界面入口唤起悬浮窗，不会把 App 主界面带到前台。更新后通常可以直接使用；如果系统提示助理不可用，重新选择一次汐即可。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                "打开系统默认应用页",
                modifier = Modifier.fillMaxWidth(),
                onClick = { openAssistantSettings() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "手动路径：设置 → 系统 → 语言与地区 → 语音 → 语音输入；数字助理在设置 → 应用 → 默认应用。不同 LineageOS 版本菜单名略有不同。",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }

        SectionHeader("悬浮助手", "我们自己的液态玻璃浮窗，默认关闭")
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
                        "开启后，按助理键会优先弹出汐的小窗；关闭时保持 0.7 的全屏对话，不影响助理选择。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (overlayFirst) "优先浮窗" else "全屏对话",
                    selected = overlayFirst,
                    color = if (overlayFirst) Glass.Mint else Glass.Accent,
                    onClick = {
                        if (!canOverlay) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            }
                        } else {
                            overlayFirst = !overlayFirst
                            vm.prefs.overlayAssistant = overlayFirst
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "开启悬浮窗权限",
                    modifier = Modifier.weight(1f),
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
                ActionButton(
                    "预览",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = AssistOverlayService.show(context)
                        Toast.makeText(
                            context,
                            if (ok) "悬浮助手已打开" else "启动失败，请检查悬浮窗权限",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }

        SectionHeader("会话", "清理当前对话与图片内存")
        GlassPanel {
            Text(
                "清理只删除当前聊天记录、图片和待确认动作，不影响模型、人设和语音设置。",
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                "清理会话",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.clearChat()
                    Toast.makeText(context, "会话已清理", Toast.LENGTH_SHORT).show()
                },
            )
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
                        vm.prefs.wakeGreetings = greetings
                        vm.prefs.wakeSoundUri = wakeSound
                        vm.prefs.exitSoundUri = exitSound
                        vm.savePersona(personaName, personaPrompt, userTitle)
                        vm.prefs.aiThinking = thinking
                        vm.prefs.aiEffort = effort
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

    if (showModels) {
        Dialog(onDismissRequest = { showModels = false }) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), opaque = true, padding = PaddingValues(16.dp)) {
                Text("选择模型", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(models, key = { it }) { id ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                model = id
                                showModels = false
                            }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (id == model) Pill("当前", selected = true, color = Glass.Mint)
                        }
                    }
                }
            }
        }
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
        Pill(
            if (value == "系统默认") "选择" else "更换",
            selected = true,
            color = Glass.Accent,
            onClick = onClick,
        )
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

private fun soundLabel(context: android.content.Context, uriString: String): String {
    if (uriString.isBlank()) return "未选择"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "系统音效"
}

@Composable
private fun AiField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    singleLine: Boolean = true,
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
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
