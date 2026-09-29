package com.lingodeck.reader.dict

import com.lingodeck.reader.data.Etymology
import com.lingodeck.reader.data.EtymologyStage
import com.lingodeck.reader.data.RelatedWords

/**
 * Reading the two parts of a Wiktionary entry that are prose rather than structure.
 *
 * `etymology_text` is a rendered template: a heading, then one line per stage of descent, and
 * sometimes a second and third chain for a word with more than one root. `antonyms`, `related` and
 * `derived` are arrays of `{word, sense}` objects, in the hundreds for a common noun.
 *
 * Both are trimmed here rather than in the card, because both are unbounded in the payload and the
 * card is a phone screen.
 */
object WordRelations {

    /**
     * How many derived terms to keep.
     *
     * Four. Enough to show that a word is productive — `abgehen`, `angehen`, `aufgehen` is a pattern
     * a learner can use — without turning the card into a word list they cannot scan. Antonyms and
     * see-also terms are kept in full because they are small and every one is worth having.
     */
    const val MAX_DERIVED = 4

    /**
     * The stage names a descent line can carry.
     *
     * Matching on a known list rather than on "text before the space" because some lines are not
     * stages at all: a heading, or the prose Wiktionary appends after the tree, which reads
     * `From Middle High German hūs, from Old High German hūs` and would otherwise be swallowed as a
     * stage called "From".
     */
    private val STAGES = listOf(
        "Proto-Indo-European",
        "Proto-Germanic",
        "Proto-West Germanic",
        "Proto-Norse",
        "Proto-Celtic",
        "Proto-Italic",
        "Proto-Slavic",
        "Indo-Iranian",
        "Old Norse",
        "Old Irish",
        "Old English",
        "Old Saxon",
        "Old High German",
        "Old Dutch",
        "Old French",
        "Old Church Slavonic",
        "Old Church Slavonic",
        "Middle High German",
        "Middle Low German",
        "Middle Dutch",
        "Middle French",
        "Middle English",
        "Early New High German",
        "New High German",
        "German",
        "English",
        "Dutch",
        "French",
        "Latin",
        "Greek",
        "Yiddish",
    ).sortedByDescending { it.length }

    /**
     * Turns `etymology_text` into chains of stages.
     *
     * The split is not on a blank line or a heading, because there is none. `gehen` runs straight
     * from `Old High German gān` into a second `Proto-Indo-European *ǵʰengʰ-der.` with nothing
     * between them, so consecutive stage lines are not one descent. The signal is a **repeated
     * language**: a line of descent moves forward through history and never visits a stage twice,
     * so the second `Proto-Indo-European` can only be a second root.
     *
     * Both chains are kept. Silently discarding half a word's ancestry is the kind of tidy-up that
     * loses information, and a card that said "gehen comes from *ǵʰeh₁-der" without mentioning the
     * other root would be making a claim the data does not support.
     */
    fun parseEtymology(text: String?): Etymology {
        if (text.isNullOrBlank()) return Etymology(emptyList())

        val chains = mutableListOf<List<EtymologyStage>>()
        var current = mutableListOf<EtymologyStage>()

        text.lineSequence().forEach { line ->
            val stage = parseStage(line)
            when {
                stage == null -> {
                    // Prose, a heading, or the sentence after the tree: the chain is over.
                    if (current.isNotEmpty()) {
                        chains.add(current)
                        current = mutableListOf()
                    }
                }
                // A language already used in this chain means a second root has started.
                current.any { it.language == stage.language } -> {
                    chains.add(current)
                    current = mutableListOf(stage)
                }
                else -> current.add(stage)
            }
        }
        if (current.isNotEmpty()) chains.add(current)

        return Etymology(chains.filter { it.isNotEmpty() })
    }

    /**
     * One line, or null when the line is not a stage.
     *
     * The form is everything after the first space, with the reconstruction markers kept: the
     * leading asterisk and the trailing `?` on a Proto-Indo-European root are the notation that says
     * this is a reconstruction rather than an attested form, and dropping them would make a guess
     * look like a document.
     */
    private fun parseStage(line: String): EtymologyStage? {
        val trimmed = line.trim().removePrefix("* ")
        STAGES.forEach { language ->
            val prefix = "$language "
            if (trimmed.startsWith(prefix)) {
                val form = trimmed.removePrefix(prefix).trim()
                if (form.isNotEmpty()) return EtymologyStage(language, form)
            }
        }
        return null
    }

    /**
     * Antonyms, see-also and the first few derived terms.
     *
     * Distinct and case-insensitive because the payload repeats a word across senses — the same
     * word listed as both a derived term and a see-also is one word.
     */
    fun relatedWords(
        antonyms: List<String>,
        related: List<String>,
        derived: List<String>,
    ): RelatedWords = RelatedWords(
        antonyms = antonyms.distinct(),
        related = related.distinct().filterNot { it in antonyms },
        derived = derived.distinct()
            .filterNot { it in antonyms || it in related }
            .take(MAX_DERIVED),
    )
}
