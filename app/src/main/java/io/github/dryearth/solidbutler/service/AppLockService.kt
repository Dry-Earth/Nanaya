package io.github.dryearth.solidbutler.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dryearth.solidbutler.core.Prefs

/**
 * Application lock. Reads only the foreground package name, then draws an
 * accessibility overlay with a local PIN pad. No screen content is read.
 */
class AppLockService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var overlayPkg: String? = null
    private var dots: TextView? = null
    private var errorLine: TextView? = null
    private val entered = StringBuilder()

    private var lastUnlockedPkg: String? = null
    private var lastUnlockedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = Prefs(this)
        if (!prefs.appLockEnabled || !prefs.hasPin()) {
            hideOverlay()
            return
        }
        if (pkg !in prefs.lockedPackages()) {
            if (overlayPkg != null && overlayPkg != pkg) hideOverlay()
            return
        }
        if (pkg == lastUnlockedPkg && SystemClock.elapsedRealtime() - lastUnlockedAt < 60_000) return
        if (overlayPkg == pkg) return
        showOverlay(pkg, prefs)
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay(pkg: String, prefs: Prefs) {
        hideOverlay()
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val label = runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(7, 8, 11))
            setPadding(dp(28), dp(48), dp(28), dp(48))
        }

        root.addView(TextView(this).apply {
            text = "汐 · 应用锁"
            setTextColor(Color.parseColor("#98A2B0"))
            textSize = 12f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#EDF0F5"))
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
        })

        dots = TextView(this).apply {
            text = "○ ○ ○ ○"
            setTextColor(Color.parseColor("#AFC6FF"))
            textSize = 20f
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(dots)

        errorLine = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#F28B8B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(errorLine)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        fun submit() {
            if (entered.isEmpty()) return
            if (prefs.verifyPin(entered.toString())) {
                lastUnlockedPkg = pkg
                lastUnlockedAt = SystemClock.elapsedRealtime()
                entered.clear()
                hideOverlay()
            } else {
                entered.clear()
                dots?.text = "○ ○ ○ ○"
                errorLine?.text = "PIN 错误"
            }
        }

        fun key(labelText: String, action: (() -> Unit)? = null) = TextView(this).apply {
            text = labelText
            setTextColor(Color.parseColor("#EDF0F5"))
            textSize = if (labelText.length > 1) 14f else 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12FFFFFF"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#20FFFFFF"))
            }
            isClickable = true
            setOnClickListener {
                if (action != null) action() else {
                    if (entered.length < 8) entered.append(labelText)
                    dots?.text = "●".repeat(entered.length) + "○".repeat((4 - entered.length).coerceAtLeast(0))
                    errorLine?.text = ""
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }

        for (row in 0 until 3) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            for (col in 1..3) {
                val n = row * 3 + col
                line.addView(key(n.toString()))
            }
            grid.addView(line)
        }
        val last = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        last.addView(key("清除") {
            entered.clear()
            dots?.text = "○ ○ ○ ○"
            errorLine?.text = ""
        })
        last.addView(key("0"))
        last.addView(key("确认") {
            submit()
        })
        grid.addView(last)

        root.addView(
            grid,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            android.graphics.PixelFormat.OPAQUE,
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching { wm.addView(root, params) }
        overlay = root
        overlayPkg = pkg
        entered.clear()
        dots?.text = "○ ○ ○ ○"
        errorLine?.text = ""
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        runCatching { windowManager?.removeView(view) }
        overlay = null
        overlayPkg = null
        entered.clear()
    }
}
