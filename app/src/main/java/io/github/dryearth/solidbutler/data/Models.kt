package io.github.dryearth.solidbutler.data

import android.graphics.drawable.Drawable

// ---------- Storage ----------

enum class StorageKind { APK, LOG, EMPTY_DIR, DOWNLOAD, THUMBNAIL, LARGE, CACHE, DUPLICATE, LEFTOVER, OTHER }

data class StorageItem(
    val path: String,
    val bytes: Long,
    val label: String,
    val kind: StorageKind,
    val detail: String? = null,
)

data class StorageCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val count: Int,
    val items: List<StorageItem> = emptyList(),
    val cleanable: Boolean = true,
    val requiresConfirm: Boolean = false,
    val warning: String? = null,
)

data class DuplicateGroup(
    val size: Long,
    val hash: String,
    val files: List<String>,
)

data class Slice(val label: String, val bytes: Long)

data class AppCache(
    val pkg: String,
    val label: String,
    val uid: Int,
    val bytes: Long,
)

data class StorageReport(
    val total: Long,
    val used: Long,
    val free: Long,
    val categories: List<StorageCategory>,
    val duplicates: List<DuplicateGroup>,
    val dirSlices: List<Slice>,
    val typeSlices: List<Slice>,
    val appCaches: List<AppCache>,
    val scannedAt: Long,
    val fileCount: Int,
)

data class CleanResult(
    val freed: Long,
    val deleted: Int,
    val failed: Int,
    val messages: List<String> = emptyList(),
)

// ---------- Apps ----------

data class AppEntry(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val enabled: Boolean,
    val frozen: Boolean,
    val uid: Int,
    val version: String,
    val targetSdk: Int,
    val installedAt: Long,
    val apkPath: String,
    val permissions: List<String> = emptyList(),
)

data class BootComponent(
    val pkg: String,
    val component: String,
    val shortName: String,
    val actions: List<String>,
    val enabled: Boolean,
)

data class AutostartApp(
    val app: AppEntry,
    val components: List<BootComponent>,
    val backgroundRestricted: Boolean,
)

data class PermissionRow(
    val name: String,
    val label: String,
    val granted: Boolean,
    val dangerous: Boolean,
)

data class AppDetail(
    val app: AppEntry,
    val permissions: List<PermissionRow>,
    val bootComponents: List<BootComponent>,
    val backgroundRestricted: Boolean,
    val wakeLockRestricted: Boolean,
    val batteryUnrestricted: Boolean,
    val locked: Boolean,
    val whitelisted: Boolean,
    val trackers: List<String> = emptyList(),
    val wifiBytes: Long = -1,
    val cellBytes: Long = -1,
)

// ---------- Performance ----------

data class CpuSample(
    val loadPct: Float = 0f,
    val perCore: List<Float> = emptyList(),
    val freqsKhz: List<Long> = emptyList(),
    val maxFreqsKhz: List<Long> = emptyList(),
    val governor: String = "",
    val cores: Int = 0,
    val minFreqsKhz: List<Long> = emptyList(),
)

data class TempReading(val label: String, val celsius: Float, val source: String = "")

data class ProcLoad(val name: String, val pct: Float, val pssKb: Long = 0)

data class MemProcess(val name: String, val pssKb: Long)

data class BatterySnapshot(
    val level: Int = 0,
    val charging: Boolean = false,
    val plugged: String = "",
    val status: String = "",
    val health: String = "",
    val tempC: Float = 0f,
    val voltageMv: Int = 0,
    val currentUa: Int = 0,
    val cycles: Int = -1,
    val chargeCounterUah: Long = -1,
    val technology: String = "",
    val powerW: Float = 0f,
)

data class PerfSnapshot(
    val cpu: CpuSample = CpuSample(),
    val ramUsed: Long = 0,
    val ramTotal: Long = 0,
    val ramAvailable: Long = 0,
    val gpuLoad: Int = -1,
    val gpuFreqMhz: Int = -1,
    val gpuMaxMhz: Int = -1,
    val temps: List<TempReading> = emptyList(),
    val rxBps: Long = 0,
    val txBps: Long = 0,
    val battery: BatterySnapshot = BatterySnapshot(),
    val npuName: String = "",
    val npuLoad: Int = -1,
    val npuFreqMhz: Int = -1,
    val swapTotal: Long = 0,
    val swapFree: Long = 0,
    val gpuName: String = "",
    val glEsVersion: String = "",
    val vulkanVersion: String = "",
    val socModel: String = "",
    val minFreqsKhz: List<Long> = emptyList(),
    val processes: List<ProcLoad> = emptyList(),
    val storageUsed: Long = 0,
    val storageTotal: Long = 0,
    val cachedBytes: Long = 0,
    val buffersBytes: Long = 0,
    val zramSize: Long = 0,
    val zramUsed: Long = 0,
    val zramOrig: Long = 0,
    val uptimeMs: Long = 0,
    val timestamp: Long = 0,
)

data class AppDrain(
    val pkg: String,
    val label: String,
    val foregroundMs: Long,
    val lastUsed: Long,
    val uid: Int,
    val cpuPct: Float = 0f,
)

data class PerfPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val governors: List<String>,
    val maxFreqPct: Int,
)

// ---------- Security ----------

data class TrackerHit(
    val pkg: String,
    val label: String,
    val trackers: List<String>,
    val error: String? = null,
)

data class PrivacyApp(
    val pkg: String,
    val label: String,
    val permissions: List<String>,
)

data class ActiveCapture(
    val kind: String,
    val pkg: String,
    val detail: String,
)

data class RiskFinding(
    val pkg: String,
    val label: String,
    val level: RiskLevel,
    val reasons: List<String>,
    val isSystem: Boolean,
)

enum class RiskLevel { HIGH, MEDIUM, LOW }

data class SecurityReport(
    val privacyApps: List<PrivacyApp> = emptyList(),
    val activeCaptures: List<ActiveCapture> = emptyList(),
    val trackerHits: List<TrackerHit> = emptyList(),
    val risks: List<RiskFinding> = emptyList(),
    val scannedAt: Long = 0,
)

// ---------- Chat ----------

data class ButlerAction(
    val type: String,
    val args: Map<String, String> = emptyMap(),
    val title: String = "",
    val detail: String = "",
)

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val role: ChatRole,
    val text: String,
    val imageBase64: String? = null,
    val action: ButlerAction? = null,
    val actionState: ActionState = ActionState.NONE,
    val actionResult: String? = null,
)

enum class ActionState { NONE, PENDING, RUNNING, DONE, FAILED, REJECTED }

// ---------- Misc ----------

data class AppIcon(val pkg: String, val drawable: Drawable?)
