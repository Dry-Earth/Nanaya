package io.github.dryearth.Nanaya.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import io.github.dryearth.Nanaya.core.Shell
import java.io.File
import java.util.Locale

class PerformanceRepository(private val context: Context) {

    private var prevCpu: Map<String, LongArray> = emptyMap()
    private var prevCpuTs = 0L
    private var prevRx = 0L
    private var prevTx = 0L
    private var prevNetTs = 0L
    private var lastProcAt = 0L
    private var lastProcList: List<ProcLoad> = emptyList()
    private var lastMemAt = 0L
    private var lastMemList: List<MemProcess> = emptyList()

    fun snapshot(includeProcesses: Boolean = true): PerfSnapshot {
        val cpu = readCpu()
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val (gpuLoad, gpuFreq, gpuMax, gpuName) = readGpu()
        val (npuName, npuLoad, npuFreq) = readNpu()
        val temps = readTemps()
        val (rx, tx) = readNetwork()
        val battery = readBattery()
        val (swapTotal, swapFree) = readSwap()
        val (cached, buffers) = readMemDetail()
        val (zramSize, zramUsed, zramOrig) = readZram()
        val storage = io.github.dryearth.Nanaya.core.Shell.storageStats()
        return PerfSnapshot(
            cpu = cpu,
            ramUsed = mi.totalMem - mi.availMem,
            ramTotal = mi.totalMem,
            ramAvailable = mi.availMem,
            gpuLoad = gpuLoad,
            gpuFreqMhz = gpuFreq,
            gpuMaxMhz = gpuMax,
            npuName = npuName,
            npuLoad = npuLoad,
            npuFreqMhz = npuFreq,
            swapTotal = swapTotal,
            swapFree = swapFree,
            gpuName = gpuName,
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
        )
    }

    private fun readCpu(): CpuSample {
        val now = readProcStat()
        if (now.isEmpty()) return CpuSample()
        val cores = now.keys.count { it != "cpu" }
        val load = if (prevCpu.isEmpty()) 0f else {
            val a = prevCpu["cpu"]
            val b = now["cpu"]
            if (a == null || b == null) 0f else busyDelta(a, b)
        }
        val perCore = (0 until cores).map { i ->
            val key = "cpu$i"
            val a = prevCpu[key]
            val b = now[key]
            if (a == null || b == null) 0f else busyDelta(a, b)
        }
        prevCpu = now
        val freqs = (0 until cores).map { sysLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }
        val maxFreqs = (0 until cores).map { sysLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").let { v -> if (v <= 0L) sysLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_max_freq") else v } }
        val minFreqs = (0 until cores).map { sysLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_min_freq").let { v -> if (v <= 0L) sysLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_min_freq") else v } }
        val governor = readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: ""
        return CpuSample(load, perCore, freqs, maxFreqs, governor, cores, minFreqs)
    }

    private fun readProcStat(): Map<String, LongArray> {
        val out = LinkedHashMap<String, LongArray>()
        runCatching {
            for (line in File("/proc/stat").readLines()) {
                if (!line.startsWith("cpu")) break
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val nums = parts.drop(1).take(8).mapNotNull { it.toLongOrNull() }.toLongArray()
                    if (nums.size >= 4) out[parts[0]] = nums
                }
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

    private fun readGpu(): Quad<Int, Int, Int, String> {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        var load = sysInt("$kgsl/gpu_busy_percentage", -1)
        var freq = (sysLong("$kgsl/gpuclk") / 1_000_000L).toInt()
        var max = (sysLong("$kgsl/max_gpuclk") / 1_000_000L).toInt()
        if (load < 0) {
            load = sysInt("/sys/class/misc/mali0/device/utilisation", -1)
            if (load < 0) load = sysInt("/sys/class/misc/mali0/device/gpu_busy", -1)
        }
        // OnePlus/SM8750 exposes the GPU through devfreq even when the kgsl
        // busy counter is missing.
        val gpuDevfreq = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0"
        if (load < 0) load = sysInt("$gpuDevfreq/gpu_load", -1)
        if (load < 0) load = sysInt("$gpuDevfreq/load", -1)
        if (freq <= 0) {
            freq = (sysLong("/sys/class/misc/mali0/device/clock") / 1_000_000L).toInt()
            if (freq <= 0) freq = (sysLong("/sys/class/misc/mali0/device/cur_freq") / 1_000_000L).toInt()
            if (freq <= 0) freq = (sysLong("$gpuDevfreq/cur_freq") / 1_000_000L).toInt()
        }
        if (max <= 0) {
            max = (sysLong("/sys/class/misc/mali0/device/max_freq") / 1_000_000L).toInt()
            if (max <= 0) max = (sysLong("$gpuDevfreq/max_freq") / 1_000_000L).toInt()
        }
        val name = readText("$kgsl/gpu_model")
            ?: readText("$kgsl/devfreq/name")
            ?: readText("/sys/class/kgsl/kgsl-3d0/device/gpu_model")
            ?: runCatching { Build.SOC_MODEL }.getOrDefault("")
        return Quad(load, freq, max, name)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun memoryProcesses(force: Boolean = false): List<MemProcess> {
        if (!Shell.isPrivileged()) return emptyList()
        val now = System.currentTimeMillis()
        if (!force && now - lastMemAt < 15_000 && lastMemList.isNotEmpty()) return lastMemList
        lastMemAt = now

        // ps is much faster than dumpsys meminfo and works through ADB.
        val ps = Shell.run("ps -A -o PID,RSS,NAME", 8_000)
        val fromPs = parsePs(ps.text)
        if (fromPs.isNotEmpty()) {
            lastMemList = fromPs
            return fromPs
        }

        // Fallback for ROMs whose ps lacks -o.
        val text = Shell.run("dumpsys meminfo", 45_000).text
        lastMemList = parseMeminfo(text)
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
        var cached = 0L
        var buffers = 0L
        runCatching {
            for (line in File("/proc/meminfo").readLines()) {
                when {
                    line.startsWith("Cached:") -> cached = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    line.startsWith("Buffers:") -> buffers = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                }
            }
        }
        return cached * 1024 to buffers * 1024
    }

    private fun readZram(): Triple<Long, Long, Long> {
        val size = sysLong("/sys/block/zram0/disksize").coerceAtLeast(0)
        var used = sysLong("/sys/block/zram0/mem_used_total")
        var orig = sysLong("/sys/block/zram0/orig_data_size")
        if (used <= 0 || orig <= 0) {
            val stat = readText("/sys/block/zram0/mm_stat")
            if (stat != null) {
                val parts = stat.trim().split(Regex("""\s+"""))
                if (orig <= 0) orig = parts.getOrNull(0)?.toLongOrNull() ?: orig
                if (used <= 0) used = parts.getOrNull(1)?.toLongOrNull() ?: used
            }
        }
        return Triple(
            size.coerceAtLeast(0),
            used.coerceAtLeast(0),
            orig.coerceAtLeast(0),
        )
    }

    private fun readSwap(): Pair<Long, Long> {
        var total = 0L
        var free = 0L
        runCatching {
            for (line in File("/proc/meminfo").readLines()) {
                when {
                    line.startsWith("SwapTotal:") -> total = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") -> free = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                }
            }
        }
        return total * 1024 to free * 1024
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

    private fun readProcesses(): List<ProcLoad> {
        val now = System.currentTimeMillis()
        if (now - lastProcAt < 10_000) return lastProcList
        lastProcAt = now
        if (!Shell.isPrivileged()) {
            lastProcList = emptyList()
            return emptyList()
        }
        val r = Shell.run("dumpsys cpuinfo", 15_000)
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
        lastProcList = if (map.isNotEmpty()) {
            map.entries.sortedByDescending { it.value }.take(10)
                .map { ProcLoad(it.key, it.value) }
        } else {
            // Android 17's cpuinfo format can differ; fall back to PSS per process.
            memoryProcesses().take(10).map { ProcLoad(it.name, 0f, it.pssKb) }
        }
        return lastProcList
    }

    private fun readNpu(): Triple<String, Int, Int> {
        val soc = runCatching { Build.SOC_MODEL }.getOrDefault(Build.HARDWARE)
        val name = when {
            soc.contains("sm8750", true) || soc.contains("sun", true) -> "Hexagon NPU"
            soc.contains("mt", true) -> "MediaTek APU"
            soc.contains("exynos", true) -> "Exynos NPU"
            soc.isBlank() -> "NPU"
            else -> "NPU · $soc"
        }
        val dirs = runCatching {
            File("/sys/class/devfreq").listFiles()?.filter { f ->
                val n = f.name.lowercase(Locale.US)
                n.contains("cdsp") || n.contains("npu") || n.contains("nsp") || n.contains("apu")
            }.orEmpty()
        }.getOrDefault(emptyList())
        var load = -1
        var freq = -1
        for (d in dirs) {
            for (f in listOf("load", "busy", "utilization", "gpu_busy_percentage")) {
                val v = sysInt("${d.absolutePath}/$f", -1)
                if (v in 0..100) { load = v; break }
            }
            for (f in listOf("cur_freq", "frequency")) {
                val v = sysLong("${d.absolutePath}/$f")
                if (v > 0) { freq = (v / 1_000_000L).toInt(); break }
            }
            if (load >= 0 || freq > 0) break
        }
        return Triple(name, load, freq)
    }

    private fun readTemps(): List<TempReading> {
        val grouped = HashMap<String, MutableList<Pair<Float, String>>>()
        val base = File("/sys/class/thermal")
        base.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
            val type = readText("${zone.absolutePath}/type") ?: return@forEach
            val raw = sysLong("${zone.absolutePath}/temp")
            if (raw <= 0) return@forEach
            val celsius = normalizeTemp(raw) ?: return@forEach
            if (celsius !in 5f..115f) return@forEach
            val label = friendlyTemp(type)
            grouped.getOrPut(label) { mutableListOf() }.add(celsius to type)
        }
        val list = grouped.map { (label, values) ->
            val sorted = values.map { it.first }.sorted()
            val median = sorted[sorted.size / 2]
            TempReading(label, median, values.map { it.second }.distinct().joinToString(" / ").take(40))
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
            t.contains("cpu") || t.contains("ap") || t.contains("soc") || t.contains("cluster") ||
                t.contains("core") || t.contains("little") || t.contains("big") ||
                t.contains("mid") || t.contains("gold") || t.contains("silver") -> "CPU"
            t.contains("skin") || t.contains("shell") || t.contains("frame") -> "机身"
            t.contains("charger") || t.contains("usb") || t.contains("vbus") ||
                t.contains("pmic") || t.contains("power") || t.contains("pm8") ||
                t.contains("pmih") || t.contains("bcl") -> "电源"
            t.contains("wifi") || t.contains("wlan") || t.contains("wcn") -> "WiFi"
            t.contains("modem") || t.contains("mdm") || t.contains("pa") || t.contains("rf") -> "基带"
            t.contains("ddr") || t.contains("mem") -> "内存"
            t.contains("video") -> "视频"
            t.contains("display") || t.contains("panel") || t.contains("lcd") -> "屏幕"
            t.contains("camera") || t.contains("cam") -> "相机"
            t.contains("npu") || t.contains("cdsp") || t.contains("nsp") || t.contains("apu") -> "NPU"
            t.contains("ambient") -> "环境"
            t.contains("quiet") || t.contains("xo") || t.contains("sys") ||
                t.contains("tsens") || t.contains("therm") -> "主板"
            else -> "其他"
        }
    }

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
        var voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (voltage <= 0) {
            val vNow = readText("/sys/class/power_supply/battery/voltage_now")?.filter { it.isDigit() }?.toLongOrNull()
            if (vNow != null && vNow > 0) voltage = (vNow / 1000).toInt()
        }
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else -1
        val chargeCounter = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(-1L)
        var current = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0L)
        if (current == 0L) {
            current = readText("/sys/class/power_supply/battery/current_now")
                ?.filter { it.isDigit() || it == '-' }
                ?.toLongOrNull() ?: 0L
        }
        if (current == 0L) {
            current = readText("/sys/class/power_supply/usb/current_now")
                ?.filter { it.isDigit() || it == '-' }
                ?.toLongOrNull() ?: 0L
        }
        if (current in 1L..9999L || current in -9999L..-1L) current *= 1000L
        var power = kotlin.math.abs(current) / 1_000_000f * (voltage / 1000f)
        if (power <= 0f) {
            val powerNow = readText("/sys/class/power_supply/battery/power_now")
                ?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
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

    fun cpuGovernor(): String =
        readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: ""

    fun availableGovernors(): List<String> =
        readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    fun applyGovernor(governor: String): Pair<Boolean, String> {
        if (!Shell.rootReady) return false to "需要 Root"
        if (!governor.matches(Regex("[A-Za-z0-9_-]+"))) return false to "governor 名称无效"
        if (governor !in availableGovernors()) return false to "内核不支持 $governor"
        val cmd = "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$f 2>/dev/null; done; " +
            "echo -n applied"
        val r = Shell.runAs(Shell.Backend.ROOT, cmd)
        return r.text.contains("applied") to (r.text.ifBlank { "完成" })
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
        val r = Shell.run("dumpsys cpuinfo")
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

    private fun readText(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

    private fun sysLong(path: String): Long =
        readText(path)?.filter { it.isDigit() }?.toLongOrNull() ?: -1L

    private fun sysInt(path: String, def: Int): Int {
        val raw = readText(path) ?: return def
        val digits = raw.filter { it.isDigit() }
        return digits.toIntOrNull() ?: def
    }
}
