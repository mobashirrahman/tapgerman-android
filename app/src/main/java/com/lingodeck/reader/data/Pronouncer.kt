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

    /**
     * A word asked for before the engine finished starting.
     *
     * The engine initialises asynchronously, and the first tap on a word is very often the first
     * tap in the process — so without this the first word a reader speaks is silently swallowed.
     * That was tolerable while this was the only way to pronounce a word; it is not now that it is
     * the fallback, because the fallback has to be dependable to be worth falling back to.
     *
     * One word is enough. Two taps faster than the engine can start is two words nobody can hear
     * anyway, and a queue would put out words the reader has already moved on from.
     */
    @Volatile
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        // Prefer German so "Häuser" is not read as English. Falls back to the default voice when
        // no German voice is installed rather than failing silently.
        if (engine.isLanguageAvailable(Locale.GERMAN) >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = Locale.GERMAN
        }
        ready = true
        pending?.let { word ->
            pending = null
            speak(word)
        }
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (!ready) {
            // Held until onInit rather than dropped, so the first word in a session is not lost.
            pending = trimmed
            return
        }
        tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, SPEECH_ID)
    }

    fun stop() {
        tts?.stop()
    }

    fun close() {
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    private companion object {
        const val SPEECH_ID = "lingodeck-word"
    }
}
