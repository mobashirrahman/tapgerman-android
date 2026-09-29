package com.glossline.reader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Article readability: pick the block of a page that is actually the article and return its
 * paragraphs.
 *
 * A compact scoring heuristic in the spirit of Mozilla Readability rather than a port of it: score
 * candidate containers by how much real paragraph text they hold and how little of that text is
 * links, then keep the best-scoring one. Paragraph-level output (rather than one big blob) is what
 * the reader needs, so word taps map back to a stable sentence context.
 */
object ArticleExtractor {

    data class Extracted(
        val title: String,
        val byline: String,
        val paragraphs: List<String>,
    )

    private val NOISE = listOf(
        "script", "style", "noscript", "template", "svg", "form", "button", "iframe",
        "nav", "aside", "footer", "header",
        "[role=navigation]", "[role=banner]", "[role=contentinfo]", "[role=complementary]",
        "[aria-hidden=true]",
        ".advertisement", ".ad", ".ads", ".advert", ".newsletter", ".signup", ".subscribe",
        ".social", ".share", ".sharing", ".related", ".recommendations", ".comments",
        ".comment", ".sidebar", ".widget", ".breadcrumb", ".pagination", ".cookie",
        ".paywall", ".banner", ".menu", ".nav", ".toolbar",
    )

    private val CANDIDATES = listOf(
        "article",
        "main",
        "[role=main]",
        "[itemprop=articleBody]",
        ".article-body",
        ".article__body",
        ".article-content",
        ".post-content",
        ".post__content",
        ".entry-content",
        ".story-body",
        ".story__body",
        "#article-body",
        "#content",
        ".content",
    )

    private const val MIN_PARAGRAPH_LENGTH = 25

    /** Below this much body text there is no article to salvage, only chrome we failed to strip. */
    private const val MIN_FALLBACK_LENGTH = 200

    private const val MAX_LINK_DENSITY = 0.5

    fun extract(html: String, pageUrl: String): Extracted {
        val document = Jsoup.parse(html, pageUrl)
        val title = extractTitle(document)
        val byline = extractByline(document)

        val cleaned = Jsoup.parse(document.html(), pageUrl)
        for (selector in NOISE) {
            runCatching { cleaned.select(selector).remove() }
        }

        val best = pickContainer(cleaned)
        val paragraphs = best?.let { paragraphsOf(it) }.orEmpty().ifEmpty { fallbackParagraphs(cleaned) }
        return Extracted(title, byline, paragraphs)
    }

    private fun extractTitle(document: Document): String {
        val og = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        if (!og.isNullOrEmpty()) return og
        val h1 = document.selectFirst("h1")?.text()?.trim()
        if (!h1.isNullOrEmpty()) return h1
        return document.title().trim().substringBeforeLast(" - ").substringBeforeLast(" | ")
            .trim().ifEmpty { "Untitled article" }
    }

    private fun extractByline(document: Document): String {
        val candidates = listOf(
            document.selectFirst("meta[name=author]")?.attr("content"),
            document.selectFirst("meta[property=article:author]")?.attr("content"),
            document.selectFirst("[rel=author]")?.text(),
            document.selectFirst(".byline")?.text(),
            document.selectFirst(".author")?.text(),
            document.selectFirst("[itemprop=author]")?.text(),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: ""
    }

    private fun pickContainer(document: Document): Element? {
        val seen = HashSet<Element>()
        val scored = ArrayList<Pair<Element, Double>>()
        for (selector in CANDIDATES) {
            for (element in document.select(selector)) {
                if (!seen.add(element)) continue
                val score = scoreOf(element)
                if (score > 0) scored.add(element to score)
            }
        }
        if (scored.isEmpty()) return document.selectFirst("body")
        return scored.maxByOrNull { it.second }?.first
    }

    private fun scoreOf(element: Element): Double {
        val paragraphs = paragraphsOf(element)
        if (paragraphs.isEmpty()) return 0.0
        val text = paragraphs.joinToString(" ")
        val linkText = element.select("a").joinToString(" ") { it.text() }
        val linkDensity = if (text.isEmpty()) 1.0 else linkText.length.toDouble() / text.length
        if (linkDensity > MAX_LINK_DENSITY) return 0.0
        // Text mass dominates, but a bonus per paragraph favours real article structure over one
        // enormous blob of boilerplate.
        return text.length.toDouble() + paragraphs.size * 25.0
    }

    private fun paragraphsOf(element: Element): List<String> =
        element.select("p")
            .map { it.text().trim() }
            .filter { it.length >= MIN_PARAGRAPH_LENGTH }

    /**
     * Last resort for pages whose article is one undifferentiated text blob with no `<p>`
     * structure. Deliberately refused when the body is short: without a floor here, every
     * unparseable or paywalled page "succeeds" as a one-line article instead of telling the reader
     * to fall back to the browser.
     */
    private fun fallbackParagraphs(document: Document): List<String> {
        val body = document.selectFirst("body") ?: return emptyList()
        val text = body.text().trim()
        if (text.length < MIN_FALLBACK_LENGTH) return emptyList()
        return text.split(Regex("(?<=[.!?])\\s+(?=[A-ZÄÖÜ„\"(])"))
            .chunked(4)
            .map { it.joinToString(" ") }
            .filter { it.isNotBlank() }
    }
}
