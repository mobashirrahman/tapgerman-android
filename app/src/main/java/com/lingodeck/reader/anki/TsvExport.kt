package com.lingodeck.reader.anki

import com.lingodeck.reader.data.VocabItem

/**
 * Anki-ready TSV export.
 *
 * Matches `exportVocabulary()` in the extension's `extension/popup.js`: the `#separator` /
 * `#html` / `#columns` preamble Anki's importer understands, then one tab-separated row per
 * saved word with every cell passed through `toAnkiTsvCell`.
 */
object TsvExport {
    const val COLUMN_SPEC = "Word\tMeaning\tSentence\tTranslation\tGrammar\tSource\tTags"

    private val header = listOf("#separator:Tab", "#html:true", "#columns:$COLUMN_SPEC")

    fun build(items: List<VocabItem>): String {
        val rows = items.map { item ->
            listOf(
                item.word,
                item.glosses.joinToString("\n").ifEmpty { item.meaning },
                item.sentence,
                "",
                item.partOfSpeech,
                item.source,
                "lingodeck language::${item.languageCode}",
            ).joinToString("\t") { AnkiCards.toAnkiTsvCell(it) }
        }
        return (header + rows).joinToString("\n")
    }
}
