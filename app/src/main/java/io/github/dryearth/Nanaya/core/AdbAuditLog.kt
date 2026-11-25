package io.github.dryearth.Nanaya.core

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AdbAuditEntry(
    val time: Long,
    val backend: String,
    val command: String,
    val code: Int,
    val output: String,
)

/** Local, bounded log of commands the AI executed through ADB / the shell backend. */
object AdbAuditLog {
    private const val FILE = "ai_adb_commands.jsonl"
    private const val MAX_ENTRIES = 200

    fun append(
        context: Context,
        backend: String,
        command: String,
        code: Int,
        output: String,
    ) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val line = JSONObject().apply {
                put("t", System.currentTimeMillis())
                put("b", backend)
                put("c", command.take(4000))
                put("x", code)
                put("o", output.take(2000))
            }.toString()
            file.appendText(line + "\n")
            val lines = file.readLines()
            if (lines.size > MAX_ENTRIES) {
                file.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun read(context: Context): List<AdbAuditEntry> = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching {
                val obj = JSONObject(line)
                AdbAuditEntry(
                    time = obj.optLong("t"),
                    backend = obj.optString("b"),
                    command = obj.optString("c"),
                    code = obj.optInt("x"),
                    output = obj.optString("o"),
                )
            }.getOrNull()
        }.reversed()
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
