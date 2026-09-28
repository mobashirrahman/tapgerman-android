package com.lingodeck.reader

import com.lingodeck.reader.anki.NoteIdentity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads `goldens.json`, which `tools/generate-goldens.mjs` produced by executing the LingoDeck
 * Chrome extension's own modules. Anything asserted from it is a cross-language contract test: if a
 * Kotlin refactor changes a hash, a URL, or a rendered field, these tests fail.
 *
 * The card shapes are read back out of the fixture rather than re-declared here, so the two sides
 * can never disagree about which object a golden hash was computed from.
 */
object Goldens {
    val root: JSONObject by lazy {
        val stream = requireNotNull(
            Goldens::class.java.classLoader?.getResourceAsStream("goldens.json"),
        ) { "goldens.json is missing from test resources. Run: node tools/generate-goldens.mjs" }
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    /** Fixture card name -> the card the extension hashed. */
    fun cards(): Map<String, NoteIdentity.Card> {
        val objectCards = root.getJSONObject("cards")
        val out = LinkedHashMap<String, NoteIdentity.Card>()
        for (name in objectCards.keys()) {
            out[name] = readCard(objectCards.getJSONObject(name))
        }
        return out
    }

    fun readCard(o: JSONObject): NoteIdentity.Card {
        val definitions = if (o.isNull("definitions")) null else {
            val array = o.getJSONArray("definitions")
            (0 until array.length()).map { array.getString(it) }
        }
        return NoteIdentity.Card(
            word = o.optString("word"),
            lemma = if (o.isNull("lemma")) null else o.optString("lemma"),
            definitions = definitions,
            meaning = if (o.isNull("meaning")) null else o.optString("meaning"),
            grammar = if (o.isNull("grammar")) null else o.optString("grammar"),
            partOfSpeech = if (o.isNull("partOfSpeech")) null else o.optString("partOfSpeech"),
            sentence = if (o.isNull("sentence")) null else o.optString("sentence"),
            translation = if (o.isNull("translation")) null else o.optString("translation"),
            languageCode = if (o.isNull("languageCode")) null else o.optString("languageCode"),
            ipa = if (o.isNull("ipa")) null else o.optString("ipa"),
            audioUrl = if (o.isNull("audioUrl")) null else o.optString("audioUrl"),
            source = if (o.isNull("source")) null else o.optString("source"),
        )
    }

    /** Rows of `[input, expected]`, where either side may be JSON null. */
    fun pairs(path: String): List<Pair<String?, String?>> {
        val array = root.getJSONArray(path)
        return (0 until array.length()).map { index ->
            val row = array.getJSONArray(index)
            (if (row.isNull(0)) null else row.getString(0)) to
                (if (row.isNull(1)) null else row.getString(1))
        }
    }

    /** Rows of `[input, languageCode, expectedUrlOrNull]`. */
    fun urlCases(path: String): List<Triple<String, String, String?>> {
        val array = root.getJSONArray(path)
        return (0 until array.length()).map { index ->
            val row = array.getJSONArray(index)
            Triple(row.getString(0), row.getString(1), if (row.isNull(2)) null else row.getString(2))
        }
    }

    /** Rows of `[input, expected]` where neither side is nullable. */
    fun textPairs(path: String): List<Pair<String, String>> {
        val array = root.getJSONArray(path)
        return (0 until array.length()).map { index ->
            val row = array.getJSONArray(index)
            row.getString(0) to row.getString(1)
        }
    }

    data class HighlightCase(
        val sentence: String,
        val candidate: String,
        val fallbacks: List<String>,
        val expected: String,
    )

    /** Rows of `[sentence, candidate, fallbackCandidates[], expected]`. */
    fun highlightCases(path: String): List<HighlightCase> {
        val array = root.getJSONArray(path)
        return (0 until array.length()).map { index ->
            val row = array.getJSONArray(index)
            val fallbacks = row.getJSONArray(2)
            HighlightCase(
                sentence = row.getString(0),
                candidate = row.getString(1),
                fallbacks = (0 until fallbacks.length()).map { fallbacks.getString(it) },
                expected = row.getString(3),
            )
        }
    }

    /** `parseKaikkiJsonl` output, laid out exactly like the JS module returned it. */
    data class GoldenEntry(
        val word: String,
        val partOfSpeech: String,
        val head: String,
        val ipa: String,
        val audioUrl: String,
        val formOf: String,
        val glosses: List<String>,
        val tagRows: List<List<String>>,
        val exampleRows: List<Pair<String, String>>,
    )

    data class GoldenParse(
        val word: String,
        val language: String,
        val languageCode: String,
        val entries: List<GoldenEntry>,
        val sourceName: String,
        val license: String,
    )

    fun parseResult(path: String): GoldenParse {
        val o = root.getJSONObject(path)
        val entries = o.getJSONArray("entries")
        return GoldenParse(
            word = o.getString("word"),
            language = o.getString("language"),
            languageCode = o.getString("languageCode"),
            sourceName = o.getString("sourceName"),
            license = o.getString("license"),
            entries = (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                val definitions = entry.getJSONArray("definitions")
                val glosses = ArrayList<String>()
                val tagRows = ArrayList<List<String>>()
                val exampleRows = ArrayList<Pair<String, String>>()
                for (d in 0 until definitions.length()) {
                    val sense = definitions.getJSONObject(d)
                    glosses.add(sense.getString("gloss"))
                    val tags = sense.getJSONArray("tags")
                    tagRows.add((0 until tags.length()).map { tags.getString(it) })
                    val examples = sense.getJSONArray("examples")
                    for (e in 0 until examples.length()) {
                        val example = examples.getJSONObject(e)
                        exampleRows.add(example.getString("text") to example.getString("translation"))
                    }
                }
                GoldenEntry(
                    word = entry.getString("word"),
                    partOfSpeech = entry.getString("partOfSpeech"),
                    head = entry.getString("head"),
                    ipa = entry.getString("ipa"),
                    audioUrl = entry.getString("audioUrl"),
                    formOf = entry.getString("formOf"),
                    glosses = glosses,
                    tagRows = tagRows,
                    exampleRows = exampleRows,
                )
            },
        )
    }

    fun string(path: String): String = root.getString(path)

    fun noteObject(path: String): JSONObject = root.getJSONObject(path)
}
