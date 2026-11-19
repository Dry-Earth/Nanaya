package io.github.dryearth.solidbutler.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import io.github.dryearth.solidbutler.core.Shell

data class SpecialApp(val pkg: String, val label: String)

data class SpecialGroup(
    val id: String,
    val title: String,
    val description: String,
    val apps: List<SpecialApp>,
    val revocable: Boolean,
)

class SpecialPermissionsRepository(
    private val context: Context,
    private val apps: AppRepository,
) {
    private val appOps: AppOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val dpm: DevicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val power: PowerManager = context.getSystemService(PowerManager::class.java)
    private val pm: PackageManager = context.packageManager

    fun scan(): List<SpecialGroup> {
        val list = apps.list().filter { it.pkg != context.packageName }

        fun byOp(op: String): List<SpecialApp> = list.filter {
            runCatching {
                appOps.unsafeCheckOpNoThrow(op, it.uid, it.pkg) == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }.map { SpecialApp(it.pkg, it.label) }

        val overlay = byOp("android:system_alert_window")
        val write = byOp("android:write_settings")
        val install = byOp("android:request_install_packages")
        val usage = byOp("android:get_usage_stats")

        val accessibilityPkgs = componentPackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val notificationPkgs = componentPackages("enabled_notification_listeners")
        val adminPkgs = runCatching {
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
        val vpnPkgs = runCatching {
            pm.queryIntentServices(Intent("android.net.VpnService"), 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())

        fun byPackages(pkgs: Set<String>): List<SpecialApp> =
            list.filter { it.pkg in pkgs }.map { SpecialApp(it.pkg, it.label) }

        val battery = list.filter {
            runCatching { power.isIgnoringBatteryOptimizations(it.pkg) }.getOrDefault(false)
        }.map { SpecialApp(it.pkg, it.label) }

        return buildList {
            add(SpecialGroup("overlay", "悬浮窗", "能覆盖在其他应用之上，常用于广告与诱导点击", overlay, true))
            add(SpecialGroup("accessibility", "无障碍服务", "能读取界面并模拟点击，权限极高", byPackages(accessibilityPkgs), false))
            add(SpecialGroup("notification", "通知监听", "能读取所有通知内容", byPackages(notificationPkgs), false))
            add(SpecialGroup("admin", "设备管理器", "能锁屏、抹除数据、限制卸载", byPackages(adminPkgs), false))
            add(SpecialGroup("install", "安装未知应用", "能拉起安装器安装 APK", install, true))
            add(SpecialGroup("write", "修改系统设置", "能改亮度、网络、声音等系统设置", write, true))
            add(SpecialGroup("usage", "使用情况访问", "能读取应用使用记录与前台时间", usage, true))
            add(SpecialGroup("battery", "电池优化白名单", "不受 Doze 限制，可长期后台运行", battery, true))
            add(SpecialGroup("vpn", "VPN 服务", "能接管网络流量（不一定正在运行）", byPackages(vpnPkgs), false))
        }.filter { it.apps.isNotEmpty() }
    }

    fun revoke(groupId: String, pkg: String): Pair<Boolean, String> {
        val cmd = when (groupId) {
            "overlay" -> "appops set $pkg SYSTEM_ALERT_WINDOW deny"
            "write" -> "appops set $pkg WRITE_SETTINGS deny"
            "install" -> "appops set $pkg REQUEST_INSTALL_PACKAGES deny"
            "usage" -> "appops set $pkg GET_USAGE_STATS deny"
            "battery" -> "cmd deviceidle whitelist -$pkg"
            else -> return false to "这项权限需要在系统设置里关闭"
        }
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        if (!Shell.isValidPackage(pkg)) return false to "包名无效"
        val r = Shell.run(cmd)
        return (r.ok && !r.text.contains("Error", true)) to r.text.ifBlank { "已收回" }
    }

    private fun componentPackages(key: String): Set<String> {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, key)
        }.getOrNull() ?: return emptySet()
        return raw.split(':').mapNotNull {
            it.substringBefore('/').takeIf { p -> p.contains('.') }
        }.toSet()
    }
}
