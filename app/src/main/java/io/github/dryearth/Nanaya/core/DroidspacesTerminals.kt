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

        /**
         * The guest PTY starts at 0x0 because the host side is a pipe.
         * Sizes are re-applied only until the user starts typing, after which
         * injecting stty lines would land inside top/htop.
         */
        private var userTyped = false
        private var appliedCols = 0
        private var appliedRows = 0

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

        internal fun start() {
            writerJob = scope.launch(Dispatchers.IO) {
                for (bytes in input) {
                    val p = process ?: continue
                    runCatching {
                        p.outputStream.write(bytes)
                        p.outputStream.flush()
                    }
                }
            }
            readerJob = scope.launch {
                val p = withContext(Dispatchers.IO) {
                    DroidspacesRepository.openTerminal(name)
                }
                if (p == null) {
                    fail("无法启动容器终端。\nDroidspaces 终端需要 Root。")
                    onStateChanged?.invoke()
                    return@launch
                }
                process = p
                _connected.value = true
                onStateChanged?.invoke()
                try {
                    withContext(Dispatchers.IO) {
                        val reader = p.inputStream.reader(Charsets.UTF_8)
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
            userTyped = true
            send(text)
        }

        private fun send(text: String) {
            if (text.isEmpty() || !_connected.value) return
            input.trySend(text.toByteArray(Charsets.UTF_8))
        }

        /**
         * The container's PTY inherits its size from the host stdin, which is a
         * pipe here, so it comes up as 0x0 and full-screen tools (top, htop,
         * vi) draw nothing. Set the size once per shell session and clear the
         * echoed setup line.
         */
        fun applySize(cols: Int, rows: Int) {
            // xterm's fit addon can report a 1x1 grid before the WebView has
            // been laid out; never let that become the guest PTY size.
            if (cols < 20 || rows < 5 || !_connected.value) return
            if (userTyped) return
            if (cols == appliedCols && rows == appliedRows) return
            appliedCols = cols
            appliedRows = rows
            send(
                "unset COLUMNS LINES 2>/dev/null; export TERM=xterm-256color; " +
                    "stty rows $rows cols $cols 2>/dev/null; " +
                    "printf '\\033[2J\\033[H'\r",
            )
        }

        /**
         * Manual re-sync after keyboard or rotation. Run it at the shell
         * prompt: while a full-screen app is active its input is this line.
         */
        fun forceSize(cols: Int, rows: Int) {
            if (cols < 20 || rows < 5 || !_connected.value) return
            appliedCols = cols
            appliedRows = rows
            send("unset COLUMNS LINES 2>/dev/null; stty rows $rows cols $cols 2>/dev/null\r")
        }

        fun close() {
            runCatching { process?.destroy() }
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
