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
        // 0.7 behaviour: always open the full conversation, no overlay dependency.
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("open_chat", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
