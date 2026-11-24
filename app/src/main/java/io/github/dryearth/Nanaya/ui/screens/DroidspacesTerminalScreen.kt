package io.github.dryearth.Nanaya.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.dryearth.Nanaya.core.DroidspacesTerminals
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass

private const val PREFS = "nanaya_terminal"
private const val KEY_BACKDROP = "backdrop"

private enum class TerminalBackdrop(val label: String, val argb: Int) {
    GLASS("液态玻璃", AndroidColor.TRANSPARENT),
    FROSTED("深色磨砂", 0xCC0B0E13.toInt()),
    BLACK("纯黑", 0xFF000000.toInt()),
}

private fun loadBackdrop(context: Context): TerminalBackdrop {
    val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_BACKDROP, TerminalBackdrop.GLASS.name)
    return TerminalBackdrop.values().firstOrNull { it.name == name } ?: TerminalBackdrop.GLASS
}

private fun saveBackdrop(context: Context, mode: TerminalBackdrop) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_BACKDROP, mode.name)
        .apply()
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun showKeyboard(context: Context, view: TerminalView) {
    view.requestFocus()
    view.post {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}

/**
 * Termux terminal front end. TerminalSession owns the PTY and emulator state,
 * so leaving and returning re-attaches the view without replaying bytes.
 */
@Composable
fun DroidspacesTerminalScreen(
    containerName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val keyboard = LocalSoftwareKeyboardController.current
    val viewRef = remember { mutableStateOf<TerminalView?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val session = remember(containerName, generation) {
        DroidspacesTerminals.getOrOpen(context, containerName)
    }
    val alive by session.alive.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(loadBackdrop(context)) }

    // A filter keyboard from the container list should not shrink the terminal
    // on entry; the user taps the terminal when they want to type.
    LaunchedEffect(Unit) { keyboard?.hide() }

    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen) { onBack() }

    fun chooseBackdrop(mode: TerminalBackdrop) {
        backdrop = mode
        saveBackdrop(context, mode)
    }

    val terminalBackground = if (fullscreen) AndroidColor.BLACK else backdrop.argb

    if (fullscreen) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .imePadding(),
        ) {
            Box(Modifier.weight(1f)) {
                key(session) {
                    TerminalHost(
                        session = session,
                        background = terminalBackground,
                        viewRef = viewRef,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Pill("退出全屏", selected = true, color = Glass.Mint) {
                        fullscreen = false
                    }
                }
            }
            ShortcutBar(
                session = session,
                viewRef = viewRef,
                fullscreen = true,
                onFullscreen = { fullscreen = false },
                onRestart = if (alive) null else ({ generation++ }),
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        containerName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Glass.Text,
                        maxLines = 1,
                    )
                    Text(
                        "Termux 终端 · ${backdrop.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Muted,
                    )
                }
                Pill(
                    if (alive) "运行中" else "已结束",
                    selected = alive,
                    color = if (alive) Glass.Mint else Glass.Amber,
                )
                if (!alive) {
                    Spacer(Modifier.width(6.dp))
                    Pill("重开", color = Glass.Amber) { generation++ }
                }
                Spacer(Modifier.width(8.dp))
                Pill("全屏", color = Glass.Accent) { fullscreen = true }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TerminalBackdrop.values().forEach { mode ->
                    val color = when (mode) {
                        TerminalBackdrop.GLASS -> Glass.Ice
                        TerminalBackdrop.FROSTED -> Glass.Accent
                        TerminalBackdrop.BLACK -> Glass.Faint
                    }
                    Pill(mode.label, selected = backdrop == mode, color = color) {
                        chooseBackdrop(mode)
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.weight(1f),
                corner = 16.dp,
                padding = PaddingValues(4.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (backdrop == TerminalBackdrop.GLASS) {
                        Box(Modifier.matchParentSize().background(Color(0x330B0E13)))
                    }
                    key(session) {
                        TerminalHost(
                            session = session,
                            background = terminalBackground,
                            viewRef = viewRef,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            ShortcutBar(
                session = session,
                viewRef = viewRef,
                fullscreen = false,
                onFullscreen = { fullscreen = true },
                onRestart = if (alive) null else ({ generation++ }),
            )

            Text(
                if (alive) {
                    "长按选择文本，双指缩放字号，点击终端弹出键盘。切换背景不会重启会话。"
                } else {
                    "会话已结束。点「重开」重新连接容器。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (alive) Glass.Faint else Glass.Amber,
            )
        }
    }
}

@Composable
private fun TerminalHost(
    session: DroidspacesTerminals.Session,
    background: Int,
    viewRef: MutableState<TerminalView?>,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = remember { TerminalView(context, null) }
    val textSize = remember {
        intArrayOf((13f * density.density).toInt().coerceIn(20, 96))
    }

    val viewClient = remember(view) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    val next = (textSize[0] * scale).toInt().coerceIn(18, 96)
                    textSize[0] = next
                    view.setTextSize(next)
                    return 1f
                }
                return scale
            }

            override fun onSingleTapUp(e: MotionEvent) {
                showKeyboard(context, view)
            }

            override fun shouldBackButtonBeMappedToEscape() = false

            override fun shouldEnforceCharBasedInput() = true

            override fun shouldUseCtrlSpaceWorkaround() = false

            override fun isTerminalViewSelected() = true

            override fun copyModeChanged(copyMode: Boolean) = Unit

            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false

            override fun onKeyUp(keyCode: Int, e: KeyEvent) = false

            override fun onLongPress(event: MotionEvent) = false

            override fun readControlKey() = false

            override fun readAltKey() = false

            override fun readShiftKey() = false

            override fun readFnKey() = false

            override fun onCodePoint(
                codePoint: Int,
                ctrlDown: Boolean,
                session: TerminalSession,
            ) = false

            override fun onEmulatorSet() = Unit

            override fun logError(tag: String, message: String) {
                Log.e(tag, message)
            }

            override fun logWarn(tag: String, message: String) {
                Log.w(tag, message)
            }

            override fun logInfo(tag: String, message: String) {
                Log.i(tag, message)
            }

            override fun logDebug(tag: String, message: String) {
                Log.d(tag, message)
            }

            override fun logVerbose(tag: String, message: String) {
                Log.v(tag, message)
            }

            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                Log.e(tag, message, e)
            }

            override fun logStackTrace(tag: String, e: Exception) {
                Log.e(tag, "terminal", e)
            }
        }
    }

    DisposableEffect(view) {
        viewRef.value = view
        view.setTerminalViewClient(viewClient)
        view.setTextSize(textSize[0])
        view.setBackgroundColor(background)
        view.setKeepScreenOn(true)
        onDispose {
            if (viewRef.value === view) viewRef.value = null
            runCatching { view.attachSession(null) }
        }
    }

    LaunchedEffect(background) { view.setBackgroundColor(background) }

    AndroidView(
        factory = {
            view.apply {
                setTerminalViewClient(viewClient)
                setTextSize(textSize[0])
                setBackgroundColor(background)
                post { attachSession(session.terminalSession) }
            }
        },
        update = { current ->
            current.setBackgroundColor(background)
            if (current.currentSession !== session.terminalSession) {
                current.post { current.attachSession(session.terminalSession) }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ShortcutBar(
    session: DroidspacesTerminals.Session,
    viewRef: MutableState<TerminalView?>,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onRestart: (() -> Unit)?,
) {
    val context = LocalContext.current

    @Composable
    fun key(label: String, color: Color, text: String) {
        Pill(label, color = color) { session.write(text) }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        key("Ctrl+C", Glass.Rose, "\u0003")
        key("Ctrl+D", Glass.Rose, "\u0004")
        key("Ctrl+Z", Glass.Rose, "\u001a")
        key("Ctrl+L", Glass.Faint, "\u000c")
        key("Ctrl+R", Glass.Accent, "\u0012")
        key("Ctrl+A", Glass.Muted, "\u0001")
        key("Ctrl+E", Glass.Muted, "\u0005")
        key("Ctrl+U", Glass.Muted, "\u0015")
        key("Ctrl+K", Glass.Muted, "\u000b")
        key("Ctrl+W", Glass.Muted, "\u0017")
        key("Ctrl+P", Glass.Muted, "\u0010")
        key("Ctrl+N", Glass.Muted, "\u000e")
        key("Tab", Glass.Accent, "\t")
        key("Esc", Glass.Muted, "\u001b")
        key("↑", Glass.Muted, "\u001b[A")
        key("↓", Glass.Muted, "\u001b[B")
        key("←", Glass.Muted, "\u001b[D")
        key("→", Glass.Muted, "\u001b[C")
        key("Home", Glass.Muted, "\u001b[H")
        key("End", Glass.Muted, "\u001b[F")
        key("PgUp", Glass.Muted, "\u001b[5~")
        key("PgDn", Glass.Muted, "\u001b[6~")
        key("Enter", Glass.Accent, "\r")
        key("清屏", Glass.Faint, "\u000c")
        Pill("键盘", color = Glass.Mint) {
            viewRef.value?.let { showKeyboard(context, it) }
        }
        if (onRestart != null) {
            Pill("重开", color = Glass.Amber) { onRestart() }
        }
        if (!fullscreen) {
            Pill("全屏", color = Glass.Accent) { onFullscreen() }
        }
    }
}
