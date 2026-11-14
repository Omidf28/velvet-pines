# Offline dictionary search — 0.6.1-dev

The bundled English–Persian index can now be searched independently of the current
subtitle. Entry points are the library's Dictionary action, the player's overflow
menu, and the expanded word card (which pre-fills the selected term).

## Behavior

- Enter an English word or the beginning of a phrase. Results match the **start of
  the dictionary key**, not arbitrary text within definitions. Search is offline.
- Up to 40 alphabetical results appear. A message indicates more matches; refine
  the query to reach a later entry. This is deliberately not an unbounded list.
- Select a result to read the full stored definition and source, pronounce it using
  an installed offline English voice, or add/edit/remove a personal definition.
- A complete supported spelling such as `prioritise` appears in results if the
  packaged dictionary has only its alternate spelling. The row keeps the typed
  word and identifies the definition as a spelling variant.
- A typed inflection such as `panicking` can also appear with a possible base
  form definition when the exact spelling is absent. The same fallback is used
  when selecting a word in subtitles.
- The direct-definition action also works for a valid typed term with no prefix
  results. This supports inflection lookup and adding an absent personal term.
- Personal entries override packaged and curated meanings, including the preview
  list. Removing an override restores the regular offline resolution on refresh.
- Personal edits affect future subtitle lookups across all videos. They do not
  create notebook cards or invent dialogue/media context. Use the movie's word
  card to save a vocabulary entry with its real source and playback timestamp.
- Opening, searching and closing the panel do not pause or seek playback. Requesting
  pronunciation explicitly pauses playback, matching the existing speech workflow;
  playback resumes through the player's controls or the retained study card.
- Search is in a separate window sharing the dark playback overlay. It does not
  change the video layout. The panel accommodates the keyboard using IME insets.
  The close action hides the keyboard and stops dictionary pronunciation.

The search panel does not perform online translation or send search queries to a
service. The existing explicit online fallback remains in the subtitle word card.

## Implementation

Input is case-insensitive, normalizes curly apostrophes and space/tab/NBSP separators,
and allows an unfinished final apostrophe or hyphen while typing. Bounds remain
80 normalized characters/eight words. SQL wildcards, punctuation outside the word
grammar, digits and non-English scripts are not accepted as search prefixes.

After a 200 ms debounce, lookup runs on `Dispatchers.IO`. SQLite uses a parameterized
primary-key range query (`term >= prefix AND term < prefix + DEL`), valid because
all packaged keys use the supported ASCII grammar. The query reads at most 41 rows
so the UI can detect another page without scanning/counting every entry. A bounded
sorted merge adds personal and curated entries and preserves override priority.

A query or personal-data change replaces the displayed search state immediately.
Compose effect cancellation discards the previous request's result. Selection and
personal edits likewise invalidate the detail result. The packaged index, licensing,
Room schema and learning-backup format are unchanged from 0.6.0-dev.

## Verification

JVM tests cover prefix normalization and limits, unfinished typing, rejected wildcard
and injection-like input, literal prefix matching, personal/curated priority,
deduplication, sorting, first-page limits and the more-results indicator.
`scripts/verify-lexicon.py` also runs the actual prefix SQL against the shipped SQLite
database and checks that its query plan uses a primary-key search.

Android runtime acceptance remains pending:

1. From a fresh library without an open video, open Dictionary; search `coffee`,
   `forest` and `take off`, then inspect a multiword definition.
2. Search `a`: confirm 40 results with the refinement message. Type quickly between
   `coffee`, `forest` and an absent invented word; no older result may replace the
   current query. Repeat during first-time lexicon installation.
3. Search mixed case, curly apostrophes, `can'` and `well-`. Try unsupported scripts,
   `%`, a long query, and nine words; the panel should explain the input requirement.
4. Add a personal meaning for `coffee`; verify the list, full definition and movie
   word card agree. Remove it and confirm the packaged meaning returns. Restart and
   verify a retained override and its compatibility with existing backups.
5. Open from a word card: the selected term should pre-fill. Return to results and
   close the panel; the video, subtitle and previous study-pause action must remain
   usable. Test both pause-on-word-tap settings.
6. In landscape during playback, open/close the panel repeatedly. Confirm video
   bounds, position, fit/crop mode and orientation remain unchanged. Try the keyboard,
   large fonts, TalkBack, Android Back, outside tap and the explicit Close button.
7. Request pronunciation: video pauses explicitly; closing stops speech. Test missing
   offline voices and the speech-help dialog without accidental playback resumption.
8. Rotate, enter/leave PiP, and background the application during a pending query or
   personal edit. Verify no late result, stuck window or data loss. Test low-storage
   lexicon failure and Retry separately.

Host tests do not establish any of these Android UI results.
