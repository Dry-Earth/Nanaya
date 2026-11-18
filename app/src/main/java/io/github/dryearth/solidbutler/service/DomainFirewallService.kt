package io.github.dryearth.solidbutler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.github.dryearth.solidbutler.MainActivity
import io.github.dryearth.solidbutler.core.Prefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Experimental DNS-level domain filter.
 *
 * Only the DNS server address is routed into the tunnel; ordinary traffic
 * keeps using the normal network. Queries for blocked domains get NXDOMAIN.
 * DNS-over-HTTPS / Private DNS and direct-IP connections bypass this, which
 * the UI states plainly.
 */
class DomainFirewallService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var active = false
    private lateinit var prefs: Prefs
    private var packetId = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = Prefs(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        if (tun != null) return START_STICKY
        val builder = Builder()
            .setSession("汐 · 域名过滤")
            .addAddress("10.111.0.2", 32)
            .addDnsServer("10.111.0.1")
            .addRoute("10.111.0.1", 32)
            .setBlocking(true)
        tun = runCatching { builder.establish() }.getOrNull()
        if (tun == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        running = true
        worker = Thread { loop() }.also { it.start() }
        return START_STICKY
    }

    override fun onRevoke() {
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        running = false
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun loop() {
        val fd = tun?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteArray(32767)
        while (active) {
            val len = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (len <= 0) continue
            if ((buffer[0].toInt() and 0xf0) != 0x40) continue
            val ihl = (buffer[0].toInt() and 0x0f) * 4
            if (ihl < 20 || len < ihl + 8) continue
            if ((buffer[9].toInt() and 0xff) != 17) continue
            val dst = "${buffer[16].toInt() and 0xff}.${buffer[17].toInt() and 0xff}.${buffer[18].toInt() and 0xff}.${buffer[19].toInt() and 0xff}"
            if (dst != "10.111.0.1") continue
            val dstPort = u16(buffer, ihl + 2)
            if (dstPort != 53) continue
            val clientPort = u16(buffer, ihl)
            val query = buffer.copyOfRange(ihl + 8, len)
            if (query.size < 13) continue
            if ((query[2].toInt() and 0x80) != 0) continue
            val domain = dnsName(query)
            val response = if (domain != null && isBlocked(domain)) {
                blockedResponse(query)
            } else {
                forward(query)
            } ?: continue
            writeUdp(output, buffer, clientPort, response)
        }
        runCatching { input.close() }
        runCatching { output.close() }
    }

    private fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.')
        return prefs.blockedDomains().any { it.isNotBlank() && (d == it || d.endsWith(".$it")) }
    }

    private fun dnsName(packet: ByteArray): String? {
        val sb = StringBuilder()
        var i = 12
        var guard = 0
        while (i < packet.size && guard < 64) {
            val n = packet[i].toInt() and 0xff
            if (n == 0) return sb.toString().lowercase().ifBlank { null }
            if (n and 0xc0 == 0xc0 || n > 63) return null
            i++
            if (i + n > packet.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until n) sb.append((packet[i + j].toInt() and 0xff).toChar())
            i += n
            guard++
        }
        return null
    }

    private fun blockedResponse(query: ByteArray): ByteArray {
        var i = 12
        while (i < query.size && (query[i].toInt() and 0xff) != 0) {
            i += (query[i].toInt() and 0xff) + 1
        }
        val end = (i + 5).coerceAtMost(query.size)
        val question = query.copyOfRange(12, end)
        val out = ByteArray(12 + question.size)
        out[0] = query[0]
        out[1] = query[1]
        out[2] = 0x81.toByte() // QR + RD
        out[3] = 0x83.toByte() // RA + NXDOMAIN
        out[5] = 1 // QDCOUNT = 1
        System.arraycopy(question, 0, out, 12, question.size)
        return out
    }

    private fun forward(query: ByteArray): ByteArray? = runCatching {
        val socket = DatagramSocket()
        protect(socket)
        socket.soTimeout = 2500
        socket.send(DatagramPacket(query, query.size, InetAddress.getByName("223.5.5.5"), 53))
        val buf = ByteArray(4096)
        val resp = DatagramPacket(buf, buf.size)
        socket.receive(resp)
        socket.close()
        buf.copyOfRange(0, resp.length)
    }.getOrNull()

    private fun writeUdp(output: FileOutputStream, original: ByteArray, clientPort: Int, payload: ByteArray) {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = ((total shr 8) and 0xff).toByte()
        p[3] = (total and 0xff).toByte()
        val id = packetId++
        p[4] = ((id shr 8) and 0xff).toByte()
        p[5] = (id and 0xff).toByte()
        p[6] = 0x40
        p[8] = 64
        p[9] = 17
        // source = original destination (10.111.0.1)
        System.arraycopy(original, 16, p, 12, 4)
        // destination = original source
        System.arraycopy(original, 12, p, 16, 4)
        val csum = checksum(p, 0, 20)
        p[10] = ((csum shr 8) and 0xff).toByte()
        p[11] = (csum and 0xff).toByte()
        // UDP header
        p[20] = 0
        p[21] = 53
        p[22] = ((clientPort shr 8) and 0xff).toByte()
        p[23] = (clientPort and 0xff).toByte()
        val ulen = 8 + payload.size
        p[24] = ((ulen shr 8) and 0xff).toByte()
        p[25] = (ulen and 0xff).toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        runCatching {
            output.write(p)
            output.flush()
        }
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += ((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv() and 0xffff
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "域名过滤", NotificationManager.IMPORTANCE_LOW).apply {
                description = "只接管 DNS 查询，用于拦截指定域名"
            }
        )
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("汐 · 域名过滤")
            .setContentText("仅接管 DNS，拦截 ${prefs.blockedDomains().size} 个域名")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "xi_domain_firewall"
        private const val NOTIFICATION_ID = 12002

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DomainFirewallService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DomainFirewallService::class.java))
        }
    }
}
