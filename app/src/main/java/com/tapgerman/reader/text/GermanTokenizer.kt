package com.tapgerman.reader.text

import java.text.BreakIterator
import java.util.Locale

data class WordSpan(val start: Int, val end: Int, val text: String)

/**
 * Word and sentence segmentation for German article text.
 *
 * The Chrome extension uses `Intl.Segmenter`, which is a Unicode word/sentence boundary algorithm.
 * `BreakIterator` with a German locale is the platform's equivalent and is what ICU backs on
 * Android, so hyphenation and umlauts behave the way the web version does.
 *
 * `BreakIterator` exposes both whitespace and punctuation as segments; only segments that begin
 * with a letter or a digit are kept as tappable words.
 */
object GermanTokenizer {
    val locale: Locale = Locale.GERMAN

    fun words(text: String, locale: Locale = GermanTokenizer.locale): List<WordSpan> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getWordInstance(locale)
        iterator.setText(text)

        val spans = ArrayList<WordSpan>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (start >= 0 && end > start && isWordStart(text[start])) {
                spans.add(WordSpan(start, end, text.substring(start, end)))
            }
            start = end
            end = iterator.next()
        }
        return spans
    }

    /**
     * The sentence containing [offset], with its bounds in the paragraph. Used to show "the exact
     * sentence the word came from" when saving a card. The returned text is trimmed while the
     * bounds describe the untrimmed run, so `text.substring(start, end).trim() == text`.
     */
    fun sentenceContaining(text: String, offset: Int, locale: Locale = GermanTokenizer.locale): TextRange {
        if (text.isEmpty()) return TextRange("", 0, 0)
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)

        val clamped = offset.coerceIn(0, text.length)
        val end = iterator.following(clamped).let { if (it == BreakIterator.DONE) text.length else it }
        val start = iterator.preceding(end).let { if (it == BreakIterator.DONE) 0 else it }
            .coerceIn(0, text.length)

        return TextRange(text.substring(start, end).trim(), start, end)
    }

    data class TextRange(val text: String, val start: Int, val end: Int)

    private fun isWordStart(ch: Char): Boolean = Character.isLetterOrDigit(ch)
}
