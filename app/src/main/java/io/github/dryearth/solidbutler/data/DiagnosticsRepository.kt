package io.github.dryearth.solidbutler.data

import android.content.Context
import io.github.dryearth.solidbutler.core.Shell

data class CrashEntry(val time: String, val pkg: String, val excerpt: String)
data class StartupEntry(val pkg: String, val ms: Long)
data class Diagnostics(
    val crashes: List<CrashEntry>,
    val startups: List<StartupEntry>,
    val anr: String,
    val available: Boolean,
)

class DiagnosticsRepository(private val context: Context) {

    fun load(): Diagnostics {
        if (!Shell.isPrivileged()) {
            return Diagnostics(emptyList(), emptyList(), "需要 Root 读取系统日志", false)
        }
        val crashText = Shell.run("logcat -d -b crash -t 600", 20_000).text
        val mainText = Shell.run("logcat -d -b main -t 600", 20_000).text
        val anr = Shell.run("dumpsys activity lastanr", 15_000).text
        return Diagnostics(
            crashes = parseCrashes(crashText),
            startups = parseStartups(mainText),
            anr = anr.take(4000),
            available = true,
        )
    }

    private fun parseCrashes(text: String): List<CrashEntry> {
        val out = ArrayList<CrashEntry>()
        var time = ""
        var block = ArrayList<String>()
        var inFatal = false
        fun flush() {
            if (!inFatal) return
            val process = block.firstOrNull { it.contains("Process: ") }
                ?.substringAfter("Process: ")?.substringBefore(',')?.trim()
            if (!process.isNullOrBlank()) {
                out.add(CrashEntry(time, process, block.take(10).joinToString("\n")))
            }
            block = ArrayList()
            inFatal = false
        }
        for (line in text.lineSequence()) {
            val stamp = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)""").find(line)
            if (stamp != null) {
                flush()
                time = stamp.groupValues[1]
            }
            if (line.contains("FATAL EXCEPTION")) {
                flush()
                inFatal = true
            }
            if (inFatal) block.add(line)
        }
        flush()
        return out.takeLast(10).reversed()
    }

    private fun parseStartups(text: String): List<StartupEntry> {
        val regex = Regex("""Displayed ([\w.]+)/[\w.$]+: \+([0-9]+)ms""")
        return text.lineSequence().mapNotNull {
            val m = regex.find(it) ?: return@mapNotNull null
            StartupEntry(m.groupValues[1], m.groupValues[2].toLongOrNull() ?: 0)
        }.distinctBy { it.pkg }.take(10).toList()
    }
}
