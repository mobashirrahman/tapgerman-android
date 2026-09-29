package com.lingodeck.reader.store

import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.VocabItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local persistence as JSON files in the app's private storage.
 *
 * The volumes here are tiny (tens of articles, hundreds of words) so a database would be overhead
 * without benefit; the extension keeps the same kind of bounded, on-device-only state. Nothing is
 * synchronised and nothing is written outside `context.filesDir`.
 */
class Store(private val directory: File) {
    private val articlesFile = File(directory, "articles.json")
    private val vocabFile = File(directory, "vocab.json")
    private val recentFile = File(directory, "recent-lookups.json")

    init {
        directory.mkdirs()
    }

    @Synchronized
    fun listArticles(): List<Article> {
        val array = readArray(articlesFile)
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val paragraphs = o.optJSONArray("paragraphs")?.let { p ->
                (0 until p.length()).map { p.optString(it) }
            } ?: emptyList()
            if (paragraphs.isEmpty()) return@mapNotNull null
            Article(
                url = o.optString("url"),
                title = o.optString("title"),
                byline = o.optString("byline"),
                paragraphs = paragraphs,
                retrievedAt = o.optLong("retrievedAt"),
                lastParagraph = o.optInt("lastParagraph", 0),
            )
        }.sortedByDescending { it.retrievedAt }
    }

    @Synchronized
    fun saveArticle(article: Article) {
        val items = listArticles().filterNot { it.url == article.url } + article
        val array = JSONArray()
        // Bound the shelf so a long reading history cannot grow without limit.
        for (item in items.sortedByDescending { it.retrievedAt }.take(50)) {
            array.put(
                JSONObject().apply {
                    put("url", item.url)
                    put("title", item.title)
                    put("byline", item.byline)
                    put("retrievedAt", item.retrievedAt)
                    put("lastParagraph", item.lastParagraph)
                    put("paragraphs", JSONArray(item.paragraphs))
                },
            )
        }
        writeArray(articlesFile, array)
    }

    /** Removes one article from the shelf. Saved words that came from it are left alone. */
    @Synchronized
    fun deleteArticle(url: String) {
        writeArray(
            articlesFile,
            JSONArray().apply {
                for (item in listArticles().filterNot { it.url == url }) {
                    put(
                        JSONObject().apply {
                            put("url", item.url)
                            put("title", item.title)
                            put("byline", item.byline)
                            put("retrievedAt", item.retrievedAt)
                            put("lastParagraph", item.lastParagraph)
                            put("paragraphs", JSONArray(item.paragraphs))
                        },
                    )
                }
            },
        )
    }

    @Synchronized
    fun clearArticles() = writeArray(articlesFile, JSONArray())

    @Synchronized
    fun listVocab(): List<VocabItem> {
        val array = readArray(vocabFile)
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            VocabItem(
                id = o.optString("id"),
                createdAt = o.optLong("createdAt"),
                word = o.optString("word"),
                lemma = o.optString("lemma"),
                partOfSpeech = o.optString("partOfSpeech"),
                ipa = o.optString("ipa"),
                audioUrl = o.optString("audioUrl"),
                glosses = o.optJSONArray("glosses")?.let { g ->
                    (0 until g.length()).map { g.optString(it) }
                } ?: emptyList(),
                meaning = o.optString("meaning"),
                sentence = o.optString("sentence"),
                articleTitle = o.optString("articleTitle"),
                sourceUrl = o.optString("sourceUrl"),
                source = o.optString("source"),
                languageCode = o.optString("languageCode").ifEmpty { "de" },
                paragraphIndex = if (o.has("paragraphIndex")) o.optInt("paragraphIndex") else null,
                wordStart = if (o.has("wordStart")) o.optInt("wordStart") else null,
                wordEnd = if (o.has("wordEnd")) o.optInt("wordEnd") else null,
                sentToAnkiAt = if (o.has("sentToAnkiAt")) o.optLong("sentToAnkiAt") else null,
            )
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun deleteVocab(id: String) {
        writeVocab(listVocab().filterNot { it.id == id })
    }

    @Synchronized
    fun clearVocab() = writeVocab(emptyList())

    /**
     * Puts a removed item back.
     *
     * Swipe-to-dismiss on the word list has to be undoable, and the id is the Anki StableId, so
     * restoring the exact object is the only safe way to do it: a re-derived id could differ and
     * silently point the word list at a note that does not exist.
     */
    @Synchronized
    fun restoreVocab(item: VocabItem) {
        writeVocab(listVocab().filterNot { it.id == item.id } + item)
    }

    @Synchronized
    fun saveVocab(item: VocabItem) {
        writeVocab(listVocab().filterNot { it.id == item.id } + item)
    }

    private fun writeVocab(entries: List<VocabItem>) {
        val array = JSONArray()
        for (entry in entries.sortedByDescending { it.createdAt }.take(1000)) {
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("createdAt", entry.createdAt)
                    put("word", entry.word)
                    put("lemma", entry.lemma)
                    put("partOfSpeech", entry.partOfSpeech)
                    put("ipa", entry.ipa)
                    put("audioUrl", entry.audioUrl)
                    put("glosses", JSONArray(entry.glosses))
                    put("meaning", entry.meaning)
                    put("sentence", entry.sentence)
                    put("articleTitle", entry.articleTitle)
                    put("sourceUrl", entry.sourceUrl)
                    put("source", entry.source)
                    put("languageCode", entry.languageCode)
                    put("paragraphIndex", entry.paragraphIndex ?: JSONObject.NULL)
                    put("wordStart", entry.wordStart ?: JSONObject.NULL)
                    put("wordEnd", entry.wordEnd ?: JSONObject.NULL)
                    put("sentToAnkiAt", entry.sentToAnkiAt ?: JSONObject.NULL)
                },
            )
        }
        writeArray(vocabFile, array)
    }

    /**
     * Words looked up from the dictionary screen, most recent first.
     *
     * Separate from the vocabulary list on purpose: looking a word up and keeping it are
     * different acts, and a reader who checks a verb twenty times has not collected twenty words.
     * This is also the only record of a word that was looked up and *not* saved, which is the
     * common case for a dictionary check.
     */
    @Synchronized
    fun listRecentLookups(): List<String> {
        val array = readArray(recentFile)
        return (0 until array.length())
            .map { array.optString(it) }
            .filter { it.isNotEmpty() }
            .take(MAX_RECENT_LOOKUPS)
    }

    /** Moves [word] to the front, adding it if new and dropping the tail past the cap. */
    @Synchronized
    fun rememberLookup(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val existing = listRecentLookups().filterNot { it.equals(trimmed, ignoreCase = true) }
        val updated = (listOf(trimmed) + existing).take(MAX_RECENT_LOOKUPS)
        writeArray(recentFile, JSONArray(updated))
    }

    @Synchronized
    fun clearRecentLookups() = writeArray(recentFile, JSONArray())

    private fun readArray(file: File): JSONArray {
        if (!file.isFile) return JSONArray()
        return runCatching { JSONArray(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONArray())
    }

    private fun writeArray(file: File, array: JSONArray) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(array.toString(2), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(array.toString(2), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private companion object {
        /**
         * Two dozen. Enough to cover a sitting of lookups and one reading session's worth of
         * corrections, without turning the dictionary screen into a list nobody reads.
         */
        const val MAX_RECENT_LOOKUPS = 24
    }
}
