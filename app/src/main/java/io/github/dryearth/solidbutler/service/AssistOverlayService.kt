package io.github.dryearth.solidbutler.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.dryearth.solidbutler.data.ActionState
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.MainActivity
import io.github.dryearth.solidbutler.data.ChatMessage
import io.github.dryearth.solidbutler.data.ChatRole
import io.github.dryearth.solidbutler.ui.theme.SolidButlerTheme

/**
 * Floating assistant. The window is a real application overlay, so it can
 * appear above another app instead of replacing the current screen.
 *
 * Everything is drawn inside a compact liquid-glass panel; the window asks
 * the system for cross-window background blur on Android 12+.
 */
class AssistOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val vm by lazy { ButlerViewModel(application) }
    private var windowManager: WindowManager? = null
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        createChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val type = if (mic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification(), type)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        showWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (view == null) showWindow()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun showWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AssistOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AssistOverlayService)
            setViewTreeViewModelStoreOwner(this@AssistOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SolidButlerTheme {
                    OverlayAssistant(
                        vm = vm,
                        onClose = { stopSelf() },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = (20 * resources.displayMetrics.density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = 70
        }
        runCatching { wm.addView(compose, params) }
        view = compose
        compose.requestFocus()
        compose.post {
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(compose, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun removeWindow() {
        val v = view ?: return
        runCatching { windowManager?.removeView(v) }
        view = null
    }

    private fun openFullChat() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("open_chat", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        stopSelf()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮助手", NotificationManager.IMPORTANCE_LOW).apply {
                description = "数字助理悬浮窗"
                setShowBadge(false)
            }
        )
    }

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("汐 · 悬浮助手")
            .setContentText("数字助理悬浮窗正在运行")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "xi_assist_overlay"
        private const val NOTIFICATION_ID = 12003

        fun show(context: android.content.Context): Boolean = runCatching {
            val intent = Intent(context, AssistOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun OverlayAssistant(
    vm: ButlerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val state by vm.chat.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val shape = RoundedCornerShape(30.dp)

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy(); recognizer = null }
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            voiceStatus = "麦克风未授权，请先在完整对话里授权"
            Toast.makeText(context, voiceStatus, Toast.LENGTH_LONG).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceStatus = "系统没有语音识别服务"
            Toast.makeText(context, voiceStatus, Toast.LENGTH_LONG).show()
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    voiceStatus = "正在聆听…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    listening = false
                    voiceStatus = "正在识别…"
                }
                override fun onError(error: Int) {
                    listening = false
                    voiceStatus = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，再试一次"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
                        else -> "识别失败：$error"
                    }
                    Toast.makeText(context, voiceStatus, Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        input = text
                        if (vm.chat.value.modelConfigured) {
                            input = ""
                            voiceStatus = null
                            vm.sendChat(text)
                        } else {
                            voiceStatus = "已识别，填入输入框；配置模型后可直接发送"
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { input = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer = r
            r
        }
        listening = !listening
        if (!listening) {
            engine.stopListening()
            voiceStatus = null
            try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 50).apply {
                    startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ release() }, 250)
                }
            } catch (_: Exception) {}
            return
        }
        voiceStatus = "正在开始语音…"
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 50).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ release() }, 250)
            }
        } catch (_: Exception) {}
        engine.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说吧")
            }
        )
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) Modifier.height(maxHeight * 0.92f)
                    else Modifier.heightIn(max = maxHeight * 0.78f)
                )
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xE6FFFFFF),
                            Color(0xB8F4F8FF),
                            Color(0x99E7EEFF),
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFFFF), Color(0x66FFFFFF), Color(0x33FFFFFF))
                    ),
                    shape,
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Color(0x220B1220)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF33406A), modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.prefs.personaName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF0B1220),
                    )
                    Text(
                        if (state.modelConfigured) "悬浮助手 · 直接对话" else "悬浮助手 · 尚未配置模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x990B1220),
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Color(0x990B1220),
                    modifier = Modifier.size(19.dp).clickable { expanded = !expanded },
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Color(0x990B1220),
                    modifier = Modifier.size(19.dp).clickable { onClose() },
                )
            }

            if (state.messages.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .then(if (expanded) Modifier.weight(1f) else Modifier.heightIn(max = 330.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages.takeLast(8), key = { it.id }) { msg ->
                        OverlayBubble(vm, msg)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "直接说或输入。要语音、操作确认和完整历史时点右上角展开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x990B1220),
                )
            }

            voiceStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (listening) Color(0xFFD85B5B) else Color(0x990B1220),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(Color(0x330B1220))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (input.isEmpty()) {
                        Text("问汐…", style = MaterialTheme.typography.bodySmall, color = Color(0x660B1220))
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = Color(0xFF0B1220), fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF33406A)),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (listening) Color(0x33D85B5B) else Color(0x220B1220))
                        .border(
                            width = if (listening) 1.5.dp else 0.dp,
                            color = if (listening) Color(0x99D85B5B) else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { startListening() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = "语音输入",
                        tint = if (listening) Color(0xFFD85B5B) else Color(0x990B1220),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (input.isBlank()) Color(0x220B1220) else Color(0xFF0B1220))
                        .clickable(enabled = input.isNotBlank()) {
                            val text = input.trim()
                            input = ""
                            if (text.isNotEmpty()) vm.sendChat(text)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (input.isBlank()) Color(0x660B1220) else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "点展开进入完整对话 / 语音",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x660B1220),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF33406A),
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
private fun OverlayBubble(vm: ButlerViewModel, msg: ChatMessage) {
    val isUser = msg.role == ChatRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isUser) Color(0x1F0B1220) else Color(0x12FFFFFF))
                .padding(10.dp),
        ) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0B1220),
            )
            val action = msg.action
            if (action != null && msg.actionState == ActionState.PENDING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    action.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF33406A),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B1220),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x220B1220))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable { vm.confirmAction(msg.id) },
                    )
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x990B1220),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x110B1220))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable { vm.rejectAction(msg.id) },
                    )
                }
            }
        }
    }
}
