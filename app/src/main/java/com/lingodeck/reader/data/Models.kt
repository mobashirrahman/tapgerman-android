package com.lingodeck.reader.data

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

data class DictEntry(
    val word: String,
    val partOfSpeech: String,
    val head: String,
    val ipa: String,
    val audioUrl: String,
    val formOf: String,
    val definitions: List<Sense>,
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
 * `definitions.join("\n") || meaning` cell; [com.lingodeck.reader.anki.AnkiCards] builds the HTML
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
