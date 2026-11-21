package io.github.dryearth.Nanaya.core

import android.os.IBinder
import lineageos.health.IHealthInterface

data class ChargingState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val mode: Int = 1,
    val limit: Int = 80,
    val startTime: Int = 0,
    val targetTime: Int = 0,
    val fineGrained: Boolean = false,
    val fastSupported: Boolean = false,
    val fastModes: List<Int> = emptyList(),
    val fastMode: Int = 1,
    val error: String? = null,
) {
    companion object {
        const val MODE_AUTO = 1
        const val MODE_MANUAL = 2
        const val MODE_LIMIT = 3
    }
}

/**
 * LineageOS 24 charging control, straight from the official
 * `lineagehealth` system service (IHealthInterface).
 *
 * Verified against android_lineage-sdk, branch lineage-24.0:
 * the service exposes charging control / fast charge without a
 * permission check on those transactions, so no root is required.
 */
object LineageHealth {

    private const val TAG = "LineageHealth"
    private var service: IHealthInterface? = null

    @Volatile
    var lastError: String? = null
        private set

    fun bind(): Boolean {
        if (service != null) return true
        return runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "lineagehealth") as? IBinder
            service = binder?.let { IHealthInterface.Stub.asInterface(it) }
            service != null
        }.getOrElse {
            lastError = it.message
            false
        }
    }

    fun available(): Boolean = bind() && runCatching {
        service!!.isChargingControlSupported
    }.getOrDefault(false)

    fun read(): ChargingState {
        if (!bind()) return ChargingState(error = "系统未提供 lineagehealth 服务")
        val s = service ?: return ChargingState(error = "lineagehealth 服务为空")
        return runCatching {
            val supported = s.isChargingControlSupported
            if (!supported) return ChargingState(
                supported = false,
                error = "该设备的内核/HAL 未提供充电控制",
            )
            ChargingState(
                supported = true,
                enabled = s.chargingControlEnabled,
                mode = s.chargingControlMode,
                limit = s.chargingControlLimit,
                startTime = s.chargingControlStartTime,
                targetTime = s.chargingControlTargetTime,
                fineGrained = s.allowFineGrainedSettings(),
                fastSupported = s.isFastChargeSupported,
                fastModes = s.supportedFastChargeModes?.toList() ?: emptyList(),
                fastMode = s.fastChargeMode,
            )
        }.getOrElse {
            lastError = it.message
            ChargingState(error = it.message ?: "读取充电控制失败")
        }
    }

    fun setEnabled(enabled: Boolean): Boolean = call { it.setChargingControlEnabled(enabled) }

    fun setMode(mode: Int): Boolean = call { it.setChargingControlMode(mode) }

    fun setLimit(limit: Int): Boolean = call { it.setChargingControlLimit(limit.coerceIn(70, 100)) }

    fun setStartTime(secondsOfDay: Int): Boolean = call { it.setChargingControlStartTime(secondsOfDay) }

    fun setTargetTime(secondsOfDay: Int): Boolean = call { it.setChargingControlTargetTime(secondsOfDay) }

    fun reset(): Boolean = call { it.resetChargingControl() }

    fun setFastChargeMode(mode: Int): Boolean = call { it.setFastChargeMode(mode) }

    fun fastModeLabel(mode: Int): String = when (mode) {
        1 -> "关闭"
        2 -> "快充"
        4 -> "超级快充"
        else -> "模式 $mode"
    }

    fun modeLabel(mode: Int): String = when (mode) {
        ChargingState.MODE_AUTO -> "自动"
        ChargingState.MODE_MANUAL -> "定时"
        ChargingState.MODE_LIMIT -> "限值"
        else -> "未设置"
    }

    private fun call(block: (IHealthInterface) -> Boolean): Boolean {
        if (!bind()) return false
        return runCatching { block(service!!) }.getOrElse {
            lastError = it.message
            false
        }
    }
}
