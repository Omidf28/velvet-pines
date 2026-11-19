"""Build offline English entry details from the pinned Open English Wordnet release."""
import argparse
import collections
import gzip
import hashlib
import json
import pathlib
import re
import sqlite3
import xml.etree.ElementTree as ET

SOURCE_URL = 'https://en-word.net/downloads/english-wordnet-2025.xml.gz'
SOURCE_SHA256 = '9ca6d1dcb75f822fdd66617f7d9da48142ace38dd544d6ad5e2feca1674ad3fe'
TERM = re.compile(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*")
PARTS_OF_SPEECH = {'n': 'noun', 'v': 'verb', 'a': 'adjective', 's': 'adjective', 'r': 'adverb'}


def clean(text):
    return ' '.join(text.split())


def unique(values):
    return list(dict.fromkeys(value for value in values if value))


def lookup_key(text):
    key = clean(text.lower().replace('’', "'"))
    return key if TERM.fullmatch(key) and len(key) <= 80 and len(key.split()) <= 8 else None


def build(source, output):
    raw = source.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == SOURCE_SHA256, 'Unexpected Wordnet source version'
    lexicon = ET.fromstring(gzip.decompress(raw)).find('Lexicon')
    assert lexicon.get('id') == 'oewn' and lexicon.get('version') == '2025'
    entries = lexicon.findall('LexicalEntry')
    synsets = {synset.get('id'): synset for synset in lexicon.findall('Synset')}
    lemmas = {entry.get('id'): clean(entry.find('Lemma').get('writtenForm')) for entry in entries}
    sense_words = {sense.get('id'): lemmas[entry.get('id')]
                   for entry in entries for sense in entry.findall('Sense')}
    assert len(lemmas) == len(entries)
    assert len(sense_words) == sum(len(entry.findall('Sense')) for entry in entries)
    grouped = collections.defaultdict(list)
    aliases = set()
    skipped = 0
    for entry in entries:
        lemma = entry.find('Lemma')
        word = lemmas[entry.get('id')]
        key = lookup_key(word)
        if key is None:
            skipped += 1
            continue
        pronunciations = [dict(ipa=clean(''.join(item.itertext())), variety=item.get('variety', ''))
                          for item in lemma.findall('Pronunciation') if clean(''.join(item.itertext()))]
        forms = unique(clean(item.get('writtenForm', '')) for item in entry.findall('Form'))
        senses = []
        for sense in entry.findall('Sense'):
            synset = synsets[sense.get('synset')]
            definitions = unique(clean(''.join(item.itertext())) for item in synset.findall('Definition'))
            assert definitions, sense.get('id')
            examples = unique(clean(''.join(item.itertext())) for item in synset.findall('Example'))
            synonyms = unique(lemmas[member] for member in synset.get('members', '').split()
                              if member in lemmas and lemmas[member].lower() != word.lower())
            relations = sense.findall('SenseRelation')
            antonyms = unique(sense_words.get(item.get('target'), '') for item in relations
                              if item.get('relType') == 'antonym')
            related = unique(sense_words.get(item.get('target'), '') for item in relations
                             if item.get('relType') == 'derivation')
            senses.append(dict(id=sense.get('id'), synset=sense.get('synset'), definitions=definitions,
                               examples=examples, synonyms=synonyms, antonyms=antonyms, related=related))
        if senses:
            grouped[key].append(dict(id=entry.get('id'), lemma=word,
                                     partOfSpeech=PARTS_OF_SPEECH[lemma.get('partOfSpeech')],
                                     pronunciations=pronunciations, forms=forms, senses=senses))
            for form in forms:
                form_key = lookup_key(form)
                if form_key is not None and form_key != key:
                    aliases.add((form_key, key, entry.get('id')))
    output.mkdir(parents=True, exist_ok=True)
    database = output / 'dictionary-en.sqlite'
    if database.exists():
        database.unlink()
    counts = collections.Counter()
    max_entry_bytes = 0
    with sqlite3.connect(database) as conn:
        conn.execute('PRAGMA user_version=1')
        conn.execute('CREATE TABLE entries(term TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID')
        conn.execute('CREATE TABLE forms(term TEXT NOT NULL, headword TEXT NOT NULL, entry_id TEXT NOT NULL, PRIMARY KEY(term,headword,entry_id)) WITHOUT ROWID')
        for term, records in sorted(grouped.items()):
            payload = json.dumps(records, ensure_ascii=False, separators=(',', ':'), sort_keys=True)
            max_entry_bytes = max(max_entry_bytes, len(payload.encode()))
            conn.execute('INSERT INTO entries VALUES (?,?)', (term, payload))
            counts['lexicalEntries'] += len(records)
            for record in records:
                counts['pronunciations'] += len(record['pronunciations'])
                counts['forms'] += len(record['forms'])
                counts['senses'] += len(record['senses'])
                for sense in record['senses']:
                    for field in ('definitions', 'examples', 'synonyms', 'antonyms', 'related'):
                        counts[field] += len(sense[field])
        conn.executemany('INSERT INTO forms VALUES (?,?,?)', sorted(aliases))
        conn.commit()
        assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    data = database.read_bytes()
    assert len(data) <= 128 * 1024 * 1024
    assert max_entry_bytes <= 1024 * 1024
    compressed = gzip.compress(data, compresslevel=9, mtime=0)
    (output / 'dictionary-en.sqlite.bin').write_bytes(compressed)
    manifest = dict(formatVersion=1, source='Open English Wordnet', sourceVersion='2025',
                    sourceUrl=SOURCE_URL, sourceSha256=SOURCE_SHA256, entries=len(grouped),
                    skippedLexicalEntries=skipped, formMappings=len(aliases), maxEntryBytes=max_entry_bytes, **counts,
                    databaseBytes=len(data), databaseSha256=hashlib.sha256(data).hexdigest(),
                    compressedSha256=hashlib.sha256(compressed).hexdigest())
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    args = parser.parse_args()
    build(args.source, args.output)
