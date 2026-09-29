package com.lingodeck.reader.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

/**
 * The translation cache, against a real directory.
 *
 * These are worth testing for one specific reason: the cache is the only thing standing between a
 * sentence being sent to a third party once or twice. If the write is dropped, every re-read of
 * the article pays again and leaks again, and nothing about the app looks broken.
 */
class TranslationCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sentence = "Ich habe nicht verstanden, was sie gesagt hat."

    @Test
    fun `an untranslated sentence is absent`() = runBlocking {
        val cache = TranslationCache(folder.root)
        assertNull(cache.get(sentence, "de", "en"))
    }

    @Test
    fun `a translation survives being written and read by a new instance`() = runBlocking {
        TranslationCache(folder.root).put(sentence, "de", "en", "I didn't understand what she said.")

        // A new instance, so this reads the file rather than anything in memory.
        assertEquals(
            "I didn't understand what she said.",
            TranslationCache(folder.root).get(sentence, "de", "en"),
        )
    }

    @Test
    fun `the same sentence in a different target language is a different entry`() = runBlocking {
        val cache = TranslationCache(folder.root)
        cache.put(sentence, "de", "en", "the English one")
        cache.put(sentence, "de", "fr", "le français")

        // A bare sentence key would return whichever was written first, which would be a wrong
        // answer presented confidently.
        assertEquals("the English one", cache.get(sentence, "de", "en"))
        assertEquals("le français", cache.get(sentence, "de", "fr"))
    }

    @Test
    fun `a different source language is a different entry`() = runBlocking {
        val cache = TranslationCache(folder.root)
        cache.put(sentence, "de", "en", "from German")
        assertNull(cache.get(sentence, "nl", "en"))
    }

    @Test
    fun `near-identical sentences do not collide`() = runBlocking {
        // Hash and length are the cache key, so this asserts two sentences that are one
        // character apart do not land on the same entry. A hash collision would be caught by the
        // second lookup returning the first sentence's translation.
        val cache = TranslationCache(folder.root)
        val shorter = "Sie hat gesagt."
        cache.put(shorter, "de", "en", "short one")
        cache.put(shorter + "!", "de", "en", "long one")

        assertEquals("short one", cache.get(shorter, "de", "en"))
        assertEquals("long one", cache.get(shorter + "!", "de", "en"))
    }

    @Test
    fun `clearing removes everything`() = runBlocking {
        val cache = TranslationCache(folder.root)
        cache.put(sentence, "de", "en", "something")
        cache.clear()
        assertNull(cache.get(sentence, "de", "en"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `the cache is bounded`() = runBlocking {
        val cache = TranslationCache(folder.root)
        // The bound exists so a long-lived install does not accumulate a second copy of article
        // prose on disk. Overflow is allowed; only the oldest entries are dropped.
        repeat(TranslationCache.MAX_ENTRIES + 40) { index ->
            cache.put("Satz Nummer $index.", "de", "en", "Sentence number $index.")
        }

        val size = cache.size()
        assertTrue("cache grew past its bound: $size", size <= TranslationCache.MAX_ENTRIES)
        assertTrue("cache should still be useful", size > 0)
    }

    @Test
    fun `the newest entry survives an overflow`() = runBlocking {
        // takeLast keeps the recent end, so the entry just written must still be readable. If the
        // order were reversed this would be the first thing to break in normal use.
        val cache = TranslationCache(folder.root)
        repeat(TranslationCache.MAX_ENTRIES + 40) { index ->
            cache.put("Satz Nummer $index.", "de", "en", "Sentence number $index.")
        }
        val newest = "Sentence number ${TranslationCache.MAX_ENTRIES + 39}."
        assertEquals(newest, cache.get("Satz Nummer ${TranslationCache.MAX_ENTRIES + 39}.", "de", "en"))
    }

    @Test
    fun `a corrupt cache file is treated as empty rather than crashing`() = runBlocking {
        folder.newFile("translations.json").writeText("{not json at all")
        val cache = TranslationCache(folder.root)

        // Opening an article must not depend on a file the app wrote itself being well formed.
        assertNull(cache.get(sentence, "de", "en"))
        assertEquals(0, cache.size())

        // And it must still be writable afterwards.
        cache.put(sentence, "de", "en", "recovered")
        assertEquals("recovered", cache.get(sentence, "de", "en"))
    }
}
