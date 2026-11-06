# Immersive Playback Acceptance — 0.4.1-dev

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

## Test Record

Record device model, Android version, navigation mode, font scale, media codec, subtitle source, and reproduction steps for each failure. Include a screen recording when reporting incorrect resizing or touch behavior. No item is marked passed until observed on a device.
