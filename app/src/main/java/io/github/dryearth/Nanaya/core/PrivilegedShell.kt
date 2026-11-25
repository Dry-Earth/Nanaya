package io.github.dryearth.Nanaya.core

import android.os.SystemClock
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Batched hardware-node reads through the privileged backend.
 *
 * Many vendor sysfs entries (`kgsl-3d0`, thermal zones, some `power_supply`
 * files) cannot be opened from the app's own `untrusted_app` SELinux domain,
 * even when the app holds a root grant. Scene reads them by running `cat`
 * inside a root shell; this does the same thing with one short-lived `su -c`
 * batch per read group.
 *
 * A previous version kept a long-lived `su -c sh` pipe. It was faster in theory
 * but a wedged shell or a slow command sharing its lock froze every metric.
 * Reads now go through the cheapest working path: direct [File] access first,
 * then one [Shell.run] batch for the nodes the app domain could not open.
 * Values are cached for a few hundred milliseconds so several callers inside
 * one sample share the same work.
 */
object PrivilegedShell {
    private const val CACHE_TTL_MS = 300L
    private const val BATCH_TIMEOUT_MS = 2_500L
    private const val MAX_BATCH_FAILURES = 2
    private const val BYPASS_MS = 60_000L

    private const val FILE_MARK = "@@NANAYA_FILE@@"
    private const val END_MARK = "@@NANAYA_END@@"
    private const val MISS_MARK = "@@NANAYA_MISS@@"

    private val lock = ReentrantLock()
    private var cacheAt = 0L
    private var batchFailures = 0
    private var bypassUntil = 0L
    private val cache = HashMap<String, CacheEntry>()

    private class CacheEntry(val value: String?, val at: Long)

    /** Human-readable backend currently used for hardware reads. */
    val channelLabel: String
        get() = when {
            Shell.rootReady -> "Root 批量读取"
            AdbManager.connected() -> "无线 ADB"
            else -> "普通权限"
        }

    /**
     * Reads several text nodes in one round trip. Missing or unreadable paths
     * are omitted from the result instead of throwing.
     */
    fun readAll(paths: Collection<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        lock.withLock {
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
            val privileged = ArrayList<String>()
            for (path in wanted) {
                val direct = readDirect(path)
                if (direct != null) {
                    values[path] = direct
                } else if (runCatching { File(path).exists() }.getOrDefault(false)) {
                    // Only ask root for nodes that exist but the app domain
                    // cannot open. Missing vendor candidates must not spawn su.
                    privileged.add(path)
                }
            }
            if (privileged.isNotEmpty() && Shell.isPrivileged()) {
                val fresh = fetchPrivileged(privileged)
                for (path in privileged) {
                    val value = fresh[path] ?: continue
                    values[path] = value
                }
            }
            for (path in wanted) {
                cache[path] = CacheEntry(values[path], now)
            }
            return values
        }
    }

    fun read(path: String): String? = readAll(listOf(path))[path]

    /** Lists `dir`, preferring the cheap direct listing. */
    fun listDir(dir: String): List<String> {
        val direct = runCatching { File(dir).listFiles()?.map { it.absolutePath } }
            .getOrNull()
            .orEmpty()
        if (direct.isNotEmpty()) return direct
        val result = Shell.run("ls -1 ${shQuote(dir)} 2>/dev/null", BATCH_TIMEOUT_MS)
        if (!result.ok) return emptyList()
        return result.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("/")) it else "$dir/$it" }
            .toList()
    }

    /** Runs a trusted shell command through the normal privileged backend. */
    fun run(command: String, timeoutMs: Long = 15_000L): ShellResult =
        Shell.run(command, timeoutMs)

    /** Drops cached values after a privilege change. */
    fun reset() {
        lock.withLock {
            cache.clear()
            cacheAt = 0L
            batchFailures = 0
            bypassUntil = 0L
        }
    }

    fun close() = reset()

    /**
     * Extracts the first integer prefix from a sysfs value such as
     * `"42 %"`, `"1800000"` or `"12345 67890"`.
     */
    fun firstNumber(text: String?): Long? {
        if (text == null) return null
        val match = Regex("""-?\d+""").find(text) ?: return null
        return match.value.toLongOrNull()
    }

    // ---------------- internals ----------------

    private fun readDirect(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()

    private fun fetchPrivileged(paths: List<String>): Map<String, String> {
        if (System.currentTimeMillis() < bypassUntil) return emptyMap()
        val script = buildReadScript(paths)
        val result = runCatching { Shell.run(script, BATCH_TIMEOUT_MS) }.getOrNull()
        if (result == null || !result.ok) {
            batchFailures++
            if (batchFailures >= MAX_BATCH_FAILURES) {
                // A root manager that blocks or a broken su must not make every
                // sample wait for the timeout. Read what the app domain can see
                // and retry the privileged path a minute later.
                bypassUntil = System.currentTimeMillis() + BYPASS_MS
                batchFailures = 0
            }
            return emptyMap()
        }
        batchFailures = 0
        return parseRead(result.text)
    }

    private fun buildReadScript(paths: List<String>): String = buildString {
        append("PATH=/system/bin:/system/xbin:/sbin:\$PATH\n")
        for (path in paths) {
            val quoted = shQuote(path)
            append("printf '\\n").append(FILE_MARK).append("%s\\n' ").append(quoted).append('\n')
            append("cat ").append(quoted).append(" 2>/dev/null || printf '")
                .append(MISS_MARK).append("\\n'\n")
            append("printf '\\n").append(END_MARK).append("\\n'\n")
        }
        // Keep the exit status tied to the batch, not the last cat.
        append("true")
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


    private fun shQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
