package com.tapgerman.reader.data

/**
 * Language-neutral article and dictionary shapes.
 *
 * [DictResult] and its children intentionally mirror the JSON that `parseKaikkiJsonl` returns in
 * `extension/src/dictionary.js`, so a golden fixture generated from the web extension can be
 * compared field-for-field.
 */

/** Light, dark, or follow the system. User-selectable; the pre-redesign app only followed the system. */
enum class ThemeMode { System, Light, Dark }

data class Article(
    val url: String,
    val title: String,
    val byline: String,
    val paragraphs: List<String>,
    val retrievedAt: Long = System.currentTimeMillis(),
    /**
     * Where the reader got to last time, so the library can show how far through it was and the
     * reader can offer to resume. Additive and optional: an article stored before this field
     * existed reads back as 0.
     */
    val lastParagraph: Int = 0,
) {
    /** The host, for the domain chip. Blank rather than throwing on a malformed share. */
    val domain: String
        get() = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrDefault("")

    /** Characters of body text, which is what a reading-time estimate needs. */
    val textLength: Int
        get() = paragraphs.sumOf { it.length }
}

data class Example(
    val text: String,
    val translation: String,
)

data class Sense(
    val gloss: String,
    val tags: List<String>,
    val examples: List<Example>,
)

/**
 * One inflected form of a word, with the grammatical labels Kaikki gives it.
 *
 * For a German verb these are the cells of a conjugation table: `geht` is
 * `third-person / present / singular`, `ging` is `preterite`, and the tags are what make the grid
 * reconstructable. Kept as raw tags rather than a parsed table because the model mirrors the
 * payload — the table is built in [com.tapgerman.reader.dict.Conjugation] — and because a tag the
 * builder does not recognise is still worth carrying.
 */
data class InflectedForm(
    val form: String,
    val tags: List<String> = emptyList(),
    /** `conjugation` for a verb table, `declension` for a noun table, absent for a loose form. */
    val source: String? = null,
)

/**
 * One step of a word's history: a language stage and the form the word took in it.
 *
 * German Wiktionary sends this as a prose block, one stage per line —
 * `Old High German hūs` — and [Etymology] pulls those lines back apart.
 */
data class EtymologyStage(val language: String, val form: String)

/**
 * A word's descent, as one or more chains of stages.
 *
 * A "tree" is not always a line: `gehen` has two independent ancestors, one through *ǵʰeh₁-der
 * and one through *ǵʰengʰ-der, because German has two roots for going. They are separate chains
 * rather than one merged graph, which is all this shape can hold and all the payload really offers.
 */
data class Etymology(val chains: List<List<EtymologyStage>>) {
    val isEmpty: Boolean get() = chains.isEmpty()

    /** The first chain, which is the line of descent a reader almost always wants. */
    val primary: List<EtymologyStage> get() = chains.firstOrNull().orEmpty()
}

/**
 * Words Wiktionary lists against this one, grouped by the relationship.
 *
 * Both lists are long in the raw payload — 407 derived terms for `Haus`, 241 hyponyms — so the
 * counts here are what the *card* shows and the parser has already trimmed. The relationship is
 * kept because "comes from" and "means the opposite of" teach different things, and a flat list of
 * related words throws that away.
 */
data class RelatedWords(
    val antonyms: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val derived: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = antonyms.isEmpty() && related.isEmpty() && derived.isEmpty()
}

data class DictEntry(
    val word: String,
    val partOfSpeech: String,
    val head: String,
    val ipa: String,
    val audioUrl: String,
    val formOf: String,
    val definitions: List<Sense>,
    /**
     * Inflected forms, kept only where Kaikki attributes them to an inflection table.
     *
     * The unfiltered array is noise for this purpose: for `Haus` it is one genitive, one plural and
     * twenty-eight diminutives, none of which is a declension case. Filtering on `source` at parse
     * time keeps a noun's table separate from its derived words and keeps a verb's from carrying
     * the adjective declension that shares the headword.
     */
    val forms: List<InflectedForm> = emptyList(),
    /** The word's descent, when the entry has an etymology section. */
    val etymology: Etymology = Etymology(emptyList()),
    /** Antonyms, see-also and a short list of derived terms. */
    val related: RelatedWords = RelatedWords(),
)

data class DictResult(
    val word: String,
    val language: String,
    val languageCode: String,
    val entries: List<DictEntry>,
    val sourceName: String = "Kaikki / English Wiktionary",
    val sourceUrl: String? = null,
    val license: String = "CC BY-SA 4.0",
    /** Set only when the surface form was an inflection whose lemma was fetched separately. */
    val lemma: String? = null,
    val lemmaEntries: List<DictEntry> = emptyList(),
)

/**
 * A saved vocabulary item: the word plus the sentence it came from.
 *
 * [glosses] and [meaning] are kept raw (not HTML) so TSV export can reproduce the web extension's
 * `definitions.join("\n") || meaning` cell; [com.tapgerman.reader.anki.AnkiCards] builds the HTML
 * form when a card is created.
 */
data class VocabItem(
    val id: String,
    val createdAt: Long,
    val word: String,
    val lemma: String,
    val partOfSpeech: String,
    val ipa: String,
    val audioUrl: String,
    val glosses: List<String>,
    val meaning: String,
    val sentence: String,
    val articleTitle: String,
    val sourceUrl: String,
    val source: String,
    val languageCode: String = "de",
    // The fields below are all additive, all read back with a null or zero default, and none of
    // them feed the Anki note. `id` in particular is the note's StableId, so it is derived in
    // anki/VocabMapping.kt and must never be touched here.
    //
    // Where in the source article the word was tapped, so the word list can reopen the article
    // at that exact word. Null for items saved before this existed, and the UI degrades to
    // "open the article" rather than "open the word" when it is null.
    val paragraphIndex: Int? = null,
    val wordStart: Int? = null,
    val wordEnd: Int? = null,
    /** When this word was sent to AnkiDroid, which is what the "sent" filter and stat count. */
    val sentToAnkiAt: Long? = null,
) {
    /** Whether the stored offsets are usable for jumping straight to this word. */
    val canReopenInArticle: Boolean
        get() = paragraphIndex != null && wordStart != null && wordEnd != null
}
