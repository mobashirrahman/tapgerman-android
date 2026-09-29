// Generates src/test/resources/goldens.json by executing the *real* GlossLine web-extension
// modules. The Android unit tests then assert byte-identical output, so a Kotlin refactor can
// never silently drift from the card identity and dictionary URLs that the Chrome extension uses.
//
// Run: node tools/generate-goldens.mjs
// Requires the GlossLine extension as a sibling checkout. The directory is still named
// lingodeck on disk; the project inside it was renamed.

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const ext = resolve(here, "../../lingodeck/extension/src");

const anki = await import(pathToFileURL(resolve(ext, "anki.js")));
const dict = await import(pathToFileURL(resolve(ext, "dictionary.js")));

const card = (over = {}) => ({
  word: "Häuser",
  lemma: "Haus",
  definitions: ["house", "building"],
  meaning: "house",
  sentence: "Das Haus ist groß.",
  translation: "",
  grammar: "noun",
  partOfSpeech: "Noun",
  ipa: "/ˈhaʊ̯zɐ/",
  languageCode: "de",
  source: "tagesschau.de via GlossLine Reader",
  ...over
});

const cards = {
  plain: card({}),
  noLemma: card({ lemma: "Häuser", word: "Häuser" }),
  quote: card({
    word: 'Schloss"',
    lemma: 'Schloss"',
    definitions: ['"castle"'],
    meaning: '"castle"',
    sentence: 'Das Schloss "leuchtet".',
    translation: 'A "b"\\c'
  }),
  homonym: card({
    word: "Schloss",
    lemma: "Schloss",
    definitions: ["lock"],
    meaning: "lock",
    sentence: "Das Schloss klemmt.",
    grammar: "noun",
    partOfSpeech: "Noun"
  }),
  empty: card({
    word: "Wort",
    lemma: "Wort",
    definitions: [],
    meaning: "word",
    partOfSpeech: "",
    grammar: "",
    sentence: "Ein Wort genügt.",
    translation: "",
    ipa: ""
  })
};

const goldens = {
  generatedFrom: "lingodeck/extension/src",
  // The exact card objects the identity hashes were computed from, so the Kotlin side never has to
  // re-type them and drift into a different shape.
  cards,
  normalizeLookupWord: [
    ["  Häuser  ", dict.normalizeLookupWord("  Häuser  ")],
    ["«Die Zeit»", dict.normalizeLookupWord("«Die Zeit»")],
    ["„Straße,", dict.normalizeLookupWord("„Straße,")],
    ["a'b-c", dict.normalizeLookupWord("a'b-c")],
    ["!!!", dict.normalizeLookupWord("!!!")],
    ["", dict.normalizeLookupWord("")],
    [null, dict.normalizeLookupWord(null)],
    ["ß", dict.normalizeLookupWord("ß")]
  ],
  buildKaikkiUrl: [
    ["Häuser", "de", dict.buildKaikkiUrl("Häuser", "de")],
    ["ö", "de", dict.buildKaikkiUrl("ö", "de")],
    ["École", "fr", dict.buildKaikkiUrl("École", "fr")],
    ["!!!", "de", dict.buildKaikkiUrl("!!!", "de")],
    ["word", "xx", dict.buildKaikkiUrl("word", "xx")],
    ["it's-ok", "de", dict.buildKaikkiUrl("it's-ok", "de")]
  ],
  buildAnkiStableId: Object.fromEntries(
    Object.entries(cards).map(([k, v]) => [k, anki.buildAnkiStableId(v)])
  ),
  buildSenseTag: Object.fromEntries(
    Object.entries(cards).map(([k, v]) => [k, anki.buildSenseTag(v)])
  ),
  buildLanguageTag: [
    ["de", anki.buildLanguageTag("de")],
    ["en-US", anki.buildLanguageTag("en-US")],
    ["we ird!", anki.buildLanguageTag("we ird!")],
    ["", anki.buildLanguageTag("")]
  ],
  escapeHtml: [
    ['<b>&"\'', anki.escapeHtml('<b>&"\'')],
    ["Häuser & Söhne", anki.escapeHtml("Häuser & Söhne")]
  ],
  toAnkiTsvCell: [
    ["=1+1", anki.toAnkiTsvCell("=1+1")],
    ["@cmd", anki.toAnkiTsvCell("@cmd")],
    ["normal", anki.toAnkiTsvCell("normal")],
    ["a\tb\nc", anki.toAnkiTsvCell("a\tb\nc")],
    ["  -5", anki.toAnkiTsvCell("  -5")],
    ["<x>", anki.toAnkiTsvCell("<x>")]
  ],
  highlightSurface: [
    ["Das Haus ist groß.", "Haus", [], anki.highlightSurface("Das Haus ist groß.", "Haus")],
    ["Der Häuser wegen.", "Häuser", ["Haus"], anki.highlightSurface("Der Häuser wegen.", "Häuser", "Haus")],
    ["Die Hauskatze.", "Haus", [], anki.highlightSurface("Die Hauskatze.", "Haus")],
    ["Das <b>Haus</b> & Co.", "Haus", [], anki.highlightSurface("Das <b>Haus</b> & Co.", "Haus")]
  ],
  buildAnkiNote: Object.fromEntries(
    Object.entries(cards).map(([k, v]) => [k, anki.buildAnkiNote(v, "GlossLine", "")])
  ),
  buildAnkiNoteAudio: anki.buildAnkiNote(cards.plain, "GlossLine", "[sound:x.mp3]"),
  constants: {
    ANKI_MODEL: anki.ANKI_MODEL,
    ANKI_CARD_TEMPLATE: anki.ANKI_CARD_TEMPLATE,
    ANKI_FIELDS: anki.ANKI_FIELDS,
    FRONT_TEMPLATE: anki.FRONT_TEMPLATE,
    BACK_TEMPLATE: anki.BACK_TEMPLATE,
    MODEL_CSS: anki.MODEL_CSS
  }
};

// Kaikki parsing is exercised through a fixture the Kotlin parser must reproduce exactly.
const kaikkiSample = [
  JSON.stringify({
    word: "Haus",
    lang: "German",
    lang_code: "de",
    pos: "noun",
    head_templates: [{ expansion: "Haus n (strong, genitive Hauses)" }],
    sounds: [{ ipa: "/haʊ̯s/" }, { mp3_url: "https://upload.wikimedia.org/haus.mp3" }],
    senses: [
      {
        glosses: ["house", "building"],
        tags: ["archaic"],
        raw_tags: ["in compounds"],
        examples: [{ text: "Das Haus ist groß.", english: "The house is big." }]
      },
      {
        glosses: ["(music) house music"]
      },
      {
        glosses: []
      }
    ]
  }),
  JSON.stringify({
    word: "Häuser",
    lang: "German",
    lang_code: "de",
    pos: "noun",
    senses: [
      {
        glosses: ["plural of Haus"],
        form_of: [{ word: "Haus" }]
      }
    ]
  }),
  "{not json",
  JSON.stringify({
    word: "Haus",
    pos: "verb",
    senses: [{ glosses: ["to house"] }]
  })
].join("\n");
goldens.kaikkiSample = kaikkiSample;
goldens.parseKaikkiJsonl = dict.parseKaikkiJsonl(kaikkiSample, "Haus", "de");
goldens.parseKaikkiJsonlEmpty = dict.parseKaikkiJsonl("", "Haus", "de");
goldens.parseKaikkiJsonlJunk = dict.parseKaikkiJsonl("{nope}\n", "Haus", "de");

const out = resolve(here, "../app/src/test/resources/goldens.json");
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(goldens, null, 2) + "\n");
console.log("wrote", out);
