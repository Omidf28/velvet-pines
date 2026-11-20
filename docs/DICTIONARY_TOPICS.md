# Offline topic browsing

In Dictionary, open **All words** and choose a topic. The selected chip becomes
**Topics**. Open it again to switch topics or choose **All dictionary words**.
Selecting a topic clears the query. English prefix search, Persian meaning search,
CEFR levels and alphabetical pages work inside that topic. Selection survives
activity recreation and home-tab changes. **Saved words** uses its own personal
list filter; returning to topic browsing restores the previously selected topic.

Choose a word, then **Save word** or **Lists**, to retain it for offline study.
This uses the existing bookmark, personal-list and backup system. Bundled topics
are immutable source data, so their memberships are not copied into user backups.

The 12 initial categories use the `lexfile` classifications in the pinned Open
English WordNet 2025 XML archive already used for English definitions. The source
hash and licensing remain in the English asset manifest and notices. The shared
`topics.json` maps selected source classifications to readable names. No word lists
or definitions are authored one entry at a time. These are broad source categories,
not an official IELTS syllabus. A term qualifies if at least one source sense has
a matching classification; other meanings may have different subjects.

Browse results use entries available in the English–Persian dictionary, with
personal meanings taking priority. Counts describe that searchable intersection,
before a CEFR or search filter. For example, Food & drink has 2,246 entries and
Weather has 104. The source English index has additional terms without Persian
definitions; exact lookup for those remains available under All dictionary words.

The English read-only SQLite index adds a `(topic, term)` primary-key table and
retains each sense's original `lexfile` evidence in its payload. Topic searches
attach this verified index to the Persian read-only connection and filter before
the 41-row fetch. This preserves paging and avoids losing matches through filtering
an already truncated page. SQL values are bound parameters. Personal and curated
results are restricted to the same source membership and selected CEFR terms.

Rebuild with `python scripts/build-english-lexicon.py <source.xml.gz> <output-dir>`.
The builder copies the repository topic configuration into the output. Ship its
database, manifest and configuration together with the existing licenses/notices.
`python scripts/verify-english-lexicon.py` checks all membership rows against the
source evidence, topic counts, config hash, references and indexed queries. The
same verification runs against debug and release APK assets in CI.

Android tests exercise topic counts, page boundaries, English and Persian search,
personal overrides, exclusion of unrelated words, combined CEFR filtering,
selection after activity recreation, saving to a personal list and returning to
all words. Physical-phone checks remain pending for first installation, airplane
mode, large text, TalkBack, both orientations and first lookup after an upgrade.
