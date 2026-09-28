package com.lingodeck.reader.data

/**
 * Language-neutral article and dictionary shapes.
 *
 * [DictResult] and its children intentionally mirror the JSON that `parseKaikkiJsonl` returns in
 * `extension/src/dictionary.js`, so a golden fixture generated from the web extension can be
 * compared field-for-field.
 */
data class Article(
    val url: String,
    val title: String,
    val byline: String,
    val paragraphs: List<String>,
    val retrievedAt: Long = System.currentTimeMillis(),
)

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
)
