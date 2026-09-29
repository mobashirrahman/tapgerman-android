package com.glossline.reader.anki

import com.glossline.reader.util.esCollapseWhitespace
import com.glossline.reader.util.esTrim
import com.glossline.reader.util.jsonArrayOfStrings
import java.text.Normalizer

/**
 * Card identity: the part of a card that decides "is this the same card?".
 *
 * This is a line-for-line port of `extension/src/anki.js` (`normalizeStablePart`,
 * `hashStableKey`, `buildAnkiStableId`, `buildSenseTag`, `buildLanguageTag`). A word saved from
 * the Chrome extension and the same word saved from this app must produce the same StableId and
 * the same sense tag, or Anki would hold two cards for one word and "cards that grow" would merge
 * or split incorrectly.
 *
 * The FNV-1a hash is 64-bit with wraparound and is rendered in base 36 of the *unsigned* value.
 */
object NoteIdentity {

    /**
     * The JS card is a loose bag of optional strings. [definitions] is nullable to preserve one
     * quirk of the original: `Array.isArray(definitions) ? definitions[0] : meaning` means a
     * *present but empty* array contributes an empty sense, while a missing array falls back to
     * [meaning]. See [senseOf].
     */
    data class Card(
        val word: String,
        val lemma: String? = null,
        val definitions: List<String>? = null,
        val meaning: String? = null,
        val grammar: String? = null,
        val partOfSpeech: String? = null,
        val sentence: String? = null,
        val translation: String? = null,
        val languageCode: String? = null,
        val ipa: String? = null,
        val audioUrl: String? = null,
        val source: String? = null,
    )

    /** anki.js `normalizeStablePart`, with the ES whitespace set rather than Java's. */
    fun normalizeStablePart(value: String?): String =
        esCollapseWhitespace(esTrim(Normalizer.normalize(value ?: "", Normalizer.Form.NFKC)))
            .lowercase()

    /** anki.js `hashStableKey`: FNV-1a over UTF-16 code units, emitted as unsigned base 36. */
    fun hashStableKey(value: String): String {
        var hash = FNV_OFFSET_BASIS
        for (index in 0 until value.length) {
            hash = hash xor value[index].code.toLong()
            hash *= FNV_PRIME
        }
        return java.lang.Long.toUnsignedString(hash, 36)
    }

    /**
     * anki.js's sense derivation: `Array.isArray(definitions) ? definitions[0] : meaning`.
     *
     * The asymmetry is deliberate and is the real JS behaviour: a *missing* definitions array falls
     * back to [Card.meaning], but a present-yet-empty one contributes an empty sense. Collapsing the
     * two would make two unrelated senses of one word hash to the same card identity.
     */
    private fun senseOf(card: Card): String? {
        val definitions = card.definitions
        return if (definitions != null) definitions.getOrNull(0) else card.meaning
    }

    fun buildAnkiStableId(card: Card): String {
        val surface = normalizeStablePart(card.word)
        val lemma = normalizeStablePart(card.lemma.orEmpty().ifEmpty { card.word })
        val sense = normalizeStablePart(senseOf(card))
        val grammar = normalizeStablePart(card.grammar.orEmpty().ifEmpty { card.partOfSpeech })
        val context = normalizeStablePart(card.sentence)
        val language = normalizeStablePart(card.languageCode.orEmpty().ifEmpty { "unknown" })
        val identity = jsonArrayOfStrings(listOf(language, lemma, surface, sense, grammar, context))
        return "glossline-v1-" + hashStableKey(identity)
    }

    /** anki.js `normalizeSenseToken` + `buildSenseTag`. */
    fun buildSenseTag(card: Card): String {
        val token = normalizeStablePart(senseOf(card)).ifEmpty { "unknown" }
        return "sense::" + hashStableKey(token)
    }

    /** anki.js `buildLanguageTag` — `\w` in JS is ASCII `[A-Za-z0-9_]`, not Unicode. */
    fun buildLanguageTag(languageCode: String?): String {
        val raw = languageCode.orEmpty().ifEmpty { "unknown" }
        return "language::" + raw.replace(Regex("[^A-Za-z0-9_-]"), "")
    }

    /** anki.js `buildAnkiNote`'s `partOfSpeech` tag derivation. */
    fun buildPosTag(partOfSpeech: String?): String =
        (partOfSpeech ?: "").lowercase().replace(Regex("[^a-z]"), "")

    // 0xCBF29CE484222325 / 0x100000001B3 are the FNV-1a 64-bit offset basis and prime. Written as
    // an unsigned literal so the constant cannot be mis-transcribed as a negative signed value.
    private val FNV_OFFSET_BASIS = 0xCBF29CE484222325UL.toLong()
    private const val FNV_PRIME = 0x100000001B3L
}
