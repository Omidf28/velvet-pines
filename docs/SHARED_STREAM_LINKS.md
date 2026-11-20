# Sharing links to Streams

From a browser or another app, share one HTTPS or RTSP URL and select Velvet Pines.
Streams opens with an editable address. Choose **Play URL** for a direct video or
**Load index** for an HTTPS archive. A normal website page is not converted into a
direct video URL. The receiver follows Android's
[text-sharing contract](https://developer.android.com/training/sharing/receive)
with `ACTION_SEND`, `text/plain` and `EXTRA_TEXT`.

The receiver accepts a URL alone or one URL in accompanying text, including styled
text. It preserves encoded paths and signed query strings, strips fragments in
the existing URL parser, and handles surrounding prose delimiters. Multiple
distinct URLs, unsupported schemes, embedded credentials, malformed hosts/ports,
oversized text and missing text produce an explanatory message. Shared text is
limited to 16 KiB and an individual URL to 8,192 characters. Invalid input keeps
the previous editable address. Other actions and MIME types are ignored.

Receiving a share never starts a connection, playback, scan or saved-source write.
An open video is paused and retained at its position. The player dialogs close,
Streams becomes the active destination, and **Return to video** opens the retained
video. If an index is open, sharing returns to the address input. Any previous
index request is cancelled; revision checks prevent its response or error from
changing the newly prepared screen.

The current Streams session lives in an Activity ViewModel without a Context.
The draft, edited address and loaded catalogs survive Activity recreation. The
original share is handled only on first creation or `onNewIntent`, so recreation
cannot replace an edited address with the original URL. The session is not written
to preferences, learning backups or saved-state bundles. After process death, a
restored Streams page has an empty session and does not replay an old share.
Explicit **Save source** continues to use its existing public-URL restrictions.

Automated checks cover extraction/validation, styled and oversized payloads,
Sharesheet intent resolution, cold launch, new intents, edited drafts across
recreation, invalid input retaining a draft, reset from a nested catalog, and
returning to the same paused video and position from a dictionary overlay.

On a phone, check sharing from the actual browser and messaging apps, both while
Velvet Pines is closed and while a video is playing. Include signed URLs, portrait
and landscape, an open index request, picture-in-picture, app backgrounding, and
process termination. Confirm that playback/loads require the chosen action and
that the original movie remains usable after returning from Streams.
