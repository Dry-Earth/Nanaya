package io.github.dryearth.Nanaya

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.dryearth.Nanaya.core.DroidspacesTerminals

/**
 * Pure native terminal screen, modelled on Termux: a plain Activity with a
 * TerminalView and native controls. No Compose view tree means the IME talks
 * directly to TerminalView's InputConnection, as it does in Termux.
 */
class TerminalActivity : ComponentActivity() {
    private var session: DroidspacesTerminals.Session? = null
    private lateinit var terminalView: TerminalView
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: HorizontalScrollView
    private lateinit var exitFullscreen: TextView
    private lateinit var title: TextView

    private var fullscreen = false
    private var backdrop = 0
    private var textSizePx = 0

    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val name = intent.getStringExtra(EXTRA_NAME)
        val pid = intent.getIntExtra(EXTRA_PID, 0)
        val rootfs = intent.getStringExtra(EXTRA_ROOTFS).orEmpty()
        if (name.isNullOrBlank()) {
            finish()
            return
        }

        session = DroidspacesTerminals.getOrOpen(this, name, pid, rootfs)

        terminalView = TerminalView(this, null).apply {
            isFocusableInTouchMode = true
            defaultFocusHighlightEnabled = false
            setBackgroundColor(backdropColor())
            textSizePx = (13f * resources.displayMetrics.scaledDensity).toInt().coerceIn(24, 96)
            setTextSize(textSizePx)
            setTerminalViewClient(viewClient)
            setKeepScreenOn(true)
        }

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        topBar = buildTopBar(name)
        content.addView(
            topBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        content.addView(
            terminalView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        bottomBar = buildBottomBar()
        content.addView(
            bottomBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        exitFullscreen = textButton("退出全屏") { setFullscreen(false) }.apply {
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(0xCC10151B.toInt(), 20f)
        }
        root.addView(
            exitFullscreen,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(12)
                rightMargin = dp(12)
            },
        )

        setContentView(root)
        applyInsets()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (fullscreen) setFullscreen(false) else finish()
                }
            },
        )

        terminalView.post { terminalView.attachSession(session?.terminalSession) }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val top = if (fullscreen) 0 else bars.top
            val bottom = maxOf(if (fullscreen) 0 else bars.bottom, ime.bottom)
            v.setPadding(bars.left, top, bars.right, bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun buildTopBar(name: String): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0B0E13.toInt())
            setPadding(dp(10), dp(6), dp(8), dp(6))
        }
        val back = textButton("返回") { finish() }
        bar.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        title = TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#EDF0F5"))
            textSize = 16f
            maxLines = 1
            setPadding(dp(10), 0, dp(8), 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(textButton("背景") { cycleBackdrop() })
        bar.addView(textButton("全屏") { setFullscreen(true) })
        return bar
    }

    private fun buildBottomBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0B0E13.toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        fun key(label: String, text: String, color: Int = 0x22FFFFFF) {
            row.addView(
                textButton(label) { session?.write(text) }.apply {
                    background = rounded(color, 14f)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(5)
                },
            )
        }
        key("Ctrl+C", "\u0003", 0x33F28B8B.toInt())
        key("Ctrl+D", "\u0004", 0x33F28B8B.toInt())
        key("Ctrl+Z", "\u001a", 0x33F28B8B.toInt())
        key("Ctrl+L", "\u000c")
        key("Tab", "\t")
        key("Esc", "\u001b")
        key("↑", "\u001b[A")
        key("↓", "\u001b[B")
        key("←", "\u001b[D")
        key("→", "\u001b[C")
        key("Enter", "\r")
        key("PgUp", "\u001b[5~")
        key("PgDn", "\u001b[6~")
        row.addView(
            textButton("键盘") { showKeyboard() }.apply { background = rounded(0x338ADBB4, 14f) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(5)
            },
        )
        scroll.addView(row)
        return scroll
    }

    private fun textButton(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#DCE3EC"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = false
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(color)
        }

    private fun backdropColor(): Int = when (backdrop) {
        0 -> Color.parseColor("#F20B0E13")
        1 -> Color.BLACK
        else -> Color.parseColor("#D90B0E13")
    }

    private fun cycleBackdrop() {
        backdrop = (backdrop + 1) % 3
        terminalView.setBackgroundColor(backdropColor())
    }

    private fun setFullscreen(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled
        topBar.visibility = if (enabled) View.GONE else View.VISIBLE
        bottomBar.visibility = if (enabled) View.GONE else View.VISIBLE
        exitFullscreen.visibility = if (enabled) View.VISIBLE else View.GONE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        applyInsets()
        if (!enabled) terminalView.requestFocus()
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        terminalView.postDelayed({
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(terminalView, 0)
        }, 120)
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                val next = (textSizePx * scale).toInt().coerceIn(18, 96)
                textSizePx = next
                terminalView.setTextSize(next)
                return 1f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent) {
            showKeyboard()
        }

        override fun shouldBackButtonBeMappedToEscape() = false

        override fun shouldEnforceCharBasedInput() = false

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

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false

        override fun onEmulatorSet() {
            session?.syncContainerTty()
        }

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

    override fun onDestroy() {
        runCatching { terminalView.attachSession(null) }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        const val EXTRA_NAME = "container"
        const val EXTRA_PID = "pid"
        const val EXTRA_ROOTFS = "rootfs"

        fun intent(
            context: Context,
            name: String,
            pid: Int,
            rootfs: String,
        ): Intent = Intent(context, TerminalActivity::class.java).apply {
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_PID, pid)
            putExtra(EXTRA_ROOTFS, rootfs)
        }
    }
}
