package io.github.dryearth.solidbutler.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dryearth.solidbutler.data.ActionState
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.data.ChatMessage
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.TtsDefaults
import io.github.dryearth.solidbutler.data.ChatRole
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.components.ToastEffect
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun AssistantScreen(
    vm: ButlerViewModel,
    autoListen: Boolean = false,
    onAutoListenConsumed: () -> Unit = {},
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.chat.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var input by rememberSaveable { mutableStateOf("") }
    var speakReplies by rememberSaveable { mutableStateOf(prefs.aiSpeakReplies) }
    var pendingImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingBase64 by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val pair = withContext(Dispatchers.IO) { encodeImage(context, uri) }
                pendingImage = pair.first
                pendingBase64 = pair.second
            }
        }
    }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    val listState = rememberLazyListState()
    var lastSpokenId by remember { mutableStateOf(state.messages.lastOrNull()?.id ?: 0L) }

    ToastEffect(state.toast) { vm.clearChatToast() }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(
            context,
            { status -> ttsReady = status == TextToSpeech.SUCCESS },
            prefs.ttsEngine.takeIf { it.isNotBlank() },
        )
        ttsEngine = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsEngine = null
        }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            ttsEngine?.let { TtsDefaults.apply(context, it) }
        }
    }
    LaunchedEffect(state.messages.size, speakReplies, ttsReady) {
        if (!speakReplies || !ttsReady) return@LaunchedEffect
        val last = state.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.role == ChatRole.ASSISTANT && last.text.isNotBlank() && last.id != lastSpokenId) {
            lastSpokenId = last.id
            runCatching {
                ttsEngine?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "xi-${last.id}")
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                input = ""
                vm.sendChat(text)
            }
        }
    }

    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说吧，汐在听")
            if (prefs.sttPackage.isNotBlank()) setPackage(prefs.sttPackage)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "系统没有语音识别服务", Toast.LENGTH_SHORT).show()
            return
        }
        speechLauncher.launch(intent)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSpeech() else {
            Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchSpeech()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(autoListen) {
        if (autoListen) {
            onAutoListenConsumed()
            requestMic()
        }
    }

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
                Text(prefs.personaName, style = MaterialTheme.typography.titleLarge, color = Glass.Text)
                Text(
                    if (state.modelConfigured) state.modelLabel else "云端模型未配置",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.modelConfigured) Glass.Mint else Glass.Amber,
                    maxLines = 1,
                )
            }
            Icon(
                if (speakReplies) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                contentDescription = "语音播报",
                tint = if (speakReplies) Glass.Accent else Glass.Faint,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        speakReplies = !speakReplies
                        prefs.aiSpeakReplies = speakReplies
                        if (!speakReplies) ttsEngine?.stop()
                    },
            )
            Spacer(Modifier.width(12.dp))
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
                            "我是${prefs.personaName}。可以帮你分析存储、盘点后台、设置充电上限、审计权限，也可以看图。",
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
                        Text("${prefs.personaName}正在思考…", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                    }
                }
            }
        }

        Composer(
            value = input,
            enabled = state.modelConfigured && !state.sending,
            assistantName = prefs.personaName,
            imagePreview = pendingImage,
            onValueChange = { input = it },
            onPickImage = { imagePicker.launch("image/*") },
            onRemoveImage = { pendingImage = null; pendingBase64 = null },
            onMic = { requestMic() },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty() || pendingBase64 != null) {
                    vm.sendChat(text, pendingBase64)
                    input = ""
                    pendingImage = null
                    pendingBase64 = null
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
                    val imageData = msg.imageBase64
                    if (imageData != null) {
                        val bmp = remember(imageData) {
                            runCatching {
                                val bytes = Base64.decode(imageData, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(
                                bmp,
                                contentDescription = "图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
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
    assistantName: String,
    imagePreview: ImageBitmap?,
    onValueChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onMic: () -> Unit,
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
            if (imagePreview != null) {
                Box(Modifier.padding(bottom = 8.dp)) {
                    Image(
                        imagePreview,
                        contentDescription = "待发送图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(96.dp).height(96.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                            .clip(CircleShape).background(Color(0xAA000000))
                            .clickable { onRemoveImage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            if (enabled) "让${assistantName}做什么…" else "配置模型后可用",
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
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x0FFFFFFF))
                        .clickable(enabled = enabled) { onPickImage() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "发送图片",
                        tint = if (enabled) Glass.Accent else Glass.Faint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x0FFFFFFF))
                        .clickable(enabled = enabled) { onMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = "语音输入",
                        tint = if (enabled) Glass.Accent else Glass.Faint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (enabled && (value.isNotBlank() || imagePreview != null)) Glass.Accent else Color(0x10FFFFFF))
                        .clickable(enabled = enabled && (value.isNotBlank() || imagePreview != null)) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (enabled && (value.isNotBlank() || imagePreview != null)) Color(0xFF0B1220) else Glass.Faint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}


private fun encodeImage(context: android.content.Context, uri: Uri): Pair<ImageBitmap?, String?> {
    return runCatching {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null to null
        val max = 1024
        val scale = minOf(1f, max.toFloat() / maxOf(original.width, original.height))
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else original
        val out = ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
        val bytes = out.toByteArray()
        (scaled.asImageBitmap()) to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrDefault(null to null)
}
