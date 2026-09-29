package com.glossline.reader

import com.glossline.reader.dict.KaikkiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden-fixture tests for the Kaikki URL builder and JSONL parser port. */
class KaikkiParserTest {

    @Test
    fun lookupWordsAreNormalizedLikeTheExtension() {
        for ((input, expected) in Goldens.pairs("normalizeLookupWord")) {
            assertEquals("normalize($input)", expected, KaikkiParser.normalizeLookupWord(input))
        }
    }

    @Test
    fun kaikkiUrlsMatchTheExtension() {
        for ((word, language, expected) in Goldens.urlCases("buildKaikkiUrl")) {
            assertEquals("url($word, $language)", expected, KaikkiParser.buildKaikkiUrl(word, language))
        }
    }

    @Test
    fun leadingCharactersAreSplitByCodePoint() {
        // "ö" is one code point, so the first and first-two buckets are both "ö".
        assertEquals(
            "https://kaikki.org/dictionary/German/meaning/%C3%B6/%C3%B6/%C3%B6.jsonl",
            KaikkiParser.buildKaikkiUrl("ö", "de"),
        )
    }

    @Test
    fun apostrophesAreNotOverEncoded() {
        // encodeURIComponent leaves `'` alone; java.net.URLEncoder would emit %27 and break the URL.
        assertEquals(
            "https://kaikki.org/dictionary/German/meaning/i/it/it's-ok.jsonl",
            KaikkiParser.buildKaikkiUrl("it's-ok", "de"),
        )
    }

    @Test
    fun parsedEntriesMatchTheExtension() {
        val sample = Goldens.string("kaikkiSample")
        val expected = Goldens.parseResult("parseKaikkiJsonl")
        val actual = KaikkiParser.parseKaikkiJsonl(sample, "Haus", "de")

        assertNotNull(actual)
        val parsed = requireNotNull(actual)
        assertEquals(expected.word, parsed.word)
        assertEquals(expected.language, parsed.language)
        assertEquals(expected.languageCode, parsed.languageCode)
        assertEquals(expected.sourceName, parsed.sourceName)
        assertEquals(expected.license, parsed.license)
        assertEquals(expected.entries.size, parsed.entries.size)

        expected.entries.forEachIndexed { index, golden ->
            val entry = parsed.entries[index]
            assertEquals("entry $index word", golden.word, entry.word)
            assertEquals("entry $index pos", golden.partOfSpeech, entry.partOfSpeech)
            assertEquals("entry $index head", golden.head, entry.head)
            assertEquals("entry $index ipa", golden.ipa, entry.ipa)
            assertEquals("entry $index audio", golden.audioUrl, entry.audioUrl)
            assertEquals("entry $index formOf", golden.formOf, entry.formOf)
            assertEquals("entry $index gloss count", golden.glosses.size, entry.definitions.size)
            golden.glosses.forEachIndexed { senseIndex, gloss ->
                val sense = entry.definitions[senseIndex]
                assertEquals("entry $index sense $senseIndex", gloss, sense.gloss)
                assertEquals("entry $index sense $senseIndex tags", golden.tagRows[senseIndex], sense.tags)
            }
        }
    }

    @Test
    fun glosslessSensesAreDroppedBeforeTheCapIsApplied() {
        // The fixture record with `glosses: []` must disappear entirely rather than consume one of
        // the six sense slots — that ordering is what the JS module does (`filter` then `slice`).
        val sample = Goldens.string("kaikkiSample")
        val parsed = requireNotNull(KaikkiParser.parseKaikkiJsonl(sample, "Haus", "de"))
        parsed.entries.forEach { entry ->
            entry.definitions.forEach { sense ->
                assertTrue("gloss must not be blank", sense.gloss.isNotBlank())
            }
        }
    }

    @Test
    fun emptyAndUnparseablePayloadsReturnNull() {
        assertNull(KaikkiParser.parseKaikkiJsonl("", "Haus", "de"))
        assertNull(KaikkiParser.parseKaikkiJsonl("{nope}\n", "Haus", "de"))
    }

    @Test
    fun examplesAreTruncatedToTwoBeforeEmptyOnesAreDropped() {
        // Three examples, the first two blank. Only two are ever looked at (JS `slice(0, 2)` runs
        // before the non-empty filter), so the third must not surface.
        val line =
            """{"word":"x","pos":"noun","senses":[{"glosses":["g"],"examples":[{"text":""},{"text":""},{"text":"kept"}]}]}"""
        val parsed = KaikkiParser.parseKaikkiJsonl(line, "x", "de")
        assertNotNull(parsed)
        assertTrue(requireNotNull(parsed).entries.first().definitions.first().examples.isEmpty())
    }

    @Test
    fun unknownLanguagesProduceNoUrl() {
        assertNull(KaikkiParser.buildKaikkiUrl("word", "xx"))
    }
}
