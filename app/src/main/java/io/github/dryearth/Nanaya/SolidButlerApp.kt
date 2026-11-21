package io.github.dryearth.Nanaya

import android.app.Application
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.service.BatteryGuardService
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
