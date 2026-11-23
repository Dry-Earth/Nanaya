package io.github.dryearth.Nanaya.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.dryearth.Nanaya.core.TerminalFrame
import kotlinx.coroutines.flow.StateFlow

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
    connected: StateFlow<Boolean>,
    onWrite: (String) -> Unit,
    onSize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    focusSignal: Int = 0,
) {
    val context = LocalContext.current
    val currentFrame by frame.collectAsState()
    val isConnected by connected.collectAsState()

    var ready by remember { mutableStateOf(false) }
    var sentRevision by remember { mutableStateOf(0L) }

    val webView = remember {
        WebView(context).apply {
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

    LaunchedEffect(Unit) {
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
            fun ready() {
                webView.post {
                    ready = true
                    sentRevision = 0
                }
            }
        }
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.loadUrl("file:///android_asset/xterm/index.html")
    }

    LaunchedEffect(focusSignal, ready) {
        if (ready && focusSignal > 0) {
            webView.evaluateJavascript("window.xtermFocus()", null)
        }
    }

    // Replay only the unseen tail. When the buffer has been trimmed the frame
    // revision tells us the client fell behind, so reset and redraw the buffer.
    LaunchedEffect(currentFrame, ready) {
        if (!ready) return@LaunchedEffect
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
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Give the fit addon a moment to report the real grid.
            kotlinx.coroutines.delay(150)
            webView.evaluateJavascript("window.xtermFocus()", null)
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.matchParentSize(),
        )
    }
}
