package com.glossline.reader.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etymology and related words, against the real `etymology_text` for `Haus` and `gehen`.
 *
 * Two of these are about refusing to be tidy. A word with two roots has two chains and dropping one
 * loses half its ancestry. And the last line of every etymology block is prose, not a stage, so a
 * parser that takes "text before the space" as a language name ends up with a stage called "From".
 */
class WordRelationsTest {

    private val haus = WordRelations.parseEtymology(KaikkiFixtures.HAUS_ETYMOLOGY)
    private val gehen = WordRelations.parseEtymology(KaikkiFixtures.GEHEN_ETYMOLOGY)

    @Test
    fun `Haus has one chain of descent`() {
        assertEquals(1, haus.chains.size)
        assertEquals(
            listOf(
                "Proto-Indo-European" to "*(s)kewH-der.?",
                "Proto-Germanic" to "*hūsą",
                "Proto-West Germanic" to "*hūs",
                "Old High German" to "hūs",
                "Middle High German" to "hūs",
                "German" to "Haus",
            ),
            haus.primary.map { it.language to it.form },
        )
    }

    @Test
    fun `a reconstruction keeps its markers`() {
        // The asterisk and the question mark are the notation that says this is a reconstruction
        // rather than an attested form. Dropping them would make a guess look like a document.
        assertEquals("*(s)kewH-der.?", haus.primary.first().form)
    }

    @Test
    fun `the heading and the trailing prose are not stages`() {
        val languages = haus.primary.map { it.language }
        assertFalse("the heading leaked in", "Etymology" in languages)
        // `From Middle High German hūs, from Old High German hūs, ...` is the sentence after the
        // tree. A parser taking the first word as a language would invent a stage called "From".
        assertFalse(languages.any { it.firstOrNull()?.isUpperCase() == false })
    }

    @Test
    fun `a word with two roots keeps both chains`() {
        // gehen descends from both *ǵʰeh₁-der and *ǵʰengʰ-der. Discarding the second would be a
        // quiet loss of half the word's history.
        assertEquals(2, gehen.chains.size)
        assertTrue(gehen.chains.any { it.any { stage -> stage.form.contains("ǵʰeh₁") } })
        assertTrue(gehen.chains.any { it.any { stage -> stage.form.contains("ǵʰengʰ") } })
    }

    @Test
    fun `the second chain runs all the way to German`() {
        // The first stops at Old High German, because the payload stops there. Only the later chain
        // reaches the modern word, so this is the one a card would show.
        assertEquals("German", gehen.chains.last().last().language)
    }

    @Test
    fun `an entry with no etymology has none`() {
        assertTrue(WordRelations.parseEtymology(null).isEmpty)
        assertTrue(WordRelations.parseEtymology("").isEmpty)
        assertTrue(WordRelations.parseEtymology("   ").isEmpty)
    }

    @Test
    fun `a block of nothing but prose yields no chains`() {
        val prose = WordRelations.parseEtymology("From Old English, borrowed from Latin.")
        assertTrue(prose.isEmpty)
    }

    // ---- related words ----

    @Test
    fun `antonyms come through`() {
        val words = WordRelations.relatedWords(
            antonyms = listOf("kommen", "rennen"),
            related = emptyList(),
            derived = emptyList(),
        )
        assertEquals(listOf("kommen", "rennen"), words.antonyms)
    }

    @Test
    fun `a word in two categories is listed once, in the first`() {
        // `gehen` is listed as both a see-also and, for some senses, among the derived terms.
        val words = WordRelations.relatedWords(
            antonyms = listOf("kommen"),
            related = listOf("gehts", "fahren"),
            derived = listOf("gehts", "abgehen", "kommen"),
        )
        assertEquals(listOf("kommen"), words.antonyms)
        assertEquals(listOf("gehts", "fahren"), words.related)
        assertEquals(listOf("abgehen"), words.derived)
    }

    @Test
    fun `derived terms are capped`() {
        // `Haus` has 407. Four is enough to show the word is productive — abgehen, angehen,
        // aufgeben is a pattern — without turning the card into a list nobody scans.
        val many = (1..500).map { "wort$it" }
        val words = WordRelations.relatedWords(emptyList(), emptyList(), many)
        assertEquals(WordRelations.MAX_DERIVED, words.derived.size)
        assertEquals("wort1", words.derived.first())
    }

    @Test
    fun `a word with no relations is empty`() {
        assertTrue(WordRelations.relatedWords(emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
