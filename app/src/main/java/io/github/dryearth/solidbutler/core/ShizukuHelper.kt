package io.github.dryearth.solidbutler.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.dryearth.solidbutler.IShizukuShell
import io.github.dryearth.solidbutler.service.ShizukuShellService
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Shizuku is used only as a shell executor. Commands run through the official
 * UserService path (a shell/root process), not the deprecated newProcess API.
 */
object ShizukuHelper {

    const val REQUEST_CODE = 4217

    private var appContext: Context? = null
    @Volatile private var shell: IShizukuShell? = null
    @Volatile private var binding = false
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "xi-shizuku") }

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext!!, ShizukuShellService::class.java)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shell = binder?.let { IShizukuShell.Stub.asInterface(it) }
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            binding = false
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (isReady()) ensureBound()
    }

    fun ensureBound() {
        if (shell != null || binding || !isReady()) return
        val context = appContext ?: return
        binding = true
        runCatching {
            Shizuku.bindUserService(args, connection)
        }.onFailure { binding = false }
    }

    fun isInstalled(): Boolean = runCatching {
        Shizuku.pingBinder()
        true
    }.getOrDefault(false)

    fun isReady(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.isPreV11()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isShellReady(): Boolean = shell != null

    fun needsPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && !isReady()
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    fun exec(cmd: String, timeoutMs: Long): ShellResult {
        if (!isReady()) return ShellResult(-2, "", "Shizuku 未授权")
        ensureBound()
        val service = shell
        if (service != null) {
            val future = executor.submit<String> { service.run(cmd) }
            return try {
                val out = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                ShellResult(if (out.startsWith("ERROR:")) -1 else 0, out)
            } catch (e: TimeoutException) {
                future.cancel(true)
                ShellResult(124, "", "Shizuku 命令超时")
            } catch (e: Exception) {
                ShellResult(-1, "", e.message ?: "Shizuku UserService 失败")
            }
        }
        // The UserService is still binding; try the legacy path as a short bridge.
        return runCatching {
            val process = newProcess(arrayOf("sh", "-c", cmd))
            var out = ""
            var err = ""
            val t1 = Thread { out = process.inputStream.bufferedReader().use { it.readText() } }
            val t2 = Thread { err = process.errorStream.bufferedReader().use { it.readText() } }
            t1.start(); t2.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroy()
            t1.join(1_500); t2.join(1_500)
            ShellResult(if (finished) process.exitValue() else 124, out, err)
        }.getOrElse { ShellResult(-1, "", "Shizuku UserService 正在连接，请重试") }
    }

    fun binder(): IBinder? = runCatching { Shizuku.getBinder() }.getOrNull()

    fun openManager(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
            ?: return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }
}
