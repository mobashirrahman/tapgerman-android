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
                    put("paragraphs", JSONArray(item.paragraphs))
                },
            )
        }
        writeArray(articlesFile, array)
    }

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
            )
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun deleteVocab(id: String) {
        writeVocab(listVocab().filterNot { it.id == id })
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
                },
            )
        }
        writeArray(vocabFile, array)
    }

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
}
