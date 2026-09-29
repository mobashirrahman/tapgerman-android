package com.lingodeck.reader.dict

import com.lingodeck.reader.data.DictEntry
import com.lingodeck.reader.data.DictResult

/**
 * Folds a lemma lookup back into the result for a surface form.
 *
 * A German article is mostly inflected words. Tapping "sagte" gets a payload whose every gloss
 * is "inflection of sagen: first/third-person singular preterite" — true, and useless, because
 * the meaning lives on "sagen", which is a second request. So the surface result has to be
 * *replaced* by the lemma's entries before anything reads it, not merely annotated with the
 * lemma's name.
 *
 * This is a separate function because getting it wrong is silent. Both halves of the result stay
 * internally consistent when the lemma entries are dropped: `entries` still has the form's
 * glosses, `lemma` is still set, and the card still renders — it just renders the definition of
 * the word's own inflection, which looks like correct-but-thin data rather than a bug. It was a
 * bug, for every conjugated verb and inflected noun in the language.
 */
object LemmaResolver {

    /** The lemma named by a surface form's senses, or null when the word is not an inflection. */
    fun lemmaFor(result: DictResult): String? =
        result.entries
            .mapNotNull { entry -> entry.formOf.takeIf { form -> form.isNotEmpty() } }
            .firstOrNull()

    /**
     * Returns [result] with its lemma name and the lemma's own entries attached.
     *
     * The name is attached even when [lemmaEntries] is empty, because the card's metadata line
     * still benefits from naming the lemma; the entries are what carry the meanings, so without
     * them the display correctly falls back to the form.
     */
    fun resolve(result: DictResult, lemmaEntries: List<DictEntry>): DictResult {
        val lemma = lemmaFor(result) ?: return result
        return result.copy(lemma = lemma, lemmaEntries = lemmaEntries)
    }
}
