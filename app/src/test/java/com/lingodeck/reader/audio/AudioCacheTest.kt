package com.lingodeck.reader.audio

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
 * The recording cache, including the eviction arithmetic.
 *
 * Nothing here touches a network or a `MediaPlayer`; [DictionaryAudio] is tested separately with a
 * fake fetcher. This is the part that runs on every spoken word and quietly runs out of space if
 * the bounds are wrong.
 */
class AudioCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(
        maxBytes: Long = AudioCache.DEFAULT_MAX_BYTES,
        maxFiles: Int = AudioCache.DEFAULT_MAX_FILES,
    ) = AudioCache(folder.root, maxBytes = maxBytes, maxFiles = maxFiles)

    private val bytes = ByteArray(1024) { it.toByte() }

    // ---- naming ----

    @Test
    fun `the file for a url is stable once stored`() {
        val cache = cache()
        val url = "https://upload.wikimedia.org/x/De-Haus.ogg.mp3"
        cache.put(url, bytes)
        assertEquals(cache.fileFor(url), cache.fileFor(url))
    }

    @Test
    fun `different urls get different files`() {
        val cache = cache()
        cache.put("https://example.com/a.mp3", bytes)
        cache.put("https://example.com/b.mp3", bytes)
        assertEquals(2, cache.count())
        assertNotNull(cache.get("https://example.com/a.mp3"))
        assertNotNull(cache.get("https://example.com/b.mp3"))
    }

    @Test
    fun `the extension survives so a file manager can tell what it is`() {
        val cache = cache()
        cache.put("https://example.com/x/De-Haus.ogg.mp3", bytes)
        cache.put("https://example.com/y/De-Wasser.ogg", bytes)
        assertTrue(cache.get("https://example.com/x/De-Haus.ogg.mp3")!!.name.endsWith(".mp3"))
        assertTrue(cache.get("https://example.com/y/De-Wasser.ogg")!!.name.endsWith(".ogg"))
    }

    @Test
    fun `a query string does not make a second copy`() {
        // Wikimedia transcoded paths put the filename twice and sometimes add a cache-busting
        // query. One recording, one file — the space bounds exist to protect is not worth
        // spending on two spellings of the same audio.
        val cache = cache()
        cache.put("https://example.com/De-Haus.ogg.mp3", bytes)
        cache.put("https://example.com/De-Haus.ogg.mp3?x=1", bytes)
        assertEquals(1, cache.count())
        assertNotNull(cache.get("https://example.com/De-Haus.ogg.mp3?x=1"))
    }

    @Test
    fun `an unknown extension is not appended`() {
        val cache = cache()
        cache.put("https://example.com/audio.php?id=7", bytes)
        val file = cache.get("https://example.com/audio.php?id=7")
        assertNotNull(file)
        assertFalse(file!!.name.contains("php"))
    }

    // ---- reading and writing ----

    @Test
    fun `a stored recording is found again`() {
        val cache = cache()
        val url = "https://example.com/a.mp3"
        cache.put(url, bytes)
        assertNotNull(cache.get(url))
    }

    @Test
    fun `an uncached url is absent`() {
        assertNull(cache().get("https://example.com/nope.mp3"))
    }

    @Test
    fun `a blank url is absent without touching the disk`() {
        assertNull(cache().get(""))
        assertNull(cache().get("   "))
    }

    @Test
    fun `an empty file is not treated as a recording`() {
        // A download cut off mid-flight leaves a zero-length file. Cached, that is a speaker
        // button that is confidently silent.
        val cache = cache()
        val url = "https://example.com/truncated.mp3"
        cache.put(url, bytes)
        cache.fileFor(url)!!.writeBytes(ByteArray(0))
        assertNull(cache.get(url))
    }

    @Test
    fun `nothing is written for an empty body`() {
        val cache = cache()
        val url = "https://example.com/empty.mp3"
        cache.put(url, ByteArray(0))
        assertNull(cache.get(url))
    }

    @Test
    fun `an implausibly large body is refused`() {
        val cache = cache()
        val url = "https://example.com/huge.mp3"
        cache.put(url, ByteArray(AudioCache.MAX_FILE_BYTES + 1))
        assertNull(cache.get(url))
    }

    @Test
    fun `writing again replaces the recording`() {
        val cache = cache()
        val url = "https://example.com/a.mp3"
        cache.put(url, bytes)
        cache.put(url, bytes)
        assertEquals(1, cache.count())
    }

    @Test
    fun `size and count reflect what is held`() {
        val cache = cache()
        repeat(3) { cache.put("https://example.com/$it.mp3", bytes) }
        assertEquals(3, cache.count())
        assertEquals(3L * 1024, cache.sizeBytes())
    }

    // ---- eviction ----

    @Test
    fun `eviction respects the byte bound`() {
        val cache = cache(maxBytes = 5 * 1024, maxFiles = 100)
        repeat(10) { cache.put("https://example.com/$it.mp3", bytes) }
        cache.evict()

        assertTrue("held ${cache.sizeBytes()} bytes", cache.sizeBytes() <= 5 * 1024)
        // A bound that never fills the disk is not a bound, so the cache should still be useful.
        assertTrue("evicted everything", cache.count() >= 4)
    }

    @Test
    fun `eviction respects the count bound`() {
        val cache = cache(maxBytes = Long.MAX_VALUE, maxFiles = 6)
        repeat(20) { cache.put("https://example.com/$it.mp3", bytes) }
        cache.evict()

        assertTrue("held ${cache.count()}", cache.count() <= 6)
    }

    @Test
    fun `eviction keeps the most recently stored`() {
        val cache = cache(maxBytes = 4 * 1024, maxFiles = 100)
        val oldest = "https://example.com/oldest.mp3"
        cache.put(oldest, bytes)
        repeat(6) { cache.put("https://example.com/filler$it.mp3", bytes) }
        val newest = "https://example.com/newest.mp3"
        cache.put(newest, bytes)
        cache.evict()

        assertNull("the oldest recording should be gone", cache.get(oldest))
        assertNotNull("the newest recording should have survived", cache.get(newest))
    }

    @Test
    fun `playing a recording again protects it from the next eviction`() {
        val cache = cache(maxBytes = 4 * 1024, maxFiles = 100)
        val popular = "https://example.com/popular.mp3"
        cache.put(popular, bytes)
        repeat(6) { cache.put("https://example.com/x$it.mp3", bytes) }

        // Standing in for playing it. The point is that a word heard often is the one to keep, and
        // that the recency is explicit rather than a timestamp: writing six files in a row can
        // easily land inside one filesystem tick, which is what made the mtime ordering arbitrary.
        cache.touch(cache.get(popular)!!)
        cache.evict()

        assertNotNull("a word heard again should outlive newer ones", cache.get(popular))
    }

    @Test
    fun `eviction leaves the directory alone when within bounds`() {
        val cache = cache(maxBytes = 1024 * 1024, maxFiles = 100)
        repeat(3) { cache.put("https://example.com/$it.mp3", bytes) }
        cache.evict()
        assertEquals(3, cache.count())
    }

    @Test
    fun `clearing empties the cache`() {
        val cache = cache()
        repeat(3) { cache.put("https://example.com/$it.mp3", bytes) }
        cache.clear()
        assertEquals(0, cache.count())
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `the directory is created on demand`() {
        val target = File(folder.root, "nested/audio")
        assertFalse(target.exists())
        AudioCache(target)
        assertTrue(target.isDirectory)
    }
}
