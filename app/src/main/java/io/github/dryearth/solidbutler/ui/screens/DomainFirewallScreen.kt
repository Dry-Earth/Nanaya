package io.github.dryearth.solidbutler.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.service.DomainFirewallService
import io.github.dryearth.solidbutler.ui.components.ActionButton
import io.github.dryearth.solidbutler.ui.components.GlassPanel
import io.github.dryearth.solidbutler.ui.components.Pill
import io.github.dryearth.solidbutler.ui.theme.Glass

private val presets = listOf(
    "doubleclick.net",
    "google-analytics.com",
    "app-measurement.com",
    "umeng.com",
    "cnzz.com",
    "adjust.com",
    "appsflyer.com",
    "gdt.qq.com",
)

@Composable
fun DomainFirewallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var enabled by remember { mutableStateOf(DomainFirewallService.running) }
    var domains by remember { mutableStateOf(prefs.blockedDomains().toList().sorted()) }
    var input by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    fun startService() {
        DomainFirewallService.start(context)
        prefs.domainFirewallEnabled = true
        enabled = true
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService()
        } else {
            toast = "没有获得 VPN 授权"
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0x0FFFFFFF)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Glass.Muted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("域名过滤", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                    Text("VpnService · 只接管 DNS", style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
            }
        }

        item {
            GlassPanel(tint = if (enabled) Glass.Mint else Glass.Amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (enabled) "过滤运行中" else "未启动",
                            style = MaterialTheme.typography.titleMedium,
                            color = Glass.Text,
                        )
                        Text(
                            "只把 10.111.0.1:53 路由进本地 VPN，普通流量不经过本应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.Muted,
                        )
                    }
                    ActionButton(
                        if (enabled) "停止" else "启动",
                        accent = !enabled,
                        onClick = {
                            if (enabled) {
                                DomainFirewallService.stop(context)
                                prefs.domainFirewallEnabled = false
                                enabled = false
                            } else {
                                val intent = VpnService.prepare(context)
                                if (intent != null) consentLauncher.launch(intent)
                                else startService()
                            }
                        },
                    )
                }
            }
        }

        item {
            GlassPanel {
                Text("添加要拦截的域名", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(Color(0x07FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text("例如 ads.example.com", style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.trim() },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Glass.Text),
                            cursorBrush = SolidColor(Glass.Accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ActionButton("添加", accent = true, enabled = input.isNotBlank()) {
                        val d = input.lowercase().trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
                        if (d.contains('.')) {
                            prefs.setDomainBlocked(d, true)
                            domains = prefs.blockedDomains().toList().sorted()
                            input = ""
                        } else {
                            toast = "请输入完整域名"
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("常用追踪域名", style = MaterialTheme.typography.labelSmall, color = Glass.Faint)
                Spacer(Modifier.height(6.dp))
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { d ->
                            Pill(
                                d,
                                selected = d in domains,
                                color = Glass.Amber,
                                onClick = {
                                    prefs.setDomainBlocked(d, d !in domains)
                                    domains = prefs.blockedDomains().toList().sorted()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item {
            Text(
                "已拦截 ${domains.size} 个域名",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.Faint,
            )
        }
        items(domains, key = { it }) { d ->
            GlassPanel(corner = 12.dp, padding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d,
                        style = MaterialTheme.typography.bodySmall,
                        color = Glass.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Pill("移除", color = Glass.Rose, onClick = {
                        prefs.setDomainBlocked(d, false)
                        domains = prefs.blockedDomains().toList().sorted()
                    })
                }
            }
        }

        item {
            GlassPanel(tint = Glass.Amber) {
                Text(
                    "限制：只处理 UDP DNS，DoH/私有 DNS、IP 直连、IPv6 会绕过；不代理普通流量，因此不会拖慢网速。它适合挡广告/追踪域名，不是完整防火墙。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Glass.Muted,
                )
            }
        }
        toast?.let {
            item {
                GlassPanel(tint = Glass.Amber) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Glass.Amber)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
