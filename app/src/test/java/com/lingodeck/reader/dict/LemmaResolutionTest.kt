package com.lingodeck.reader.dict

import com.lingodeck.reader.data.DictResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conjugated-verb path, against the two payloads the app actually receives.
 *
 * Both are real kaikki.org responses, trimmed to the fields the parser reads: the record for
 * "sagte", and the record for "sagen" that it points at through `sense.form_of`.
 *
 * This exists because a device check found the lemma lookup coming back empty, which left the
 * card showing only "inflection of sagen: first/third-person singular preterite". A conjugated
 * verb is the single most common shape of tapped word in a German article, and resolving it to
 * a restatement of its own inflection is a definition that tells a reader nothing.
 */
class LemmaResolutionTest {

    @Test
    fun `a conjugated verb names its lemma`() {
        val result = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGTE, "sagte", "de")!!
        val lemma = result.entries.firstNotNullOfOrNull { it.formOf.takeIf(String::isNotEmpty) }
        assertEquals("sagen", lemma)
    }

    @Test
    fun `the form itself carries no meaning`() {
        val form = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGTE, "sagte", "de")!!
        val glosses = form.entries.flatMap { entry -> entry.definitions.map { it.gloss } }
        assertTrue("glosses=$glosses", glosses.all { it.startsWith("inflection of") })
    }

    @Test
    fun `the lemma entry carries the meanings the form does not`() {
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!
        val glosses = lemma.entries.flatMap { entry -> entry.definitions.map { it.gloss } }
        assertTrue("glosses=$glosses", glosses.any { it.startsWith("to say") })
    }

    @Test
    fun `the lemma entry carries the grammatical tags`() {
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!
        val tags = lemma.entries.flatMap { entry -> entry.definitions.flatMap { it.tags } }
        assertTrue("tags=$tags", tags.containsAll(listOf("transitive", "weak")))
    }

    @Test
    fun `the lemma wins for display, and the form is only a fallback`() {
        val form = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGTE, "sagte", "de")!!
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!

        val shownWithLemma = displayEntries(form.copy(lemmaEntries = lemma.entries))
        val shownWithout = displayEntries(form)

        // The whole point: with the lemma available the card says "to say", not "inflection of".
        assertTrue(
            "shown=${shownWithLemma.flatMap { it.definitions.map { d -> d.gloss } }}",
            shownWithLemma.flatMap { it.definitions }.any { it.gloss.startsWith("to say") },
        )
        // And with no lemma available the form is still better than nothing.
        assertTrue(
            shownWithout.flatMap { it.definitions }.all { it.gloss.startsWith("inflection of") },
        )
    }

    // ---- the wiring that was actually broken -----------------------------------------

    @Test
    fun `resolve folds the lemma entries into the result the card reads`() {
        val form = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGTE, "sagte", "de")!!
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!

        val resolved = LemmaResolver.resolve(form, lemma.entries)

        // The lemma name, for the card's metadata line.
        assertEquals("sagen", resolved.lemma)
        // The lemma's entries, which is what the senses are read from. This is the assertion
        // that would have caught the bug: `entries` alone is always internally consistent, so a
        // dropped `lemmaEntries` produces a card that renders and says nothing useful.
        assertEquals(lemma.entries, resolved.lemmaEntries)
        assertTrue(
            resolved.entries.flatMap { it.definitions }.all { it.gloss.startsWith("inflection of") },
        )
        // And the combination now shows a meaning.
        val glosses = com.lingodeck.reader.data.LookupCardBuilder.glosses(resolved)
        assertTrue("glosses=$glosses", glosses.any { it.startsWith("to say") })
    }

    @Test
    fun `resolve names the lemma even when its entries could not be fetched`() {
        val form = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGTE, "sagte", "de")!!
        val resolved = LemmaResolver.resolve(form, lemmaEntries = emptyList())

        // Better to name the lemma and fall back to the form than to lose the name as well.
        assertEquals("sagen", resolved.lemma)
        assertTrue(resolved.lemmaEntries.isEmpty())
    }

    @Test
    fun `resolve leaves an uninflected word untouched`() {
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!
        val resolved = LemmaResolver.resolve(lemma, lemmaEntries = emptyList())

        assertEquals(null, resolved.lemma)
        assertEquals(lemma.entries, resolved.entries)
    }

    @Test
    fun `lemmaFor finds nothing on an uninflected word`() {
        val lemma = KaikkiParser.parseKaikkiJsonl(KaikkiFixtures.SAGEN, "sagen", "de")!!
        assertEquals(null, LemmaResolver.lemmaFor(lemma))
    }

    /** Mirrors `LookupCardBuilder.displayEntries`, which the card renders through. */
    private fun displayEntries(result: DictResult) =
        (if (result.lemmaEntries.isNotEmpty()) result.lemmaEntries else result.entries).take(4)
}
