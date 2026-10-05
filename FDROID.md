# Free distribution — TapGerman (no Play Console needed)

Play charges a $25 one-time developer fee with no free tier, so this app ships
through free open-source channels instead. This file is the guide; `PLAY_STORE.md`
stays as the fallback if Play ever becomes an option.

## Channel 1: GitHub Releases + Obtainium (immediate, ~today)

Zero review, zero cost, auto-updates for users via Obtainium.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/tapgerman-1.0.0.apk
git tag v1.0.0
gh release create v1.0.0 dist/tapgerman-1.0.0.apk \
  --title "TapGerman 1.0.0" \
  --notes-file fastlane/metadata/android/en-US/changelogs/4.txt
```

Users install the APK (allow unknown sources once) and optionally add the repo
to Obtainium, which polls GitHub releases and offers updates. Publish `PRIVACY.md`
on the TapGerman page and link it from the release notes.

## Channel 2: F-Droid official repo (free, slow: review queue is weeks–months)

This app fits F-Droid's criteria well: MIT-licensed, builds from source, no
proprietary dependencies (AndroidX + jsoup + coroutines only), no tracking, no
ads, `targetSdk 36`. The in-repo work is already done:

- `fastlane/metadata/android/en-US/`: short/full description, `4.txt` changelog,
  `images/phoneScreenshots/` (2× 1080×2400), `images/featureGraphic.png` (1024×500).
- Privacy policy: `PRIVACY.md` (link it in the F-Droid submission).

Submission (done once, in a browser):

1. Fork https://gitlab.com/fdroid/fdroiddata, add
   `metadata/com.tapgerman.reader.yml` (use any recent MIT app's file as the
   template: `Categories: [Education]`, `License: MIT`, `SourceCode`,
   `IssueTracker`, current `VersionCode: 4` / `VersionName: 1.0.0` with
   `commit: v1.0.0`, `gradle: [yes]`, no `AntiFeatures` — no ads, no tracking,
   no non-free network service operated by the project).
2. Open the merge request against fdroiddata. A maintainer reviews, builds, and
   signs it with F-Droid's key (users get F-Droid's signature, not ours — normal).
3. Answer review comments (usually: confirm the build recipe, confirm no binary
   blobs — there are none: fonts are OFL, dictionary is a network API).

## Channel 3: IzzyOnDroid (free, fast middle ground)

IzzyOnDroid pulls APKs straight from GitHub releases into the F-Droid client,
no fdroiddata review queue. Once Channel 1 exists, ask Izzy (documented on
https://apt.izzysoft.de/fdroid/) to pick up the repo. Good discoverability
while the official F-Droid MR is pending.

## Notes

- F-Droid builds and signs with its own key: F-Droid users and GitHub-APK users
  get different signatures and cannot cross-upgrade without reinstalling. Say so
  in the release notes.
- Keep `versionCode` strictly increasing across all channels.
- The old `GlossLine-0.3.0.apk` at the repo root and `dist/` 0.2.0 APKs are
  pre-rename archives (different package/signature lineage); leave them alone.
