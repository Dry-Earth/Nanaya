package io.github.dryearth.solidbutler.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import io.github.dryearth.solidbutler.ui.AssistLaunchActivity

/**
 * Makes 汐 eligible to be the system's default digital assistant.
 * The session immediately hands off to the floating glass overlay.
 */
class AssistVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        launchOverlay()
    }

    private fun launchOverlay() {
        runCatching {
            startActivity(
                Intent(this, io.github.dryearth.solidbutler.MainActivity::class.java)
                    .putExtra("open_chat", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}

class AssistVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        AssistVoiceSession(this)
}

private class AssistVoiceSession(context: VoiceInteractionSessionService) :
    VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
    }

    override fun onCreateContentView(): View = View(context)

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        runCatching {
            context.startActivity(
                Intent(context, io.github.dryearth.solidbutler.MainActivity::class.java)
                    .putExtra("open_chat", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
        finish()
    }
}
