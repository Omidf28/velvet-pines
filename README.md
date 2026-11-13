# Velvet Pines

**An Android video player designed for contextual language learning.**

Velvet Pines integrates media playback with interactive English subtitles, Persian word definitions, and a persistent vocabulary notebook. Learners can select a word or phrase during playback, view its definition for approximately three seconds, and save it with the original dialogue and playback timestamp for subsequent review.

The application uses Kotlin, Jetpack Compose, AndroidX Media3, and Room. The English interface uses a charcoal palette, indigo accents, and translucent panels. Files, Streams, and Flashcards are available from the bottom navigation.

## Project Status

**Current version:** `0.7.0-beta.10` · **Platform:** Android 8.0 / API 26 and later

The project is an experimental implementation with local build and automated test validation. Physical-device acceptance testing remains outstanding. A Windows edition is planned; no Windows application is currently available.

## Core Capabilities

### Media Playback

- Local video playback through Android's Storage Access Framework, with folder browsing and recent media. Select a video directly from either view.
- A redesigned library with continue-watching cards, per-video checkpoints within retained history, recorded progress, folder labels, and explicit playback from the beginning.
- Network playback support for HTTPS streams, HLS, DASH, RTSP, and read-only SMB access, subject to source and codec compatibility.
- Seekable on-demand videos opened from Streams can resume from their session checkpoint when the same link is reopened. Catalog rows show the resume time or replay status. Live streams start at the source's default position.
- The Streams tab plays direct HTTPS and RTSP links and browses user-supplied HTTPS indexes. Linked HTML pages and directories open as text lists. Search inside an index filters that page. At the root of Streams, search also finds episodes from recently opened indexes by series or season path, title, or filename. Site-wide search requires a provider's searchable catalog or API.
- Save HTTPS index addresses without query parameters for quick access. Scan archive searches linked pages on the same origin, up to 12 pages and three levels below the archive. Episode search results stay in the current session.
- Video-first playback with automatic landscape immersion, fading overlaid controls, and control auto-hide. Tapping the video toggles controls without changing the video bounds.
- Playback speed adjustment, audio-track selection, aspect-ratio controls, A–B repeat, sleep timer, and manual picture-in-picture.
- Configurable seek gestures, control and orientation locking, and per-media subtitle profiles.
- Explicit fullscreen rotation on phones, with an adaptive settings overlay beside the video in landscape and above it in portrait.

### Interactive Subtitles

- English and Persian SRT/WebVTT subtitles with independent timing adjustments.
- Compatible embedded text-subtitle tracks, subtitle encoding selection, and an indexed timeline for subtitle lookup.
- Automatic embedded English selection for new, unprofiled videos, with metadata-based ranking and explicit manual/off choices retained in local video profiles. External subtitles take priority.
- Nearby SRT/WebVTT discovery within a granted library folder: an unambiguous tagged English match can attach automatically; ambiguous and untagged files are offered for manual selection.
- Word-level selection by default, with an optional recognized-phrase mode persisted in study settings.
- The word card can list dictionary phrases containing the selected text and open their definitions with the original subtitle context.
- Cue-specific selection highlighting and compact definition cards over the video; cards are measured separately above the subtitles so opening one does not shift subtitle text. Editing and pronunciation remain available through the card menu.
- Dialogue navigation, sentence repetition, and optional pause at the end of a subtitle cue.

### Vocabulary and Review

- A bundled English–Persian offline index with 230,921 entries (97,026 single-token keys and 133,895 multiword keys), probable inflection lookup, and personal definition overrides.
- Offline prefix search for words and multiword entries from the library, player menu, or selected word card, with full definitions, pronunciation and personal corrections.
- An explicit MyMemory translation fallback for absent terms. The card stays open when an offline meaning is missing, and definitions use a dismissal timer after lookup completes.
- Bounded in-memory reuse of recent definitions and successful online translations, explicit failure-specific retries, loading cancellation, and accessibility-aware definition timeouts.
- Persistent vocabulary entries containing definitions, dialogue context, media references, and timestamps.
- Spaced-repetition flashcards, due-review sessions, search, filtering, sorting, and study statistics.
- English text-to-speech using installed Android voices, including slower pronunciation.

### Data Portability

- Personal dictionary import and export using TSV with preview and transactional import.
- Vocabulary export to CSV and Anki-compatible text.
- Versioned `.spbackup` files with SHA-256 integrity verification and transactional restoration of learning data.
- Backups preserve vocabulary and review progress; media files, file permissions, and application preferences are excluded.

## Technology Stack

| Component | Technology |
| --- | --- |
| Language | Kotlin |
| User interface | Jetpack Compose and Material 3 |
| Playback | AndroidX Media3 / ExoPlayer |
| Persistence | Room / SQLite |
| SMB access | SMBJ |
| Build system | Gradle Kotlin DSL and KSP |
| Verification | JUnit, Android Lint, and a Python SQLite verification script |

## Build and Run

### Requirements

- JDK 17
- Android SDK Platform 36 and Android SDK Build Tools 35.0.0
- An Android device or emulator running API 26 or later
- Network access for the initial Gradle and dependency downloads

Clone the repository and open it in Android Studio, or build from the command line:

```powershell
git clone https://github.com/Omidf28/velvet-pines.git
cd velvet-pines
.\gradlew.bat :app:assembleDebug
```

On Linux or macOS, use `./gradlew` in place of `.\gradlew.bat`.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the application and open the bundled synthetic sample, or select a local video and an English subtitle file. Select a subtitle word to view its definition. Most lookups work offline. An absent term offers explicit online translation or a personal definition; names, typos and every possible word are not guaranteed.

### Release APK

Set these environment variables in the build process using a private keystore and its credentials. Keep the keystore outside the repository and supply credentials through a local secret store or the CI secret manager.

| Environment variable | Value |
| --- | --- |
| `VELVET_PINES_KEYSTORE` | Absolute path to the release keystore |
| `VELVET_PINES_STORE_PASSWORD` | Keystore password |
| `VELVET_PINES_KEY_ALIAS` | Release key alias |
| `VELVET_PINES_KEY_PASSWORD` | Release key password |

```powershell
.\gradlew.bat --no-configuration-cache :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
python scripts/verify-lexicon.py --apk app/build/outputs/apk/release/app-release.apk
```

`apksigner` is provided by Android SDK Build Tools. Compare its certificate SHA-256 fingerprint with the expected release certificate before distributing the APK. With all four variables unset, Gradle produces `app-release-unsigned.apk` for build verification. A partially configured signing environment fails with the names of the missing variables.

Android upgrades require a compatible signing identity. Before replacing a debug-signed beta with a differently signed release, open Flashcards, choose Backup and Restore from the overflow menu, and export a `.spbackup` to Downloads or another shared folder. Select **Verify backup** and check the card count before uninstalling. On earlier betas, use **Choose backup file**, check the preview counts, and select **Cancel**. After installing the release, use **Restore backup** to recover saved words and review history, then grant access to your video folders again.

## Validation

[Android validation](https://github.com/Omidf28/velvet-pines/actions/workflows/android-validation.yml) runs on pushes and pull requests targeting `main`. It builds the debug and unsigned release APKs, runs unit tests and Lint, compares SQLite migrations with the Room-generated schema, and verifies the bundled dictionary in both packages. APKs from this workflow are used for validation.

Run [Android emulator smoke test](https://github.com/Omidf28/velvet-pines/actions/workflows/android-emulator-smoke.yml) at a release checkpoint. On an Android 15 emulator, it checks saving a subtitle definition to Flashcards, switching to landscape fullscreen, preserving the video frame bounds as playback controls toggle, and dismissing a timed definition. It also exports a `.spbackup` from one Room database, restores it to another, and checks that a repeat restore preserves local edits. To run it locally, use `:app:connectedDebugAndroidTest` with a clean emulator.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
python scripts/verify-review-migration.py
python scripts/verify-lexicon.py --apk app/build/outputs/apk/debug/app-debug.apk
```

The latest recorded validation includes:

| Check | Recorded result |
| --- | --- |
| Android debug build and APK signature verification | Passed |
| Unsigned Android release build and critical lint | Passed |
| JVM unit tests | 235 passed |
| SQLite migration and review checks | 14 passed, including comparison with the Room-generated schema |
| Packaged lexicon hashes, SQLite integrity, license and all-row validation | Passed |
| Android Lint | Passed |

These results do not establish physical-device, network interoperability, or accessibility acceptance. Playback gestures, TalkBack, device-specific codec behavior, file access, and external service integration require practical testing. The SQLite script complements Android database migration testing and does not replace installation or upgrade tests.

## Repository Layout

```text
app/src/main/          Android application, resources, and synthetic demo assets
app/src/test/          JVM unit tests
scripts/               SQLite migration and review verification
test-fixtures/        Subtitle, dictionary, and embedded-subtitle samples
docs/                 Extended documentation
```

Application code currently resides in the `com.subtitleplayer.prototype` package. The historical application identifier is retained to preserve continuity with existing development installations. A shared desktop core has not yet been extracted.

## Documentation and Roadmaps

- [Lookup reliability](docs/LOOKUP_RELIABILITY.md) — cache priorities, explicit online intent, failure recovery, and cancellation limits.
- [Offline dictionary search](docs/DICTIONARY_SEARCH.md) — search behavior, limits, implementation, and device acceptance.
- [Word meanings and dictionary provenance](docs/WORD_MEANINGS.md) — offline coverage, online privacy, rebuilding, and device acceptance.
- [Android development roadmap](ROADMAP.md) — implementation history and remaining acceptance work; Persian.
- [Windows development roadmap](WINDOWS_ROADMAP.md) — architecture evaluation, interactive subtitle prototype, shared logic, data compatibility, and distribution milestones; Persian.
- [Engineering checkpoint](CHECKPOINT.md) — recorded build results and outstanding verification; Persian.
- [Library and playback bookmarks](docs/LIBRARY_PLAYBACK.md) — navigation, checkpoint semantics, retention limits, and migration behavior.
- [Nearby subtitle discovery](docs/SIDECAR_DISCOVERY.md) — filename rules, access scope, result validity, and a synthetic device test kit.
- [Embedded subtitle discovery](docs/SUBTITLE_DISCOVERY.md) — selection rules, profile compatibility, and verification scope.
- [Playback acceptance checklist](docs/PLAYER_ACCEPTANCE.md) — device tests for the immersive player and subtitle interactions.
- [Manual testing guide](docs/MANUAL.fa.md) — detailed setup and acceptance scenarios; Persian.

The Windows plan begins with a playback and subtitle-overlay feasibility prototype before committing to a desktop framework or native media engine.

## Scope and Licensing

The bundled videos are synthetic test assets. Commercial films and television episodes are not included.

A source-code license has not yet been selected. Third-party libraries and bundled dictionary data remain subject to their respective licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
