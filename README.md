# LingoDeck Reader

An Android companion to [LingoDeck](https://github.com/mobashirrahman/lingodeck): the same
word-level language learning loop the Chrome extension does for Prime Video subtitles, applied to
German newspaper and web articles.

Share an article from Chrome. Read it in German. Tap any word to see what it means. Keep the word
together with the sentence it came from. Send it to AnkiDroid.

No full-page machine translation. The article stays in German.

## The loop

1. Find a German article in your browser.
2. **Share → LingoDeck Reader** (or paste the address on the library screen).
3. Read the article in German. Every word is a tap target.
4. Tap a word → a small card opens **next to the word**, not over it: the article stays readable and
   there is no scrim. Pronunciation, **Save word** and **Send to Anki** are pinned at the top of the
   card so they never scroll away. Under them: the lemma, part of speech and IPA, the exact sentence
   the word came from, and its English meaning.
5. If the word is an inflected form ("höchste") the meaning comes from its lemma ("hoch"). If it has
   several senses, pick the one you actually met — the card keeps only that one, so *Schloss* the
   castle and *Schloss* the lock stay on separate Anki cards. A single-sense word just shows the
   definition.
6. **Words → Export TSV** is the fallback when AnkiDroid is not available.

## Building

Open the project in Android Studio (Ladybug or newer) and run. Requirements:

- JDK 17 (Android Studio bundles one)
- `minSdk 26`, `compileSdk 35`
- Two runtime dependencies outside AndroidX: [Jsoup](https://jsoup.org/) for HTML and
  `kotlinx-coroutines`. Storage is JSON in the app's private files directory, and AnkiDroid is
  reached through its published ContentProvider contract — no database, no SDK, no third-party Anki
  library.

```bash
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 59 tests, incl. parity against the web extension
```

## AnkiDroid

Cards use the note type **`LingoDeck Context v2`** with the same ten fields, templates and CSS as
the Chrome extension, so a card saved on the phone looks exactly like one saved from the browser.

The app talks to `content://com.ichi2.anki.flashcards` directly (AnkiDroid's published
`FlashCardsContract`) and declares `com.ichi2.anki.permission.READ_WRITE_DATABASE`. On first
**Send to Anki** Android asks for that permission.

"Cards that grow" works the same way as on desktop: saving the same word in the same sense appends
the new sentence to the existing note, while a genuinely different sense (Schloss the castle vs.
Schloss the lock) gets its own card. Note identity is a port of the extension's `buildAnkiStableId`
and `buildSenseTag`, and the tests below are what prove the two agree.

If AnkiDroid is not installed, **Words → Export TSV** produces the same `#separator:Tab` /
`#html:true` / `#columns:…` file the extension exports.

## Dictionary

Definitions come from [Kaikki](https://kaikki.org/)'s English Wiktionary extraction
(`https://kaikki.org/dictionary/…`), CC BY-SA 4.0. Only the tapped word is sent; the article body
never leaves the device. Lookups are cached in memory for 24h, and missing entries are cached
negatively for 5 min so they are not hammered.

## Parity with the Chrome extension

The interesting requirement here is that a word saved on the phone and the same word saved from the
browser must land on one Anki note. So the parts that decide card identity are not reimplemented
from memory — they are generated from the extension and asserted against it:

| Piece | Ported from | Verified by |
| --- | --- | --- |
| `buildAnkiStableId`, `buildSenseTag`, `buildLanguageTag`, `normalizeStablePart` | `extension/src/anki.js` | `NoteIdentityTest` against `goldens.json` |
| `escapeHtml`, `escapeRegExp`, `highlightSurface`, `toAnkiTsvCell`, `buildMeaning`, `buildAnkiNote` | `extension/src/anki.js` | `AnkiCardsTest` against `goldens.json` |
| Note type CSS and card templates | `extension/src/anki.js` | `AnkiTemplates.kt` is **generated** from it |
| `normalizeLookupWord`, `buildKaikkiUrl`, `parseKaikkiJsonl` | `extension/src/dictionary.js` | `KaikkiParserTest` against `goldens.json` |
| TSV export preamble and columns | `extension/popup.js` | `AnkiCardsTest` |
| Which glosses a card carries, sense narrowing | `extension/content.js` `renderDictionary` | `LookupCardBuilderTest` |

Two subtleties worth keeping in mind if you touch this code:

- **Whitespace is ECMAScript's, not Java's.** ES `trim()`/`\s` keep NBSP, figure and narrow spaces and
  the BOM in the set and drop U+001C–U+001F; `Character.isWhitespace` does the opposite. Article text
  is full of `&nbsp;`, so `util/Escaping.kt` implements the ES set (`esTrim`, `esTrimStart`,
  `esCollapseWhitespace`) and everything that feeds a hash uses it.
- **`escapeRegExp` is a character loop.** JavaScript treats `[` as a literal inside a character class;
  Java starts a nested class. Re-deriving that regex from the JS source would silently change what
  `highlightSurface` matches.

### Regenerating the shared fixtures

```bash
node tools/generate-goldens.mjs     # runs ../lingodeck/extension/src/{anki,dictionary}.js → goldens.json
node tools/generate-templates.mjs   # goldens.json → AnkiTemplates.kt
```

Both scripts expect the `lingodeck` repository checked out as a sibling directory. Commit the
regenerated `goldens.json` and `AnkiTemplates.kt` together so the two can never drift apart.

## Privacy

- The article URL and body are fetched on demand and stored only in app-private storage. The shelf is
  capped at 50 articles and the word list at 1000 entries.
- Only a tapped word is sent to Kaikki. Nothing is sent anywhere else, and there is no account, no
  analytics, and no sync.
- Anki export is a local file handed to the system share sheet.

## Layout

```
app/src/main/java/com/lingodeck/reader/
  util/Escaping.kt        ES-compatible trim/escape/encode primitives
  text/GermanTokenizer.kt BreakIterator word + sentence segmentation
  dict/KaikkiParser.kt    Kaikki URL + JSONL parsing  (port of dictionary.js)
  dict/KaikkiClient.kt    fetch, bounds, TTL cache, German case ladder
  anki/NoteIdentity.kt    StableId / sense tag hashing  (port of anki.js)
  anki/AnkiTemplates.kt   GENERATED note type CSS + templates
  anki/AnkiCards.kt       field rendering, highlighting, TSV cells  (port of anki.js)
  anki/AnkiDroid.kt       flashcards ContentProvider access
  anki/AnkiSaver.kt       create-or-grow save flow
  anki/TsvExport.kt       Anki-importable TSV  (port of popup.js)
  data/ArticleExtractor.kt  readability scoring
  data/ArticleFetcher.kt    bounded fetch + share-URL parsing
  store/Store.kt          JSON persistence
  ui/                     Compose screens
tools/                    fixture generators
app/src/test/             golden-fixture parity tests
```

## Limitations

- German → English only for now. The dictionary and tokenizer are language-parameterised; the UI is not.
- Paywalled or heavily client-rendered pages extract poorly. The reader tells you and you can fall
  back to the browser.
- Pronunciation audio is stored on the Anki card when Kaikki offers an `mp3_url`; AnkiDroid downloads
  it when it syncs media.

## License

MIT. Dictionary content comes from Kaikki / English Wiktionary under CC BY-SA 4.0; see the
[third-party notices](https://github.com/mobashirrahman/lingodeck/blob/main/THIRD_PARTY_NOTICES.md)
in the main repository.
