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
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.SystemSounds
import io.github.dryearth.solidbutler.core.TtsDefaults
import io.github.dryearth.solidbutler.data.ActionState
import io.github.dryearth.solidbutler.AppActivity
import io.github.dryearth.solidbutler.ButlerViewModel
import io.github.dryearth.solidbutler.data.ChatMessage
import io.github.dryearth.solidbutler.data.ChatRole
import io.github.dryearth.solidbutler.ui.theme.SolidButlerTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_VOICE, false) == true) {
            autoVoice = true
            SystemSounds.play(this, Prefs(this).wakeSoundUri)
        }
        if (view == null) showWindow()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.requestLayout()
    }

    override fun onDestroy() {
        runCatching { receiver?.let { unregisterReceiver(it) } }
        if (active === this) active = null
        SystemSounds.play(this, Prefs(this).exitSoundUri)
        OverlaySpeech.stop()
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            windowAnimations = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        runCatching { wm.addView(compose, params) }
        view = compose
        compose.requestFocus()
    }

    private fun removeWindow() {
        val v = view ?: return
        runCatching { windowManager?.removeViewImmediate(v) }
        view = null
    }

    private fun openFullChat() {
        runCatching {
            startActivity(
                Intent(this, AppActivity::class.java)
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

private suspend fun speakAndWait(engine: TextToSpeech, text: String) {
    suspendCancellableCoroutine<Unit> { cont ->
        val id = "xi-wake-${System.nanoTime()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onError(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }
        })
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
    }
}

private val GlassGrainPoints = List(56) { i ->
    val x = ((i * 37 + 11) % 97) / 97f
    val y = ((i * 53 + 29) % 89) / 89f
    Offset(x, y)
}

/** Frosted liquid-glass surface: translucency, specular sweep and fine grain. */
private fun Modifier.frostedGlass(shape: Shape, corner: Dp, colors: List<Color>): Modifier =
    this
        .clip(shape)
        .background(Brush.linearGradient(colors))
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0x30FFFFFF),
                    0.45f to Color(0x0AFFFFFF),
                    1f to Color(0x24000000),
                ),
            )
            drawRect(
                Brush.linearGradient(
                    0f to Color(0x22FFFFFF),
                    0.40f to Color(0x00FFFFFF),
                    0.58f to Color(0x12FFFFFF),
                    1f to Color(0x00FFFFFF),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            GlassGrainPoints.forEach { p ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = 0.65.dp.toPx(),
                    center = Offset(p.x * size.width, p.y * size.height),
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x1C000000)),
                    startY = size.height * 0.55f,
                    endY = size.height,
                ),
            )
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(Color(0x99FFFFFF), Color(0x33FFFFFF), Color(0x14FFFFFF)),
            ),
            shape,
        )
        .drawWithContent {
            drawContent()
            val inset = corner.toPx() * 1.1f
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0x88FFFFFF), Color.Transparent),
                ),
                start = Offset(inset, 0.75f),
                end = Offset(size.width - inset, 0.75f),
                strokeWidth = 1f,
            )
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
    var revealed by remember { mutableStateOf(!autoVoice) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val busStatus by OverlayVoiceBus.status.collectAsState()
    val busListening by OverlayVoiceBus.listening.collectAsState()
    val busResult by OverlayVoiceBus.result.collectAsState()
    val busError by OverlayVoiceBus.error.collectAsState()
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    // Entrance: the panel rises once, the aurora blooms then settles into a
    // quiet halo that breathes with the microphone while listening.
    val entry = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    val entrance = remember { Animatable(0f) }
    val sheetEntry = remember { Animatable(0f) }
    var wakeSerial by remember { mutableIntStateOf(0) }
    var wakeToken by remember { mutableIntStateOf(0) }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsReady = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(
            context,
            { status -> ttsReady.value = status == TextToSpeech.SUCCESS },
            vm.prefs.ttsEngine.takeIf { it.isNotBlank() } ?: TtsDefaults.defaultEngine(),
        )
        ttsRef.value = engine
        onDispose {
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
            ttsRef.value = null
            ttsReady.value = false
        }
    }
    LaunchedEffect(ttsReady.value) {
        if (ttsReady.value) {
            ttsRef.value?.let { TtsDefaults.apply(context, it) }
        }
    }
    LaunchedEffect(Unit) { if (wakeSerial == 0) wakeSerial = 1 }
    LaunchedEffect(wakeSerial) {
        if (wakeSerial == 0) return@LaunchedEffect
        dim.snapTo(0f)
        entrance.snapTo(0f)
        entry.snapTo(0f)
        glow.snapTo(0f)
        launch { dim.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { entrance.animateTo(1f, tween(2600, easing = LinearOutSlowInEasing)) }
        launch {
            glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            glow.animateTo(0.52f, tween(1100, easing = FastOutSlowInEasing))
        }
        kotlinx.coroutines.delay(160)
        entry.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    val sheetVisible = revealed
    LaunchedEffect(sheetVisible) {
        if (sheetVisible) {
            sheetEntry.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        } else {
            sheetEntry.snapTo(0f)
        }
    }

    LaunchedEffect(busResult) {
        val text = busResult ?: return@LaunchedEffect
        if (text.isNotBlank()) {
            input = ""
            voiceStatus = null
            vm.sendChat(text)
            revealed = true
        }
        OverlayVoiceBus.reset()
    }
    LaunchedEffect(busError) {
        val err = busError ?: return@LaunchedEffect
        voiceStatus = err
        revealed = true
        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        OverlayVoiceBus.error.value = null
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            voiceStatus = "麦克风未授权，请先在完整对话里授权"
            revealed = true
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
        val ok = runCatching { OverlaySpeech.start(context) }.isSuccess
        if (!ok) {
            voiceStatus = "无法启动语音识别"
            revealed = true
        }
    }

    var closing by remember { mutableStateOf(false) }
    val exit = remember { Animatable(0f) }
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            exit.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    LaunchedEffect(autoVoice) {
        if (autoVoice) {
            revealed = false
            wakeSerial++
            wakeToken++
        }
    }
    LaunchedEffect(wakeToken) {
        if (wakeToken == 0) return@LaunchedEffect
        val phrases = vm.prefs.wakeGreetings
            .lines()
            .map { it.trim().trim(',', '，', ';', '；') }
            .filter { it.isNotBlank() }
        val phrase = phrases.randomOrNull()
        if (phrase != null) {
            // Give the system engine time to initialise after the wake sound.
            val ready = withTimeoutOrNull(4000) {
                while (!ttsReady.value) delay(60)
                true
            } == true
            if (ready) {
                if (vm.prefs.wakeSoundUri.isNotBlank()) delay(450)
                val engine = ttsRef.value
                if (engine != null) {
                    val finished = withTimeoutOrNull(6000) { speakAndWait(engine, phrase) }
                    if (finished == null) runCatching { engine.stop() }
                    delay(120)
                }
            }
        }
        startVoice()
        onVoiceConsumed()
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
        val dismissHeight = if (sheetVisible) {
            (maxHeight - with(density) { panelHeightPx.toDp() }).coerceAtLeast(0.dp)
        } else {
            maxHeight
        }
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

        // Tapping the open screen either opens the panel or dismisses the
        // assistant; the panel itself consumes its own touches.
        Box(
            Modifier
                .fillMaxWidth()
                .height(dismissHeight)
                .align(Alignment.TopCenter)
                .pointerInput(sheetVisible) {
                    detectTapGestures {
                        if (sheetVisible) dismiss() else revealed = true
                    }
                },
        )
        WakeAmbient(
            modifier = Modifier.fillMaxSize(),
            progress = glow.value * (1f - exit.value),
            entrance = entrance.value,
            dim = dim.value * (1f - exit.value),
            panelHeightPx = if (sheetVisible) panelHeightPx else with(density) { 92.dp.toPx() },
            sheetVisible = sheetVisible,
        )

        if (sheetVisible) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { panelHeightPx.toDp() })
                    .graphicsLayer {
                        alpha = sheetEntry.value * (1f - exit.value)
                        translationY = (1f - sheetEntry.value) * 28.dp.toPx() +
                            exit.value * 40.dp.toPx()
                    }
                    .shadow(26.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .frostedGlass(
                        shape = shape,
                        corner = 30.dp,
                        colors = listOf(
                            Color(0xB8FFFFFF),
                            Color(0x80F4F8FF),
                            Color(0x55E7EEFF),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
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
                        modifier = Modifier.size(19.dp).clickable { dismiss() },
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
                                    Intent(context, AppActivity::class.java)
                                        .putExtra("open_chat", true)
                                        .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                        ),
                                )
                            }
                            dismiss()
                        },
                    )
                }
            }
        } else {
            VoiceCapsule(
                status = busStatus ?: voiceStatus,
                listening = busListening,
                onOpen = { revealed = true },
                onClose = { dismiss() },
                onMic = { if (busListening) revealed = true else startVoice() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .graphicsLayer {
                        alpha = entry.value * (1f - exit.value)
                        translationY = (1f - entry.value) * 34.dp.toPx() +
                            exit.value * 34.dp.toPx()
                    },
            )
        }
        WakeStreamerLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = entrance.value,
            layerAlpha = 1f - exit.value,
        )
    }
}

/**
 * Full-screen wake layer: a power-key aurora on the right edge, a rim that
 * contracts into the real screen edge, an edge streamer, a colored ribbon around
 * the voice capsule, then a bottom aurora that breathes with the microphone.
 */
@Composable
private fun WakeAmbient(
    modifier: Modifier = Modifier,
    progress: Float,
    entrance: Float,
    dim: Float,
    panelHeightPx: Float,
    sheetVisible: Boolean,
) {
    val level by OverlayVoiceBus.level.collectAsState()
    val listening by OverlayVoiceBus.listening.collectAsState()
    val pulse = if (listening) 1f + level * 0.85f else 1f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val scrim = (if (sheetVisible) 0.30f else 0.38f) * dim
        if (scrim > 0.001f) drawRect(Color.Black.copy(alpha = scrim))

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

        val e = entrance.coerceIn(0f, 1f)

        // 1. Power-key aurora entering from the right edge.
        if (e < 1f) {
            val fade = 1f - e
            bloom(w * 1.08f, h * 0.46f, w * (0.55f + 0.22f * e), Color(0xFF93C5FD), 0.55f * fade)
            bloom(w * 1.12f, h * 0.62f, w * 0.45f, Color(0xFFC4B5FD), 0.42f * fade)
        }

        // 2. Rim contracts from 16dp outside into the real screen edge.
        if (e < 0.995f) {
            val inset = (1f - e) * 16.dp.toPx() + 1.5.dp.toPx()
            val radius = 30.dp.toPx()
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4F8BFF),
                        Color(0xFFA78BFA),
                        Color(0xFFFF7AC6),
                        Color(0xFFFFE4B5),
                        Color(0xFF4F8BFF),
                    ),
                ),
                topLeft = Offset(inset, inset),
                size = Size(
                    (w - inset * 2f).coerceAtLeast(0f),
                    (h - inset * 2f).coerceAtLeast(0f),
                ),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = 2.dp.toPx()),
                alpha = (1f - e) * 0.9f,
            )
        }

        // 4. Bottom aurora above the sheet or capsule.
        val topY = (h - panelHeightPx - 8.dp.toPx()).coerceIn(0f, h)
        val p = progress.coerceIn(0f, 1f) * pulse
        bloom(w * 0.18f, topY, w * 0.46f, Color(0xFF4F8BFF), 0.34f * p)
        bloom(w * 0.50f, topY - 6.dp.toPx(), w * 0.52f, Color(0xFFA78BFA), 0.31f * p)
        bloom(w * 0.82f, topY, w * 0.46f, Color(0xFFFF7AC6), 0.29f * p)
        bloom(w * 0.50f, topY - 26.dp.toPx(), w * 0.40f, Color(0xFFFFE4B5), 0.10f * p)
    }
}


/**
 * Edge streamer + capsule orbit glow + particle burst, drawn above the capsule.
 * The streamer enters at the capsule's right edge; a colored ribbon then travels
 * around the capsule outline, throws sparkles, and dissolves.
 */
@Composable
private fun WakeStreamerLayer(
    modifier: Modifier = Modifier,
    entrance: Float,
    layerAlpha: Float,
) {
    if (entrance <= 0.02f || layerAlpha <= 0.001f) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        fun bloom(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            val a = (alpha * layerAlpha).coerceIn(0f, 1f)
            if (a <= 0.001f || radius <= 0f) return
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = a),
                        color.copy(alpha = (a * 0.38f).coerceIn(0f, 1f)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        val e = entrance.coerceIn(0f, 1f)
        val capInset = 18.dp.toPx()
        val capHeight = 56.dp.toPx()
        val capBottom = h - capInset
        val capTop = capBottom - capHeight
        val capLeft = capInset
        val capRight = w - capInset
        val capRadius = capHeight / 2f
        val capCenterX = w * 0.5f
        val capCenterY = (capTop + capBottom) / 2f

        // 1. Top-right -> right edge -> capsule right edge.
        val travel = ((e - 0.02f) / 0.40f).coerceIn(0f, 1f)
        val edgePath = Path().apply {
            moveTo(w - 2.dp.toPx(), 0f)
            lineTo(w - 2.dp.toPx(), capCenterY)
            quadraticBezierTo(w - 2.dp.toPx(), capCenterY, capRight, capCenterY)
            lineTo(capRight - 1.dp.toPx(), capCenterY)
        }
        val edgeMeasure = PathMeasure().apply { setPath(edgePath, false) }
        val edgeLength = edgeMeasure.length
        if (edgeLength > 1f && travel < 1f) {
            val head = edgeLength * travel
            val tail = (head - edgeLength * 0.26f).coerceAtLeast(0f)
            val segment = Path()
            if (edgeMeasure.getSegment(tail, head, segment, true)) {
                drawPath(
                    path = segment,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF8FB7FF),
                            Color(0xFFC4B5FD),
                            Color(0xFFFFE4B5),
                        ),
                    ),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                    alpha = (1f - e * 0.18f) * layerAlpha,
                )
            }
            if (head > 0f) {
                val point = edgeMeasure.getPosition(head)
                bloom(point.x, point.y, 32.dp.toPx(), Color(0xFFFFF3D6), 0.9f)
                // Small sparks trail behind the head.
                for (i in 1..6) {
                    val d = (head - i * 15.dp.toPx()).coerceAtLeast(0f)
                    if (d <= 0f) break
                    val p = edgeMeasure.getPosition(d)
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFFFF9ECB) else Color(0xFFBFE0FF),
                        radius = (2.4.dp.toPx()) * (1f - i * 0.11f),
                        center = Offset(p.x, p.y),
                        alpha = (0.50f - i * 0.06f).coerceAtLeast(0f) * layerAlpha,
                    )
                }
            }
        }

        // 2. Ribbon travels around the capsule outline.
        val orbit = ((e - 0.42f) / 0.38f).coerceIn(0f, 1f)
        if (orbit > 0f) {
            val capsule = Path().apply {
                addRoundRect(
                    RoundRect(
                        capLeft,
                        capTop,
                        capRight,
                        capBottom,
                        capRadius,
                        capRadius,
                    ),
                )
            }
            val measure = PathMeasure().apply { setPath(capsule, false) }
            val perimeter = measure.length
            if (perimeter > 1f) {
                val fade = if (orbit < 0.82f) 1f else (1f - (orbit - 0.82f) / 0.18f).coerceIn(0f, 1f)
                val ringBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF4F8BFF),
                        Color(0xFFA78BFA),
                        Color(0xFFFF7AC6),
                        Color(0xFFFFE4B5),
                        Color(0xFF4F8BFF),
                    ),
                    center = Offset(capCenterX, capCenterY),
                )
                drawPath(
                    path = capsule,
                    brush = ringBrush,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                    alpha = 0.10f * orbit * fade * layerAlpha,
                )
                drawPath(
                    path = capsule,
                    brush = ringBrush,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                    alpha = 0.20f * orbit * fade * layerAlpha,
                )

                val startOffset = perimeter * 0.25f
                val head = (startOffset + perimeter * orbit) % perimeter
                val tailLength = perimeter * 0.30f
                fun drawSegment(from: Float, to: Float) {
                    if (to <= from + 0.5f) return
                    val part = Path()
                    if (measure.getSegment(from, to, part, true)) {
                        drawPath(
                            path = part,
                            brush = ringBrush,
                            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round),
                            alpha = 0.95f * fade * layerAlpha,
                        )
                    }
                }
                if (head - tailLength >= 0f) {
                    drawSegment(head - tailLength, head)
                } else {
                    drawSegment(0f, head)
                    drawSegment(perimeter + (head - tailLength), perimeter)
                }
                val point = measure.getPosition(head)
                bloom(point.x, point.y, 42.dp.toPx(), Color(0xFFFFF3D6), 0.9f * fade)
                bloom(point.x, point.y, 22.dp.toPx(), Color(0xFFFF9ECB), 0.55f * fade)
            }
        }

        // 3. Particle burst leaves the capsule edge and fades outward.
        val burst = ((e - 0.80f) / 0.20f).coerceIn(0f, 1f)
        if (burst > 0f) {
            val pad = 10.dp.toPx() * burst
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF4F8BFF),
                        Color(0xFFA78BFA),
                        Color(0xFFFF7AC6),
                        Color(0xFFFFE4B5),
                        Color(0xFF4F8BFF),
                    ),
                    center = Offset(capCenterX, capCenterY),
                ),
                topLeft = Offset(capLeft - pad, capTop - pad),
                size = Size(
                    (capRight - capLeft + pad * 2f).coerceAtLeast(1f),
                    (capHeight + pad * 2f).coerceAtLeast(1f),
                ),
                cornerRadius = CornerRadius(capRadius + pad),
                style = Stroke(width = 2.dp.toPx()),
                alpha = 0.35f * (1f - burst) * layerAlpha,
            )

            val count = 56
            val rx = (capRight - capLeft) * 0.5f
            val ry = capHeight * 0.5f
            for (i in 0 until count) {
                val angle = i * (2.0 * Math.PI / count) + 0.17
                val spread = 1f + 0.10f * burst + 1.55f * burst * (0.35f + 0.65f * ((i % 7) / 6f))
                val px = capCenterX + cos(angle).toFloat() * rx * spread
                val py = capCenterY + sin(angle).toFloat() * ry * spread
                val color = when (i % 4) {
                    0 -> Color(0xFF8FB7FF)
                    1 -> Color(0xFFC4B5FD)
                    2 -> Color(0xFFFF9ECB)
                    else -> Color(0xFFFFE4B5)
                }
                drawCircle(
                    color = color,
                    radius = (2.6.dp.toPx()) * (1f - 0.55f * burst),
                    center = Offset(px, py),
                    alpha = 0.60f * (1f - burst) * layerAlpha,
                )
            }
        }
    }
}

/** Compact voice-first capsule shown before the conversation sheet opens. */
@Composable
private fun VoiceCapsule(
    status: String?,
    listening: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level by OverlayVoiceBus.level.collectAsState()
    val shape = CircleShape
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .frostedGlass(
                shape = shape,
                corner = 30.dp,
                colors = listOf(
                    Color(0x99FFFFFF),
                    Color(0x66F4F8FF),
                    Color(0x40E7EEFF),
                ),
            )
            .clickable { onOpen() }
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer {
                    val scale = 1f + if (listening) level * 0.08f else 0f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (listening) Color(0x33D85B5B) else Color(0x220B1220))
                .clickable { onMic() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = "语音输入",
                tint = if (listening) Color(0xFFD85B5B) else Color(0x990B1220),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (listening) "汐在听…" else "轻触打开面板",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF0B1220),
                maxLines = 1,
            )
            Text(
                status ?: if (listening) "说完会自动发送" else "语音或文字都可以",
                style = MaterialTheme.typography.labelSmall,
                color = if (listening) Color(0xFFD85B5B) else Color(0x880B1220),
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.Close,
            contentDescription = "关闭",
            tint = Color(0x990B1220),
            modifier = Modifier.size(18.dp).clickable { onClose() },
        )
        Spacer(Modifier.width(8.dp))
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
