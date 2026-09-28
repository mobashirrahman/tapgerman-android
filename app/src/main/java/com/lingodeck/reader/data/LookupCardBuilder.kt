package com.lingodeck.reader.data

import com.lingodeck.reader.anki.NoteIdentity

/**
 * Turns a dictionary lookup into the card the learner saves.
 *
 * A port of `state.lookupCard` in `extension/content.js` (`renderDictionary`). Three rules here
 * matter and are easy to get wrong:
 *
 * 1. **Glosses come from the lemma's entries when there are any.** An inflected surface like
 *    "höchste" has only "inflection of hoch:" entries; the meanings live on "hoch". Preferring
 *    `lemmaEntries` is what makes German inflection usable at all.
 * 2. **Pronunciation is searched across every entry, then the lemma's.** Kaikki splits IPA and
 *    audio across records, so reading only `entries[0]` routinely finds neither.
 * 3. **The saved card carries one sense, not all of them.** The learner picks the sense they
 *    actually met and the card is narrowed to that gloss, so Schloss-the-castle and
 *    Schloss-the-lock become two Anki cards instead of one four-gloss blur.
 */
object LookupCardBuilder {

    /**
     * The most senses a card can carry. Capped here as well as in
     * [com.lingodeck.reader.anki.AnkiCards.buildMeaning] so the picker can never offer a sense the
     * card would have to drop anyway.
     */
    const val MAX_SENSES = 4

    /**
     * Every gloss the picker should offer, in display order.
     *
     * Deduplicated because Wiktionary often repeats a gloss across entries: two identical strings
     * would hash to the same sense tag and the same card, so offering them twice only adds a
     * meaningless choice.
     */
    fun glosses(result: DictResult): List<String> =
        displayEntries(result)
            .flatMap { entry -> entry.definitions.take(MAX_SENSES).map { it.gloss } }
            .distinct()
            .take(MAX_SENSES)

    /** The entries whose senses should be shown: the lemma's when it has any. */
    fun displayEntries(result: DictResult): List<DictEntry> =
        (if (result.lemmaEntries.isNotEmpty()) result.lemmaEntries else result.entries).take(4)

    /** `ipa` to `audioUrl`, each the first entry that carries one. */
    fun pronunciation(result: DictResult): Pair<String, String> {
        val all = result.entries + result.lemmaEntries
        val ipa = all.firstOrNull { it.ipa.isNotEmpty() }?.ipa ?: ""
        val audioUrl = all.firstOrNull { it.audioUrl.isNotEmpty() }?.audioUrl ?: ""
        return ipa to audioUrl
    }

    /**
     * Builds the saved card. [chosenGloss] narrows the card to one sense, mirroring the extension's
     * `withChosenSense()`; with no choice the card keeps every gloss.
     */
    fun build(
        result: DictResult,
        chosenGloss: String?,
        sentence: String,
        translation: String = "",
    ): NoteIdentity.Card {
        val firstEntry = result.entries.firstOrNull() ?: result.lemmaEntries.firstOrNull()
        val (ipa, audioUrl) = pronunciation(result)
        val definitions = if (chosenGloss != null) listOf(chosenGloss) else glosses(result)

        return NoteIdentity.Card(
            word = result.word,
            lemma = result.lemma ?: result.word,
            definitions = definitions,
            meaning = definitions.firstOrNull().orEmpty(),
            grammar = listOfNotNull(firstEntry?.partOfSpeech, firstEntry?.head)
                .filter { it.isNotEmpty() }
                .joinToString(" · "),
            partOfSpeech = firstEntry?.partOfSpeech.orEmpty(),
            sentence = sentence,
            translation = translation,
            languageCode = result.languageCode.ifEmpty { "de" },
            ipa = ipa,
            audioUrl = audioUrl,
            source = null,
        )
    }
}
