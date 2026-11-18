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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.data.ActionState
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.data.ChatMessage
import io.github.dryearth.solidbutler.data.ChatRole
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass

@Composable
fun AssistantScreen(vm: ButlerViewModel, onClose: () -> Unit, onSettings: () -> Unit) {
    val state by vm.chat.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    ToastEffect(state.toast) { vm.clearChatToast() }

    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                Text("汐", style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Text(
                    if (state.modelConfigured) state.modelLabel else "云端模型未配置",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.modelConfigured) Glass.Mint else Glass.Amber,
                    maxLines = 1,
                )
            }
            if (state.messages.isNotEmpty()) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "清空",
                    tint = Glass.Faint,
                    modifier = Modifier.size(20.dp).clickable { vm.clearChat() },
                )
            }
        }

        if (!state.modelConfigured) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                GlassPanel(tint = Glass.Violet) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Glass.Violet, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("先接一个云端模型", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                            Text(
                                "支持任意 OpenAI 兼容接口。只发送对话文字与设备摘要，不上传文件。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Glass.Muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        "去设置",
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSettings,
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    GlassPanel(padding = PaddingValues(18.dp)) {
                        Text(
                            "我是汐。可以帮你分析存储、盘点后台、设置充电上限、审计权限。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Glass.Text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "所有操作都会先给你看命令与影响，再确认执行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Suggestion("看看存储里有什么可以清理的") { vm.sendChat(it) }
                        Spacer(Modifier.height(8.dp))
                        Suggestion("哪些应用在后台偷跑？") { vm.sendChat(it) }
                        Spacer(Modifier.height(8.dp))
                        Suggestion("把充电限制到 80%") { vm.sendChat(it) }
                        Spacer(Modifier.height(8.dp))
                        Suggestion("帮我找出高风险应用") { vm.sendChat(it) }
                    }
                }
            }
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(vm, msg)
            }
            if (state.sending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Glass.Accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("汐正在思考…", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                    }
                }
            }
        }

        Composer(
            value = input,
            enabled = state.modelConfigured && !state.sending,
            onValueChange = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    input = ""
                    vm.sendChat(text)
                }
            },
        )
    }
}

@Composable
private fun Suggestion(text: String, onClick: (String) -> Unit) {
    GlassPanel(
        corner = 14.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        onClick = { onClick(text) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Glass.Text, modifier = Modifier.weight(1f))
            Text("→", style = MaterialTheme.typography.bodyMedium, color = Glass.Accent)
        }
    }
}

@Composable
private fun MessageBubble(vm: ButlerViewModel, msg: ChatMessage) {
    val isUser = msg.role == ChatRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Glass.Accent.copy(alpha = 0.16f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
            }
        } else {
            Column(Modifier.fillMaxWidth(0.94f)) {
                GlassPanel(corner = 16.dp, padding = PaddingValues(14.dp)) {
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                    msg.action?.let { action ->
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x08FFFFFF))
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill("待确认", selected = true, color = Glass.Amber)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    action.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Glass.Text,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (action.detail.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    action.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.Muted,
                                )
                            }
                            when (msg.actionState) {
                                ActionState.PENDING -> {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ActionButton(
                                            "取消",
                                            modifier = Modifier.weight(1f),
                                            onClick = { vm.rejectAction(msg.id) },
                                        )
                                        ActionButton(
                                            "执行",
                                            accent = true,
                                            modifier = Modifier.weight(1f),
                                            onClick = { vm.confirmAction(msg.id) },
                                        )
                                    }
                                }
                                ActionState.RUNNING -> {
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = Glass.Accent,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("执行中…", style = MaterialTheme.typography.labelSmall, color = Glass.Muted)
                                    }
                                }
                                ActionState.DONE, ActionState.FAILED, ActionState.REJECTED -> {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        msg.actionResult ?: "已处理",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (msg.actionState) {
                                            ActionState.DONE -> Glass.Mint
                                            ActionState.FAILED -> Glass.Rose
                                            else -> Glass.Faint
                                        },
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        GlassPanel(
            corner = 20.dp,
            padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            if (enabled) "让汐做什么…" else "配置模型后可用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Glass.Faint,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = Glass.Text,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                        cursorBrush = SolidColor(Glass.Accent),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (enabled && value.isNotBlank()) Glass.Accent else Color(0x10FFFFFF))
                        .clickable(enabled = enabled && value.isNotBlank()) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (enabled && value.isNotBlank()) Color(0xFF0B1220) else Glass.Faint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
