package io.github.dryearth.Nanaya.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.dryearth.Nanaya.core.TerminalFrame
import kotlinx.coroutines.flow.StateFlow

/** UI actions wired to the xterm.js instance. */
class XtermController {
    var focus: () -> Unit = {}
    var copy: () -> Unit = {}
    var paste: () -> Unit = {}
    var scrollPages: (Int) -> Unit = {}
    var scrollTop: () -> Unit = {}
    var scrollBottom: () -> Unit = {}
}

/**
 * A full ANSI terminal front end (xterm.js) backed by a byte channel.
 *
 * The channel can come from Droidspaces, a local root shell or an ADB shell:
 * only raw output and [onWrite] are required. xterm.js handles colors, cursor
 * movement, alternate screens, scrollback and TUI programs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XtermTerminal(
    frame: StateFlow<TerminalFrame>,
    onWrite: (String) -> Unit,
    onSize: (Int, Int) -> Unit,
    controller: XtermController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val hasHardwareKeyboard = configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    val currentFrame by frame.collectAsState()

    // With a physical keyboard attached the soft keyboard only covers the
    // prompt. Block the input connection until the user taps 键盘 on purpose.
    var allowIme by remember { mutableStateOf(!hasHardwareKeyboard) }
    var imeWasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(hasHardwareKeyboard) {
        allowIme = !hasHardwareKeyboard
    }

    var ready by remember { mutableStateOf(false) }
    var sentRevision by remember { mutableStateOf(0L) }
    // At most three unacknowledged chunks may be in flight. Without this a
    // fast producer (htop, yes, a big log) floods the WebView JS queue.
    var outstanding by remember { mutableIntStateOf(0) }

    val webView = remember {
        object : WebView(context) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                // Hardware keys arrive through dispatchKeyEvent, not the input
                // connection, so typing still works while the IME is blocked.
                if (!allowIme) return null
                return super.onCreateInputConnection(outAttrs)
            }
        }.apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient = WebViewClient()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { webView.removeJavascriptInterface("AndroidBridge") }
            runCatching { webView.destroy() }
        }
    }

    // Once the user dismisses the soft keyboard, go back to hardware mode.
    DisposableEffect(hasHardwareKeyboard) {
        val listener = object : ViewCompat.OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                if (imeVisible) {
                    imeWasVisible = true
                } else if (imeWasVisible) {
                    imeWasVisible = false
                    if (hasHardwareKeyboard) allowIme = false
                }
                return insets
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(webView, listener)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(
                webView,
                null as ViewCompat.OnApplyWindowInsetsListener?,
            )
        }
    }

    LaunchedEffect(Unit) {
        controller.focus = {
            if (hasHardwareKeyboard) {
                allowIme = true
                webView.requestFocus()
                webView.evaluateJavascript("window.xtermFocus()", null)
                webView.postDelayed({
                    val imm = context.getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                }, 120)
            } else {
                webView.evaluateJavascript("window.xtermFocus()", null)
            }
        }
        controller.copy = {
            webView.evaluateJavascript("window.xtermCopy()", null)
        }
        controller.paste = {
            val text = readClipboard(context)
            if (text.isNullOrEmpty()) {
                Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            } else {
                val encoded = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
                webView.evaluateJavascript("window.xtermPaste('$encoded')", null)
            }
        }
        controller.scrollPages = { pages ->
            webView.evaluateJavascript("window.xtermScroll($pages)", null)
        }
        controller.scrollTop = {
            webView.evaluateJavascript("window.xtermTop()", null)
        }
        controller.scrollBottom = {
            webView.evaluateJavascript("window.xtermBottom()", null)
        }

        val bridge = object {
            @JavascriptInterface
            fun send(base64: String) {
                runCatching {
                    val bytes = Base64.decode(base64, Base64.NO_WRAP)
                    onWrite(String(bytes, Charsets.UTF_8))
                }
            }

            @JavascriptInterface
            fun resize(cols: Int, rows: Int) {
                webView.post { onSize(cols, rows) }
            }

            @JavascriptInterface
            fun copyB64(base64: String) {
                val text = runCatching {
                    String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull() ?: return
                webView.post {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    manager?.setPrimaryClip(ClipData.newPlainText("终端", text))
                    Toast.makeText(context, "已复制 ${text.length} 字", Toast.LENGTH_SHORT).show()
                }
            }

            @JavascriptInterface
            fun ack() {
                webView.post { outstanding = (outstanding - 1).coerceAtLeast(0) }
            }

            @JavascriptInterface
            fun ready() {
                webView.post {
                    ready = true
                    sentRevision = 0
                    outstanding = 0
                }
            }
        }
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.loadUrl("file:///android_asset/xterm/index.html")
    }

    // Replay only the unseen tail. When the buffer has been trimmed the frame
    // revision tells us the client fell behind, so reset and redraw the buffer.
    LaunchedEffect(currentFrame, ready, outstanding) {
        if (!ready || outstanding >= 3) return@LaunchedEffect
        val snapshot = currentFrame
        val start = snapshot.revision - snapshot.text.length
        if (sentRevision < start) {
            webView.evaluateJavascript("window.xtermReset()", null)
            sentRevision = start
        }
        val offset = (sentRevision - start).toInt().coerceIn(0, snapshot.text.length)
        if (snapshot.text.length > offset) {
            val delta = snapshot.text.substring(offset)
            val encoded = Base64.encodeToString(delta.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            webView.evaluateJavascript("window.xtermWrite('$encoded')", null)
            sentRevision = snapshot.revision
            outstanding += 1
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.matchParentSize(),
        )
    }
}

private fun readClipboard(context: android.content.Context): String? = runCatching {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    clip.getItemAt(0).coerceToText(context)?.toString()
}.getOrNull()
