package io.github.dryearth.Nanaya.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

/** Discovers the on-device ADB wireless pairing service over mDNS. */
object AdbDiscovery {
    private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    private const val CONNECT_SERVICE = "_adb-tls-connect._tcp"

    fun discoverPairing(
        context: Context,
        timeoutMs: Long = 30_000,
        callback: (host: String?, port: Int?) -> Unit,
    ) {
        discover(context, PAIRING_SERVICE, timeoutMs, callback)
    }

    fun discoverConnect(
        context: Context,
        timeoutMs: Long = 15_000,
        callback: (host: String?, port: Int?) -> Unit,
    ) {
        discover(context, CONNECT_SERVICE, timeoutMs, callback)
    }

    private fun discover(
        context: Context,
        type: String,
        timeoutMs: Long,
        callback: (String?, Int?) -> Unit,
    ) {
        val nsd = context.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            callback(null, null)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var discoveryListener: NsdManager.DiscoveryListener? = null

        fun finish(host: String?, port: Int?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            discoveryListener?.let { listener ->
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
            callback(host, port)
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                finish(null, null)
            }

            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                finish(serviceInfo.host?.hostAddress, serviceInfo.port)
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.startsWith(type)) {
                    runCatching { nsd.resolveService(serviceInfo, resolveListener) }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish(null, null)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        discoveryListener = listener
        handler.postDelayed({ finish(null, null) }, timeoutMs)
        runCatching {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { finish(null, null) }
    }
}
