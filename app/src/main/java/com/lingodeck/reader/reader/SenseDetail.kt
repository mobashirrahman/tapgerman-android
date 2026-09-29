package com.lingodeck.reader.reader

import com.lingodeck.reader.data.Example
import com.lingodeck.reader.data.InflectedForm
import com.lingodeck.reader.data.LookupCardBuilder
import com.lingodeck.reader.ui.LookupUi

/**
 * The detail behind each sense: grammatical tags and worked examples with translations.
 *
 * The redesigned lookup card showed the headword, the part of speech, the IPA and the English
 * glosses, and stopped there. Everything else Kaikki sends was parsed and then dropped on the
 * floor: `Sense.tags` (gender, number, transitivity, verb class, register) and `Sense.examples`
 * (a German usage sentence *and* its English translation). For "sagen" that is the difference
 * between "to say (to pronounce; communicate verbally)" on its own, and a sense that also says it
 * is a *weak transitive* verb and shows "Ich habe nicht verstanden, was sie gesagt hat." against
 * "I didn't understand what she said."
 *
 * This lives apart from `data/LookupCardBuilder` deliberately. That object decides what goes on
 * the *Anki card* and is ported from the browser extension and byte-asserted against
 * `goldens.json`, so widening a card is a parity change and out of bounds. This decides only what
 * the *app* shows, so it can be as detailed as a phone screen holds.
 *
 * Wiktionary repeats a gloss across entries that differ in their tags, so the detail shown is the
 * union over every entry carrying that gloss. Two senses that read identically in English but
 * differ in gender are genuinely different to a learner, and this is where that becomes visible.
 */
data class SenseDetail(
    val gloss: String,
    val tags: List<String>,
    val examples: List<Example>,
    /**
     * The inflection table of the entries carrying this sense, when they have one.
     *
     * Carried per sense rather than looked up in the card because the tags and the examples are
     * gathered the same way — from every entry that carries this gloss — and a conjugation that came
     * from somewhere else would be the one thing on the card not describing the sense on screen.
     * Empty for everything that is not a verb.
     */
    val forms: List<InflectedForm> = emptyList(),
) {
    /** Whether there is anything worth drawing beyond the gloss line itself. */
    val hasDetail: Boolean get() = tags.isNotEmpty() || examples.isNotEmpty()

    /**
     * The tags as one readable line: "feminine · transitive · weak".
     *
     * A property rather than a top-level formatter because a `String` extension and a plain
     * function of the same name compile to the same JVM signature and clash.
     */
    val tagsLabel: String get() = tags.joinToString(" · ", transform = ::readableTag)
}

/**
 * The tags worth showing, in the order worth showing them.
 *
 * Kaikki's raw tag list is long and machine-shaped — "table-tags", "form-of", "obsolete" and
 * dozens more that mean nothing to a reader. These are the ones that change how a German word
 * behaves. Anything unrecognised is dropped rather than shown: a card reading "class-7
 * ditransitive weak" tells a learner nothing, while "ditransitive · weak" does.
 */
private val INTERESTING_TAGS = listOf(
    // Gender and number, for nouns.
    "feminine", "masculine", "neuter", "common",
    "plural", "singular", "plural-only", "uncountable", "diminutive",
    // Transitivity, for verbs.
    "transitive", "intransitive", "ditransitive", "auxiliary", "modal",
    // Verb class, which is what a German learner needs in order to conjugate.
    "strong", "weak",
    "class-1", "class-2", "class-3", "class-4", "class-5",
    "class-6", "class-7", "class-8", "class-9",
    // Kind and register.
    "proper-noun", "formal", "informal", "colloquial", "slang", "vulgar",
    "obsolete", "rare",
)

/** Wiktionary's own name is not always a reader's word. */
private fun readableTag(tag: String): String = when {
    tag == "proper-noun" -> "proper noun"
    tag == "plural-only" -> "plural only"
    tag.startsWith("class-") -> "class " + tag.removePrefix("class-")
    else -> tag
}

/**
 * Detail for every gloss the card is offering, in the same order as
 * [com.lingodeck.reader.ui.LookupUi.glosses], so the two can be zipped positionally.
 */
fun senseDetails(lookup: LookupUi): List<SenseDetail> {
    val result = lookup.result ?: return emptyList()
    val entries = LookupCardBuilder.displayEntries(result)
    val senses = entries.flatMap { it.definitions }
    return lookup.glosses.map { gloss ->
        val matching = senses.filter { it.gloss == gloss }
        SenseDetail(
            gloss = gloss,
            tags = matching.flatMap { it.tags }
                .filter { INTERESTING_TAGS.contains(it) }
                .distinct()
                .sortedBy { INTERESTING_TAGS.indexOf(it) },
            // Two examples per sense is the cap KaikkiParser already applies, so there is nothing
            // more to fetch here; duplicates across entries are dropped.
            examples = matching.flatMap { it.examples }.distinctBy { it.text },
            // Deduplicated because a gloss is usually repeated across the verb's several entries,
            // each carrying the same table; and only the first table that has rows, because merging
            // two of them would produce a grid with every cell filled twice.
            forms = entries
                .filter { entry -> entry.definitions.any { it.gloss == gloss } }
                .firstOrNull { entry -> entry.forms.isNotEmpty() }
                ?.forms
                .orEmpty(),
        )
    }
}
