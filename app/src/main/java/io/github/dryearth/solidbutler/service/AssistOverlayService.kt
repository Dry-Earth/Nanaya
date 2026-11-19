package io.github.dryearth.solidbutler.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch

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
    private var receiver: android.content.BroadcastReceiver? = null
    private var voiceBadge: android.view.View? = null
    private var autoVoice by mutableStateOf(false)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        active = this
        registerVoiceReceiver()
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
        if (intent?.getBooleanExtra(EXTRA_VOICE, false) == true) autoVoice = true
        if (view == null) showWindow()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { receiver?.let { unregisterReceiver(it) } }
        if (active === this) active = null
        removeVoiceBadge()
        removeWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun registerVoiceReceiver() {
        receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    ACTION_VOICE_START -> showVoiceBadge()
                    ACTION_VOICE_END -> removeVoiceBadge()
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_VOICE_START)
            addAction(ACTION_VOICE_END)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun showVoiceBadge() {
        if (!Settings.canDrawOverlays(this) || voiceBadge != null) return
        val density = resources.displayMetrics.density
        val text = android.widget.TextView(this).apply {
            this.text = "汐在听…"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#CC0B1220"))
                cornerRadius = 22 * density
                setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#55FFFFFF"))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (78 * density).toInt()
        }
        runCatching { windowManager?.addView(text, params) }
        voiceBadge = text
    }

    private fun removeVoiceBadge() {
        voiceBadge?.let { runCatching { windowManager?.removeView(it) } }
        voiceBadge = null
    }

    private fun showWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm
        val compose = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@AssistOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AssistOverlayService)
            setViewTreeViewModelStoreOwner(this@AssistOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SolidButlerTheme {
                    OverlayAssistant(
                        vm = vm,
                        autoVoice = autoVoice,
                        onVoiceConsumed = { autoVoice = false },
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = (20 * resources.displayMetrics.density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching { wm.addView(compose, params) }
        view = compose
        compose.requestFocus()
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
        private const val EXTRA_VOICE = "xi_voice"
        const val ACTION_VOICE_START = "io.github.dryearth.solidbutler.VOICE_START"
        const val ACTION_VOICE_END = "io.github.dryearth.solidbutler.VOICE_END"

        @Volatile
        var active: AssistOverlayService? = null
            private set

        fun show(context: android.content.Context, voice: Boolean = false): Boolean = runCatching {
            val intent = Intent(context, AssistOverlayService::class.java)
                .putExtra(EXTRA_VOICE, voice)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun OverlayAssistant(
    vm: ButlerViewModel,
    autoVoice: Boolean,
    onVoiceConsumed: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state by vm.chat.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val busStatus by OverlayVoiceBus.status.collectAsState()
    val busListening by OverlayVoiceBus.listening.collectAsState()
    val busResult by OverlayVoiceBus.result.collectAsState()
    val busError by OverlayVoiceBus.error.collectAsState()
    val shape = RoundedCornerShape(30.dp)

    // Entrance: the panel rises once, the aurora blooms then settles into a
    // quiet halo that breathes with the microphone while listening.
    val entry = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        glow.animateTo(0.52f, tween(1100, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(busResult) {
        val text = busResult ?: return@LaunchedEffect
        input = text
        if (vm.chat.value.modelConfigured && text.isNotBlank()) {
            input = ""
            vm.sendChat(text)
        } else if (text.isNotBlank()) {
            voiceStatus = "已识别，填入输入框"
        }
        OverlayVoiceBus.reset()
    }
    LaunchedEffect(busError) {
        val err = busError ?: return@LaunchedEffect
        voiceStatus = err
        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        OverlayVoiceBus.error.value = null
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            voiceStatus = "麦克风未授权，请先在完整对话里授权"
            Toast.makeText(context, voiceStatus, Toast.LENGTH_LONG).show()
            return
        }
        voiceStatus = "正在启动语音…"
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 50).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ release() }, 250)
            }
        } catch (_: Exception) {}
        val ok = runCatching {
            context.startActivity(
                Intent(context, OverlayVoiceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }.isSuccess
        if (!ok) voiceStatus = "无法启动语音识别窗口"
    }

    LaunchedEffect(autoVoice) {
        if (autoVoice) {
            onVoiceConsumed()
            startVoice()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maxPanelPx = with(density) { maxHeight.toPx() } * 0.92f
        val minPanelPx = with(density) { 232.dp.toPx() }.coerceAtMost(maxPanelPx)
        val heightAnim = remember { Animatable(0f) }
        var dragHeight by remember { mutableStateOf<Float?>(null) }
        var dragStart by remember { mutableFloatStateOf(0f) }
        var dragDelta by remember { mutableFloatStateOf(0f) }
        var contentHeight by remember { mutableFloatStateOf(0f) }
        var autoSuppressed by remember { mutableStateOf(false) }

        LaunchedEffect(minPanelPx) {
            if (heightAnim.value <= 0.5f) heightAnim.snapTo(minPanelPx)
        }
        val autoTargetPx = (minPanelPx + contentHeight).coerceIn(minPanelPx, maxPanelPx)
        LaunchedEffect(autoTargetPx, autoSuppressed) {
            if (!autoSuppressed && dragHeight == null && autoTargetPx > heightAnim.value + 0.5f) {
                heightAnim.animateTo(
                    autoTargetPx,
                    spring(dampingRatio = 0.9f, stiffness = 320f),
                )
            }
        }
        LaunchedEffect(state.messages.size) {
            autoSuppressed = false
        }
        LaunchedEffect(state.messages.isEmpty()) {
            if (state.messages.isEmpty()) contentHeight = 0f
        }

        val panelHeightPx = dragHeight ?: heightAnim.value
        val dragModifier = Modifier.pointerInput(minPanelPx, maxPanelPx, autoTargetPx) {
            detectVerticalDragGestures(
                onDragStart = {
                    dragStart = heightAnim.value
                    dragDelta = 0f
                },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    dragDelta += amount
                    dragHeight = (dragStart - dragDelta).coerceIn(minPanelPx, maxPanelPx)
                },
                onDragEnd = {
                    val released = dragHeight ?: heightAnim.value
                    dragHeight = null
                    val step = with(density) { 42.dp.toPx() }
                    val anchors = listOf(
                        minPanelPx,
                        autoTargetPx.coerceIn(minPanelPx, maxPanelPx),
                        maxPanelPx,
                    ).distinct().sorted()
                    val target = when {
                        dragDelta > step -> minPanelPx
                        dragDelta < -step -> anchors.firstOrNull { it > released + 1f } ?: maxPanelPx
                        else -> anchors.minByOrNull { kotlin.math.abs(it - released) } ?: released
                    }
                    if (dragDelta > step) autoSuppressed = true
                    scope.launch {
                        heightAnim.snapTo(released)
                        heightAnim.animateTo(
                            target,
                            spring(dampingRatio = 0.86f, stiffness = 380f),
                        )
                    }
                },
                onDragCancel = {
                    val released = dragHeight ?: heightAnim.value
                    dragHeight = null
                    scope.launch {
                        heightAnim.snapTo(released)
                        heightAnim.animateTo(
                            autoTargetPx,
                            spring(dampingRatio = 0.9f, stiffness = 320f),
                        )
                    }
                },
            )
        }

        WakeGlow(
            modifier = Modifier.fillMaxSize(),
            progress = glow.value,
            panelHeightPx = panelHeightPx,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .height(with(density) { panelHeightPx.toDp() })
                .graphicsLayer {
                    alpha = entry.value
                    translationY = (1f - entry.value) * 34.dp.toPx()
                    scaleX = 0.98f + 0.02f * entry.value
                    scaleY = 0.98f + 0.02f * entry.value
                }
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(18.dp).then(dragModifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(Color(0x330B1220)),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = dragModifier.fillMaxWidth(),
            ) {
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
                        when {
                            busListening -> "正在聆听…"
                            state.modelConfigured -> "悬浮助手 · 直接对话"
                            else -> "悬浮助手 · 尚未配置模型"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (busListening) Color(0xFFD85B5B) else Color(0x990B1220),
                    )
                }
                Icon(
                    if (panelHeightPx > minPanelPx + with(density) { 24.dp.toPx() }) {
                        Icons.Rounded.CloseFullscreen
                    } else {
                        Icons.Rounded.OpenInFull
                    },
                    contentDescription = "展开或收起",
                    tint = Color(0x990B1220),
                    modifier = Modifier.size(19.dp).clickable {
                        scope.launch {
                            val collapsed = panelHeightPx <= minPanelPx + with(density) { 24.dp.toPx() }
                            val target = if (collapsed) {
                                maxOf(autoTargetPx, maxPanelPx * 0.62f)
                            } else {
                                minPanelPx
                            }
                            heightAnim.animateTo(
                                target.coerceIn(minPanelPx, maxPanelPx),
                                spring(dampingRatio = 0.86f, stiffness = 380f),
                            )
                        }
                    },
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
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height.toFloat() },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.messages.takeLast(8).forEach { msg ->
                                OverlayBubble(vm, msg)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "直接说或输入。上拉面板看历史，下拉收回到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x990B1220),
                )
            }

            (busStatus ?: voiceStatus)?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (busListening) Color(0xFFD85B5B) else Color(0x990B1220),
                    maxLines = 1,
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
                        .background(if (busListening) Color(0x33D85B5B) else Color(0x220B1220))
                        .border(
                            width = if (busListening) 1.5.dp else 0.dp,
                            color = if (busListening) Color(0x99D85B5B) else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { startVoice() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = "语音输入",
                        tint = if (busListening) Color(0xFFD85B5B) else Color(0x990B1220),
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
                    "上拉展开 · 下拉收起",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x660B1220),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "完整对话",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF33406A),
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .putExtra("open_chat", true)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                                    ),
                            )
                        }
                        onClose()
                    },
                )
            }
        }
    }
}

/** Aurora drawn behind the panel: blooms on wake and breathes with the mic. */
@Composable
private fun WakeGlow(
    modifier: Modifier = Modifier,
    progress: Float,
    panelHeightPx: Float,
) {
    val level by OverlayVoiceBus.level.collectAsState()
    val listening by OverlayVoiceBus.listening.collectAsState()
    val pulse = if (listening) 1f + level * 0.85f else 1f
    Canvas(modifier) {
        if (progress <= 0.001f) return@Canvas
        val w = size.width
        val h = size.height
        fun bloom(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            if (alpha <= 0.001f || radius <= 0f) return
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha.coerceAtMost(1f)),
                        color.copy(alpha = (alpha * 0.38f).coerceAtMost(1f)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        val p = (progress * pulse).coerceAtMost(1.2f)
        // Center the aurora just above the panel's top edge so it is visible
        // over the app behind the floating window instead of being covered.
        val topY = (h - panelHeightPx - 10.dp.toPx()).coerceIn(0f, h)
        bloom(w * 0.18f, topY, w * 0.46f, Color(0xFF4F8BFF), 0.38f * p)
        bloom(w * 0.50f, topY - 6.dp.toPx(), w * 0.52f, Color(0xFFA78BFA), 0.34f * p)
        bloom(w * 0.82f, topY, w * 0.46f, Color(0xFFFF7AC6), 0.32f * p)
        bloom(w * 0.50f, topY - 26.dp.toPx(), w * 0.40f, Color(0xFFFFE4B5), 0.12f * p)
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
