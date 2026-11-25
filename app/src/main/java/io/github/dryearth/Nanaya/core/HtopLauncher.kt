package io.github.dryearth.Nanaya.core

import android.content.Context
import io.github.dryearth.Nanaya.TerminalActivity
import java.io.File

/**
 * Launches the statically linked aarch64 htop bundled in the APK.
 *
 * htop must run on the Android host, not inside a Droidspaces container: the
 * container has its own PID namespace and cannot see Android processes. The
 * binary is copied to /data/local/tmp and executed through `su` so that it can
 * read every /proc entry.
 */
object HtopLauncher {
    private const val ASSET = "htop/htop-aarch64"
    private const val REMOTE = "/data/local/tmp/nanaya-htop"

    /** Returns an error message, or null when the terminal was launched. */
    fun launch(context: Context): String? {
        if (!Shell.rootReady) {
            return "htop 需要 Root：请先授予 Nanaya Root 权限"
        }
        val binary = extract(context) ?: return "无法解压 htop 二进制"
        val temp = "$REMOTE.new"
        val command = buildString {
            append("cp ").append(quote(binary.absolutePath)).append(' ').append(temp)
            append(" || exit 1; ")
            append("chmod 755 ").append(temp).append(" || exit 1; ")
            append("mv ").append(temp).append(' ').append(REMOTE).append(" || exit 1; ")
            append("export TERM=xterm-256color; ")
            append("export HOME=/data/local/tmp; ")
            append("exec ").append(REMOTE)
        }
        return runCatching {
            context.startActivity(TerminalActivity.hostIntent(context, "htop · Android", command))
            null
        }.getOrElse { it.message ?: "无法打开终端" }
    }

    private fun extract(context: Context): File? = runCatching {
        val binary = File(context.filesDir, "htop-aarch64")
        // Re-extract if missing or obviously truncated.
        if (!binary.exists() || binary.length() < 100_000L) {
            context.assets.open(ASSET).use { input ->
                binary.outputStream().use { output -> input.copyTo(output) }
            }
            binary.setReadable(true, false)
            binary.setExecutable(true, false)
        }
        binary
    }.getOrNull()

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
