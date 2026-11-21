package io.github.dryearth.Nanaya.core

import android.os.IBinder
import lineageos.hardware.ILineageHardwareService

data class LineageHardwareFeature(
    val bit: Int,
    val label: String,
    val description: String,
    val toggleable: Boolean = true,
)

/**
 * LineageOS 24 `lineagehardware` service. Verified against
 * android_lineage-sdk (lineage-24.0): the service is a plain binder with
 * get/set per feature, so the app can read and toggle exactly what the
 * ROM exposes, without root.
 */
object LineageHardware {

    private var service: ILineageHardwareService? = null

    const val FEATURE_ADAPTIVE_BACKLIGHT = 0x1
    const val FEATURE_COLOR_ENHANCEMENT = 0x2
    const val FEATURE_DISPLAY_COLOR_CALIBRATION = 0x4
    const val FEATURE_HIGH_TOUCH_POLLING_RATE = 0x8
    const val FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10
    const val FEATURE_KEY_DISABLE = 0x20
    const val FEATURE_KEY_SWAP = 0x40
    const val FEATURE_SUNLIGHT_ENHANCEMENT = 0x100
    const val FEATURE_VIBRATOR = 0x400
    const val FEATURE_TOUCH_HOVERING = 0x800
    const val FEATURE_AUTO_CONTRAST = 0x1000
    const val FEATURE_DISPLAY_MODES = 0x2000
    const val FEATURE_READING_ENHANCEMENT = 0x4000
    const val FEATURE_COLOR_BALANCE = 0x20000
    const val FEATURE_PICTURE_ADJUSTMENT = 0x40000
    const val FEATURE_TOUCHSCREEN_GESTURES = 0x80000
    const val FEATURE_ANTI_FLICKER = 0x200000

    val FEATURES = listOf(
        LineageHardwareFeature(FEATURE_ADAPTIVE_BACKLIGHT, "自适应背光", "根据环境光调整背光曲线"),
        LineageHardwareFeature(FEATURE_COLOR_ENHANCEMENT, "色彩增强", "提升饱和度与对比度"),
        LineageHardwareFeature(FEATURE_HIGH_TOUCH_POLLING_RATE, "高触控采样率", "提高触控刷新频率，增加耗电"),
        LineageHardwareFeature(FEATURE_HIGH_TOUCH_SENSITIVITY, "高触控灵敏度", "适合贴膜或手套操作"),
        LineageHardwareFeature(FEATURE_KEY_DISABLE, "禁用按键", "关闭硬件按键的导航功能"),
        LineageHardwareFeature(FEATURE_KEY_SWAP, "按键互换", "交换返回与多任务按键"),
        LineageHardwareFeature(FEATURE_SUNLIGHT_ENHANCEMENT, "阳光屏", "强光下提升可读性"),
        LineageHardwareFeature(FEATURE_TOUCH_HOVERING, "悬浮触控", "支持悬停手势"),
        LineageHardwareFeature(FEATURE_AUTO_CONTRAST, "自动对比度", "根据内容调整对比度"),
        LineageHardwareFeature(FEATURE_READING_ENHANCEMENT, "阅读增强", "降低蓝光、柔化画面"),
        LineageHardwareFeature(FEATURE_ANTI_FLICKER, "防闪烁", "DC 调光或高频 PWM"),
        LineageHardwareFeature(FEATURE_DISPLAY_COLOR_CALIBRATION, "色彩校准", "读取/写入 RGB 校准值", toggleable = false),
        LineageHardwareFeature(FEATURE_DISPLAY_MODES, "显示模式", "由 LineageOS 显示设置管理", toggleable = false),
        LineageHardwareFeature(FEATURE_COLOR_BALANCE, "色彩平衡", "由 LineageOS 显示设置管理", toggleable = false),
        LineageHardwareFeature(FEATURE_PICTURE_ADJUSTMENT, "画面调整", "由 LineageOS 显示设置管理", toggleable = false),
        LineageHardwareFeature(FEATURE_TOUCHSCREEN_GESTURES, "触屏手势", "由 LineageOS 手势设置管理", toggleable = false),
        LineageHardwareFeature(FEATURE_VIBRATOR, "振动器强度", "由 LineageOS 振动设置管理", toggleable = false),
    )

    fun bind(): Boolean {
        if (service != null) return true
        return runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "lineagehardware") as? IBinder
            service = binder?.let { ILineageHardwareService.Stub.asInterface(it) }
            service != null
        }.getOrDefault(false)
    }

    fun supportedMask(): Int = runCatching {
        if (!bind()) 0 else service!!.supportedFeatures
    }.getOrDefault(0)

    fun isSupported(bit: Int): Boolean = supportedMask() and bit != 0

    fun get(bit: Int): Boolean = runCatching {
        if (!bind()) false else service!!.get(bit)
    }.getOrDefault(false)

    fun set(bit: Int, enabled: Boolean): Boolean = runCatching {
        if (!bind()) false else service!!.set(bit, enabled)
    }.getOrDefault(false)

    fun supported(): List<LineageHardwareFeature> {
        val mask = supportedMask()
        return FEATURES.filter { mask and it.bit != 0 }
    }
}
