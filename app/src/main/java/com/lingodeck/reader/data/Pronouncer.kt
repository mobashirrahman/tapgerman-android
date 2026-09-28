package com.lingodeck.reader.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks a word through the platform text-to-speech engine.
 *
 * The Android counterpart of `speechSynthesis.speak(new SpeechSynthesisUtterance(...))` in
 * `extension/content.js`, which is how the Chrome extension pronounces a word. On-device TTS keeps
 * the audio path offline and free of a media pipeline, and it works even when Kaikki offers no
 * `mp3_url` for the form.
 */
class Pronouncer(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        // Prefer German so "Häuser" is not read as English. Falls back to the default voice when
        // no German voice is installed rather than failing silently.
        if (engine.isLanguageAvailable(Locale.GERMAN) >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = Locale.GERMAN
        }
        ready = true
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (!ready || trimmed.isEmpty()) return
        tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, SPEECH_ID)
    }

    fun stop() {
        tts?.stop()
    }

    fun close() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val SPEECH_ID = "lingodeck-word"
    }
}
