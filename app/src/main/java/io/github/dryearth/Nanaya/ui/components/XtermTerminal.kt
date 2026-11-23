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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
    output: StateFlow<String>,
    connected: StateFlow<Boolean>,
    onWrite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rawOutput by output.collectAsState()
    val isConnected by connected.collectAsState()

    var ready by remember { mutableStateOf(false) }
    var sentChars by remember { mutableStateOf(0) }
    var pendingSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sizeApplied by remember { mutableStateOf(false) }

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
                webView.post {
                    pendingSize = cols to rows
                }
            }

            @JavascriptInterface
            fun ready() {
                webView.post {
                    ready = true
                    sentChars = 0
                }
            }
        }
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.loadUrl("file:///android_asset/xterm/index.html")
    }

    // Size the guest PTY once. The Droidspaces PTY is created inside the
    // container, so the host pipe has no winsize; stty is the reliable way.
    LaunchedEffect(pendingSize, ready, isConnected, sizeApplied) {
        val size = pendingSize ?: return@LaunchedEffect
        if (!ready || !isConnected || sizeApplied) return@LaunchedEffect
        if (size.first > 0 && size.second > 0) {
            onWrite("stty rows ${size.second} cols ${size.first} 2>/dev/null\n")
            sizeApplied = true
        }
    }

    LaunchedEffect(rawOutput, ready) {
        if (!ready) return@LaunchedEffect
        val current = rawOutput
        if (current.length < sentChars) {
            webView.evaluateJavascript("window.xtermReset()", null)
            sentChars = 0
        }
        if (current.length > sentChars) {
            val delta = current.substring(sentChars)
            val encoded = Base64.encodeToString(delta.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            webView.evaluateJavascript("window.xtermWrite('$encoded')", null)
            sentChars = current.length
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.matchParentSize(),
        )
    }
}
