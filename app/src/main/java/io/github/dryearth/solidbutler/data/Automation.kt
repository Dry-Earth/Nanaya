package io.github.dryearth.solidbutler.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.github.dryearth.solidbutler.core.LineageHardware
import io.github.dryearth.solidbutler.core.LineageHealth
import io.github.dryearth.solidbutler.core.Prefs
import io.github.dryearth.solidbutler.core.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val trigger: String,
    val triggerValue: String = "",
    val action: String,
    val actionValue: String = "",
)

class AutomationRepository(private val context: Context) {
    private val sp = context.getSharedPreferences("xi_automation", Context.MODE_PRIVATE)

    fun all(): List<AutomationRule> = runCatching {
        val arr = JSONArray(sp.getString("rules", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AutomationRule(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name", "未命名规则"),
                enabled = o.optBoolean("enabled", true),
                trigger = o.optString("trigger", "time"),
                triggerValue = o.optString("triggerValue", ""),
                action = o.optString("action", "notify"),
                actionValue = o.optString("actionValue", ""),
            )
        }
    }.getOrDefault(emptyList())

    fun save(rules: List<AutomationRule>) {
        val arr = JSONArray()
        rules.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("enabled", it.enabled)
                    .put("trigger", it.trigger)
                    .put("triggerValue", it.triggerValue)
                    .put("action", it.action)
                    .put("actionValue", it.actionValue)
            )
        }
        sp.edit().putString("rules", arr.toString()).apply()
    }

    fun add(rule: AutomationRule) = save(all() + rule)

    fun update(rule: AutomationRule) = save(all().map { if (it.id == rule.id) rule else it })

    fun setEnabled(id: String, enabled: Boolean) =
        save(all().map { if (it.id == id) it.copy(enabled = enabled) else it })

    fun delete(id: String) = save(all().filterNot { it.id == id })

    fun lastRun(id: String): Long = sp.getLong("last_$id", 0L)

    fun markRun(id: String) = sp.edit().putLong("last_$id", System.currentTimeMillis()).apply()
}

/**
 * Local automation engine. It is driven by BatteryGuardService, which already
 * owns the battery/screen/Wi-Fi callbacks for the voice reminders.
 */
object AutomationEngine {

    private var lastCharging = false

    fun onBattery(context: Context, level: Int, charging: Boolean, speak: (String) -> Unit = {}) {
        val repo = AutomationRepository(context)
        val rules = repo.all().filter { it.enabled }
        rules.forEach { rule ->
            val value = rule.triggerValue.toIntOrNull() ?: 25
            val match = when (rule.trigger) {
                "battery_low" -> !charging && level <= value
                "battery_full" -> charging && level >= value
                "charging_start" -> charging && !lastCharging
                else -> false
            }
            if (match && canRun(repo, rule, cooldown(rule))) run(context, rule, repo, speak)
        }
        lastCharging = charging
    }

    fun onScreenOff(context: Context, speak: (String) -> Unit = {}) {
        val repo = AutomationRepository(context)
        repo.all().filter { it.enabled && it.trigger == "screen_off" }.forEach {
            if (canRun(repo, it, 5 * 60_000L)) run(context, it, repo, speak)
        }
    }

    fun onWifi(context: Context, connected: Boolean, speak: (String) -> Unit = {}) {
        val repo = AutomationRepository(context)
        repo.all().filter {
            it.enabled && it.trigger == if (connected) "wifi_on" else "wifi_off"
        }.forEach {
            if (canRun(repo, it, 60_000L)) run(context, it, repo, speak)
        }
    }

    fun tickTime(context: Context, speak: (String) -> Unit = {}) {
        val now = java.util.Calendar.getInstance()
        val hhmm = String.format(java.util.Locale.US, "%02d:%02d", now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
        val repo = AutomationRepository(context)
        repo.all().filter { it.enabled && it.trigger == "time" && it.triggerValue == hhmm }.forEach {
            if (canRun(repo, it, 20 * 3600_000L)) run(context, it, repo, speak)
        }
    }

    fun onBoot(context: Context) {
        val repo = AutomationRepository(context)
        repo.all().filter { it.enabled && it.trigger == "boot" }.forEach {
            if (canRun(repo, it, 30_000L)) run(context, it, repo) {}
        }
    }

    private fun cooldown(rule: AutomationRule): Long = when (rule.trigger) {
        "battery_low", "battery_full" -> 3 * 3600_000L
        "charging_start" -> 5 * 60_000L
        "time" -> 20 * 3600_000L
        else -> 60_000L
    }

    private fun canRun(repo: AutomationRepository, rule: AutomationRule, cooldown: Long): Boolean =
        System.currentTimeMillis() - repo.lastRun(rule.id) >= cooldown

    private fun run(
        context: Context,
        rule: AutomationRule,
        repo: AutomationRepository,
        speak: (String) -> Unit,
    ) {
        repo.markRun(rule.id)
        Thread {
            when (rule.action) {
                "speak" -> speak(rule.actionValue.ifBlank { rule.name })
                "notify" -> notify(context, rule.name, rule.actionValue.ifBlank { "自动化已触发" })
                "charge_limit" -> {
                    val limit = (rule.actionValue.toIntOrNull() ?: 80).coerceIn(70, 100)
                    LineageHealth.setLimit(limit)
                    LineageHealth.setMode(3)
                    LineageHealth.setEnabled(true)
                }
                "restrict_app" -> runApp(context, rule.actionValue) { app, appRepo ->
                    appRepo.restrictBackground(app.pkg, app.uid, true)
                }
                "standby" -> runApp(context, rule.actionValue) { app, appRepo ->
                    appRepo.setStandbyBucket(app.pkg, "restricted")
                }
                "freeze" -> runApp(context, rule.actionValue) { app, appRepo ->
                    appRepo.freeze(app.pkg, true)
                }
                "lineage_feature" -> {
                    val parts = rule.actionValue.split(':', limit = 2)
                    val feature = LineageHardware.FEATURES.firstOrNull {
                        it.label.contains(parts[0], true)
                    }
                    if (feature != null) {
                        LineageHardware.set(feature.bit, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
                    }
                }
                "brightness" -> {
                    val pct = (rule.actionValue.toIntOrNull() ?: 50).coerceIn(1, 100)
                    Shell.run("settings put system screen_brightness ${pct * 255 / 100}")
                }
                "ringer" -> {
                    val am = context.getSystemService(AudioManager::class.java)
                    val mode = when (rule.actionValue.lowercase()) {
                        "silent", "静音" -> AudioManager.RINGER_MODE_SILENT
                        "vibrate", "振动" -> AudioManager.RINGER_MODE_VIBRATE
                        else -> AudioManager.RINGER_MODE_NORMAL
                    }
                    runCatching { am.ringerMode = mode }
                }
            }
        }.start()
    }

    private fun runApp(
        context: Context,
        token: String,
        block: (AppEntry, AppRepository) -> Unit,
    ) {
        val appRepo = AppRepository(context, Prefs(context))
        val app = appRepo.list().firstOrNull {
            it.pkg.equals(token, true) || it.label.contains(token, true)
        } ?: return
        block(app, appRepo)
    }

    private fun notify(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("xi_automation", "自动化", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, "xi_automation")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        nm.notify(
            title.hashCode(),
            n.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }
}
