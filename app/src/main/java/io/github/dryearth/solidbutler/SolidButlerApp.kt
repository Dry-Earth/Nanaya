package io.github.dryearth.solidbutler

import android.app.Application
import io.github.dryearth.solidbutler.core.ShizukuHelper
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
        runCatching {
            ShizukuHelper.addPermissionListener { requestCode, grantResult ->
                if (requestCode == ShizukuHelper.REQUEST_CODE) {
                    Shell.refreshShizuku()
                }
                if (grantResult != 0) Shell.refreshShizuku()
            }
        }
    }
}
