# Lookup reliability — 0.6.2-dev

This release improves subtitle word lookup without changing the packaged lexicon,
the personal dictionary schema or learning backups.

## Repeated selections

An Activity-scoped lookup service retains at most 256 offline results (including
known misses) and 64 successful online translations in separate least-recently-used
caches. Keys use the existing normalized term grammar. Caches are memory-only and
are cleared when the Activity is replaced; they are not a permanent offline pack.

Personal meanings are checked before cached results. Changing or removing an override
therefore takes effect without clearing the whole cache. Packaged/curated definitions
win over cached machine translations during ordinary lookup. When no offline meaning
exists, a previously requested successful online translation can be reused without
another network request and is labeled as a temporary cached translation.

Errors and invalid translations are not cached. A read or translation failure can
be retried explicitly. The independent dictionary browser remains an offline search
of packaged/personal data; it does not display this transient online cache.

## Network intent and cancellation

Each press of the online button creates an intent consumed once by the current card.
A personal-data refresh defaults to offline resolution; it cannot replay the earlier
online request. Changes to unrelated personal entries do not restart the selected
word's lookup. A new online attempt still requires pressing the button.

The Cancel action closes the loading card. Selecting another word or closing the
card cancels the Compose effect; results cannot update the replacement card.
Cancellation is checked before requests, between response reads and before results
are cached. An already-blocked HTTPS operation may continue until the configured
connection/read timeout; Cancel does not guarantee an immediate socket abort or
recovery of provider quota already consumed. No retry runs automatically.

The existing privacy behavior is unchanged: only the selected normalized term is
sent to MyMemory after explicit intent. The subtitle, media source and notebook are
not sent. See [the API specification](https://mymemory.translated.net/doc/spec.php)
and [provider usage limits](https://mymemory.translated.net/doc/usagelimits.php).

## Card behavior and failure messages

Ordinary results receive approximately three seconds after lookup completes. Android's
recommended accessibility timeout can extend this period. Expanded options and
explicit study pauses continue to prevent automatic dismissal.

Failures open the card's options and keep it visible for a deliberate retry or close.
Separate messages identify an unavailable offline index, connection failure, a socket
timeout, service failure, quota/rate limitation, and an invalid translation. Offline
index failures offer an offline Retry action; online failures offer an explicit
online Retry action. Failed results cannot be saved as vocabulary definitions.

The HTTPS client retains 8-second connection and 10-second read timeouts, rejects
redirects, bounds responses to 64 KiB, and rejects non-success HTTP/provider statuses.
HTTP/provider 429 or a provider quota flag maps to the quota/limit message. JSON errors,
oversized bodies and translations without Persian/Arabic characters are invalid
responses. The client never displays a raw server error body as a Persian meaning.
These are connect/read limits, not a promised total end-to-end duration.

## Verification and Android acceptance

JVM tests exercise repeated positive/negative lookups, normalization, override edits
and removal, offline precedence, online-cache reuse, LRU eviction, explicit retries,
failed-intent replay prevention, rejected translations, cancellation propagation,
response-size limits, cancellation between reads and status classification. Fakes
replace the provider in these tests; they do not establish TLS or device behavior.

The existing APK lexicon verifier checks data hashes, license files, every indexed
row, SQLite integrity and the prefix-search query plan.

Pending Android scenarios:

1. In airplane mode, repeatedly select a packaged word and an absent invented word.
   Confirm the movie keeps playing and each card gets a fresh dismissal interval.
2. Explicitly translate an absent term known to the service. Close it, disable the
   network and select it again; the temporary translation should remain available
   during the same Activity lifetime. Recreate/restart and confirm no persistence
   is promised unless saved personally or in the notebook.
3. Correct that term personally, select it again, remove the override and select it
   once more. Confirm the personal and regular meanings take the documented priority.
4. Use a failing/slow connection. Cancel and immediately select another word; the old
   response must never replace the new card. Confirm video and controls remain usable.
5. Test offline-index failure, connection failure, timeout, quota indication and invalid
   translation. Verify actionable messages, retained error cards, explicit retries,
   no empty notebook entries and no automatic request after a personal-data change.
6. Enable Android's longer accessibility timeout and TalkBack; verify the result card
   respects it without changing the default interval on a normal configuration.
7. Repeat in landscape with dual subtitles, large fonts, rotation and PiP transitions.
   These checks have not been performed on a physical device.
