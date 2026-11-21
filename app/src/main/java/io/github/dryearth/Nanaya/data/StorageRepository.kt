package io.github.dryearth.Nanaya.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.core.isExternalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Storage analysis built on public Android APIs:
 * StorageStatsManager for per-app cache, MediaStore-independent file walk
 * for category analysis, and streaming SHA-256 for duplicate detection.
 * Nothing leaves the device.
 */
class StorageRepository(
    private val context: Context,
    private val prefs: Prefs,
) {
    private val pm: PackageManager = context.packageManager
    private val stats: StorageStatsManager? =
        context.getSystemService(StorageStatsManager::class.java)
    private val trash = TrashRepository(context)

    private data class Found(
        val file: File,
        val rel: String,
        val size: Long,
        val mtime: Long,
        val top: String,
        val ext: String,
    )

    suspend fun scan(progress: (String, Float) -> Unit): StorageReport = withContext(Dispatchers.IO) {
        progress("读取存储概况", 0.02f)
        val (total, used, free) = Shell.storageStats()
        val root = Environment.getExternalStorageDirectory()
        val canReadAll = isExternalManager()

        progress("遍历文件", 0.08f)
        val files = ArrayList<Found>(24_000)
        val emptyDirs = ArrayList<StorageItem>()
        val topSizes = HashMap<String, Long>()
        val typeSizes = HashMap<String, Long>()
        var scanned = 0
        var lastReport = 0L

        val queue = ArrayDeque<Pair<File, String>>()
        if (canReadAll) {
            root.listFiles()?.forEach { queue.add(it to it.name) }
        } else {
            root.listFiles()?.forEach { queue.add(it to it.name) }
        }

        while (queue.isNotEmpty() && files.size < 400_000) {
            val (file, rel) = queue.removeFirst()
            if (file.name == "." || file.name == "..") continue
            if (rel.count { it == '/' } > 14) continue
            if (file.isDirectory) {
                if (file.name == "obb") continue
                val children = runCatching { file.listFiles() }.getOrNull()
                if (children == null) continue
                if (children.isEmpty() && file.canRead() && !rel.startsWith("Android/data")) {
                    runCatching {
                        emptyDirs.add(
                            StorageItem(
                                path = file.absolutePath,
                                bytes = 0,
                                label = file.name.ifBlank { rel },
                                kind = StorageKind.EMPTY_DIR,
                            )
                        )
                    }
                }
                children.forEach { queue.add(it to "$rel/${it.name}") }
            } else {
                val size = runCatching { file.length() }.getOrDefault(0L)
                if (size <= 0) continue
                val ext = file.extension.lowercase()
                val top = rel.substringBefore('/')
                files.add(Found(file, rel, size, file.lastModified(), top, ext))
                topSizes[top] = (topSizes[top] ?: 0L) + size
                typeSizes[groupOf(ext)] = (typeSizes[groupOf(ext)] ?: 0L) + size
                scanned++
                val now = System.currentTimeMillis()
                if (now - lastReport > 220) {
                    lastReport = now
                    progress("已扫描 $scanned 个文件", 0.08f + (0.42f * (scanned % 40_000) / 40_000f))
                }
            }
        }

        progress("统计应用缓存", 0.55f)
        val appCaches = collectAppCaches()

        val installedPkgs = runCatching {
            pm.getInstalledPackages(0).map { it.packageName }.toHashSet()
        }.getOrDefault(hashSetOf())

        val apkItems = files.filter { it.ext == "apk" }.map { f ->
            val pkg = runCatching {
                pm.getPackageArchiveInfo(f.file.absolutePath, 0)?.packageName
            }.getOrNull()
            val installed = pkg != null && pkg in installedPkgs
            StorageItem(
                path = f.file.absolutePath,
                bytes = f.size,
                label = f.file.name,
                kind = StorageKind.APK,
                detail = when {
                    pkg == null -> "无法解析包名，可能是损坏的安装包"
                    installed -> "已安装：$pkg · 删除安装包不会卸载应用"
                    else -> "未安装：$pkg"
                },
            )
        }.sortedByDescending { it.bytes }

        val logs = files.filter {
            it.ext == "log" || it.file.name.contains(".log.", true) || it.ext == "dmp"
        }.sortedByDescending { it.size }

        val oldDownloads = files.filter {
            it.rel.startsWith("Download/") && System.currentTimeMillis() - it.mtime > 30L * 24 * 3600 * 1000
        }.sortedByDescending { it.size }

        val thumbs = files.filter {
            it.rel.contains("/.thumbnails/") || it.rel.contains("/thumbnails/")
        }

        val large = files.filter { it.size > 50L * 1024 * 1024 }
            .sortedByDescending { it.size }
            .take(300)
            .map {
                StorageItem(
                    it.file.absolutePath, it.size, it.file.name,
                    StorageKind.LARGE, it.size.bytes(),
                )
            }

        progress("查找重复文件", 0.68f)
        val duplicates = findDuplicates(files.filter { it.size >= 1024 * 1024 }, progress)

        progress("检查卸载残留", 0.94f)
        val (leftoverBytes, leftoverItems) = findLeftovers(installedPkgs)
        val duplicateWaste = duplicates.sumOf { it.size * (it.files.size - 1) }

        val cacheBytes = appCaches.sumOf { it.bytes }
        val categoryList = buildList {
            add(
                StorageCategory(
                    "cache", "应用缓存", "StorageStats 官方统计 · 只清 cache 目录",
                    cacheBytes, appCaches.count { it.bytes > 0 },
                    appCaches.filter { it.bytes > 0 }.map {
                        StorageItem("", it.bytes, it.label, StorageKind.CACHE, it.pkg)
                    },
                    cleanable = true,
                )
            )
            add(
                StorageCategory(
                    "apk", "安装包（APK）",
                    "${apkItems.count { !(it.detail?.startsWith("已安装") ?: false) }} 个未安装 · 删除不会卸载应用",
                    apkItems.sumOf { it.bytes }, apkItems.size,
                    apkItems.take(300),
                    requiresConfirm = true,
                    warning = "安装包属于你的文件。删除后需要重新下载；应用本身不会被卸载。",
                )
            )
            add(
                StorageCategory(
                    "log", "日志与崩溃转储", "*.log / *.dmp",
                    logs.sumOf { it.size }, logs.size,
                    logs.take(300).map { StorageItem(it.file.absolutePath, it.size, it.file.name, StorageKind.LOG) },
                )
            )
            add(
                StorageCategory(
                    "thumb", "缩略图", "相册/视频缓存缩略图，可安全重建",
                    thumbs.sumOf { it.size }, thumbs.size,
                    thumbs.sortedByDescending { it.size }.take(300).map {
                        StorageItem(it.file.absolutePath, it.size, it.file.name, StorageKind.THUMBNAIL)
                    },
                )
            )
            add(
                StorageCategory(
                    "old_download", "用户下载（30 天前）", "Download/ 目录 · 可能是模型、素材或离线资源",
                    oldDownloads.sumOf { it.size }, oldDownloads.size,
                    oldDownloads.take(300).map {
                        StorageItem(it.file.absolutePath, it.size, it.file.name, StorageKind.DOWNLOAD)
                    },
                    requiresConfirm = true,
                    warning = "下载目录属于你的文件，可能包含模型、素材、离线包。请逐项确认后再删除。",
                )
            )
            add(
                StorageCategory(
                    "empty", "空文件夹", "没有内容的占位目录",
                    0, emptyDirs.size, emptyDirs.take(400), cleanable = true,
                )
            )
            add(
                StorageCategory(
                    "large", "大型文件（> 50MB）", "只读展示，不提供清理入口",
                    large.sumOf { it.bytes }, large.size, large, cleanable = false,
                )
            )
            add(
                StorageCategory(
                    "dup", "重复文件", "${duplicates.size} 组 · 需要你选择保留哪一份",
                    duplicateWaste, duplicates.sumOf { it.files.size - 1 },
                    emptyList(), requiresConfirm = true,
                    warning = "重复文件中可能有照片、视频或模型。默认一份都不选，请逐组确认。",
                )
            )
            if (leftoverItems.isNotEmpty()) {
                add(
                    StorageCategory(
                        "leftover", "卸载残留", "已卸载应用留下的私有数据 · 需 Root",
                        leftoverBytes, leftoverItems.size,
                        leftoverItems,
                        requiresConfirm = true,
                        warning = "这些是已卸载应用的数据目录，可能包含聊天记录或账号缓存。删除不可恢复。",
                    )
                )
            }
        }

        val dirSlices = topSizes.entries.sortedByDescending { it.value }.take(12)
            .map { Slice(it.key, it.value) }
        val typeSlices = typeSizes.entries.sortedByDescending { it.value }.take(10)
            .map { Slice(it.key, it.value) }

        progress("完成", 1f)
        StorageReport(
            total = total,
            used = used,
            free = free,
            categories = categoryList,
            duplicates = duplicates,
            dirSlices = dirSlices,
            typeSlices = typeSlices,
            appCaches = appCaches,
            scannedAt = System.currentTimeMillis(),
            fileCount = scanned,
        )
    }

    /** Per-app cache via the official StorageStatsManager (needs usage-access). */
    fun collectAppCaches(): List<AppCache> {
        val sm = stats ?: return emptyList()
        val apps = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        val out = ArrayList<AppCache>()
        for (ai in apps) {
            val size = runCatching {
                sm.queryStatsForUid(StorageManager.UUID_DEFAULT, ai.uid).cacheBytes
            }.getOrNull() ?: continue
            if (size <= 0) continue
            out.add(
                AppCache(
                    pkg = ai.packageName,
                    label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName),
                    uid = ai.uid,
                    bytes = size,
                )
            )
        }
        return out.sortedByDescending { it.bytes }
    }

    private fun findDuplicates(
        candidates: List<Found>,
        progress: (String, Float) -> Unit,
    ): List<DuplicateGroup> {
        val bySize = candidates.groupBy { it.size }.filter { it.value.size > 1 }
        val sampleCandidates = bySize.entries.sortedByDescending { it.key }.take(250)
        val sampleHashes = HashMap<String, MutableList<Found>>()
        var hashed = 0
        for ((_, group) in sampleCandidates) {
            for (f in group) {
                val h = runCatching { hashFile(f.file, 64 * 1024) }.getOrNull() ?: continue
                sampleHashes.getOrPut("${f.size}:$h") { ArrayList() }.add(f)
                hashed++
                if (hashed % 25 == 0) {
                    progress("局部哈希 $hashed 个候选", 0.7f + 0.1f * (hashed % 200) / 200f)
                }
            }
        }
        val fullGroups = sampleHashes.values.filter { it.size > 1 }
        val result = ArrayList<DuplicateGroup>()
        var fullDone = 0
        for (group in fullGroups.sortedByDescending { it.first().size * it.size }) {
            if (result.size >= 120) break
            val map = HashMap<String, MutableList<String>>()
            for (f in group) {
                val h = runCatching { hashFile(f.file, Long.MAX_VALUE) }.getOrNull() ?: continue
                map.getOrPut(h) { ArrayList() }.add(f.file.absolutePath)
                fullDone++
            }
            map.entries.filter { it.value.size > 1 }.forEach {
                result.add(DuplicateGroup(size = group.first().size, hash = it.key, files = it.value))
            }
            if (fullDone % 10 == 0) {
                progress("完整哈希 $fullDone 个文件", 0.82f + 0.12f * (fullDone % 50) / 50f)
            }
        }
        return result.sortedByDescending { it.size * (it.files.size - 1) }
    }

    private fun findLeftovers(installed: Set<String>): Pair<Long, List<StorageItem>> {
        if (!Shell.rootReady) return 0L to emptyList()
        val dirs = Shell.run("ls /data/data 2>/dev/null", 20_000).text
            .lineSequence()
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z0-9._]{2,120}")) }
            .filter { it !in installed }
            .filterNot { it == "android" || it == "misc" || it == "system" || it == "vendor" }
            .distinct()
            .take(50)
            .toList()
        if (dirs.isEmpty()) return 0L to emptyList()
        val script = "for p in " + dirs.joinToString(" ") +
            "; do s=\$(du -sk /data/data/\$p 2>/dev/null | cut -f1); " +
            "[ -n \"\$s\" ] && echo \"\$p \$s\"; done"
        val out = Shell.run(script, 90_000).text
        val items = ArrayList<StorageItem>()
        for (line in out.lineSequence()) {
            val parts = line.trim().split(' ')
            if (parts.size != 2) continue
            val pkg = parts[0]
            val kb = parts[1].toLongOrNull() ?: continue
            if (pkg !in dirs) continue
            items.add(
                StorageItem(
                    path = "/data/data/$pkg",
                    bytes = kb * 1024,
                    label = pkg,
                    kind = StorageKind.LEFTOVER,
                    detail = "已卸载应用的私有数据目录",
                )
            )
        }
        return items.sumOf { it.bytes } to items.sortedByDescending { it.bytes }
    }

    private fun hashFile(file: File, limit: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(128 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
                total += n
                if (total >= limit) break
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun groupOf(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "raw" -> "图片"
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "ts" -> "视频"
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus" -> "音频"
        "apk", "xapk", "apks", "aab" -> "安装包"
        "zip", "rar", "7z", "tar", "gz", "xz", "zst" -> "压缩包"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub" -> "文档"
        "log", "dmp", "trace" -> "日志"
        else -> if (ext.isBlank()) "无扩展名" else "其他"
    }

    suspend fun clearAppCaches(pkgs: List<String>): CleanResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var deleted = 0
        var failed = 0
        val msgs = ArrayList<String>()
        if (pkgs.isEmpty()) {
            return@withContext CleanResult(
                0, 0, 0,
                listOf("没有可清理的应用缓存。请先授予「使用情况访问」，并在应用详情里确认应用有缓存。"),
            )
        }
        val targets = pkgs.filter { Shell.isValidPackage(it) }
            .filterNot { it in prefs.whitelist() }
        if (targets.isEmpty()) {
            return@withContext CleanResult(0, 0, 0, listOf("目标都在保护名单里，已跳过"))
        }
        for (pkg in targets) {
            val before = runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                stats?.queryStatsForUid(StorageManager.UUID_DEFAULT, ai.uid)?.cacheBytes ?: 0L
            }.getOrDefault(0L)
            var ok = false
            val r = Shell.run("pm clear --cache-only $pkg")
            if (r.text.contains("Success", true)) ok = true
            if (!ok && Shell.rootReady) {
                val r2 = Shell.runAs(
                    Shell.Backend.ROOT,
                    "find /data/user/0/$pkg/cache /data/data/$pkg/cache -mindepth 1 -delete 2>/dev/null; echo done",
                )
                ok = r2.text.contains("done")
            }
            if (ok) {
                freed += before
                deleted++
            } else {
                failed++
                msgs.add("$pkg: ${r.text.take(120)}")
            }
        }
        CleanResult(freed, deleted, failed, msgs)
    }

    suspend fun deleteItems(
        items: List<StorageItem>,
        allowDirs: Boolean = false,
        useTrash: Boolean = false,
    ): CleanResult =
        withContext(Dispatchers.IO) {
            var freed = 0L
            var deleted = 0
            var failed = 0
            val paths = ArrayList<String>()
            val rootPath = Environment.getExternalStorageDirectory().absolutePath
            for (item in items) {
                if (item.kind == StorageKind.CACHE) continue
                val path = item.path
                if (path.isBlank() || !path.startsWith(rootPath)) {
                    failed++
                    continue
                }
                val f = File(path)
                val ok = if (useTrash && f.isFile) {
                    trash.trash(f)
                } else if (f.isDirectory) {
                    allowDirs && runCatching { f.delete() }.getOrDefault(false)
                } else {
                    runCatching { f.delete() }.getOrDefault(false)
                }
                if (ok) {
                    freed += item.bytes
                    deleted++
                } else failed++
                if (ok) paths.add(path)
            }
            if (paths.isNotEmpty()) {
                runCatching {
                    MediaScannerConnection.scanFile(
                        context, paths.toTypedArray(), null, null,
                    )
                }
            }
            CleanResult(freed, deleted, failed)
        }

    suspend fun deleteLeftovers(items: List<StorageItem>): CleanResult = withContext(Dispatchers.IO) {
        if (!Shell.rootReady) return@withContext CleanResult(0, 0, 0, listOf("需要 Root"))
        var freed = 0L
        var deleted = 0
        var failed = 0
        for (item in items) {
            val pkg = item.path.removePrefix("/data/data/")
            if (!pkg.matches(Regex("[A-Za-z0-9._]{2,120}"))) {
                failed++
                continue
            }
            val r = Shell.runAs(
                Shell.Backend.ROOT,
                "rm -rf -- /data/data/$pkg /data/user/0/$pkg && echo DELETED",
                30_000,
            )
            if (r.text.contains("DELETED")) {
                freed += item.bytes
                deleted++
            } else failed++
        }
        CleanResult(freed, deleted, failed)
    }

    suspend fun deleteDuplicateCopies(
        groups: List<DuplicateGroup>,
        keepFirst: Boolean = true,
        useTrash: Boolean = false,
    ): CleanResult =
        withContext(Dispatchers.IO) {
            var freed = 0L
            var deleted = 0
            var failed = 0
            for (g in groups) {
                val victims = if (keepFirst) g.files.drop(1) else g.files
                for (p in victims) {
                    val f = File(p)
                    if (!p.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
                        failed++
                        continue
                    }
                    val ok = if (useTrash) trash.trash(f) else runCatching { f.delete() }.getOrDefault(false)
                    if (ok) {
                        freed += g.size
                        deleted++
                    } else failed++
                }
            }
            CleanResult(freed, deleted, failed)
        }
}
