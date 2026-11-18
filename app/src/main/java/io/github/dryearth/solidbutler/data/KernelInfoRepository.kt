package io.github.dryearth.solidbutler.data

import android.content.Context
import io.github.dryearth.solidbutler.core.Shell
import java.io.File

data class KernelItem(val label: String, val value: String)

/** Read-only kernel / root-manager capability probe. */
class KernelInfoRepository(private val context: Context) {

    fun detect(): List<KernelItem> {
        val out = ArrayList<KernelItem>()
        fun add(label: String, value: String?) {
            if (!value.isNullOrBlank()) out.add(KernelItem(label, value.trim()))
        }
        fun read(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()
        fun prop(key: String): String? =
            Shell.run("getprop $key", 4_000).text.trim().ifBlank { null }

        add("内核", read("/proc/version")?.take(120) ?: System.getProperty("os.version"))
        add("SELinux", selinux(read))
        add("Root", rootManager(read))
        add("模块", modules())
        add("ZRAM", zram(read))
        add("Swap", swaps(read))
        add("Governors", read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors"))
        add("I/O 调度", read("/sys/block/sda/queue/scheduler")
            ?: read("/sys/block/mmcblk0/queue/scheduler"))
        add("cgroups", cgroups())
        add("内核模块", runCatching {
            File("/proc/modules").readLines().count { it.isNotBlank() }.takeIf { it > 0 }?.let { "$it 个" }
        }.getOrNull())
        add("Verified Boot", prop("ro.boot.verifiedbootstate"))
        add("Bootloader", when (prop("ro.boot.flash.locked")) {
            "1" -> "已锁定"
            "0" -> "已解锁"
            else -> null
        })
        add("LSPosed", listOf("/data/adb/lspd", "/data/adb/modules/lsposed").firstOrNull { File(it).exists() }?.let { "已安装" })
        add("KernelSU", read("/data/adb/ksu/version"))
        add("Magisk", read("/data/adb/magisk/version"))
        add("内核配置", config())
        return out
    }

    private fun selinux(read: (String) -> String?): String? {
        val enforce = read("/sys/fs/selinux/enforce")
        return when (enforce) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> Shell.run("getenforce", 3_000).text.trim().ifBlank { null }
        }
    }

    private fun rootManager(read: (String) -> String?): String? {
        val ksu = File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists()
        val magisk = File("/data/adb/magisk").exists()
        val su = Shell.run("command -v su || which su", 3_000).text.trim()
        return when {
            ksu && magisk -> "KernelSU + Magisk"
            ksu -> "KernelSU"
            magisk -> "Magisk"
            su.isNotBlank() -> su
            else -> null
        }
    }

    private fun modules(): String? {
        val names = Shell.run("ls /data/adb/modules 2>/dev/null", 5_000).text
            .lineSequence().map { it.trim() }.filter { it.isNotBlank() && !it.endsWith(".disable") }
            .take(8).toList()
        return if (names.isEmpty()) null else "${names.size} 个 · ${names.joinToString("、")}"
    }

    private fun zram(read: (String) -> String?): String? {
        val size = read("/sys/block/zram0/disksize")?.toLongOrNull() ?: 0L
        if (size <= 0) return null
        val algo = read("/sys/block/zram0/comp_algorithm")
            ?.replace("[", "")?.replace("]", "")
            ?.substringBefore(' ')
        return "${size / 1024 / 1024} MB" + if (!algo.isNullOrBlank()) " · $algo" else ""
    }

    private fun swaps(read: (String) -> String?): String? {
        val text = read("/proc/swaps") ?: return null
        val lines = text.lineSequence().drop(1).filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null
        val totalKb = lines.sumOf { line ->
            line.split(Regex("\\s+")).getOrNull(2)?.toLongOrNull() ?: 0L
        }
        return "${lines.size} 个 · ${totalKb / 1024} MB"
    }

    private fun cgroups(): String? {
        val dir = File("/sys/fs/cgroup")
        val names = dir.list()?.filter { it.isNotBlank() }?.take(6) ?: return null
        return if (names.isEmpty()) null else names.joinToString("、")
    }

    private fun config(): String? {
        if (!Shell.rootReady) return null
        val text = Shell.run(
            "zcat /proc/config.gz 2>/dev/null | grep -E '^CONFIG_(KSU|ZRAM|CGROUP|BPF|SECURITY_SELINUX|OVERLAY_FS|SCHED_CORE|KALLSYMS)=' | head -20",
            10_000,
        ).text
        return text.lineSequence().filter { it.isNotBlank() }.take(8).joinToString(" · ")
            .ifBlank { null }
    }
}
