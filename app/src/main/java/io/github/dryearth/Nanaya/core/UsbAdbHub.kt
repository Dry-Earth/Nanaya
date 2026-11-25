package io.github.dryearth.Nanaya.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Owns one USB OTG ADB connection so both the USB tool screen and the AI can
 * reuse it. The screen adopts its connection here; the connection survives
 * leaving the screen until the user disconnects or unplugs the cable.
 */
object UsbAdbHub {
    @Volatile private var client: UsbAdbClient? = null
    @Volatile private var device: UsbDevice? = null

    fun current(): UsbAdbClient? = client
    fun currentDevice(): UsbDevice? = device

    fun adopt(connection: UsbAdbClient, usbDevice: UsbDevice? = null) {
        if (client !== connection) {
            runCatching { client?.close() }
        }
        client = connection
        device = usbDevice
    }

    fun close() {
        runCatching { client?.close() }
        client = null
        device = null
    }

    fun status(context: Context): String {
        if (client != null) return "已连接"
        val manager = context.getSystemService(UsbManager::class.java) ?: return "不可用"
        val candidate = manager.deviceList.values.firstOrNull { UsbAdbClient.isAdbDevice(it) }
            ?: return "未检测到设备"
        return if (manager.hasPermission(candidate)) "已检测到设备，未连接" else "检测到设备，未授权"
    }

    /** Runs a command, connecting the first permitted USB ADB device if needed. */
    fun run(context: Context, command: String): String {
        client?.let { return it.runShell(command) }
        val manager = context.getSystemService(UsbManager::class.java)
            ?: return "USB 服务不可用"
        val candidate = manager.deviceList.values.firstOrNull { UsbAdbClient.isAdbDevice(it) }
            ?: return "没有检测到 USB ADB 设备"
        if (!manager.hasPermission(candidate)) {
            return "USB ADB 未授权，请先在 工具 → USB OTG ADB 里连接一次"
        }
        val created = UsbAdbClient(context, candidate)
        val error = runCatching { created.connect() }.getOrElse { it.message ?: "USB 连接异常" }
        if (error != null) {
            runCatching { created.close() }
            return "USB ADB 连接失败：$error"
        }
        adopt(created, candidate)
        return created.runShell(command)
    }
}
