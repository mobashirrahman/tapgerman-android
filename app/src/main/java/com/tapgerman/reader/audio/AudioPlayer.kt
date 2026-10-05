package com.tapgerman.reader.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

/**
 * Plays one recording at a time and gives the device back afterwards.
 *
 * A word is a second or two long and the reader taps the next one almost immediately, so the
 * player is torn down on every completion rather than kept warm. Holding a `MediaPlayer` alive
 * between taps costs a codec and a file handle for no benefit, and a stale one is a classic source
 * of "playback stopped for no reason".
 *
 * Audio focus is requested and abandoned, so a pronunciation does not talk over whatever the
 * reader was already listening to. Loss is not contested: if focus is refused, the recording is
 * simply not played and the caller falls back to speech, which is quieter and less rude than
 * either pausing someone else's music or skipping the word.
 */
class AudioPlayer(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Plays [file], calling [onUnavailable] if it cannot be heard.
     *
     * Returns false when playback could not be started at all. [onUnavailable] covers the other
     * case — it started and then failed — because a corrupt file that `prepare()` accepted can
     * still fail on the way out, and that is the same problem from the reader's side: no sound.
     * Either way the caller's response is the same, to speak the word some other way.
     */
    fun play(file: File, onUnavailable: () -> Unit): Boolean {
        if (!file.isFile || file.length() == 0L) {
            onUnavailable()
            return false
        }
        if (!requestFocus()) {
            onUnavailable()
            return false
        }

        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            // Prepared synchronously: a word is short, and the alternative — preparing then
            // starting in a callback — is audible as a gap between the tap and the sound.
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { abandonFocus(); it.release() }
            player.setOnErrorListener { _, _, _ ->
                abandonFocus()
                player.release()
                onUnavailable()
                true
            }
            player.prepare()
            player.start()
            true
        } catch (e: Exception) {
            // A corrupt or unsupported file throws here rather than calling the error listener.
            runCatching { player.release() }
            abandonFocus()
            onUnavailable()
            false
        }
    }

    /** Stops and releases the current player, if any. */
    fun stop() {
        abandonFocus()
    }

    private fun requestFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusRequest = request.takeIf { granted }
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { request ->
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        }
        focusRequest = null
    }
}
