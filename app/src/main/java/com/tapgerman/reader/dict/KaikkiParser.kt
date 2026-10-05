package com.tapgerman.reader.dict

import com.tapgerman.reader.data.DictEntry
import com.tapgerman.reader.data.DictResult
import com.tapgerman.reader.data.InflectedForm
import com.tapgerman.reader.data.Example
import com.tapgerman.reader.data.Sense
import com.tapgerman.reader.util.encodeUriComponent
import com.tapgerman.reader.util.esTrim
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Port of `extension/src/dictionary.js`: Kaikki URL construction and JSONL parsing.
 *
 * The caps (30 records, 8 entries, 6 senses, 2 examples, 8 tags) are the extension's, and their
 * *order of application* matters: senses are filtered to those with glosses before the first six
 * are taken, while examples are truncated to two before empty ones are dropped. Keeping that
 * ordering is what makes a word render identically here and in the browser extension.
 */
object KaikkiParser {
    val languageNames: Map<String, String> = linkedMapOf(
        "ar" to "Arabic", "bg" to "Bulgarian", "ca" to "Catalan", "cs" to "Czech",
        "de" to "German", "el" to "Greek", "en" to "English", "es" to "Spanish",
        "fr" to "French", "ga" to "Irish", "he" to "Hebrew", "hi" to "Hindi",
        "hu" to "Hungarian", "it" to "Italian", "ja" to "Japanese", "ko" to "Korean",
        "lt" to "Lithuanian", "lv" to "Latvian", "nl" to "Dutch", "pl" to "Polish",
        "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian", "sv" to "Swedish",
        "ta" to "Tamil", "te" to "Telugu", "tr" to "Turkish", "uk" to "Ukrainian",
        "ur" to "Urdu", "vi" to "Vietnamese", "zh" to "Chinese",
    )

    /** dictionary.js `normalizeLookupWord`. */
    fun normalizeLookupWord(input: String?): String {
        val normalized = esTrim(Normalizer.normalize(input ?: "", Normalizer.Form.NFC))
        val stripped = normalized
            .replace(Regex("^[^\\p{L}\\p{M}\\p{N}]+"), "")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}'’\\-]+$"), "")
        return stripped.take(120)
    }

    /** dictionary.js `buildKaikkiUrl`. Splits the leading characters by code point, not UTF-16 unit. */
    fun buildKaikkiUrl(input: String, languageCode: String): String? {
        val word = normalizeLookupWord(input)
        val language = languageNames[languageCode] ?: return null
        if (word.isEmpty()) return null

        val codePoints = word.codePoints().toArray()
        val first = String(Character.toChars(codePoints[0]))
        val firstTwo = codePoints.take(2).joinToString("") { String(Character.toChars(it)) }
        val path = listOf(language, "meaning", first, firstTwo, "$word.jsonl")
            .joinToString("/") { encodeUriComponent(it) }
        return "https://kaikki.org/dictionary/$path"
    }

    /** dictionary.js `parseKaikkiJsonl`. Returns null when the payload holds no usable record. */
    fun parseKaikkiJsonl(text: String, requestedWord: String = "", languageCode: String = ""): DictResult? {
        val records = text.split(Regex("\r?\n"))
            .filter { it.isNotEmpty() }
            .take(30)
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
        if (records.isEmpty()) return null

        val entries = records.take(8).map { parseEntry(it, requestedWord) }
        val first = records.first()
        return DictResult(
            word = first.stringOr("word", requestedWord),
            language = first.stringOr("lang", languageNames[languageCode] ?: languageCode),
            languageCode = first.stringOr("lang_code", languageCode),
            entries = entries.filter { it.definitions.isNotEmpty() },
        )
    }

    private fun parseEntry(record: JSONObject, requestedWord: String): DictEntry {
        val senses = record.objectList("senses")
        val definitions = senses
            .filter { it.stringList("glosses").isNotEmpty() }
            .take(6)
            .map { sense ->
                Sense(
                    gloss = sense.stringList("glosses")[0],
                    tags = (sense.stringList("tags") + sense.stringList("raw_tags")).distinct().take(8),
                    examples = sense.objectList("examples")
                        .take(2)
                        .map {
                            Example(
                                text = it.stringOr("text", ""),
                                translation = it.stringOr("english", "").ifEmpty { it.stringOr("translation", "") },
                            )
                        }
                        .filter { it.text.isNotEmpty() },
                )
            }

        // Only table rows. The raw array also holds derived words, diminutives and the like, which
        // are not cells of a table and would crowd one out.
        val tableForms = record.objectList("forms")
            .filter { it.truthy("form") }
            .filter { form -> form.optString("source") in TABLE_SOURCES }
            .map { form: JSONObject ->
                InflectedForm(
                    form = form.optString("form"),
                    tags = form.optJSONArray("tags")?.let { tags ->
                        (0 until tags.length()).map { tags.optString(it) }
                    }.orEmpty(),
                    source = form.optString("source").takeIf { it.isNotEmpty() },
                )
            }

        // `{word, sense}` objects, repeated across senses in the payload.
        fun wordList(key: String): List<String> =
            record.objectList(key)
                .mapNotNull { it.optString("word").takeIf(String::isNotEmpty) }

        val sounds = record.objectList("sounds")
        val head = record.objectList("head_templates").firstOrNull { it.truthy("expansion") }
        val formOf = senses.asSequence()
            .flatMap { it.objectList("form_of") + it.objectList("alt_of") }
            .firstOrNull { it.truthy("word") }

        return DictEntry(
            word = record.stringOr("word", requestedWord),
            partOfSpeech = record.stringOr("pos", ""),
            head = head?.optString("expansion") ?: "",
            ipa = sounds.firstOrNull { it.truthy("ipa") }?.optString("ipa") ?: "",
            audioUrl = sounds.firstOrNull { it.truthy("mp3_url") }?.optString("mp3_url") ?: "",
            formOf = formOf?.optString("word") ?: "",
            definitions = definitions,
            forms = tableForms,
            etymology = WordRelations.parseEtymology(record.optString("etymology_text")),
            related = WordRelations.relatedWords(
                antonyms = wordList("antonyms"),
                related = wordList("related"),
                derived = wordList("derived"),
            ),
        )
    }
}

/** Kaikki's `source` values that mean "this row belongs to an inflection table". */
private val TABLE_SOURCES = setOf("conjugation", "declension")

/** JS truthiness for a string field: absent, null and "" all fall through to the fallback. */
private fun JSONObject.truthy(key: String): Boolean = optString(key).isNotEmpty()

private fun JSONObject.stringOr(key: String, fallback: String): String =
    optString(key).ifEmpty { fallback }

private fun JSONObject.objectList(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }
}
