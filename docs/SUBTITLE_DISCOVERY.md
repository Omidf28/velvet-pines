# Embedded English Subtitle Discovery — 0.4.3-dev

## Selection Policy

A newly opened video without a saved subtitle profile starts in automatic English mode unless an external subtitle file has already been supplied. Discovery considers only supported embedded text tracks exposed by Media3 and accepted by `embeddedTextTracks`. The current adapter accepts SubRip, SSA/ASS, and WebVTT text formats, including Media3-transcoded cue samples that preserve their original MIME type.

A language tag is treated as English when its normalized base is `en` or `eng`; regional tags and underscore separators are supported. Track names alone do not establish the language. Image subtitles and burned-in text are outside this feature's scope.

Candidates are ranked in this order:

1. Non-forced English tracks before forced English tracks.
2. Container-default tracks within the same forced/non-forced category.
3. The current automatic choice when otherwise equivalent tracks are reordered.
4. The first remaining candidate in discovery order.

Forced English is used if no non-forced English candidate is available. Its menu label identifies it as forced; it may contain only a subset of the dialogue. If English disappears, automatic selection does not fall back to another language.

## Explicit User Choices

- Selecting an embedded track makes it a manual choice, even when it was already selected automatically.
- Turning primary subtitles off disables automatic selection for that video.
- Selecting an external subtitle file takes priority over embedded discovery.
- Selecting automatic English again explicitly re-enables discovery.
- Hiding English is a separate display preference. It retains the selected source; the caption menu exposes a visibility toggle and reports when English is hidden.
- If a manually selected track is temporarily unavailable, its key remains requested. Another language is not silently substituted.

Reselecting an unchanged track retains the current cue. A changed track or source clears the previous cue before new subtitle output is accepted.

## Persistence and Compatibility

The existing bounded per-video profile JSON now includes `automaticEnglish`. Older profiles and saved Activity state without this field default to manual behavior, preserving prior manual selections and off states. New unprofiled videos explicitly opt into automatic mode.

The mode and requested track key are retained in Activity saved state and profiles for local videos with retained file access. Profiles still follow the existing 20-video limit. This does not introduce persistent network credentials or change the application's existing network-session restoration limits.

Database schemas, saved vocabulary, review progress, and `.spbackup` are unchanged. Subtitle profiles remain application preferences and are not included in learning backups.

## Event Handling

The adapter checks the current media URI and latest track/cue state before processing subtitle output. It updates selection parameters only when they differ from the current parameters, avoiding repeated selection changes on matching track notifications. The Media3 1.8.0 implementation updates its current cue group before forwarding the corresponding listener callback; see the [versioned ExoPlayer source](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java#L3050).

## Verification

Seventeen JVM tests cover language recognition, track ranking, ordering stability, disappearing tracks, explicit off/manual choices, external-file priority, re-enabling automatic mode, and legacy profile defaults. These tests exercise the policy and profile defaults; they do not execute Android's JSON persistence, an actual media decoder, or the touch interface.

The [device acceptance checklist](PLAYER_ACCEPTANCE.md) includes integration and restart scenarios. Those checks remain pending. Use test videos with known language metadata and forced/default flags; do not infer the expected outcome solely from visible track names.
