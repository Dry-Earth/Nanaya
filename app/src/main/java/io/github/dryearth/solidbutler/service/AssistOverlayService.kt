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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
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
import io.github.dryearth.solidbutler.ui.components.liquid.lens
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import io.github.dryearth.solidbutler.ui.components.VoiceWave
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
        if (intent?.action == ACTION_REFRESH_MIC) {
            upgradeMicrophoneType()
            if (view == null) showWindow()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_VOICE, false) == true) {
            autoVoice = true
            SystemSounds.play(this, Prefs(this).wakeSoundUri)
        }
        if (view == null) showWindow()
        return START_NOT_STICKY
    }

    /**
     * Android 11+ only lets a background service record when its foreground
     * service type includes microphone. The service may have started before
     * the user granted RECORD_AUDIO, so refresh the type after the grant.
     */
    internal fun upgradeMicrophoneType() {
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (Build.VERSION.SDK_INT < 34) {
            runCatching { startForeground(NOTIFICATION_ID, notification()) }
            return
        }
        val type = if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            base or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            base
        }
        runCatching {
            startForeground(NOTIFICATION_ID, notification(), type)
        }.onFailure {
            runCatching { startForeground(NOTIFICATION_ID, notification()) }
        }
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
            .setContentTitle("${Prefs(this).personaName} · 悬浮助手")
            .setContentText("数字助理悬浮窗正在运行")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "xi_assist_overlay"
        private const val NOTIFICATION_ID = 12003
        private const val EXTRA_VOICE = "xi_voice"
        private const val ACTION_REFRESH_MIC = "xi_refresh_mic"

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

        /** Re-apply the microphone foreground type after RECORD_AUDIO is granted. */
        fun refreshMic(context: android.content.Context) {
            runCatching {
                val intent = Intent(context, AssistOverlayService::class.java)
                    .setAction(ACTION_REFRESH_MIC)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
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
private data class OverlayPalette(
    val glass: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val danger: Color,
    val ink: Color,
    val inputInk: Color,
    val onPrimary: Color,
    val shadow: Shadow,
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
)

private fun contrastRatio(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    val light = maxOf(x, y)
    val dark = minOf(x, y)
    return (light + 0.05f) / (dark + 0.05f)
}

/**
 * The glass is translucent, so text must survive both a bright and a dark
 * backdrop. Check the candidate against the tint composited over black, white
 * and mid grey, then fall back to white or near-black when needed.
 */
private fun ensureContrast(preferred: Color, tint: Color, minRatio: Float): Color {
    val samples = listOf(
        tint.compositeOver(Color.Black),
        tint.compositeOver(Color.White),
        tint.compositeOver(Color(0xFF808080)),
    )
    fun score(color: Color): Float = samples.minOf { contrastRatio(color, it) }
    if (score(preferred) >= minRatio) return preferred
    val light = Color(0xFFF7F8FC)
    val dark = Color(0xFF0B0B0D)
    return if (score(light) >= score(dark)) light else dark
}

@Composable
private fun rememberOverlayPalette(): OverlayPalette {
    val context = LocalContext.current
    val scheme = runCatching { dynamicDarkColorScheme(context) }
        .getOrElse { darkColorScheme() }
    val glass = scheme.surfaceContainer.copy(alpha = 0.22f)
    val primary = ensureContrast(scheme.onSurface, glass, 4.5f)
    val secondaryCandidate = ensureContrast(scheme.onSurfaceVariant, glass, 3.0f)
    val secondary = if (contrastRatio(secondaryCandidate, primary) < 1.15f) {
        primary.copy(alpha = 0.72f)
    } else {
        secondaryCandidate
    }
    val accent = scheme.primary
    val danger = scheme.error
    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF0B0B0D) else Color(0xFFF7F8FC)
    val shadow = Shadow(
        color = if (primary.luminance() > 0.5f) Color(0x99000000) else Color(0x66FFFFFF),
        offset = Offset(0f, 1.5f),
        blurRadius = 3.5f,
    )
    return remember(scheme, glass, primary, secondary, accent, danger) {
        OverlayPalette(
            glass = glass,
            primaryText = primary,
            secondaryText = secondary,
            accent = accent,
            danger = danger,
            ink = scheme.surfaceContainerHighest.copy(alpha = 0.20f),
            inputInk = scheme.surfaceContainerHigh.copy(alpha = 0.24f),
            onPrimary = onPrimary,
            shadow = shadow,
            auroraA = scheme.primary.copy(alpha = 0.055f),
            auroraB = scheme.tertiary.copy(alpha = 0.045f),
            auroraC = scheme.secondary.copy(alpha = 0.035f),
        )
    }
}

@Composable
private fun PanelAurora(palette: OverlayPalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // A uniform, edge-free color field: it gives the lens something to
        // sample without drawing a visible frame or inner rectangle.
        drawRect(
            Brush.linearGradient(
                0f to palette.auroraA.copy(alpha = 0.028f),
                0.45f to palette.auroraB.copy(alpha = 0.018f),
                1f to palette.auroraC.copy(alpha = 0.024f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        GlassGrainPoints.forEach { p ->
            drawCircle(
                color = palette.primaryText.copy(alpha = 0.018f),
                radius = 0.6.dp.toPx(),
                center = Offset(p.x * w, p.y * h),
            )
        }
    }
}

@Composable
private fun FlowingAiIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "overlay-ai-icon")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shift",
    )
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val side = size.minDimension
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF67E8F9),
                            Color(0xFF818CF8),
                            Color(0xFFE879F9),
                            Color(0xFF6EE7B7),
                            Color(0xFF67E8F9),
                        ),
                        start = Offset(-side + shift * side * 2f, 0f),
                        end = Offset(shift * side * 2f, side),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: OverlayPalette,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(28.dp)
            .overlayGlass(
                backdrop = backdrop,
                shape = CircleShape,
                palette = palette,
                tintAlpha = 0.20f,
                refractionHeight = 8.dp,
                refractionAmount = 6.dp,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = palette.secondaryText,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun Modifier.overlayGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    palette: OverlayPalette,
    tint: Color = palette.ink,
    tintAlpha: Float = 0.24f,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 9.dp,
): Modifier = this
    .clip(shape)
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            lens(
                refractionHeight = refractionHeight.toPx(),
                refractionAmount = refractionAmount.toPx(),
                depthEffect = true,
                chromaticAberration = 0.04f,
            )
        },
        onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(Color(0x55FFFFFF), Color(0x1AFFFFFF), Color(0x0DFFFFFF)),
        ),
        shape,
    )

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
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0x33FFFFFF), Color(0x08FFFFFF), Color(0x26000000)),
                ),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(
                    (size.width - 2.dp.toPx()).coerceAtLeast(0f),
                    (size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
                cornerRadius = CornerRadius((corner.toPx() - 1.dp.toPx()).coerceAtLeast(0f)),
                style = Stroke(width = 1.2.dp.toPx()),
            )
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
    val palette = rememberOverlayPalette()
    val scope = rememberCoroutineScope()
    val state by vm.chat.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(!autoVoice) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val busStatus by OverlayVoiceBus.status.collectAsState()
    val busListening by OverlayVoiceBus.listening.collectAsState()
    val busLevel by OverlayVoiceBus.level.collectAsState()
    val busResult by OverlayVoiceBus.result.collectAsState()
    val busError by OverlayVoiceBus.error.collectAsState()
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    // Entrance: the panel rises once, the aurora blooms then settles into a
    // quiet halo that breathes with the microphone while listening.
    val entry = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    val entrance = remember { Animatable(0f) }
    val orbit = remember { Animatable(0f) }
    val sheetEntry = remember { Animatable(0f) }
    var wakeSerial by remember { mutableIntStateOf(0) }
    var wakeToken by remember { mutableIntStateOf(0) }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsReady = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(
            context,
            { status -> ttsReady.value = status == TextToSpeech.SUCCESS },
            vm.prefs.ttsEngine.takeIf { it.isNotBlank() },
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
        orbit.snapTo(0f)
        entry.snapTo(0f)
        glow.snapTo(0f)
        launch { dim.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { entrance.animateTo(1f, tween(2600, easing = LinearOutSlowInEasing)) }
        launch {
            glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            glow.animateTo(0.52f, tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            kotlinx.coroutines.delay(520)
            orbit.animateTo(1f, tween(2800, easing = LinearEasing))
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
            voiceStatus = "麦克风未授权，请在 设置 → 权限与授权 开启"
            revealed = true
            Toast.makeText(context, voiceStatus, Toast.LENGTH_LONG).show()
            return
        }
        AssistOverlayService.active?.upgradeMicrophoneType()
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
            val panelBackdrop = rememberLayerBackdrop {
                drawRect(Color.Transparent)
                drawContent()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { panelHeightPx.toDp() })
                    .graphicsLayer {
                        alpha = sheetEntry.value * (1f - exit.value)
                        translationY = (1f - sheetEntry.value) * 28.dp.toPx() +
                            exit.value * 40.dp.toPx()
                    }
                    .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape),
            ) {
                Box(Modifier.matchParentSize().layerBackdrop(panelBackdrop)) {
                    PanelAurora(palette, Modifier.matchParentSize())
                }
                Column(
                    Modifier
                        .matchParentSize()
                        .drawBackdrop(
                            backdrop = panelBackdrop,
                            shape = { shape },
                            effects = {
                                lens(
                                    refractionHeight = 16.dp.toPx(),
                                    refractionAmount = 10.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = 0.04f,
                                )
                            },
                            onDrawSurface = { drawRect(palette.glass) },
                        )
                        .drawWithContent {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x18FFFFFF),
                                        Color(0x06000000),
                                        Color(0x2A000000),
                                    ),
                                ),
                            )
                            drawContent()
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent),
                                ),
                                start = Offset(24.dp.toPx(), 0.8f),
                                end = Offset(size.width - 24.dp.toPx(), 0.8f),
                                strokeWidth = 1f,
                            )
                        }
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0x88FFFFFF), Color(0x2AFFFFFF), Color(0x12FFFFFF)),
                            ),
                            shape,
                        )
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
                ) {
                val expanded = panelHeightPx >
                    minPanelPx + with(density) { 24.dp.toPx() }
                Box(Modifier.fillMaxWidth().height(30.dp).then(dragModifier)) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(palette.secondaryText.copy(alpha = 0.45f)),
                    )
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GlassIconButton(
                            icon = if (expanded) {
                                Icons.Rounded.CloseFullscreen
                            } else {
                                Icons.Rounded.OpenInFull
                            },
                            contentDescription = "展开或收起",
                            palette = palette,
                            backdrop = panelBackdrop,
                        ) {
                            scope.launch {
                                val collapsed = panelHeightPx <=
                                    minPanelPx + with(density) { 24.dp.toPx() }
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
                        }
                        GlassIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            palette = palette,
                            backdrop = panelBackdrop,
                            onClick = { dismiss() },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = dragModifier.fillMaxWidth(),
                ) {
                    FlowingAiIcon(Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            vm.prefs.personaName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = palette.shadow,
                            ),
                            color = palette.primaryText,
                        )
                        Text(
                            when {
                                busListening -> "正在聆听…"
                                state.modelConfigured -> "悬浮助手 · 直接对话"
                                else -> "悬浮助手 · 尚未配置模型"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (busListening) palette.danger else palette.secondaryText,
                        )
                    }
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
                                    OverlayBubble(vm, msg, palette, panelBackdrop)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "直接说或输入。上拉面板看历史，下拉收回到这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                    )
                }

                (busStatus ?: voiceStatus)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (busListening) palette.danger else palette.secondaryText,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f)
                            .overlayGlass(
                                backdrop = panelBackdrop,
                                shape = RoundedCornerShape(16.dp),
                                palette = palette,
                                tint = palette.inputInk,
                                tintAlpha = 0.28f,
                                refractionHeight = 10.dp,
                                refractionAmount = 8.dp,
                            )
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            palette.accent.copy(alpha = 0.20f),
                                            Color.Transparent,
                                            palette.auroraB.copy(alpha = 0.24f),
                                        ),
                                    ),
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text("问${vm.prefs.personaName}…", style = MaterialTheme.typography.bodySmall, color = palette.secondaryText.copy(alpha = 0.55f))
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(
                                color = palette.primaryText,
                                fontSize = 13.sp,
                                shadow = palette.shadow,
                            ),
                            cursorBrush = SolidColor(palette.accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(36.dp)
                            .overlayGlass(
                                backdrop = panelBackdrop,
                                shape = CircleShape,
                                palette = palette,
                                tint = if (busListening) palette.danger else palette.accent,
                                tintAlpha = if (busListening) 0.30f else 0.16f,
                                refractionHeight = 10.dp,
                                refractionAmount = 8.dp,
                            )
                            .clickable { startVoice() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "语音输入",
                            tint = if (busListening) palette.danger else palette.accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(36.dp)
                            .overlayGlass(
                                backdrop = panelBackdrop,
                                shape = CircleShape,
                                palette = palette,
                                tint = if (input.isBlank()) palette.ink else palette.accent,
                                tintAlpha = if (input.isBlank()) 0.18f else 0.30f,
                                refractionHeight = 10.dp,
                                refractionAmount = 8.dp,
                            )
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
                            tint = if (input.isBlank()) palette.secondaryText.copy(alpha = 0.35f) else palette.primaryText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "上拉展开 · 下拉收起",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.70f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "完整对话",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
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
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .graphicsLayer {
                        alpha = entry.value * (1f - exit.value)
                        translationY = (1f - entry.value) * 34.dp.toPx() +
                            exit.value * 34.dp.toPx()
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (busListening) {
                    VoiceWave(
                        active = true,
                        level = busLevel,
                        modifier = Modifier.fillMaxWidth().height(84.dp),
                    )
                }
                VoiceCapsule(
                    assistantName = vm.prefs.personaName,
                    status = busStatus ?: voiceStatus,
                    listening = busListening,
                    onOpen = { revealed = true },
                    onClose = { dismiss() },
                    onMic = { if (busListening) revealed = true else startVoice() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        WakeStreamerLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = entrance.value,
            orbit = orbit.value,
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
    orbit: Float,
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
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    alpha = (1f - e * 0.18f) * layerAlpha,
                )
            }
            if (head > 0f) {
                val point = edgeMeasure.getPosition(head)
                bloom(point.x, point.y, 18.dp.toPx(), Color(0xFFFFF3D6), 0.65f)
                // Small sparks trail behind the head.
                for (i in 1..6) {
                    val d = (head - i * 15.dp.toPx()).coerceAtLeast(0f)
                    if (d <= 0f) break
                    val p = edgeMeasure.getPosition(d)
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFFFF9ECB) else Color(0xFFBFE0FF),
                        radius = (1.3.dp.toPx()) * (1f - i * 0.11f),
                        center = Offset(p.x, p.y),
                        alpha = (0.50f - i * 0.06f).coerceAtLeast(0f) * layerAlpha,
                    )
                }
            }
        }

        // 2. Thin ribbon travels slowly around the capsule outline.
        val ribbon = orbit.coerceIn(0f, 1f)
        if (ribbon > 0f) {
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
                val fade = if (ribbon < 0.86f) 1f else (1f - (ribbon - 0.86f) / 0.14f).coerceIn(0f, 1f)
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
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    alpha = 0.06f * ribbon * fade * layerAlpha,
                )
                drawPath(
                    path = capsule,
                    brush = ringBrush,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                    alpha = 0.16f * ribbon * fade * layerAlpha,
                )

                val startOffset = perimeter * 0.25f
                val head = (startOffset + perimeter * ribbon) % perimeter
                val tailLength = perimeter * 0.45f
                fun drawSegment(from: Float, to: Float) {
                    if (to <= from + 0.5f) return
                    val part = Path()
                    if (measure.getSegment(from, to, part, true)) {
                        drawPath(
                            path = part,
                            brush = ringBrush,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                            alpha = 0.82f * fade * layerAlpha,
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
                bloom(point.x, point.y, 20.dp.toPx(), Color(0xFFFFF3D6), 0.55f * fade)
                bloom(point.x, point.y, 10.dp.toPx(), Color(0xFFFF9ECB), 0.30f * fade)
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
    assistantName: String,
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
                    Color(0x66FFFFFF),
                    Color(0x40F4F8FF),
                    Color(0x22E7EEFF),
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
                if (listening) "${assistantName}在听…" else "轻触打开面板",
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
private fun OverlayBubble(
    vm: ButlerViewModel,
    msg: ChatMessage,
    palette: OverlayPalette,
    backdrop: LayerBackdrop,
) {
    val isUser = msg.role == ChatRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .overlayGlass(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(14.dp),
                    palette = palette,
                    tintAlpha = if (isUser) 0.34f else 0.20f,
                    refractionHeight = 12.dp,
                    refractionAmount = 9.dp,
                )
                .padding(10.dp),
        ) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodySmall.copy(shadow = palette.shadow),
                color = palette.primaryText,
            )
            val action = msg.action
            if (action != null && msg.actionState == ActionState.PENDING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    action.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.inputInk)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable { vm.confirmAction(msg.id) },
                    )
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.ink.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable { vm.rejectAction(msg.id) },
                    )
                }
            }
        }
    }
}
