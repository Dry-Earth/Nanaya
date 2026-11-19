package io.github.dryearth.solidbutler.data

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.NetworkCapabilities
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.os.PowerManager
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.Shell
import java.text.Collator
import java.util.Locale

class AppRepository(
    private val context: Context,
    private val prefs: Prefs,
) {
    private val pm: PackageManager = context.packageManager
    private val appOps: AppOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val power: PowerManager = context.getSystemService(PowerManager::class.java)

    @Volatile private var cachedApps: List<AppEntry> = emptyList()
    @Volatile private var cachedAt = 0L

    @Suppress("DEPRECATION")
    fun list(force: Boolean = false): List<AppEntry> {
        val now = System.currentTimeMillis()
        if (!force && cachedApps.isNotEmpty() && now - cachedAt < 20_000) return cachedApps
        val packages = runCatching { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) }
            .getOrDefault(emptyList())
        val collator = Collator.getInstance(Locale.CHINA)
        val result = packages.mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == context.packageName) return@mapNotNull null
            val setting = runCatching { pm.getApplicationEnabledSetting(ai.packageName) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            AppEntry(
                pkg = ai.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                enabled = ai.enabled && setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                frozen = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                    setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                uid = ai.uid,
                version = info.versionName ?: "",
                targetSdk = ai.targetSdkVersion,
                installedAt = info.firstInstallTime,
                apkPath = ai.sourceDir,
                permissions = info.requestedPermissions?.toList() ?: emptyList(),
            )
        }.sortedWith(compareBy<AppEntry> { it.system }.thenComparator { a, b ->
            collator.compare(a.label, b.label)
        })
        cachedApps = result
        cachedAt = System.currentTimeMillis()
        return result
    }

    fun detail(pkg: String): AppDetail? {
        val app = list().firstOrNull { it.pkg == pkg } ?: return null
        val net = networkUsage(app.uid)
        val perms = app.permissions.map { name ->
            val granted = runCatching {
                pm.checkPermission(name, pkg) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            val info = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
            val dangerous = info != null &&
                (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
            PermissionRow(name, permLabel(name), granted, dangerous)
        }.sortedWith(compareByDescending<PermissionRow> { it.dangerous }.thenBy { it.label })

        return AppDetail(
            app = app,
            permissions = perms,
            bootComponents = bootComponents(pkg),
            backgroundRestricted = isOpIgnored("android:run_in_background", app.uid, pkg),
            wakeLockRestricted = isOpIgnored("android:wake_lock", app.uid, pkg),
            batteryUnrestricted = runCatching { power.isIgnoringBatteryOptimizations(pkg) }.getOrDefault(false),
            locked = pkg in prefs.lockedPackages(),
            whitelisted = pkg in prefs.whitelist(),
            trackers = emptyList(),
            wifiBytes = net.first,
            cellBytes = net.second,
        )
    }

    private fun networkUsage(uid: Int): Pair<Long, Long> {
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
            ?: return -1L to -1L
        val end = System.currentTimeMillis()
        val start = end - 24L * 3600_000
        fun query(transport: Int): Long = runCatching {
            val stats = nsm.queryDetailsForUid(transport, null, start, end, uid)
            val bucket = NetworkStats.Bucket()
            var total = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            stats.close()
            total
        }.getOrDefault(-1L)
        return query(NetworkCapabilities.TRANSPORT_WIFI) to query(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun freeze(pkg: String, freeze: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val cmd = if (freeze) "pm disable-user --user 0 $pkg" else "pm enable $pkg"
        val r = Shell.run(cmd)
        val ok = r.ok && !r.text.contains("Error", true) && !r.text.contains("Exception", true)
        return ok to r.text.ifBlank { if (ok) "完成" else "失败" }
    }

    fun uninstallForUser(pkg: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = Shell.run("pm uninstall --user 0 $pkg")
        val ok = r.text.contains("Success", true)
        return ok to r.text.ifBlank { "失败" }
    }

    fun restoreForUser(pkg: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = Shell.run("cmd package install-existing --user 0 $pkg")
        return r.text.contains("installed", true) to r.text
    }

    fun restrictBackground(pkg: String, uid: Int, restrict: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val mode = if (restrict) "ignore" else "default"
        val r = Shell.run(
            "appops set $pkg RUN_IN_BACKGROUND $mode; " +
                "appops set $pkg RUN_ANY_IN_BACKGROUND $mode"
        )
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "完成" }
    }

    fun restrictWakeLock(pkg: String, restrict: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = Shell.run("appops set $pkg WAKE_LOCK ${if (restrict) "ignore" else "default"}")
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "完成" }
    }

    fun setBatteryUnrestricted(pkg: String, unrestricted: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val cmd = if (unrestricted) "cmd deviceidle whitelist +$pkg" else "cmd deviceidle whitelist -$pkg"
        val r = Shell.run(cmd)
        return r.ok to r.text.ifBlank { "完成" }
    }

    fun revoke(pkg: String, permission: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = Shell.run("pm revoke $pkg $permission")
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "已收回" }
    }

    fun bootComponents(pkg: String): List<BootComponent> {
        val bootActions = listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.CONNECTIVITY_CHANGE",
        )
        val found = linkedMapOf<String, MutableList<String>>()
        for (action in bootActions) {
            val receivers = runCatching {
                pm.queryBroadcastReceivers(
                    android.content.Intent(action).setPackage(pkg),
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.getOrDefault(emptyList())
            for (ri in receivers) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName != pkg) continue
                found.getOrPut(ai.name) { mutableListOf() }
                    .add(action.substringAfterLast('.'))
            }
        }
        return found.map { (name, actions) ->
            val comp = ComponentName(pkg, name)
            val setting = runCatching { pm.getComponentEnabledSetting(comp) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val enabled = setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            BootComponent(
                pkg = pkg,
                component = name,
                shortName = name.substringAfterLast('.'),
                actions = actions.distinct(),
                enabled = enabled,
            )
        }.sortedBy { it.shortName }
    }

    fun autostartApps(): List<AutostartApp> {
        val bootActions = listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.CONNECTIVITY_CHANGE",
        )
        val found = HashMap<String, MutableMap<String, MutableList<String>>>()
        for (action in bootActions) {
            val receivers = runCatching {
                pm.queryBroadcastReceivers(
                    android.content.Intent(action),
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.getOrDefault(emptyList())
            for (ri in receivers) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName ?: continue
                val name = ai.name ?: continue
                found.getOrPut(pkg) { linkedMapOf() }
                    .getOrPut(name) { mutableListOf() }
                    .add(action.substringAfterLast('.'))
            }
        }
        val apps = list().filter { it.pkg in found }
        return apps.mapNotNull { app ->
            val comps = found[app.pkg]?.map { (name, actions) ->
                val comp = ComponentName(app.pkg, name)
                val setting = runCatching { pm.getComponentEnabledSetting(comp) }
                    .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                BootComponent(
                    pkg = app.pkg,
                    component = name,
                    shortName = name.substringAfterLast('.'),
                    actions = actions.distinct(),
                    enabled = setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                        setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                )
            }?.sortedBy { it.shortName } ?: emptyList()
            if (comps.isEmpty()) null else AutostartApp(
                app = app,
                components = comps,
                backgroundRestricted = isOpIgnored("android:run_in_background", app.uid, app.pkg),
            )
        }.sortedWith(
            compareByDescending<AutostartApp> { it.components.count { c -> c.enabled } }
                .thenBy { it.app.system }
                .thenBy { it.app.label }
        )
    }

    fun setStandbyBucket(pkg: String, bucket: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = Shell.run("am set-standby-bucket $pkg $bucket")
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "已设为 $bucket" }
    }

    fun setComponentEnabled(pkg: String, component: String, enabled: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val verb = if (enabled) "enable" else "disable"
        val r = Shell.run("pm $verb --user 0 $pkg/$component")
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "完成" }
    }

    fun isOpIgnored(op: String, uid: Int, pkg: String): Boolean = runCatching {
        appOps.unsafeCheckOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_IGNORED
    }.getOrDefault(false)

    fun permissionApps(permission: String): List<AppEntry> =
        list().filter { it.enabled && permission in it.permissions }

    fun sensitiveApps(): List<PrivacyApp> {
        val map = linkedMapOf<String, MutableList<String>>()
        for (app in list()) {
            if (!app.enabled) continue
            val hits = app.permissions.filter { it in SENSITIVE }.toMutableList()
            if (hits.isNotEmpty()) map[app.pkg] = hits
        }
        val labels = list().associate { it.pkg to it.label }
        return map.map { (pkg, perms) ->
            PrivacyApp(pkg, labels[pkg] ?: pkg, perms)
        }.sortedByDescending { it.permissions.size }
    }

    fun permLabel(name: String): String = when (name) {
        "android.permission.CAMERA" -> "相机"
        "android.permission.RECORD_AUDIO" -> "麦克风"
        "android.permission.ACCESS_FINE_LOCATION" -> "精确位置"
        "android.permission.ACCESS_COARSE_LOCATION" -> "大致位置"
        "android.permission.ACCESS_BACKGROUND_LOCATION" -> "后台位置"
        "android.permission.READ_CONTACTS" -> "读取联系人"
        "android.permission.WRITE_CONTACTS" -> "修改联系人"
        "android.permission.READ_SMS" -> "读取短信"
        "android.permission.SEND_SMS" -> "发送短信"
        "android.permission.READ_CALL_LOG" -> "读取通话记录"
        "android.permission.CALL_PHONE" -> "拨打电话"
        "android.permission.READ_PHONE_STATE" -> "电话状态"
        "android.permission.READ_PHONE_NUMBERS" -> "本机号码"
        "android.permission.BODY_SENSORS" -> "身体传感器"
        "android.permission.READ_CALENDAR" -> "读取日历"
        "android.permission.WRITE_CALENDAR" -> "修改日历"
        "android.permission.READ_EXTERNAL_STORAGE" -> "读取存储"
        "android.permission.WRITE_EXTERNAL_STORAGE" -> "写入存储"
        "android.permission.POST_NOTIFICATIONS" -> "通知"
        "android.permission.SYSTEM_ALERT_WINDOW" -> "悬浮窗"
        "android.permission.PACKAGE_USAGE_STATS" -> "使用情况"
        else -> name.substringAfterLast('.')
    }

    companion object {
        val SENSITIVE = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.BODY_SENSORS",
            "android.permission.READ_CALENDAR",
        )
    }
}
