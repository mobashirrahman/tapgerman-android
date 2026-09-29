package com.lingodeck.reader.util

import com.lingodeck.reader.data.VocabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The article filter that shipped as a control which did nothing.
 *
 * `VocabFilter.ByArticle` had a label, a chip and a branch in the engine that returned the search
 * unchanged. Nothing failed and nothing looked broken; picking it just did not change the list.
 * These pin the behaviour it was supposed to have.
 */
class ArticleFilterTest {

    @Test
    fun `by article narrows to the chosen title`() {
        val items = listOf(
            item(word = "sagte", article = "Koalition einig"),
            item(word = "Haus", article = "Ein Haus am See"),
            item(word = "gehen", article = "Koalition einig"),
        )

        val visible = VocabFilterEngine.apply(
            items = items,
            filter = VocabFilter.ByArticle,
            sort = VocabSort.Newest,
            article = "Koalition einig",
        )

        assertEquals(setOf("sagte", "gehen"), visible.map { it.word }.toSet())
    }

    @Test
    fun `an unchosen article shows everything rather than nothing`() {
        val items = listOf(
            item(word = "sagte", article = "Koalition einig"),
            item(word = "Haus", article = "Ein Haus am See"),
        )

        // A filter that can only show an empty list until a second control is also used is not a
        // filter, so no selection means no narrowing.
        assertEquals(2, VocabFilterEngine.apply(items, VocabFilter.ByArticle, VocabSort.Newest).size)
    }

    @Test
    fun `the article choice is ignored by the other filters`() {
        val items = listOf(
            item(word = "sagte", article = "Koalition einig"),
            item(word = "Haus", article = "Ein Haus am See", sent = true),
        )

        val all = VocabFilterEngine.apply(
            items, VocabFilter.All, VocabSort.Newest, article = "Koalition einig",
        )
        val notSent = VocabFilterEngine.apply(
            items, VocabFilter.NotSent, VocabSort.Newest, article = "Koalition einig",
        )

        assertEquals(2, all.size)
        assertEquals(listOf("sagte"), notSent.map { it.word })
    }

    @Test
    fun `the article filter composes with the search field`() {
        val items = listOf(
            item(word = "sagte", article = "Koalition einig"),
            item(word = "Haus", article = "Ein Haus am See"),
        )

        val visible = VocabFilterEngine.apply(
            items = items,
            filter = VocabFilter.ByArticle,
            sort = VocabSort.Newest,
            query = "haus",
            article = "Koalition einig",
        )

        assertTrue(visible.isEmpty())
    }

    @Test
    fun `dictionary lookups with no article are not lost to the filter`() {
        // A word looked up in the dictionary has no article title, so it must not be swept up by
        // an article grouping, and must not break the title list.
        val items = listOf(
            item(word = "sagte", article = "Koalition einig"),
            item(word = "Haus", article = ""),
        )

        assertEquals(listOf("Koalition einig"), VocabFilterEngine.articleTitles(items))
    }

    @Test
    fun `article titles keep the order the words were saved in`() {
        val items = listOf(
            item(word = "erste", article = "B"),
            item(word = "zweite", article = "A"),
            item(word = "dritte", article = "B"),
        )

        assertEquals(listOf("B", "A"), VocabFilterEngine.articleTitles(items))
    }

    private fun item(
        word: String,
        article: String = "",
        sent: Boolean = false,
    ) = VocabItem(
        id = "id-$word",
        createdAt = 0,
        word = word,
        lemma = word,
        partOfSpeech = "",
        ipa = "",
        audioUrl = "",
        glosses = emptyList(),
        meaning = "",
        sentence = "",
        articleTitle = article,
        sourceUrl = "",
        source = "",
        sentToAnkiAt = if (sent) 1L else null,
    )
}
