package io.github.dryearth.Nanaya.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.coroutines.resume

data class AdbScanResult(
    val host: String,
    val port: Int,
    val name: String,
    val kind: Kind,
) {
    enum class Kind { WIRELESS, PAIRING, LAN }
}

/**
 * Finds ADB endpoints on the current LAN.
 *
 * Two complementary sources:
 * - mDNS (`_adb-tls-connect._tcp` / `_adb-tls-pairing._tcp`) for Android 11+
 *   wireless debugging, which uses a random port.
 * - A /24 sweep of TCP 5555 for TVs, head units and older boxes that run
 *   classic `adbd` on the fixed port.
 */
object LanAdbScanner {

    suspend fun scan(context: Context): List<AdbScanResult> = coroutineScope {
        val mdns = async { runCatching { scanMdns(context) }.getOrDefault(emptyList()) }
        val lan = async { runCatching { scanSubnet() }.getOrDefault(emptyList()) }
        // mDNS first so its friendly service name wins the de-duplication.
        (mdns.await() + lan.await())
            .distinctBy { it.host to it.port }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.host }, { it.port }))
    }

    private suspend fun scanMdns(context: Context): List<AdbScanResult> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val results = mutableListOf<AdbScanResult>()
        val resultLock = Mutex()
        val resolveLock = Mutex()

        suspend fun collect(type: String, kind: AdbScanResult.Kind) {
            runCatching {
                withTimeoutOrNull(4_500) {
                    mdnsServices(nsd, type).collect { info ->
                        val resolved = resolveLock.withLock {
                            withTimeoutOrNull(1_500) { resolve(nsd, info) }
                        } ?: return@collect
                        val address = resolved.host?.hostAddress ?: return@collect
                        if (resolved.port <= 0) return@collect
                        resultLock.withLock {
                            if (results.none { it.host == address && it.port == resolved.port }) {
                                results += AdbScanResult(
                                    host = address,
                                    port = resolved.port,
                                    name = info.serviceName ?: "",
                                    kind = kind,
                                )
                            }
                        }
                    }
                }
            }
        }

        coroutineScope {
            listOf(
                launch { collect("_adb-tls-connect._tcp", AdbScanResult.Kind.WIRELESS) },
                launch { collect("_adb-tls-pairing._tcp", AdbScanResult.Kind.PAIRING) },
            ).joinAll()
        }
        return results
    }

    private fun mdnsServices(nsd: NsdManager, type: String) = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
        }
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            close()
        }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(serviceInfo)
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private suspend fun scanSubnet(): List<AdbScanResult> = coroutineScope {
        val local = localIpv4() ?: return@coroutineScope emptyList()
        val parts = local.split('.')
        if (parts.size != 4) return@coroutineScope emptyList()
        val self = parts[3].toIntOrNull() ?: return@coroutineScope emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        val gate = Semaphore(48)
        val found = Channel<AdbScanResult>(Channel.UNLIMITED)
        (1..254)
            .filter { it != self }
            .map { last ->
                launch(Dispatchers.IO) {
                    val host = "$prefix.$last"
                    gate.withPermit {
                        if (probe(host, 5555, 320)) {
                            found.send(
                                AdbScanResult(
                                    host = host,
                                    port = 5555,
                                    name = "",
                                    kind = AdbScanResult.Kind.LAN,
                                ),
                            )
                        }
                    }
                }
            }
            .joinAll()
        found.close()
        found.toList()
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("eth") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { !it.startsWith("127.") && !it.startsWith("169.254.") }
    }.getOrNull()
}
