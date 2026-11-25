package io.github.dryearth.Nanaya.core

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ADB-over-USB host transport. It implements the packet framing, RSA
 * authentication and a single shell stream. It intentionally does not replace
 * the TCP libadb path; it is used only by the USB OTG screen.
 */
class UsbAdbClient(private val context: Context, private val device: UsbDevice) {
    private var connection: UsbDeviceConnection? = null
    private var intf: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val nextId = AtomicInteger(1)
    private var remoteId = 0
    private var localId = 0

    fun connect(): String? {
        val manager = context.getSystemService(UsbManager::class.java)
        if (!manager.hasPermission(device)) return "没有 USB 权限"
        val foundInterface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull {
                it.interfaceClass == 0xFF &&
                    it.interfaceSubclass == 0x42 &&
                    it.interfaceProtocol == 0x01
            } ?: return "这个 USB 设备没有 ADB 接口"
        val foundIn = (0 until foundInterface.endpointCount)
            .map { foundInterface.getEndpoint(it) }
            .firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        val foundOut = (0 until foundInterface.endpointCount)
            .map { foundInterface.getEndpoint(it) }
            .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (foundIn == null || foundOut == null) return "ADB 接口缺少 USB 端点"

        val conn = manager.openDevice(device) ?: return "无法打开 USB 设备"
        if (!conn.claimInterface(foundInterface, true)) {
            conn.close()
            return "无法占用 ADB USB 接口"
        }
        connection = conn
        intf = foundInterface
        endpointIn = foundIn
        endpointOut = foundOut
        return handshake()
    }

    private fun handshake(): String? {
        sendPacket(CMD_CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
        val privateKey = AdbManager.usbPrivateKey(context) ?: return "没有 ADB 私钥"
        val certificate = AdbManager.usbCertificate(context) ?: return "没有 ADB 证书"
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val packet = readPacket(1_000) ?: continue
            when (packet.command) {
                CMD_AUTH -> {
                    when (packet.arg0) {
                        AUTH_TOKEN -> {
                            val signature = sign(packet.payload, privateKey) ?: return "ADB 签名失败"
                            sendPacket(CMD_AUTH, AUTH_SIGNATURE, 0, signature)
                        }
                        AUTH_RSAPUBLICKEY -> {
                            sendPacket(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(certificate))
                        }
                    }
                }
                CMD_CNXN -> return null
            }
        }
        return "USB ADB 握手超时"
    }

    fun runShell(command: String): String {
        val conn = connection ?: return "USB 未连接"
        val inEp = endpointIn ?: return "USB 未连接"
        val outEp = endpointOut ?: return "USB 未连接"
        localId = nextId.getAndIncrement()
        remoteId = 0
        sendPacket(CMD_OPEN, localId, 0, "shell:$command\u0000".toByteArray())
        val output = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val packet = readPacket(1_000) ?: continue
            when (packet.command) {
                CMD_OKAY -> {
                    remoteId = packet.arg0
                }
                CMD_WRTE -> {
                    if (packet.arg1 == localId) {
                        output.write(packet.payload)
                        sendPacket(CMD_OKAY, localId, remoteId, ByteArray(0))
                    }
                }
                CMD_CLSE -> {
                    if (packet.arg1 == localId || remoteId == 0) {
                        runCatching { sendPacket(CMD_CLSE, localId, remoteId, ByteArray(0)) }
                        return output.toString("UTF-8")
                    }
                }
            }
        }
        runCatching { sendPacket(CMD_CLSE, localId, remoteId, ByteArray(0)) }
        return output.toString("UTF-8").ifBlank { "命令超时或没有输出" }
    }

    fun close() {
        runCatching { intf?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null
        intf = null
        endpointIn = null
        endpointOut = null
    }

    private fun sign(token: ByteArray, privateKey: PrivateKey): ByteArray? = runCatching {
        Signature.getInstance("SHA1withRSA").run {
            initSign(privateKey)
            update(token)
            sign()
        }
    }.getOrNull()

    private fun publicKey(certificate: Certificate): ByteArray {
        val publicKey = certificate.publicKey as RSAPublicKey
        val out = ByteArrayOutputStream()
        fun writeInt(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }
        fun writeMpint(value: java.math.BigInteger) {
            var bytes = value.toByteArray()
            if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
            val needPad = bytes.isNotEmpty() && (bytes[0].toInt() and 0x80) != 0
            writeInt(bytes.size + if (needPad) 1 else 0)
            if (needPad) out.write(0)
            out.write(bytes)
        }
        val type = "ssh-rsa".toByteArray()
        writeInt(type.size)
        out.write(type)
        writeMpint(publicKey.publicExponent)
        writeMpint(publicKey.modulus)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "$encoded nanaya@android\u0000".toByteArray()
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val conn = connection ?: return
        val out = endpointOut ?: return
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum(payload))
        header.putInt(command xor -1)
        writeFully(conn, out, header.array())
        if (payload.isNotEmpty()) writeFully(conn, out, payload)
    }

    private fun readPacket(timeoutMs: Int): Packet? {
        val conn = connection ?: return null
        val input = endpointIn ?: return null
        val header = ByteArray(24)
        if (!readFully(conn, input, header, timeoutMs)) return null
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val length = buffer.int
        buffer.int // checksum
        buffer.int // magic
        if (length < 0 || length > 1_048_576) return null
        val payload = ByteArray(length)
        if (length > 0 && !readFully(conn, input, payload, timeoutMs)) return null
        return Packet(command, arg0, arg1, payload)
    }

    private fun writeFully(conn: UsbDeviceConnection, endpoint: UsbEndpoint, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val written = conn.bulkTransfer(endpoint, data, offset, data.size - offset, 2_000)
            if (written <= 0) throw IllegalStateException("USB 写入失败")
            offset += written
        }
    }

    private fun readFully(
        conn: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        data: ByteArray,
        timeoutMs: Int,
    ): Boolean {
        var offset = 0
        while (offset < data.size) {
            val read = conn.bulkTransfer(endpoint, data, offset, data.size - offset, timeoutMs)
            if (read <= 0) return false
            offset += read
        }
        return true
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xff
        return sum
    }

    private data class Packet(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    )

    companion object {
        private const val CMD_CNXN = 0x4e584e43
        private const val CMD_AUTH = 0x48545541
        private const val CMD_OPEN = 0x4e45504f
        private const val CMD_OKAY = 0x59414b4f
        private const val CMD_CLSE = 0x45534c43
        private const val CMD_WRTE = 0x45545257
        private const val VERSION = 0x01000000
        private const val MAX_DATA = 4096
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        fun isAdbDevice(device: UsbDevice): Boolean =
            (0 until device.interfaceCount).any {
                val intf = device.getInterface(it)
                intf.interfaceClass == 0xFF &&
                    intf.interfaceSubclass == 0x42 &&
                    intf.interfaceProtocol == 0x01
            }
    }
}
