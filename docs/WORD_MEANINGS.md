# Word meanings — 0.6.0-dev

Since 0.6.1-dev, the index also has an independent [offline search panel](DICTIONARY_SEARCH.md).

See [0.6.2 lookup reliability](LOOKUP_RELIABILITY.md) for temporary caches, explicit retries and cancellation behavior.

## What ships

The bundled English–Persian SQLite dictionary has **233,691 indexed entries**:
**97,969 single-token entries** and **135,722 multiword entries**. These are dictionary
keys, not a measured percentage of film dialogue or a guarantee of distinct linguistic
headwords. The phrase-selection mode recognizes curated phrases. The expanded word card can search the larger phrase index.

In 0.6.3-dev, tap a word, open the three-dot menu, and choose the subtitle phrase list.
It checks exact adjacent phrases containing the selected text on its original subtitle
line. Choosing a result opens its meaning and lets you save it with the original
dialogue and timestamp. The list uses the packaged and personal dictionaries and
curated entries. It excludes phrases that cross punctuation or a subtitle line break.

On a device, try a cue containing `coffee break`, select `coffee`, then choose the phrase.
Check its highlight, meaning, and saved context. Repeat during playback and during
a study pause. Try a repeated word, a line break, and switching words during a search.

Source: [EnglishToPersianDictionaries](https://github.com/VahidN/EnglishToPersianDictionaries),
commit `224f15d42d145c59b5dc2c6890c4736693613cc2`. Six additional source groups
contribute 2,292 keys, including `imo` and `break the ice`. The automated
import keeps the original indexed definitions for keys already present. A
subsequent import of `generic-14` supplied 478 previously missing keys,
including `flawlessly` and `anonymously`. Comparison against the prior SQLite
index confirmed that all 233,213 existing key and definition pairs were retained.
License: Apache 2.0. The original license, attribution and modification notice ship
inside the APK. See [third-party notices](../THIRD_PARTY_NOTICES.md).

The gzip asset is 21,363,196 bytes. The installed read-only database is 58,802,176 bytes,
including 1,172,977 Persian token-to-term mappings. Prior packaged index files are
removed after the replacement's size and hash have been verified.
The `.bin` asset contains gzip data; this extension prevents the Android asset merger from silently unpacking `.gz` source assets. It is unpacked into Android's no-backup directory on the first indexed lookup, with
size bounds and SHA-256 verification. Installation and queries run on an IO dispatcher;
no full dictionary map is loaded into memory. It does not modify the user's Room
schema, saved vocabulary or learning backups. A failed installation can be retried
by selecting the word again. Disk space is required for the unpacked index.

## Lookup behavior

1. An exact personal dictionary entry overrides other sources.
2. Curated common/function words and the existing curated definitions are retained.
3. The packaged index is queried for the exact normalized term.
4. Only after an exact miss, plausible inflections and irregular roots are looked up.
   Returned roots are labeled as probable; up to three alternatives are retained.
5. A missing term offers an explicit online translation button and personal editing.

The project also supplies 52 dialogue definitions with spelling and inflection
variants. They are available in offline search and word lookup. Multiword
entries such as `call it a day` also work in Known phrases mode. Additional
irregular forms, including `withdrew` and `overslept`, use the indexed base
definition after an exact miss.

Regular fallback also handles selected spelling changes before applying
generic suffix rules. Examples include `panicking` → `panic`,
`untying` → `untie`, and `unhappier` → `unhappy`.

For selected British and American spelling pairs, an exact miss also checks
the alternate spelling. For example, `prioritise` can use `prioritize`.
When only a base form is found, the result keeps the possible-base-form label.

The phrase menu also recognizes selected verbs with an object between the verb
and particle. For example, selecting `brought the issue up` highlights the text
as it appears in the subtitle and can look up `bring up` after an exact miss.
The player smoke test opens the bundled sample, looks up `bring the issue up`
from the subtitle phrase menu, and verifies the saved Persian meaning in
Flashcards with the emulator in airplane mode.
[Emulator run](https://github.com/Omidf28/velvet-pines/actions/runs/43588288212)

Keys use the existing English word grammar: letters, internal apostrophes/hyphens,
and up to eight words. Matching is case-insensitive and normalizes curly apostrophes.
Definitions are limited to 1,000 UTF-16 code units for compatibility with learning
storage. No context-sensitive word-sense selection is performed. Names, typos,
new slang, numeric tokens, unsupported scripts and specialist terms may be absent.
Some source definitions are old or imperfect; users can correct them personally.

A loading message appears during lookup. The approximately three-second dismissal
period begins after a result arrives. Changing the selected word cancels the previous
Compose request; a late response cannot replace the next word's card. Opening the
card menu or using an explicit study pause prevents automatic dismissal as before.
Playback is not paused by lookup when pause-on-word-tap is disabled.

## Dialogue coverage baseline

The [dialogue fixture](../test-fixtures/dialogue-coverage.en.srt) is checked
against the offline Android lookup in airplane mode. The first run used 20 cues and found
entries for 133 of 133 distinct word forms and 191 of 191 word occurrences.
Fourteen distinct forms used a possible base form. Thirteen of 16 selected
phrases returned a definition. The missing phrases were `brought it up`,
`start over`, and `putting it off`. This counts returned definitions in
the fixture. Judging whether a definition fits its line requires a separate review.
[Emulator run](https://github.com/Omidf28/velvet-pines/actions/runs/43506288003)

The expanded fixture includes 28 cues and 20 selected phrases. The new lines
cover casual speech such as `gonna`, contractions such as `couldn't've`, and
idioms such as `on the house` and `take a rain check`.

## Online fallback

The button sends only the selected normalized term to
`https://api.mymemory.translated.net/get` with `langpair=en|fa`. It does not send the
subtitle cue, media URI, filename, saved notebook, identity or email address, and
never calls the provider's contribution API. There are no automatic network lookups.

Results are labeled `MyMemory` machine translations. HTTP errors, non-success
response statuses, quota failures, oversized responses, HTML and results without
Persian/Arabic characters are rejected rather than saved as definitions. Requests
have connection/read timeouts and a 64 KiB response limit. Results are not persisted
unless the user saves them to the notebook or personal dictionary.

The provider documents an anonymous quota of 5,000 characters/day; availability and
limits can change. See [API specification](https://mymemory.translated.net/doc/spec.php)
and [usage limits](https://mymemory.translated.net/doc/usagelimits.php).
A successful request does not guarantee a useful Persian translation. During local
verification, `hello` returned `سلام`, while `serendipity` returned a Latin copy that
the application correctly rejects (the latter has an offline definition).

## Rebuilding the data

Download the files listed in the manifest's `sourceFiles` from the pinned commit
under `Dictionaries/` and preserve their group and letter paths in `<cache>`.
The builder records which letter files are absent upstream. Then run:

```text
python scripts/build-lexicon.py <cache> <output>
```

Copy `dictionary-en-fa.sqlite.bin` and `manifest.json` into the existing asset folder,
retaining its license and notice. The script uses only Python's standard library,
validates SQLite integrity, emits source checksums, and uses deterministic gzip
metadata. Rebuilding from the same cached files reproduced the committed gzip hash.

For a bulk import, keep a copy of the previous compressed index and compare it
with the rebuilt one before shipping:

```text
python scripts/verify-lexicon.py --assets <rebuilt-assets> --previous <previous.sqlite.bin> --require-additive
```

The asset directory should include the license and notice alongside the generated
files. The optional `--apk` flag reads the new index from a built APK. The
comparison reports new single-token and multiword keys, deleted keys, and
changed definitions. Use `--require-additive` for import batches that must
preserve every existing key and definition.

Android validation runs this comparison automatically when a push or pull
request changes the dictionary asset. A push compares against the branch tip
before the push, and a pull request compares its merge result against the
target commit. The counts appear in the GitHub Actions job summary, including
when an update intentionally corrects existing definitions.

Run `python scripts/verify-lexicon.py --apk <apk>` to verify the actual APK assets, their hashes, all definitions and SQLite integrity.

Python/SQLite version differences may change output bytes; regenerate the manifest
with the asset whenever the builder or data changes.

## Acceptance still required on Android

Opening the dictionary with an empty search now shows an alphabetical list.
The search field accepts English words and Persian meanings. Persian lookup uses
a bundled inverted index over the existing definitions. Arabic yeh and kaf are
folded to Persian forms, and vowel marks, tatweel and zero-width non-joiners are
removed for matching. Each query word must match a word prefix in the definition.
Queries accept up to eight words and 80 input characters. Exact single-word
Persian senses rank first, followed by single-word English entries and shorter
terms. Personal definitions participate in search and override archived meanings.
The Saved words filter searches its retained definitions with the same rules.

The Dictionary tab in the home navigation opens this list as a full screen,
without opening a video. Switching tabs preserves the query, browse page and
selected entry. Back from an entry returns to its list, and Back from the list
returns to Files. If a video is loaded, Return to video opens its paused player.
The dictionary opened from the player menu or a selected subtitle word continues
to use the overlay panel.

Choose Save word in an entry to bookmark it, or Remove from saved to remove the
bookmark. Saved marks appear in list rows. The Saved words filter supports the
same English prefix search and alphabetical pages. Each bookmark keeps the term
and a definition snapshot in the user's Room database. Personal definitions
take priority, and the saved snapshot is available if an updated offline index
no longer contains the term or the index cannot be read.

Room version 4 adds the bookmark table through a migration from version 3.
Learning backups now use format version 2 and include bookmarks. This app also
reads version 1 backups, which contain no bookmark section. Restoring either
format adds entries while preserving existing entries with the same key.

Each page contains up to 40 terms merged from the packaged, supplemental and
personal dictionaries. Previous and Next replace the current page. The SQL query
uses a primary-key boundary and reads at most 41 rows per page. Tap a term to
open its full meaning and pronunciation. Searching resets the browse position.

Check first opening, several consecutive pages, returning from a definition,
clearing a search, editing a personal definition, rotation and large text on a
phone. Confirm personal meanings appear once and retain priority. The final page
should disable Next, and Previous should return to the preceding page.
Also switch from Dictionary to every other home tab and back, both from the
list and from an open entry. Repeat with the keyboard open and after rotation.
Verify Back first leaves the entry, then returns to Files. Return to a loaded
video and confirm its position and selected subtitle track are preserved.
Save a term, restart in airplane mode, find it under Saved words and open it.
Remove its bookmark and confirm its personal definition and any flashcard remain.
Test upgrading a version 3 database with existing cards and personal definitions,
then creating and restoring a backup with bookmarks. Restore a version 1 backup
as well and confirm previously saved bookmarks remain.
In airplane mode, search for قهوه and open coffee, then repeat with Arabic kaf
and yeh in a word such as كتاب. Check a multiword query, a personal-only meaning,
rapid language changes while typing, clearing the field and reopening a result.
Verify the first search after upgrade installs the larger index without losing
bookmarks, personal definitions or review progress.

Use `test-fixtures/broad-meanings.en.srt` with any suitable local test video:

- In airplane mode, tap `beautiful`, `forest`, `serendipity` and `language`.
- Tap a word immediately after installation and confirm video remains responsive.
- Tap several words quickly and confirm only the latest selected word gets a result.
- Confirm the three-second period starts after lookup, including online responses.
- Set a personal meaning for `the`, then confirm it overrides the packaged meaning.
- Expand and save a long definition; restart and verify notebook/backup compatibility.
- Try an absent invented name: no fabricated meaning; explicit online action or edit.
- With network enabled, explicitly request online translation; test offline and quota
  failures as well. Confirm errors remain retryable and do not save empty definitions.
- Rotate and leave/reopen the player while lookup is pending; check cancellation and
  fullscreen overlays on both API 26 and a current Android version.

JVM tests and host-side asset/service checks do not establish these device results.
