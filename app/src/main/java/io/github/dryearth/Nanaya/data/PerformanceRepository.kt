package io.github.dryearth.Nanaya.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import io.github.dryearth.Nanaya.core.PrivilegedShell
import io.github.dryearth.Nanaya.core.Shell
import java.io.File
import java.util.Locale

class PerformanceRepository(private val context: Context) {

    private var prevCpu: Map<String, LongArray> = emptyMap()
    private var prevRx = 0L
    private var prevTx = 0L
    private var prevNetTs = 0L
    private var lastProcAt = 0L
    private var lastProcList: List<ProcLoad> = emptyList()
    private var lastMemAt = 0L
    private var lastMemList: List<MemProcess> = emptyList()

    fun snapshot(includeProcesses: Boolean = true): PerfSnapshot {
        // Each group is isolated: one blocked/failed node (a vendor sysfs file,
        // a binder, a storage mount that disappeared with a container) must not
        // discard the metrics that did read successfully.
        val cpu = runCatching { readCpu() }.getOrDefault(CpuSample())
        val memory = runCatching {
            val am = context.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi
        }.getOrNull()
        val gpu = runCatching { readGpu() }
            .getOrDefault(GpuReading(-1, 0, 0, -1, "", emptyList(), emptyList(), ""))
        val npu = runCatching { readNpu() }.getOrDefault(NpuReading("", -1, -1))
        val temps = runCatching { readTemps() }.getOrDefault(emptyList())
        val (rx, tx) = runCatching { readNetwork() }.getOrDefault(0L to 0L)
        val battery = runCatching { readBattery() }.getOrDefault(BatterySnapshot())
        val vendorCharging = runCatching { readVendorCharging() }.getOrDefault(VendorCharging())
        val swappiness = runCatching { readSwappiness() }.getOrDefault(-1)
        val (swapTotal, swapFree) = runCatching { readSwap() }.getOrDefault(0L to 0L)
        val (cached, buffers) = runCatching { readMemDetail() }.getOrDefault(0L to 0L)
        val (zramSize, zramUsed, zramOrig) = runCatching { readZram() }
            .getOrDefault(Triple(0L, 0L, 0L))
        val storage = runCatching { Shell.storageStats() }
            .getOrElse { Triple(0L, 0L, 0L) }
        val refreshRateHz = runCatching {
            val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f
        }.getOrDefault(0f)
        return PerfSnapshot(
            cpu = cpu,
            ramUsed = ((memory?.totalMem ?: 0L) - (memory?.availMem ?: 0L)).coerceAtLeast(0L),
            ramTotal = memory?.totalMem ?: 0L,
            ramAvailable = memory?.availMem ?: 0L,
            gpuLoad = gpu.load,
            gpuFreqMhz = gpu.freqMhz,
            gpuMaxMhz = gpu.maxMhz,
            gpuMinMhz = gpu.minMhz,
            gpuGovernor = gpu.governor,
            gpuGovernors = gpu.governors,
            gpuAvailableMhz = gpu.availableMhz,
            swappiness = swappiness,
            vendorChargeSupported = vendorCharging.supported,
            vendorChargeEnabled = vendorCharging.enabled,
            vendorChargeLimitMa = vendorCharging.limitMa,
            vendorChargeLimitMaxMa = vendorCharging.limitMaxMa,
            npuName = npu.name,
            npuLoad = npu.load,
            npuFreqMhz = npu.freqMhz,
            swapTotal = swapTotal,
            swapFree = swapFree,
            gpuName = gpu.name,
            glEsVersion = glEsVersion(),
            vulkanVersion = vulkanVersion(),
            socModel = runCatching { Build.SOC_MODEL }.getOrDefault(Build.HARDWARE),
            minFreqsKhz = cpu.minFreqsKhz,
            processes = if (includeProcesses) readProcesses() else emptyList(),
            storageUsed = storage.second,
            storageTotal = storage.first,
            cachedBytes = cached,
            buffersBytes = buffers,
            zramSize = zramSize,
            zramUsed = zramUsed,
            zramOrig = zramOrig,
            temps = temps,
            rxBps = rx,
            txBps = tx,
            battery = battery,
            uptimeMs = SystemClock.elapsedRealtime(),
            timestamp = System.currentTimeMillis(),
            refreshRateHz = refreshRateHz,
        )
    }

    // ---------------- CPU ----------------

    private fun readCpu(): CpuSample {
        val statText = PrivilegedShell.read("/proc/stat")
            ?: runCatching { File("/proc/stat").readText() }.getOrNull()
        val now = parseProcStat(statText.orEmpty())
        if (now.isEmpty()) return CpuSample()
        val cores = now.keys.count { it != "cpu" }.coerceAtLeast(1)
        val load = if (prevCpu.isEmpty()) 0f else {
            val a = prevCpu["cpu"]
            val b = now["cpu"]
            if (a == null || b == null) 0f else busyDelta(a, b)
        }
        val perCore = (0 until cores).map { i ->
            val a = prevCpu["cpu$i"]
            val b = now["cpu$i"]
            if (a == null || b == null) 0f else busyDelta(a, b)
        }
        prevCpu = now

        val paths = ArrayList<String>(cores * 4 + 1)
        for (i in 0 until cores) {
            paths += cpuFreqPaths(i, "scaling_cur_freq")
            paths += cpuFreqPaths(i, "cpuinfo_max_freq")
            paths += cpuFreqPaths(i, "scaling_max_freq")
            paths += cpuFreqPaths(i, "cpuinfo_min_freq")
            paths += cpuFreqPaths(i, "scaling_min_freq")
        }
        paths += cpuFreqPaths(0, "scaling_governor")
        val values = PrivilegedShell.readAll(paths)

        fun pick(i: Int, vararg leaves: String): Long {
            for (leaf in leaves) {
                for (path in cpuFreqPaths(i, leaf)) {
                    val v = PrivilegedShell.firstNumber(values[path]) ?: continue
                    if (v > 0) return v
                }
            }
            return -1L
        }

        val freqs = (0 until cores).map { pick(it, "scaling_cur_freq") }
        val maxFreqs = (0 until cores).map {
            pick(it, "cpuinfo_max_freq", "scaling_max_freq")
        }
        val minFreqs = (0 until cores).map {
            pick(it, "cpuinfo_min_freq", "scaling_min_freq")
        }
        val governor = cpuFreqPaths(0, "scaling_governor").asSequence()
            .mapNotNull { values[it]?.takeIf { v -> v.isNotBlank() } }
            .firstOrNull()
            .orEmpty()
        return CpuSample(load, perCore, freqs, maxFreqs, governor, cores, minFreqs)
    }

    private fun cpuFreqPaths(cpu: Int, leaf: String): List<String> = listOf(
        "/sys/devices/system/cpu/cpu$cpu/cpufreq/$leaf",
        "/sys/devices/system/cpu/cpufreq/policy$cpu/$leaf",
    )

    private fun parseProcStat(text: String): Map<String, LongArray> {
        val out = LinkedHashMap<String, LongArray>()
        for (line in text.lineSequence()) {
            if (!line.startsWith("cpu")) break
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 5) {
                val nums = parts.drop(1).take(8).mapNotNull { it.toLongOrNull() }.toLongArray()
                if (nums.size >= 4) out[parts[0]] = nums
            }
        }
        return out
    }

    private fun busyDelta(a: LongArray, b: LongArray): Float {
        val totalA = a.sum()
        val totalB = b.sum()
        // /proc/stat: 0 user, 1 nice, 2 system, 3 idle, 4 iowait
        val idleA = (a.getOrNull(3) ?: 0L) + (a.getOrNull(4) ?: 0L)
        val idleB = (b.getOrNull(3) ?: 0L) + (b.getOrNull(4) ?: 0L)
        val dt = totalB - totalA
        val di = idleB - idleA
        if (dt <= 0) return 0f
        return ((dt - di).toFloat() / dt.toFloat() * 100f).coerceIn(0f, 100f)
    }

    // ---------------- GPU ----------------

    private data class GpuReading(
        val load: Int,
        val freqMhz: Int,
        val maxMhz: Int,
        val minMhz: Int,
        val governor: String,
        val governors: List<String>,
        val availableMhz: List<Int>,
        val name: String,
    )

    private fun readGpu(): GpuReading {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        val kgslDev = "$kgsl/devfreq"
        val devfreq = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0"
        val candidates = listOf(
            "$kgsl/gpu_busy_percentage",
            "$kgsl/gpubusy",
            "$kgsl/gpuclk",
            "$kgsl/max_gpuclk",
            "$kgsl/min_gpuclk",
            "$kgsl/gpu_model",
            "$kgsl/device/gpu_model",
            "$kgsl/gpu_available_frequencies",
            "$kgsl/freq_table_mhz",
            "$kgsl/governor",
            "$kgsl/available_governors",
            "$kgslDev/cur_freq",
            "$kgslDev/max_freq",
            "$kgslDev/min_freq",
            "$kgslDev/governor",
            "$kgslDev/available_governors",
            "$kgslDev/name",
            "$kgslDev/available_frequencies",
            "$devfreq/gpu_load",
            "$devfreq/load",
            "$devfreq/cur_freq",
            "$devfreq/max_freq",
            "$devfreq/min_freq",
            "$devfreq/governor",
            "$devfreq/available_governors",
            "$devfreq/name",
            "$devfreq/available_frequencies",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/class/devfreq/gpufreq/max_freq",
            "/sys/class/devfreq/gpufreq/min_freq",
            "/sys/class/devfreq/gpufreq/governor",
            "/sys/class/devfreq/gpufreq/available_governors",
            "/sys/class/devfreq/gpufreq/available_frequencies",
            "/sys/class/devfreq/gpufreq/gpu_load",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/kernel/gpu/gpu_freq",
            "/sys/kernel/fpsgo/common/gpu_load",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/proc/game_opt/gpu_load",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/class/misc/mali0/device/gpu_busy",
            "/sys/class/misc/mali0/device/clock",
            "/sys/class/misc/mali0/device/cur_freq",
            "/sys/class/misc/mali0/device/max_freq",
            "/sys/class/misc/mali0/device/min_freq",
            "/sys/class/misc/mali0/device/governor",
        )
        val values = PrivilegedShell.readAll(candidates)

        fun number(path: String): Long? = PrivilegedShell.firstNumber(values[path])

        var load = -1
        for (path in listOf(
            "$kgsl/gpu_busy_percentage",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/devfreq/gpufreq/gpu_load",
            "$devfreq/gpu_load",
            "$devfreq/load",
            "/sys/kernel/fpsgo/common/gpu_load",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/proc/game_opt/gpu_load",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/class/misc/mali0/device/gpu_busy",
        )) {
            val v = number(path) ?: continue
            if (v in 0..100) {
                load = v.toInt()
                break
            }
        }
        if (load < 0) {
            // gpubusy is "busy total" and encodes percentage as busy/total.
            val raw = values["$kgsl/gpubusy"]
            val parts = raw?.trim()?.split(Regex("\\s+")).orEmpty()
            if (parts.size >= 2) {
                val busy = parts[0].toLongOrNull() ?: 0L
                val total = parts[1].toLongOrNull() ?: 0L
                if (total > 0) load = (busy * 100 / total).toInt().coerceIn(0, 100)
            }
        }

        var freqMhz = 0
        for (path in listOf(
            "$kgsl/gpuclk",
            "$kgslDev/cur_freq",
            "$devfreq/cur_freq",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/kernel/gpu/gpu_freq",
            "/sys/class/misc/mali0/device/clock",
            "/sys/class/misc/mali0/device/cur_freq",
        )) {
            val v = number(path) ?: continue
            if (v > 0) {
                freqMhz = hzToMhz(v)
                break
            }
        }

        // The available table is the hardware maximum. `max_freq` may already
        // have been lowered by an earlier limit, so never use it as the base
        // for a percentage: that would ratchet the limit down every time.
        val availableMhz = run {
            for (path in listOf(
                "$kgslDev/available_frequencies",
                "$devfreq/available_frequencies",
                "/sys/class/devfreq/gpufreq/available_frequencies",
                "$kgsl/gpu_available_frequencies",
                "$kgsl/freq_table_mhz",
            )) {
                val raw = values[path] ?: continue
                val nums = Regex("""\d+""").findAll(raw)
                    .mapNotNull { it.value.toLongOrNull() }
                    .filter { it > 0 }
                    .toList()
                if (nums.isEmpty()) continue
                val mhz = nums
                    .map { if (path.endsWith("freq_table_mhz")) it.toInt() else hzToMhz(it) }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                if (mhz.isNotEmpty()) return@run mhz
            }
            emptyList<Int>()
        }

        var maxMhz = availableMhz.lastOrNull() ?: 0
        if (maxMhz <= 0) {
            for (path in listOf(
                "$kgsl/max_gpuclk",
                "$kgslDev/max_freq",
                "$devfreq/max_freq",
                "/sys/class/devfreq/gpufreq/max_freq",
                "/sys/class/misc/mali0/device/max_freq",
            )) {
                val v = number(path) ?: continue
                if (v > 0) {
                    maxMhz = hzToMhz(v)
                    break
                }
            }
        }

        var minMhz = 0
        for (path in listOf(
            "$kgsl/min_gpuclk",
            "$kgslDev/min_freq",
            "$devfreq/min_freq",
            "/sys/class/devfreq/gpufreq/min_freq",
            "/sys/class/misc/mali0/device/min_freq",
        )) {
            val v = number(path) ?: continue
            if (v > 0) {
                minMhz = hzToMhz(v)
                break
            }
        }
        if (minMhz <= 0) minMhz = availableMhz.firstOrNull() ?: -1

        var governor = ""
        for (path in listOf(
            "$kgslDev/governor",
            "/sys/class/devfreq/gpufreq/governor",
            "$devfreq/governor",
            "$kgsl/governor",
            "/sys/class/misc/mali0/device/governor",
        )) {
            val v = values[path]?.takeIf { it.isNotBlank() } ?: continue
            governor = v
            break
        }
        val governors = run {
            for (path in listOf(
                "$kgslDev/available_governors",
                "/sys/class/devfreq/gpufreq/available_governors",
                "$devfreq/available_governors",
                "$kgsl/available_governors",
            )) {
                val raw = values[path] ?: continue
                val list = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (list.isNotEmpty()) return@run list
            }
            emptyList<String>()
        }

        val name = values["$kgsl/gpu_model"]
            ?: values["$kgsl/device/gpu_model"]
            ?: values["$kgslDev/name"]
            ?: values["$devfreq/name"]
            ?: runCatching { Build.SOC_MODEL }.getOrDefault("")
        return GpuReading(load, freqMhz, maxMhz, minMhz, governor, governors, availableMhz, name)
    }

    private fun hzToMhz(value: Long): Int =
        if (value >= 100_000L) (value / 1_000_000L).toInt() else value.toInt()

    // ---------------- NPU ----------------

    private data class NpuReading(val name: String, val load: Int, val freqMhz: Int)

    private fun readNpu(): NpuReading {
        val soc = runCatching { Build.SOC_MODEL }.getOrDefault(Build.HARDWARE)
        val name = when {
            soc.contains("sm8750", true) || soc.contains("sun", true) -> "Hexagon NPU"
            soc.contains("mt", true) -> "MediaTek APU"
            soc.contains("exynos", true) -> "Exynos NPU"
            soc.isBlank() -> "NPU"
            else -> "NPU · $soc"
        }
        val dirs = PrivilegedShell.listDir("/sys/class/devfreq").filter { path ->
            val n = path.substringAfterLast('/').lowercase(Locale.US)
            n.contains("cdsp") || n.contains("npu") || n.contains("nsp") || n.contains("apu")
        }
        if (dirs.isEmpty()) return NpuReading(name, -1, -1)
        val paths = ArrayList<String>(dirs.size * 5)
        for (dir in dirs) {
            for (leaf in listOf("load", "busy", "utilization", "gpu_busy_percentage", "cur_freq", "frequency")) {
                paths += "$dir/$leaf"
            }
        }
        val values = PrivilegedShell.readAll(paths)
        var load = -1
        var freq = -1
        for (dir in dirs) {
            for (leaf in listOf("load", "busy", "utilization", "gpu_busy_percentage")) {
                val v = PrivilegedShell.firstNumber(values["$dir/$leaf"]) ?: continue
                if (v in 0..100) {
                    load = v.toInt()
                    break
                }
            }
            for (leaf in listOf("cur_freq", "frequency")) {
                val v = PrivilegedShell.firstNumber(values["$dir/$leaf"]) ?: continue
                if (v > 0) {
                    freq = hzToMhz(v)
                    break
                }
            }
            if (load >= 0 || freq > 0) break
        }
        return NpuReading(name, load, freq)
    }

    // ---------------- memory ----------------

    fun memoryProcesses(force: Boolean = false): List<MemProcess> {
        if (!Shell.isPrivileged()) return emptyList()
        val now = System.currentTimeMillis()
        if (!force && now - lastMemAt < 15_000 && lastMemList.isNotEmpty()) return lastMemList
        lastMemAt = now

        // ps is much faster than dumpsys meminfo and works through ADB too.
        val ps = PrivilegedShell.run("ps -A -o PID,RSS,NAME", 8_000)
        val fromPs = parsePs(ps.text)
        if (fromPs.isNotEmpty()) {
            lastMemList = fromPs
            return fromPs
        }

        // Fallback for ROMs whose ps lacks -o.
        val text = PrivilegedShell.run("dumpsys meminfo", 20_000).text
        val parsed = parseMeminfo(text)
        if (parsed.isNotEmpty()) lastMemList = parsed
        return lastMemList
    }

    private fun parsePs(text: String): List<MemProcess> {
        val regex = Regex("""^\s*(\d+)\s+(\d+)\s+(\S.*)$""")
        return text.lineSequence().mapNotNull { line ->
            if (line.contains("PID") && line.contains("RSS")) return@mapNotNull null
            val m = regex.find(line) ?: return@mapNotNull null
            val rss = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val name = m.groupValues[3].trim()
            if (rss <= 0 || name.isBlank()) return@mapNotNull null
            MemProcess(name, rss)
        }.distinctBy { it.name }.sortedByDescending { it.pssKb }.take(40).toList()
    }

    private fun parseMeminfo(text: String): List<MemProcess> {
        val withPid = Regex("""^\s*([\d,]+)K:\s+(.+?)\s+\(pid\s+\d+.*$""")
        val plain = Regex("""^\s*([\d,]+)K:\s+(\S+).*$""")
        return text.lineSequence().mapNotNull { line ->
            val m = withPid.find(line) ?: plain.find(line) ?: return@mapNotNull null
            val pss = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@mapNotNull null
            MemProcess(m.groupValues[2].trim(), pss)
        }.distinctBy { it.name }.sortedByDescending { it.pssKb }.take(40).toList()
    }

    private fun readMemDetail(): Pair<Long, Long> {
        val text = PrivilegedShell.read("/proc/meminfo")
            ?: runCatching { File("/proc/meminfo").readText() }.getOrNull()
            ?: return 0L to 0L
        var cached = 0L
        var buffers = 0L
        for (line in text.lineSequence()) {
            when {
                line.startsWith("Cached:") -> cached = firstKb(line)
                line.startsWith("Buffers:") -> buffers = firstKb(line)
            }
        }
        return cached * 1024 to buffers * 1024
    }

    private fun readZram(): Triple<Long, Long, Long> {
        val paths = listOf(
            "/sys/block/zram0/disksize",
            "/sys/block/zram0/mem_used_total",
            "/sys/block/zram0/orig_data_size",
            "/sys/block/zram0/mm_stat",
            "/proc/zraminfo",
        )
        val values = PrivilegedShell.readAll(paths)
        val size = PrivilegedShell.firstNumber(values["/sys/block/zram0/disksize"])?.coerceAtLeast(0) ?: 0L
        var used = PrivilegedShell.firstNumber(values["/sys/block/zram0/mem_used_total"]) ?: 0L
        var orig = PrivilegedShell.firstNumber(values["/sys/block/zram0/orig_data_size"]) ?: 0L
        if (used <= 0 || orig <= 0) {
            val stat = values["/sys/block/zram0/mm_stat"]
            if (stat != null) {
                val parts = stat.trim().split(Regex("""\s+"""))
                if (orig <= 0) orig = parts.getOrNull(0)?.toLongOrNull() ?: orig
                if (used <= 0) used = parts.getOrNull(1)?.toLongOrNull() ?: used
            }
        }
        return Triple(size.coerceAtLeast(0), used.coerceAtLeast(0), orig.coerceAtLeast(0))
    }

    private fun readSwap(): Pair<Long, Long> {
        val text = PrivilegedShell.read("/proc/meminfo")
            ?: runCatching { File("/proc/meminfo").readText() }.getOrNull()
            ?: return 0L to 0L
        var total = 0L
        var free = 0L
        for (line in text.lineSequence()) {
            when {
                line.startsWith("SwapTotal:") -> total = firstKb(line)
                line.startsWith("SwapFree:") -> free = firstKb(line)
            }
        }
        return total * 1024 to free * 1024
    }

    private fun firstKb(line: String): Long {
        val value = line.substringAfter(':').trim().substringBefore(' ').replace(",", "")
        return value.toLongOrNull() ?: 0L
    }

    private fun glEsVersion(): String = runCatching {
        val am = context.getSystemService(ActivityManager::class.java)
        am.deviceConfigurationInfo.glEsVersion
    }.getOrDefault("")

    private fun vulkanVersion(): String = runCatching {
        val info = context.packageManager.systemAvailableFeatures
            ?.firstOrNull { it.name == "android.hardware.vulkan.version" }
        val v = info?.version ?: 0
        if (v == 0) "" else {
            val major = v shr 22
            val minor = (v shr 12) and 0x3ff
            val patch = v and 0xfff
            "$major.$minor.$patch"
        }
    }.getOrDefault("")

    fun processLoads(force: Boolean = false): List<ProcLoad> {
        if (force) lastProcAt = 0
        return readProcesses()
    }

    private fun readProcesses(): List<ProcLoad> {
        val now = System.currentTimeMillis()
        if (now - lastProcAt < 10_000 && lastProcList.isNotEmpty()) return lastProcList
        lastProcAt = now
        if (!Shell.isPrivileged()) return lastProcList

        val r = PrivilegedShell.run("dumpsys cpuinfo", 12_000)
        val regex = Regex("""^\s*([0-9.]+)%\s+\d+/([^\s:]+)""")
        val map = HashMap<String, Float>()
        if (r.ok) {
            for (line in r.text.lineSequence()) {
                val m = regex.find(line) ?: continue
                val pct = m.groupValues[1].toFloatOrNull() ?: continue
                val name = m.groupValues[2]
                map[name] = (map[name] ?: 0f) + pct
            }
        }
        if (map.isNotEmpty()) {
            lastProcList = map.entries.sortedByDescending { it.value }.take(10)
                .map { ProcLoad(it.key, it.value) }
        } else if (lastProcList.isEmpty()) {
            // Android's cpuinfo format can differ; fall back to PSS per process.
            lastProcList = memoryProcesses().take(10).map { ProcLoad(it.name, 0f, it.pssKb) }
        }
        return lastProcList
    }

    // ---------------- thermal ----------------

    private fun readTemps(): List<TempReading> {
        val zones = PrivilegedShell.listDir("/sys/class/thermal").filter {
            it.substringAfterLast('/').startsWith("thermal_zone")
        }
        val paths = ArrayList<String>(zones.size * 2)
        for (zone in zones) {
            paths += "$zone/type"
            paths += "$zone/temp"
        }
        val values = PrivilegedShell.readAll(paths)
        val grouped = HashMap<String, MutableList<Pair<Float, String>>>()
        for (zone in zones) {
            val type = values["$zone/type"]?.takeIf { it.isNotBlank() } ?: continue
            val raw = PrivilegedShell.firstNumber(values["$zone/temp"]) ?: continue
            if (raw <= 0) continue
            val celsius = normalizeTemp(raw) ?: continue
            if (celsius !in 5f..115f) continue
            val label = friendlyTemp(type)
            grouped.getOrPut(label) { mutableListOf() }.add(celsius to type)
        }
        if (grouped.isEmpty()) {
            val board = PrivilegedShell.firstNumber(
                PrivilegedShell.read("/sys/class/thermal/thermal_message/board_sensor_temp")
            )
            if (board != null) {
                normalizeTemp(board)?.let { grouped.getOrPut("主板") { mutableListOf() }.add(it to "board_sensor_temp") }
            }
        }
        val list = grouped.map { (label, entries) ->
            val sorted = entries.map { it.first }.sorted()
            val median = sorted[sorted.size / 2]
            TempReading(label, median, entries.map { it.second }.distinct().joinToString(" / ").take(40))
        }.sortedByDescending { it.celsius }.toMutableList()
        if (list.isEmpty()) {
            val b = readBattery()
            if (b.tempC > 0) list.add(TempReading("电池", b.tempC))
        }
        return list
    }

    /** Thermal zones use millidegrees, decicelsius, or raw degrees depending on vendor. */
    private fun normalizeTemp(raw: Long): Float? =
        listOf(raw / 1000f, raw / 10f, raw.toFloat()).firstOrNull { it in 5f..115f }

    private fun friendlyTemp(type: String): String {
        val t = type.lowercase(Locale.US)
        return when {
            t.contains("battery") || t.contains("batt") || t.contains("bms") ||
                t.contains("vbat") || t.contains("ibat") -> "电池"
            t.contains("gpu") || t.contains("kgsl") || t.contains("mali") || t.contains("adreno") -> "GPU"
            t.contains("npu") || t.contains("cdsp") || t.contains("nsp") ||
                t.contains("apu") || t.contains("hvx") -> "NPU"
            t.contains("skin") || t.contains("shell") || t.contains("frame") -> "机身"
            t.contains("charger") || t.contains("usb") || t.contains("vbus") ||
                t.contains("pmic") || t.contains("pm8") || t.contains("pmih") ||
                t.contains("bcl") -> "电源"
            t.contains("wifi") || t.contains("wlan") || t.contains("wcn") -> "WiFi"
            t.contains("modem") || t.contains("mdm") || t.contains("rf") -> "基带"
            t.contains("ddr") || t.contains("mem") -> "内存"
            t.contains("video") -> "视频"
            t.contains("display") || t.contains("panel") || t.contains("lcd") -> "屏幕"
            t.contains("camera") || t.contains("cam") -> "相机"
            t.contains("ambient") -> "环境"
            t.contains("socd") || t.contains("soc") || t.contains("cpu") ||
                t.contains("cluster") || t.contains("core") || t.contains("little") ||
                t.contains("big") || t.contains("mid") || t.contains("gold") ||
                t.contains("silver") -> "CPU"
            t.contains("quiet") || t.contains("xo") || t.contains("sys") ||
                t.contains("tsens") || t.contains("therm") -> "主板"
            else -> "其他"
        }
    }

    // ---------------- network ----------------

    private fun readNetwork(): Pair<Long, Long> {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        var rxBps = 0L
        var txBps = 0L
        if (prevNetTs > 0 && now > prevNetTs) {
            val dt = (now - prevNetTs) / 1000.0
            if (dt > 0) {
                rxBps = ((rx - prevRx).coerceAtLeast(0) / dt).toLong()
                txBps = ((tx - prevTx).coerceAtLeast(0) / dt).toLong()
            }
        }
        prevRx = rx; prevTx = tx; prevNetTs = now
        return rxBps to txBps
    }

    // ---------------- battery ----------------

    @Suppress("DEPRECATION")
    fun readBattery(): BatterySnapshot {
        val intent = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        }.getOrNull()
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        val values = PrivilegedShell.readAll(
            listOf(
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/usb/current_now",
                "/sys/class/power_supply/main/current_now",
                "/sys/class/power_supply/battery/power_now",
                "/sys/class/power_supply/usb/power_now",
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle",
                "/sys/class/power_supply/battery/charge_counter",
                "/sys/class/power_supply/battery/charge_now",
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/battery/charge_full_design",
            )
        )

        var voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (voltage <= 0) {
            val vNow = PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/voltage_now"])
                ?: PrivilegedShell.firstNumber(values["/sys/class/power_supply/usb/voltage_now"])
            if (vNow != null && vNow > 0) voltage = (vNow / 1000).toInt()
        }

        var cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else -1
        if (cycles < 0) {
            cycles = (
                PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/cycle_count"])
                    ?: PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/battery_cycle"])
                )?.toInt() ?: -1
        }

        var chargeCounter = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(-1L)
        if (chargeCounter <= 0) {
            chargeCounter = PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/charge_counter"])
                ?: PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/charge_now"])
                ?: -1L
        }

        var current = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0L)
        if (current == 0L) {
            current = PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/current_now"]) ?: 0L
        }
        if (current == 0L) {
            current = PrivilegedShell.firstNumber(values["/sys/class/power_supply/usb/current_now"]) ?: 0L
        }
        if (current == 0L) {
            current = PrivilegedShell.firstNumber(values["/sys/class/power_supply/main/current_now"]) ?: 0L
        }
        if (current in 1L..9999L || current in -9999L..-1L) current *= 1000L

        var power = kotlin.math.abs(current) / 1_000_000f * (voltage / 1000f)
        if (power <= 0f) {
            val powerNow = PrivilegedShell.firstNumber(values["/sys/class/power_supply/battery/power_now"])
                ?: PrivilegedShell.firstNumber(values["/sys/class/power_supply/usb/power_now"])
                ?: 0L
            if (powerNow > 0) power = powerNow / 1_000_000f
        }

        return BatterySnapshot(
            level = level * 100 / scale.coerceAtLeast(1),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
                else -> "未连接"
            },
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
                BatteryManager.BATTERY_STATUS_FULL -> "已充满"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
                else -> "未知"
            },
            health = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
                BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
                BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "异常"
                else -> "未知"
            },
            tempC = temp,
            voltageMv = voltage,
            currentUa = current.toInt(),
            cycles = cycles,
            chargeCounterUah = chargeCounter,
            technology = tech,
            powerW = power,
        )
    }

    // ---------------- vendor charging ----------------

    private data class VendorCharging(
        val supported: Boolean = false,
        val enabled: Boolean = false,
        val limitMa: Int = -1,
        val limitMaxMa: Int = -1,
    )

    private fun readVendorCharging(): VendorCharging {
        val enabledPaths = listOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/input_suspend",
            "/sys/class/power_supply/battery/charge_disable",
        )
        val limitPaths = listOf(
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/battery/input_current_limit",
            "/sys/class/power_supply/usb/input_current_limit",
        )
        val maxPaths = listOf(
            "/sys/class/power_supply/battery/current_max",
            "/sys/class/power_supply/main/current_max",
            "/sys/class/power_supply/battery/charge_control_limit_max",
            "/sys/class/power_supply/battery/current_limit_max",
        )
        val values = PrivilegedShell.readAll(enabledPaths + limitPaths + maxPaths)
        val enabledPath = enabledPaths.firstOrNull { values[it] != null } ?: ""
        val limitPath = limitPaths.firstOrNull { values[it] != null } ?: ""
        val maxPath = maxPaths.firstOrNull { values[it] != null } ?: ""
        if (enabledPath.isEmpty() && limitPath.isEmpty()) return VendorCharging()

        val rawEnabled = PrivilegedShell.firstNumber(values[enabledPath])
        val enabled = when {
            rawEnabled == null -> true
            enabledPath.endsWith("input_suspend") || enabledPath.endsWith("charge_disable") ->
                rawEnabled == 0L
            else -> rawEnabled != 0L
        }
        val rawLimit = PrivilegedShell.firstNumber(values[limitPath])
        val rawMax = PrivilegedShell.firstNumber(values[maxPath])
        val micro = (rawLimit ?: 0L) >= 100_000L || (rawMax ?: 0L) >= 100_000L
        fun toMa(value: Long?): Int = when {
            value == null || value <= 0 -> -1
            micro -> (value / 1000L).toInt()
            else -> value.toInt()
        }
        return VendorCharging(
            supported = true,
            enabled = enabled,
            limitMa = toMa(rawLimit),
            limitMaxMa = toMa(rawMax),
        )
    }

    fun setVendorChargingEnabled(enabled: Boolean): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val paths = listOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/input_suspend",
            "/sys/class/power_supply/battery/charge_disable",
        )
        val values = PrivilegedShell.readAll(paths)
        val path = paths.firstOrNull { values[it] != null }
            ?: return false to "设备没有充电开关节点"
        val inverted = path.endsWith("input_suspend") || path.endsWith("charge_disable")
        val raw = if (enabled != inverted) "1" else "0"
        val ok = writeFirst(listOf(path), raw)
        return ok to if (ok) {
            if (enabled) "已允许充电" else "已停止充电"
        } else {
            "写入失败，需要 Root"
        }
    }

    fun setVendorChargeLimit(pct: Int): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val state = readVendorCharging()
        val rawMax = readChargeMaxRaw()
        if (!state.supported || rawMax == null || rawMax <= 0) {
            return false to "设备没有可写的最大电流节点"
        }
        val clamped = pct.coerceIn(20, 100)
        val target = rawMax * clamped / 100
        val path = listOf(
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/battery/input_current_limit",
            "/sys/class/power_supply/usb/input_current_limit",
        ).firstOrNull { PrivilegedShell.read(it) != null } ?: return false to "找不到限流节点"
        val ok = writeFirst(listOf(path), target.toString())
        return ok to if (ok) "充电电流上限已设为 $clamped%" else "写入失败，需要 Root"
    }

    private fun readChargeMaxRaw(): Long? {
        val paths = listOf(
            "/sys/class/power_supply/battery/current_max",
            "/sys/class/power_supply/main/current_max",
            "/sys/class/power_supply/battery/charge_control_limit_max",
            "/sys/class/power_supply/battery/current_limit_max",
        )
        val values = PrivilegedShell.readAll(paths)
        for (path in paths) {
            val value = PrivilegedShell.firstNumber(values[path]) ?: continue
            if (value > 0) return value
        }
        return null
    }

    // ---------------- memory tuning ----------------

    private fun readSwappiness(): Int =
        runCatching {
            File("/proc/sys/vm/swappiness").readText().trim().toIntOrNull()
        }.getOrNull() ?: -1

    fun setSwappiness(value: Int): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val clamped = value.coerceIn(0, 200)
        val ok = writeFirst(listOf("/proc/sys/vm/swappiness"), clamped.toString())
        return ok to if (ok) "swappiness 已设为 $clamped" else "写入失败，需要 Root"
    }

    fun dropCaches(): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val r = PrivilegedShell.run(
            "sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; echo done",
            15_000,
        )
        val ok = r.text.contains("done")
        return ok to if (ok) "已清理页缓存、dentries 与 inodes" else "清理失败，需要 Root"
    }

    // ---------------- GPU control ----------------

    fun applyGpuGovernor(governor: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        if (!governor.matches(Regex("[A-Za-z0-9_-]+"))) return false to "governor 名称无效"
        val ok = writeFirst(
            listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                "/sys/class/devfreq/gpufreq/governor",
                "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/governor",
                "/sys/class/kgsl/kgsl-3d0/governor",
            ),
            governor,
        )
        return ok to if (ok) "GPU governor：$governor" else "设备不支持写 GPU governor"
    }

    fun setGpuMaxMhz(mhz: Int): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        if (mhz <= 0) return false to "频率无效"
        val hz = mhz.toLong() * 1_000_000L
        val ok = writeFirst(
            listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
                "/sys/class/devfreq/gpufreq/max_freq",
                "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/max_freq",
                "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            ),
            hz.toString(),
        )
        return ok to if (ok) "GPU 最高频率：$mhz MHz" else "写入失败，需要 Root"
    }

    fun setCpuMaxPct(pct: Int): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        val p = pct.coerceIn(30, 100)
        val cmd = "ok=0; for d in /sys/devices/system/cpu/cpu[0-9]*/cpufreq " +
            "/sys/devices/system/cpu/cpufreq/policy[0-9]*; do " +
            "[ -d \"\$d\" ] || continue; " +
            "max=\$(cat \"\$d/cpuinfo_max_freq\" 2>/dev/null); " +
            "[ -z \"\$max\" ] && max=\$(cat \"\$d/scaling_max_freq\" 2>/dev/null); " +
            "[ -z \"\$max\" ] && continue; " +
            "target=\$((max * $p / 100)); " +
            "echo \$target > \"\$d/scaling_max_freq\" 2>/dev/null && ok=1; " +
            "done; echo \$ok"
        val r = PrivilegedShell.run(cmd, 12_000)
        val ok = r.text.trim().endsWith("1")
        return ok to if (ok) "CPU 最高频率限制为 $p%" else "写入失败，需要 Root"
    }

    private fun writeFirst(paths: List<String>, value: String): Boolean {
        if (!value.matches(Regex("[-0-9A-Za-z_.]+"))) return false
        val script = buildString {
            append("ok=0\n")
            for (path in paths) {
                val quoted = shQuote(path)
                append("if [ -e ").append(quoted).append(" ]; then ")
                append("echo ").append(value).append(" > ").append(quoted)
                    .append(" 2>/dev/null && ok=1; fi\n")
            }
            append("echo \$ok")
        }
        return PrivilegedShell.run(script, 10_000).text.trim().endsWith("1")
    }

    private fun shQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    // ---------------- CPU control ----------------

    fun cpuGovernor(): String =
        PrivilegedShell.readAll(cpuFreqPaths(0, "scaling_governor"))
            .values.firstOrNull { it.isNotBlank() }.orEmpty()

    fun availableGovernors(): List<String> =
        PrivilegedShell.readAll(cpuFreqPaths(0, "scaling_available_governors"))
            .values.firstOrNull { it.isNotBlank() }
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    fun applyGovernor(governor: String): Pair<Boolean, String> {
        if (!Shell.isPrivileged()) return false to "需要 Root 或无线 ADB"
        if (!governor.matches(Regex("[A-Za-z0-9_-]+"))) return false to "governor 名称无效"
        if (governor !in availableGovernors()) return false to "内核不支持 $governor"
        val cmd = "ok=0; for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor " +
            "/sys/devices/system/cpu/cpufreq/policy*/scaling_governor; do " +
            "[ -e \"\$f\" ] && echo $governor > \"\$f\" 2>/dev/null && ok=1; done; echo \$ok"
        val r = PrivilegedShell.run(cmd, 12_000)
        val ok = r.text.trim().endsWith("1")
        return ok to if (ok) "CPU governor：$governor" else "写入失败，需要 Root"
    }

    fun presets(): List<PerfPreset> {
        val govs = availableGovernors()
        fun pick(vararg candidates: String): List<String> =
            candidates.filter { it in govs }.take(1)
        return listOf(
            PerfPreset("battery", "省电", "优先功耗，限制调度", pick("powersave", "conservative", "schedutil"), 55),
            PerfPreset("balanced", "均衡", "系统默认调度", pick("schedutil", "interactive", "walt"), 85),
            PerfPreset("perf", "性能", "释放全部频率", pick("performance", "schedutil"), 100),
        )
    }

    fun topDrain(): List<AppDrain> {
        val usm = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 24 * 3600_000, now)
        }.getOrDefault(emptyList())
        val pm = context.packageManager
        val cpu = cpuInfo()
        return stats.asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(40)
            .map { s ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                }.getOrDefault(s.packageName)
                val uid = runCatching { pm.getApplicationInfo(s.packageName, 0).uid }.getOrDefault(-1)
                AppDrain(
                    pkg = s.packageName,
                    label = label,
                    foregroundMs = s.totalTimeInForeground,
                    lastUsed = s.lastTimeUsed,
                    uid = uid,
                    cpuPct = cpu[s.packageName] ?: 0f,
                )
            }
            .toList()
    }

    private fun cpuInfo(): Map<String, Float> {
        val out = HashMap<String, Float>()
        if (!Shell.isPrivileged()) return out
        val r = PrivilegedShell.run("dumpsys cpuinfo", 12_000)
        if (!r.ok) return out
        val regex = Regex("""^\s*([0-9.]+)%\s+\d+/([^\s:]+)""")
        for (line in r.text.lineSequence()) {
            val m = regex.find(line) ?: continue
            val pct = m.groupValues[1].toFloatOrNull() ?: continue
            val pkg = m.groupValues[2]
            if (pkg.contains('.')) out[pkg] = (out[pkg] ?: 0f) + pct
        }
        return out
    }

    fun recordBattery(snapshot: BatterySnapshot) {
        runCatching {
            val f = File(context.filesDir, "battery_history.csv")
            val now = System.currentTimeMillis()
            if (f.exists()) {
                val last = f.readLines().lastOrNull()?.substringBefore(',')?.toLongOrNull() ?: 0
                if (now - last < 30 * 60_000) return
            }
            f.appendText("$now,${snapshot.level},${snapshot.cycles},${snapshot.tempC}\n")
        }
    }

    fun batteryHistory(): List<Triple<Long, Int, Int>> = runCatching {
        val f = File(context.filesDir, "battery_history.csv")
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull {
            val p = it.split(',')
            if (p.size < 4) null else {
                val ts = p[0].toLongOrNull() ?: return@mapNotNull null
                Triple(ts, p[1].toIntOrNull() ?: 0, p[3].toFloatOrNull()?.toInt() ?: 0)
            }
        }.takeLast(500)
    }.getOrDefault(emptyList())
}
