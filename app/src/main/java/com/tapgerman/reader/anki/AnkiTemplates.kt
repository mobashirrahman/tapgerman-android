package com.tapgerman.reader.anki

/**
 * GENERATED FILE - do not edit.
 *
 * Source: tools/generate-templates.mjs, which copies these strings out of the TapGerman Chrome
 * extension's extension/src/anki.js. Keeping them identical is what lets a card saved from the
 * phone look exactly like a card saved from the browser, and lets both land on one note.
 */
object AnkiTemplates {
    const val MODEL_NAME = """TapGerman Context v2"""
    const val CARD_TEMPLATE_NAME = """Recognition"""

    val FIELDS: List<String> = listOf(
        "StableId",
        "Surface",
        "Lemma",
        "Reading",
        "Audio",
        "Meaning",
        "Sentence",
        "Translation",
        "Grammar",
        "Source",
    )

    val MODEL_CSS: String = """.card{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;font-size:20px;line-height:1.5;text-align:left}
.word{font-size:34px;font-weight:700;line-height:1.2}
.lemma,.reading{font-size:16px;opacity:.65}
.context{margin-top:16px;padding:12px 14px;border-left:4px solid #f7b32b;background:rgba(128,128,128,.14);border-radius:0 6px 6px 0}
.target{background:rgba(247,179,43,.4);border-radius:3px;padding:0 3px;font-weight:700}
.senses{margin:0;padding-left:22px}
.senses li{margin-bottom:4px}
.grammar{margin-top:10px;font-size:16px;opacity:.75}
.audio{margin-top:12px}
.meta{margin-top:16px;font-size:12px;opacity:.55;word-break:break-word}
hr#answer{margin:18px 0;border:none;border-top:1px solid rgba(128,128,128,.4)}
.context-sep{margin:14px 0;border:none;border-top:1px dashed rgba(128,128,128,.35)}"""

    val FRONT_TEMPLATE: String = """<div class="word">{{Surface}}</div>{{#Lemma}}<div class="lemma">{{Lemma}}</div>{{/Lemma}}{{#Reading}}<div class="reading">{{Reading}}</div>{{/Reading}}{{#Sentence}}<div class="context">{{Sentence}}</div>{{/Sentence}}"""

    val BACK_TEMPLATE: String = """{{FrontSide}}<hr id="answer">{{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}<div class="meaning">{{Meaning}}</div>{{#Grammar}}<div class="grammar">{{Grammar}}</div>{{/Grammar}}{{#Translation}}<div class="context">{{Translation}}</div>{{/Translation}}{{#Source}}<div class="meta">{{Source}}</div>{{/Source}}"""
}
