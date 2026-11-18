package io.github.dryearth.solidbutler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.Shell
import io.github.dryearth.solidbutler.data.AppRepository
import io.github.dryearth.solidbutler.data.SecurityRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.batteryGuardEnabled) BatteryGuardService.start(context)
        if (!prefs.firewallPersist) return
        val uids = prefs.blockedUids()
        if (uids.isEmpty()) return
        Thread {
            runCatching {
                Shell.detectRoot()
                if (Shell.rootReady) {
                    SecurityRepository(context, prefs, AppRepository(context, prefs))
                        .applyFirewall(uids)
                }
            }
        }.start()
    }
}
