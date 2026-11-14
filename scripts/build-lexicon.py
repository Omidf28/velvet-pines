"""Build the pinned Apache-2.0 English/Persian lexicon; Python standard library only."""
import argparse, gzip, hashlib, html, json, re, sqlite3, pathlib
PIN = '224f15d42d145c59b5dc2c6890c4736693613cc2'
BASE_GROUPS = ('generic-1', 'generic-13')
EXTRA_GROUPS = ('chat-slang', 'idioms-1', 'idioms-2', 'idioms-3', 'essential-english-words-2', 'generic-16')
ABSENT_LETTERS = {'idioms-1': 'X', 'idioms-2': 'JQVXZ', 'idioms-3': 'KQRX'}

def build(cache, output):
    output.mkdir(parents=True, exist_ok=True)
    entries, hashes = {}, {}
    original_keys = set()
    for group in BASE_GROUPS + EXTRA_GROUPS:
        if group == EXTRA_GROUPS[0]:
            original_keys = {key for key, meanings in entries.items() if meanings}
        for letter in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ':
            if letter in ABSENT_LETTERS.get(group, ''):
                continue
            path = cache / group / (letter + '.json')
            raw = path.read_bytes()
            hashes[f'{group}/{letter}.json'] = hashlib.sha256(raw).hexdigest()
            for row in json.loads(raw.decode('utf-8-sig'))['Words']:
                key = ' '.join(row['EnglishWord'].lower().replace('’', "'").split())
                if key in original_keys:
                    continue
                if len(key) > 80 or len(key.split()) > 8 or not re.fullmatch(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*", key):
                    continue
                meanings = entries.setdefault(key, [])
                for item in row.get('Meanings', []):
                    value = html.unescape(re.sub(r'<[^>]*>', '', item))
                    value = ' '.join(''.join(c for c in value if ord(c) >= 32).split())
                    if re.search('[\u0600-\u06ff]', value) and value not in meanings and len(meanings) < 6:
                        meanings.append(value)
    entries = {key: '\n'.join(value).encode('utf-16-le')[:2000].decode('utf-16-le', errors='ignore') for key, value in entries.items() if value}
    db = output / 'dictionary-en-fa.sqlite'
    if db.exists(): db.unlink()
    with sqlite3.connect(db) as conn:
        conn.execute('PRAGMA page_size=4096')
        conn.execute('PRAGMA user_version=1')
        conn.execute('CREATE TABLE lexicon(term TEXT PRIMARY KEY, definition TEXT NOT NULL) WITHOUT ROWID')
        conn.executemany('INSERT INTO lexicon VALUES (?,?)', sorted(entries.items()))
        conn.commit()
        assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    data = db.read_bytes()
    compressed = gzip.compress(data, compresslevel=9, mtime=0)
    (output / 'dictionary-en-fa.sqlite.bin').write_bytes(compressed)
    manifest = dict(formatVersion=1, sourceCommit=PIN, sourceGroups=BASE_GROUPS + EXTRA_GROUPS,
                    absentSourceLetters=ABSENT_LETTERS,
                    entries=len(entries), addedEntries=len(entries) - len(original_keys),
                    words=sum(' ' not in key for key in entries), phrases=sum(' ' in key for key in entries),
                    databaseBytes=len(data), databaseSha256=hashlib.sha256(data).hexdigest(),
                    compressedSha256=hashlib.sha256(compressed).hexdigest(), sourceFiles=hashes)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in manifest.items() if k != 'sourceFiles'}, indent=2))
    for word in ('hello','coffee','beautiful','serendipity','forest','language','running'):
        print(word, ':', ascii(entries.get(word, '(missing)')[:100]))
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('cache', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    args = parser.parse_args()
    build(args.cache, args.output)
