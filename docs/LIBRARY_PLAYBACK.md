# Library and Playback Bookmarks — 0.5.0-dev

## Library Experience

Launching from the app icon opens Files. Choose a video or resume one from Recent. Opening a video from the Android file picker or another app goes directly to the player. The library retains access to local files, folder selection, network links, the synthetic sample, and offline help.

The header, tabs, and search are compact. Source actions, sorting, the continue-watching card, and media rows use the scrolling content area. While the search field owns the visible keyboard, secondary headers are hidden so results remain reachable in a short landscape viewport. Opening media explicitly hides the keyboard.

Media rows show the filename, known folder label, playback state, and recorded progress when available. Their artwork is a generic branded file tile, not an extracted video thumbnail. Tap a row to play or resume it. A resumable row also offers playback from the beginning. When an episode ends, return to the library and choose the next episode from the same folder.

Folder scans start when the Folder tab is requested. Returning from playback to the same folder keeps the search, scroll position, and completed scan in the current session. Refresh reads the folder again, and Retry is available after a read error. Recent browsing does not eagerly rescan the selected folder. File changes require a refresh; this is not a persistent media index or filesystem watcher.

## Bookmark Semantics

Bookmarks are retained with up to 20 recent entries. Legacy queue records can supply a cached bookmark or folder context when reopening the same video.

A checkpoint contains position in milliseconds, duration when known, and whether the previous playback reached the end. An unknown duration has no percentage indicator. A completed source replays from zero, and starting it again replaces its prior completion state. Closing during credits does not count as completion merely because the end is near.

The application checkpoints seekable document-based media with retained read access. Snapshots are taken approximately every ten seconds while playing in the foreground or PiP, and at explicit pause/resume, completion, source changes, library/notebook entry, normal lifecycle saves, and destruction. Failed or unprepared sources do not overwrite a valid checkpoint. Abrupt process termination can still lose progress since the latest completed persistence operation.

Explicit start-from-beginning takes priority over a stored bookmark, including an explicit zero position. Incoming history is captured before recording the outgoing source so bounded-history eviction cannot erase the resume point of the movie being opened. Equivalent document URIs use the existing canonical document identity rather than filename equality.

## Persistence and Compatibility

Recent entries and legacy queue records can carry `positionMs`, `durationMs`, `completed`, and the display-only `parentPath`. Older entries without a position field remain valid and have no bookmark. The previous global last-position value is used as a fallback when restoring legacy last playback, while a known per-video checkpoint takes priority.

Folder labels are displayed and searched as metadata; they are never used as filesystem paths or permission grants. Existing parent-directory URIs continue to define authorized nearby-subtitle access.

Room, vocabulary records, subtitle-profile behavior, and the learning-backup format are unchanged. Playback history and display preferences are not part of `.spbackup`.

## Verification Scope

Seventeen new JVM tests cover checkpoint normalization, completion, unknown duration, long values, explicit restart, checkpoint precedence, and incoming-entry eviction. They do not execute Android preference JSON, the media engine, UI layout, or lifecycle callbacks.

Device verification remains pending. In particular, compare independent positions for two videos, restart the application, test PiP closure, check aliases for the same document, and exercise the library in landscape with the keyboard and a large font. The detailed scenarios are in the [acceptance checklist](PLAYER_ACCEPTANCE.md).
