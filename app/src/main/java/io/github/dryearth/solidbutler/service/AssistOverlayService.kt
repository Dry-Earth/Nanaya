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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.DeleteSweep
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
import androidx.compose.ui.graphics.drawscope.rotate
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
import io.github.dryearth.solidbutler.ui.components.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
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
        val band = 34.dp.toPx()
        val radius = CornerRadius(30.dp.toPx())
        // Only a soft colored rim: no filled rectangle behind the glass.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    palette.auroraA,
                    palette.auroraB,
                    palette.auroraC,
                    palette.auroraA,
                ),
            ),
            topLeft = Offset(-band / 2f, -band / 2f),
            size = Size(w + band, h + band),
            cornerRadius = radius,
            style = Stroke(width = band),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    palette.auroraC.copy(alpha = palette.auroraC.alpha * 0.8f),
                    palette.auroraA.copy(alpha = palette.auroraA.alpha * 0.8f),
                ),
            ),
            topLeft = Offset(-band / 4f, -band / 4f),
            size = Size(w + band / 2f, h + band / 2f),
            cornerRadius = CornerRadius(32.dp.toPx()),
            style = Stroke(width = band / 2f),
        )
        GlassGrainPoints.forEach { p ->
            drawCircle(
                color = palette.primaryText.copy(alpha = 0.025f),
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
            .size(30.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = palette.primaryText,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun OverlayBubble(
    vm: ButlerViewModel,
    msg: ChatMessage,
    palette: OverlayPalette,
    backdrop: LayerBackdrop,
    textShift: Float,
) {
    val isUser = msg.role == ChatRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
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
            val textBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE0F7FF),
                    Color(0xFFC7D2FE),
                    Color(0xFFFBCFE8),
                    Color(0xFFD1FAE5),
                    Color(0xFFE0F7FF),
                ),
                start = Offset(-260f + textShift * 520f, 0f),
                end = Offset(260f + textShift * 520f, 320f),
            )
            Text(
                msg.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    shadow = palette.shadow,
                    brush = textBrush,
                ),
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
