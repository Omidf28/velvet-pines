"""Normalize pinned CEFR-J and Octanove vocabulary profiles for offline use."""
import argparse
import csv
import hashlib
import io
import json
import pathlib
import re

PIN = 'd4e45b75b38f27b30dfc5c44d8c571aec7e7092f'
SOURCES = {
    'cefrj-vocabulary-profile-1.5.csv': 'CEFR-J',
    'octanove-vocabulary-profile-c1c2-1.0.csv': 'Octanove',
}
SOURCE_HASHES = {
    'cefrj-vocabulary-profile-1.5.csv': 'b0dd3c635f1c9a4fdf1490c7e5b7c48e8bbe55b652ad0c9860a95f98e10ae498',
    'octanove-vocabulary-profile-c1c2-1.0.csv': '18c33a407f2f89f7b8de9671c6d45fe3ea0bce45e7d2d7dcaab48d73e0f7b380',
}
TERM = re.compile(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*")


def build(cache, output):
    output.mkdir(parents=True, exist_ok=True)
    profiles, hashes, skipped = set(), {}, []
    for filename, source in SOURCES.items():
        raw = (cache / filename).read_bytes()
        hashes[filename] = hashlib.sha256(raw).hexdigest()
        assert hashes[filename] == SOURCE_HASHES[filename], f'Unexpected source version: {filename}'
        for row in csv.DictReader(io.StringIO(raw.decode('utf-8-sig'))):
            level, pos = row['CEFR'].strip(), row['pos'].strip()
            assert level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')
            assert len(pos) <= 80 and not any(c in pos for c in '\t\r\n')
            accepted = False
            for variant in row['headword'].split('/'):
                term = ' '.join(variant.lower().replace('’', "'").split())
                if TERM.fullmatch(term) and len(term) <= 80 and len(term.split()) <= 8:
                    profiles.add((term, level, pos, source))
                    accepted = True
            if not accepted:
                skipped.append(dict(source=source, headword=row['headword'], level=level, pos=pos))
    table = io.StringIO(newline='')
    writer = csv.writer(table, delimiter='\t', lineterminator='\n')
    writer.writerow(('term', 'level', 'pos', 'source'))
    writer.writerows(sorted(profiles))
    data = table.getvalue().encode()
    (output / 'profiles.tsv').write_bytes(data)
    manifest = dict(
        formatVersion=1, sourceCommit=PIN, sourceFiles=hashes,
        profiles=len(profiles), terms=len({row[0] for row in profiles}),
        levelTerms={level: len({row[0] for row in profiles if row[1] == level})
                    for level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')},
        sha256=hashlib.sha256(data).hexdigest(), skipped=skipped
    )
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in manifest.items() if key != 'skipped'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('cache', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    args = parser.parse_args()
    build(args.cache, args.output)
