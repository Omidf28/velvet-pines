"""Verify the shipped lexicon, optionally reading its assets directly from an APK."""
import argparse, gzip, hashlib, json, pathlib, re, sqlite3, zipfile

def verify(asset_dir, apk=None):
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
        assert len(manifest['sourceFiles']) == 52
        assert all(re.fullmatch('[a-f0-9]{64}', value) for value in manifest['sourceFiles'].values())
        assert b'Apache License' in read('LICENSE.txt')
        assert manifest['sourceCommit'].encode() in read('NOTICE.txt')
        with sqlite3.connect(':memory:') as conn:
            conn.deserialize(data)
            assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            assert conn.execute('PRAGMA user_version').fetchone()[0] == 1
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
        print(f'PASS: {count:,} entries; {words:,} single-token keys; hashes, license, integrity and all rows valid')
    finally:
        if archive: archive.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/lexicon')
    parser.add_argument('--apk', type=pathlib.Path)
    args = parser.parse_args()
    verify(args.assets, args.apk)
