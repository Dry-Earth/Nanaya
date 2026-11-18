package io.github.dryearth.solidbutler.service

import android.app.NotificationChannel
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.dryearth.solidbutler.core.Prefs

/**
 * Channel-level notification filter.
 *
 * Android does not let a normal app change another app's channels, so the
 * listener cancels notifications from channels the user has muted. This is
 * honest suppression, not a fake "channel off" switch.
 */
class NotificationGuardService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val channel = sbn.notification.channelId ?: return
        if (Prefs(this).blockedChannels().contains("${sbn.packageName}|$channel")) {
            runCatching { cancelNotification(sbn.key) }
        }
    }

    fun channelsFor(pkg: String): List<NotificationChannel> = runCatching {
        getNotificationChannels(pkg, Process.myUserHandle())
    }.getOrDefault(emptyList())

    fun active(): List<StatusBarNotification> = runCatching {
        activeNotifications?.toList() ?: emptyList()
    }.getOrDefault(emptyList())

    fun cancelKey(key: String) {
        runCatching { cancelNotification(key) }
    }

    companion object {
        @Volatile
        var instance: NotificationGuardService? = null
            private set
    }
}
