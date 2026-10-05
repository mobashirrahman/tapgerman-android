# Play Store launch — TapGerman (personal account, 1.0.0)

Package: `com.tapgerman.reader` · versionCode **4**, versionName **1.0.0** ·
AAB: `app/build/outputs/bundle/release/app-release.aab` (copy to `dist/` before upload).

> Rename note: GlossLine → TapGerman is a **full identity break** (third after
> LexiCue → LingoDeck → GlossLine). Application id, note type (`TapGerman Context v2`),
> `tapgerman-v1-` StableId prefix, and `tapgerman` tag all changed. A word saved before
> and after lands on two different Anki notes. The extension's Anki identity
> (`extension/src/anki.js` + TSV tag/filename in `extension/popup.js`) was renamed
> together with this app and both suites pass (73 extension + 236 Android tests);
> `goldens.json`/`AnkiTemplates.kt` were regenerated from the renamed extension.
> The extension's remaining UI strings (popup text, manifest, page channel) still say
> GlossLine — a separate full-rebrand task that does not affect card identity.

## 0. Name check (do before upload)

- [x] Name checks: Play Console search for "TapGerman" still recommended at upload
      time. Trademark clearance explicitly waived — free open-source project
      (note: this leaves a residual risk if a mark owner ever objects, in which
      case the remedy is another rename).
- [x] Play title (≤30 chars): **`TapGerman – German Reader`** (25, decided).
      Launcher label stays `TapGerman`.

## 1. Play Console setup ($25 one-time, personal account)

1. Create/pick the developer account. Personal accounts need identity verification.
2. Create app → name `TapGerman – German Reader`, default language English (US),
   category **Education**, type App, free.
3. Contact email (required, shown on listing): `tapgerman.support@gmail.com`
   (dedicated address, created).
4. Publish the privacy policy on the **new standalone TapGerman page** (decided —
   not the GlossLine site) and set that privacy-policy URL, e.g.
   `https://___/privacy` (Play fetches it — the URL must be live before submission).

## 2. Upload (personal accounts: closed testing first)

New personal accounts **cannot ship production on day one**: run a closed test with
**≥12 testers for 14 days** first.

1. Release → Closed testing → create track, upload
   `app-release.aab` (versionCode 4). Play App Signing enrolls on first upload —
   keep `release.jks` (alias `glossline`, pre-rename key) backed up offline; it is
   the upload key and sideload testers upgrade cleanly only with it.
2. Add ≥12 tester emails (Google Groups or lists), share the opt-in link.
3. After 14 days with testers opted in, apply for production access, then promote
   the same AAB to production (staged rollout recommended: 20% → 100%).

## 3. Questionnaires (fill in Console)

- **Content rating (IARC):** Everyone. No violence, sexual content, language,
  gambling, ads, or user interaction beyond local Anki export. No "Designed for
  Families" (general audience, not directed at children).
- **Target audience:** 18+ general (or 13+ if you prefer); not directed at children.
- **Data safety:** No data collected by the developer, no data shared with third
  parties, no analytics/ads SDKs (deps: AndroidX + jsoup + coroutines only).
  Disclose user-triggered network use in the privacy policy: single-word Kaikki
  lookups, article fetches from the article's own site, Wikimedia audio on hold.
  Answer the Console form literally — if it counts IP/lookup transmission as
  "collection", declare it rather than claiming zero transmission.
- **Permissions:** `INTERNET` (article fetch, dictionary, audio) +
  `com.ichi2.anki.permission.READ_WRITE_DATABASE` (local AnkiDroid provider).
  No location, camera, storage, or foreground-service permissions.
- **News:** not a news publisher (reads pages the user shares) — no News policy.
- **16 KB page size:** no action — no bundled native code of ours (only current
  AndroidX `.so`s); Play will flag if anything changes.

## 4. Store listing (paste-ready drafts)

- **Short description (80 chars):**
  `Read German articles. Tap any word, keep it, send it to Anki.` (60)
- **Full description:**
  ```
  TapGerman is a German reader for English speakers.

  Share any German article from your browser and read it in German. Every word
  is tappable: tap to see the lemma, part of speech, pronunciation, and English
  glosses with translated examples, plus conjugation and declension tables.

  Save the meaning you actually met — with the exact sentence it came from —
  and send it to AnkiDroid. The same word in the same sense grows one card;
  a different sense gets its own card. Export TSV when AnkiDroid is not installed.

  • German → English dictionary (Kaikki / English Wiktionary, CC BY-SA 4.0)
  • Tap for meaning, hold to hear a native-speaker recording or device voice
  • Works on newspaper and web articles shared from any browser
  • No account, no analytics, no ads. Articles and words stay on your device.
  ```
- **Category:** Education. **Tags:** German, vocabulary, flashcards, reading.
- **Graphics:** icon 512×512 (from `tools/render_icon.py`), feature graphic
  1024×500: `play-assets/feature-graphic-1024x500.png` ✅ (generated by
  `tools/render_feature_graphic.py`; no wordmark — no font rasteriser on this
  machine — overlay "TapGerman – German Reader" before upload if wanted).
  Phone screenshots ≥2: `screenshots/library.png` + `screenshots/lookup.png`
  (both 1080×2400 — compliant) ✅. Recommended before production: add Words +
  reader screenshots re-taken post-rename.
- **Contact:** tapgerman.support@gmail.com + `https://github.com/mobashirrahman/tapgerman-android`.

## 5. Pre-upload verification (run every release)

```bash
./gradlew :app:testDebugUnitTest   # 236 tests incl. Anki parity
./gradlew :app:lintDebug            # 0 issues
./gradlew :app:bundleRelease        # → app/build/outputs/bundle/release/app-release.aab
cp app/build/outputs/bundle/release/app-release.aab dist/tapgerman-1.0.0.aab
```

Upload key (pre-rename `release.jks`, alias `glossline`, kept so sideload testers
upgrade cleanly): SHA-256
`45:95:2E:D1:9B:35:D4:EE:CB:2C:D1:1B:58:19:06:FF:33:00:33:2C:EF:0E:C9:9F:01:2F:D2:DF:43:53:88:C0`,
valid to 2056.

- [ ] `versionCode` bumped every upload (Play rejects repeats).
- [ ] `release.jks` + `keystore.properties` present (gitignored, backed up off-machine).
- [ ] Screenshots show current name (re-take after rename — old shots say GlossLine).
- [ ] Extension rename shipped or explicitly deferred (cards split until it ships).
