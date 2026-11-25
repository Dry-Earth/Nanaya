package io.github.dryearth.Nanaya.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import io.github.dryearth.Nanaya.service.DroidspacesTerminalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Long-lived container terminals backed by Termux's terminal-emulator
 * (Apache 2.0). TerminalSession owns the PTY, the emulator state and the
 * scrollback, so re-attaching a view does not replay raw bytes.
 */
object DroidspacesTerminals {
    private val sessions = mutableMapOf<String, Session>()
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun get(name: String): Session? = sessions[name]?.takeIf { it.alive.value }

    fun getOrOpen(
        context: Context,
        name: String,
        pid: Int = 0,
        rootfs: String? = null,
    ): Session {
        appContext = context.applicationContext
        sessions[name]?.takeIf { it.alive.value }?.let {
            DroidspacesTerminalService.start(appContext)
            return it
        }
        val session = Session(name, pid, rootfs)
        session.onFinished = {
            if (sessions[name] === session) sessions.remove(name)
            refreshActive()
        }
        sessions[name] = session
        DroidspacesTerminalService.start(appContext)
        refreshActive()
        return session
    }

    fun close(name: String) {
        sessions.remove(name)?.close()
        refreshActive()
    }

    fun closeAll() {
        val current = sessions.values.toList()
        sessions.clear()
        current.forEach { it.close() }
        refreshActive()
    }

    private fun refreshActive() {
        _active.value = sessions.filterValues { it.alive.value }.keys.toSet()
        if (_active.value.isEmpty()) {
            DroidspacesTerminalService.stop(appContext)
        } else {
            DroidspacesTerminalService.start(appContext)
        }
    }

    internal fun copyText(text: String) {
        val context = appContext ?: return
        mainHandler.post {
            val manager = context.getSystemService(ClipboardManager::class.java)
            manager?.setPrimaryClip(ClipData.newPlainText("终端", text))
            Toast.makeText(context, "已复制 ${text.length} 字", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun pasteText(): String? {
        val context = appContext ?: return null
        return runCatching {
            val manager = context.getSystemService(ClipboardManager::class.java)
            val clip = manager?.primaryClip ?: return@runCatching null
            if (clip.itemCount == 0) return@runCatching null
            clip.getItemAt(0).coerceToText(context)?.toString()
        }.getOrNull()
    }

    class Session internal constructor(
        val name: String,
        pid: Int,
        rootfs: String?,
    ) {
        private val terminalCommand = DroidspacesRepository.terminalCommand(name, pid, rootfs)
        private val needsScriptSize = terminalCommand?.direct == false

        private val _alive = MutableStateFlow(true)
        val alive: StateFlow<Boolean> = _alive.asStateFlow()

        internal var onFinished: (() -> Unit)? = null
        @Volatile var shellPid: Int = 0
            private set
        @Volatile var exitCode: Int? = null
            private set

        private val client = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) = Unit

            override fun onTitleChanged(changedSession: TerminalSession) = Unit

            override fun onSessionFinished(finishedSession: TerminalSession) {
                _alive.value = false
                onFinished?.invoke()
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                DroidspacesTerminals.copyText(text)
            }

            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                val text = DroidspacesTerminals.pasteText() ?: return
                write(text)
            }

            override fun onBell(session: TerminalSession) = Unit

            override fun onColorsChanged(session: TerminalSession) = Unit

            override fun onTerminalCursorStateChange(state: Boolean) = Unit

            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
                shellPid = pid
            }

            override fun getTerminalCursorStyle(): Int? = null

            override fun logError(tag: String, message: String) {
                Log.e(tag, message)
            }

            override fun logWarn(tag: String, message: String) {
                Log.w(tag, message)
            }

            override fun logInfo(tag: String, message: String) {
                Log.i(tag, message)
            }

            override fun logDebug(tag: String, message: String) {
                Log.d(tag, message)
            }

            override fun logVerbose(tag: String, message: String) {
                Log.v(tag, message)
            }

            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                Log.e(tag, message, e)
            }

            override fun logStackTrace(tag: String, e: Exception) {
                Log.e(tag, "terminal", e)
            }
        }

        private val handler = Handler(Looper.getMainLooper())
        private var scriptSized = false

        /**
         * script creates the container PTY before the view knows its grid, so
         * pin the inner PTY to the real size once the emulator exists.
         */
        fun syncContainerTty() {
            if (!needsScriptSize) return
            if (scriptSized) return
            val emulator = terminalSession.emulator ?: return
            val rows = emulator.mRows
            val cols = emulator.mColumns
            if (rows < 5 || cols < 20) return
            scriptSized = true
            handler.postDelayed({
                write("stty rows $rows cols $cols 2>/dev/null\r")
            }, 700)
        }

        val terminalSession: TerminalSession = TerminalSession(
            "su",
            "/",
            arrayOf("su", "-c", terminalCommand?.command ?: "exit 1"),
            DroidspacesTerminals.buildEnvironment(),
            5000,
            client,
        ).apply {
            mSessionName = name
        }

        fun write(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) return
            terminalSession.write(bytes, 0, bytes.size)
        }

        fun close() {
            _alive.value = false
            exitCode = runCatching { terminalSession.exitStatus }.getOrNull()
            runCatching { terminalSession.finishIfRunning() }
            onFinished?.invoke()
        }
    }

    internal fun buildEnvironment(): Array<String> {
        val appPath = System.getenv("PATH").orEmpty()
        val path = buildList {
            addAll(appPath.split(':').filter { it.isNotBlank() })
            addAll(
                listOf(
                    "/system/bin",
                    "/system/xbin",
                    "/system/sbin",
                    "/vendor/bin",
                    "/product/bin",
                    "/sbin",
                ),
            )
        }.distinct().joinToString(":")

        return buildList {
            add("HOME=/")
            add("PATH=$path")
            add("TERM=xterm-256color")
            add("DS_NO_PROXY=1")
            add("ANDROID_ROOT=/system")
            add("ANDROID_DATA=/data")
            add("LANG=C.UTF-8")
            System.getenv("LD_LIBRARY_PATH")
                ?.takeIf { it.isNotBlank() }
                ?.let { add("LD_LIBRARY_PATH=$it") }
        }.toTypedArray()
    }
}
