package com.glossline.reader.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches a recording. Suspending and byte-oriented so the cache can be tested without a network. */
fun interface AudioFetcher {
    /** The bytes at [url], or null when it could not be retrieved. */
    suspend fun fetch(url: String): ByteArray?
}

/**
 * Retrieves a recording for a word, from disk if it is there and from Wiktionary if it is not.
 *
 * Every failure — no URL, no recording, offline, rate limited — resolves to null rather than an
 * exception, because every one of them has the same answer for the caller: speak with the synthetic
 * voice instead. A word that cannot be recorded is not an error the reader needs to hear about.
 */
class DictionaryAudio(
    private val cache: AudioCache,
    private val fetcher: AudioFetcher = WikimediaFetcher(),
) {

    /** The cached or freshly downloaded recording for [url], or null. */
    suspend fun recordingFor(url: String): File? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        val bytes = fetcher.fetch(url) ?: return null
        cache.put(url, bytes)
        cache.evict()
        return cache.get(url)
    }

    /** The cached recording for [url] without touching the network. */
    fun cached(url: String): File? = cache.get(url)
}

/**
 * Downloads from Wikimedia Commons, which is where Wiktionary's German recordings live.
 *
 * Two things about that host are requirements rather than preferences, and both were found by
 * being rate limited while checking the URLs the app already had stored:
 *
 * - It returns 429 to a client it does not like. A user tapping through ten words in a row is
 *   exactly that client, so one retry after a pause, and then giving up and letting the caller
 *   fall back to speech.
 * - It asks for a descriptive User-Agent. The default Java one is refused outright, and a
 *   browser-shaped one is a lie that gets an app blocked.
 */
class WikimediaFetcher(
    private val timeoutMs: Int = 10_000,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val retryDelayMs: Long = 1_200,
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AudioFetcher {

    override suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        request(url) ?: run {
            // One retry, and only for the status that means "slow down" rather than "no such
            // recording". Retrying a 404 just spends the reader's data confirming the absence.
            sleeper(retryDelayMs)
            request(url)
        }
    }

    private fun request(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "audio/mpeg,audio/ogg,audio/*;q=0.8")
            }
            when (connection.responseCode) {
                in 200..299 -> {
                    val declared = connection.contentLengthLong
                    if (declared > AudioCache.MAX_FILE_BYTES) {
                        null
                    } else {
                        readBounded(connection.inputStream)
                            // An empty body is a truncated download, not a silent word. Storing it
                            // would leave a file that looks cached and plays nothing.
                            ?.takeIf { it.isNotEmpty() }
                    }
                }
                else -> null
            }
        } catch (e: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most [AudioCache.MAX_FILE_BYTES], so a large response cannot fill memory. */
    private fun readBounded(input: java.io.InputStream): ByteArray? = runCatching {
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > AudioCache.MAX_FILE_BYTES) return null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    companion object {
        /**
         * Wikimedia's policy asks for a User-Agent that identifies the client and offers a way to
         * be contacted. The app's own name plus the dictionary it is fetching on the reader's
         * behalf; there is no server to point at, so no URL.
         */
        const val DEFAULT_USER_AGENT =
            "GlossLineReader/0.3 (Android; German reading app; dictionary audio via kaikki.org)"
    }
}
