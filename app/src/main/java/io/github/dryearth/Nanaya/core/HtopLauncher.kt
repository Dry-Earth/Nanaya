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

    /** Returns an error message, or null when the terminal was launched. */
    fun launch(context: Context): String? {
        if (!Shell.rootReady) {
            return "htop 需要 Root：请先授予 Nanaya Root 权限"
        }
        val binary = extract(context) ?: return "无法解压 htop 二进制"
        val source = quote(binary.absolutePath)
        val command = buildString {
            append("export TERM=xterm-256color; export HOME=/data/local/tmp; ")
            append("for d in /data/local/tmp /data/adb; do ")
            append("[ -d \"\$d\" ] || continue; ")
            append("cp ").append(source).append(" \"\$d/nanaya-htop.new\" 2>/dev/null || continue; ")
            append("chmod 755 \"\$d/nanaya-htop.new\" 2>/dev/null || continue; ")
            append("mv -f \"\$d/nanaya-htop.new\" \"\$d/nanaya-htop\" 2>/dev/null || continue; ")
            append("\"\$d/nanaya-htop\"; code=\$?; ")
            append("[ \$code -ne 126 ] && [ \$code -ne 127 ] && exit \$code; ")
            append("done; echo '无法执行 htop（SELinux 拒绝执行）'; exit 1")
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
