package io.github.dryearth.solidbutler.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.dryearth.solidbutler.MainActivity
import io.github.dryearth.solidbutler.service.AssistOverlayService

/** Invisible bridge for ACTION_ASSIST: show the floating glass assistant when allowed. */
class AssistLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AssistOverlayService::class.java),
                )
            }
        } else {
            Toast.makeText(this, "悬浮助手需要显示在其他应用上层权限", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("open_chat", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
