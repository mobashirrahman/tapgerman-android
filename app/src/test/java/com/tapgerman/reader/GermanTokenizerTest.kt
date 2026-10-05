package com.tapgerman.reader

import com.tapgerman.reader.text.GermanTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanTokenizerTest {

    @Test
    fun wordsExcludePunctuationAndWhitespace() {
        val text = "Das große Haus steht am Rhein."
        val words = GermanTokenizer.words(text).map { it.text }
        assertEquals(listOf("Das", "große", "Haus", "steht", "am", "Rhein"), words)
    }

    @Test
    fun wordSpansPointBackAtTheOriginalText() {
        val text = "Die „neue“ Regierung kam."
        for (span in GermanTokenizer.words(text)) {
            assertEquals(span.text, text.substring(span.start, span.end))
        }
    }

    @Test
    fun umlautsAndSharfesSStayInsideWords() {
        val text = "Straßenzüge und Häuser"
        assertEquals(
            listOf("Straßenzüge", "und", "Häuser"),
            GermanTokenizer.words(text).map { it.text },
        )
    }

    @Test
    fun hyphenAndQuoteMarksAreNeverTappableWords() {
        // BreakIterator emits punctuation as its own segments. Whatever it decides about compounds
        // (ICU may or may not split "Bundes-Regierung"), a hyphen must never be a word on its own
        // and every span must still point at the original text.
        val text = "Bundes-Regierung und „neue“ Häuser"
        val words = GermanTokenizer.words(text)
        assertTrue(words.none { it.text == "-" || it.text.contains('„') })
        for (span in words) {
            assertEquals(span.text, text.substring(span.start, span.end))
        }
        assertTrue(words.any { it.text == "Häuser" })
        assertTrue(words.any { it.text == "Bundes" || it.text == "Bundes-Regierung" })
    }

    @Test
    fun numbersAreTappableWords() {
        assertEquals(listOf("42", "Meter"), GermanTokenizer.words("42 Meter").map { it.text })
    }

    @Test
    fun sentenceContainingReturnsTheWholeSentence() {
        val text = "Der Bundestag tagte. Die Regierung sprach danach. Später ging es weiter."
        val range = GermanTokenizer.sentenceContaining(text, text.indexOf("sprach"))
        assertTrue(range.text.startsWith("Die Regierung"))
        assertTrue(range.text.endsWith("danach."))
        assertEquals(range.text, text.substring(range.start, range.end).trim())
    }

    @Test
    fun sentenceContainingClampsOutOfRangeOffsets() {
        val text = "Nur ein Satz."
        assertEquals("Nur ein Satz.", GermanTokenizer.sentenceContaining(text, -5).text)
        assertEquals("Nur ein Satz.", GermanTokenizer.sentenceContaining(text, 999).text)
    }

    @Test
    fun emptyTextIsSafe() {
        assertTrue(GermanTokenizer.words("").isEmpty())
        assertEquals("", GermanTokenizer.sentenceContaining("", 0).text)
    }
}
