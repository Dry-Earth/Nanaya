package io.github.dryearth.solidbutler.core

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("solid_butler", Context.MODE_PRIVATE)

    var aiBaseUrl: String
        get() = sp.getString("ai_base", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) = sp.edit().putString("ai_base", value).apply()

    var aiApiKey: String
        get() = sp.getString("ai_key", "") ?: ""
        set(value) = sp.edit().putString("ai_key", value).apply()

    var aiModel: String
        get() = sp.getString("ai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = sp.edit().putString("ai_model", value).apply()

    var shizukuAsked: Boolean
        get() = sp.getBoolean("shizuku_asked", false)
        set(value) = sp.edit().putBoolean("shizuku_asked", value).apply()

    var aiSpeakReplies: Boolean
        get() = sp.getBoolean("ai_speak", true)
        set(value) = sp.edit().putBoolean("ai_speak", value).apply()

    var sttPackage: String
        get() = sp.getString("stt_package", "") ?: ""
        set(value) = sp.edit().putString("stt_package", value).apply()

    var ttsEngine: String
        get() = sp.getString("tts_engine", "") ?: ""
        set(value) = sp.edit().putString("tts_engine", value).apply()

    var backgroundStyle: String
        get() = sp.getString("bg_style", "stars") ?: "stars"
        set(value) = sp.edit().putString("bg_style", value).apply()

    var backgroundUri: String
        get() = sp.getString("bg_uri", "") ?: ""
        set(value) = sp.edit().putString("bg_uri", value).apply()

    var aiConfigured: Boolean
        get() = aiApiKey.isNotBlank()
        set(value) = sp.edit().putBoolean("ai_configured", value).apply()

    var appLockEnabled: Boolean
        get() = sp.getBoolean("app_lock_enabled", false)
        set(value) = sp.edit().putBoolean("app_lock_enabled", value).apply()

    var pinHash: String
        get() = sp.getString("pin_hash", "") ?: ""
        set(value) = sp.edit().putString("pin_hash", value).apply()

    var pinSalt: String
        get() = sp.getString("pin_salt", "") ?: ""
        set(value) = sp.edit().putString("pin_salt", value).apply()

    var firewallPersist: Boolean
        get() = sp.getBoolean("firewall_persist", true)
        set(value) = sp.edit().putBoolean("firewall_persist", value).apply()

    fun hasPin(): Boolean = pinHash.isNotBlank()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        pinSalt = toHex(salt)
        pinHash = hashPin(pin, pinSalt)
    }

    fun verifyPin(pin: String): Boolean = pinHash.isNotBlank() && pinHash == hashPin(pin, pinSalt)

    fun clearPin() {
        pinHash = ""
        pinSalt = ""
        appLockEnabled = false
    }

    fun lockedPackages(): MutableSet<String> =
        (sp.getStringSet("locked_packages", emptySet()) ?: emptySet()).toMutableSet()

    fun setLocked(pkg: String, locked: Boolean) {
        val set = lockedPackages()
        if (locked) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet("locked_packages", set).apply()
    }

    fun whitelist(): MutableSet<String> =
        (sp.getStringSet("whitelist", emptySet()) ?: emptySet()).toMutableSet()

    fun setWhitelisted(pkg: String, white: Boolean) {
        val set = whitelist()
        if (white) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet("whitelist", set).apply()
    }

    fun blockedUids(): MutableSet<Int> =
        (sp.getStringSet("firewall_uids", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toMutableSet()

    fun setFirewallBlocked(uid: Int, blocked: Boolean) {
        val set = blockedUids()
        if (blocked) set.add(uid) else set.remove(uid)
        sp.edit().putStringSet("firewall_uids", set.map { it.toString() }.toSet()).apply()
    }

    var lastChargingLimit: Int
        get() = sp.getInt("last_charging_limit", 80)
        set(value) = sp.edit().putInt("last_charging_limit", value).apply()

    var chargingAlert: Boolean
        get() = sp.getBoolean("charging_alert", false)
        set(value) = sp.edit().putBoolean("charging_alert", value).apply()

    var batteryGuardEnabled: Boolean
        get() = sp.getBoolean("battery_guard", true)
        set(value) = sp.edit().putBoolean("battery_guard", value).apply()

    var lowBatteryVoice: Boolean
        get() = sp.getBoolean("low_battery_voice", true)
        set(value) = sp.edit().putBoolean("low_battery_voice", value).apply()

    var fullBatteryVoice: Boolean
        get() = sp.getBoolean("full_battery_voice", true)
        set(value) = sp.edit().putBoolean("full_battery_voice", value).apply()

    var lowNotified: Boolean
        get() = sp.getBoolean("low_notified", false)
        set(value) = sp.edit().putBoolean("low_notified", value).apply()

    var fullNotified: Boolean
        get() = sp.getBoolean("full_notified", false)
        set(value) = sp.edit().putBoolean("full_notified", value).apply()

    var chargeLimitRuleEnabled: Boolean
        get() = sp.getBoolean("charge_rule", false)
        set(value) = sp.edit().putBoolean("charge_rule", value).apply()

    var chargeLimitRule: Int
        get() = sp.getInt("charge_rule_limit", 80)
        set(value) = sp.edit().putInt("charge_rule_limit", value).apply()

    var screenOffRule: Boolean
        get() = sp.getBoolean("screen_off_rule", false)
        set(value) = sp.edit().putBoolean("screen_off_rule", value).apply()

    var screenOffPkg: String
        get() = sp.getString("screen_off_pkg", "") ?: ""
        set(value) = sp.edit().putString("screen_off_pkg", value).apply()

    var wifiRule: Boolean
        get() = sp.getBoolean("wifi_rule", false)
        set(value) = sp.edit().putBoolean("wifi_rule", value).apply()

    var wifiPkg: String
        get() = sp.getString("wifi_pkg", "") ?: ""
        set(value) = sp.edit().putString("wifi_pkg", value).apply()

    fun blockedChannels(): MutableSet<String> =
        (sp.getStringSet("blocked_channels", emptySet()) ?: emptySet()).toMutableSet()

    fun setChannelBlocked(pkg: String, channel: String, blocked: Boolean) {
        val set = blockedChannels()
        val key = "$pkg|$channel"
        if (blocked) set.add(key) else set.remove(key)
        sp.edit().putStringSet("blocked_channels", set).apply()
    }

    fun blockedDomains(): MutableSet<String> =
        (sp.getStringSet("blocked_domains", emptySet()) ?: emptySet()).toMutableSet()

    fun setDomainBlocked(domain: String, blocked: Boolean) {
        val set = blockedDomains()
        if (blocked) set.add(domain.lowercase().trim()) else set.remove(domain.lowercase().trim())
        sp.edit().putStringSet("blocked_domains", set).apply()
    }

    var domainFirewallEnabled: Boolean
        get() = sp.getBoolean("domain_firewall", false)
        set(value) = sp.edit().putBoolean("domain_firewall", value).apply()

    companion object {
        fun hashPin(pin: String, saltHex: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(hexToBytes(saltHex))
            return toHex(md.digest(pin.toByteArray()))
        }
    }
}

private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

private fun hexToBytes(hex: String): ByteArray =
    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
