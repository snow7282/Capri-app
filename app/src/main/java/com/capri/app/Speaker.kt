package com.capri.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads her lines out loud with Android's built-in text-to-speech. */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.UK)
            tts?.setPitch(1.1f)
            tts?.setSpeechRate(1.0f)
            ready = true
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val clean = Fmt.stripActions(text)
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "capri")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
