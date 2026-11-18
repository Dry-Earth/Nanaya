package io.github.dryearth.solidbutler.service

import io.github.dryearth.solidbutler.IShizukuShell

/**
 * Runs inside Shizuku/Sui's shell process. This is the official UserService
 * path; it avoids the private Shizuku.newProcess API.
 */
class ShizukuShellService : IShizukuShell.Stub() {
    override fun run(command: String): String {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        }.getOrElse { "ERROR: ${it.message}" }
    }
}
