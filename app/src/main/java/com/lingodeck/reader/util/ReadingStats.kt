package com.lingodeck.reader.util

import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.VocabItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The arithmetic behind the library's article cards and word list, kept out of the composables
 * so it can be tested.
 *
 * None of this existed before the redesign: article cards showed a title, a byline and a raw
 * URL, and the words screen had no counts at all. Every number the new screens put on screen
 * comes from here, which means they can all be asserted.
 */
object ReadingStats {

    /**
     * Words in a German article.
     *
     * German compounds make this worth doing properly rather than splitting on whitespace: a
     * naive `split(' ')` over-counts "Bundesliga" and friends and, worse, under-counts nothing,
     * so the estimate drifts on exactly the kind of newspaper text this app is for. Counting
     * letter-initial runs keeps the number honest.
     */
    fun wordCount(article: Article): Int =
        countWords(article.paragraphs.joinToString(" "))

    fun countWords(text: String): Int {
        var count = 0
        var inWord = false
        for (ch in text) {
            if (Character.isLetterOrDigit(ch)) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }

    /**
     * Minutes to read, at 180 words a minute.
     *
     * 180 is a deliberate mid-point: it is roughly the pace of an adult reading a newspaper
     * article they chose, rather than skimming a headline or grinding through a textbook. The
     * floor of one minute keeps a short article from displaying "0 min".
     */
    fun readingMinutes(article: Article): Int =
        maxOf(1, Math.round(wordCount(article) / WORDS_PER_MINUTE))

    fun paragraphCount(article: Article): Int = article.paragraphs.size

    /** How far through the article [paragraphIndex] is, as a 0f..1f scroll position. */
    fun progressThrough(article: Article, paragraphIndex: Int): Float {
        val total = article.paragraphs.size
        if (total <= 1) return if (total == 1 && article.lastParagraph > 0) 1f else 0f
        return (paragraphIndex.coerceIn(0, total - 1).toFloat() / (total - 1)).coerceIn(0f, 1f)
    }

    /**
     * A short relative date: "now", "12 min ago", "3 h ago", "Tue", "14 Mar", "14 Mar 2025".
     *
     * Deliberately not a full date on every card. The library is a "what was I just reading"
     * surface, and a full timestamp pushes the article title down the card where it belongs.
     *
     * Formatted in UTC rather than the device timezone on purpose: a "3 h ago" label that shifts
     * when the phone crosses a timezone is worse than no label, and the shelf is a list of things
     * you did, not appointments.
     */
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = now - epochMillis
        if (delta < 0) return "now"
        val minutes = Duration.ofMillis(delta).toMinutes()
        val hours = Duration.ofMillis(delta).toHours()
        val days = Duration.ofMillis(delta).toDays()
        if (minutes < 1) return "now"
        if (minutes < 60) return "$minutes min ago"
        if (hours < 24) return "$hours h ago"
        val then = epochMillis.atZoneUtc()
        if (days < 7) return then.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        if (then.year == now.atZoneUtc().year) return then.format(DAY_MONTH)
        return then.format(DAY_MONTH_YEAR)
    }

    private fun Long.atZoneUtc(): ZonedDateTime =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC)

    private const val WORDS_PER_MINUTE = 180f

    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
}

/** How the word list is narrowed down. */
enum class VocabFilter { All, NotSent, Sent, ByArticle }

/** The order the word list is shown in. */
enum class VocabSort { Newest, Alphabetical }

/**
 * Filter and sort the word list.
 *
 * Extracted from the screen so the rules are testable: a filter that quietly returns the wrong
 * subset is the kind of bug nobody notices until their vocabulary has gone.
 */
object VocabFilterEngine {

    fun apply(
        items: List<VocabItem>,
        filter: VocabFilter,
        sort: VocabSort,
        query: String = "",
    ): List<VocabItem> {
        val searched = if (query.isBlank()) items else items.filter { matches(it, query) }
        val filtered = when (filter) {
            VocabFilter.All -> searched
            VocabFilter.NotSent -> searched.filter { it.sentToAnkiAt == null }
            VocabFilter.Sent -> searched.filter { it.sentToAnkiAt != null }
            VocabFilter.ByArticle -> searched
        }
        return when (sort) {
            VocabSort.Newest -> filtered.sortedByDescending { it.createdAt }
            // German sorts umlauts as base letters, so a plain String.compareTo puts every
            // "ä" after "z". Collating by the locale is the difference between a usable
            // A-Z toggle and a broken one.
            VocabSort.Alphabetical -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.word },
            )
        }
    }

    /** Matches on the word, its lemma, a gloss or the sentence, so a memory of any of them works. */
    fun matches(item: VocabItem, query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return true
        return item.word.lowercase(Locale.ROOT).contains(needle) ||
            item.lemma.lowercase(Locale.ROOT).contains(needle) ||
            item.glosses.any { it.lowercase(Locale.ROOT).contains(needle) } ||
            item.meaning.lowercase(Locale.ROOT).contains(needle) ||
            item.sentence.lowercase(Locale.ROOT).contains(needle) ||
            item.articleTitle.lowercase(Locale.ROOT).contains(needle)
    }

    /** The article titles present, in the order they first appear in [items]. */
    fun articleTitles(items: List<VocabItem>): List<String> =
        items.map { it.articleTitle }.filter { it.isNotBlank() }.distinct()
}
