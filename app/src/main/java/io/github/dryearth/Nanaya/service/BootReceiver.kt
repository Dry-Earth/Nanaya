package io.github.dryearth.Nanaya.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.data.AppRepository
import io.github.dryearth.Nanaya.data.SecurityRepository
import io.github.dryearth.Nanaya.data.AutomationEngine

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.batteryGuardEnabled) BatteryGuardService.start(context)
        AutomationEngine.onBoot(context)
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
