package com.tapgerman.reader

import com.tapgerman.reader.anki.AnkiCards
import com.tapgerman.reader.anki.AnkiSaver
import com.tapgerman.reader.anki.TsvExport
import com.tapgerman.reader.data.VocabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden-fixture tests for the HTML / TSV rendering ports in anki.js and popup.js. */
class AnkiCardsTest {

    @Test
    fun escapeHtmlMatchesTheExtension() {
        for ((input, expected) in Goldens.textPairs("escapeHtml")) {
            assertEquals("escapeHtml($input)", expected, com.tapgerman.reader.util.escapeHtml(input))
        }
    }

    @Test
    fun tsvCellsMatchTheExtension() {
        for ((input, expected) in Goldens.textPairs("toAnkiTsvCell")) {
            assertEquals("toAnkiTsvCell($input)", expected, AnkiCards.toAnkiTsvCell(input))
        }
    }

    @Test
    fun tsvCellsNeutraliseSpreadsheetFormulas() {
        assertTrue(AnkiCards.toAnkiTsvCell("=1+1").startsWith("'"))
        assertTrue(AnkiCards.toAnkiTsvCell("@cmd").startsWith("'"))
        assertTrue(AnkiCards.toAnkiTsvCell("  -5").startsWith("'"))
        assertFalse(AnkiCards.toAnkiTsvCell("normal").startsWith("'"))
    }

    @Test
    fun tsvCellsTurnNewlinesIntoBreaksAndTabsIntoSpaces() {
        assertEquals("a b<br>c", AnkiCards.toAnkiTsvCell("a\tb\nc"))
        assertEquals("a<br>b", AnkiCards.toAnkiTsvCell("a\r\nb"))
    }

    @Test
    fun highlightedSentencesMatchTheExtension() {
        for (case in Goldens.highlightCases("highlightSurface")) {
            val actual = AnkiCards.highlightSurface(
                case.sentence,
                case.candidate,
                *case.fallbacks.toTypedArray(),
            )
            assertEquals(
                "highlightSurface(${case.sentence}, ${case.candidate}, ${case.fallbacks})",
                case.expected,
                actual,
            )
        }
    }

    @Test
    fun inflectedSurfaceWinsOverLemma() {
        // The first candidate that matches is used, so "Häuser" is marked up rather than "Haus".
        val html = AnkiCards.highlightSurface("Der Häuser wegen.", "Häuser", "Haus")
        assertTrue(html.contains("<span class=\"target\">Häuser</span>"))
    }

    @Test
    fun wordsInsideLongerWordsAreNotHighlighted() {
        val html = AnkiCards.highlightSurface("Die Hauskatze.", "Haus")
        assertFalse(html.contains("class=\"target\""))
        assertEquals("Die Hauskatze.", html)
    }

    @Test
    fun nonAsciiCaseInsensitiveMatchingMatchesJavaScript() {
        // JavaScript's `/i` with the `/u` flag case-folds umlauts. `Pattern.CASE_INSENSITIVE`
        // alone is US-ASCII only, so this needs `Pattern.UNICODE_CASE` too.
        assertEquals(
            "<span class=\"target\">HÄUSER</span>",
            AnkiCards.highlightSurface("HÄUSER", "Häuser"),
        )
        assertEquals(
            "<span class=\"target\">strasse</span>",
            AnkiCards.highlightSurface("strasse", "Strasse"),
        )
        // "Straße" and "STRASSE" are different strings under simple case folding in both
        // JavaScript and Java, so this must NOT match.
        assertEquals("STRASSE", AnkiCards.highlightSurface("STRASSE", "Straße"))
    }

    @Test
    fun htmlInTheSentenceIsEscapedBeforeMatching() {
        // Matching happens in escaped space, so injected markup can never become live HTML.
        val html = AnkiCards.highlightSurface("Das <b>Haus</b> & Co.", "Haus")
        assertTrue(html.contains("&lt;b&gt;"))
        assertFalse(html.contains("<b>"))
    }

    @Test
    fun fieldSegmentsStripMarkupSoDuplicatesAreDetected() {
        val a = "Das <span class=\"target\">Haus</span> ist groß."
        val b = "Das Haus ist groß."
        assertEquals(AnkiSaver.fieldSegments(a), AnkiSaver.fieldSegments(b))
    }

    @Test
    fun fieldSegmentsSplitOnTheContextSeparator() {
        val joined = "Erste Zeile." + AnkiCards.CONTEXT_SEPARATOR + "Zweite Zeile."
        assertEquals(listOf("Erste Zeile.", "Zweite Zeile."), AnkiSaver.fieldSegments(joined))
    }

    @Test
    fun tsvExportUsesTheExtensionColumns() {
        val item = VocabItem(
            id = "tapgerman-v1-abc",
            createdAt = 0L,
            word = "Häuser",
            lemma = "Haus",
            partOfSpeech = "Noun",
            ipa = "/ˈhaʊ̯zɐ/",
            audioUrl = "",
            glosses = listOf("house", "building"),
            meaning = "house",
            sentence = "Das Haus ist groß.",
            articleTitle = "Titel",
            sourceUrl = "https://example.com/a",
            source = "Titel — https://example.com/a",
        )
        val lines = TsvExport.build(listOf(item)).split("\n")
        assertEquals("#separator:Tab", lines[0])
        assertEquals("#html:true", lines[1])
        assertEquals("#columns:" + TsvExport.COLUMN_SPEC, lines[2])
        val cells = lines[3].split("\t")
        assertEquals(7, cells.size)
        assertEquals("Häuser", cells[0])
        assertEquals("house<br>building", cells[1])
        assertEquals("Das Haus ist groß.", cells[2])
        assertEquals("tapgerman language::de", cells[6])
    }
}
