package io.github.dryearth.solidbutler.proxy

import android.content.Context
import io.github.dryearth.solidbutler.core.AdbManager
import io.github.dryearth.solidbutler.core.Shell

/** Starts and checks the shell-UID Nanaya proxy process. */
object ProxyStarter {
    private const val MAIN_CLASS = "io.github.dryearth.solidbutler.proxy.ProxyMainKt"
    private const val PROCESS_NAME = "nanaya_proxy"
    private const val LOG_PATH = "/data/local/tmp/nanaya_proxy.log"

    fun start(context: Context): String? {
        val apk = context.applicationInfo.sourceDir
        val command = "rm -f $LOG_PATH; " +
            "nohup sh -c 'export CLASSPATH=$apk; exec app_process /system/bin " +
            "--nice-name=$PROCESS_NAME $MAIN_CLASS' >$LOG_PATH 2>&1 & " +
            "sleep 1; if ps -A | grep -q $PROCESS_NAME; then echo nanaya_proxy_started; " +
            "else cat $LOG_PATH 2>/dev/null || echo '进程启动后立即退出'; fi"
        val result = Shell.runAs(backend(), command, 15_000)
        return if (result.ok && result.text.contains("nanaya_proxy_started")) {
            null
        } else {
            result.text.ifBlank { "启动失败，没有返回输出" }
        }
    }

    fun stop(): String? {
        val result = Shell.runAs(backend(), "pkill -f $PROCESS_NAME; echo stopped", 6_000)
        return if (result.ok) null else result.text.ifBlank { "停止失败" }
    }

    fun running(): Boolean {
        if (!Shell.isPrivileged()) return false
        val result = Shell.runAs(backend(), "ps -A | grep -F $PROCESS_NAME | grep -v grep", 5_000)
        return result.ok && result.text.isNotBlank()
    }

    fun lastLog(): String {
        if (!Shell.isPrivileged()) return ""
        return Shell.runAs(backend(), "cat $LOG_PATH 2>/dev/null", 5_000).text
    }

    private fun backend(): Shell.Backend =
        if (Shell.rootReady) Shell.Backend.ROOT
        else if (AdbManager.connected()) Shell.Backend.ADB
        else Shell.Backend.ADB
}
