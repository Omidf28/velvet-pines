# Immersive Playback Acceptance — 0.4.0-dev

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
- [ ] Play controls remain available while paused. Menus and seek dragging are not interrupted by control auto-hide.
- [ ] Two-line English, optional Persian, long definitions, and large system fonts fit usable screen bounds.
- [ ] Word options allow editing and pronunciation without the default card timeout interrupting the action. Closing expanded options and resuming a study pause work.
- [ ] The top bar exposes subtitle selection, settings, and overflow actions in landscape without leaving playback.
- [ ] Study tools retain A/B repeat, repeat cancellation, and previous/next dialogue navigation.
- [ ] Lock prevents subtitle selection and gestures; unlock restores controls.
- [ ] Picture-in-picture is entered explicitly; returning restores playback and subtitle interaction.
- [ ] Library, notebook, file picker, and settings remain accessible; returning preserves the current source and position.
- [ ] Rotate with a menu, card, or seek interaction active; verify no crash, unexpected source reload, or stuck controls.
- [ ] Verify TalkBack labels, focus traversal, recommended control timeout, display cutouts, gesture navigation, and three-button navigation.

## Test Record

Record device model, Android version, navigation mode, font scale, media codec, subtitle source, and reproduction steps for each failure. Include a screen recording when reporting incorrect resizing or touch behavior. No item is marked passed until observed on a device.
