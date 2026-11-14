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

## Optional translation service

The explicit online translation action uses MyMemory's HTTPS API. The selected term
is sent to that service only after the user presses the online button. MyMemory
translations are labeled machine translations and are not included in the bundled
lexicon. Availability, quality and service quotas depend on the provider.

Other application dependencies remain subject to their respective licenses.
