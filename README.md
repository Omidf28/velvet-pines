# Velvet Pines

**An Android video player designed for contextual language learning.**

Velvet Pines integrates media playback with interactive English subtitles, Persian word definitions, and a persistent vocabulary notebook. Learners can select a word or phrase during playback, view its definition for approximately three seconds, and save it with the original dialogue and playback timestamp for subsequent review.

The application is implemented in Kotlin using Jetpack Compose, AndroidX Media3, and Room. Its dark visual identity combines burgundy, charcoal, forest green, and coffee tones with restrained curtain and woodland motifs.

## Project Status

**Current version:** `0.4.2-dev` · **Platform:** Android 8.0 / API 26 and later

The project is an experimental implementation with local build and automated test validation. Physical-device acceptance testing remains outstanding. A Windows edition is planned; no Windows application is currently available.

## Core Capabilities

### Media Playback

- Local video playback through Android's Storage Access Framework, including folder browsing, recent media, and a reorderable playback queue.
- Network playback support for HTTPS streams, HLS, DASH, RTSP, and read-only SMB access, subject to source and codec compatibility.
- Video-first playback with automatic landscape immersion, overlaid controls, and control auto-hide. Tapping the video toggles controls without changing the video bounds.
- Playback speed adjustment, audio-track selection, aspect-ratio controls, A–B repeat, sleep timer, and manual picture-in-picture.
- Configurable seek gestures, control and orientation locking, and per-media subtitle profiles.
- Explicit fullscreen rotation on phones, with an adaptive settings overlay beside the video in landscape and above it in portrait.

### Interactive Subtitles

- English and Persian SRT/WebVTT subtitles with independent timing adjustments.
- Compatible embedded text-subtitle tracks, subtitle encoding selection, and an indexed timeline for subtitle lookup.
- Word-level selection by default, with an optional recognized-phrase mode persisted in study settings.
- Cue-specific selection highlighting and compact definition cards over the video; cards are measured separately above the subtitles so opening one does not shift subtitle text. Editing and pronunciation remain available through the card menu.
- Dialogue navigation, sentence repetition, and optional pause at the end of a subtitle cue.

### Vocabulary and Review

- A limited offline starter dictionary and an editable personal dictionary with custom phrase support.
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

Run the application and open the bundled synthetic sample, or select a local video and an English subtitle file. Select a subtitle word to view its definition. Words outside the starter dictionary require a personal dictionary entry.

## Validation

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
python scripts/verify-review-migration.py
```

The latest recorded validation includes:

| Check | Recorded result |
| --- | --- |
| Android debug build and APK signature verification | Passed |
| JVM unit tests | 121 passed |
| SQLite migration and review checks | 14 passed |
| Android Lint | 0 errors; 26 warnings |

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

- [Android development roadmap](ROADMAP.md) — implementation history and remaining acceptance work; Persian.
- [Windows development roadmap](WINDOWS_ROADMAP.md) — architecture evaluation, interactive subtitle prototype, shared logic, data compatibility, and distribution milestones; Persian.
- [Engineering checkpoint](CHECKPOINT.md) — recorded build results and outstanding verification; Persian.
- [Playback acceptance checklist](docs/PLAYER_ACCEPTANCE.md) — device tests for the immersive player and subtitle interactions.
- [Manual testing guide](docs/MANUAL.fa.md) — detailed setup and acceptance scenarios; Persian.

The Windows plan begins with a playback and subtitle-overlay feasibility prototype before committing to a desktop framework or native media engine.

## Scope and Licensing

The bundled videos are synthetic test assets. Commercial films and television episodes are not included.

A source-code license has not yet been selected. Third-party libraries remain subject to their respective licenses.
