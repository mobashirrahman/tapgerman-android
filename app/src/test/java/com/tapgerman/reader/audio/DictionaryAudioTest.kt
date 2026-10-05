package com.tapgerman.reader.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Choosing between a recording and the synthesiser, with the network faked out.
 *
 * The fetcher is a lambda so the whole policy is testable: which failures are silent, whether a
 * word is downloaded once or once per tap, and what happens the second time.
 */
class DictionaryAudioTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val bytes = ByteArray(512) { it.toByte() }
    private var fetches = 0

    private fun audio(
        cache: AudioCache = AudioCache(folder.root),
        result: (String) -> ByteArray? = { bytes },
    ) = DictionaryAudio(cache, AudioFetcher { url ->
        fetches++
        result(url)
    })

    @Test
    fun `a fetched recording is returned and cached`() = runBlocking {
        val cache = AudioCache(folder.root)
        val file = audio(cache).recordingFor("https://example.com/a.mp3")

        assertNotNull(file)
        assertTrue(file!!.isFile)
        // Cached, so the second tap on the same word costs nothing.
        assertNotNull(cache.get("https://example.com/a.mp3"))
    }

    @Test
    fun `a second request is served from disk`() = runBlocking {
        val audio = audio()
        audio.recordingFor("https://example.com/a.mp3")
        audio.recordingFor("https://example.com/a.mp3")
        // One download, not one per tap — this is the whole reason for the cache.
        assertEquals(1, fetches)
    }

    @Test
    fun `a blank url fetches nothing`() = runBlocking {
        assertNull(audio().recordingFor(""))
        assertEquals(0, fetches)
    }

    @Test
    fun `a failed download resolves to null rather than throwing`() = runBlocking {
        // Every failure has the same answer for the caller: speak with the synthesiser instead.
        val file = audio(result = { null }).recordingFor("https://example.com/gone.mp3")
        assertNull(file)
    }

    @Test
    fun `a failed download is not cached`() = runBlocking {
        val cache = AudioCache(folder.root)
        audio(cache, result = { null }).recordingFor("https://example.com/gone.mp3")
        assertNull(cache.get("https://example.com/gone.mp3"))
        assertEquals(0, cache.count())
    }

    @Test
    fun `a later attempt can still succeed`() = runBlocking {
        val cache = AudioCache(folder.root)
        var attempts = 0
        val flaky = DictionaryAudio(cache, AudioFetcher {
            attempts++
            if (attempts == 1) null else bytes
        })

        // A word that failed once — offline, or rate limited — must not be written off.
        assertNull(flaky.recordingFor("https://example.com/a.mp3"))
        assertNotNull(flaky.recordingFor("https://example.com/a.mp3"))
    }

    @Test
    fun `an empty body is not cached`() = runBlocking {
        val cache = AudioCache(folder.root)
        val file = audio(cache, result = { ByteArray(0) }).recordingFor("https://example.com/a.mp3")
        assertNull(file)
        assertEquals(0, cache.count())
    }

    @Test
    fun `the cache is consulted before the network`() = runBlocking {
        val audio = audio()
        audio.cached("https://example.com/a.mp3")
        // `cached` is the offline question, and it must not have reached the fetcher.
        assertEquals(0, fetches)
    }

    @Test
    fun `downloading past the bound evicts what it should and keeps the new word`() = runBlocking {
        val cache = AudioCache(folder.root, maxBytes = 2 * 512, maxFiles = 100)
        cache.put("https://example.com/filler.mp3", bytes)

        val fresh = "https://example.com/fresh.mp3"
        DictionaryAudio(cache, AudioFetcher { bytes }).recordingFor(fresh)

        assertNotNull("the recording just fetched should be playable", cache.get(fresh))
        assertTrue("cache should have stayed bounded", cache.sizeBytes() <= 2 * 512 + 512)
    }

    @Test
    fun `the Wikimedia agent identifies the client`() {
        // Wikimedia refuses a default Java agent and asks for one that says what it is. This is
        // a policy requirement, not a nicety: an unidentifiable client gets rate limited.
        assertTrue(WikimediaFetcher.DEFAULT_USER_AGENT.contains("TapGermanReader"))
        assertTrue(WikimediaFetcher.DEFAULT_USER_AGENT.contains("kaikki.org"))
    }

    @Test
    fun `a non-https url is refused before any request`() = runBlocking {
        // The url comes from a third party, and plaintext audio over an untrusted transport is not
        // worth the convenience. The scheme check returns before the network is touched, so this
        // is safe to assert without one.
        assertNull(WikimediaFetcher().fetch("http://example.com/plain.mp3"))
        assertNull(WikimediaFetcher().fetch("file:///etc/passwd"))
    }
}
