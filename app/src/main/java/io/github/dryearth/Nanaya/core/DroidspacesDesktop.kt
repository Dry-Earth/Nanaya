package io.github.dryearth.Nanaya.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Opens the desktop app that a Droidspaces container booted with. */
object DroidspacesDesktop {
    fun available(container: DsContainer): Boolean =
        container.running && (container.anlandSock.isNotBlank() || container.x11)

    fun open(context: Context, container: DsContainer): String? {
        if (container.anlandSock.isNotBlank()) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(
                    "com.anland.consumer",
                    "com.anland.consumer.SecondaryActivity",
                )
                putExtra("socket_path", container.anlandSock)
                putExtra("window_name", container.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            return runCatching {
                context.startActivity(intent)
                null
            }.getOrElse { "打开 Anland 失败，可能没有安装 Anland" }
        }
        if (container.x11) {
            val intent = context.packageManager.getLaunchIntentForPackage("com.termux.x11")
                ?: return "没有安装 Termux:X11"
            return runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                null
            }.getOrElse { "打开 Termux:X11 失败" }
        }
        return "这个容器没有开启桌面"
    }
}
