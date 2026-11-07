# Immersive Playback Acceptance — 0.5.0-dev

Status: pending execution on an Android device. Compilation and JVM tests do not verify touch routing, overlay positioning, or device rendering.

## Primary Workflow

1. Open the synthetic sample. Rotate the phone to landscape. Confirm that system bars become transient and playback continues without reloading or losing position.
2. Wait during playback. Controls should disappear after approximately 3.5 seconds, or the longer timeout requested by Android accessibility preferences.
3. Tap empty video space. Confirm that controls appear over the same video. The rendered video must retain the same size, aspect ratio, crop, and position.
4. Tap empty space again. Confirm that only controls disappear. Repeat at least ten times while playing and while paused.
5. Select `afford` in the sample's English subtitles. Confirm that the compact Persian definition appears over the video, controls are hidden, playback continues with the default study setting, and the card disappears after approximately three seconds.
6. Select another word before the previous card expires. Confirm that the latest selection receives a fresh display interval.
7. Save a defined word. Confirm that playback continues and the saved entry contains the correct subtitle, source, and timestamp after restarting the application.

## Controls and Layout

- [ ] Play/pause, replay after end, seek slider, double-tap seeking, and seek boundaries work.
- [ ] Showing controls does not change video measurements in portrait or landscape, including fit/crop/stretch modes.
- [ ] Subtitle lines remain readable above the seek bar; selecting a word does not also trigger a background tap or seek.
- [ ] Android Back dismisses a visible word card before changing orientation; returning from the library restores visible controls.
- [ ] Play controls remain available while paused. Menus and seek dragging are not interrupted by control auto-hide.
- [ ] Two-line English, optional Persian, long definitions, and large system fonts fit usable screen bounds.
- [ ] Word options allow editing and pronunciation without the default card timeout interrupting the action. Closing expanded options and resuming a study pause work.
- [ ] The top bar exposes subtitle selection, settings, and overflow actions in landscape without leaving playback.
- [ ] Study tools retain A/B repeat, repeat cancellation, and previous/next dialogue navigation.
- [ ] Lock prevents subtitle selection and gestures, and pins the current viewing orientation on supported phones; unlock restores controls and the prior viewing preference.
- [ ] Picture-in-picture is entered explicitly; returning restores playback and subtitle interaction.
- [ ] Library, notebook, file picker, and settings remain accessible; returning preserves the current source and position.
- [ ] Rotate with a menu, card, or seek interaction active; verify no crash, unexpected source reload, or stuck controls.
- [ ] Verify TalkBack labels, focus traversal, recommended control timeout, display cutouts, gesture navigation, and three-button navigation.

## Fullscreen and Settings Regression

- [ ] On a portrait phone, press fullscreen. The Activity should rotate into landscape; the source, subtitle selection, and playback position must remain intact.
- [ ] Press fullscreen again or Android Back while immersive. The player should return to portrait without seeking or reopening the source. Background taps must continue to toggle controls only.
- [ ] Lock in each orientation, physically rotate the phone, and unlock. The lock should pin the selected viewing orientation and release back to the prior automatic or explicit preference.
- [ ] Enter PiP while viewing, return, and verify the orientation preference. Library and notebook screens should not inherit a forced playback orientation.
- [ ] Change font scale or otherwise recreate the Activity, then leave playback. The original Activity orientation policy should still be restored.
- [ ] Open settings in landscape: it should appear beside the video. In portrait, it should appear as a lower panel. Opening and closing it must not resize or reload the underlying video.
- [ ] Verify all four settings sections, scrolling at large font scale, outside-tap dismissal, Android Back, and the close button. Taps inside blank panel space must not dismiss it.
- [ ] Test tablets, foldables, and multi-window independently; rotation requests are not guaranteed on those configurations.

Android 16 can ignore orientation requests on displays of at least sw600dp for applications targeting API 36. The layout must remain usable when a rotation request is ignored; this project does not opt out of adaptive platform behavior. See the [official Android orientation and resizability documentation](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability).

## Word Selection and Definition Layout

- [ ] With default settings, select a word inside a known phrase such as `gave up`. Only the selected word should be highlighted and looked up.
- [ ] Switch Study settings to recognized phrases. Selecting either part of a recognized phrase should select the full phrase; custom dictionary phrases should also work. Restart the application and verify the choice persists.
- [ ] Select one occurrence of a repeated word. Only that occurrence should be highlighted. Identical text in a later cue must not retain the previous cue's selection.
- [ ] Select an unknown word. Wait longer than three seconds. Its card should remain available for Translate online, Close, or adding a personal definition from Word options. Confirm playback continues with the default study setting.
- [ ] Compare subtitle coordinates before and after opening and expanding a definition card, with controls hidden in both cases. The card must not push the subtitles or change the video's bounds.
- [ ] Test English and Persian together, two-line cues, large fonts, display cutouts, and a short landscape viewport. The card should use the remaining space above the subtitles and scroll when needed.
- [ ] Tap another word before timeout; its definition should receive a fresh display interval. After timeout or dismissal, the highlight must clear.
- [ ] Show playback controls and tap two words from the same cue in quick succession. The subtitle row should stay under the same touch targets while the controls hide. Only the last word should remain highlighted and its card should get a full display interval.
- [ ] Start an online lookup for one word and immediately select another. A late result or timeout must leave the newer card intact. Start a save just before the timeout and verify that Saved is visible when the write finishes.
- [ ] Tap empty video space while a definition is open. The definition should close and the playback controls should appear without resizing the video or automatically resuming a study pause.
- [ ] Verify word selection, save, edit, speech, study-pause resume, and lock/unlock in both lookup modes; existing saved phrase cards must remain intact.
## Automatic Embedded English

- [ ] Open a new local video with supported embedded English text. It should select English automatically and allow word selection without attaching a separate SRT file.
- [ ] Use multiple language tracks with a non-English container default. Confirm that automatic mode still chooses English.
- [ ] With both forced and non-forced English tracks, confirm the non-forced track wins. With only forced English, confirm the forced indicator and limited dialogue coverage are understood.
- [ ] Try missing language metadata, image-only subtitles, and no embedded English. Do not infer English from a track label. The menu should offer external file selection and report no compatible English after preparation completes.
- [ ] Manually select another track, including reselecting the current automatic track. Confirm that later metadata changes do not override this choice and an unchanged track does not blank its current cue.
- [ ] Turn primary subtitles off, then wait, seek, close and reopen the local video, and restart the application. Automatic mode must remain off while its retained profile is available.
- [ ] Disable or change the bundled sample subtitles, then choose Play Sample again. Its bundled English and Persian subtitle files should be restored.
- [ ] Re-enable automatic English while paused; playback must remain paused.
- [ ] Re-enable automatic English from the menu. It should resume discovery; a selected external subtitle must take priority until the user explicitly switches away from it.
- [ ] Hide and show English using the caption menu. This should preserve its source and profile mode; automatic selection should not silently undo the display preference.
- [ ] Upgrade with existing manual/off profiles and verify that the absence of the new JSON field preserves the old behavior.
- [ ] Switch quickly between two videos in the same folder. No subtitle text or track selection from the previous source should appear in the next one.
- [ ] Verify adaptive stream track changes separately where compatible text tracks are exposed. This does not establish general protocol or codec compatibility.
## Nearby Subtitle Files

- [ ] Generate the sidecar test kit, extract it into a fresh phone folder, and open that folder through the library. Verify the auto, ambiguous, and untagged cases separately.
- [ ] Open identically named videos in different nested folders. Only subtitles from the actual known parent may be offered or attached.
- [ ] Verify recent playback after restarting the application. The known parent and subtitle choice should remain usable while their grants remain valid.
- [ ] Open the same video using a single-file picker without known parent context. Do not assume parent access or search unrelated folders.
- [ ] Make a manual/off/internal-only selection or select a word while discovery is slow. A late result must not overwrite the choice or dismiss the word card. Switch A → B → A during a slow query to check stale-result rejection.
- [ ] Test malformed, empty, oversized, and inaccessible English-tagged files. Failed validation must leave the current source intact and must not pause, seek, or resize video playback.
- [ ] Test permission revocation, provider cancellation, a missing source row, and a directory exceeding the discovery limit. Failure should remain within the caption menu.
- [ ] Upgrade with an older profile lacking `automaticSidecars`; verify that its prior subtitle choice is unchanged. Confirm that an unfinished newer automatic preference remains eligible after reopening.
- [ ] Start an A/B repeat while discovery is pending; automatic attachment must not disrupt the active study interval.
- [ ] Refresh nearby matches from the caption menu and select one explicitly. Verify pause state, subtitle timing, source labels, and word selection after attachment.
## Files, Flashcards, and Independent Resume

- [ ] Set the phone language to Persian and open Velvet Pines. Files, Flashcards, menus, player settings, and app messages should remain English and left-to-right. Open a saved card with a Persian definition and verify its text order. The Android file picker follows the phone language.
- [ ] Launch from the app icon after a fresh install and again after watching a video and closing the app. Both launches should land on Files. Open a video explicitly and check its resume position. Android Back from the library root should leave the Activity.
- [ ] Grant access to a folder, open a subfolder, favorite it, and reopen it from the favorite list after restarting the app. Tap a video row to play immediately, then go back to the folder.
- [ ] Open Flashcards from the bottom bar without starting a video. Check the empty state and a populated list. Tap a saved English word to reveal its meaning and sentence, then tap again to collapse it. Switch back to Files and check that the folder is still selected.
- [ ] With existing saved words and review history, export a `.spbackup` from the Flashcards overflow menu. Restore it on a separate test install and verify the saved words, personal definitions, and review progress.
- [ ] From Files, verify sample playback, help, single-file selection, and network access.
- [ ] Play video A to one position, open B and stop at another position, then reopen A. Each should resume at its own checkpoint rather than the previous global position.
- [ ] Repeat after normal app restart, foreground playback, PiP playback/closure, and explicit pause. Checkpoint timing is approximate; do not expect unflushed progress after abrupt process termination.
- [ ] Use Playback from beginning for a partially watched and a completed source. Verify zero takes priority over older global and per-video positions and the next saved checkpoint reflects the restart.
- [ ] Complete a video naturally. Confirm completion is recorded even without immediately leaving the player; replay should begin at zero. Near-end credits alone must not mark it completed.
- [ ] Open an old JSON/history installation without bookmark fields. Existing entries and legacy last-position restore should remain usable.
- [ ] Fill retained recent history, then open its oldest entry while recording an outgoing movie. The incoming checkpoint must survive that eviction boundary.
- [ ] Open the same document through file and folder URIs, and different documents with identical filenames. Identity should share a checkpoint only for the same document; folder labels should distinguish duplicates after restart.
- [ ] Exercise unknown-duration and nonseekable sources. Do not show an invented percentage or persist a nonseekable resume point.
- [ ] Test search with the keyboard in portrait/landscape, large fonts, TalkBack, empty results, refresh/retry, and repeated Folder/Recent switches. Controls and results must remain reachable.
- [ ] Play an episode directly from a folder and let it finish. Return to the same library tab and folder, then select the next episode. Verify playback and subtitle discovery use the selected file.
- [ ] From portrait playback, use Android Back to return to the same file list and scroll position. Use the Files toolbar play icon to return to the paused video. In fullscreen, Back should leave fullscreen before returning to Files.
- [ ] Scroll in Folders and Recent, then switch between them. Each tab should retain its position. Back from Recent should return to Folders. Cancel the folder picker from Recent and verify that Recent stays selected.
- [ ] Set a Flashcards filter and expand a word. Switch to Files and back. Verify that the filter and expanded word are still selected.
- [ ] In a long folder list, scroll down, open a subfolder, and use Back to return. Repeat with an active search when opening the subfolder. The parent should reopen at its prior search and scroll position after loading.
- [ ] With Files showing a nested folder, use Android's Don't keep activities developer option to force activity recreation by opening and dismissing the folder picker. Verify the current folder, search, and scroll are restored. Repeat from Recent and verify the tab is still selected.
## Test Record

Record device model, Android version, navigation mode, font scale, media codec, subtitle source, and reproduction steps for each failure. Include a screen recording when reporting incorrect resizing or touch behavior. No item is marked passed until observed on a device.
