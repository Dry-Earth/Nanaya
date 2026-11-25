package io.github.dryearth.Nanaya

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dryearth.Nanaya.ai.AiClient
import io.github.dryearth.Nanaya.core.Capabilities
import io.github.dryearth.Nanaya.core.CapabilityProbe
import io.github.dryearth.Nanaya.core.ChargingState
import io.github.dryearth.Nanaya.core.LineageHardware
import io.github.dryearth.Nanaya.core.LineageHealth
import io.github.dryearth.Nanaya.core.Prefs
import io.github.dryearth.Nanaya.core.PrivilegedShell
import io.github.dryearth.Nanaya.core.Shell
import io.github.dryearth.Nanaya.core.bytes
import io.github.dryearth.Nanaya.core.safePct
import io.github.dryearth.Nanaya.data.AppDrain
import io.github.dryearth.Nanaya.data.ActionState
import io.github.dryearth.Nanaya.data.AppRepository
import io.github.dryearth.Nanaya.data.AutomationRepository
import io.github.dryearth.Nanaya.data.AutomationRule
import io.github.dryearth.Nanaya.data.ButlerAction
import io.github.dryearth.Nanaya.data.ChatImageStore
import io.github.dryearth.Nanaya.data.ChatMessage
import io.github.dryearth.Nanaya.data.ChatRole
import io.github.dryearth.Nanaya.data.PerformanceRepository
import io.github.dryearth.Nanaya.data.RiskLevel
import io.github.dryearth.Nanaya.data.SecurityReport
import io.github.dryearth.Nanaya.data.SecurityRepository
import io.github.dryearth.Nanaya.data.DuplicateGroup
import io.github.dryearth.Nanaya.data.StorageCategory
import io.github.dryearth.Nanaya.data.StorageItem
import io.github.dryearth.Nanaya.data.StorageRepository
import io.github.dryearth.Nanaya.data.StorageReport
import io.github.dryearth.Nanaya.music.MusicAssistant
import io.github.dryearth.Nanaya.music.MusicIntentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ButlerViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val storageRepo = StorageRepository(app, prefs)
    private val appRepo = AppRepository(app, prefs)
    private val perfRepo = PerformanceRepository(app)
    private val securityRepo = SecurityRepository(app, prefs, appRepo)
    private val ai = AiClient(prefs)

    private val _caps = MutableStateFlow(Capabilities())
    val caps = _caps.asStateFlow()

    private val _storage = MutableStateFlow(StorageUiState())
    val storage = _storage.asStateFlow()

    private val _apps = MutableStateFlow(AppsUiState())
    val apps = _apps.asStateFlow()

    private val _perf = MutableStateFlow(PerfUiState())
    val perf = _perf.asStateFlow()

    private val _security = MutableStateFlow(SecurityUiState())
    val security = _security.asStateFlow()

    private val _chat = MutableStateFlow(ChatUiState())
    val chat = _chat.asStateFlow()

    private val _autostart = MutableStateFlow(AutostartUiState())
    val autostart = _autostart.asStateFlow()

    private val _appearance = MutableStateFlow(
        Appearance(prefs.backgroundStyle, prefs.backgroundUri, prefs.backgroundSoften),
    )
    val appearance = _appearance.asStateFlow()

    private val _navigate = MutableStateFlow<String?>(null)
    val navigate = _navigate.asStateFlow()

    private var monitorJob: Job? = null
    private var monitorIntervalMs = 0L
    private var booted = false

    val aiConfigured: Boolean get() = ai.configured()
    val aiLabel: String get() = ai.describe()

    init {
        viewModelScope.launch {
            refreshCapabilities()
            refreshApps()
            startMonitoring()
            refreshSecurity()
            _chat.update { it.copy(modelLabel = ai.describe(), modelConfigured = ai.configured()) }
        }
    }

    // ---------------- capabilities ----------------

    fun refreshCapabilities(force: Boolean = false) {
        viewModelScope.launch {
            val c = withContext(Dispatchers.IO) {
                CapabilityProbe.probe(getApplication(), detectRoot = true, forceRoot = force)
            }
            _caps.value = c
            _security.update { it.copy(rootFirewall = securityRepo.firewallSupported()) }
        }
    }

    fun redetectPrivileges() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Shell.detectRoot(force = true)
                PrivilegedShell.reset()
            }
            refreshCapabilities(force = true)
            refreshPerf(includeProcesses = true, includeHistory = true)
        }
    }

    /**
     * Called when the app returns to the foreground. A previously denied root
     * grant is cached in Shell, so retry it automatically instead of making the
     * user find "重新检测权限" or start a container to refresh the state.
     */
    fun onAppResumed() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (Shell.rootReady) {
                    Shell.revalidate()
                } else {
                    val age = System.currentTimeMillis() - Shell.lastRootCheckAt
                    if (age >= 15_000L) Shell.detectRoot(force = true)
                }
                // A grant revoked while the app was in the background must not
                // leave the old root shell alive, and a new grant needs a fresh
                // shell instead of cached denials.
                PrivilegedShell.reset()
            }
            refreshCapabilities()
            refreshCharging()
            refreshPerf(includeProcesses = true, includeHistory = true)
        }
    }

    // ---------------- storage ----------------

    fun scanStorage() {
        if (_storage.value.scanning) return
        _storage.update { it.copy(scanning = true, stage = "准备扫描", progress = 0.01f, error = null) }
        viewModelScope.launch {
            runCatching {
                storageRepo.scan { stage, p ->
                    _storage.update { it.copy(stage = stage, progress = p) }
                }
            }.onSuccess { report ->
                _storage.update { it.copy(scanning = false, report = report, stage = "完成", progress = 1f) }
            }.onFailure { e ->
                _storage.update {
                    it.copy(
                        scanning = false,
                        error = e.message ?: "扫描失败",
                        stage = "",
                    )
                }
            }
        }
    }

    fun cleanAppCaches(pkgs: List<String>, labels: String = "所选应用") {
        if (pkgs.isEmpty()) return
        _storage.update { it.copy(cleaning = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { storageRepo.clearAppCaches(pkgs) }
            _storage.update {
                val msg = if (result.deleted > 0 && result.freed <= 0) {
                    "已清理 ${result.deleted} 个应用缓存（系统未返回释放量）"
                } else if (result.deleted == 0 && result.messages.isNotEmpty()) {
                    result.messages.first()
                } else {
                    "已清理 $labels：释放 ${result.freed.bytes()}（${result.deleted} 个成功，${result.failed} 个失败）"
                }
                it.copy(cleaning = false, toast = msg)
            }
            scanStorage()
        }
    }

    fun cleanTopCaches() {
        val report = _storage.value.report
        val targets = report?.appCaches
            ?.map { it.pkg }
            ?.filterNot { it in prefs.whitelist() }
            ?.take(8)
            ?: run {
                viewModelScope.launch {
                    val caches = withContext(Dispatchers.IO) { storageRepo.collectAppCaches() }
                    cleanAppCaches(
                        caches.map { it.pkg }.filterNot { it in prefs.whitelist() }.take(8),
                        "缓存最大的 8 个应用",
                    )
                }
                return
            }
        cleanAppCaches(targets, "缓存最大的 8 个应用")
    }

    fun deleteCategory(category: StorageCategory) {
        if (_storage.value.cleaning || category.requiresConfirm) return
        _storage.update { it.copy(cleaning = true) }
        viewModelScope.launch {
            val result = when (category.id) {
                "cache" -> {
                    val pkgs = _storage.value.report?.appCaches?.take(12)?.map { it.pkg }.orEmpty()
                    withContext(Dispatchers.IO) { storageRepo.clearAppCaches(pkgs) }
                }
                else -> withContext(Dispatchers.IO) {
                    storageRepo.deleteItems(category.items, allowDirs = category.id == "empty")
                }
            }
            _storage.update {
                it.copy(
                    cleaning = false,
                    toast = when {
                        result.deleted == 0 && result.failed > 0 ->
                            "${category.title}：没有删除任何项（${result.failed} 项失败，可能受系统保护）"
                        result.deleted == 0 ->
                            "${category.title}：没有可删除的项"
                        else ->
                            "${category.title}：释放 ${result.freed.bytes()}，删除 ${result.deleted} 项" +
                                if (result.failed > 0) "，${result.failed} 项失败" else ""
                    },
                )
            }
            scanStorage()
        }
    }

    fun deleteSelectedFiles(
        category: StorageCategory,
        items: List<StorageItem>,
        duplicateGroups: List<DuplicateGroup> = emptyList(),
    ) {
        if (_storage.value.cleaning) return
        if (items.isEmpty() && duplicateGroups.isEmpty()) return
        _storage.update { it.copy(cleaning = true) }
        viewModelScope.launch {
            val result = when (category.id) {
                "dup" -> withContext(Dispatchers.IO) {
                    storageRepo.deleteDuplicateCopies(duplicateGroups, keepFirst = true, useTrash = false)
                }
                "leftover" -> withContext(Dispatchers.IO) { storageRepo.deleteLeftovers(items) }
                else -> withContext(Dispatchers.IO) { storageRepo.deleteItems(items, useTrash = false) }
            }
            _storage.update {
                it.copy(
                    cleaning = false,
                    toast = when {
                        result.deleted == 0 && result.failed > 0 ->
                            "${category.title}：没有删除任何项（${result.failed} 项失败，可能受系统保护）"
                        result.deleted == 0 ->
                            "${category.title}：没有可删除的项"
                        else ->
                            "${category.title}：释放 ${result.freed.bytes()}，删除 ${result.deleted} 项" +
                                if (result.failed > 0) "，${result.failed} 项失败" else ""
                    },
                )
            }
            scanStorage()
        }
    }

    fun clearStorageToast() = _storage.update { it.copy(toast = null) }

    // ---------------- apps ----------------

    fun refreshApps() {
        _apps.update { it.copy(loading = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { appRepo.list(force = true) }
            _apps.update { it.copy(loading = false, apps = list) }
            booted = true
        }
    }

    fun setAppQuery(q: String) = _apps.update { it.copy(query = q) }

    fun setAppFilter(f: AppFilter) = _apps.update { it.copy(filter = f) }

    fun openApp(pkg: String) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { appRepo.detail(pkg) }
            _apps.update { it.copy(detail = d) }
        }
    }

    fun closeApp() = _apps.update { it.copy(detail = null) }

    fun freeze(pkg: String, freeze: Boolean) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.freeze(pkg, freeze) }
            toast(if (ok) (if (freeze) "已冻结" else "已解冻") else "失败：$msg")
            refreshApps()
            openApp(pkg)
        }
    }

    fun uninstallForUser(pkg: String) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.uninstallForUser(pkg) }
            toast(if (ok) "已为当前用户卸载（可恢复）" else "失败：$msg")
            refreshApps()
            closeApp()
        }
    }

    fun restoreForUser(pkg: String) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.restoreForUser(pkg) }
            toast(if (ok) "已恢复安装" else "失败：$msg")
            refreshApps()
            openApp(pkg)
        }
    }

    fun restrictBackground(pkg: String, uid: Int, restrict: Boolean) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.restrictBackground(pkg, uid, restrict) }
            toast(if (ok) (if (restrict) "已限制后台" else "已恢复后台") else "失败：$msg")
            openApp(pkg)
        }
    }

    fun restrictWakeLock(pkg: String, restrict: Boolean) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.restrictWakeLock(pkg, restrict) }
            toast(if (ok) (if (restrict) "已拦截唤醒锁" else "已恢复唤醒锁") else "失败：$msg")
            openApp(pkg)
        }
    }

    fun setBatteryUnrestricted(pkg: String, unrestricted: Boolean) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.setBatteryUnrestricted(pkg, unrestricted) }
            toast(if (ok) (if (unrestricted) "已加入电池白名单" else "已移出电池白名单") else "失败：$msg")
            openApp(pkg)
        }
    }

    fun revokePermission(pkg: String, permission: String) {
        val detailOpen = _apps.value.detail?.app?.pkg == pkg
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.revoke(pkg, permission) }
            val message = if (ok) "已收回权限" else "失败：$msg"
            toast(message)
            _security.update { it.copy(toast = message) }
            if (detailOpen) openApp(pkg) else refreshSecurity()
        }
    }

    fun toggleBoot(pkg: String, component: String, enabled: Boolean) {
        runAppAction(pkg) {
            val (ok, msg) = withContext(Dispatchers.IO) { appRepo.setComponentEnabled(pkg, component, enabled) }
            toast(if (ok) (if (enabled) "已允许自启动组件" else "已禁用自启动组件") else "失败：$msg")
            openApp(pkg)
        }
    }

    fun toggleLock(pkg: String, locked: Boolean) {
        prefs.setLocked(pkg, locked)
        _apps.update { st ->
            st.copy(detail = st.detail?.copy(locked = locked))
        }
        _apps.update { it.copy(toast = if (locked) "已加入应用锁" else "已移出应用锁") }
    }

    fun toggleWhitelist(pkg: String, white: Boolean) {
        prefs.setWhitelisted(pkg, white)
        _apps.update { st -> st.copy(detail = st.detail?.copy(whitelisted = white)) }
    }

    private fun runAppAction(pkg: String, block: suspend () -> Unit) {
        _apps.update { it.copy(busy = pkg) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _apps.update { it.copy(busy = null) }
            }
        }
    }

    private fun toast(text: String) = _apps.update { it.copy(toast = text) }

    fun clearAppsToast() = _apps.update { it.copy(toast = null) }

    // ---------------- performance ----------------

    fun startMonitoring() {
        val interval = prefs.perfIntervalMs.coerceIn(500, 5000).toLong()
        if (monitorJob?.isActive == true && monitorIntervalMs == interval) return
        monitorJob?.cancel()
        monitorIntervalMs = interval
        _perf.update { it.copy(monitoring = true, intervalMs = interval.toInt()) }
        monitorJob = viewModelScope.launch {
            var tick = 0
            var lastSlowAt = 0L
            var lastPrivilegeCheck = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastPrivilegeCheck >= 30_000) {
                    lastPrivilegeCheck = now
                    // Root probes can take seconds (root manager prompt, stale
                    // daemon). Never run them on the main thread: it froze the
                    // whole screen, not just the performance numbers.
                    withContext(Dispatchers.IO) {
                        val before = Shell.isPrivileged()
                        Shell.revalidate()
                        if (before != Shell.isPrivileged()) {
                            // Drop the old channel and its cache on any privilege
                            // change so reads switch to the correct domain.
                            PrivilegedShell.reset()
                        }
                    }
                    refreshCapabilities()
                }
                val slow = now - lastSlowAt >= 30_000
                if (slow) {
                    lastSlowAt = now
                    refreshCharging()
                }
                // One transient sysfs/binder failure must not kill the whole
                // sampler; otherwise every metric stays frozen at its default.
                val sampleError = runCatching {
                    refreshPerf(includeProcesses = slow, includeHistory = slow)
                }.exceptionOrNull()
                if (sampleError != null) {
                    _perf.update {
                        it.copy(
                            lastSampleAt = System.currentTimeMillis(),
                            sampleError = sampleError.toString(),
                        )
                    }
                }
                tick++
                delay(interval)
            }
        }
    }

    fun setPerfInterval(ms: Int) {
        prefs.perfIntervalMs = ms
        if (monitorJob?.isActive == true) {
            monitorJob?.cancel()
            monitorJob = null
            startMonitoring()
        } else {
            _perf.update { it.copy(intervalMs = prefs.perfIntervalMs) }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        monitorIntervalMs = 0L
        _perf.update { it.copy(monitoring = false) }
    }

    @Volatile private var chargingBusy = false

    /**
     * LineageHealth is a binder service. Keep it off the sampling critical path:
     * a dead/blocked binder must not freeze CPU/GPU/RAM updates.
     */
    private fun refreshCharging() {
        if (chargingBusy) return
        chargingBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val charging = LineageHealth.read()
                _perf.update { it.copy(charging = charging) }
            }
            chargingBusy = false
        }
    }

    private suspend fun refreshPerf(
        includeProcesses: Boolean = false,
        includeHistory: Boolean = false,
    ) {
        // Publish the fast metrics first. Process listing, LineageHealth binder
        // calls and battery history can be slow or block; they must never hold
        // back CPU/GPU/RAM from the UI.
        val snapResult = runCatching {
            withContext(Dispatchers.IO) { perfRepo.snapshot(includeProcesses = false) }
        }
        val snap = snapResult.getOrNull()
        if (snap == null) {
            _perf.update {
                it.copy(
                    lastSampleAt = System.currentTimeMillis(),
                    sampleError = snapResult.exceptionOrNull()?.toString() ?: "snapshot 返回空",
                )
            }
            return
        }
        _perf.update {
            val cpuHist = (it.cpuHistory + snap.cpu.loadPct).takeLast(90)
            val ramHist = (it.ramHistory + safePct(snap.ramUsed, snap.ramTotal) * 100f).takeLast(90)
            val tempHist = if (snap.battery.tempC > 0f) {
                (it.tempHistory + snap.battery.tempC).takeLast(90)
            } else {
                it.tempHistory
            }
            val currentHist = (it.currentHistory + kotlin.math.abs(snap.battery.currentUa) / 1000f)
                .takeLast(90)
            it.copy(
                snapshot = snap.copy(processes = it.snapshot.processes),
                cpuHistory = cpuHist,
                ramHistory = ramHist,
                tempHistory = tempHist,
                currentHistory = currentHist,
                lastSampleAt = System.currentTimeMillis(),
                sampleError = null,
                readChannel = PrivilegedShell.channelLabel,
            )
        }
        val governor = withContext(Dispatchers.IO) { perfRepo.cpuGovernor() }
        val presets = withContext(Dispatchers.IO) { perfRepo.presets() }
        _perf.update { it.copy(governor = governor, presets = presets) }
        if (includeProcesses) {
            // Async and non-blocking: a slow root shell or dumpsys cannot stall
            // the sampler any more.
            viewModelScope.launch(Dispatchers.IO) {
                val procs = runCatching { perfRepo.processLoads() }.getOrDefault(emptyList())
                _perf.update { current ->
                    current.copy(snapshot = current.snapshot.copy(processes = procs))
                }
            }
        }
        if (includeHistory) {
            val batteryHistory = withContext(Dispatchers.IO) { perfRepo.batteryHistory() }
            _perf.update { it.copy(history = batteryHistory ?: it.history) }
            withContext(Dispatchers.IO) { perfRepo.recordBattery(snap.battery) }
        }
    }

    fun refreshDrain() {
        viewModelScope.launch {
            val drain = withContext(Dispatchers.IO) { perfRepo.topDrain() }
            _perf.update { it.copy(drain = drain) }
        }
    }

    fun setChargingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { LineageHealth.setEnabled(enabled) }
            _perf.update {
                it.copy(
                    charging = it.charging.copy(enabled = enabled),
                    toast = if (ok) (if (enabled) "充电控制已开启" else "充电控制已关闭") else "设置失败，设备可能不支持",
                )
            }
            refreshPerf()
        }
    }

    fun setChargingLimit(limit: Int) {
        prefs.lastChargingLimit = limit
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { LineageHealth.setLimit(limit) }
            _perf.update {
                it.copy(
                    charging = it.charging.copy(limit = limit),
                    toast = if (ok) "充电上限已设为 $limit%" else "设置失败",
                )
            }
            refreshPerf()
        }
    }

    fun setChargingMode(mode: Int) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { LineageHealth.setMode(mode) }
            _perf.update {
                it.copy(
                    charging = it.charging.copy(mode = mode),
                    toast = if (ok) "充电模式：${LineageHealth.modeLabel(mode)}" else "该模式不被设备支持",
                )
            }
            refreshPerf()
        }
    }

    fun setChargingWindow(startSec: Int, targetSec: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LineageHealth.setStartTime(startSec)
                LineageHealth.setTargetTime(targetSec)
            }
            _perf.update { it.copy(toast = "已设置充电时段") }
            refreshPerf()
        }
    }

    fun setFastCharge(mode: Int) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { LineageHealth.setFastChargeMode(mode) }
            _perf.update {
                it.copy(
                    charging = it.charging.copy(fastMode = mode),
                    toast = if (ok) "快充模式：${LineageHealth.fastModeLabel(mode)}" else "设置失败",
                )
            }
            refreshPerf()
        }
    }

    fun clearPerfToast() = _perf.update { it.copy(toast = null) }

    fun killBackground() {
        if (!_caps.value.privileged) {
            _perf.update { it.copy(toast = "回收后台需要 Root 或无线 ADB") }
            return
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Shell.run("am kill-all") }
            _perf.update {
                it.copy(
                    toast = if (r.ok) "已回收后台进程（前台与系统关键进程不受影响）"
                    else "失败：${r.text.take(120)}",
                )
            }
            refreshPerf()
        }
    }

    // ---------------- security ----------------

    fun refreshSecurity() {
        viewModelScope.launch {
            val privacy = withContext(Dispatchers.IO) { appRepo.sensitiveApps() }
            val risks = withContext(Dispatchers.IO) { securityRepo.risks() }
            val captures = withContext(Dispatchers.IO) { securityRepo.activeCaptures() }
            _security.update {
                it.copy(
                    report = it.report.copy(
                        privacyApps = privacy,
                        risks = risks,
                        activeCaptures = captures,
                        scannedAt = it.report.scannedAt,
                    ),
                    firewallBlocked = prefs.blockedUids(),
                    rootFirewall = securityRepo.firewallSupported(),
                )
            }
        }
    }

    fun scanTrackers(pkg: String? = null, onlyUser: Boolean = true) {
        if (_security.value.scanning) return
        _security.update { it.copy(scanning = true, progress = "准备扫描") }
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { appRepo.list() }
            val targets = if (pkg != null) all.filter { it.pkg == pkg }
            else all.filter { it.enabled && (!onlyUser || !it.system) && it.apkPath.isNotBlank() }
            val hits = withContext(Dispatchers.IO) {
                securityRepo.scanTrackers(targets) { i, n, label ->
                    _security.update { it.copy(progress = "扫描 $label（${i + 1}/$n）") }
                }
            }
            _security.update {
                it.copy(
                    scanning = false,
                    progress = "",
                    report = it.report.copy(trackerHits = hits, scannedAt = System.currentTimeMillis()),
                    toast = "扫描完成：${hits.size} 个应用命中追踪 SDK",
                )
            }
            if (pkg != null) {
                val trackers = hits.firstOrNull { it.pkg == pkg }?.trackers.orEmpty()
                _apps.update { st ->
                    st.copy(detail = st.detail?.copy(trackers = trackers))
                }
            }
        }
    }

    fun toggleFirewall(pkg: String, uid: Int, blocked: Boolean) {
        prefs.setFirewallBlocked(uid, blocked)
        val set = prefs.blockedUids()
        _security.update { it.copy(firewallBlocked = set) }
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) { securityRepo.applyFirewall(set) }
            _security.update {
                it.copy(
                    toast = if (ok) (if (blocked) "已切断联网：$pkg" else "已恢复联网：$pkg")
                    else "失败：$msg",
                )
            }
        }
    }

    fun clearFirewall() {
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) { securityRepo.clearFirewall() }
            if (ok) prefs.blockedUids().forEach { prefs.setFirewallBlocked(it, false) }
            _security.update {
                it.copy(
                    firewallBlocked = if (ok) emptySet() else it.firewallBlocked,
                    toast = if (ok) "已清空防火墙规则" else "失败：$msg",
                )
            }
        }
    }

    fun clearSecurityToast() = _security.update { it.copy(toast = null) }

    // ---------------- autostart ----------------

    fun loadAutostart() {
        _autostart.update { it.copy(loading = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { appRepo.autostartApps() }
            _autostart.update { it.copy(loading = false, items = list) }
        }
    }

    fun setAutostartQuery(q: String) = _autostart.update { it.copy(query = q) }

    fun setAutostartOnlyUser(v: Boolean) = _autostart.update { it.copy(onlyUser = v) }

    fun setAutostartOnlyEnabled(v: Boolean) = _autostart.update { it.copy(onlyEnabled = v) }

    fun toggleAutostartComponent(app: io.github.dryearth.Nanaya.data.AppEntry, comp: io.github.dryearth.Nanaya.data.BootComponent, enabled: Boolean) {
        _autostart.update { it.copy(busy = comp.component) }
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                appRepo.setComponentEnabled(app.pkg, comp.component, enabled)
            }
            _autostart.update {
                it.copy(
                    busy = null,
                    toast = if (ok) {
                        if (enabled) "已启用 ${comp.shortName}" else "已禁用 ${comp.shortName}"
                    } else "失败：$msg",
                )
            }
            loadAutostart()
        }
    }

    fun setAutostartBackground(app: io.github.dryearth.Nanaya.data.AppEntry, restrict: Boolean) {
        _autostart.update { it.copy(busy = app.pkg) }
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                appRepo.restrictBackground(app.pkg, app.uid, restrict)
            }
            _autostart.update {
                it.copy(
                    busy = null,
                    toast = if (ok) {
                        if (restrict) "已限制 ${app.label} 后台" else "已恢复 ${app.label} 后台"
                    } else "失败：$msg",
                )
            }
            loadAutostart()
        }
    }

    fun setAutostartStandby(app: io.github.dryearth.Nanaya.data.AppEntry, bucket: String) {
        _autostart.update { it.copy(busy = app.pkg) }
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                appRepo.setStandbyBucket(app.pkg, bucket)
            }
            _autostart.update {
                it.copy(
                    busy = null,
                    toast = if (ok) "${app.label} 已设为 $bucket" else "失败：$msg",
                )
            }
        }
    }

    fun clearAutostartToast() = _autostart.update { it.copy(toast = null) }

    // ---------------- AI ----------------

    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        prefs.aiBaseUrl = baseUrl.trim()
        prefs.aiApiKey = apiKey.trim()
        prefs.aiModel = model.trim()
        _chat.update { it.copy(modelLabel = ai.describe(), modelConfigured = ai.configured(), toast = "云端模型已保存") }
    }

    fun savePersona(name: String, prompt: String, user: String) {
        prefs.personaName = name.trim().ifBlank { "汐" }
        prefs.personaPrompt = prompt.trim()
        prefs.userTitle = user.trim().ifBlank { "你" }
        _chat.update { it.copy(toast = "人设已保存") }
    }

    fun saveVoiceConfig(sttPackage: String, ttsEngine: String, speakReplies: Boolean) {
        prefs.sttPackage = sttPackage.trim()
        prefs.ttsEngine = ttsEngine.trim()
        prefs.aiSpeakReplies = speakReplies
        _chat.update { it.copy(toast = "语音设置已保存") }
    }

    fun setBackground(style: String, uri: String = prefs.backgroundUri) {
        prefs.backgroundStyle = style
        prefs.backgroundUri = uri
        _appearance.value = Appearance(style, uri, prefs.backgroundSoften)
    }

    fun setBackgroundSoften(enabled: Boolean) {
        prefs.backgroundSoften = enabled
        _appearance.value = Appearance(prefs.backgroundStyle, prefs.backgroundUri, enabled)
    }

    fun sendChat(
        text: String,
        imagePath: String? = null,
        imageSendPath: String? = null,
        imageThumbPath: String? = null,
    ) {
        val input = text.trim().ifBlank { if (imageSendPath != null) "看看这张图" else "" }
        if (input.isBlank() || _chat.value.sending) return
        val user = ChatMessage(
            role = ChatRole.USER,
            text = input,
            imagePath = imagePath,
            imageSendPath = imageSendPath,
            imageThumbPath = imageThumbPath,
        )
        _chat.update { it.copy(messages = trimChatImages(it.messages + user), sending = true) }
        val musicIntent = if (imageSendPath == null) MusicIntentParser.parse(input) else null
        if (musicIntent != null) {
            viewModelScope.launch {
                val reply = runCatching { MusicAssistant.handle(getApplication(), musicIntent) }
                    .getOrElse { "音乐操作失败：${it.message ?: "未知错误"}" }
                _chat.update {
                    it.copy(
                        sending = false,
                        messages = trimChatImages(
                            it.messages + ChatMessage(role = ChatRole.ASSISTANT, text = reply),
                        ),
                    )
                }
            }
            return
        }
        viewModelScope.launch {
            runCatching { ai.complete(buildSystemPrompt(), _chat.value.messages) }
                .onSuccess { raw ->
                    val (reply, action) = parseResponse(raw)
                    _chat.update {
                        it.copy(
                            sending = false,
                            messages = trimChatImages(it.messages + ChatMessage(
                                role = ChatRole.ASSISTANT,
                                text = reply,
                                action = action,
                                actionState = if (action != null) ActionState.PENDING else ActionState.NONE,
                            )),
                        )
                    }
                }
                .onFailure { e ->
                    _chat.update {
                        it.copy(
                            sending = false,
                            messages = trimChatImages(it.messages + ChatMessage(
                                role = ChatRole.ASSISTANT,
                                text = "调用模型失败：${e.message}",
                            )),
                        )
                    }
                }
        }
    }

    /** Keep only the newest four images so base64 strings cannot grow forever. */
    private fun trimChatImages(messages: List<ChatMessage>): List<ChatMessage> {
        val keep = messages.filter { it.imageSendPath != null }.takeLast(4).map { it.id }.toSet()
        val images = messages.count { it.imageSendPath != null }
        if (keep.size == images) return messages
        return messages.map { message ->
            if (message.imageSendPath != null && message.id !in keep) {
                message.copy(imagePath = null, imageSendPath = null, imageThumbPath = null)
            } else message
        }
    }

    fun confirmAction(id: Long) {
        val msg = _chat.value.messages.firstOrNull { it.id == id } ?: return
        val action = msg.action ?: return
        updateMessage(id) { it.copy(actionState = ActionState.RUNNING) }
        viewModelScope.launch {
            val result = runCatching { execute(action) }
                .getOrElse { "执行失败：${it.message}" }
            updateMessage(id) {
                it.copy(
                    actionState = if (result.startsWith("执行失败") || result.startsWith("失败")) ActionState.FAILED
                    else ActionState.DONE,
                    actionResult = result,
                )
            }
        }
    }

    fun rejectAction(id: Long) {
        updateMessage(id) { it.copy(actionState = ActionState.REJECTED, actionResult = "已取消") }
    }

    fun clearChat() {
        _chat.update { it.copy(messages = emptyList(), toast = null) }
        viewModelScope.launch(Dispatchers.IO) {
            ChatImageStore.clear(getApplication())
        }
    }

    fun clearChatToast() = _chat.update { it.copy(toast = null) }

    fun consumeNavigation(): String? {
        val v = _navigate.value
        _navigate.value = null
        return v
    }

    private fun updateMessage(id: Long, block: (ChatMessage) -> ChatMessage) {
        _chat.update { st ->
            st.copy(messages = st.messages.map { if (it.id == id) block(it) else it })
        }
    }

    private fun parseResponse(raw: String): Pair<String, ButlerAction?> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return cleaned to null
        val json = runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
            ?: return cleaned to null
        val reply = json.optString("reply").ifBlank { json.optString("message") }
        val actionObj = json.optJSONObject("action")
        val action = if (actionObj != null) {
            val type = actionObj.optString("type")
            val argsObj = actionObj.optJSONObject("args")
            val args = HashMap<String, String>()
            if (argsObj != null) {
                for (key in argsObj.keys()) args[key] = argsObj.optString(key)
            }
            if (type.isBlank()) null else describeAction(ButlerAction(type, args))
        } else null
        return reply.ifBlank { if (action == null) cleaned else "我可以执行：${action.title}" } to action
    }

    private fun describeAction(action: ButlerAction): ButlerAction {
        val args = action.args
        val appToken = args["package"] ?: args["app"] ?: ""
        val app = if (appToken.isNotBlank()) resolveApp(appToken) else null
        val name = app?.label ?: appToken.ifBlank { "所选应用" }
        return when (action.type) {
            "clean_caches" -> action.copy(
                title = "清理应用缓存",
                detail = if (appToken.isBlank()) "清理缓存最大的 8 个应用（只清 cache，不动数据）"
                else "清理 $name 的 cache 目录（不删除账号与数据）",
            )
            "freeze_app" -> action.copy(
                title = "冻结 $name",
                detail = "pm disable-user --user 0 ${app?.pkg ?: appToken}；应用会从桌面消失，可随时解冻",
            )
            "unfreeze_app" -> action.copy(
                title = "解冻 $name",
                detail = "pm enable ${app?.pkg ?: appToken}",
            )
            "restrict_background" -> action.copy(
                title = "限制 $name 后台",
                detail = "AppOps RUN_IN_BACKGROUND / RUN_ANY_IN_BACKGROUND = ignore",
            )
            "revoke_permission" -> action.copy(
                title = "收回 $name 的权限",
                detail = "目标权限：${args["permission"] ?: "未指定"}",
            )
            "set_charging_limit" -> action.copy(
                title = "设置充电上限",
                detail = "上限 ${args["limit"] ?: "80"}% · 通过 LineageOS lineagehealth 服务",
            )
            "scan_storage" -> action.copy(title = "扫描存储", detail = "遍历文件、重复项与应用缓存")
            "scan_trackers" -> action.copy(title = "扫描追踪器", detail = "离线读取 APK 的 DEX 字符串")
            "firewall" -> action.copy(
                title = "修改防火墙",
                detail = "${if (args["blocked"] == "true") "阻断" else "放行"} $name 的联网",
            )
            "lineage_feature" -> action.copy(
                title = "开关 LineageOS 特性",
                detail = "${args["feature"] ?: "特性"} → ${if (args["enabled"] != "false") "开启" else "关闭"}（lineagehardware）",
            )
            "create_automation" -> action.copy(
                title = "创建自动化",
                detail = "${args["name"] ?: "新规则"} · ${args["trigger"] ?: ""} → ${args["action"] ?: ""}",
            )
            "list_automations" -> action.copy(title = "查看自动化", detail = "读取本地规则")
            "device_state" -> action.copy(title = "读取设备状态", detail = "电量、亮度、音量、响铃、网络省电")
            "set_brightness" -> action.copy(title = "设置亮度", detail = "亮度 ${args["percent"] ?: "50"}%")
            "set_screen_timeout" -> action.copy(title = "设置息屏时间", detail = "${args["seconds"] ?: "60"} 秒")
            "set_volume" -> action.copy(title = "设置媒体音量", detail = "${args["percent"] ?: "50"}%")
            "set_ringer" -> action.copy(title = "设置响铃模式", detail = args["mode"] ?: "normal")
            "open_settings" -> action.copy(title = "打开系统设置", detail = args["page"] ?: "settings")
            "launch_app" -> action.copy(title = "启动应用", detail = name)
            "standby" -> action.copy(
                title = "设置待机分组",
                detail = "$name → ${args["bucket"] ?: "restricted"}（am set-standby-bucket）",
            )
            "open_screen" -> action.copy(title = "打开页面", detail = args["screen"] ?: "首页")
            "list_drain" -> action.copy(title = "查看耗电排行", detail = "读取 UsageStats 与 cpuinfo")
            else -> action.copy(title = action.type, detail = args.entries.joinToString { "${it.key}=${it.value}" })
        }
    }

    private suspend fun execute(action: ButlerAction): String {
        val args = action.args
        return when (action.type) {
            "scan_storage" -> {
                scanStorage()
                "已开始扫描存储，完成后在「存储」页查看。"
            }
            "clean_caches" -> {
                val token = args["package"] ?: args["app"]
                val pkgs = if (token.isNullOrBlank()) {
                    val caches = _storage.value.report?.appCaches
                        ?: withContext(Dispatchers.IO) { storageRepo.collectAppCaches() }
                    caches.take(8).map { it.pkg }
                } else {
                    listOfNotNull(
                        resolveApp(token)?.pkg
                            ?: token.takeIf { Shell.isValidPackage(it) },
                    )
                }
                if (pkgs.isEmpty()) "没有找到可清理的应用。" else {
                    val r = withContext(Dispatchers.IO) { storageRepo.clearAppCaches(pkgs) }
                    "已清理 ${r.deleted} 个应用缓存，释放 ${r.freed.bytes()}。"
                }
            }
            "freeze_app", "unfreeze_app" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "")
                    ?: return "没有找到该应用"
                val freeze = action.type == "freeze_app"
                val (ok, msg) = withContext(Dispatchers.IO) { appRepo.freeze(app.pkg, freeze) }
                refreshApps()
                if (ok) "已${if (freeze) "冻结" else "解冻"} ${app.label}。" else "失败：$msg"
            }
            "restrict_background" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "") ?: return "没有找到该应用"
                val (ok, msg) = withContext(Dispatchers.IO) {
                    appRepo.restrictBackground(app.pkg, app.uid, args["restrict"] != "false")
                }
                if (ok) "已限制 ${app.label} 的后台活动。" else "失败：$msg"
            }
            "revoke_permission" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "") ?: return "没有找到该应用"
                val perm = normalizePermission(args["permission"] ?: "")
                if (perm == null) return "没有识别出要收回的权限"
                val (ok, msg) = withContext(Dispatchers.IO) { appRepo.revoke(app.pkg, perm) }
                if (ok) "已收回 ${app.label} 的 $perm。" else "失败：$msg"
            }
            "set_charging_limit" -> {
                val enabled = args["enabled"] != "false"
                val limit = (args["limit"]?.toIntOrNull() ?: prefs.lastChargingLimit).coerceIn(70, 100)
                val supported = withContext(Dispatchers.IO) {
                    LineageHealth.setLimit(limit)
                    LineageHealth.setMode(ChargingState.MODE_LIMIT)
                    LineageHealth.setEnabled(enabled)
                }
                refreshPerf()
                if (supported) "充电上限已设为 $limit%" else "设备没有提供充电控制接口。"
            }
            "firewall" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "") ?: return "没有找到该应用"
                val blocked = args["blocked"] != "false"
                prefs.setFirewallBlocked(app.uid, blocked)
                val (ok, msg) = withContext(Dispatchers.IO) { securityRepo.applyFirewall(prefs.blockedUids()) }
                _security.update { it.copy(firewallBlocked = prefs.blockedUids()) }
                if (ok) "${if (blocked) "已阻断" else "已放行"} ${app.label} 的联网。" else "失败：$msg"
            }
            "scan_trackers" -> {
                val app = args["package"]?.let { resolveApp(it) }
                scanTrackers(app?.pkg, onlyUser = app == null)
                "已开始扫描追踪 SDK，完成后在「安全」页查看。"
            }
            "list_drain" -> {
                refreshDrain()
                "已刷新 24 小时耗电排行，见「性能」页。"
            }
            "lineage_feature" -> {
                val token = args["feature"] ?: return "没有指定特性"
                val feature = LineageHardware.FEATURES.firstOrNull {
                    it.label.contains(token, true) || token.contains(it.label, true)
                } ?: return "没有找到特性：$token"
                val enabled = args["enabled"] != "false"
                val ok = withContext(Dispatchers.IO) { LineageHardware.set(feature.bit, enabled) }
                if (ok) "已${if (enabled) "开启" else "关闭"} ${feature.label}。" else "设备拒绝了这项写入。"
            }
            "create_automation" -> {
                val name = args["name"]?.ifBlank { "AI 自动化" } ?: "AI 自动化"
                val trigger = args["trigger"] ?: return "没有指定触发条件"
                val action = args["action"] ?: return "没有指定动作"
                val rule = AutomationRule(
                    name = name,
                    trigger = trigger,
                    triggerValue = args["trigger_value"] ?: args["triggerValue"] ?: "",
                    action = action,
                    actionValue = args["action_value"] ?: args["actionValue"] ?: "",
                )
                AutomationRepository(getApplication()).add(rule)
                "已创建自动化「$name」，可在首页「自动化」里修改或关闭。"
            }
            "list_automations" -> {
                val rules = AutomationRepository(getApplication()).all()
                if (rules.isEmpty()) "还没有自动化规则。" else rules.joinToString("\n") {
                    "• ${it.name}：${it.trigger}/${it.triggerValue} → ${it.action}/${it.actionValue}（${if (it.enabled) "开" else "关"}）"
                }
            }
            "device_state" -> deviceState()
            "set_brightness" -> {
                val percent = (args["percent"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
                val value = percent * 255 / 100
                val r = withContext(Dispatchers.IO) { Shell.run("settings put system screen_brightness $value") }
                if (r.ok) "亮度已设为 $percent%。" else "需要 Root 或无线 ADB 才能改系统亮度。"
            }
            "set_screen_timeout" -> {
                val sec = (args["seconds"]?.toIntOrNull() ?: 60).coerceIn(15, 1800)
                val r = withContext(Dispatchers.IO) {
                    Shell.run("settings put system screen_off_timeout ${sec * 1000}")
                }
                if (r.ok) "息屏时间已设为 $sec 秒。" else "需要 Root 或无线 ADB。"
            }
            "set_volume" -> {
                val percent = (args["percent"]?.toIntOrNull() ?: 50).coerceIn(0, 100)
                val am = getApplication<Application>().getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val ok = runCatching {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
                }.isSuccess
                if (ok) "媒体音量已设为 $percent%。" else "系统拒绝了音量修改。"
            }
            "set_ringer" -> {
                val am = getApplication<Application>().getSystemService(AudioManager::class.java)
                val mode = when ((args["mode"] ?: "").lowercase()) {
                    "silent", "静音" -> AudioManager.RINGER_MODE_SILENT
                    "vibrate", "振动" -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                val ok = runCatching { am.ringerMode = mode }.isSuccess
                if (ok) "响铃模式已切换。" else "没有勿扰访问权限，系统拒绝了切换。"
            }
            "open_settings" -> openSettingsPage(args["page"] ?: "settings")
            "launch_app" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "") ?: return "没有找到该应用"
                val intent = getApplication<Application>().packageManager.getLaunchIntentForPackage(app.pkg)
                    ?: return "这个应用没有可启动的入口"
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { getApplication<Application>().startActivity(intent) }
                "已启动 ${app.label}。"
            }
            "standby" -> {
                val app = resolveApp(args["package"] ?: args["app"] ?: "") ?: return "没有找到该应用"
                val bucket = args["bucket"] ?: "restricted"
                val (ok, msg) = withContext(Dispatchers.IO) { appRepo.setStandbyBucket(app.pkg, bucket) }
                if (ok) "${app.label} 已设为待机分组 $bucket。" else "失败：$msg"
            }
            "open_screen" -> {
                _navigate.value = args["screen"] ?: "home"
                "已为你打开。"
            }
            else -> "暂不支持这个动作：${action.type}"
        }
    }

    private fun resolveApp(token: String): io.github.dryearth.Nanaya.data.AppEntry? {
        val t = token.trim()
        if (t.isBlank()) return null
        val list = _apps.value.apps
        return list.firstOrNull { it.pkg.equals(t, true) }
            ?: list.firstOrNull { it.label.equals(t, true) }
            ?: list.firstOrNull { it.label.contains(t, true) }
            ?: list.firstOrNull { it.pkg.contains(t, true) }
    }

    private fun normalizePermission(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.startsWith("android.permission.") &&
            raw.matches(Regex("android\\.permission\\.[A-Za-z0-9._]+"))
        ) return raw
        return when (raw) {
            "相机", "摄像头", "camera" -> "android.permission.CAMERA"
            "麦克风", "录音", "mic", "microphone" -> "android.permission.RECORD_AUDIO"
            "位置", "定位", "location" -> "android.permission.ACCESS_FINE_LOCATION"
            "后台位置" -> "android.permission.ACCESS_BACKGROUND_LOCATION"
            "联系人", "contacts" -> "android.permission.READ_CONTACTS"
            "短信", "sms" -> "android.permission.READ_SMS"
            "通话记录", "call log" -> "android.permission.READ_CALL_LOG"
            "电话", "phone" -> "android.permission.CALL_PHONE"
            "日历", "calendar" -> "android.permission.READ_CALENDAR"
            else -> null
        }
    }

    private fun deviceState(): String {
        val app = getApplication<Application>()
        val bm = app.getSystemService(BatteryManager::class.java)
        val am = app.getSystemService(AudioManager::class.java)
        val nm = app.getSystemService(NotificationManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = runCatching { bm.isCharging }.getOrDefault(false)
        val brightness = runCatching {
            Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(-1)
        val volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val vmax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringer = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "静音"
            AudioManager.RINGER_MODE_VIBRATE -> "振动"
            else -> "正常"
        }
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return "电量 $battery%${if (charging) " · 充电中" else ""}；亮度 $brightness/255；" +
            "媒体音量 $volume/$vmax；响铃 $ringer；省电模式 ${pm.isPowerSaveMode}；" +
            "Wi-Fi ${if (wifi) "已连接" else "未连接"}；移动数据 ${if (cell) "已连接" else "未连接"}；" +
            "勿扰过滤 ${nm.currentInterruptionFilter}；Root=${Shell.rootReady}"
    }

    private fun openSettingsPage(page: String): String {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val ok = runCatching {
            getApplication<Application>().startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        return if (ok) "已打开系统设置：$page。" else "系统没有打开这个设置页。"
    }

    private suspend fun buildSystemPrompt(): String {
        val c = _caps.value
        val p = _perf.value
        val s = _storage.value.report
        val appsCount = _apps.value.apps.size
        val sen = _security.value.report
        return """
你是「${prefs.personaName}」，住在用户 LineageOS 24（Android 17）手机里的设备管家。
用户自定义人设：${prefs.personaPrompt.ifBlank { "冷静、简短、可靠，不卖萌" }}
称呼用户：${prefs.userTitle}
你只能通过下面列出的 action 操作设备，每次操作都会由用户二次确认后执行。
不确定的数据不要编造；需要先扫描就提出扫描。

设备状态：
- 机型：${c.device}，${c.android}
- 权限：Root=${c.root}，无线ADB=${c.adbConnected}，所有文件访问=${c.allFiles}，使用情况访问=${c.usageAccess}
- 充电控制：${if (p.charging.supported) "支持，当前上限 ${p.charging.limit}%，模式 ${LineageHealth.modeLabel(p.charging.mode)}" else "不支持"}
- LineageOS 硬件接口：${if (io.github.dryearth.Nanaya.core.LineageHardware.bind()) "已连接 lineagehardware" else "未连接"}
- 电池：${p.snapshot.battery.level}%，${p.snapshot.battery.status}，温度 ${p.snapshot.battery.tempC}℃
- 内存：${p.snapshot.ramUsed.bytes()} / ${p.snapshot.ramTotal.bytes()}
- 存储：${if (s != null) "${s.used.bytes()} / ${s.total.bytes()}，剩余 ${s.free.bytes()}" else "尚未扫描"}
- 应用：$appsCount 个；敏感权限应用 ${sen.privacyApps.size} 个；高风险项 ${sen.risks.count { it.level == RiskLevel.HIGH }} 个

可用 action（JSON）：
1. {"type":"scan_storage","args":{}}
2. {"type":"clean_caches","args":{"package":"可选，留空表示缓存最大的 8 个应用"}}
3. {"type":"freeze_app","args":{"package":"应用名或包名"}}
4. {"type":"unfreeze_app","args":{"package":"应用名或包名"}}
5. {"type":"restrict_background","args":{"package":"应用名或包名","restrict":"true"}}
6. {"type":"revoke_permission","args":{"package":"应用名或包名","permission":"相机/麦克风/位置/联系人/短信"}}
7. {"type":"set_charging_limit","args":{"enabled":"true","limit":"80"}}
8. {"type":"scan_trackers","args":{"package":"可选"}}
9. {"type":"firewall","args":{"package":"应用名或包名","blocked":"true"}}
10. {"type":"list_drain","args":{}}
11. {"type":"lineage_feature","args":{"feature":"防闪烁/色彩增强/自适应背光等","enabled":"true"}}
12. {"type":"standby","args":{"package":"应用名或包名","bucket":"restricted|rare|frequent|active"}}
13. {"type":"open_screen","args":{"screen":"home|storage|apps|perf|security"}}
14. {"type":"device_state","args":{}}
15. {"type":"set_brightness","args":{"percent":"50"}}
16. {"type":"set_screen_timeout","args":{"seconds":"60"}}
17. {"type":"set_volume","args":{"percent":"50"}}
18. {"type":"set_ringer","args":{"mode":"normal|silent|vibrate"}}
19. {"type":"open_settings","args":{"page":"wifi|bluetooth|battery|display|sound|apps|accessibility|notifications|location"}}
20. {"type":"launch_app","args":{"package":"应用名或包名"}}
21. {"type":"create_automation","args":{"name":"规则名","trigger":"time|battery_low|battery_full|charging_start|screen_off|wifi_on|wifi_off|boot","trigger_value":"23:00 或 25","action":"speak|notify|charge_limit|restrict_app|standby|freeze|lineage_feature|brightness|ringer","action_value":"内容/百分比/应用名/特征:true"}}
22. {"type":"list_automations","args":{}}

输出规则：
- 只输出一个 JSON 对象，不要 Markdown，不要代码围栏，不要额外文字。
- 格式：{"reply":"给用户看的中文回答","action":{"type":"...","args":{...}}}
- 没有要执行的动作时，省略 action 字段。
- 一次只给一个 action；需要多步时先做第一步，再询问用户。
- 创建自动化时，先解释触发条件与后果；删除文件、冻结、断网等动作仍要用户确认。
- 删除文件、卸载、冻结、断网都属于破坏性操作，reply 里要先说明影响。
        """.trimIndent()
    }

    override fun onCleared() {
        PrivilegedShell.close()
        super.onCleared()
    }
}
