package io.github.dryearth.solidbutler.core

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

data class Capabilities(
    val root: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuReady: Boolean = false,
    val allFiles: Boolean = false,
    val usageAccess: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val accessibility: Boolean = false,
    val lineageHealth: Boolean = false,
    val device: String = "",
    val android: String = "",
    val backend: String = "普通权限",
) {
    val privileged: Boolean get() = root || shizukuReady
}

object CapabilityProbe {

    fun probe(context: Context, detectRoot: Boolean = true, forceRoot: Boolean = false): Capabilities {
        val pm = context.packageManager
        val shizukuInstalled = runCatching {
            pm.getPackageInfo("rikka.shizuku", 0)
            true
        }.getOrDefault(false) || ShizukuHelper.isInstalled()

        if (detectRoot) Shell.detectRoot(force = forceRoot)
        Shell.refreshShizuku()

        val usage = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        val accessibility = runCatching {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return@runCatching false
            enabled.split(':').any {
                it.startsWith("${context.packageName}/")
            }
        }.getOrDefault(false)

        val batteryUnrestricted = runCatching {
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
        val lineage = pm.hasSystemFeature("org.lineageos.health") && LineageHealth.bind()

        return Capabilities(
            root = Shell.rootReady,
            shizukuInstalled = shizukuInstalled,
            shizukuReady = Shell.shizukuReady,
            allFiles = isExternalManager(),
            usageAccess = usage,
            batteryUnrestricted = batteryUnrestricted,
            accessibility = accessibility,
            lineageHealth = lineage,
            device = Shell.deviceLine(),
            android = Shell.androidLine(),
            backend = Shell.backendLabel(),
        )
    }

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}
