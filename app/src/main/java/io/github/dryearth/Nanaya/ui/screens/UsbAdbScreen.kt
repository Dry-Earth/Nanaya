package io.github.dryearth.Nanaya.ui.screens

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.dryearth.Nanaya.core.UsbAdbClient
import io.github.dryearth.Nanaya.ui.components.ActionButton
import io.github.dryearth.Nanaya.ui.components.GlassBackButton
import io.github.dryearth.Nanaya.ui.components.GlassPanel
import io.github.dryearth.Nanaya.ui.components.Pill
import io.github.dryearth.Nanaya.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UsbAdbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usbManager = remember { context.getSystemService(UsbManager::class.java) }
    var devices by remember {
        mutableStateOf(
            usbManager.deviceList.values.filter { UsbAdbClient.isAdbDevice(it) },
        )
    }
    var client by remember { mutableStateOf<UsbAdbClient?>(null) }
    var connectedDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var pendingDevice by remember { mutableStateOf<UsbDevice?>(null) }

    fun connect(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            pendingDevice = device
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                20,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            usbManager.requestPermission(device, pending)
            message = "请在系统弹窗里允许 USB 调试"
            return
        }
        busy = true
        message = "正在连接 USB ADB…"
        scope.launch {
            val created = UsbAdbClient(context, device)
            val error = withContext(Dispatchers.IO) {
                runCatching { created.connect() }.getOrElse { it.message ?: "USB 连接异常" }
            }
            busy = false
            if (error == null) {
                client?.close()
                client = created
                connectedDevice = device
                message = "USB ADB 已连接"
            } else {
                created.close()
                message = "连接失败：$error"
            }
        }
    }

    fun run(cmd: String) {
        val target = cmd.trim()
        val active = client
        if (target.isBlank() || busy || active == null) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { active.runShell(target) }.getOrElse { "失败：${it.message}" }
            }
            output = "$ ${target}\n$result\n\n$output"
            busy = false
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_USB_PERMISSION -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            ?: pendingDevice
                        if (device != null && usbManager.hasPermission(device)) {
                            connect(device)
                        } else {
                            message = "USB 权限被拒绝"
                        }
                        pendingDevice = null
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        devices = usbManager.deviceList.values.filter { UsbAdbClient.isAdbDevice(it) }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            client?.close()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("USB OTG ADB", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                Text("用 USB 线直接控制另一台设备", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            }
            Pill(if (client != null) "已连接" else "未连接", selected = client != null, color = if (client != null) Glass.Mint else Glass.Faint)
        }

        if (devices.isEmpty()) {
            GlassPanel(tint = Glass.Amber, corner = 14.dp, padding = PaddingValues(12.dp)) {
                Text(
                    "没有检测到 ADB USB 设备。请用 OTG 线连接目标手机，并在目标设备上开启 USB 调试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Amber,
                )
            }
        } else {
            GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
                devices.forEach { device ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.productName ?: device.deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.Text,
                            )
                            Text(
                                "${device.manufacturerName ?: ""} · ${device.deviceId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Glass.Faint,
                            )
                        }
                        ActionButton(
                            if (connectedDevice == device) "已连接" else "连接",
                            enabled = !busy && connectedDevice != device,
                            onClick = { connect(device) },
                        )
                    }
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = if (client != null) Glass.Mint else Glass.Amber)
        }

        GlassPanel(
            modifier = Modifier.weight(1f),
            corner = 16.dp,
            padding = PaddingValues(12.dp),
        ) {
            SelectionContainer {
                Text(
                    output.ifBlank { "USB ADB 连接后，在这里执行远程 shell 命令。" },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = Glass.Muted,
                    ),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
        }

        GlassPanel(corner = 14.dp, padding = PaddingValues(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Glass.Accent))
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Glass.Text,
                    ),
                    cursorBrush = SolidColor(Glass.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { run(command) }),
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    "执行",
                    accent = true,
                    enabled = !busy && command.isNotBlank() && client != null,
                    onClick = { run(command) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("设备信息", color = Glass.Ice) {
                    run("id; getprop ro.product.model; getprop ro.build.version.release; wm size")
                }
                Pill("应用列表", color = Glass.Violet) { run("pm list packages -3") }
                Pill("电池", color = Glass.Mint) { run("dumpsys battery | head -12") }
            }
        }
    }
}

private const val ACTION_USB_PERMISSION = "io.github.dryearth.Nanaya.USB_PERMISSION"
