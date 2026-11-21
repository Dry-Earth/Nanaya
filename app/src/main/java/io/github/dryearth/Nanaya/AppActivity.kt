package io.github.dryearth.Nanaya

import android.content.Intent
import android.os.Bundle
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
import io.github.dryearth.Nanaya.ui.ButlerApp
import io.github.dryearth.Nanaya.ui.components.LiquidGlassHost
import io.github.dryearth.Nanaya.ui.theme.Glass
import io.github.dryearth.Nanaya.ui.theme.SolidButlerTheme

class AppActivity : ComponentActivity() {
    private var assistRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openChat = intent?.getBooleanExtra("open_chat", false) == true
        assistRequest = openChat
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            SolidButlerTheme {
                LiquidGlassHost {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_chat", false)) assistRequest = true
    }

}
