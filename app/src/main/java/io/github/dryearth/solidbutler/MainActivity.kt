package io.github.dryearth.solidbutler

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dryearth.solidbutler.service.AssistOverlayService
import io.github.dryearth.solidbutler.ui.ButlerApp
import io.github.dryearth.solidbutler.ui.theme.Glass
import io.github.dryearth.solidbutler.ui.theme.SolidButlerTheme

class MainActivity : ComponentActivity() {
    private var assistRequest by mutableStateOf(false)
    private var visible = false
    private var pendingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isAssist = intent?.action == Intent.ACTION_ASSIST
        val openChat = intent?.getBooleanExtra("open_chat", false) == true
        if (isAssist && Settings.canDrawOverlays(this)) {
            if (AssistOverlayService.show(this, voice = true)) {
                finish()
                return
            }
        }
        assistRequest = isAssist || openChat
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            SolidButlerTheme {
                val vm: ButlerViewModel = viewModel()
                Box(Modifier.fillMaxSize().background(Glass.Ink)) {
                    ButlerApp(
                        vm = vm,
                        startWithChat = assistRequest,
                        onStartChatConsumed = { assistRequest = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        if (pendingBack) {
            pendingBack = false
            window.decorView.postDelayed({ moveTaskToBack(true) }, 80)
        }
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isAssist = intent.action == Intent.ACTION_ASSIST
        val openChat = intent.getBooleanExtra("open_chat", false)
        if (isAssist && Settings.canDrawOverlays(this)) {
            val wasVisible = visible
            if (AssistOverlayService.show(this, voice = true)) {
                assistRequest = false
                if (!wasVisible) {
                    // The assist intent brought the app task forward from the
                    // background. Wait until the service window is attached,
                    // then put the task back so the user's previous app stays
                    // behind the overlay instead of seeing 汐's last page.
                    pendingBack = true
                    window.decorView.postDelayed({
                        if (pendingBack) {
                            pendingBack = false
                            moveTaskToBack(true)
                        }
                    }, 220)
                }
                return
            }
        }
        if (isAssist || openChat) assistRequest = true
    }
}
