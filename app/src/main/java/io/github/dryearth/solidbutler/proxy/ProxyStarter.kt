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
        val scriptPath = "/data/local/tmp/nanaya_proxy.sh"
        val script = "printf '%s\\n' " +
            "'exec app_process -Djava.class.path=$apk /system/bin " +
            "--nice-name=$PROCESS_NAME $MAIN_CLASS' " +
            ">$scriptPath; chmod 755 $scriptPath; " +
            "setsid sh $scriptPath >$LOG_PATH 2>&1 </dev/null &"
        val first = Shell.runAs(backend(), script, 8_000)
        if (!first.ok) {
            return first.text.ifBlank { "启动命令执行失败" }
        }
        val ps = Shell.runAs(
            backend(),
            "sleep 2; ps -A | grep -E '$PROCESS_NAME|ProxyMainKt|app_process' | grep -v grep",
            10_000,
        )
        if (ps.text.isNotBlank()) return null
        val diagnostics = Shell.runAs(
            backend(),
            "echo '--- script'; ls -l $scriptPath 2>&1; cat $scriptPath 2>&1; " +
                "echo '--- log file'; ls -l $LOG_PATH 2>&1; cat $LOG_PATH 2>&1; " +
                "echo '--- tools'; command -v setsid; command -v app_process; " +
                "echo '--- logcat'; logcat -d -t 120 2>/dev/null | " +
                "grep -E 'ProxyMain|nanaya_proxy|AndroidRuntime|ClassNotFound|SELinux' | tail -30",
            12_000,
        )
        return diagnostics.text.ifBlank { "进程未启动，日志为空" }
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
