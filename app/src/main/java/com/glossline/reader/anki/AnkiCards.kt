package com.glossline.reader.anki

import com.glossline.reader.util.escapeHtml
import com.glossline.reader.util.escapeRegExp
import com.glossline.reader.util.esTrim
import com.glossline.reader.util.esTrimStart
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Rendering of a card's field values and tags.
 *
 * A port of `extension/src/anki.js`'s `escapeHtml`, `highlightSurface`, `toAnkiTsvCell`,
 * `buildMeaning` and `buildAnkiNote`. Field order comes from [AnkiTemplates.FIELDS], which is
 * generated from the extension, so the two apps always agree on the note layout.
 */
data class AnkiNote(
    val deckName: String,
    val modelName: String,
    /** Insertion-ordered to match `AnkiTemplates.FIELDS`, which is the order the provider needs. */
    val fields: Map<String, String>,
    val tags: List<String>,
) {
    /** Fields joined with Anki's 0x1F separator, ready for the flashcards ContentProvider. */
    fun joinedFields(): String = AnkiTemplates.FIELDS.joinToString("\u001F") { fields[it] ?: "" }

    fun fieldValues(): List<String> = AnkiTemplates.FIELDS.map { fields[it] ?: "" }
}

object AnkiCards {
    const val DEFAULT_SOURCE = "Prime Video via GlossLine"

    /** Only the senses that fit on a card. Reviewing eight glosses teaches recognition of none. */
    const val MAX_CARD_SENSES = 4

    const val CONTEXT_SEPARATOR = "<hr class=\"context-sep\">"

    /**
     * anki.js `highlightSurface`: escape the sentence, then wrap whole-word occurrences of the
     * first candidate that matches. Candidates are tried in order so an inflected surface wins over
     * the lemma.
     *
     * Compiled with `java.util.regex.Pattern` rather than `kotlin.text.Regex` because Kotlin's
     * `RegexOption` has no `UNICODE_CASE`. JavaScript's `/i` with the `/u` flag case-folds
     * non-ASCII ("Häuser" ~ "HÄUSER"); `Pattern.CASE_INSENSITIVE` alone is US-ASCII only and would
     * silently stop matching umlauts.
     */
    fun highlightSurface(sentence: String?, vararg candidates: String?): String {
        val escapedSentence = escapeHtml(sentence)
        for (candidate in candidates) {
            val escapedCandidate = escapeHtml(esTrim(candidate ?: ""))
            if (escapedCandidate.isEmpty()) continue
            val pattern = Pattern.compile(
                "(?<!\\p{L})" + escapeRegExp(escapedCandidate) + "(?!\\p{L})",
                Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
            )
            val matcher = pattern.matcher(escapedSentence)
            if (matcher.find()) {
                matcher.reset()
                val out = StringBuffer()
                while (matcher.find()) {
                    val wrapped = "<span class=\"target\">" + matcher.group() + "</span>"
                    matcher.appendReplacement(out, Matcher.quoteReplacement(wrapped))
                }
                matcher.appendTail(out)
                return out.toString()
            }
        }
        return escapedSentence
    }

    /** anki.js `toAnkiTsvCell`, including the leading-apostrophe guard against spreadsheet formulas. */
    fun toAnkiTsvCell(value: String?): String {
        val raw = value ?: ""
        val escaped = escapeHtml(raw)
            .replace("\t", " ")
            .replace(Regex("\r\n?|\n"), "<br>")
        val firstNonSpace = esTrimStart(raw).firstOrNull()
        val looksLikeFormula = firstNonSpace == '=' || firstNonSpace == '+' ||
            firstNonSpace == '-' || firstNonSpace == '@'
        return if (looksLikeFormula) "'$escaped" else escaped
    }

    /** anki.js `buildMeaning`. */
    fun buildMeaning(card: NoteIdentity.Card): String {
        val definitions = card.definitions
        val source: List<String> =
            if (definitions != null && definitions.isNotEmpty()) definitions else listOf(card.meaning ?: "")
        val glosses = source
            .filter { it.trim().isNotEmpty() }
            .take(MAX_CARD_SENSES)
        return when {
            glosses.isEmpty() -> ""
            glosses.size == 1 -> escapeHtml(glosses[0])
            else -> glosses.joinToString("", prefix = "<ol class=\"senses\">", postfix = "</ol>") {
                "<li>${escapeHtml(it)}</li>"
            }
        }
    }

    /** anki.js `buildAnkiNote`. */
    fun buildAnkiNote(card: NoteIdentity.Card, deckName: String = "GlossLine", audioTag: String = ""): AnkiNote {
        val lemmaDiffers = !card.lemma.isNullOrEmpty() &&
            NoteIdentity.normalizeStablePart(card.lemma) != NoteIdentity.normalizeStablePart(card.word)

        val tags = mutableListOf(
            "glossline",
            NoteIdentity.buildLanguageTag(card.languageCode),
            NoteIdentity.buildSenseTag(card),
        )
        val posTag = NoteIdentity.buildPosTag(card.partOfSpeech)
        if (posTag.isNotEmpty()) tags.add("pos::$posTag")

        val grammar = card.grammar.orEmpty().ifEmpty { card.partOfSpeech.orEmpty() }
        val fields = linkedMapOf(
            "StableId" to NoteIdentity.buildAnkiStableId(card),
            "Surface" to escapeHtml(card.word),
            "Lemma" to (if (lemmaDiffers) escapeHtml(card.lemma) else ""),
            "Reading" to escapeHtml(card.ipa.orEmpty()),
            "Audio" to audioTag,
            "Meaning" to buildMeaning(card),
            "Sentence" to highlightSurface(card.sentence, card.word, card.lemma),
            "Translation" to escapeHtml(card.translation),
            "Grammar" to escapeHtml(grammar),
            "Source" to escapeHtml(card.source.orEmpty().ifEmpty { DEFAULT_SOURCE }),
        )
        return AnkiNote(deckName, AnkiTemplates.MODEL_NAME, fields, tags)
    }
}
