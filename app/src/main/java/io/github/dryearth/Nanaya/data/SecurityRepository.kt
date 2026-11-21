package io.github.dryearth.Nanaya.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.Shell
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Local-only security analysis.
 * - tracker SDK detection reads DEX strings inside the installed APK
 * - risk rules combine install source, target SDK and permission combos
 * - firewall uses the kernel's own netfilter via an isolated chain
 */
class SecurityRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val apps: AppRepository,
) {
    private val pm: PackageManager = context.packageManager

    data class TrackerDef(val name: String, val markers: List<String>)

    private val trackerDefs = listOf(
        TrackerDef("Google Analytics", listOf("com/google/android/gms/analytics", "com/google/firebase/analytics")),
        TrackerDef("Google AdMob", listOf("com/google/android/gms/ads", "com/google/ads")),
        TrackerDef("Firebase", listOf("com/google/firebase")),
        TrackerDef("Facebook", listOf("com/facebook/appevents", "com/facebook/ads", "com.facebook.ads")),
        TrackerDef("Adjust", listOf("com/adjust/sdk")),
        TrackerDef("AppsFlyer", listOf("com/appsflyer")),
        TrackerDef("Branch", listOf("io/branch")),
        TrackerDef("Kochava", listOf("com/kochava")),
        TrackerDef("Singular", listOf("com/singular/sdk")),
        TrackerDef("Tenjin", listOf("com/tenjin")),
        TrackerDef("Unity Ads", listOf("com/unity3d/ads", "com/unity3d/services")),
        TrackerDef("AppLovin", listOf("com/applovin")),
        TrackerDef("ironSource", listOf("com/ironsource")),
        TrackerDef("Vungle", listOf("com/vungle")),
        TrackerDef("AdColony", listOf("com/adcolony")),
        TrackerDef("Chartboost", listOf("com/chartboost")),
        TrackerDef("Mintegral", listOf("com/mbridge", "com/mintegral")),
        TrackerDef("Pangle", listOf("com/bytedance/sdk/openadsdk", "com/pangle")),
        TrackerDef("字节跳动 SDK", listOf("com/bytedance")),
        TrackerDef("腾讯广点通", listOf("com/qq/e")),
        TrackerDef("百度联盟", listOf("com/baidu/mobads")),
        TrackerDef("友盟", listOf("com/umeng", "com/uc/crash")),
        TrackerDef("TalkingData", listOf("com/tendcloud")),
        TrackerDef("GrowingIO", listOf("com/growingio")),
        TrackerDef("神策", listOf("com/sensorsdata")),
        TrackerDef("极光推送", listOf("cn/jpush")),
        TrackerDef("个推", listOf("com/igexin")),
        TrackerDef("华为推送", listOf("com/huawei/hms/push")),
        TrackerDef("小米推送", listOf("com/xiaomi/mipush")),
        TrackerDef("OPPO 推送", listOf("com/heytap/msp")),
        TrackerDef("vivo 推送", listOf("com/vivo/push")),
        TrackerDef("Sentry", listOf("io/sentry")),
        TrackerDef("Crashlytics", listOf("com/crashlytics")),
        TrackerDef("Bugly", listOf("com/tencent/bugly")),
        TrackerDef("OneSignal", listOf("com/onesignal")),
        TrackerDef("Mixpanel", listOf("com/mixpanel")),
        TrackerDef("Amplitude", listOf("com/amplitude")),
        TrackerDef("Segment", listOf("com/segment/analytics")),
        TrackerDef("Braze", listOf("com/appboy", "com/braze")),
        TrackerDef("Airship", listOf("com/urbanairship")),
        TrackerDef("InMobi", listOf("com/inmobi")),
        TrackerDef("MoPub", listOf("com/mopub")),
        TrackerDef("Fyber", listOf("com/fyber", "com/digitalturbine")),
        TrackerDef("Ogury", listOf("com/ogury")),
        TrackerDef("Criteo", listOf("com/criteo")),
    )

    fun scanTrackers(
        targets: List<AppEntry>,
        progress: (Int, Int, String) -> Unit,
    ): List<TrackerHit> {
        val out = ArrayList<TrackerHit>()
        targets.forEachIndexed { index, app ->
            progress(index, targets.size, app.label)
            val found = HashSet<String>()
            var error: String? = null
            runCatching {
                ZipFile(File(app.apkPath)).use { zip ->
                    val entries = zip.entries().toList()
                    val names = entries.map { it.name.lowercase(Locale.US) }
                    for (def in trackerDefs) {
                        if (def.markers.any { marker -> names.any { it.contains(marker.lowercase(Locale.US)) } }) {
                            found.add(def.name)
                        }
                    }
                    val remaining = trackerDefs.filter { it.name !in found }
                    if (remaining.isNotEmpty()) {
                        for (entry in entries) {
                            if (!entry.name.endsWith(".dex")) continue
                            if (entry.size <= 0 || entry.size > 40L * 1024 * 1024) continue
                            val text = runCatching {
                                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                            }.getOrNull() ?: continue
                            for (def in remaining) {
                                if (def.markers.any { text.contains(it) }) found.add(def.name)
                            }
                            if (found.size == trackerDefs.size) break
                        }
                    }
                }
            }.onFailure { error = it.message }

            if (found.isNotEmpty() || error != null) {
                out.add(TrackerHit(app.pkg, app.label, found.distinct().sorted(), error))
            }
        }
        return out.sortedByDescending { it.trackers.size }
    }

    fun risks(): List<RiskFinding> {
        val out = ArrayList<RiskFinding>()
        for (app in apps.list()) {
            if (!app.enabled) continue
            val reasons = ArrayList<String>()
            var level = RiskLevel.LOW
            val perms = app.permissions.toSet()
            val sideloaded = runCatching {
                if (app.system) false else {
                    val src = pm.getInstallSourceInfo(app.pkg)
                    src.installingPackageName == null && src.initiatingPackageName == null
                }
            }.getOrDefault(false)

            if (sideloaded) {
                reasons.add("侧载安装，非应用商店来源")
                level = RiskLevel.MEDIUM
            }
            if (app.targetSdk in 1..29 && !app.system) {
                reasons.add("targetSdk ${app.targetSdk}，低于 Android 11 的存储与权限模型")
                if (level == RiskLevel.LOW) level = RiskLevel.MEDIUM
            }
            val sms = "android.permission.READ_SMS" in perms || "android.permission.SEND_SMS" in perms
            val overlay = "android.permission.SYSTEM_ALERT_WINDOW" in perms
            val install = "android.permission.REQUEST_INSTALL_PACKAGES" in perms
            val mic = "android.permission.RECORD_AUDIO" in perms
            val cam = "android.permission.CAMERA" in perms
            val loc = "android.permission.ACCESS_BACKGROUND_LOCATION" in perms
            val contacts = "android.permission.READ_CONTACTS" in perms
            if (sms && overlay) {
                reasons.add("短信权限 + 悬浮窗，可读取验证码并遮挡界面")
                level = RiskLevel.HIGH
            }
            if (install && overlay && !app.system) {
                reasons.add("可安装应用 + 悬浮窗，存在诱导安装风险")
                level = RiskLevel.HIGH
            }
            if (mic && cam && loc && contacts && !app.system) {
                reasons.add("同时申请麦克风、相机、后台位置、联系人")
                if (level != RiskLevel.HIGH) level = RiskLevel.MEDIUM
            }
            if (hasAccessibilityService(app.pkg) && sideloaded) {
                reasons.add("侧载应用注册了无障碍服务")
                level = RiskLevel.HIGH
            }
            if (app.permissions.size > 22 && !app.system) {
                reasons.add("申请 ${app.permissions.size} 项权限")
                if (level == RiskLevel.LOW) level = RiskLevel.MEDIUM
            }
            if (reasons.isNotEmpty()) {
                out.add(RiskFinding(app.pkg, app.label, level, reasons, app.system))
            }
        }
        return out.sortedWith(compareBy<RiskFinding> { it.level.ordinal }.thenByDescending { it.reasons.size })
            .take(50)
    }

    private fun hasAccessibilityService(pkg: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES)
        info.services?.any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE } == true
    }.getOrDefault(false)

    fun activeCaptures(): List<ActiveCapture> {
        if (!Shell.isPrivileged()) return emptyList()
        val out = ArrayList<ActiveCapture>()
        val cam = Shell.run("dumpsys media.camera").text
        if (cam.contains("Active Camera Clients")) {
            val active = cam.substringAfter("Active Camera Clients").take(6000)
            Regex("""Package:\s*([A-Za-z0-9_.]+)""").findAll(active).map { it.groupValues[1] }
                .distinct().take(6).forEach { out.add(ActiveCapture("相机", it, "正在使用摄像头")) }
        }

        val audio = Shell.run("dumpsys audio").text
        val mentionsRecord = audio.lineSequence().any { it.contains("record", true) }
        if (mentionsRecord) {
            audio.lineSequence()
                .filter { it.contains("record", true) || it.contains("active", true) }
                .mapNotNull { Regex("""(?:package|client)[=:\s]+([A-Za-z][A-Za-z0-9_.]+)""").find(it)?.groupValues?.get(1) }
                .distinct()
                .take(5)
                .forEach { out.add(ActiveCapture("麦克风", it, "音频输入处于活动状态")) }
        }
        return out.distinctBy { it.kind + it.pkg }
    }

    fun firewallSupported(): Boolean = Shell.rootReady && Shell.hasCommand("iptables")

    fun applyFirewall(blockedUids: Set<Int>): Pair<Boolean, String> {
        if (!Shell.rootReady) return false to "防火墙需要 Root（内核 netfilter）"
        if (!Shell.hasCommand("iptables")) return false to "系统没有 iptables"
        val rules = buildString {
            appendLine("IPT=iptables")
            appendLine("\$IPT -w -N BUTLER_FW 2>/dev/null")
            appendLine("\$IPT -w -C OUTPUT -j BUTLER_FW 2>/dev/null || \$IPT -w -I OUTPUT 1 -j BUTLER_FW")
            appendLine("\$IPT -w -F BUTLER_FW")
            blockedUids.filter { it > 0 }.forEach { uid ->
                appendLine("\$IPT -w -A BUTLER_FW -m owner --uid-owner $uid -j REJECT")
            }
            appendLine("command -v ip6tables >/dev/null 2>&1 && { ip6tables -w -N BUTLER_FW6 2>/dev/null; ip6tables -w -C OUTPUT -j BUTLER_FW6 2>/dev/null || ip6tables -w -I OUTPUT 1 -j BUTLER_FW6; ip6tables -w -F BUTLER_FW6; }")
            blockedUids.filter { it > 0 }.forEach { uid ->
                appendLine("command -v ip6tables >/dev/null 2>&1 && ip6tables -w -A BUTLER_FW6 -m owner --uid-owner $uid -j REJECT")
            }
            appendLine("echo RULES_OK")
        }
        val r = Shell.runAs(Shell.Backend.ROOT, rules.toString(), 30_000)
        return (r.text.contains("RULES_OK") && !r.text.contains("Permission denied")) to
            (r.text.ifBlank { "完成" } + "\n已拦截 ${blockedUids.size} 个 UID")
    }

    fun clearFirewall(): Pair<Boolean, String> {
        if (!Shell.rootReady) return false to "需要 Root"
        val cmd = "iptables -w -F BUTLER_FW 2>/dev/null; iptables -w -D OUTPUT -j BUTLER_FW 2>/dev/null; " +
            "ip6tables -w -F BUTLER_FW6 2>/dev/null; ip6tables -w -D OUTPUT -j BUTLER_FW6 2>/dev/null; echo CLEARED"
        val r = Shell.runAs(Shell.Backend.ROOT, cmd)
        return r.text.contains("CLEARED") to r.text.ifBlank { "完成" }
    }
}
