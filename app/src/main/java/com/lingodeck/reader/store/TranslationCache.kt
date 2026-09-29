package com.lingodeck.reader.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remembers which sentences have already been translated.
 *
 * Two reasons this exists rather than translating on every tap. The obvious one is cost and
 * politeness: sentence translation is a paid API call and every repeated tap on the same word in
 * the same paragraph would otherwise pay for it again. The subtler one is that the reader
 * re-reads articles — a word tapped on Monday and again on Thursday is the same sentence, and
 * sending it twice is a privacy cost the user cannot see.
 *
 * A JSON file next to the article and word stores, for the same reason those are files: the
 * volumes are tiny and nothing here should reach the network or a database. The article text
 * this caches is already sitting in `articles.json`, so caching it adds no new category of data on
 * the device — but it does add a new category of data that *left* it, which is why the bound is
 * tight and documented in the README.
 */
class TranslationCache(private val directory: File) {

    private val file = File(directory, "translations.json")
    private val lock = Mutex()

    /**
     * Keyed by sentence, target and source. Including the languages matters: the same string
     * translated de→en and de→fr are different answers, and a bare sentence key would return
     * whichever arrived first.
     */
    private fun key(sentence: String, source: String, target: String): String =
        "$source>$target:${sentence.hashCode()}:${sentence.length}"

    suspend fun get(sentence: String, source: String, target: String): String? = withContext(Dispatchers.IO) {
        lock.withLock { read()[key(sentence, source, target)] }
    }

    suspend fun put(sentence: String, source: String, target: String, translation: String) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val all = read().toMutableMap()
                all[key(sentence, source, target)] = translation
                write(all)
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { write(emptyMap()) }
    }

    /** How many sentences are cached, for the Settings screen. */
    suspend fun size(): Int = withContext(Dispatchers.IO) {
        lock.withLock { read().size }
    }

    private fun read(): Map<String, String> = runCatching {
        val array = JSONArray(if (file.isFile) file.readText(Charsets.UTF_8) else "[]")
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val k = o.optString("k")
            val v = o.optString("v")
            if (k.isEmpty() || v.isEmpty()) null else k to v
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun write(entries: Map<String, String>) {
        runCatching {
            val array = JSONArray()
            // Bounded, and insertion-ordered by recency because `put` appends. The cap is small
            // because each entry is a full sentence of article prose, and a reader with a year of
            // history could otherwise accumulate a lot of text that has been through a third
            // party's servers and is now cached in a second place.
            // `Map` has no takeLast, and the entries are already in insertion order, which is
            // recency order because `put` appends.
            entries.entries.toList().takeLast(MAX_ENTRIES).forEach { entry ->
                array.put(JSONObject().put("k", entry.key).put("v", entry.value))
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(array.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(array.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    companion object {
        /**
         * Roughly 400 sentences. A reader re-reading a shelf of 50 articles hits the cache; one
         * who is three years in does not, and that reader is also the one for whom a second copy
         * of article prose on disk is least welcome.
         */
        const val MAX_ENTRIES = 400
    }
}
