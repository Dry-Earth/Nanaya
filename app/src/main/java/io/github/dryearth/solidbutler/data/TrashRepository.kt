package io.github.dryearth.solidbutler.data

import android.content.Context
import java.io.File

data class TrashEntry(
    val id: String,
    val name: String,
    val original: String,
    val bytes: Long,
    val deletedAt: Long,
)

class TrashRepository(private val context: Context) {

    private val dir: File = (context.getExternalFilesDir(null) ?: context.filesDir)
        .resolve("trash")
        .apply { mkdirs() }
    private val index: File = File(context.filesDir, "trash_index.tsv")

    fun trash(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        val id = "${System.currentTimeMillis()}_${file.name}"
        val bytes = file.length()
        val dest = File(dir, id)
        val ok = runCatching {
            if (file.renameTo(dest)) true else {
                file.copyTo(dest, overwrite = true)
                file.delete()
            }
        }.getOrDefault(false)
        if (!ok) return false
        runCatching {
            index.appendText("$id\t${file.absolutePath}\t$bytes\t${System.currentTimeMillis()}\n")
        }
        return true
    }

    fun list(): List<TrashEntry> = runCatching {
        if (!index.exists()) return emptyList()
        index.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            val id = p[0]
            val dest = File(dir, id)
            if (!dest.exists()) return@mapNotNull null
            TrashEntry(
                id = id,
                name = id.substringAfter('_'),
                original = p[1],
                bytes = p[2].toLongOrNull() ?: dest.length(),
                deletedAt = p[3].toLongOrNull() ?: 0L,
            )
        }.sortedByDescending { it.deletedAt }
    }.getOrDefault(emptyList())

    fun restore(entry: TrashEntry): Boolean {
        val src = File(dir, entry.id)
        if (!src.exists()) return false
        var target = File(entry.original)
        if (target.exists()) {
            target = File(target.parentFile, "${target.nameWithoutExtension}.restored.${target.extension}")
        }
        target.parentFile?.mkdirs()
        val ok = runCatching {
            if (src.renameTo(target)) true else {
                src.copyTo(target, overwrite = true)
                src.delete()
            }
        }.getOrDefault(false)
        if (ok) removeLine(entry.id)
        return ok
    }

    fun purge(entry: TrashEntry) {
        runCatching { File(dir, entry.id).delete() }
        removeLine(entry.id)
    }

    fun purgeAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        runCatching { index.delete() }
    }

    fun purgeOld(days: Int = 7) {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600_000
        list().filter { it.deletedAt < cutoff }.forEach { purge(it) }
    }

    fun totalBytes(): Long = list().sumOf { it.bytes }

    private fun removeLine(id: String) {
        runCatching {
            if (!index.exists()) return
            val kept = index.readLines().filterNot { it.startsWith("$id\t") }
            index.writeText(kept.joinToString("\n") + if (kept.isEmpty()) "" else "\n")
        }
    }
}
