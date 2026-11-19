"""Verify the offline English dictionary or the actual assets packaged in an APK."""
import argparse
import collections
import gzip
import hashlib
import json
import pathlib
import re
import sqlite3
import zipfile


def verify(asset_dir, apk=None):
    archive = zipfile.ZipFile(apk) if apk else None
    def read(name):
        return archive.read('assets/english-lexicon/' + name) if archive else (asset_dir / name).read_bytes()
    try:
        if archive:
            expected = {'assets/english-lexicon/' + name for name in
                        ('dictionary-en.sqlite.bin', 'manifest.json', 'LICENSE.md', 'WNDB_License.txt', 'NOTICE.txt')}
            assert {name for name in archive.namelist() if name.startswith('assets/english-lexicon/')} == expected
        manifest = json.loads(read('manifest.json'))
        assert manifest['formatVersion'] == 1 and manifest['sourceVersion'] == '2025'
        assert manifest['sourceSha256'] == '9ca6d1dcb75f822fdd66617f7d9da48142ace38dd544d6ad5e2feca1674ad3fe'
        assert b'Princeton' in read('LICENSE.md') and b'Attribution 4.0' in read('LICENSE.md')
        assert b'Princeton' in read('WNDB_License.txt')
        assert manifest['sourceSha256'].encode() in read('NOTICE.txt')
        compressed = read('dictionary-en.sqlite.bin')
        assert hashlib.sha256(compressed).hexdigest() == manifest['compressedSha256']
        data = gzip.decompress(compressed)
        assert len(data) == manifest['databaseBytes'] <= 128 * 1024 * 1024
        assert hashlib.sha256(data).hexdigest() == manifest['databaseSha256']
        counts = collections.Counter()
        max_entry_bytes = 0
        with sqlite3.connect(':memory:') as conn:
            conn.deserialize(data)
            assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            assert conn.execute('PRAGMA user_version').fetchone()[0] == 1
            assert conn.execute('SELECT count(*) FROM entries').fetchone()[0] == manifest['entries']
            for term, payload in conn.execute('SELECT term,payload FROM entries'):
                assert len(term) <= 80 and len(term.split()) <= 8
                assert re.fullmatch(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*", term)
                max_entry_bytes = max(max_entry_bytes, len(payload.encode()))
                entries = json.loads(payload)
                assert entries and len({entry['id'] for entry in entries}) == len(entries)
                counts['lexicalEntries'] += len(entries)
                sense_ids = []
                for entry in entries:
                    assert entry['lemma'] and entry['partOfSpeech'] in ('noun', 'verb', 'adjective', 'adverb')
                    assert all(p['ipa'] and isinstance(p['variety'], str) for p in entry['pronunciations'])
                    assert all(isinstance(form, str) and form for form in entry['forms'])
                    counts['pronunciations'] += len(entry['pronunciations'])
                    counts['forms'] += len(entry['forms'])
                    assert entry['senses']
                    counts['senses'] += len(entry['senses'])
                    for sense in entry['senses']:
                        sense_ids.append(sense['id'])
                        assert sense['definitions'] and sense['synset'].startswith('oewn-')
                        for field in ('definitions', 'examples', 'synonyms', 'antonyms', 'related'):
                            assert all(isinstance(text, str) and text.strip() for text in sense[field])
                            counts[field] += len(sense[field])
                assert len(set(sense_ids)) == len(sense_ids)
            for key, count in counts.items():
                assert count == manifest[key], (key, count)
            assert max_entry_bytes == manifest['maxEntryBytes'] <= 1024 * 1024
            assert conn.execute('SELECT count(*) FROM forms').fetchone()[0] == manifest['formMappings']
            assert conn.execute('SELECT count(*) FROM forms LEFT JOIN entries ON forms.headword=entries.term WHERE entries.term IS NULL').fetchone()[0] == 0
            for headword, entry_id in conn.execute('SELECT headword,entry_id FROM forms'):
                records = json.loads(conn.execute('SELECT payload FROM entries WHERE term=?', (headword,)).fetchone()[0])
                assert any(record['id'] == entry_id for record in records)
            assert conn.execute('SELECT headword FROM forms WHERE term=?', ('analyses',)).fetchone()[0] == 'analysis'
            form_plan = conn.execute('EXPLAIN QUERY PLAN SELECT headword,entry_id FROM forms WHERE term=?', ('analyses',)).fetchall()
            assert any('SEARCH' in row[3] and 'PRIMARY KEY' in row[3] for row in form_plan), form_plan
            query = 'SELECT payload FROM entries WHERE term = ? LIMIT 1'
            plan = conn.execute('EXPLAIN QUERY PLAN ' + query, ('coffee',)).fetchall()
            assert any('SEARCH' in row[3] and 'PRIMARY KEY' in row[3] for row in plan), plan
            coffee = json.loads(conn.execute(query, ('coffee',)).fetchone()[0])[0]
            assert any(p['variety'] == 'GB' and p['ipa'] == '\u02c8k\u0252.fi' for p in coffee['pronunciations'])
            assert 'java' in coffee['senses'][0]['synonyms'] and coffee['senses'][0]['examples']
            analysis = json.loads(conn.execute(query, ('analysis',)).fetchone()[0])[0]
            assert 'analyses' in analysis['forms']
            assert any('synthesis' in sense['antonyms'] for sense in analysis['senses'])
            assert conn.execute(query, ('zzvelvetqz',)).fetchone() is None
        print(f"PASS: {manifest['entries']:,} English terms; {counts['senses']:,} senses; all rows, hashes, licenses and indexed queries valid")
    finally:
        if archive:
            archive.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=pathlib.Path,
                        default=pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/english-lexicon')
    parser.add_argument('--apk', type=pathlib.Path)
    args = parser.parse_args()
    verify(args.assets, args.apk)
