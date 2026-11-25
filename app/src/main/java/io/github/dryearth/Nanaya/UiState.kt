package io.github.dryearth.Nanaya

import io.github.dryearth.Nanaya.core.Capabilities
import io.github.dryearth.Nanaya.core.ChargingState
import io.github.dryearth.Nanaya.data.AppDetail
import io.github.dryearth.Nanaya.data.AppDrain
import io.github.dryearth.Nanaya.data.AppEntry
import io.github.dryearth.Nanaya.data.AutostartApp
import io.github.dryearth.Nanaya.data.ChatMessage
import io.github.dryearth.Nanaya.data.PerfPreset
import io.github.dryearth.Nanaya.data.PerfSnapshot
import io.github.dryearth.Nanaya.data.SecurityReport
import io.github.dryearth.Nanaya.data.StorageReport

enum class AppFilter { ALL, USER, SYSTEM, FROZEN }

data class StorageUiState(
    val scanning: Boolean = false,
    val stage: String = "",
    val progress: Float = 0f,
    val report: StorageReport? = null,
    val error: String? = null,
    val toast: String? = null,
    val cleaning: Boolean = false,
)

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val detail: AppDetail? = null,
    val busy: String? = null,
    val toast: String? = null,
)

data class PerfUiState(
    val snapshot: PerfSnapshot = PerfSnapshot(),
    val charging: ChargingState = ChargingState(),
    val drain: List<AppDrain> = emptyList(),
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val tempHistory: List<Float> = emptyList(),
    val currentHistory: List<Float> = emptyList(),
    val governor: String = "",
    val presets: List<PerfPreset> = emptyList(),
    val history: List<Triple<Long, Int, Int>> = emptyList(),
    val toast: String? = null,
    val monitoring: Boolean = false,
    val intervalMs: Int = 1000,
    val lastSampleAt: Long = 0L,
    val sampleError: String? = null,
    val readChannel: String = "",
)

data class SecurityUiState(
    val report: SecurityReport = SecurityReport(),
    val scanning: Boolean = false,
    val progress: String = "",
    val firewallBlocked: Set<Int> = emptySet(),
    val toast: String? = null,
    val rootFirewall: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val toast: String? = null,
    val modelLabel: String = "",
    val modelConfigured: Boolean = false,
)


data class AutostartUiState(
    val loading: Boolean = true,
    val items: List<AutostartApp> = emptyList(),
    val query: String = "",
    val onlyUser: Boolean = true,
    val onlyEnabled: Boolean = false,
    val busy: String? = null,
    val toast: String? = null,
)


data class Appearance(val style: String = "stars", val uri: String = "", val soften: Boolean = false)
