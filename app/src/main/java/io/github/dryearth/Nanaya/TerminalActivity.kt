package io.github.dryearth.Nanaya

import android.content.Context
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
import io.github.dryearth.Nanaya.ui.screens.DroidspacesTerminalScreen
import io.github.dryearth.Nanaya.ui.theme.Glass
import io.github.dryearth.Nanaya.ui.theme.SolidButlerTheme

/**
 * A plain Activity for the container terminal, the same shape Termux uses.
 * Keeping the terminal out of the app's Compose/LiquidGlassHost tree avoids the
 * backdrop capture that runs around every native view invalidation.
 */
class TerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val name = intent.getStringExtra(EXTRA_NAME)
        val pid = intent.getIntExtra(EXTRA_PID, 0)
        val rootfs = intent.getStringExtra(EXTRA_ROOTFS).orEmpty()
        if (name.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            SolidButlerTheme {
                Box(Modifier.fillMaxSize().background(Glass.Ink)) {
                    DroidspacesTerminalScreen(
                        containerName = name,
                        containerPid = pid,
                        rootfs = rootfs,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

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
