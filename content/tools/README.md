# Content tools

Everything used to write and check LearnPaper's ~2,500 trilingual word entries. There is no native
Tajik reviewer on the project, so Tajik is checked against independent sources: the Rahimi–Uspenskaya
Tajik–Russian dictionary and a Russian–Tajik one (sahifa.tj), English and Russian Wiktionary translation
tables, and real usage in a 564k-sentence Tajik corpus (Leipzig). Interface wording follows GNOME's
professional Tajik translation.

Setup once (downloads ~100 MB, builds ~250 MB into `content/cache/tools/`, git-ignored):

```bash
python3 content/tools/setup.py
```

| Tool | What it answers |
|---|---|
| `dict.py tj\|ru\|en\|tjen WORD…` | What does the dictionary say? (sahifa.tj; cached, rate-limited to ~1 request/s) |
| `conc.py 'phrase' [-n N]` | Is this phrase real Tajik? Count + example sentences from the corpus (`word*` = prefix) |
| `tjfreq.py WORD…` | How common is this exact word form? |
| `stress.py СЛОВО…` | Where is the Russian stress? (OpenRussian) |
| `ipa.py WORD…` | British IPA from English Wiktionary |
| `emoji.py KEYWORD…` | Exact Fluent Emoji folder names for `image` |
| `find.py WORD…` | Is this English/Russian/Tajik headword already used? |
| `l10n.py 'english term'…` | How GNOME's Tajik translation words an interface term |
| `validate.py FILE` | Style/schema check of a JSONL file (stress marks, Tajik orthography, lengths, clashes) |
| `verify.py FILE…` | Automated evidence per entry: dictionary/Wiktionary confirmation of the Tajik word, IPA, stress, rare tokens |

How new words were made (Sep 2026): candidates per level from the CEFR-J Wordlist (A1–B2) and the Octanove
C1/C2 profile, ranked by frequency; drafts written to `CONTENT_SPEC.md`; `verify.py` evidence; a second-pass
review following `REVIEW_SPEC.md` that fixed or deleted what could not be confirmed; then merged into
`content/source/words.jsonl` and built with `content/scripts/build.py`.

Drafts that are not merged yet can live in `content/cache/batches/*.jsonl` (or `$LP_BATCHES`); `find.py`
and `validate.py` include them in their duplicate checks.
