package io.github.dryearth.solidbutler.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import io.github.dryearth.solidbutler.core.Shell
import java.io.File
import java.util.Locale

class PerformanceRepository(private val context: Context) {

    private var prevCpu: Map<String, LongArray> = emptyMap()
    private var prevCpuTs = 0L
    private var prevRx = 0L
    private var prevTx = 0L
    private var prevNetTs = 0L

    fun snapshot(): PerfSnapshot {
        val cpu = readCpu()
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val (gpuLoad, gpuFreq, gpuMax) = readGpu()
        val temps = readTemps()
        val (rx, tx) = readNetwork()
        val battery = readBattery()
        return PerfSnapshot(
            cpu = cpu,
            ramUsed = mi.totalMem - mi.availMem,
            ramTotal = mi.totalMem,
            ramAvailable = mi.availMem,
            gpuLoad = gpuLoad,
            gpuFreqMhz = gpuFreq,
            gpuMaxMhz = gpuMax,
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
        val governor = readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: ""
        return CpuSample(load, perCore, freqs, maxFreqs, governor, cores)
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
        fun sumFrom(aa: LongArray, from: Int) = aa.drop(from).sum()
        fun sumTo(aa: LongArray, to: Int) = aa.take(to).sum()
        val totalA = sumFrom(a, 0)
        val totalB = sumFrom(b, 0)
        val idleA = sumTo(a, 4.coerceAtMost(a.size))
        val idleB = sumTo(b, 4.coerceAtMost(b.size))
        val dt = totalB - totalA
        val di = idleB - idleA
        if (dt <= 0) return 0f
        return ((dt - di).toFloat() / dt.toFloat() * 100f).coerceIn(0f, 100f)
    }

    private fun readGpu(): Triple<Int, Int, Int> {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        var load = sysInt("$kgsl/gpu_busy_percentage", -1)
        var freq = (sysLong("$kgsl/gpuclk") / 1_000_000L).toInt()
        var max = (sysLong("$kgsl/max_gpuclk") / 1_000_000L).toInt()
        if (load < 0) {
            load = sysInt("/sys/class/misc/mali0/device/utilisation", -1)
            if (load < 0) load = sysInt("/sys/class/misc/mali0/device/gpu_busy", -1)
        }
        if (freq <= 0) {
            freq = (sysLong("/sys/class/misc/mali0/device/clock") / 1_000_000L).toInt()
            if (freq <= 0) freq = (sysLong("/sys/class/misc/mali0/device/cur_freq") / 1_000_000L).toInt()
        }
        if (max <= 0) {
            max = (sysLong("/sys/class/misc/mali0/device/max_freq") / 1_000_000L).toInt()
        }
        return Triple(load, freq, max)
    }

    private fun readTemps(): List<TempReading> {
        val list = ArrayList<TempReading>()
        val base = File("/sys/class/thermal")
        base.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
            val type = readText("${zone.absolutePath}/type") ?: return@forEach
            val raw = sysLong("${zone.absolutePath}/temp")
            if (raw == 0L) return@forEach
            val c = if (raw > 1000) raw / 1000f else raw.toFloat()
            if (c < -20f || c > 130f) return@forEach
            val label = friendlyTemp(type)
            if (list.none { it.label == label }) list.add(TempReading(label, c))
        }
        if (list.isEmpty()) {
            val b = readBattery()
            if (b.tempC > 0) list.add(TempReading("电池", b.tempC))
        }
        return list.sortedByDescending { it.celsius }.take(8)
    }

    private fun friendlyTemp(type: String): String {
        val t = type.lowercase(Locale.US)
        return when {
            t.contains("battery") || t.contains("batt") -> "电池"
            t.contains("gpu") -> "GPU"
            t.contains("cpu") || t.contains("ap") || t.contains("soc") || t.contains("cluster") -> "CPU"
            t.contains("skin") || t.contains("shell") -> "机身"
            t.contains("charger") || t.contains("usb") -> "充电"
            t.contains("wifi") || t.contains("wlan") -> "WiFi"
            t.contains("modem") || t.contains("pa") -> "基带"
            else -> type.take(12)
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
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else -1
        val chargeCounter = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(-1L)
        val current = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0L)
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
            currentUa = current,
            cycles = cycles,
            chargeCounterUah = chargeCounter,
            technology = tech,
        )
    }

    fun cpuGovernor(): String =
        readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: ""

    fun availableGovernors(): List<String> =
        readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    fun applyGovernor(governor: String): Pair<Boolean, String> {
        if (!Shell.rootReady) return false to "需要 Root"
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
