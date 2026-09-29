package com.lingodeck.reader.data

import com.lingodeck.reader.util.esTrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where sentence translations come from. Chosen in Settings. */
enum class TranslationProvider {
    /** Off. The lookup card still shows the dictionary's own translated examples. */
    None,

    /**
     * Google Cloud Translation v2.
     *
     * The default because it needs no SDK — the app already speaks `HttpURLConnection` for
     * article fetching, and adding a Google client library for one POST would be a lot of
     * dependency for one request.
     */
    GoogleCloud,

    /**
     * LibreTranslate, and other self-hosted instances of it.
     *
     * Needs no API key, which makes it the only option here that does not put a credential in
     * the app at all. The default endpoint is a public instance and is heavily rate limited;
     * point [Settings.translationEndpoint] at your own server for real use.
     */
    LibreTranslate,
}

/** A failure to translate, phrased so the card can show it. */
sealed interface TranslationError {
    /** No key or endpoint configured for the chosen provider. */
    data class NotConfigured(val provider: TranslationProvider) : TranslationError

    /** The provider was reached and declined. */
    data class Rejected(val status: Int, val detail: String) : TranslationError

    /** The provider could not be reached at all. */
    data class Unreachable(val detail: String) : TranslationError

    /** The sentence is longer than any provider here will take in one request. */
    data class TooLong(val length: Int) : TranslationError
}

/**
 * Translates one sentence.
 *
 * Deliberately not an inline function in the view model: the lookup card needs to call this
 * behind a loading state, on a coroutine, and the failure modes above are what the card turns
 * into a message, so they have to be a type rather than a caught exception.
 */
interface Translator {
    val provider: TranslationProvider
    suspend fun translate(text: String, source: String, target: String): Result<String>
}

/**
 * Builds a [Translator] from settings, or null when the chosen provider is not usable.
 *
 * Returning null rather than a translator that fails on every call is what lets the card simply
 * not offer the action, instead of offering it and then apologising.
 */
fun translatorFor(
    provider: TranslationProvider,
    apiKey: String,
    endpoint: String,
): Translator? = when (provider) {
    TranslationProvider.None -> null
    TranslationProvider.GoogleCloud ->
        if (apiKey.isBlank()) null else GoogleCloudTranslator(apiKey.trim())
    TranslationProvider.LibreTranslate ->
        LibreTranslateTranslator(endpoint.trim().ifBlank { LibreTranslateTranslator.DEFAULT_ENDPOINT })
}

/**
 * Shared plumbing: a bounded JSON POST over `HttpURLConnection`, matching `ArticleFetcher` and
 * `KaikkiClient` rather than introducing an HTTP client for four call sites.
 *
 * The key is never placed in the query string. Google's v2 endpoint accepts `X-Goog-Api-Key`, and
 * a credential in a URL ends up in proxy logs, in any crash report that captures the request, and
 * in `adb logcat` output from a mistaken `HttpLogging` style call.
 */
abstract class JsonTranslator(
    protected val endpoint: String,
    private val timeoutMs: Int = 15_000,
) : Translator {

    abstract override val provider: TranslationProvider

    /**
     * Extra headers, which is where the credential goes.
     *
     * Internal rather than protected so a test can assert that the key is not in the body: the
     * whole point of this design is that the key stays out of URLs, and a test that cannot see
     * the header mapping cannot hold that line.
     */
    internal open fun headers(): Map<String, String> = emptyMap()

    abstract fun buildBody(text: String, source: String, target: String): String

    /** Pulls the translated text out of the provider's response. */
    abstract fun parseTranslation(body: String): String

    override suspend fun translate(
        text: String,
        source: String,
        target: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        // Every provider here has a per-request ceiling well under this; a sentence from an
        // article is never this long, and a longer one is a sign the selection went wrong.
        if (text.length > MAX_SENTENCE_LENGTH) {
            return@withContext Result.failure(
                TranslationFailure(TranslationError.TooLong(text.length)),
            )
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                headers().forEach { (name, value) -> setRequestProperty(name, value) }
            }
            connection.outputStream.use { it.write(buildBody(text, source, target).toByteArray()) }

            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                .orEmpty()

            if (status !in 200..299) {
                return@withContext Result.failure(
                    TranslationFailure(TranslationError.Rejected(status, extractDetail(payload))),
                )
            }

            val translated = parseTranslation(payload)
            if (translated.isBlank()) {
                Result.failure(TranslationFailure(TranslationError.Rejected(status, "empty response")))
            } else {
                Result.success(translated)
            }
        } catch (e: IOException) {
            Result.failure(TranslationFailure(TranslationError.Unreachable(e.message ?: e::class.java.simpleName)))
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractDetail(payload: String): String = runCatching {
        JSONObject(payload).optJSONObject("error")?.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: esTrim(payload.take(160))

    private companion object {
        const val MAX_SENTENCE_LENGTH = 2_000
    }
}

/** Wraps a [TranslationError] so it can travel inside a [Result] without a second type. */
class TranslationFailure(val error: TranslationError) : Exception()

/**
 * Google Cloud Translation v2.
 *
 * `POST /language/translate/v2` with the credential in `X-Goog-Api-Key`. The response nests the
 * result one level down, under `data.translations[0].translatedText`.
 */
class GoogleCloudTranslator(private val apiKey: String) : JsonTranslator(ENDPOINT) {

    override val provider = TranslationProvider.GoogleCloud

    override fun headers() = mapOf("X-Goog-Api-Key" to apiKey)

    override fun buildBody(text: String, source: String, target: String): String = JSONObject()
        .put("q", text)
        .put("source", source)
        .put("target", target)
        .put("format", "text")
        .toString()

    override fun parseTranslation(body: String): String =
        JSONObject(body)
            .getJSONObject("data")
            .getJSONArray("translations")
            .getJSONObject(0)
            .optString("translatedText")

    private companion object {
        const val ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
    }
}

/**
 * LibreTranslate, and the many servers that speak its API.
 *
 * No credential: the only configuration is which server to talk to. Defaults to a public
 * instance, which is fine for trying and not for daily use — they are rate limited and they log
 * the text they translate.
 */
class LibreTranslateTranslator(endpoint: String = DEFAULT_ENDPOINT) : JsonTranslator(endpoint) {

    override val provider = TranslationProvider.LibreTranslate

    override fun buildBody(text: String, source: String, target: String): String = JSONObject()
        .put("q", text)
        .put("source", source)
        .put("target", target)
        .put("format", "text")
        .toString()

    override fun parseTranslation(body: String): String = JSONObject(body).optString("translatedText")

    companion object {
        const val DEFAULT_ENDPOINT = "https://libretranslate.com/translate"

        /** A bare host is the most likely thing to be pasted in, so it is filled out for them. */
        fun normalize(raw: String): String {
            val trimmed = esTrim(raw).removeSuffix("/")
            if (trimmed.isEmpty()) return DEFAULT_ENDPOINT
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            return if (withScheme.endsWith("/translate")) withScheme else "$withScheme/translate"
        }
    }
}
