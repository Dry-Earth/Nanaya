package io.github.dryearth.solidbutler

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import io.github.dryearth.solidbutler.service.AssistOverlayService

/**
 * No-display entry for the system assistant. It starts the overlay service and
 * finishes without bringing the main app task to the foreground, so waking the
 * assistant does not flash the last page of the app.
 */
open class AssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this) &&
            AssistOverlayService.show(this, voice = true)
        ) {
            finish()
            return
        }
        startActivity(
            Intent(this, AppActivity::class.java)
                .putExtra("open_chat", true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        finish()
    }
}
