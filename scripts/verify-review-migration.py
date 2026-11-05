"""Exercise the app's migration and review queries using desktop SQLite.

Optionally compare the migrated table with Room's generated CREATE TABLE.
This complements, but does not replace, an Android installation/upgrade test.
"""
import argparse
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/subtitleplayer/prototype/SavedWordsDatabase.kt").read_text(encoding="utf-8-sig")
MIGRATIONS = re.findall(r'db.execSQL\("([^"]+)"\)', SOURCE)
QUERIES = re.findall(r'@Query\("([^"]+)"\)', SOURCE)
DUE = next(query for query in QUERIES if "WHERE reviewDueAt" in query)
DELETE_WORD = next(query for query in QUERIES if query.startswith("DELETE FROM saved_words"))
SOURCE_DUE = next(query for query in QUERIES if "WHERE sourceUri = :source AND reviewDueAt" in query)
RECORD = next(query for query in QUERIES if "SET reviewLevel" in query)
UPDATE_DEFINITION = next(query for query in QUERIES if "SET headword" in query)
V1_SCHEMA = """CREATE TABLE saved_words (
word TEXT NOT NULL, wordKey TEXT NOT NULL, headword TEXT NOT NULL,
meaning TEXT NOT NULL, dialogue TEXT NOT NULL, positionMs INTEGER NOT NULL,
subtitleStartMs INTEGER NOT NULL, sourceUri TEXT NOT NULL, sourceName TEXT NOT NULL,
id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"""
V1_INDEX = """CREATE UNIQUE INDEX index_saved_words_sourceUri_subtitleStartMs_dialogue_wordKey
ON saved_words (sourceUri, subtitleStartMs, dialogue, wordKey)"""
OLD_WORD = ("went", "go", "go", "رفتن", "We went home.", 2500, 2000, "asset:///demo.mp4", "نمونه", 1)
COLUMNS = "word, wordKey, headword, meaning, dialogue, positionMs, subtitleStartMs, sourceUri, sourceName, id"
ROOM_SCHEMA = None


class ReviewMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute(V1_SCHEMA)
        self.db.execute(V1_INDEX)
        self.db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", OLD_WORD)
        self.assertEqual(4, len(MIGRATIONS))
        for sql in MIGRATIONS:
            self.db.execute(sql)

    def test_existing_word_content_and_id_survive_upgrade(self):
        self.assertEqual(OLD_WORD, self.db.execute(f"SELECT {COLUMNS} FROM saved_words").fetchone())
        self.assertEqual((0, 0, 0), self.db.execute("SELECT reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone())
        self.assertEqual(1, len(self.db.execute(DUE, {"now": 0}).fetchall()))

    def test_review_persists_and_becomes_due_at_exact_boundary(self):
        args = dict(id=1, expectedCount=0, level=1, dueAt=86400000)
        self.assertEqual(1, self.db.execute(RECORD, args).rowcount)
        self.assertEqual([], self.db.execute(DUE, {"now": 86399999}).fetchall())
        self.assertEqual(1, len(self.db.execute(DUE, {"now": 86400000}).fetchall()))
        self.assertEqual((1, 86400000, 1), self.db.execute("SELECT reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone())

    def test_duplicate_or_stale_answer_cannot_overwrite_progress(self):
        args = dict(id=1, expectedCount=0, level=3, dueAt=10000)
        self.assertEqual(1, self.db.execute(RECORD, args).rowcount)
        self.assertEqual(0, self.db.execute(RECORD, {**args, "level": 0, "dueAt": 2}).rowcount)
        self.assertEqual((3, 10000, 1), self.db.execute("SELECT reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone())

    def test_duplicate_save_preserves_review_and_unique_index(self):
        self.db.execute(RECORD, dict(id=1, expectedCount=0, level=2, dueAt=100))
        # Room's IGNORE insert for the same source/dialogue/word, with a new auto ID.
        self.db.execute(
            f"INSERT OR IGNORE INTO saved_words ({COLUMNS.rsplit(', id', 1)[0]}) VALUES ({','.join('?' * 9)})",
            OLD_WORD[:-1],
        )
        self.assertEqual((1, 2, 100, 1), self.db.execute("SELECT id, reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone())
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM saved_words").fetchone()[0])

    def test_due_query_is_ordered_and_bounded(self):
        for ident in range(2, 62):
            row = (*OLD_WORD[:6], ident * 10000, *OLD_WORD[7:9], ident)
            self.db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", row)
        rows = self.db.execute(DUE, {"now": 0}).fetchall()
        self.assertEqual(50, len(rows))
        self.assertEqual(list(range(1, 51)), [row[9] for row in rows])

    def test_resaving_a_corrected_definition_keeps_id_and_review_schedule(self):
        self.db.execute(RECORD, dict(id=1, expectedCount=0, level=3, dueAt=5000))
        self.db.execute(UPDATE_DEFINITION, dict(
            source=OLD_WORD[7], start=OLD_WORD[6], dialogue=OLD_WORD[4],
            key=OLD_WORD[1], headword="go", meaning="معنی اصلاح‌شده",
        ))
        self.assertEqual(
            (1, "go", "معنی اصلاح‌شده", 3, 5000, 1),
            self.db.execute("SELECT id, headword, meaning, reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone(),
        )

    def test_upgrade_from_version_two_preserves_review_progress(self):
        db = sqlite3.connect(":memory:")
        self.addCleanup(db.close)
        db.execute(V1_SCHEMA)
        db.execute(V1_INDEX)
        db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", OLD_WORD)
        for sql in MIGRATIONS[:3]:
            db.execute(sql)
        db.execute(RECORD, dict(id=1, expectedCount=0, level=4, dueAt=123456))
        db.execute(MIGRATIONS[3])
        self.assertEqual(
            (1, 4, 123456, 1),
            db.execute("SELECT id, reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone(),
        )
        self.assertEqual(0, db.execute("SELECT count(*) FROM personal_dictionary").fetchone()[0])

    def test_personal_definitions_are_unique_and_can_be_reset_independently(self):
        self.db.execute(RECORD, dict(id=1, expectedCount=0, level=2, dueAt=5000))
        sql = "INSERT OR REPLACE INTO personal_dictionary (wordKey, headword, meaning) VALUES (?, ?, ?)"
        self.db.execute(sql, ("went", "go", "معنی اول"))
        self.db.execute(sql, ("went", "go", "معنی جدید"))
        self.assertEqual([("went", "go", "معنی جدید")], self.db.execute("SELECT * FROM personal_dictionary").fetchall())
        self.db.execute("DELETE FROM personal_dictionary WHERE wordKey = ?", ("went",))
        self.assertEqual([], self.db.execute("SELECT * FROM personal_dictionary").fetchall())
        self.assertEqual(
            (1, 2, 5000, 1),
            self.db.execute("SELECT id, reviewLevel, reviewDueAt, reviewCount FROM saved_words").fetchone(),
        )

    def test_deletion_targets_only_the_requested_saved_entry(self):
        row = (*OLD_WORD[:6], 9000, *OLD_WORD[7:9], 2)
        self.db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", row)
        self.db.execute("INSERT INTO personal_dictionary VALUES ('went', 'go', 'معنی شخصی')")
        self.assertEqual(1, self.db.execute(DELETE_WORD, {"id": 1}).rowcount)
        self.assertEqual(0, self.db.execute(DELETE_WORD, {"id": 1}).rowcount)
        self.assertEqual([row], self.db.execute(f"SELECT {COLUMNS} FROM saved_words").fetchall())
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM personal_dictionary").fetchone()[0])

    def test_undo_restores_full_snapshot_and_never_replaces_existing_data(self):
        self.db.execute(RECORD, dict(id=1, expectedCount=0, level=4, dueAt=90000))
        snapshot = self.db.execute("SELECT * FROM saved_words WHERE id=1").fetchone()
        self.db.execute(DELETE_WORD, {"id": 1})
        sql = "INSERT OR IGNORE INTO saved_words VALUES (" + ",".join("?" * len(snapshot)) + ")"
        self.assertEqual(1, self.db.execute(sql, snapshot).rowcount)
        self.assertEqual(snapshot, self.db.execute("SELECT * FROM saved_words WHERE id=1").fetchone())
        self.db.execute(UPDATE_DEFINITION, dict(
            source=OLD_WORD[7], start=OLD_WORD[6], dialogue=OLD_WORD[4],
            key=OLD_WORD[1], headword="go", meaning="تغییر تازه",
        ))
        self.assertEqual(0, self.db.execute(sql, snapshot).rowcount)
        self.assertEqual("تغییر تازه", self.db.execute("SELECT meaning FROM saved_words WHERE id=1").fetchone()[0])

    def test_source_review_filters_due_dates_and_limits_without_touching_other_sources(self):
        row = (*OLD_WORD[:6], 9000, "movie-b", OLD_WORD[8], 2)
        self.db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", row)
        self.db.execute(RECORD, dict(id=2, expectedCount=0, level=1, dueAt=200))
        self.assertEqual([], self.db.execute(SOURCE_DUE, {"source": "movie-b", "now": 199}).fetchall())
        self.assertEqual(1, len(self.db.execute(SOURCE_DUE, {"source": "movie-b", "now": 200}).fetchall()))
        for ident in range(3, 64):
            row = (*OLD_WORD[:6], ident * 10000, "movie-b", OLD_WORD[8], ident)
            self.db.execute(f"INSERT INTO saved_words ({COLUMNS}) VALUES ({','.join('?' * 10)})", row)
        rows = self.db.execute(SOURCE_DUE, {"source": "movie-b", "now": 100}).fetchall()
        self.assertEqual(50, len(rows))
        self.assertEqual(list(range(3, 53)), [item[9] for item in rows])
        self.assertTrue(all(item[7] == "movie-b" for item in rows))
        self.assertEqual(OLD_WORD, self.db.execute(f"SELECT {COLUMNS} FROM saved_words WHERE id=1").fetchone())

    def test_restore_preserves_existing_definition_and_review(self):
        self.db.execute(RECORD, dict(id=1, expectedCount=0, level=4, dueAt=5000))
        before = self.db.execute("SELECT * FROM saved_words WHERE id=1").fetchone()
        columns = COLUMNS.rsplit(", id", 1)[0]
        self.db.execute(f"INSERT OR IGNORE INTO saved_words ({columns}) VALUES ({','.join('?' * 9)})", OLD_WORD[:-1])
        self.assertEqual(before, self.db.execute("SELECT * FROM saved_words WHERE id=1").fetchone())
        self.db.execute("INSERT INTO personal_dictionary VALUES ('hello', 'hello', 'فعلی')")
        self.db.execute("INSERT OR IGNORE INTO personal_dictionary VALUES ('hello', 'hello', 'پشتیبان')")
        self.assertEqual("فعلی", self.db.execute("SELECT meaning FROM personal_dictionary").fetchone()[0])

    def test_transaction_failure_rolls_back_all_restored_rows(self):
        self.db.commit()
        with self.assertRaises(RuntimeError):
            with self.db:
                self.db.execute("INSERT INTO personal_dictionary VALUES ('hello', 'hello', 'سلام')")
                raise RuntimeError("Simulated failure before restoring other records")
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM personal_dictionary").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM saved_words").fetchone()[0])

    def test_migrated_schema_matches_room_generated_schema(self):
        if ROOM_SCHEMA is None:
            self.skipTest("pass --room-schema to compare Room's generated schema")
        creates = re.findall(r'connection.execSQL\("(CREATE (?:TABLE|UNIQUE INDEX)[^"]+)"\)', ROOM_SCHEMA.read_text(encoding="utf-8-sig"))
        fresh = sqlite3.connect(":memory:")
        self.addCleanup(fresh.close)
        for sql in creates:
            fresh.execute(sql)
        for table in ("saved_words", "personal_dictionary"):
            self.assertEqual(
                fresh.execute(f"PRAGMA table_info({table})").fetchall(),
                self.db.execute(f"PRAGMA table_info({table})").fetchall(),
            )
            self.assertEqual(
                fresh.execute(f"PRAGMA index_list({table})").fetchall(),
                self.db.execute(f"PRAGMA index_list({table})").fetchall(),
            )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--room-schema", type=Path)
    args, remaining = parser.parse_known_args()
    ROOM_SCHEMA = args.room_schema
    unittest.main(argv=[__file__, *remaining], verbosity=2)
