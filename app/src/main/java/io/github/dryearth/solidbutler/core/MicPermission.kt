package io.github.dryearth.solidbutler.core

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat

/**
 * Microphone permission plus the app-op check.
 *
 * Some ROMs and privacy tools keep the runtime permission granted while
 * silently ignoring recording through the app-op. Checking both avoids
 * asking for a permission that already looks granted, and avoids pretending
 * recording works when the OS is actually blocking it.
 */
object MicPermission {
    fun granted(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return true
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    Process.myUid(),
                    context.packageName,
                )
            }
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        return mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
    }
}
