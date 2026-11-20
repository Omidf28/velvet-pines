# Personal vocabulary lists

Open a dictionary word and choose **Lists**. Select as many lists as needed, or
choose **New list**. Creating a list here also adds the current word. Adding a word
to a list saves its available definition for offline use; an existing saved
definition is preserved.

In dictionary results, select **Saved words**, then tap the selected chip again to
open its list menu. Select a list to filter the results, or choose **Manage lists**
to create, rename, delete or open a list. Opening from the manager clears the query;
search and CEFR filters continue to apply within the selected list. **All saved
words** returns to the complete saved collection.

Names support 1–80 characters, collapse spaces, and are unique ignoring case.
Removing a word from one list keeps its bookmark and its other memberships.
Deleting a list keeps all its saved words. Removing the bookmark removes its list
memberships. These actions leave personal definitions and flashcards intact.

Room schema 5 adds `vocabulary_lists` and `vocabulary_list_words` through a migration
from schema 4. Foreign keys cascade list renames and removal of lists/bookmarks;
the membership primary key prevents duplicate assignments. List changes and
automatic bookmark saves run in transactions.

Learning-backup version 3 includes names and memberships after saved dictionary
words. Readers also accept versions 1 and 2 with empty list sections. Restore is
additive: existing bookmarks and lists with the same normalized name are preserved,
new memberships are merged, and all sections restore in one transaction. Backups
are limited to 32 MiB and 20,000 records per section, including memberships.
Duplicate or malformed lists, duplicate memberships and dangling references are
rejected before restore. A list renamed since a backup can coexist with its old
name when that backup is restored.

Automated checks cover normalization, legacy backup readers, round trips, malformed
references, Room restore, multiple memberships, rename conflicts, cascades, and the
UI path through creation, opening, activity recreation, renaming and deletion.
Physical-phone acceptance remains pending: repeat these operations offline, with
large fonts and TalkBack, combine search with CEFR filtering, and transfer a backup
to a second installation.
