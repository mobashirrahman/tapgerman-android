package com.tapgerman.reader

import com.tapgerman.reader.anki.AnkiCards
import com.tapgerman.reader.anki.AnkiTemplates
import com.tapgerman.reader.anki.NoteIdentity
import com.tapgerman.reader.util.jsonArrayOfStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-language contract tests: every expectation comes from goldens.json, which was produced by
 * running the Chrome extension's own `extension/src/anki.js`.
 */
class NoteIdentityTest {

    private val cards = Goldens.cards()
    private val expectedStableIds = Goldens.noteObject("buildAnkiStableId")
    private val expectedSenseTags = Goldens.noteObject("buildSenseTag")

    @Test
    fun stableIdsMatchTheWebExtension() {
        for ((name, card) in cards) {
            assertEquals(
                "stable id for '$name'",
                expectedStableIds.getString(name),
                NoteIdentity.buildAnkiStableId(card),
            )
        }
    }

    @Test
    fun senseTagsMatchTheWebExtension() {
        for ((name, card) in cards) {
            assertEquals(
                "sense tag for '$name'",
                expectedSenseTags.getString(name),
                NoteIdentity.buildSenseTag(card),
            )
        }
    }

    @Test
    fun homonymsDoNotShareASenseTag() {
        val castle = cards.getValue("plain")
        val lock = cards.getValue("homonym")
        assertNotEquals(NoteIdentity.buildSenseTag(castle), NoteIdentity.buildSenseTag(lock))
    }

    @Test
    fun languageTagsAreAsciiFiltered() {
        for ((input, expected) in Goldens.textPairs("buildLanguageTag")) {
            assertEquals("language tag for '$input'", expected, NoteIdentity.buildLanguageTag(input))
        }
    }

    @Test
    fun normalizeStablePartMatchesEcmaScriptWhitespace() {
        assertEquals("häuser", NoteIdentity.normalizeStablePart("  HÄUSER  "))
        assertEquals("a b", NoteIdentity.normalizeStablePart("a\u00A0\u00A0b"))
        assertEquals("straße", NoteIdentity.normalizeStablePart("STRAßE"))
        // ES trim() removes a BOM; Java's String.trim() does not. Article text carries both.
        assertEquals("haus", NoteIdentity.normalizeStablePart(" \uFEFFHaus\uFEFF "))
        assertEquals("a b", NoteIdentity.normalizeStablePart("a\u2028\u2029b"))
    }

    @Test
    fun hashStableKeyUsesUnsignedBase36() {
        // Same fixture string hashed by the JS module; catches sign/format drift in the FNV port.
        val card = cards.getValue("plain")
        assertEquals(
            expectedStableIds.getString("plain"),
            "tapgerman-v1-" + NoteIdentity.hashStableKey(
                jsonArrayOfStrings(
                    listOf(
                        NoteIdentity.normalizeStablePart(card.languageCode),
                        NoteIdentity.normalizeStablePart(card.lemma),
                        NoteIdentity.normalizeStablePart(card.word),
                        NoteIdentity.normalizeStablePart(card.definitions?.firstOrNull() ?: ""),
                        NoteIdentity.normalizeStablePart(card.grammar),
                        NoteIdentity.normalizeStablePart(card.sentence),
                    ),
                ),
            ),
        )
    }

    @Test
    fun emptyDefinitionListDoesNotFallBackToMeaning() {
        // anki.js uses `Array.isArray(definitions) ? definitions[0] : meaning`, so an *empty* array
        // yields an empty sense rather than the meaning. A missing array falls back to `meaning`.
        val emptyList = cards.getValue("empty")
        val withMissingList = emptyList.copy(definitions = null)
        assertNotEquals(
            NoteIdentity.buildAnkiStableId(emptyList),
            NoteIdentity.buildAnkiStableId(withMissingList),
        )
        assertNotEquals(
            NoteIdentity.buildSenseTag(emptyList),
            NoteIdentity.buildSenseTag(withMissingList),
        )
    }

    @Test
    fun noteFieldOrderMatchesTheGeneratedTemplate() {
        val expectedFields = Goldens.noteObject("buildAnkiNote").getJSONObject("plain").getJSONObject("fields")
        val built = AnkiCards.buildAnkiNote(cards.getValue("plain"))
        assertEquals(AnkiTemplates.FIELDS.size, built.fields.size)
        for (name in AnkiTemplates.FIELDS) {
            assertTrue("field '$name' present", built.fields.containsKey(name))
            assertEquals("field '$name'", expectedFields.getString(name), built.fields.getValue(name))
        }
    }

    @Test
    fun notesMatchTheWebExtension() {
        val expectedNotes = Goldens.noteObject("buildAnkiNote")
        for ((name, card) in cards) {
            val expected = expectedNotes.getJSONObject(name)
            val built = AnkiCards.buildAnkiNote(card)

            assertEquals("deck for '$name'", expected.getString("deckName"), built.deckName)
            assertEquals("model for '$name'", expected.getString("modelName"), built.modelName)

            val expectedFields = expected.getJSONObject("fields")
            for (field in AnkiTemplates.FIELDS) {
                assertEquals(
                    "$name/$field",
                    expectedFields.getString(field),
                    built.fields.getValue(field),
                )
            }

            val expectedTags = expected.getJSONArray("tags")
            val tags = built.tags
            assertEquals("tag count for '$name'", expectedTags.length(), tags.size)
            for (index in 0 until expectedTags.length()) {
                assertEquals("$name tag $index", expectedTags.getString(index), tags[index])
            }
        }
    }

    @Test
    fun noteWithAudioKeepsTheSoundTag() {
        val expected = Goldens.noteObject("buildAnkiNoteAudio").getJSONObject("fields")
        val built = AnkiCards.buildAnkiNote(cards.getValue("plain"), "TapGerman", "[sound:x.mp3]")
        assertEquals(expected.getString("Audio"), built.fields.getValue("Audio"))
    }

    @Test
    fun templatesMatchTheWebExtension() {
        val constants = Goldens.noteObject("constants")
        assertEquals(constants.getString("ANKI_MODEL"), AnkiTemplates.MODEL_NAME)
        assertEquals(constants.getString("ANKI_CARD_TEMPLATE"), AnkiTemplates.CARD_TEMPLATE_NAME)
        assertEquals(
            constants.getJSONArray("ANKI_FIELDS").let { a -> (0 until a.length()).map { a.getString(it) } },
            AnkiTemplates.FIELDS,
        )
        assertEquals(constants.getString("MODEL_CSS"), AnkiTemplates.MODEL_CSS)
        assertEquals(constants.getString("FRONT_TEMPLATE"), AnkiTemplates.FRONT_TEMPLATE)
        assertEquals(constants.getString("BACK_TEMPLATE"), AnkiTemplates.BACK_TEMPLATE)
    }

    @Test
    fun lemmaIsOnlyShownWhenItDiffers() {
        val withLemma = AnkiCards.buildAnkiNote(cards.getValue("plain")).fields.getValue("Lemma")
        val withoutLemma = AnkiCards.buildAnkiNote(cards.getValue("noLemma")).fields.getValue("Lemma")
        assertEquals("Haus", withLemma)
        assertEquals("", withoutLemma)
    }

    @Test
    fun senseTagIgnoresSurfaceForm() {
        // The same sense reached through different surfaces keeps one tag, which is what lets a
        // repeat save merge instead of forking the card.
        val a = cards.getValue("plain").copy(word = "Haus", lemma = "Haus")
        val b = cards.getValue("plain")
        assertEquals(NoteIdentity.buildSenseTag(a), NoteIdentity.buildSenseTag(b))
    }

    @Test
    fun stableIdIsSensitiveToSentence() {
        val a = cards.getValue("plain")
        val b = a.copy(sentence = "Ein anderes Haus.")
        assertNotEquals(NoteIdentity.buildAnkiStableId(a), NoteIdentity.buildAnkiStableId(b))
    }

    @Test
    fun posTagStripsNonAsciiLetters() {
        assertEquals("noun", NoteIdentity.buildPosTag("Noun"))
        assertEquals("nomen", NoteIdentity.buildPosTag("Nomen,"))
        assertEquals("", NoteIdentity.buildPosTag(null))
        assertEquals("", NoteIdentity.buildPosTag("  "))
    }
}
