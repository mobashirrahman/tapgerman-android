package com.tapgerman.reader

import com.tapgerman.reader.data.Article
import com.tapgerman.reader.data.VocabItem
import com.tapgerman.reader.util.ReadingStats
import com.tapgerman.reader.util.VocabFilter
import com.tapgerman.reader.util.VocabFilterEngine
import com.tapgerman.reader.util.VocabSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * The arithmetic behind the redesigned article cards and word list.
 *
 * None of this existed before the redesign, and every number the new screens put on screen comes
 * from here, which is exactly why it is worth asserting. A reading estimate that is off by a
 * factor of two is the kind of bug nobody reports and everybody notices.
 */
class ReadingStatsTest {

    private fun article(
        url: String = "https://www.spiegel.de/politik/ausgabe-1",
        paragraphs: List<String> = listOf("Erst Absatz.", "Zweiter Absatz."),
    ) = Article(url = url, title = "Titel", byline = "", paragraphs = paragraphs)

    // --- word count ---------------------------------------------------------------------

    @Test
    fun countsLetterRunsRatherThanWhitespace() {
        // Splitting on whitespace would over-count German compounds, which is most of what a
        // German news article contains.
        assertEquals(1, ReadingStats.countWords("Bundesliga"))
        assertEquals(2, ReadingStats.countWords("Die Bundesliga"))
        // Bundesliga | und | DFB | Pokal
        assertEquals(4, ReadingStats.countWords("Bundesliga und DFB-Pokal"))
        // The hyphen is a boundary, so a hyphenated compound is two words.
        assertEquals(2, ReadingStats.countWords("Bundesliga-Pokal"))
    }

    @Test
    fun countsAcrossPunctuationAndNewlines() {
        assertEquals(4, ReadingStats.countWords("Ja, aber — nein.\nTrotzdem."))
    }

    @Test
    fun countsDigitsAsWordsAndKeepsThemAttachedWhenTheyAre() {
        // A standalone number is a word.
        assertEquals(3, ReadingStats.countWords("Im Jahr 2024"))
        // But a number glued to letters stays inside the same word, which is what makes
        // "B2B" and "S9" single tokens rather than three.
        assertEquals(1, ReadingStats.countWords("B2B"))
        assertEquals(1, ReadingStats.countWords("S9"))
        assertEquals(2, ReadingStats.countWords("S9 Bahn"))
    }

    @Test
    fun emptyTextHasNoWords() {
        assertEquals(0, ReadingStats.countWords(""))
        assertEquals(0, ReadingStats.countWords("   \n\t "))
    }

    @Test
    fun countsUmlautsAndEszett() {
        assertEquals(3, ReadingStats.countWords("Größe für Straße"))
    }

    @Test
    fun wordCountSumsEveryParagraph() {
        val a = article(paragraphs = listOf("Eins zwei", "drei vier", "fünf"))
        assertEquals(5, ReadingStats.wordCount(a))
    }

    // --- reading time -------------------------------------------------------------------

    @Test
    fun readingTimeIsRoundedToMinutes() {
        // 180 words a minute, the pace of an adult reading an article they chose.
        val minutes = ReadingStats.readingMinutes(article(paragraphs = List(10) { "word ".repeat(36) }))
        assertEquals(2, minutes)
    }

    @Test
    fun shortArticleStillReadsAsOneMinute() {
        assertEquals(1, ReadingStats.readingMinutes(article(paragraphs = listOf("Kurz."))))
    }

    @Test
    fun emptyArticleIsOneMinuteNotZero() {
        assertEquals(1, ReadingStats.readingMinutes(article(paragraphs = emptyList())))
    }

    // --- progress -----------------------------------------------------------------------

    @Test
    fun progressIsZeroAtTheStartAndOneAtTheEnd() {
        val a = article(paragraphs = listOf("a", "b", "c", "d", "e"))
        assertEquals(0f, ReadingStats.progressThrough(a, 0), 0.001f)
        assertEquals(1f, ReadingStats.progressThrough(a, 4), 0.001f)
    }

    @Test
    fun progressIsClampedToTheArticle() {
        val a = article(paragraphs = listOf("a", "b", "c"))
        assertEquals(0f, ReadingStats.progressThrough(a, -5), 0.001f)
        assertEquals(1f, ReadingStats.progressThrough(a, 99), 0.001f)
    }

    @Test
    fun singleParagraphArticleIsCompleteOnceReached() {
        val a = article(paragraphs = listOf("only"))
        assertEquals(0f, ReadingStats.progressThrough(a, 0), 0.001f)
        assertEquals(1f, ReadingStats.progressThrough(a.copy(lastParagraph = 1), 0), 0.001f)
    }

    // --- domain -------------------------------------------------------------------------

    @Test
    fun domainDropsTheWwwPrefix() {
        assertEquals("spiegel.de", article(url = "https://www.spiegel.de/politik/x").domain)
        assertEquals("example.com", article(url = "http://example.com").domain)
    }

    @Test
    fun malformedUrlGivesABlankDomainRatherThanThrowing() {
        // Shared text is not always a URL; a library card must still render.
        assertEquals("", article(url = "not a url").domain)
    }

    // --- relative time ------------------------------------------------------------------

    private val now = Instant.parse("2026-09-29T12:00:00Z").toEpochMilli()

    @Test
    fun freshItemsSayNow() {
        assertEquals("now", ReadingStats.relativeTime(now - 10_000, now))
    }

    @Test
    fun minutesHoursAndDaysEachGetTheirOwnFormat() {
        assertEquals("now", ReadingStats.relativeTime(now - 30_000, now))
        assertEquals("5 min ago", ReadingStats.relativeTime(now - 5 * 60_000, now))
        assertEquals("3 h ago", ReadingStats.relativeTime(now - 3 * 3_600_000, now))
        // Four days back lands on a weekday name rather than a date.
        assertEquals("Fri", ReadingStats.relativeTime(now - 4 * 86_400_000L, now))
    }

    @Test
    fun olderItemsGetADateAndThenAYear() {
        val thisYear = Instant.parse("2026-03-14T09:00:00Z").toEpochMilli()
        val lastYear = Instant.parse("2025-11-02T09:00:00Z").toEpochMilli()
        assertEquals("14 Mar", ReadingStats.relativeTime(thisYear, now))
        assertEquals("2 Nov 2025", ReadingStats.relativeTime(lastYear, now))
    }

    @Test
    fun clockGoingBackwardsDoesNotProduceNegativeLabels() {
        assertEquals("now", ReadingStats.relativeTime(now + 60_000, now))
    }
}

/** Search, filter and sort for the word list. */
class VocabFilterEngineTest {

    private var id = 0

    private fun item(
        word: String,
        lemma: String = "",
        glosses: List<String> = emptyList(),
        meaning: String = "",
        sentence: String = "",
        articleTitle: String = "Artikel",
        createdAt: Long = 0,
        sentToAnkiAt: Long? = null,
    ) = VocabItem(
        id = "id-${id++}",
        createdAt = createdAt,
        word = word,
        lemma = lemma,
        partOfSpeech = "",
        ipa = "",
        audioUrl = "",
        glosses = glosses,
        meaning = meaning,
        sentence = sentence,
        articleTitle = articleTitle,
        sourceUrl = "https://example.com/$articleTitle",
        source = "",
        sentToAnkiAt = sentToAnkiAt,
    )

    private val items = listOf(
        item(
            word = "Schloss",
            glosses = listOf("castle"),
            sentence = "Das Schloss steht im Wald.",
            articleTitle = "Burgen",
            createdAt = 100,
            sentToAnkiAt = 1_000,
        ),
        item(
            word = "schließen",
            glosses = listOf("to close"),
            sentence = "Kannst du das Fenster schließen?",
            articleTitle = "Alltag",
            createdAt = 300,
        ),
        item(
            word = "höchste",
            lemma = "hoch",
            glosses = listOf("highest"),
            articleTitle = "Burgen",
            createdAt = 200,
            sentToAnkiAt = 2_000,
        ),
    )

    // --- search -------------------------------------------------------------------------

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(3, VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest).size)
    }

    @Test
    fun searchMatchesTheWordCaseInsensitively() {
        // "schl" reaches both the noun and the verb, in either case.
        assertEquals(2, VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, "SCHL").size)
        assertEquals(2, VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, "schl").size)
        // "schloss" is not a substring of "schließen", and must not match it.
        assertEquals(1, VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, "schloss").size)
    }

    @Test
    fun searchMatchesLemmaGlossSentenceAndArticle() {
        fun match(query: String) =
            VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, query).map { it.word }.toSet()

        // "höchste" was saved with lemma "hoch", so the search has to reach the lemma.
        assertEquals(setOf("höchste"), match("hoch"))
        // A gloss.
        assertEquals(setOf("höchste"), match("highest"))
        // The sentence the word came from.
        assertEquals(setOf("schließen"), match("Fenster"))
        // The article title.
        assertEquals(setOf("Schloss", "höchste"), match("Burgen"))
    }

    @Test
    fun searchWithNoMatchIsEmptyNotEverything() {
        // The failure mode this guards: an empty result set falling back to the full list.
        assertTrue(VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, "zzzz").isEmpty())
    }

    @Test
    fun searchIsTrimmed() {
        assertEquals(2, VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest, "  schl  ").size)
    }

    // --- filters ------------------------------------------------------------------------

    @Test
    fun notSentExcludesWordsAlreadyOnAnki() {
        val found = VocabFilterEngine.apply(items, VocabFilter.NotSent, VocabSort.Newest)
        assertEquals(listOf("schließen"), found.map { it.word })
    }

    @Test
    fun sentIncludesOnlyWordsThatReachedAnki() {
        val found = VocabFilterEngine.apply(items, VocabFilter.Sent, VocabSort.Newest)
        assertEquals(setOf("Schloss", "höchste"), found.map { it.word }.toSet())
    }

    @Test
    fun searchAndFilterCompose() {
        val found = VocabFilterEngine.apply(items, VocabFilter.Sent, VocabSort.Newest, "schloss")
        assertEquals(listOf("Schloss"), found.map { it.word })
    }

    // --- sort ---------------------------------------------------------------------------

    @Test
    fun newestSortIsMostRecentFirst() {
        val found = VocabFilterEngine.apply(items, VocabFilter.All, VocabSort.Newest)
        assertEquals(listOf("schließen", "höchste", "Schloss"), found.map { it.word })
    }

    @Test
    fun alphabeticalSortTreatsUmlautsAsBaseLetters() {
        val withUmlauts = listOf(
            item(word = "Zug", createdAt = 1),
            item(word = "Ähre", createdAt = 2),
            item(word = "Apfel", createdAt = 3),
        )
        val found = VocabFilterEngine.apply(withUmlauts, VocabFilter.All, VocabSort.Alphabetical)
        // A plain String.compareTo sorts every "Ä" after "Z", which would be useless.
        assertEquals(listOf("Ähre", "Apfel", "Zug"), found.map { it.word })
    }

    // --- grouping -----------------------------------------------------------------------

    @Test
    fun articleTitlesAreDistinctAndOrderPreserving() {
        assertEquals(listOf("Burgen", "Alltag"), VocabFilterEngine.articleTitles(items))
    }

    @Test
    fun blankArticleTitlesAreDroppedFromGrouping() {
        val mixed = listOf(item(word = "a", articleTitle = ""), item(word = "b", articleTitle = "X"))
        assertEquals(listOf("X"), VocabFilterEngine.articleTitles(mixed))
    }

    // --- the model contract the new screens rely on ---------------------------------------

    @Test
    fun onlyItemsWithStoredOffsetsCanReopenInArticle() {
        val bare = item(word = "alt")
        assertFalse(bare.canReopenInArticle)
        val located = bare.copy(paragraphIndex = 3, wordStart = 10, wordEnd = 15)
        assertTrue(located.canReopenInArticle)
        // A partial set is not enough; half an offset would scroll to the wrong place.
        assertFalse(bare.copy(paragraphIndex = 3).canReopenInArticle)
    }

    @Test
    fun articleDomainIsExposedForTheLibraryChip() {
        val a = Article(
            url = "https://www.zeit.de/politik/ausgabe-1",
            title = "T",
            byline = "",
            paragraphs = listOf("x"),
        )
        assertEquals("zeit.de", a.domain)
        assertTrue(a.textLength > 0)
    }

    @Test
    fun readingEstimateAndWordCountAreNotConfusedWithEachOther() {
        // Both are on the same card. One is a duration in minutes, the other a count of words,
        // and swapping them would be a bug neither would be caught by looking at.
        val paragraphs = List(20) { "Wort ".repeat(20) }
        val a = Article(url = "https://example.com", title = "T", byline = "", paragraphs = paragraphs)
        assertEquals(400, ReadingStats.wordCount(a))
        assertEquals(2, ReadingStats.readingMinutes(a))
    }

    @Test
    fun datesAreFormattedInUtcRatherThanTheDeviceTimezone() {
        // 23:30 UTC on 14 March. Formatted in a zone far behind UTC this would be 14 March
        // still, but in one far ahead it would already be the 15th; rendering in UTC means the
        // label cannot change when the reader crosses a timezone.
        val epoch = Instant.parse("2026-03-14T23:30:00Z").toEpochMilli()
        val now = epoch + 200L * 86_400_000L
        assertEquals("14 Mar", ReadingStats.relativeTime(epoch, now))
    }
}
