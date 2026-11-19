package io.github.dryearth.solidbutler

import android.app.Application
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.Shell
import io.github.dryearth.solidbutler.service.BatteryGuardService
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SolidButlerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/",
                "Llineageos/",
                "Lvendor/",
                "Landroid/app/",
                "Landroid/content/pm/",
            )
        }
        runCatching {
            if (Prefs(this).batteryGuardEnabled) BatteryGuardService.start(this)
        }
    }
}
