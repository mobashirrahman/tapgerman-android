package com.tapgerman.reader

import com.tapgerman.reader.data.ArticleExtractor
import com.tapgerman.reader.data.ArticleFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    @Test
    fun findsTheArticleBodyAndDropsChrome() {
        val html = """
            <html><head>
              <meta property="og:title" content="Koalition einig über Entlastungen">
              <meta name="author" content="Redaktion">
            </head><body>
              <nav>Start Politik Wirtschaft Sport</nav>
              <header><h1>Menü</h1></header>
              <article>
                <p>Der Bundestag hat am Donnerstag über das Gesetz debattiert und am Abend abgestimmt.</p>
                <p>Die Opposition kritisierte die Pläne als unzureichend und forderte Nachbesserungen.</p>
                <aside>Verwandte Artikel</aside>
              </article>
              <footer>Impressum Datenschutz</footer>
              <script>track();</script>
            </body></html>
        """.trimIndent()

        val extracted = ArticleExtractor.extract(html, "https://example.com/a")
        assertEquals("Koalition einig über Entlastungen", extracted.title)
        assertEquals("Redaktion", extracted.byline)
        assertEquals(2, extracted.paragraphs.size)
        assertTrue(extracted.paragraphs[0].startsWith("Der Bundestag"))
        assertTrue(extracted.paragraphs.none { it.contains("Impressum") })
        assertTrue(extracted.paragraphs.none { it.contains("Start Politik") })
    }

    @Test
    fun linkHeavyBlocksAreNotMistakenForTheArticle() {
        val html = """
            <html><body>
              <div class="sidebar">
                <p><a href="#">Noch mehr Nachrichten und Artikel und Berichte hier</a></p>
                <p><a href="#">Weitere Meldungen aus aller Welt lesen</a></p>
              </div>
              <div class="article-body">
                <p>Der Finanzminister stellte den Haushaltsentwurf vor und verteidigte die Zahlen.</p>
                <p>Die Schuldenbremse bleibt erhalten, sagte er vor Journalisten in Berlin am Mittwoch.</p>
              </div>
            </body></html>
        """.trimIndent()

        val extracted = ArticleExtractor.extract(html, "https://example.com/a")
        assertEquals(2, extracted.paragraphs.size)
        assertTrue(extracted.paragraphs.none { it.contains("Noch mehr") })
    }

    @Test
    fun titleFallsBackToThePageTitle() {
        val html = "<html><head><title>Kanzler reist nach Paris | Nachrichten</title></head>" +
            "<body><article><p>Der Kanzler wird am Montag in Paris erwartet und mit dem Präsidenten sprechen.</p></article></body></html>"
        val extracted = ArticleExtractor.extract(html, "https://example.com/a")
        assertEquals("Kanzler reist nach Paris", extracted.title)
    }

    @Test
    fun thinPagesProduceNoParagraphsSoTheReaderCanFallBack() {
        val extracted = ArticleExtractor.extract("<html><body><p>Kurz.</p></body></html>", "https://example.com")
        assertTrue(extracted.paragraphs.isEmpty())
    }

    @Test
    fun unstructuredBlobsAreStillSalvaged() {
        // An article dumped as one text blob with no <p> markup is what the sentence-splitting
        // fallback exists for. It must be distinguished from a thin page, which is not an article.
        val sentence = "Der Finanzminister stellte den Haushaltsentwurf vor und verteidigte die Zahlen im Bundestag am Mittwoch. "
        val blob = "<html><body>${sentence.repeat(5).trim()}</body></html>"
        val extracted = ArticleExtractor.extract(blob, "https://example.com")
        assertTrue("expected paragraphs, got ${extracted.paragraphs.size}", extracted.paragraphs.isNotEmpty())
    }

    @Test
    fun sharedTextYieldsTheFirstHttpUrl() {
        assertEquals(
            "https://www.tagesschau.de/wirtschaft/foo-bar-100.html",
            ArticleFetcher.extractUrlFromSharedText(
                "Foo-Bar https://www.tagesschau.de/wirtschaft/foo-bar-100.html via tagesschau",
            ),
        )
        assertEquals("https://example.com/x", ArticleFetcher.extractUrlFromSharedText("https://example.com/x."))
        assertNull(ArticleFetcher.extractUrlFromSharedText("kein Link hier"))
        assertNull(ArticleFetcher.extractUrlFromSharedText(null))
    }
}
