package com.glossline.reader.anki

import com.glossline.reader.data.VocabItem

/**
 * Conversion between the card shape used for note identity and the stored vocabulary item.
 *
 * Both directions must agree: `VocabItem.id` *is* `NoteIdentity.buildAnkiStableId` of the card, so
 * a word saved on the phone and the same word saved from the Chrome extension resolve to the same
 * Anki note and merge instead of duplicating.
 */
fun NoteIdentity.Card.toVocabItem(
    articleTitle: String,
    sourceUrl: String,
    source: String,
    createdAt: Long = System.currentTimeMillis(),
): VocabItem = VocabItem(
    id = NoteIdentity.buildAnkiStableId(this),
    createdAt = createdAt,
    word = word,
    lemma = lemma.orEmpty().ifEmpty { word },
    partOfSpeech = partOfSpeech.orEmpty(),
    ipa = ipa.orEmpty(),
    audioUrl = audioUrl.orEmpty(),
    glosses = definitions.orEmpty(),
    meaning = meaning.orEmpty(),
    sentence = sentence.orEmpty(),
    articleTitle = articleTitle,
    sourceUrl = sourceUrl,
    source = source,
    languageCode = languageCode.orEmpty().ifEmpty { "de" },
)

/** Rebuilds the card a stored item stands for. The inverse of [toVocabItem], modulo provenance. */
fun VocabItem.toCard(): NoteIdentity.Card = NoteIdentity.Card(    word = word,
    lemma = lemma,
    definitions = glosses,
    meaning = meaning,
    grammar = partOfSpeech,
    partOfSpeech = partOfSpeech,
    sentence = sentence,
    translation = "",
    languageCode = languageCode,
    ipa = ipa,
    audioUrl = audioUrl,
    source = source,
)

/**
 * `toCard` sets `grammar` as well as `partOfSpeech`, while [NoteIdentity.Card.toVocabItem]
 * round-trips through `partOfSpeech` only. anki.js resolves `grammar || partOfSpeech`, so both
 * produce the same stable id; `NoteIdentityTest` covers that guarantee.
 */
