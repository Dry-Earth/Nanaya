package io.github.dryearth.solidbutler.core

import android.app.Notification
import kotlinx.coroutines.flow.MutableStateFlow

data class AdbPairingOffer(
    val host: String? = null,
    val port: Int? = null,
    val code: String? = null,
)

/**
 * In-memory bridge for the Wireless debugging pairing notification. The code is
 * never persisted and is cleared when pairing starts or finishes.
 */
object AdbPairingBus {
    val offer = MutableStateFlow<AdbPairingOffer?>(null)

    fun clear() {
        offer.value = null
    }

    fun publish(text: String) {
        if (text.isBlank()) return
        val lower = text.lowercase()
        val looksLikePairing = lower.contains("配对") ||
            lower.contains("pairing") ||
            lower.contains("wireless debugging") ||
            lower.contains("无线调试")
        if (!looksLikePairing) return

        val code = Regex("(?<!\\d)(\\d{6})(?!\\d)")
            .findAll(text)
            .map { it.groupValues[1] }
            .lastOrNull()
        val endpoint = Regex("((?:\\d{1,3}\\.){3}\\d{1,3}):(\\d{2,5})").find(text)
        val host = endpoint?.groupValues?.get(1)
        val port = endpoint?.groupValues?.get(2)?.toIntOrNull()
        if (code == null && host == null) return

        val current = offer.value
        offer.value = AdbPairingOffer(
            host = host ?: current?.host,
            port = port ?: current?.port,
            code = code ?: current?.code,
        )
    }

    fun textOf(notification: Notification): String {
        val extras = notification.extras ?: return ""
        return buildList {
            add(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString())
        }.filterNotNull().joinToString(" ")
    }
}
