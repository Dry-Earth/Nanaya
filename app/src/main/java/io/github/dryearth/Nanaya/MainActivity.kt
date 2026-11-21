package io.github.dryearth.Nanaya

import android.content.Intent
import android.os.Bundle

/**
 * Compatibility assistant entry. Older assistant selections and shortcuts may
 * still point at this component. Assistant invocations stay no-display; normal
 * launches are forwarded to the real app UI in AppActivity.
 */
class MainActivity : AssistEntryActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.action != Intent.ACTION_ASSIST) {
            startActivity(
                Intent(this, AppActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}
