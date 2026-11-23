package io.github.dryearth.Nanaya.core

import android.os.ParcelFileDescriptor
import android.system.Os
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
    private val loaded: Boolean = runCatching { System.loadLibrary("ptybridge") }.isSuccess
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
        if (!loaded) return null
        val values = runCatching { nativeOpen(suPath, command, cols, rows) }.getOrNull()
            ?: return null
        if (values.size < 2 || values[0] < 0 || values[1] <= 0) return null
        val channel = runCatching { Channel(values[0], values[1]) }.getOrNull()
        if (channel == null) {
            runCatching { ParcelFileDescriptor.adoptFd(values[0]).close() }
            runCatching { nativeSignal(values[1], 9) }
        }
        return channel
    }

    class Channel internal constructor(fd: Int, val pid: Int) {
        private val readPfd = ParcelFileDescriptor.adoptFd(fd)
        private val writePfd = ParcelFileDescriptor.adoptFd(Os.dup(readPfd.fileDescriptor))
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
