package com.lingodeck.reader.dict

import com.lingodeck.reader.data.DictResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches Kaikki entries.
 *
 * Ports the guard rails from `extension/src/dictionary.js`: 8 second timeout, 2 MB body cap, 24h
 * positive cache, 5 minute negative cache so a missing word is not hammered, and the German
 * lowercase/Titlecase retry ladder (Kaikki files are named by their Wiktionary page title, which is
 * capitalised for German nouns).
 */
object KaikkiClient {
    private const val TIMEOUT_MS = 8_000
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val POSITIVE_TTL_MS = 24L * 60 * 60 * 1000
    private const val NEGATIVE_TTL_MS = 5L * 60 * 1000
    private const val MAX_CACHE_ENTRIES = 300

    private data class Cached(val result: DictResult?, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    sealed class LookupOutcome {
        data class Found(val result: DictResult) : LookupOutcome()
        data class NotFound(val result: DictResult) : LookupOutcome()
        data class Failed(val message: String) : LookupOutcome()
    }

    fun lookup(input: String, languageCode: String = "de"): LookupOutcome {
        val normalized = KaikkiParser.normalizeLookupWord(input)
        if (normalized.isEmpty()) {
            return LookupOutcome.Failed("Choose a word containing letters or numbers.")
        }

        val candidates = linkedSetOf(normalized)
        if (languageCode == "de") {
            val lower = normalized.lowercase()
            val title = lower.replaceFirstChar { it.titlecase() }
            candidates.add(lower)
            candidates.add(title)
        }

        var last: DictResult? = null
        for (candidate in candidates) {
            when (val outcome = fetch(candidate, languageCode)) {
                is LookupOutcome.Found -> return outcome
                is LookupOutcome.NotFound -> last = outcome.result
                is LookupOutcome.Failed -> return outcome
            }
        }

        return LookupOutcome.NotFound(
            last ?: emptyResult(normalized, languageCode),
        )
    }

    /** Refetches the lemma behind an inflected form, so "Häuser" can show "Haus" in full. */
    fun lookupLemma(lemma: String, languageCode: String): List<com.lingodeck.reader.data.DictEntry> {
        if (lemma.isEmpty()) return emptyList()
        return when (val outcome = fetch(KaikkiParser.normalizeLookupWord(lemma), languageCode)) {
            is LookupOutcome.Found -> outcome.result.entries
            else -> emptyList()
        }
    }

    private fun fetch(word: String, languageCode: String): LookupOutcome {
        val url = KaikkiParser.buildKaikkiUrl(word, languageCode)
            ?: return LookupOutcome.NotFound(emptyResult(word, languageCode))

        val cached = cache[url]
        if (cached != null) {
            if (cached.expiresAt > System.currentTimeMillis()) {
                return cached.result?.let { LookupOutcome.Found(it) }
                    ?: LookupOutcome.NotFound(emptyResult(word, languageCode))
            }
            cache.remove(url)
        }

        val body = try {
            readBounded(url)
        } catch (error: IOException) {
            // A missing entry is a 404 and is cached negatively; anything else is a real failure.
            val message = error.message.orEmpty()
            if (message.contains("404")) {
                cachePut(url, null, NEGATIVE_TTL_MS)
                return LookupOutcome.NotFound(emptyResult(word, languageCode))
            }
            return LookupOutcome.Failed("The dictionary service is unavailable right now.")
        }

        val parsed = KaikkiParser.parseKaikkiJsonl(body, word, languageCode)
        if (parsed == null || parsed.entries.isEmpty()) {
            cachePut(url, null, NEGATIVE_TTL_MS)
            return LookupOutcome.NotFound(emptyResult(word, languageCode))
        }

        val withSource = parsed.copy(sourceUrl = url.removeSuffix(".jsonl") + ".html")
        cachePut(url, withSource, POSITIVE_TTL_MS)
        return LookupOutcome.Found(withSource)
    }

    private fun emptyResult(word: String, languageCode: String): DictResult =
        DictResult(
            word = word,
            language = KaikkiParser.languageNames[languageCode] ?: languageCode,
            languageCode = languageCode,
            entries = emptyList(),
            sourceUrl = "https://en.wiktionary.org/wiki/${com.lingodeck.reader.util.encodeUriComponent(word)}",
        )

    private fun cachePut(url: String, result: DictResult?, ttlMs: Long) {
        cache[url] = Cached(result, System.currentTimeMillis() + ttlMs)
        while (cache.size > MAX_CACHE_ENTRIES) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    private fun readBounded(url: String): String {
        val connection = runCatching { URI(url).toURL().openConnection() as HttpURLConnection }
            .getOrElse { throw IOException("The dictionary service is unavailable right now.") }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/jsonl, application/json;q=0.9, */*;q=0.5")
            val status = connection.responseCode
            if (status == 404) throw IOException("404")
            if (status !in 200..299) throw IOException("The dictionary service is unavailable right now.")
            val declared = connection.contentLengthLong
            if (declared > MAX_BYTES) throw IOException("Dictionary entry is unexpectedly large.")
            val bytes = readBounded(connection.inputStream)
            return String(bytes, Charsets.UTF_8)
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
                if (total > MAX_BYTES) throw IOException("Dictionary entry is unexpectedly large.")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}
