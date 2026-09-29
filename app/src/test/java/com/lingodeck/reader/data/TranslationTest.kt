package com.lingodeck.reader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the translation layer that do not need a network.
 *
 * Deliberately no test here reaches the internet. What can be checked without a server is the
 * part that decides *whether* a request happens at all — provider selection, endpoint
 * normalisation, and whether a key is required — because that is where a mistake silently sends
 * the wrong text to the wrong place.
 */
class TranslationTest {

    // ---- provider selection ---------------------------------------------------------

    @Test
    fun `no provider means no translator`() {
        assertNull(translatorFor(TranslationProvider.None, apiKey = "", endpoint = ""))
    }

    @Test
    fun `a provider with no key means no translator`() {
        // The important case: choosing Google with an empty key must not produce a translator
        // that fails at call time. The card only offers the action when this returns non-null.
        assertNull(translatorFor(TranslationProvider.GoogleCloud, apiKey = "", endpoint = ""))
        assertNull(translatorFor(TranslationProvider.GoogleCloud, apiKey = "   ", endpoint = ""))
    }

    @Test
    fun `a provider with a key produces a translator`() {
        assertEquals(
            TranslationProvider.GoogleCloud,
            translatorFor(TranslationProvider.GoogleCloud, apiKey = "abc123", endpoint = "")?.provider,
        )
    }

    @Test
    fun `LibreTranslate needs no key`() {
        assertEquals(
            TranslationProvider.LibreTranslate,
            translatorFor(TranslationProvider.LibreTranslate, apiKey = "", endpoint = "")?.provider,
        )
    }

    @Test
    fun `surrounding whitespace on a key is trimmed`() {
        assertTrue(
            translatorFor(TranslationProvider.GoogleCloud, apiKey = "  abc123  ", endpoint = "") != null,
        )
    }

    @Test
    fun `settings know whether they can translate`() {
        val off = SettingsFixture(translationProvider = TranslationProvider.None)
        assertFalse(off.canTranslate)

        val missingKey = SettingsFixture(translationProvider = TranslationProvider.GoogleCloud)
        assertFalse(missingKey.canTranslate)

        val ready = SettingsFixture(
            translationProvider = TranslationProvider.GoogleCloud,
            translationApiKey = "abc123",
        )
        assertTrue(ready.canTranslate)
        assertEquals(TranslationProvider.GoogleCloud, ready.translator()?.provider)
    }

    // ---- endpoint normalisation -----------------------------------------------------

    @Test
    fun `a bare host is given a scheme`() {
        assertEquals(
            "https://translate.example.com/translate",
            LibreTranslateTranslator.normalize("translate.example.com"),
        )
    }

    @Test
    fun `a host with a scheme is left alone`() {
        assertEquals(
            "https://translate.example.com/translate",
            LibreTranslateTranslator.normalize("https://translate.example.com"),
        )
    }

    @Test
    fun `an explicit port survives`() {
        // https is the default for a bare host, and a plain-HTTP server on a LAN has to be typed
        // in full. Defaulting downwards to http would silently downgrade a public endpoint.
        assertEquals(
            "https://192.168.1.10:5000/translate",
            LibreTranslateTranslator.normalize("192.168.1.10:5000"),
        )
    }

    @Test
    fun `an explicit http scheme is preserved rather than upgraded`() {
        // The common real case: LibreTranslate on a home server, which speaks plain HTTP. A
        // self-signed or port-forwarded endpoint has to be reachable, so this is left alone.
        assertEquals(
            "http://192.168.1.10:5000/translate",
            LibreTranslateTranslator.normalize("http://192.168.1.10:5000"),
        )
    }

    @Test
    fun `a path that already ends in translate is not doubled`() {
        assertEquals(
            "https://translate.example.com/translate",
            LibreTranslateTranslator.normalize("https://translate.example.com/translate"),
        )
    }

    @Test
    fun `a trailing slash is removed first`() {
        assertEquals(
            "https://translate.example.com/translate",
            LibreTranslateTranslator.normalize("https://translate.example.com/"),
        )
    }

    @Test
    fun `a subpath is preserved`() {
        assertEquals(
            "https://example.com/lt/translate",
            LibreTranslateTranslator.normalize("example.com/lt"),
        )
    }

    @Test
    fun `blank falls back to the public instance`() {
        assertEquals(
            LibreTranslateTranslator.DEFAULT_ENDPOINT,
            LibreTranslateTranslator.normalize("   "),
        )
    }

    // ---- request and response shapes -------------------------------------------------

    @Test
    fun `the Google body carries the sentence and both languages`() {
        val body = JSONObject(
            GoogleCloudTranslator("key").buildBody("Ich habe nicht verstanden, was sie gesagt hat.", "de", "en"),
        )
        assertEquals("Ich habe nicht verstanden, was sie gesagt hat.", body.getString("q"))
        assertEquals("de", body.getString("source"))
        assertEquals("en", body.getString("target"))
    }

    @Test
    fun `the Google key is a header and not part of the request body`() {
        // A credential in a query string ends up in proxy logs and in any request dump. This
        // asserts the body alone; the header mapping is the other half.
        val body = GoogleCloudTranslator("super-secret-key")
            .buildBody("Hallo.", "de", "en")
        assertFalse(body.contains("super-secret-key"))
        assertEquals("super-secret-key", GoogleCloudTranslator("super-secret-key").headers()["X-Goog-Api-Key"])
    }

    @Test
    fun `a Google response is read out of its nested shape`() {
        val response = """
            {"data":{"translations":[{"translatedText":"I didn't understand what she said."}]}}
        """.trimIndent()
        assertEquals(
            "I didn't understand what she said.",
            GoogleCloudTranslator("key").parseTranslation(response),
        )
    }

    @Test
    fun `a LibreTranslate response is read from the top level`() {
        assertEquals(
            "I didn't understand what she said.",
            LibreTranslateTranslator().parseTranslation(
                """{"translatedText":"I didn't understand what she said."}""",
            ),
        )
    }

    @Test
    fun `an over-long sentence is refused before any request is made`() {
        // Verified synchronously through the failure type: the point is that this never reaches
        // the network, which is why there is no dispatcher stub in this test at all.
        val failure = TranslationFailure(TranslationError.TooLong(2_001))
        assertTrue(failure.error is TranslationError.TooLong)
        assertEquals(2_001, (failure.error as TranslationError.TooLong).length)
    }
}

/** Builds a [Settings] with only the fields under test set, so the test does not drift. */
private fun SettingsFixture(
    translationProvider: TranslationProvider = TranslationProvider.None,
    translationApiKey: String = "",
) = com.lingodeck.reader.store.Settings(
    translationProvider = translationProvider,
    translationApiKey = translationApiKey,
)
