# Privacy policy — TapGerman for Android

Last updated: 2026-10-05.

TapGerman is an open-source German reading app. It has no accounts, no analytics,
no telemetry, no advertising, and no server operated by the project.

## What stays on your device

- **Articles** — pages you open, in app-private storage. Capped at 50 articles.
- **Saved vocabulary** — only words you explicitly save: the word, its lemma, the
  sentence it came from, glosses, and a timestamp. Capped at 1000 entries.
- **Settings** — theme, text size, deck name, and preferences, in DataStore.
- **Anki export files** — TSV files written to app-private cache and handed to the
  system share sheet.

## What leaves your device

Nothing leaves unless a feature you use requires it:

| Destination | What is sent | When |
| --- | --- | --- |
| `kaikki.org` | The single word you tap | Every dictionary lookup |
| `upload.wikimedia.org` | A pronunciation recording request | Only when you hold a word and Wiktionary has a native-speaker recording (disable in Settings to use the device voice only) |
| AnkiDroid on your device (`content://com.ichi2.anki.flashcards`) | The card you chose to send | Only when you use Send to Anki |
| The article's own website | A normal page fetch | Only when you open or re-read an article |

The article body is never sent to a dictionary, translation, or analytics service.
No translation service is contacted: usage-example translations come from the
dictionary entry itself.

## What TapGerman never does

- No accounts, no sign-in, no analytics, no crash reporting SDKs, no advertisers.
- It does not sell or share data, because it collects none.
- It does not read cookies, credentials, or account details.
- Anki export is a local file; AnkiDroid communication never leaves your device.

## Dictionary content

Definitions come from [Kaikki](https://kaikki.org/)'s English Wiktionary extraction,
CC BY-SA 4.0. Requests you trigger are subject to Kaikki/Wiktionary privacy practices.
Attribution is shown in the app (reader footer, word list, Settings → About).

## Your control

Delete individual words in the Words tab, clear all words or the article history in
Settings, or uninstall the app to delete everything. Cards already sent to AnkiDroid
live in AnkiDroid and must be deleted there.

## Questions

Open an issue at https://github.com/mobashirrahman/tapgerman-android/issues.
