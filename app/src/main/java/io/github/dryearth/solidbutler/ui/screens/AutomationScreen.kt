package io.github.dryearth.solidbutler.ui.screens

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
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.dryearth.solidbutler.data.AutomationRepository
import io.github.dryearth.solidbutler.data.AutomationRule
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.EmptyHint
import io.github.dryearth.solidbutler.ui.components.GlassBackButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.SectionHeader
import io.github.dryearth.solidbutler.ui.theme.Glass

private val triggers = listOf(
    "time" to "每天时间",
    "battery_low" to "电量低于",
    "battery_full" to "电量达到",
    "charging_start" to "开始充电",
    "screen_off" to "锁屏时",
    "wifi_on" to "连接 Wi-Fi",
    "wifi_off" to "断开 Wi-Fi",
    "boot" to "开机后",
)

private val actions = listOf(
    "speak" to "语音播报",
    "notify" to "通知",
    "charge_limit" to "充电上限",
    "restrict_app" to "限制后台",
    "standby" to "待机受限",
    "freeze" to "冻结应用",
    "lineage_feature" to "Lineage 特性",
    "brightness" to "亮度",
    "ringer" to "响铃模式",
)

@Composable
fun AutomationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AutomationRepository(context) }
    var rules by remember { mutableStateOf(repo.all()) }
    var editor by remember { mutableStateOf<AutomationRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun refresh() { rules = repo.all() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = { onBack() })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("自动化", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("本地规则 · 不需要云端", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            ActionButton("新建", Icons.Rounded.AutoMode, accent = true) {
                creating = true
                editor = null
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                GlassPanel(tint = Glass.Violet, corner = 14.dp, padding = PaddingValues(12.dp)) {
                    Text("模板", style = MaterialTheme.typography.labelSmall, color = Glass.Violet)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("25% 语音", color = Glass.Amber) {
                            repo.add(AutomationRule(name = "低电量语音", trigger = "battery_low", triggerValue = "25", action = "speak", actionValue = "电量低于25%，请充电"))
                            refresh()
                        }
                        Pill("95% 语音", color = Glass.Mint) {
                            repo.add(AutomationRule(name = "充满语音", trigger = "battery_full", triggerValue = "95", action = "speak", actionValue = "电量已充到95%，可以拔掉充电器了"))
                            refresh()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("80% 限充", color = Glass.Accent) {
                            repo.add(AutomationRule(name = "80% 自动限充", trigger = "battery_full", triggerValue = "80", action = "charge_limit", actionValue = "80"))
                            refresh()
                        }
                        Pill("23:00 静音", color = Glass.Ice) {
                            repo.add(AutomationRule(name = "夜间静音", trigger = "time", triggerValue = "23:00", action = "ringer", actionValue = "silent"))
                            refresh()
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item { EmptyHint("还没有规则。点右上角新建，或直接让 AI 管家创建。") }
            } else {
                items(rules, key = { it.id }) { rule ->
                    AutomationCard(rule, onToggle = {
                        repo.setEnabled(rule.id, it)
                        refresh()
                    }, onEdit = { editor = rule; creating = false }, onDelete = {
                        repo.delete(rule.id)
                        refresh()
                    })
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (creating || editor != null) {
        val initial = editor ?: AutomationRule(name = "", trigger = "time", action = "speak")
        RuleEditor(initial = initial, onDismiss = { creating = false; editor = null }, onSave = { rule ->
            if (editor == null) repo.add(rule) else repo.update(rule)
            creating = false; editor = null; refresh()
        })
    }
}

@Composable
private fun AutomationCard(rule: AutomationRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    GlassPanel(corner = 15.dp, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onEdit() }) {
                Text(
                    rule.name.ifBlank { "未命名规则" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Glass.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    triggerLabel(rule),
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Muted,
                    maxLines = 1,
                )
                Text(
                    actionLabel(rule),
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Faint,
                    maxLines = 1,
                )
            }
            Pill(
                if (rule.enabled) "开" else "关",
                selected = rule.enabled,
                color = if (rule.enabled) Glass.Mint else Glass.Faint,
                onClick = { onToggle(!rule.enabled) },
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "删除",
                tint = Glass.Faint,
                modifier = Modifier.size(18.dp).clickable { onDelete() },
            )
        }
    }
}

@Composable
private fun RuleEditor(
    initial: AutomationRule,
    onDismiss: () -> Unit,
    onSave: (AutomationRule) -> Unit,
) {
    var rule by remember(initial.id) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val triggerNeedsValue = rule.trigger in setOf("time", "battery_low", "battery_full")
    val actionNeedsValue = rule.action in setOf(
        "speak", "notify", "charge_limit", "restrict_app", "standby", "freeze",
        "lineage_feature", "brightness", "ringer",
    )

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            opaque = true,
            padding = PaddingValues(16.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (initial.name.isBlank()) "新建自动化" else "编辑自动化", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Spacer(Modifier.height(10.dp))
                AutoField("名称", rule.name, "例如：睡前静音") { rule = rule.copy(name = it) }

                Spacer(Modifier.height(10.dp))
                Text("触发条件", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(5.dp))
                Options(triggers, rule.trigger) { rule = rule.copy(trigger = it, triggerValue = "") }
                if (triggerNeedsValue) {
                    Spacer(Modifier.height(6.dp))
                    AutoField(
                        if (rule.trigger == "time") "时间 HH:mm" else "电量百分比",
                        rule.triggerValue,
                        if (rule.trigger == "time") "23:00" else "25",
                    ) { rule = rule.copy(triggerValue = it) }
                }

                Spacer(Modifier.height(10.dp))
                Text("执行动作", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(5.dp))
                Options(actions, rule.action) { rule = rule.copy(action = it, actionValue = "") }
                if (actionNeedsValue) {
                    Spacer(Modifier.height(6.dp))
                    AutoField(
                        actionHint(rule.action),
                        rule.actionValue,
                        actionPlaceholder(rule.action),
                    ) { rule = rule.copy(actionValue = it) }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Glass.Rose)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("取消", modifier = Modifier.weight(1f), onClick = onDismiss)
                    ActionButton(
                        "保存",
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                rule.name.isBlank() -> error = "请填写名称"
                                rule.trigger == "time" && !Regex("""\d{2}:\d{2}""").matches(rule.triggerValue) ->
                                    error = "时间格式应为 HH:mm"
                                rule.trigger in setOf("battery_low", "battery_full") &&
                                    rule.triggerValue.toIntOrNull() !in 1..100 -> error = "电量应为 1–100"
                                rule.action == "speak" && rule.actionValue.isBlank() -> error = "请填写播报内容"
                                rule.action == "notify" && rule.actionValue.isBlank() -> error = "请填写通知内容"
                                rule.action == "charge_limit" && rule.actionValue.toIntOrNull() !in 70..100 ->
                                    error = "充电上限应为 70–100"
                                rule.action in setOf("restrict_app", "standby", "freeze") && rule.actionValue.isBlank() ->
                                    error = "请填写应用名或包名"
                                else -> onSave(rule)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Options(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { (id, label) ->
                    Pill(label, selected = selected == id, color = Glass.Accent) { onPick(id) }
                }
            }
        }
    }
}

@Composable
private fun AutoField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Color(0x07FFFFFF))
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun triggerLabel(rule: AutomationRule): String {
    val name = triggers.firstOrNull { it.first == rule.trigger }?.second ?: rule.trigger
    return when (rule.trigger) {
        "time" -> "每天 ${rule.triggerValue.ifBlank { "--:--" }}"
        "battery_low" -> "电量 ≤ ${rule.triggerValue}%"
        "battery_full" -> "电量 ≥ ${rule.triggerValue}%"
        else -> name
    }
}

private fun actionLabel(rule: AutomationRule): String {
    val name = actions.firstOrNull { it.first == rule.action }?.second ?: rule.action
    return if (rule.actionValue.isBlank()) name else "$name · ${rule.actionValue}"
}

private fun actionHint(action: String): String = when (action) {
    "speak", "notify" -> "内容"
    "charge_limit" -> "上限 70–100"
    "restrict_app", "standby", "freeze" -> "应用名或包名"
    "lineage_feature" -> "特性名:true/false"
    "brightness" -> "亮度 1–100"
    "ringer" -> "normal/silent/vibrate"
    else -> "参数"
}

private fun actionPlaceholder(action: String): String = when (action) {
    "speak" -> "例如：电量偏低，记得充电"
    "notify" -> "例如：该休息了"
    "charge_limit" -> "80"
    "restrict_app", "standby", "freeze" -> "例如：抖音"
    "lineage_feature" -> "例如：防闪烁:true"
    "brightness" -> "40"
    "ringer" -> "silent"
    else -> ""
}
