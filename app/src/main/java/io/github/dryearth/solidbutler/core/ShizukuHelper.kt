package io.github.dryearth.solidbutler.core

import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Shizuku is used only as a shell executor. The app never asks for the
 * Shizuku "system UID" mode: every command is a normal shell command.
 */
object ShizukuHelper {

    const val REQUEST_CODE = 4217

    fun isInstalled(): Boolean = runCatching {
        Shizuku.pingBinder()
        true
    }.getOrDefault(false)

    fun isReady(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.isPreV11()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun needsPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && !isReady()
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    fun exec(cmd: String, timeoutMs: Long): ShellResult {
        if (!isReady()) return ShellResult(-2, "", "Shizuku 未授权")
        return runCatching {
            val process = newProcess(arrayOf("sh", "-c", cmd))
            var out = ""
            var err = ""
            val t1 = Thread { out = process.inputStream.bufferedReader().use { it.readText() } }
            val t2 = Thread { err = process.errorStream.bufferedReader().use { it.readText() } }
            t1.start(); t2.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroy()
            t1.join(1_500); t2.join(1_500)
            ShellResult(if (finished) process.exitValue() else 124, out, err)
        }.getOrElse { ShellResult(-1, "", it.message ?: "Shizuku exec failed") }
    }

    fun binder(): IBinder? = runCatching { Shizuku.getBinder() }.getOrNull()

    /**
     * Shizuku.newProcess is marked @RestrictTo in recent api artifacts, so the
     * Kotlin compiler refuses the direct call. The method itself is public on
     * the binder side, so invoke it reflectively.
     */
    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }
}
