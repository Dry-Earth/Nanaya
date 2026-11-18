package io.github.dryearth.solidbutler.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val code: Int,
    val out: String,
    val err: String = "",
) {
    val ok: Boolean get() = code == 0
    val text: String get() = (out + if (err.isNotBlank()) "\n$err" else "").trim()
}

/**
 * Single entry point for privileged work.
 * Order: root shell -> Shizuku shell -> app shell.
 *
 * Everything is executed through the platform's own shell commands
 * (pm / appops / cmd / dumpsys / iptables), not through private hooks.
 */
object Shell {

    @Volatile var rootReady: Boolean = false
        private set
    @Volatile var shizukuReady: Boolean = false
        private set

    @Volatile private var rootChecked = false

    private const val DEFAULT_TIMEOUT = 25_000L

    fun detectRoot(force: Boolean = false): Boolean {
        if (rootReady) return true
        if (rootChecked && !force) return false
        rootChecked = true
        rootReady = runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = p.waitFor(5, TimeUnit.SECONDS)
            val text = p.inputStream.bufferedReader().readText()
            if (!finished) p.destroyForcibly()
            finished && text.contains("uid=0")
        }.getOrDefault(false)
        return rootReady
    }

    fun refreshShizuku() {
        shizukuReady = ShizukuHelper.isReady()
    }

    fun run(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT): ShellResult {
        return when {
            rootReady || detectRoot() -> exec(arrayOf("su", "-c", cmd), timeoutMs)
            shizukuReady -> runCatching {
                ShizukuHelper.exec(cmd, timeoutMs)
            }.getOrElse { exec(arrayOf("sh", "-c", cmd), timeoutMs) }
            else -> exec(arrayOf("sh", "-c", cmd), timeoutMs)
        }
    }

    /** Force one backend, used by the UI to preview the exact command. */
    fun runAs(backend: Backend, cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT): ShellResult {
        return when (backend) {
            Backend.ROOT -> exec(arrayOf("su", "-c", cmd), timeoutMs)
            Backend.SHIZUKU -> ShizukuHelper.exec(cmd, timeoutMs)
            Backend.APP -> exec(arrayOf("sh", "-c", cmd), timeoutMs)
        }
    }

    private fun exec(argv: Array<String>, timeoutMs: Long): ShellResult {
        return runCatching {
            val process = ProcessBuilder(*argv).redirectErrorStream(false).start()
            var out = ""
            var err = ""
            val t1 = Thread { out = process.inputStream.bufferedReader().use { it.readText() } }
            val t2 = Thread { err = process.errorStream.bufferedReader().use { it.readText() } }
            t1.start(); t2.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            t1.join(1_500); t2.join(1_500)
            ShellResult(if (finished) process.exitValue() else 124, out, err)
        }.getOrElse { ShellResult(-1, "", it.message ?: "exec failed") }
    }

    enum class Backend { ROOT, SHIZUKU, APP }

    fun backend(): Backend = when {
        rootReady -> Backend.ROOT
        shizukuReady -> Backend.SHIZUKU
        else -> Backend.APP
    }

    fun backendLabel(): String = when (backend()) {
        Backend.ROOT -> "Root"
        Backend.SHIZUKU -> "Shizuku"
        Backend.APP -> "普通权限"
    }

    fun isPrivileged(): Boolean = rootReady || shizukuReady

    fun hasLineageHealth(context: Context): Boolean =
        context.packageManager.hasSystemFeature("org.lineageos.health") &&
            LineageHealth.bind()

    fun storageStats(): Triple<Long, Long, Long> {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        return Triple(stat.totalBytes(), stat.usedBytes(), stat.freeBytes())
    }

    fun deviceLine(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun androidLine(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun hasCommand(name: String): Boolean {
        if (!isPrivileged()) return false
        val r = run("command -v $name || which $name")
        return r.ok && r.text.isNotBlank()
    }

    fun readText(path: String): String? = runCatching {
        File(path).readText().trim()
    }.getOrNull()
}
