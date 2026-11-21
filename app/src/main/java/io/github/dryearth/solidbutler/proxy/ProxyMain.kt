package io.github.dryearth.solidbutler.proxy

import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * Entry point for the shell-UID proxy process.
 *
 * This first stage only proves that Nanaya can start and keep its own process
 * under shell/root. Binder distribution and Shizuku compatibility are added in
 * later stages.
 */
fun main(args: Array<String>) {
    Looper.prepareMainLooper()
    Log.i("NanayaProxy", "proxy process started uid=${Process.myUid()} args=${args.joinToString(" ")}")
    Looper.loop()
}
