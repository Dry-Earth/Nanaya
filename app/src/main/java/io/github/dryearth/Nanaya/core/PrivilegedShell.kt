package io.github.dryearth.Nanaya.core

import android.os.SystemClock
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Batched privileged access for hardware nodes.
 *
 * Many vendor sysfs entries (`kgsl-3d0`, thermal zones, some `power_supply`
 * files) cannot be opened by the app's own `untrusted_app` SELinux domain, even
 * when the app holds a root grant. Scene reads them by running `cat` inside a
 * long-lived root shell instead of reading the file from the app process.
 *
 * This object keeps one `su -c sh` alive and streams batched reads through it.
 * When root is unavailable it falls back to wireless ADB and finally to direct
 * `File` reads, which still cover world-readable nodes.
 */
object PrivilegedShell {
    private const val CACHE_TTL_MS = 300L
    private const val READ_TIMEOUT_MS = 6_000L

    private const val FILE_MARK = "@@NANAYA_FILE@@"
    private const val END_MARK = "@@NANAYA_END@@"
    private const val MISS_MARK = "@@NANAYA_MISS@@"
    private const val DONE_PREFIX = "@@NANAYA_DONE_"

    private val shellLock = ReentrantLock()
    private val monitor = Object()
    private val output = StringBuilder()
    private var rootProcess: Process? = null
    private var rootWriter: BufferedWriter? = null
    private var sequence = 0L
    private var cacheAt = 0L
    private val cache = HashMap<String, CacheEntry>()

    private class CacheEntry(val value: String?, val at: Long)

    /** True while the persistent root shell process is alive. */
    val rootShellRunning: Boolean
        get() = synchronized(monitor) { rootProcess?.isAlive == true }

    /**
     * Reads several text nodes in one round trip. Missing or unreadable paths
     * are omitted from the result instead of throwing.
     */
    fun readAll(paths: Collection<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        shellLock.withLock {
            val now = SystemClock.elapsedRealtime()
            if (now - cacheAt > CACHE_TTL_MS) {
                cache.clear()
                cacheAt = now
            }
            val values = HashMap<String, String>()
            val wanted = ArrayList<String>(paths.size)
            for (path in paths) {
                if (path.isBlank()) continue
                val hit = cache[path]
                if (hit != null && now - hit.at <= CACHE_TTL_MS) {
                    hit.value?.let { values[path] = it }
                } else {
                    wanted.add(path)
                }
            }
            if (wanted.isEmpty()) return values
            val fresh = fetch(wanted)
            for (path in wanted) {
                val value = fresh[path]
                cache[path] = CacheEntry(value, now)
                if (value != null) values[path] = value
            }
            return values
        }
    }

    fun read(path: String): String? = readAll(listOf(path))[path]

    fun readLong(path: String, fallback: Long = -1L): Long =
        firstNumber(read(path)) ?: fallback

    /** Lists `dir` through the same channel, so vendor dirs stay visible. */
    fun listDir(dir: String): List<String> {
        val script = buildString {
            append(pathPrefix())
            append("for f in ").append(shQuote(dir)).append("/*; do ")
            append("[ -e \"\$f\" ] && printf '%s\\n' \"\$f\"; done\n")
            append(":")
        }
        val text = execText(script)
        if (text != null) {
            val entries = text.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("/") }
                .toList()
            if (entries.isNotEmpty()) return entries
        }
        return runCatching { File(dir).listFiles()?.map { it.absolutePath } }.getOrNull().orEmpty()
    }

    /** Runs a trusted shell command, preferring the persistent root shell. */
    fun run(command: String, timeoutMs: Long = 15_000L): ShellResult {
        shellLock.withLock {
            runRoot(command, timeoutMs)?.let { return it }
        }
        if (AdbManager.connected()) {
            AdbManager.run(command, timeoutMs)?.let {
                return ShellResult(it.code, it.output)
            }
        }
        return Shell.runAs(Shell.Backend.APP, command, timeoutMs)
    }

    /** Drops cached values and restarts the shell after a privilege change. */
    fun reset() {
        shellLock.withLock {
            cache.clear()
            cacheAt = 0L
            stopRoot()
        }
    }

    fun close() = reset()

    // ---------------- internals ----------------

    private fun fetch(paths: List<String>): Map<String, String> {
        val text = execText(buildReadScript(paths))
        if (text != null) return parseRead(text)
        return directRead(paths)
    }

    private fun execText(script: String): String? {
        shellLock.withLock {
            val result = runRoot(script, READ_TIMEOUT_MS)
            if (result != null) {
                if (result.code == 0) return result.out
                return null
            }
        }
        if (AdbManager.connected()) {
            val result = AdbManager.run(script, READ_TIMEOUT_MS)
            if (result != null && result.code == 0) return result.output
        }
        return null
    }

    private fun buildReadScript(paths: List<String>): String = buildString {
        append(pathPrefix())
        for (path in paths) {
            val quoted = shQuote(path)
            append("printf '\\n").append(FILE_MARK).append("%s\\n' ").append(quoted).append('\n')
            append("cat ").append(quoted).append(" 2>/dev/null || printf '")
                .append(MISS_MARK).append("\\n'\n")
            append("printf '\\n").append(END_MARK).append("\\n'\n")
        }
        // adb appends its own redirect to the last line; keep it harmless.
        append(':')
    }

    private fun parseRead(text: String): Map<String, String> {
        val values = HashMap<String, String>()
        var index = 0
        while (true) {
            val start = text.indexOf(FILE_MARK, index)
            if (start < 0) break
            val pathStart = start + FILE_MARK.length
            val pathEnd = text.indexOf('\n', pathStart)
            if (pathEnd < 0) break
            val path = text.substring(pathStart, pathEnd).trim()
            val bodyEnd = text.indexOf("\n$END_MARK", pathEnd)
            if (bodyEnd < 0) break
            val body = text.substring(pathEnd + 1, bodyEnd)
            index = bodyEnd + END_MARK.length + 1
            if (body.trim() == MISS_MARK) continue
            if (path.isNotEmpty()) values[path] = body.trim()
        }
        return values
    }

    private fun directRead(paths: List<String>): Map<String, String> {
        val values = HashMap<String, String>()
        for (path in paths) {
            val text = runCatching { File(path).readText().trim() }.getOrNull() ?: continue
            values[path] = text
        }
        return values
    }

    private fun pathPrefix(): String =
        "PATH=/system/bin:/system/xbin:/sbin:\$PATH\n"

    private fun shQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun runRoot(script: String, timeoutMs: Long): ShellResult? {
        shellLock.withLock {
            if (!ensureRoot()) return null
            val writer = synchronized(monitor) { rootWriter } ?: return null
            val id = synchronized(monitor) { ++sequence }
            val marker = "$DONE_PREFIX${id}@@"
            synchronized(monitor) {
                output.setLength(0)
                monitor.notifyAll()
            }
            val payload = buildString {
                append(script)
                append("\nprintf '\\n").append(marker).append("%s\\n' \"\$?\"\n")
            }
            try {
                writer.write(payload)
                writer.flush()
            } catch (_: Exception) {
                stopRoot()
                return null
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(monitor) {
                while (true) {
                    val text = output.toString()
                    val at = text.indexOf(marker)
                    if (at >= 0) {
                        val body = text.substring(0, at)
                        val end = text.indexOf('\n', at + marker.length)
                        val codeText = if (end >= 0) {
                            text.substring(at + marker.length, end)
                        } else {
                            text.substring(at + marker.length)
                        }
                        val code = codeText.trim().toIntOrNull() ?: -1
                        val consume = if (end >= 0) end + 1 else text.length
                        output.delete(0, consume)
                        return ShellResult(code, body.trimEnd())
                    }
                    val alive = rootProcess?.isAlive == true
                    if (!alive && output.isEmpty()) return null
                    if (!alive) break
                    val remain = deadline - System.currentTimeMillis()
                    if (remain <= 0) break
                    try {
                        monitor.wait(remain.coerceAtMost(250L))
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            // A timed-out command may still be running inside the shell. Drop it
            // so the next call starts from a clean state.
            stopRoot()
            return ShellResult(124, "", "shell 超时")
        }
    }

    private fun ensureRoot(): Boolean {
        synchronized(monitor) {
            rootProcess?.let { if (it.isAlive) return true }
        }
        stopRoot()
        if (!Shell.detectRoot()) return false
        val su = Shell.rootShellPath() ?: return false
        return runCatching {
            val process = ProcessBuilder(su, "-c", "exec /system/bin/sh")
                .redirectErrorStream(true)
                .start()
            synchronized(monitor) {
                rootProcess = process
                rootWriter = process.outputStream.bufferedWriter()
                output.setLength(0)
            }
            val input: InputStream = process.inputStream
            Thread {
                val bytes = ByteArray(8_192)
                try {
                    while (true) {
                        val read = input.read(bytes)
                        if (read <= 0) break
                        synchronized(monitor) {
                            output.append(String(bytes, 0, read, Charsets.UTF_8))
                            monitor.notifyAll()
                        }
                    }
                } catch (_: Exception) {
                    // The shell died; the next call restarts it.
                } finally {
                    synchronized(monitor) {
                        if (rootProcess === process) {
                            rootProcess = null
                            rootWriter = null
                        }
                        monitor.notifyAll()
                    }
                }
            }.apply {
                isDaemon = true
                name = "nanaya-privileged-shell"
            }.start()
            true
        }.getOrDefault(false)
    }

    private fun stopRoot() {
        val writer: BufferedWriter?
        val process: Process?
        synchronized(monitor) {
            writer = rootWriter
            process = rootProcess
            rootWriter = null
            rootProcess = null
            output.setLength(0)
            monitor.notifyAll()
        }
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
    }

    /**
     * Extracts the first integer/float prefix from a sysfs value such as
     * `"42 %"`, `"1800000"` or `"12345 67890"`.
     */
    fun firstNumber(text: String?): Long? {
        if (text == null) return null
        val match = Regex("""-?\d+""").find(text) ?: return null
        return match.value.toLongOrNull()
    }
}
