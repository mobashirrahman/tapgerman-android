package com.glossline.reader.anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Writes cards to AnkiDroid through its flashcards ContentProvider.
 *
 * This is a hand-rolled port of the bits of AnkiDroid's own `AddContentApi` that the reader needs,
 * against the published `FlashCardsContract` (`content://com.ichi2.anki.flashcards`, decks / models /
 * notes / notes/<id>/cards/<ord>). Going straight at the provider keeps the app free of the
 * JitPack `Anki-Android:api` artifact, which is built from source on demand and has historically
 * been the flakiest dependency in the Anki ecosystem.
 *
 * Fields are joined with Anki's 0x1F separator. Tags are space-separated with spaces inside a tag
 * folded to underscores, exactly as AnkiDroid's `Utils.joinTags` does.
 */
object AnkiDroid {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")
    private val DECKS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")
    private val MODELS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
    private val NOTES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")

    private const val FIELD_SEPARATOR = "\u001F"

    private const val COL_DECK_ID = "deck_id"
    private const val COL_DECK_NAME = "deck_name"
    private const val COL_MODEL_ID = "_id"
    private const val COL_MODEL_NAME = "name"
    private const val COL_MODEL_FIELD_NAMES = "field_names"
    private const val COL_MODEL_NUM_CARDS = "num_cards"
    private const val COL_MODEL_CSS = "css"
    private const val COL_MODEL_DECK_ID = "deck_id"
    private const val COL_MODEL_SORT_FIELD_INDEX = "sort_field_index"
    private const val COL_NOTE_ID = "_id"
    private const val COL_NOTE_MID = "mid"
    private const val COL_NOTE_FLDS = "flds"
    private const val COL_NOTE_TAGS = "tags"
    private const val COL_CARD_ORD = "ord"

    private const val TEMPLATE_NAME = "card_template_name"
    private const val TEMPLATE_QUESTION = "question_format"
    private const val TEMPLATE_ANSWER = "answer_format"

    const val DEFAULT_DECK = "GlossLine"

    /** A success-or-message outcome. Named so it cannot be confused with `kotlin.Result`. */
    sealed class AnkiResult<out T> {
        data class Ok<T>(val value: T) : AnkiResult<T>()
        data class Failed(val message: String) : AnkiResult<Nothing>()
    }

    data class ModelInfo(val id: Long, val name: String, val fieldNames: List<String>)

    /** True when AnkiDroid is installed and exposes its provider. */
    fun isInstalled(context: Context): Boolean =
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun queryDecks(context: Context): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        try {
            context.contentResolver.query(DECKS_URI, null, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(COL_DECK_ID)
                val nameIndex = cursor.getColumnIndex(COL_DECK_NAME)
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0) continue
                    out[cursor.getLong(idIndex)] = cursor.getString(nameIndex) ?: continue
                }
            }
        } catch (_: Exception) {
            // Surfaced as an empty map; the caller reports "AnkiDroid unavailable".
        }
        return out
    }

    fun queryModels(context: Context): List<ModelInfo> {
        val out = ArrayList<ModelInfo>()
        try {
            context.contentResolver.query(MODELS_URI, null, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(COL_MODEL_ID)
                val nameIndex = cursor.getColumnIndex(COL_MODEL_NAME)
                val fieldsIndex = cursor.getColumnIndex(COL_MODEL_FIELD_NAMES)
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0) continue
                    val raw = if (fieldsIndex >= 0) cursor.getString(fieldsIndex) else null
                    out.add(
                        ModelInfo(
                            id = cursor.getLong(idIndex),
                            name = cursor.getString(nameIndex) ?: continue,
                            fieldNames = raw?.split(FIELD_SEPARATOR) ?: emptyList(),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            // Empty list means "no usable note type".
        }
        return out
    }

    /** Finds the named deck, creating it when missing. */
    fun ensureDeck(context: Context, deckName: String): AnkiResult<Long> {
        try {
            queryDecks(context).entries.firstOrNull { it.value == deckName }?.let {
                return AnkiResult.Ok(it.key)
            }
            val values = ContentValues().apply { put(COL_DECK_NAME, deckName) }
            val uri = context.contentResolver.insert(DECKS_URI, values)
                ?: return AnkiResult.Failed("AnkiDroid refused to create the deck \"$deckName\".")
            val id = uri.lastPathSegment?.toLongOrNull()
                ?: return AnkiResult.Failed("AnkiDroid created the deck but did not report its id.")
            return AnkiResult.Ok(id)
        } catch (error: Exception) {
            return AnkiResult.Failed(describe(error))
        }
    }

    /**
     * Finds the note type named [modelName], creating it when missing. An existing note type with a
     * different field layout is reported as a failure rather than silently reused, because writing
     * against the wrong layout corrupts cards.
     */
    fun ensureModel(context: Context, modelName: String, deckId: Long): AnkiResult<Long> {
        try {
            val existing = queryModels(context).firstOrNull { it.name == modelName }
            if (existing != null) {
                return if (existing.fieldNames.isEmpty() || existing.fieldNames == AnkiTemplates.FIELDS) {
                    AnkiResult.Ok(existing.id)
                } else {
                    AnkiResult.Failed(
                        "The note type \"$modelName\" exists in AnkiDroid but has a different field layout. " +
                            "Rename or delete it there, then try again.",
                    )
                }
            }

            val values = ContentValues().apply {
                put(COL_MODEL_NAME, modelName)
                put(COL_MODEL_FIELD_NAMES, AnkiTemplates.FIELDS.joinToString(FIELD_SEPARATOR))
                put(COL_MODEL_NUM_CARDS, 1)
                put(COL_MODEL_CSS, AnkiTemplates.MODEL_CSS)
                put(COL_MODEL_DECK_ID, deckId)
                put(COL_MODEL_SORT_FIELD_INDEX, 0)
            }
            val modelUri = context.contentResolver.insert(MODELS_URI, values)
                ?: return AnkiResult.Failed("AnkiDroid refused to create the note type \"$modelName\".")
            val modelId = modelUri.lastPathSegment?.toLongOrNull()
                ?: return AnkiResult.Failed("AnkiDroid created the note type but did not report its id.")

            val templateValues = ContentValues().apply {
                put(TEMPLATE_NAME, AnkiTemplates.CARD_TEMPLATE_NAME)
                put(TEMPLATE_QUESTION, AnkiTemplates.FRONT_TEMPLATE)
                put(TEMPLATE_ANSWER, AnkiTemplates.BACK_TEMPLATE)
            }
            context.contentResolver.update(
                Uri.withAppendedPath(Uri.withAppendedPath(modelUri, "templates"), "0"),
                templateValues,
                null,
                null,
            )
            return AnkiResult.Ok(modelId)
        } catch (error: Exception) {
            return AnkiResult.Failed(describe(error))
        }
    }

    /**
     * Best-effort lookup of an existing note, so "cards that grow": the same word in the same sense
     * accumulates sentences on one note instead of spawning duplicates.
     *
     * Uses Anki's own browser search syntax on the `notes` URI, mirroring the extension's
     * `findExistingAnkiNote`. Any failure here is non-fatal — the caller falls back to adding a note.
     */
    fun findExistingNoteId(context: Context, query: String): Long? = try {
        context.contentResolver.query(NOTES_URI, arrayOf(COL_NOTE_ID), query, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(COL_NOTE_ID)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else null
        }
    } catch (_: Exception) {
        null
    }

    fun readNoteFields(context: Context, noteId: Long): List<String>? = try {
        val uri = Uri.withAppendedPath(NOTES_URI, noteId.toString())
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(COL_NOTE_FLDS)
            if (cursor.moveToFirst() && index >= 0) {
                cursor.getString(index)?.split(FIELD_SEPARATOR)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Replaces only the named fields of an existing note, leaving the rest untouched. Returns true
     * when the note is unchanged or was written.
     */
    fun updateNoteFields(context: Context, noteId: Long, replacements: Map<String, String>): Boolean {
        val current = readNoteFields(context, noteId) ?: return false
        val merged = current.toMutableList()
        var changed = false
        AnkiTemplates.FIELDS.forEachIndexed { index, name ->
            val replacement = replacements[name] ?: return@forEachIndexed
            if (index < merged.size && merged[index] != replacement) {
                merged[index] = replacement
                changed = true
            }
        }
        if (!changed) return true
        val payload = ContentValues().apply { put(COL_NOTE_FLDS, merged.joinToString(FIELD_SEPARATOR)) }
        return try {
            context.contentResolver.update(
                Uri.withAppendedPath(NOTES_URI, noteId.toString()),
                payload,
                null,
                null,
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Creates the note, then moves each generated card into [deckId]. */
    fun addNote(context: Context, deckId: Long, modelId: Long, note: AnkiNote): AnkiResult<Long> {
        try {
            val values = ContentValues().apply {
                put(COL_NOTE_MID, modelId)
                put(COL_NOTE_FLDS, note.joinedFields())
                put(COL_NOTE_TAGS, note.tags.joinToString(" ") { it.replace(" ", "_") })
            }
            val noteUri = context.contentResolver.insert(NOTES_URI, values)
                ?: return AnkiResult.Failed("AnkiDroid refused to add the note.")
            val noteId = noteUri.lastPathSegment?.toLongOrNull()
                ?: return AnkiResult.Failed("AnkiDroid added the note but did not report its id.")
            moveCardsToDeck(context, noteId, deckId)
            return AnkiResult.Ok(noteId)
        } catch (error: Exception) {
            return AnkiResult.Failed(describe(error))
        }
    }

    private fun moveCardsToDeck(context: Context, noteId: Long, deckId: Long) {
        val cardsUri = Uri.withAppendedPath(Uri.withAppendedPath(NOTES_URI, noteId.toString()), "cards")
        val orders = ArrayList<String>()
        try {
            context.contentResolver.query(cardsUri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(COL_CARD_ORD)
                while (cursor.moveToNext()) {
                    if (index >= 0) cursor.getString(index)?.let { orders.add(it) }
                }
            }
        } catch (_: Exception) {
            return
        }
        for (ord in orders) {
            val values = ContentValues().apply { put(COL_DECK_ID, deckId) }
            try {
                context.contentResolver.update(Uri.withAppendedPath(cardsUri, ord), values, null, null)
            } catch (_: Exception) {
                // The note is still usable; worst case it lands in AnkiDroid's default deck.
            }
        }
    }

    /** anki.js `escapeAnkiSearchValue` — Anki search wildcards and quotes get backslash-escaped. */
    fun escapeSearchValue(value: String?): String =
        (value ?: "").replace(Regex("[\\\\\"*_]"), "\\\\$0")

    /** Builds the browser query the extension uses to find a note for the same word and sense. */
    fun buildNoteQuery(deckName: String, languageTag: String, senseTag: String, key: String): String =
        listOf(
            "deck:\"${escapeSearchValue(deckName)}\"",
            "note:\"${escapeSearchValue(AnkiTemplates.MODEL_NAME)}\"",
            "tag:\"${escapeSearchValue(languageTag)}\"",
            "tag:\"${escapeSearchValue(senseTag)}\"",
            "(\"Surface:${escapeSearchValue(key)}\" OR \"Lemma:${escapeSearchValue(key)}\")",
        ).joinToString(" ")

    private fun describe(error: Throwable): String = when (error) {
        is SecurityException ->
            "AnkiDroid denied access. Grant GlossLine the AnkiDroid permission, then try again."
        is IllegalStateException ->
            "AnkiDroid is installed but its storage is not set up yet. Open AnkiDroid once first."
        else -> error.message ?: "AnkiDroid could not be reached."
    }
}
