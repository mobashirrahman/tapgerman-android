package com.tapgerman.reader

import com.tapgerman.reader.anki.AnkiCards
import com.tapgerman.reader.anki.NoteIdentity
import com.tapgerman.reader.data.DictEntry
import com.tapgerman.reader.data.DictResult
import com.tapgerman.reader.data.Example
import com.tapgerman.reader.data.LookupCardBuilder
import com.tapgerman.reader.data.Sense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the card-selection rules ported from `extension/content.js` `renderDictionary` /
 * `state.lookupCard` / `withChosenSense`. This is the layer that decides *which* glosses a saved
 * card carries, which in turn decides its Anki identity.
 */
class LookupCardBuilderTest {

    private fun entry(
        word: String,
        pos: String = "noun",
        head: String = "",
        ipa: String = "",
        audioUrl: String = "",
        formOf: String = "",
        glosses: List<String>,
    ) = DictEntry(
        word = word,
        partOfSpeech = pos,
        head = head,
        ipa = ipa,
        audioUrl = audioUrl,
        formOf = formOf,
        definitions = glosses.map { Sense(it, emptyList(), emptyList<Example>()) },
    )

    private fun result(
        word: String = "Haus",
        lemma: String? = null,
        entries: List<DictEntry> = emptyList(),
        lemmaEntries: List<DictEntry> = emptyList(),
    ) = DictResult(
        word = word,
        language = "German",
        languageCode = "de",
        entries = entries,
        lemma = lemma,
        lemmaEntries = lemmaEntries,
    )

    @Test
    fun duplicateGlossesAcrossEntriesAreOfferedOnce() {
        // Wiktionary repeats glosses across entries. Two identical strings share one sense tag and
        // one Anki card, so a second identical row is a choice that cannot change anything.
        val r = result(
            word = "sagte",
            lemma = "sagen",
            entries = listOf(
                entry("sagen", "verb", glosses = listOf("to say", "to tell (to inform verbally)")),
                entry("sagen", "verb", glosses = listOf("to tell (to inform verbally)")),
            ),
        )
        assertEquals(listOf("to say", "to tell (to inform verbally)"), LookupCardBuilder.glosses(r))
    }

    @Test
    fun lemmaEntriesWinOverSurfaceEntriesForGlosses() {
        // The German inflection case: "höchste" only has "inflection of hoch:"; the meanings live
        // on the lemma. This is the single most important rule in the builder.
        val r = result(
            word = "höchste",
            lemma = "hoch",
            entries = listOf(entry("höchste", "adj", formOf = "hoch", glosses = listOf("inflection of hoch:"))),
            lemmaEntries = listOf(entry("hoch", "adj", glosses = listOf("high", "tall"))),
        )
        assertEquals(listOf("high", "tall"), LookupCardBuilder.glosses(r))
    }

    @Test
    fun emptyLemmaEntriesFallBackToTheSurfaceGlosses() {
        // The lemma fetch can fail. In that case the surface glosses are all there is, and
        // discarding them for an empty lemma list would leave the card with no meaning at all.
        val r = result(
            word = "höchste",
            lemma = "hoch",
            entries = listOf(entry("höchste", "adj", formOf = "hoch", glosses = listOf("inflection of hoch:"))),
            lemmaEntries = emptyList(),
        )
        assertEquals(listOf("inflection of hoch:"), LookupCardBuilder.glosses(r))
    }

    @Test
    fun surfaceEntriesAreUsedWhenThereIsNoLemma() {
        val r = result(entries = listOf(entry("Haus", glosses = listOf("house", "building"))))
        assertEquals(listOf("house", "building"), LookupCardBuilder.glosses(r))
    }

    @Test
    fun glossesAreCappedAtWhatAcardCanActuallyCarry() {
        // The picker must not offer a sense the card would have to drop: `AnkiCards.buildMeaning`
        // keeps at most 4, and the lookup card is sized for a short list, not a scroll of 16.
        val entries = (1..5).map { n ->
            entry("w$n", glosses = listOf("$n-a", "$n-b", "$n-c", "$n-d", "$n-e"))
        }
        val glosses = LookupCardBuilder.glosses(result(entries = entries))
        assertEquals(LookupCardBuilder.MAX_SENSES, glosses.size)
        assertEquals(listOf("1-a", "1-b", "1-c", "1-d"), glosses)
    }

    @Test
    fun pronunciationIsSearchedAcrossEntriesThenLemma() {
        // IPA on the surface entry, audio only on the lemma: both must be recovered.
        val r = result(
            word = "höchste",
            lemma = "hoch",
            entries = listOf(entry("höchste", ipa = "/ˈhøːçstə/", glosses = listOf("inflection of hoch:"))),
            lemmaEntries = listOf(entry("hoch", audioUrl = "https://example.com/hoch.mp3", glosses = listOf("high"))),
        )
        val (ipa, audio) = LookupCardBuilder.pronunciation(r)
        assertEquals("/ˈhøːçstə/", ipa)
        assertEquals("https://example.com/hoch.mp3", audio)
    }

    @Test
    fun grammarIsPosAndHeadJoined() {
        val r = result(
            entries = listOf(
                entry("Haus", "noun", head = "Haus n (strong)", glosses = listOf("house")),
            ),
        )
        val card = LookupCardBuilder.build(r, null, "Ein Satz.")
        assertEquals("noun · Haus n (strong)", card.grammar)
        assertEquals("noun", card.partOfSpeech)
    }

    @Test
    fun chosenGlossNarrowsTheCardToOneSense() {
        // Mirrors `withChosenSense()`. Two senses must therefore become two distinct Anki cards.
        val r = result(entries = listOf(entry("Schloss", glosses = listOf("castle", "lock"))))
        val castle = LookupCardBuilder.build(r, "castle", "Das Schloss leuchtet.")
        val lock = LookupCardBuilder.build(r, "lock", "Das Schloss klemmt.")

        assertEquals(listOf("castle"), castle.definitions)
        assertEquals(listOf("lock"), lock.definitions)
        assertTrue(
            "different senses must not share a sense tag",
            NoteIdentity.buildSenseTag(castle) != NoteIdentity.buildSenseTag(lock),
        )
    }

    @Test
    fun withoutAChosenSenseTheCardKeepsEveryGloss() {
        val r = result(entries = listOf(entry("Schloss", glosses = listOf("castle", "lock"))))
        assertEquals(listOf("castle", "lock"), LookupCardBuilder.build(r, null, "Satz.").definitions)
    }

    @Test
    fun sameChosenSenseInDifferentSentencesSharesASenseTag() {
        // This is what lets a repeat save grow one card instead of forking it.
        val r = result(entries = listOf(entry("Haus", glosses = listOf("house", "building"))))
        val a = LookupCardBuilder.build(r, "house", "Das Haus ist groß.")
        val b = LookupCardBuilder.build(r, "house", "Ein anderes Haus.")
        assertEquals(NoteIdentity.buildSenseTag(a), NoteIdentity.buildSenseTag(b))
        assertTrue(NoteIdentity.buildAnkiStableId(a) != NoteIdentity.buildAnkiStableId(b))
    }

    @Test
    fun surfaceFormStillDrivesTheHighlightWhenTheLemmaDrivesTheGlosses() {
        // The card's `word` is the lookup's word; the highlight falls back to the lemma, so
        // "höchste" is marked up in the sentence even though the glosses came from "hoch".
        val r = result(
            word = "höchste",
            lemma = "hoch",
            entries = listOf(entry("höchste", "adj", formOf = "hoch", glosses = listOf("inflection of hoch:"))),
            lemmaEntries = listOf(entry("hoch", "adj", glosses = listOf("high"))),
        )
        val card = LookupCardBuilder.build(r, "high", "Das höchste deutsche Gericht.")
        val html = AnkiCards.highlightSurface(card.sentence, card.word, card.lemma)
        assertTrue(html, html.contains("class=\"target\""))
    }
}
