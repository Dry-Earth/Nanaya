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
        ShizukuHelper.init(this)
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
            val prefs = Prefs(this)
            val installed = packageManager.getPackageInfo("moe.shizuku.manager", 0) != null
            if (installed && !ShizukuHelper.isReady() && !prefs.shizukuAsked) {
                prefs.shizukuAsked = true
                ShizukuHelper.requestPermission()
            }
        }
        runCatching {
            ShizukuHelper.addPermissionListener { requestCode, grantResult ->
                ShizukuHelper.ensureBound()
                Shell.refreshShizuku()
            }
        }
    }
}
