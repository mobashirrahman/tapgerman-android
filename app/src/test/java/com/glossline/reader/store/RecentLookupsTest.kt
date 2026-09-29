package com.glossline.reader.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The dictionary's recent-lookup list.
 *
 * Kept apart from the vocabulary list deliberately, and the tests are here to hold that line: the
 * two are different acts, and merging them would mean a reader who checked a verb twenty times had
 * collected twenty words.
 */
class RecentLookupsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = Store(folder.root)

    @Test
    fun `a fresh store has no recent lookups`() {
        assertTrue(store().listRecentLookups().isEmpty())
    }

    @Test
    fun `a lookup is remembered`() {
        val store = store()
        store.rememberLookup("Entlastungen")
        assertEquals(listOf("Entlastungen"), store.listRecentLookups())
    }

    @Test
    fun `most recent comes first`() {
        val store = store()
        store.rememberLookup("erste")
        store.rememberLookup("zweite")
        store.rememberLookup("dritte")
        assertEquals(listOf("dritte", "zweite", "erste"), store.listRecentLookups())
    }

    @Test
    fun `looking the same word up again moves it up rather than repeating it`() {
        val store = store()
        store.rememberLookup("Haus")
        store.rememberLookup("Baum")
        store.rememberLookup("Haus")

        // Three entries with "Haus" twice would show a duplicate row that does the same thing.
        assertEquals(listOf("Haus", "Baum"), store.listRecentLookups())
    }

    @Test
    fun `re-lookup ignores case`() {
        val store = store()
        store.rememberLookup("Haus")
        store.rememberLookup("haus")
        assertEquals(1, store.listRecentLookups().size)
    }

    @Test
    fun `whitespace is trimmed`() {
        val store = store()
        store.rememberLookup("  Haus  ")
        assertEquals(listOf("Haus"), store.listRecentLookups())
    }

    @Test
    fun `a blank lookup is not remembered`() {
        val store = store()
        store.rememberLookup("   ")
        assertTrue(store().listRecentLookups().isEmpty())
    }

    @Test
    fun `the list is bounded and keeps the recent end`() {
        val store = store()
        repeat(40) { store.rememberLookup("Wort $it") }

        val recent = store.listRecentLookups()
        assertEquals(24, recent.size)
        // "Wort 39" was written last, so it is the first thing a reader sees. If the cap kept the
        // wrong end the list would open on words from an hour ago.
        assertEquals("Wort 39", recent.first())
    }

    @Test
    fun `recent lookups survive a new store over the same directory`() {
        store().rememberLookup("Haus")
        assertEquals(listOf("Haus"), store().listRecentLookups())
    }

    @Test
    fun `clearing empties the list`() {
        val store = store()
        store.rememberLookup("Haus")
        store.clearRecentLookups()
        assertTrue(store.listRecentLookups().isEmpty())
    }

    @Test
    fun `a corrupt file reads as empty rather than crashing`() {
        folder.newFile("recent-lookups.json").writeText("{not json")
        val store = store()

        // Opening the dictionary must not depend on a file the app wrote itself.
        assertTrue(store.listRecentLookups().isEmpty())

        // And it must still be writable afterwards.
        store.rememberLookup("Haus")
        assertEquals(listOf("Haus"), store.listRecentLookups())
    }
}
