package com.lingodeck.reader

import com.lingodeck.reader.data.DictEntry
import com.lingodeck.reader.data.DictResult
import com.lingodeck.reader.data.Example
import com.lingodeck.reader.data.Sense
import com.lingodeck.reader.reader.SenseDetail
import com.lingodeck.reader.reader.senseDetails
import com.lingodeck.reader.ui.LookupUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detail behind each sense: tags and worked examples.
 *
 * These exist because the lookup card was fetching all of this from Kaikki and throwing it away.
 * The rules worth pinning are the two filters — which tags survive, and which examples — plus the
 * alignment between a gloss and its detail, since the card zips the two by position and a
 * mismatch would show one sense's gender against another's examples.
 *
 * The fixtures here are taken from real Kaikki responses, because the tag vocabulary is
 * machine-shaped and inventing plausible-looking tags would not have caught a filtering bug.
 */
class SenseDetailTest {

    private val entry = DictEntry(
        word = "sagen",
        partOfSpeech = "verb",
        head = "sagen",
        ipa = "ˈzaːɡn̩",
        audioUrl = "",
        formOf = "",
        definitions = listOf(
            Sense(
                gloss = "to say (to pronounce; communicate verbally)",
                // The real payload mixes machine tags in with the useful ones.
                tags = listOf("table-tags", "transitive", "weak", "form-of"),
                examples = listOf(
                    Example(
                        text = "Ich habe nicht verstanden, was sie gesagt hat.",
                        translation = "I didn't understand what she said.",
                    ),
                ),
            ),
            Sense(
                gloss = "to tell (to inform someone verbally)",
                tags = listOf("ditransitive", "weak", "table-tags"),
                examples = listOf(
                    Example(
                        text = "Sie hat mir gesagt, dass sie später kommt.",
                        translation = "She told me that she would be late.",
                    ),
                ),
            ),
        ),
    )

    private fun lookup(vararg glosses: String) = LookupUi(
        word = "sagen",
        sentence = "Er sagte, dass es regnet.",
        paragraphIndex = 0,
        start = 3,
        end = 8,
        result = DictResult(
            word = "sagen",
            language = "German",
            languageCode = "de",
            entries = listOf(entry),
        ),
        glosses = glosses.toList(),
    )

    // --- alignment -----------------------------------------------------------------------

    @Test
    fun detailLinesUpWithTheOfferedGlosses() {
        // The card zips `glosses` and `senseDetails` by index, so this is the property that keeps a
        // sense's examples from appearing under the wrong sense.
        val details = senseDetails(lookup("to say (to pronounce; communicate verbally)", "to tell (to inform someone verbally)"))
        assertEquals(2, details.size)
        assertEquals("to say (to pronounce; communicate verbally)", details[0].gloss)
        assertEquals("to tell (to inform someone verbally)", details[1].gloss)
    }

    @Test
    fun aGlossWithNoMatchingSenseYieldsEmptyDetailRatherThanShifting() {
        val details = senseDetails(lookup("a gloss that is not in the entry"))
        assertEquals(1, details.size)
        assertEquals("a gloss that is not in the entry", details[0].gloss)
        assertTrue(details[0].tags.isEmpty())
        assertTrue(details[0].examples.isEmpty())
    }

    @Test
    fun noResultMeansNoDetail() {
        val bare = lookup("anything").copy(result = null)
        assertTrue(senseDetails(bare).isEmpty())
    }

    // --- tags ----------------------------------------------------------------------------

    @Test
    fun machineTagsAreFilteredOut() {
        val details = senseDetails(lookup("to say (to pronounce; communicate verbally)"))
        // "table-tags" and "form-of" are what Kaikki emits for every sense; showing them would
        // put noise on every row.
        assertEquals(listOf("transitive", "weak"), details[0].tags)
    }

    @Test
    fun tagsComeOutInTheOrderThatIsUseful() {
        val details = senseDetails(lookup("to say (to pronounce; communicate verbally)"))
        // Transitivity before verb class, because it is the broader fact.
        assertEquals("transitive · weak", details[0].tagsLabel)
    }

    @Test
    fun aSenseWithOnlyNoiseStillRendersItsGloss() {
        val noisy = entry.copy(
            definitions = listOf(
                Sense("meaning", listOf("table-tags", "obsolete-form", "form-of"), emptyList()),
            ),
        )
        val result = lookup("meaning").copy(
            result = DictResult("sagen", "German", "de", listOf(noisy), "de"),
        )
        val details = senseDetails(result)
        assertEquals(1, details.size)
        assertEquals("", details[0].tagsLabel)
    }

    @Test
    fun theSameGlossAcrossEntriesUnionsItsTagsAndExamples() {
        // Wiktionary repeats a gloss across entries that differ, and for a learner a shared gloss
        // with two genders is two different words. Taking only the first entry would lose half.
        val doubled = entry.copy(
            definitions = entry.definitions + entry.definitions.map { sense ->
                sense.copy(tags = sense.tags + "feminine")
            },
        )
        val result = lookup("to say (to pronounce; communicate verbally)").copy(
            result = DictResult("sagen", "German", "de", listOf(doubled), "de"),
        )
        val tags = senseDetails(result).first().tags
        assertTrue("feminine" in tags)
        assertTrue("transitive" in tags)
    }

    @Test
    fun duplicateExamplesAcrossEntriesAreCollapsed() {
        val doubled = entry.copy(definitions = entry.definitions + entry.definitions)
        val result = lookup("to say (to pronounce; communicate verbally)").copy(
            result = DictResult("sagen", "German", "de", listOf(doubled), "de"),
        )
        assertEquals(1, senseDetails(result).first().examples.size)
    }

    // --- examples ------------------------------------------------------------------------

    @Test
    fun examplesCarryBothLanguages() {
        val detail = senseDetails(lookup("to say (to pronounce; communicate verbally)")).first()
        val example = detail.examples.single()
        assertEquals("Ich habe nicht verstanden, was sie gesagt hat.", example.text)
        assertEquals("I didn't understand what she said.", example.translation)
    }

    @Test
    fun aSenseWithNoExamplesIsNotAnError() {
        val noExamples = entry.copy(definitions = listOf(Sense("bare gloss", listOf("weak"), emptyList())))
        val result = lookup("bare gloss").copy(
            result = DictResult("sagen", "German", "de", listOf(noExamples), "de"),
        )
        val detail = senseDetails(result).first()
        assertTrue(detail.examples.isEmpty())
        assertTrue(detail.hasDetail) // still has tags
    }

    @Test
    fun aSenseWithNeitherIsMarkedAsHavingNoDetail() {
        val bare = entry.copy(definitions = listOf(Sense("bare", emptyList(), emptyList())))
        val result = lookup("bare").copy(
            result = DictResult("sagen", "German", "de", listOf(bare), "de"),
        )
        assertTrue(!senseDetails(result).first().hasDetail)
    }

    @Test
    fun tagLabelsAreReadable() {
        // The rank order is applied by `senseDetails`, so the tags arrive pre-ordered here and
        // `tagsLabel` only has to render them. This asserts the labels, not the ordering.
        val detail = SenseDetail(
            gloss = "g",
            tags = listOf("transitive", "class-7", "proper-noun"),
            examples = emptyList(),
        )
        assertEquals("transitive · class 7 · proper noun", detail.tagsLabel)
    }

    @Test
    fun properNounAndVerbClassSurviveTheFilter() {
        // Both were in the real Griechenland payload, and a learner meeting a country name does
        // want to know it is neuter and a proper noun.
        val country = DictEntry(
            word = "Griechenland", partOfSpeech = "name", head = "", ipa = "", audioUrl = "",
            formOf = "",
            definitions = listOf(
                Sense(
                    gloss = "Greece (a country in Southeastern Europe)",
                    tags = listOf("neuter", "proper-noun", "table-tags"),
                    examples = emptyList(),
                ),
            ),
        )
        val result = lookup("Greece (a country in Southeastern Europe)").copy(
            result = DictResult(
                "Griechenland", "German", "de", listOf(country), "de",
                lemma = "Griechenland",
            ),
        )
        assertEquals("neuter · proper noun", senseDetails(result).first().tagsLabel)
    }
}
