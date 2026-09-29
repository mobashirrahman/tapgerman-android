package com.glossline.reader.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Fetches the shared page URL and turns it into an [Article].
 *
 * Uses `HttpURLConnection` rather than a networking library so the app has exactly two external
 * runtime dependencies. Enforces a redirect scheme check and a byte cap: a shared link is untrusted
 * input, and a multi-megabyte "article" should not be buffered into memory.
 */
object ArticleFetcher {
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 5L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    fun fetch(rawUrl: String): Article {
        val finalUrl = follow(rawUrl)
        val html = readBody(finalUrl)
        val extracted = ArticleExtractor.extract(html, finalUrl)
        val title = extracted.title.ifBlank { finalUrl }
        if (extracted.paragraphs.isEmpty()) {
            throw IOException("That page has no readable article text. Try opening it in the browser instead.")
        }
        return Article(
            url = finalUrl,
            title = title,
            byline = extracted.byline,
            paragraphs = extracted.paragraphs,
        )
    }

    /** Returns the article URL after redirects, or a friendly error for unusable links. */
    fun follow(rawUrl: String): String {
        var target = normalize(rawUrl)
        repeat(MAX_REDIRECTS) {
            val connection = open(target) ?: throw IOException("Only http and https links can be read.")
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            val status = connection.responseCode
            if (status in 301..308) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("The site sent a redirect with no destination.")
                target = normalize(URI(target).resolve(location).toString())
                return@repeat
            }
            connection.disconnect()
            return target
        }
        throw IOException("That link redirected too many times.")
    }

    private fun readBody(url: String): String {
        val connection = open(url) ?: throw IOException("Only http and https links can be read.")
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
        connection.setRequestProperty("Accept-Encoding", "gzip")

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("The site responded with HTTP $status.")
            val declared = connection.contentLengthLong
            if (declared > MAX_BYTES) throw IOException("That page is too large to read (limit 5 MB).")

            val raw: InputStream = connection.inputStream
            val decoded = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            val bytes = readBounded(decoded)
            return String(bytes, charsetFor(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) throw IOException("That page is too large to read (limit 5 MB).")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun charsetFor(connection: HttpURLConnection): Charset {
        val header = connection.contentType
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"', '\'')
        return runCatching { Charset.forName(header ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
    }

    private fun open(url: String): HttpURLConnection? {
        val parsed = runCatching { URI(url).toURL() }.getOrNull() ?: return null
        if (parsed.protocol != "http" && parsed.protocol != "https") return null
        return (parsed.openConnection() as? HttpURLConnection)?.apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
    }

    private fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return try {
            val uri = URI(withScheme)
            if (uri.host.isNullOrBlank()) throw IOException("That does not look like a web address.")
            uri.toString()
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            throw IOException("That does not look like a web address.")
        }
    }

    /** Pulls the first http(s) URL out of a share payload, which may be title + URL, or just text. */
    fun extractUrlFromSharedText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("https?://[^\\s<>\"']+").find(text) ?: return null
        return match.value.trimEnd('.', ',', ';', ')', ']')
    }
}
