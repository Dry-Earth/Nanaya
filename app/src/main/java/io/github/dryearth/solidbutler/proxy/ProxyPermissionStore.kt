package io.github.dryearth.solidbutler.proxy

import android.content.Context

data class ProxyGrant(
    val uid: Int,
    val packageName: String,
    val permission: String,
    val grantedAt: Long,
    val lastUsedAt: Long = grantedAt,
)

/**
 * Per-UID grants for the Nanaya privileged proxy. Secrets and command output
 * are deliberately not stored here.
 */
class ProxyPermissionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("nanaya_proxy_permissions", Context.MODE_PRIVATE)

    fun grant(uid: Int, packageName: String, permission: String = SHIZUKU_PERMISSION) {
        prefs.edit()
            .putBoolean(key(uid, permission), true)
            .putString("pkg:$uid", packageName)
            .putLong("at:$uid:$permission", System.currentTimeMillis())
            .apply()
    }

    fun revoke(uid: Int, permission: String = SHIZUKU_PERMISSION) {
        prefs.edit()
            .remove(key(uid, permission))
            .remove("at:$uid:$permission")
            .apply()
    }

    fun isGranted(uid: Int, permission: String = SHIZUKU_PERMISSION): Boolean =
        prefs.getBoolean(key(uid, permission), false)

    fun touch(uid: Int, permission: String = SHIZUKU_PERMISSION) {
        prefs.edit().putLong("last:$uid:$permission", System.currentTimeMillis()).apply()
    }

    fun grants(): List<ProxyGrant> = prefs.all.keys
        .filter { it.startsWith("grant:") }
        .mapNotNull { key ->
            val parts = key.split(':')
            if (parts.size != 3) return@mapNotNull null
            val uid = parts[1].toIntOrNull() ?: return@mapNotNull null
            val permission = parts[2]
            ProxyGrant(
                uid = uid,
                packageName = prefs.getString("pkg:$uid", "") ?: "",
                permission = permission,
                grantedAt = prefs.getLong("at:$uid:$permission", 0L),
                lastUsedAt = prefs.getLong("last:$uid:$permission", 0L),
            )
        }
        .sortedBy { it.uid }

    private fun key(uid: Int, permission: String) = "grant:$uid:$permission"

    companion object {
        const val SHIZUKU_PERMISSION = "shizuku"
    }
}
