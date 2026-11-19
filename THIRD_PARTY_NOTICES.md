# Third-party notices

## Bundled English–Persian lexicon

The files in `app/src/main/assets/lexicon/` include transformed data from
[VahidN/EnglishToPersianDictionaries](https://github.com/VahidN/EnglishToPersianDictionaries),
commit `224f15d42d145c59b5dc2c6890c4736693613cc2`. The included datasets and
file hashes are listed in `app/src/main/assets/lexicon/manifest.json`.
The upstream repository publishes these files under the Apache License, Version 2.0.
The complete upstream license and a modification notice are distributed alongside
the data inside the APK as `lexicon/LICENSE.txt` and `lexicon/NOTICE.txt`.

Velvet Pines normalizes and filters English keys, cleans and merges definitions,
limits definition length, and converts the entries to a compressed SQLite index.
The build manifest records source-file checksums and output checksums.
This attribution does not assign a license to the application's own source code.

## CEFR vocabulary profiles

The learner profile assets and the SQLite learner_profiles table include
CEFR-J Wordlist 1.5 by Yukio Tono, Tono Laboratory at Tokyo University of Foreign
Studies, and Octanove Vocabulary Profile C1/C2 1.0 by Octanove Labs. They are
imported from Open Language Profiles at commit
`d4e45b75b38f27b30dfc5c44d8c571aec7e7092f`. The original terms, a modification
notice, a source manifest and the CC BY-SA 4.0 text ship in
`app/src/main/assets/learner-profiles/`.

CEFR-J permits research and commercial use with citation. Octanove and the
transformed Octanove data use CC BY-SA 4.0. Database rights held by Velvet Pines
in the combined SQLite compilation are offered under CC BY-SA 4.0. Individual
contents retain their stated source terms and notices.
The import lowercases keys, expands slash-separated variants, removes duplicate
records and omits unsupported keys. Source levels and parts of speech are retained.
[Source and supplied terms](https://github.com/openlanguageprofiles/olp-en-cefrj),
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

## English entry details

The assets in `app/src/main/assets/english-lexicon/` contain transformed data
from Open English Wordnet 2025, derived from Princeton University's WordNet
by the Open English Wordnet Community. The source is supplied under CC BY 4.0
with attribution to both Princeton WordNet and the Open English Wordnet Team.
The original license and supplied WordNet notice, a modification notice and
source/output checksums ship alongside the English index.
The import normalizes supported lookup keys, groups lexical entries and
resolves source sense references. Source definitions, examples, IPA text,
forms and sense relationships are retained. The transformed English data is
supplied under CC BY 4.0.
[Source release](https://github.com/globalwordnet/english-wordnet/releases/tag/2025-edition),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## Optional translation service

The explicit online translation action uses MyMemory's HTTPS API. The selected term
is sent to that service only after the user presses the online button. MyMemory
translations are labeled machine translations and are not included in the bundled
lexicon. Availability, quality and service quotas depend on the provider.

Other application dependencies remain subject to their respective licenses.
