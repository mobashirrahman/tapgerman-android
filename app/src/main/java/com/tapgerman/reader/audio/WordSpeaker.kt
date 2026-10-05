package com.tapgerman.reader.audio

import android.content.Context
import com.tapgerman.reader.data.Pronouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Decides how a word is spoken: a person saying it, or a synthesiser.
 *
 * Wiktionary records German words as spoken German, the app has had the URL for every one of them
 * since the first version, stored on every saved word and never played. When a recording exists it
 * beats any synthetic voice on the device, so it is preferred and speech is the fallback rather
 * than the default.
 *
 * Every path that cannot produce a recording — no URL, offline, rate limited, a file the player
 * will not open — ends at the synthesiser without saying anything about it, because a word that
 * cannot be recorded is not a problem worth interrupting a reader over.
 */
class WordSpeaker(
    context: Context,
    private val scope: CoroutineScope,
    private val cache: AudioCache,
    private val audio: DictionaryAudio = DictionaryAudio(cache),
    /** Whether the reader wants recordings at all. Off means the synthetic voice, as before. */
    private val recordingsEnabled: () -> Boolean = { true },
) {

    private val tts = Pronouncer(context)
    private val player = AudioPlayer(context)
    private var pending: Job? = null

    /**
     * Speaks [word], preferring the recording at [audioUrl].
     *
     * [immediate] speaks through the synthesiser without touching the network, for
     * long-pressing a word in the reader: that gesture has to respond at once, and the word has
     * usually not been looked up, so there is no recording to hand and no time to fetch one.
     */
    fun speak(word: String, audioUrl: String = "", immediate: Boolean = false) {
        pending?.cancel()
        if (word.isBlank()) return

        if (immediate || !recordingsEnabled() || audioUrl.isBlank()) {
            player.stop()
            tts.speak(word)
            return
        }

        pending = scope.launch {
            val recording = audio.recordingFor(audioUrl)
            if (recording == null) {
                tts.speak(word)
            } else {
                // The flag keeps a late playback error from speaking over the recording that is
                // already sounding.
                var spoken = false
                val started = player.play(recording) { if (!spoken) { spoken = true; tts.speak(word) } }
                if (!started && !spoken) tts.speak(word)
            }
        }
    }

    fun stop() {
        pending?.cancel()
        player.stop()
        tts.stop()
    }

    fun release() {
        stop()
        tts.close()
    }
}
