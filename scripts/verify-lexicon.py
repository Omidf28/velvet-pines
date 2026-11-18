"""Verify the shipped lexicon, optionally reading its assets directly from an APK."""
import argparse, gzip, hashlib, json, pathlib, re, sqlite3, zipfile

def compare_previous(conn, previous, require_additive):
    conn.execute("ATTACH DATABASE ':memory:' AS previous")
    conn.deserialize(gzip.decompress(previous.read_bytes()), name='previous')
    added_words, added_phrases = conn.execute("""
        SELECT coalesce(sum(instr(current.term, ' ') = 0), 0),
               coalesce(sum(instr(current.term, ' ') > 0), 0)
        FROM main.lexicon AS current
        LEFT JOIN previous.lexicon AS old USING (term)
        WHERE old.term IS NULL
    """).fetchone()
    removed, changed = conn.execute("""
        SELECT coalesce(sum(current.term IS NULL), 0),
               coalesce(sum(current.definition <> old.definition), 0)
        FROM previous.lexicon AS old
        LEFT JOIN main.lexicon AS current USING (term)
    """).fetchone()
    print(f'Comparison: {added_words + added_phrases:,} added '
          f'({added_words:,} single-token keys, {added_phrases:,} multiword keys), '
          f'{removed:,} removed, {changed:,} definitions changed')
    if require_additive and (removed or changed):
        raise SystemExit('Bulk import must preserve all previous keys and definitions.')


def verify(asset_dir, apk=None, previous=None, require_additive=False):
    archive = zipfile.ZipFile(apk) if apk else None
    def read(name):
        return archive.read('assets/lexicon/' + name) if archive else (asset_dir / name).read_bytes()
    try:
        if archive:
            expected = {'assets/lexicon/' + name for name in ('manifest.json', 'dictionary-en-fa.sqlite.bin', 'LICENSE.txt', 'NOTICE.txt')}
            assert {name for name in archive.namelist() if name.startswith('assets/lexicon/')} == expected
        manifest = json.loads(read('manifest.json'))
        compressed = read('dictionary-en-fa.sqlite.bin')
        assert hashlib.sha256(compressed).hexdigest() == manifest['compressedSha256']
        data = gzip.decompress(compressed)
        assert len(data) == manifest['databaseBytes']
        assert hashlib.sha256(data).hexdigest() == manifest['databaseSha256']
        groups = manifest.get('sourceGroups', ['generic-1', 'generic-13'])
        absent = manifest.get('absentSourceLetters', {})
        assert len(groups) == len(set(groups))
        assert set(manifest['sourceFiles']) == {
            f'{group}/{letter}.json' for group in groups
            for letter in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' if letter not in absent.get(group, '')
        }
        assert 0 < len(data) <= 64 * 1024 * 1024
        assert all(re.fullmatch('[a-f0-9]{64}', value) for value in manifest['sourceFiles'].values())
        assert b'Apache License' in read('LICENSE.txt')
        assert manifest['sourceCommit'].encode() in read('NOTICE.txt')
        with sqlite3.connect(':memory:') as conn:
            conn.deserialize(data)
            assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            version = manifest['formatVersion']
            assert version in (1, 2)
            assert conn.execute('PRAGMA user_version').fetchone()[0] == version
            count, words = conn.execute("SELECT count(*),sum(instr(term, ' ')=0) FROM lexicon").fetchone()
            assert count == manifest['entries'] and words == manifest['words']
            assert count - words == manifest['phrases']
            for key, meaning in conn.execute('SELECT term,definition FROM lexicon'):
                assert len(key) <= 80 and len(key.split()) <= 8
                assert re.fullmatch(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*", key)
                assert 0 < len(meaning.encode('utf-16-le')) // 2 <= 1000
                assert re.search('[\u0600-\u06ff]', meaning)
                assert not any(ord(c) < 32 and c not in '\n\t' for c in meaning)
            for term in ('beautiful','forest','serendipity','language','coffee'):
                assert conn.execute('SELECT 1 FROM lexicon WHERE term=?', (term,)).fetchone(), term
            assert not conn.execute('SELECT 1 FROM lexicon WHERE term=?', ('zzvelvetqz',)).fetchone()
            query = 'SELECT term, definition FROM lexicon WHERE term >= ? AND term < ? ORDER BY term LIMIT ?'
            for prefix in ('a', 'coffee', "can'", 'take off', 'well-', 'zzvelvetqz'):
                rows = conn.execute(query, (prefix, prefix + '\x7f', 41)).fetchall()
                keys = [row[0] for row in rows]
                assert len(keys) <= 41 and keys == sorted(keys)
                assert all(key.startswith(prefix) for key in keys)
            plan = conn.execute('EXPLAIN QUERY PLAN ' + query, ('coffee', 'coffee\x7f', 41)).fetchall()
            assert any('SEARCH' in row[3] and 'PRIMARY KEY' in row[3] for row in plan), plan
            browse = 'SELECT term, definition FROM lexicon WHERE term > ? ORDER BY term LIMIT ?'
            for boundary in ('', 'coffee', 'take off', 'zzzzzz'):
                keys = [row[0] for row in conn.execute(browse, (boundary, 41))]
                assert len(keys) <= 41 and keys == sorted(set(keys))
                assert all(key > boundary for key in keys)
            plan = conn.execute('EXPLAIN QUERY PLAN ' + browse, ('coffee', 41)).fetchall()
            assert any('SEARCH' in row[3] and 'PRIMARY KEY' in row[3] for row in plan), plan
            if version >= 2:
                assert conn.execute('SELECT count(*) FROM persian_terms').fetchone()[0] == manifest['persianIndexRows']
                assert conn.execute('SELECT count(*) FROM persian_terms LEFT JOIN lexicon USING(term) WHERE lexicon.term IS NULL').fetchone()[0] == 0
                reverse = ("SELECT term, definition FROM lexicon WHERE term IN "
                           "(SELECT term FROM persian_terms WHERE token >= ? AND token < ?) "
                           "ORDER BY NOT EXISTS (SELECT 1 FROM persian_terms AS exact "
                           "WHERE exact.token = ? AND exact.term = lexicon.term AND exact.exact_meaning = 1), "
                           "instr(term, ' ') > 0, length(term), term LIMIT ?")
                for token, expected in (('قهوه', 'coffee'), ('جنگل', 'forest')):
                    keys = [row[0] for row in conn.execute(reverse, (token, token + '\uffff', token, 41))]
                    assert expected in keys[:5], (token, keys)
                plan = conn.execute('EXPLAIN QUERY PLAN ' + reverse, ('قهوه', 'قهوه\uffff', 'قهوه', 41)).fetchall()
                assert any('SEARCH persian_terms USING PRIMARY KEY' in row[3] for row in plan), plan
                assert conn.execute('SELECT 1 FROM persian_terms WHERE token = ? LIMIT 1', ('كتاب',)).fetchone() is None
            if previous is not None:
                compare_previous(conn, previous, require_additive)
        print(f'PASS: {count:,} entries; {words:,} single-token keys; hashes, license, integrity, all rows and indexed search/browse queries valid')
    finally:
        if archive: archive.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/lexicon')
    parser.add_argument('--apk', type=pathlib.Path)
    parser.add_argument('--previous', type=pathlib.Path, help='Previous compressed lexicon asset to compare')
    parser.add_argument('--require-additive', action='store_true', help='Fail if the comparison finds deleted keys or changed definitions')
    args = parser.parse_args()
    if args.require_additive and args.previous is None:
        parser.error('--require-additive requires --previous')
    verify(args.assets, args.apk, args.previous, args.require_additive)
