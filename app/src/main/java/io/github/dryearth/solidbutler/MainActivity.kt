package io.github.dryearth.solidbutler

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
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dryearth.solidbutler.ui.ButlerApp
import io.github.dryearth.solidbutler.ui.theme.Glass
import io.github.dryearth.solidbutler.ui.theme.SolidButlerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            SolidButlerTheme {
                val vm: ButlerViewModel = viewModel()
                Box(Modifier.fillMaxSize().background(Glass.Ink)) {
                    ButlerApp(vm)
                }
            }
        }
    }
}
