package io.github.dryearth.solidbutler.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Binds the external RecognitionService selected in
 * Settings.Secure.voice_recognition_service directly, instead of launching
 * the system speech Activity. This keeps the overlay visible and avoids a
 * second speech window.
 */
object SystemSpeechRecognizer {

    private const val VOICE_RECOGNITION_SERVICE = "voice_recognition_service"

    fun create(context: Context): SpeechRecognizer? {
        val app = context.applicationContext
        resolveExternalService(app)?.let { component ->
            return SpeechRecognizer.createSpeechRecognizer(app, component)
        }
        return if (SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        } else {
            null
        }
    }

    fun resolveExternalService(context: Context): ComponentName? {
        val configured = runCatching {
            Settings.Secure.getString(context.contentResolver, VOICE_RECOGNITION_SERVICE)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(ComponentName::unflattenFromString)

        @Suppress("DEPRECATION")
        val services = runCatching {
            context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.MATCH_ALL,
            )
        }.getOrDefault(emptyList())

        return selectExternalService(services, context.packageName, configured)
    }

    private fun selectExternalService(
        services: List<ResolveInfo>,
        ownPackage: String,
        configured: ComponentName?,
    ): ComponentName? = services.asSequence()
        .mapNotNull { it.serviceInfo }
        .filter {
            it.packageName != ownPackage && it.exported && it.enabled &&
                it.applicationInfo?.enabled != false
        }
        .filter {
            val component = ComponentName(it.packageName, it.name)
            component == configured ||
                it.permission == "android.permission.BIND_SPEECH_RECOGNITION_SERVICE"
        }
        .sortedWith(
            compareByDescending<android.content.pm.ServiceInfo> {
                ComponentName(it.packageName, it.name) == configured
            }.thenByDescending {
                (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            },
        )
        .map { ComponentName(it.packageName, it.name) }
        .firstOrNull()
}
