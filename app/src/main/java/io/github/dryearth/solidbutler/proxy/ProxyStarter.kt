package io.github.dryearth.solidbutler.proxy

import android.content.Context
import io.github.dryearth.solidbutler.core.AdbManager
import io.github.dryearth.solidbutler.core.Shell

/** Starts and checks the shell-UID Nanaya proxy process. */
object ProxyStarter {
    private const val MAIN_CLASS = "io.github.dryearth.solidbutler.proxy.ProxyMainKt"

    fun start(context: Context): String? {
        val apk = context.applicationInfo.sourceDir
        val command = "nohup sh -c 'CLASSPATH=$apk app_process /system/bin $MAIN_CLASS' " +
            ">/dev/null 2>&1 & echo nanaya_proxy_started"
        val result = Shell.runAs(backend(), command, 12_000)
        return if (result.ok && result.text.contains("nanaya_proxy_started")) {
            null
        } else {
            result.text.ifBlank { "启动失败，没有返回输出" }
        }
    }

    fun stop(): String? {
        val result = Shell.runAs(backend(), "pkill -f $MAIN_CLASS; echo stopped", 6_000)
        return if (result.ok) null else result.text.ifBlank { "停止失败" }
    }

    fun running(): Boolean {
        if (!Shell.isPrivileged()) return false
        val result = Shell.runAs(backend(), "ps -A | grep -F $MAIN_CLASS | grep -v grep", 5_000)
        return result.ok && result.text.isNotBlank()
    }

    private fun backend(): Shell.Backend =
        if (Shell.rootReady) Shell.Backend.ROOT
        else if (AdbManager.connected()) Shell.Backend.ADB
        else Shell.Backend.ADB
}
