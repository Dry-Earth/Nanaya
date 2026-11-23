package io.github.dryearth.Nanaya.core

import android.content.Context
import io.github.dryearth.Nanaya.service.DroidspacesTerminalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Long-lived container terminal sessions. Leaving the terminal screen no longer
 * kills the shell; the card can show "回到终端" while the process is alive.
 */
object DroidspacesTerminals {
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

        private val _output = MutableStateFlow("")
        val output: StateFlow<String> = _output.asStateFlow()

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        internal fun fail(message: String) {
            _output.value = message
            _connected.value = false
        }

        internal fun start() {
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
                        val buffer = ByteArray(4096)
                        while (isActive) {
                            val read = p.inputStream.read(buffer)
                            if (read <= 0) break
                            val text = stripNoise(String(buffer, 0, read, Charsets.UTF_8))
                            if (text.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _output.value = (_output.value + text).takeLast(120_000)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _output.value += "\n[终端错误：${e.message ?: "读取失败"}]"
                    }
                } finally {
                    process = null
                    _connected.value = false
                    withContext(Dispatchers.Main) {
                        _output.value += "\n[会话结束]"
                    }
                    onStateChanged?.invoke()
                }
            }
        }

        fun write(text: String) {
            val p = process ?: return
            scope.launch(Dispatchers.IO) {
                runCatching {
                    p.outputStream.write(text.toByteArray(Charsets.UTF_8))
                    p.outputStream.flush()
                }
            }
        }

        fun close() {
            runCatching { process?.destroy() }
            process = null
            _connected.value = false
            readerJob?.cancel()
            readerJob = null
            scope.coroutineContext[Job]?.cancel()
            onStateChanged?.invoke()
        }
    }

    private val ansiCsi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val ansiOsc = Regex("\u001B\\][^\u0007]*(\u0007|\u001B\\\\)")

    private fun stripNoise(value: String): String =
        value.replace(ansiOsc, "").replace(ansiCsi, "").replace("\r", "")
}
