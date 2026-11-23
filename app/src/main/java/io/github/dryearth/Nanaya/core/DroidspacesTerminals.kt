package io.github.dryearth.Nanaya.core

import android.content.Context
import io.github.dryearth.Nanaya.service.DroidspacesTerminalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A snapshot of the raw terminal byte stream. [revision] is the number of
 * characters ever emitted, so a re-attached xterm can replay only the part it
 * has not seen even after the front of [text] has been trimmed.
 */
data class TerminalFrame(
    val revision: Long,
    val text: String,
)

/**
 * Long-lived container terminal sessions. Leaving the terminal screen no longer
 * kills the shell; the card can show "回到终端" while the process is alive.
 */
object DroidspacesTerminals {
    private const val MAX_BUFFER = 240_000
    private const val INITIAL_COLS = 80
    private const val INITIAL_ROWS = 24

    private val sessions = mutableMapOf<String, Session>()
    private var appContext: Context? = null
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun get(name: String): Session? = sessions[name]?.takeIf { it.connected.value }

    fun getOrOpen(context: Context, name: String): Session {
        appContext = context.applicationContext
        sessions[name]?.takeIf { it.connected.value }?.let {
            DroidspacesTerminalService.start(appContext)
            return it
        }
        val session = Session(name)
        session.onStateChanged = { refreshActive() }
        sessions[name] = session
        session.start()
        refreshActive()
        return session
    }

    fun close(name: String) {
        sessions.remove(name)?.close()
        refreshActive()
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        refreshActive()
    }

    private fun refreshActive() {
        _active.value = sessions.filterValues { it.connected.value }.keys.toSet()
        if (_active.value.isEmpty()) {
            DroidspacesTerminalService.stop(appContext)
        } else {
            DroidspacesTerminalService.start(appContext)
        }
    }

    class Session internal constructor(val name: String) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var process: Process? = null
        @Volatile private var pty: PtyBridge.Channel? = null
        private var readerJob: Job? = null
        internal var onStateChanged: (() -> Unit)? = null

        private val buffer = StringBuilder()
        private var emitted = 0L
        private val _frame = MutableStateFlow(TerminalFrame(0, ""))
        val frame: StateFlow<TerminalFrame> = _frame.asStateFlow()

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val input = Channel<ByteArray>(Channel.UNLIMITED)
        private var writerJob: Job? = null
        private var pipeSized = false
        private var requestedCols = INITIAL_COLS
        private var requestedRows = INITIAL_ROWS

        internal fun fail(message: String) {
            append(message)
            _connected.value = false
        }

        private fun append(text: String) {
            buffer.append(text)
            if (buffer.length > MAX_BUFFER) {
                buffer.delete(0, buffer.length - MAX_BUFFER)
            }
            emitted += text.length
            _frame.value = TerminalFrame(emitted, buffer.toString())
        }

        private fun connected() {
            _connected.value = true
            onStateChanged?.invoke()
            // The UI may have reported its grid before droidspaces installed
            // its SIGWINCH handler; apply the last known size once more.
            scope.launch {
                kotlinx.coroutines.delay(900)
                pty?.resize(requestedCols, requestedRows)
            }
        }

        internal fun start() {
            writerJob = scope.launch(Dispatchers.IO) {
                for (bytes in input) {
                    val out = pty?.output ?: process?.outputStream ?: continue
                    runCatching {
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
            readerJob = scope.launch {
                // A host PTY gives droidspaces a real TTY and a real window
                // size. Only fall back to the pipe + stty path if the native
                // bridge is unavailable.
                val channel = withContext(Dispatchers.IO) {
                    DroidspacesRepository.openTerminalPty(name, INITIAL_COLS, INITIAL_ROWS)
                }
                val stream: java.io.InputStream
                if (channel != null) {
                    pty = channel
                    stream = channel.input
                    connected()
                } else {
                    val p = withContext(Dispatchers.IO) {
                        DroidspacesRepository.openTerminal(name)
                    }
                    if (p == null) {
                        fail("无法启动容器终端。\nDroidspaces 终端需要 Root。")
                        onStateChanged?.invoke()
                        return@launch
                    }
                    process = p
                    stream = p.inputStream
                    connected()
                }
                try {
                    withContext(Dispatchers.IO) {
                        val reader = stream.reader(Charsets.UTF_8)
                        val chars = CharArray(8192)
                        while (isActive) {
                            // The reader keeps partial UTF-8 sequences between
                            // reads, so split escape output is not corrupted.
                            val read = reader.read(chars)
                            if (read <= 0) break
                            val text = String(chars, 0, read)
                            if (text.isNotEmpty()) {
                                withContext(Dispatchers.Main) { append(text) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        append("\n[终端错误：${e.message ?: "读取失败"}]\n")
                    }
                } finally {
                    runCatching { pty?.close() }
                    runCatching { process?.destroy() }
                    pty = null
                    process = null
                    _connected.value = false
                    withContext(Dispatchers.Main) {
                        append("\n[会话结束]\n")
                    }
                    onStateChanged?.invoke()
                }
            }
        }

        fun write(text: String) {
            if (text.isEmpty() || !_connected.value) return
            input.trySend(text.toByteArray(Charsets.UTF_8))
        }

        private fun send(text: String) {
            if (text.isEmpty() || !_connected.value) return
            input.trySend(text.toByteArray(Charsets.UTF_8))
        }

        /**
         * With a PTY this is a single ioctl: the kernel sends SIGWINCH to the
         * foreground process and droidspaces forwards the new size to the
         * container PTY. The pipe fallback still needs an stty line.
         */
        fun applySize(cols: Int, rows: Int) {
            if (cols < 20 || rows < 5) return
            requestedCols = cols
            requestedRows = rows
            val channel = pty
            if (channel != null) {
                channel.resize(cols, rows)
                return
            }
            if (!_connected.value || pipeSized) return
            pipeSized = true
            send("stty rows $rows cols $cols 2>/dev/null\r")
        }

        /** Manual re-sync after keyboard or rotation. */
        fun forceSize(cols: Int, rows: Int) {
            if (cols < 20 || rows < 5) return
            requestedCols = cols
            requestedRows = rows
            val channel = pty
            if (channel != null) {
                channel.resize(cols, rows)
                return
            }
            if (!_connected.value) return
            send("stty rows $rows cols $cols 2>/dev/null\r")
        }

        fun close() {
            runCatching { pty?.close() }
            runCatching { process?.destroy() }
            pty = null
            process = null
            _connected.value = false
            readerJob?.cancel()
            readerJob = null
            writerJob?.cancel()
            writerJob = null
            input.close()
            scope.coroutineContext[Job]?.cancel()
            onStateChanged?.invoke()
        }
    }
}
