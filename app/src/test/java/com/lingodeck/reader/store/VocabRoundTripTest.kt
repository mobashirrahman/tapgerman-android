package com.lingodeck.reader.store

import com.lingodeck.reader.data.VocabItem
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A word that survives being written to disk and read back unchanged.
 *
 * Every one of these assertions failed at some point, in a way that looked fine on screen. The
 * `sentToAnkiAt` one is the reason this file exists: a null written as `JSONObject.NULL` was read
 * back with `has`, which is true for that key, and `optLong`, which answers a null with `0`. So
 * every word the app had ever saved came back claiming to have been sent to Anki at the epoch,
 * was badged "On Anki" in the word list, was hidden by the "Not on Anki" filter, and inflated the
 * "Sent to Anki" count in Settings. Nothing looked broken; the word list was just quietly lying
 * about everything on it.
 */
class VocabRoundTripTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun item(
        word: String = "Haus",
        sentence: String = "In dem Haus haben wir mal gewohnt.",
        articleTitle: String = "Ein Haus am See",
        sourceUrl: String = "https://example.com/haus",
        paragraphIndex: Int? = 3,
        wordStart: Int? = 7,
        wordEnd: Int? = 11,
        sentToAnkiAt: Long? = null,
    ) = VocabItem(
        id = "id-$word",
        createdAt = 1_700_000_000_000,
        word = word,
        lemma = word,
        partOfSpeech = "noun",
        ipa = "[haʊ̯s]",
        audioUrl = "https://example.com/haus.mp3",
        glosses = listOf("house, building"),
        meaning = "house, building",
        sentence = sentence,
        articleTitle = articleTitle,
        sourceUrl = sourceUrl,
        source = "Ein Haus am See — https://example.com/haus",
        paragraphIndex = paragraphIndex,
        wordStart = wordStart,
        wordEnd = wordEnd,
        sentToAnkiAt = sentToAnkiAt,
    )

    private fun roundTrip(vararg items: VocabItem): List<VocabItem> {
        val store = Store(folder.root)
        items.forEach { store.saveVocab(it) }
        return Store(folder.root).listVocab()
    }

    @Test
    fun `a word saved without Anki still reads back as not sent`() {
        val read = roundTrip(item(sentToAnkiAt = null)).single()
        assertNull("read back as $read", read.sentToAnkiAt)
        assertFalse(read.sentToAnkiAt != null)
    }

    @Test
    fun `a word that was sent to Anki keeps its timestamp`() {
        val read = roundTrip(item(sentToAnkiAt = 1_700_000_500_000)).single()
        assertEquals(1_700_000_500_000L, read.sentToAnkiAt)
    }

    @Test
    fun `a word with no article reads back with no position`() {
        // The shape a dictionary lookup produces. With the old read these came back as paragraph
        // 0 of an empty article, and the word list offered to reopen a word that was never in one.
        val read = roundTrip(
            item(
                sentence = "",
                articleTitle = "",
                sourceUrl = "",
                paragraphIndex = null,
                wordStart = null,
                wordEnd = null,
            ),
        ).single()

        assertNull(read.paragraphIndex)
        assertNull(read.wordStart)
        assertNull(read.wordEnd)
        assertFalse(read.canReopenInArticle)
    }

    @Test
    fun `a word with a position keeps it`() {
        val read = roundTrip(item(paragraphIndex = 3, wordStart = 7, wordEnd = 11)).single()
        assertEquals(3, read.paragraphIndex)
        assertEquals(7, read.wordStart)
        assertEquals(11, read.wordEnd)
        assertTrue(read.canReopenInArticle)
    }

    @Test
    fun `zero is not confused with absent`() {
        // Paragraph 0 is a real paragraph, so a reader that treats 0 as "no article" would hide
        // words tapped in the first paragraph. The mirror image of the bug above.
        val read = roundTrip(
            item(paragraphIndex = 0, wordStart = 0, wordEnd = 4),
        ).single()
        assertEquals(0, read.paragraphIndex)
        assertEquals(0, read.wordStart)
        assertEquals(4, read.wordEnd)
    }

    @Test
    fun `every field survives`() {
        val original = item()
        val read = roundTrip(original).single()
        assertEquals(original, read)
    }

    @Test
    fun `the written file stores a real null rather than a zero`() {
        // Parsed rather than string-matched, so this does not depend on the writer's indentation.
        // The bug was in reading these values back, so the file has to be inspected directly.
        val store = Store(folder.root)
        store.saveVocab(
            item(
                sentToAnkiAt = null,
                paragraphIndex = null,
                wordStart = null,
                wordEnd = null,
            ),
        )
        val written = JSONArray(folder.root.resolve("vocab.json").readText()).getJSONObject(0)

        assertTrue("sentToAnkiAt was $written", written.isNull("sentToAnkiAt"))
        assertTrue("paragraphIndex was $written", written.isNull("paragraphIndex"))
        assertTrue("wordStart was $written", written.isNull("wordStart"))
    }

    @Test
    fun `items written by an older build still read`() {
        // Every field absent rather than null, which is what a build from before these fields
        // existed wrote. Reading them must not throw and must not invent values.
        folder.newFile("vocab.json").writeText(
            JSONArray().put(
                org.json.JSONObject()
                    .put("id", "legacy")
                    .put("createdAt", 1_700_000_000_000)
                    .put("word", "sagte")
                    .put("lemma", "sagen")
                    .put("meaning", "to say")
                    .put("sentence", "Er sagte etwas."),
            ).toString(),
        )

        val read = Store(folder.root).listVocab().single()
        assertEquals("sagte", read.word)
        assertNull(read.sentToAnkiAt)
        assertNull(read.paragraphIndex)
        assertFalse(read.canReopenInArticle)
    }
}
