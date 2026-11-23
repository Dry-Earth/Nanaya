package io.github.dryearth.Nanaya.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DsContainer(
    val name: String,
    val folder: String,
    val hostname: String,
    val rootfs: String,
    val netMode: String,
    val running: Boolean,
    val pid: Int,
    val os: String,
    val ip: String,
    val uptime: String,
    val cpuPermill: Long,
    val ramUsedKb: Long,
    val ramLimitKb: Long,
    val cpuLimitPermill: Long,
    val hwAccess: Boolean,
    val gpu: Boolean,
    val x11: Boolean,
    val anland: Boolean,
    val anlandSock: String,
    val memoryLimit: Long,
    val cpus: String,
    val pidsLimit: Long,
    val disk: String,
)

data class DsProcess(
    val pid: String,
    val ppid: String,
    val user: String,
    val name: String,
    val args: String,
)

data class DsService(
    val name: String,
    val state: String,
)

data class DsApp(
    val name: String,
    val exec: String,
    val desktopId: String,
    val systemApp: Boolean = false,
)

data class DsSnapshot(
    val containers: List<DsContainer>,
    val hostRamKb: Long,
    val binaryFound: Boolean,
    val error: String? = null,
)

/**
 * Read/write adapter for the Droidspaces CLI. Droidspaces is a separate GPL
 * runtime; Nanaya only shells out to its documented commands and reads its
 * config files, in the same way any terminal would.
 */
object DroidspacesRepository {
    const val BINARY = "/data/local/Droidspaces/bin/droidspaces"
    const val WORKSPACE = "/data/local/Droidspaces"
    const val CONTAINERS = "$WORKSPACE/Containers"

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun validContainer(name: String): Boolean =
        name.matches(Regex("[A-Za-z0-9._ -]{1,80}")) && name.isNotBlank()

    private fun validPid(pid: String): Boolean = pid.matches(Regex("[0-9]{1,10}"))

    private fun validService(name: String): Boolean =
        name.matches(Regex("[A-Za-z0-9_.@:+\\-]{1,120}"))

    private fun runIn(name: String, command: String, timeoutMs: Long = 15_000): ShellResult {
        if (!validContainer(name)) return ShellResult(2, "", "容器名不合法")
        return Shell.run("$BINARY --name=${quote(name)} run ${quote(command)}", timeoutMs)
    }

    suspend fun snapshot(context: android.content.Context): DsSnapshot = withContext(Dispatchers.IO) {
        val found = Shell.run("[ -x $BINARY ] && echo yes || echo no", 5_000).out.trim() == "yes"
        if (!found) {
            return@withContext DsSnapshot(emptyList(), 0L, false, "没有找到 $BINARY，请先安装并初始化 Droidspaces")
        }
        val running = mutableMapOf<String, JSONObject>()
        var hostRam = 0L
        val show = Shell.run("$BINARY --json show", 20_000)
        if (show.ok) {
            runCatching {
                val json = JSONObject(show.out.substringAfter("{", "").let { "{$it" })
                hostRam = json.optLong("ram_total_kb")
                val array = json.optJSONArray("running")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        running[obj.optString("name")] = obj
                    }
                }
            }
        }

        val list = Shell.run("ls -1 $CONTAINERS 2>/dev/null", 8_000).out.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("/") }
        val containers = list.mapNotNull { folder ->
            val configPath = "$CONTAINERS/$folder/container.config"
            val configText = Shell.run("cat ${quote(configPath)} 2>/dev/null", 8_000).out
            if (configText.isBlank()) return@mapNotNull null
            val config = parseConfig(configText)
            val name = config["name"]?.takeIf { it.isNotBlank() } ?: folder
            val live = running[name] ?: running[folder]
            val rootfs = config["rootfs_path"].orEmpty()
            val runningNow = live != null
            DsContainer(
                name = name,
                folder = folder,
                hostname = live?.optString("hostname")?.ifBlank { null }
                    ?: config["hostname"].orEmpty().ifBlank { name },
                rootfs = rootfs,
                netMode = config["net_mode"].orEmpty().ifBlank { "nat" },
                running = runningNow,
                pid = live?.optInt("pid") ?: 0,
                os = live?.optString("os").orEmpty(),
                ip = live?.optString("ip").orEmpty(),
                uptime = live?.optString("uptime").orEmpty(),
                cpuPermill = live?.optLong("cpu_permill") ?: 0L,
                ramUsedKb = live?.optLong("ram_used_kb") ?: 0L,
                ramLimitKb = live?.optLong("ram_limit_kb") ?: 0L,
                cpuLimitPermill = live?.optLong("cpu_limit_permill") ?: 0L,
                hwAccess = config["enable_hw_access"] == "1",
                gpu = config["enable_gpu_mode"] == "1",
                x11 = config["enable_x11"] == "1",
                anland = config["enable_anland"] == "1",
                anlandSock = live?.optString("anland_sock").orEmpty(),
                memoryLimit = config["memory_limit"]?.toLongOrNull() ?: 0L,
                cpus = config["cpu_quota"].orEmpty(),
                pidsLimit = config["pids_limit"]?.toLongOrNull() ?: 0L,
                disk = "",
            )
        }.sortedBy { it.name.lowercase() }

        // A running container whose folder disappeared is still worth showing.
        val known = containers.map { it.name }.toSet()
        val extra = running.filterKeys { it !in known }.map { (name, live) ->
            DsContainer(
                name = name,
                folder = name,
                hostname = live.optString("hostname").ifBlank { name },
                rootfs = "",
                netMode = "?",
                running = true,
                pid = live.optInt("pid"),
                os = live.optString("os"),
                ip = live.optString("ip"),
                uptime = live.optString("uptime"),
                cpuPermill = live.optLong("cpu_permill"),
                ramUsedKb = live.optLong("ram_used_kb"),
                ramLimitKb = live.optLong("ram_limit_kb"),
                cpuLimitPermill = live.optLong("cpu_limit_permill"),
                hwAccess = false,
                gpu = false,
                x11 = live.optInt("x11") == 1,
                anland = live.optString("anland_sock").isNotBlank(),
                anlandSock = live.optString("anland_sock"),
                memoryLimit = 0L,
                cpus = "",
                pidsLimit = 0L,
                disk = "",
            )
        }
        DsSnapshot((containers + extra).sortedBy { it.name.lowercase() }, hostRam, true, null)
    }

    /** Only the live status fields; used for the periodic refresh. */
    suspend fun liveStatus(): Pair<Map<String, JSONObject>, Long> = withContext(Dispatchers.IO) {
        val show = Shell.run("$BINARY --json show", 15_000)
        if (!show.ok) return@withContext emptyMap<String, JSONObject>() to 0L
        runCatching {
            val json = JSONObject(show.out.substringAfter("{", "").let { "{$it" })
            val map = mutableMapOf<String, JSONObject>()
            val array = json.optJSONArray("running")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    map[obj.optString("name")] = obj
                }
            }
            map to json.optLong("ram_total_kb")
        }.getOrDefault(emptyMap<String, JSONObject>() to 0L)
    }

    fun openTerminal(name: String): Process? {
        if (!validContainer(name)) return null
        val command = "export DS_NO_PROXY=1 TERM=xterm-256color COLUMNS=100 LINES=40; " +
            "exec $BINARY --name=${quote(name)} enter"
        return Shell.interactiveProcess(command)
    }

    suspend fun start(name: String): String? = withContext(Dispatchers.IO) {
        val r = Shell.run("$BINARY --name=${quote(name)} start", 120_000)
        if (r.ok) null else r.text.ifBlank { "启动失败（${r.code}）" }
    }

    suspend fun stop(name: String): String? = withContext(Dispatchers.IO) {
        val r = Shell.run("$BINARY --name=${quote(name)} stop", 60_000)
        if (r.ok) null else r.text.ifBlank { "停止失败（${r.code}）" }
    }

    suspend fun restart(name: String): String? = withContext(Dispatchers.IO) {
        val r = Shell.run("$BINARY --name=${quote(name)} restart", 120_000)
        if (r.ok) null else r.text.ifBlank { "重启失败（${r.code}）" }
    }

    suspend fun processes(name: String): List<DsProcess> = withContext(Dispatchers.IO) {
        val r = runIn(
            name,
            "ps -eo pid,ppid,user,comm,args 2>/dev/null || ps -ef 2>/dev/null",
            15_000,
        )
        if (!r.ok) return@withContext emptyList()
        val list = mutableListOf<DsProcess>()
        for (line in r.out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val first = trimmed.substringBefore(' ')
            if (first.equals("PID", true) || first.equals("UID", true)) continue
            val match = Regex("^\\s*(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s*(.*)$").find(trimmed)
            if (match != null) {
                list += DsProcess(
                    pid = match.groupValues[1],
                    ppid = match.groupValues[2],
                    user = match.groupValues[3],
                    name = match.groupValues[4],
                    args = match.groupValues[5],
                )
            } else {
                val old = Regex("^\\s*(\\S+)\\s+(\\d+)\\s+(\\d+).*?\\d\\d:\\d\\d\\s+(.*)$").find(trimmed)
                if (old != null) {
                    list += DsProcess(
                        pid = old.groupValues[2],
                        ppid = old.groupValues[3],
                        user = old.groupValues[1],
                        name = old.groupValues[4].substringBefore(' '),
                        args = old.groupValues[4],
                    )
                }
            }
        }
        list.take(100)
    }

    suspend fun killProcess(name: String, pid: String, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!validPid(pid)) return@withContext "PID 不合法"
        val r = runIn(name, "kill ${if (force) "-9" else "-TERM"} $pid 2>&1", 8_000)
        if (r.ok) null else r.text.ifBlank { "结束进程失败" }
    }

    suspend fun services(name: String): Pair<String, List<DsService>> = withContext(Dispatchers.IO) {
        val detect = runIn(
            name,
            "if [ -d /run/systemd/system ] || command -v systemctl >/dev/null 2>&1; then echo systemd; " +
                "elif command -v rc-service >/dev/null 2>&1; then echo openrc; " +
                "elif [ -d /etc/service ] || [ -d /var/service ]; then echo runit; " +
                "elif [ -d /etc/init.d ]; then echo initd; else echo unknown; fi",
            10_000,
        ).out.trim().lineSequence().lastOrNull().orEmpty().ifBlank { "unknown" }
        val list = when (detect) {
            "systemd" -> {
                val r = runIn(
                    name,
                    "systemctl list-units --type=service --all --no-legend --plain --no-pager 2>/dev/null | head -120",
                    15_000,
                )
                r.out.lineSequence().mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 5)
                    if (parts.size < 4 || !parts[0].endsWith(".service")) return@mapNotNull null
                    val unit = parts[0].removeSuffix(".service")
                    DsService(unit, "${parts[2]}/${parts[3]}")
                }.toList()
            }
            "openrc" -> {
                val r = runIn(name, "rc-status --all --color=no 2>/dev/null", 15_000)
                r.out.lineSequence().mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Runlevel:") || trimmed.startsWith("Dynamic")) {
                        return@mapNotNull null
                    }
                    val match = Regex("^(\\S+)\\s+\\[([^]]+)]").find(trimmed)
                        ?: return@mapNotNull null
                    DsService(match.groupValues[1], match.groupValues[2])
                }.toList()
            }
            "runit" -> {
                val script = """
                    for s in ${'$'}(ls -1 /etc/service /var/service 2>/dev/null | sort -u); do
                      st=${'$'}(sv status "${'$'}s" 2>/dev/null | head -1)
                      [ -n "${'$'}st" ] && echo "${'$'}s|${'$'}st"
                    done | head -120
                """.trimIndent()
                val r = runIn(name, script, 15_000)
                r.out.lineSequence().mapNotNull { line ->
                    val parts = line.split('|', limit = 2)
                    if (parts.size < 2) return@mapNotNull null
                    val state = when {
                        parts[1].startsWith("run:") -> "running"
                        parts[1].startsWith("down:") || parts[1].startsWith("fail:") -> "stopped"
                        else -> "状态未知"
                    }
                    DsService(parts[0], state)
                }.toList()
            }
            "initd" -> {
                val r = runIn(name, "ls -1 /etc/init.d 2>/dev/null", 10_000)
                r.out.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
                    .map { DsService(it, "状态未知") }.toList()
            }
            else -> emptyList()
        }
        detect to list.distinctBy { it.name }.take(120)
    }

    suspend fun serviceAction(name: String, init: String, service: String, action: String): String? =
        withContext(Dispatchers.IO) {
            if (!validService(service) || action !in setOf("start", "stop", "restart")) {
                return@withContext "动作或服务名不合法"
            }
            val command = when (init) {
                "systemd" -> "systemctl $action ${quote("$service.service")} 2>&1"
                "openrc" -> "rc-service ${quote(service)} $action 2>&1"
                "runit" -> "sv $action ${quote("/etc/service/$service")} 2>&1 || " +
                    "sv $action ${quote("/var/service/$service")} 2>&1"
                else -> "/etc/init.d/${quote(service)} $action 2>&1"
            }
            val r = runIn(name, command, 20_000)
            if (r.ok) null else r.text.ifBlank { "服务操作失败" }
        }

    suspend fun apps(name: String): List<DsApp> = withContext(Dispatchers.IO) {
        val script = """
            for f in /usr/share/applications/*.desktop /usr/local/share/applications/*.desktop; do
              [ -f "${'$'}f" ] || continue
              grep -q '^NoDisplay=true' "${'$'}f" && continue
              n=${'$'}(grep -m1 '^Name=' "${'$'}f" | cut -d= -f2-)
              e=${'$'}(grep -m1 '^Exec=' "${'$'}f" | cut -d= -f2-)
              [ -n "${'$'}n" ] && [ -n "${'$'}e" ] && printf '%s\t%s\t%s\t%s\n' "${'$'}n" "${'$'}e" "${'$'}(basename "${'$'}f" .desktop)" "${'$'}f"
            done | head -80
        """.trimIndent()
        val r = runIn(name, script, 15_000)
        r.out.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            DsApp(
                name = parts[0],
                exec = parts[1],
                desktopId = parts[2],
                systemApp = parts[3].startsWith("/usr/share/"),
            )
        }.toList()
    }

    suspend fun startApp(name: String, app: DsApp, x11: Boolean, anland: Boolean): String? =
        withContext(Dispatchers.IO) {
            val exec = app.exec.replace(Regex("%[a-zA-Z]"), "").trim()
            if (exec.isBlank()) return@withContext "应用没有可执行命令"
            val env = when {
                x11 -> "DISPLAY=:0 "
                anland -> "WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000 "
                else -> "DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 "
            }
            val r = runIn(
                name,
                "nohup env $env sh -c ${quote(exec)} >/dev/null 2>&1 & echo started",
                10_000,
            )
            if (r.ok) null else r.text.ifBlank { "启动应用失败" }
        }

    suspend fun stopApp(name: String, app: DsApp): String? = withContext(Dispatchers.IO) {
        val exec = app.exec.replace(Regex("%[a-zA-Z]"), "").trim()
        val token = exec.substringBefore(' ').substringAfterLast('/')
        if (token.isBlank() || token in setOf("sh", "bash", "env", "python", "python3")) {
            return@withContext "这个应用的关闭方式不安全，请到进程页手动结束"
        }
        val r = runIn(name, "pkill -TERM -f ${quote(exec)} 2>&1", 8_000)
        if (r.ok) null else r.text.ifBlank { "关闭应用失败" }
    }

    private fun parseConfig(text: String): Map<String, String> = buildMap {
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val index = trimmed.indexOf('=')
            if (index <= 0) continue
            put(trimmed.substring(0, index).trim(), trimmed.substring(index + 1).trim())
        }
    }

    suspend fun diskUsage(rootfs: String): String = withContext(Dispatchers.IO) {
        if (rootfs.isBlank()) return@withContext ""
        val path = quote(rootfs)
        val isDir = Shell.run("[ -d $path ] && echo dir || echo file", 5_000).out.trim() == "dir"
        val r = if (isDir) {
            Shell.run("du -sh $path 2>/dev/null | cut -f1", 10_000)
        } else {
            Shell.run("stat -c %s $path 2>/dev/null", 5_000)
        }
        val raw = r.out.trim()
        if (raw.isBlank()) return@withContext ""
        if (isDir) return@withContext raw
        val bytes = raw.toLongOrNull() ?: return@withContext raw
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return@withContext if (gb >= 1) String.format("%.1f GB", gb) else "${bytes / (1024 * 1024)} MB"
    }
}

fun DsContainer.mergeLive(live: JSONObject?): DsContainer {
    if (live == null) return copy(running = false, pid = 0)
    return copy(
        running = true,
        pid = live.optInt("pid"),
        os = live.optString("os").ifBlank { os },
        hostname = live.optString("hostname").ifBlank { hostname },
        ip = live.optString("ip"),
        uptime = live.optString("uptime"),
        cpuPermill = live.optLong("cpu_permill"),
        ramUsedKb = live.optLong("ram_used_kb"),
        ramLimitKb = live.optLong("ram_limit_kb"),
        cpuLimitPermill = live.optLong("cpu_limit_permill"),
        anlandSock = live.optString("anland_sock"),
        anland = anland || live.optString("anland_sock").isNotBlank(),
    )
}
