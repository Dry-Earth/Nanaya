package io.github.dryearth.Nanaya.core

import android.os.ParcelFileDescriptor
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * A real host-side PTY for the container terminal.
 *
 * The child is made a session leader with the PTY slave as its controlling
 * terminal and then execs `su -c "<command>"`. Because the terminal now has a
 * real window size, `droidspaces enter` no longer starts at 0x0 and resizing
 * the master with TIOCSWINSZ reaches the container through SIGWINCH.
 */
object PtyBridge {
    @Volatile var lastError: String = ""
        private set

    private val loaded: Boolean = runCatching { System.loadLibrary("ptybridge") }
        .onFailure { lastError = "libptybridge 加载失败：${it.message ?: it.javaClass.simpleName}" }
        .isSuccess
    val available: Boolean get() = loaded

    private external fun nativeOpen(
        suPath: String,
        command: String,
        cols: Int,
        rows: Int,
    ): IntArray?

    private external fun nativeResize(fd: Int, cols: Int, rows: Int): Boolean
    private external fun nativeWait(pid: Int): Int
    private external fun nativeSignal(pid: Int, signal: Int): Int

    fun open(suPath: String, command: String, cols: Int, rows: Int): Channel? {
        lastError = ""
        if (!loaded) {
            lastError = "libptybridge 未加载"
            return null
        }
        val values = try {
            nativeOpen(suPath, command, cols, rows)
        } catch (e: Throwable) {
            lastError = "nativeOpen 异常：${e.message ?: e.javaClass.simpleName}"
            return null
        }
        if (values == null) {
            lastError = "nativeOpen 返回空（posix_openpt/fork 失败）"
            return null
        }
        if (values.size < 2 || values[0] < 0 || values[1] <= 0) {
            lastError = "nativeOpen 返回值无效"
            return null
        }
        val channel = try {
            Channel(values[0], values[1])
        } catch (e: Throwable) {
            lastError = "PTY 通道初始化失败：${e.message ?: e.javaClass.simpleName}"
            runCatching { ParcelFileDescriptor.adoptFd(values[0]).close() }
            runCatching { nativeSignal(values[1], 9) }
            null
        }
        return channel
    }

    class Channel internal constructor(fd: Int, val pid: Int) {
        private val readPfd = ParcelFileDescriptor.adoptFd(fd)
        private val writePfd = ParcelFileDescriptor.dup(readPfd.fileDescriptor)
        val input = FileInputStream(readPfd.fileDescriptor)
        val output = FileOutputStream(writePfd.fileDescriptor)

        @Volatile private var alive = true

        init {
            Thread {
                runCatching { nativeWait(pid) }
                alive = false
            }.apply {
                isDaemon = true
                name = "pty-reaper-$pid"
            }.start()
        }

        fun resize(cols: Int, rows: Int) {
            if (cols <= 0 || rows <= 0) return
            runCatching { nativeResize(readPfd.fd, cols, rows) }
            // The kernel only signals the tty's foreground group. If su moved
            // the shell to another process group, nudge -pid as well.
            if (alive) runCatching { nativeSignal(-pid, OsConstants.SIGWINCH) }
        }

        fun close() {
            if (alive) runCatching { nativeSignal(pid, 1) } // SIGHUP
            runCatching { input.close() }
            runCatching { output.close() }
            runCatching { writePfd.close() }
            runCatching { readPfd.close() }
            if (alive) runCatching { nativeSignal(pid, 15) } // SIGTERM
        }
    }
}
