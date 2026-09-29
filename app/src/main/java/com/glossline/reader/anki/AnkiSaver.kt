package com.glossline.reader.anki

import android.content.Context
import com.glossline.reader.data.VocabItem

/**
 * High-level "save this word to Anki" flow: create deck/note type if needed, then either create a
 * note or append the new sentence to the note that already holds this word in this sense.
 *
 * Mirrors `addCardToAnki` / `appendSentenceToNote` in `extension/src/anki.js`, including the rule
 * that only the per-sentence fields grow — word-level facts (Meaning, Grammar, Reading) are left
 * untouched so a repeated save never rewrites what the learner already studied.
 */
object AnkiSaver {

    sealed class SaveOutcome {
        data class Created(val noteId: Long) : SaveOutcome()
        data class Appended(val noteId: Long) : SaveOutcome()
        data class AlreadyPresent(val noteId: Long) : SaveOutcome()
        data class Failed(val message: String) : SaveOutcome()
    }

    fun save(context: Context, item: VocabItem, deckName: String = AnkiDroid.DEFAULT_DECK): SaveOutcome {
        if (!AnkiDroid.isInstalled(context)) {
            return SaveOutcome.Failed("AnkiDroid is not installed. Export the word as TSV instead.")
        }
        if (!AnkiDroid.hasPermission(context)) {
            return SaveOutcome.Failed("Grant GlossLine the AnkiDroid permission in system settings, then try again.")
        }

        val deck = when (val result = AnkiDroid.ensureDeck(context, deckName)) {
            is AnkiDroid.AnkiResult.Failed -> return SaveOutcome.Failed(result.message)
            is AnkiDroid.AnkiResult.Ok -> result.value
        }
        val model = when (val result = AnkiDroid.ensureModel(context, AnkiTemplates.MODEL_NAME, deck)) {
            is AnkiDroid.AnkiResult.Failed -> return SaveOutcome.Failed(result.message)
            is AnkiDroid.AnkiResult.Ok -> result.value
        }

        val card = item.toCard()
        val existingId = findExisting(context, deckName, card, item.languageCode)

        if (existingId != null) {
            val appended = appendSentence(context, existingId, card)
            return if (appended == null) {
                SaveOutcome.Failed("Found the existing card but could not update it.")
            } else if (appended) {
                SaveOutcome.Appended(existingId)
            } else {
                SaveOutcome.AlreadyPresent(existingId)
            }
        }

        val note = AnkiCards.buildAnkiNote(card, deckName)
        return when (val result = AnkiDroid.addNote(context, deck, model, note)) {
            is AnkiDroid.AnkiResult.Failed -> SaveOutcome.Failed(result.message)
            is AnkiDroid.AnkiResult.Ok -> SaveOutcome.Created(result.value)
        }
    }

    private fun findExisting(
        context: Context,
        deckName: String,
        card: NoteIdentity.Card,
        languageCode: String,
    ): Long? {
        val key = NoteIdentity.normalizeStablePart(card.lemma.orEmpty().ifEmpty { card.word })
        if (key.isEmpty()) return null
        val query = AnkiDroid.buildNoteQuery(
            deckName = deckName,
            languageTag = NoteIdentity.buildLanguageTag(languageCode),
            senseTag = NoteIdentity.buildSenseTag(card),
            key = key,
        )
        return AnkiDroid.findExistingNoteId(context, query)
    }

    /** Returns null on failure, true when something was appended, false when nothing changed. */
    private fun appendSentence(context: Context, noteId: Long, card: NoteIdentity.Card): Boolean? {
        val current = AnkiDroid.readNoteFields(context, noteId) ?: return null
        val sentenceIndex = AnkiTemplates.FIELDS.indexOf("Sentence")
        val translationIndex = AnkiTemplates.FIELDS.indexOf("Translation")
        val sourceIndex = AnkiTemplates.FIELDS.indexOf("Source")
        if (sentenceIndex < 0) return null

        val existingSentence = current.getOrNull(sentenceIndex) ?: ""
        val newSentenceHtml = AnkiCards.highlightSurface(card.sentence, card.word, card.lemma)
        val newSentencePlain = fieldSegments(newSentenceHtml).firstOrNull() ?: ""
        if (newSentencePlain.isNotEmpty() && fieldSegments(existingSentence).contains(newSentencePlain)) {
            return false
        }

        val replacements = mutableMapOf("Sentence" to appendToField(existingSentence, newSentenceHtml))
        val translation = escapeForField(card.translation.orEmpty())
        if (translation.isNotEmpty()) {
            replacements["Translation"] = appendToField(current.getOrNull(translationIndex) ?: "", translation)
        }
        val source = escapeForField(card.source.orEmpty())
        if (source.isNotEmpty()) {
            replacements["Source"] = appendToField(current.getOrNull(sourceIndex) ?: "", source)
        }
        return AnkiDroid.updateNoteFields(context, noteId, replacements)
    }

    /** anki.js `CONTEXT_SEPARATOR` split on stripped plain text, so highlight markup cannot hide a duplicate. */
    fun fieldSegments(value: String?): List<String> =
        (value ?: "")
            .split(AnkiCards.CONTEXT_SEPARATOR)
            .map { com.glossline.reader.util.esTrim(com.glossline.reader.util.esCollapseWhitespace(it.replace(Regex("<[^>]*>"), ""))) }
            .filter { it.isNotEmpty() }

    private fun appendToField(existing: String, addition: String): String =
        if (existing.isEmpty()) addition else existing + AnkiCards.CONTEXT_SEPARATOR + addition

    private fun escapeForField(value: String): String =
        com.glossline.reader.util.escapeHtml(value)
}
