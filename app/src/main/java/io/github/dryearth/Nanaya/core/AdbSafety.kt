package io.github.dryearth.Nanaya.core

/** Shared classification for commands that need a second confirmation. */
object AdbSafety {
    private val patterns = listOf(
        "rm ", "rm -", "dd ", "mkfs", "reboot", "shutdown",
        "pm uninstall", "pm clear", "pm disable", "pm enable",
        "settings put", "settings delete", "appops set",
        "mount ", "chmod ", "chown ", "flash ", "fastboot",
        "wipe", "setprop", "insmod", "rmmod", "iptables", "nft ",
        "killall", "pkill",
    )

    fun isDangerous(command: String): Boolean {
        val lower = command.lowercase()
        return patterns.any { lower.contains(it) }
    }
}
