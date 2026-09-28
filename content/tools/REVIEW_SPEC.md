# Review spec — second-pass review of new LearnPaper word entries

You are the quality gate for vocabulary entries written by another agent (spec they followed:
content/tools/CONTENT_SPEC.md — read it first).
There is no native Tajik reviewer. Every card will sit on someone's lock screen, so each language must be
correct and natural on its own (the card can be read EN→RU/TJ, RU→EN/TJ or TJ→EN/RU).

## Inputs per batch

- The batch file `content/cache/batches/<name>.jsonl` — you FIX IT IN PLACE (keep one JSON object per line, same key order).
- Automated evidence `content/cache/tools/verify/<name>.json` (same entries, same order): for each entry
  `sources` = which independent sources confirm the Tajik headword (ru→tj = sahifa Russian→Tajik dictionary
  lists it for the Russian headword; tj→ru = sahifa Tajik→Russian gives the Russian headword; enwikt/ruwikt =
  Wiktionary translation tables), `dict_tj_ru` / `dict_ru_tj` / `dict_parts` = the dictionary entries,
  `tj_freq` = corpus frequency of each Tajik headword token, `rare_ex_tokens` = example tokens seen < 2 times
  in the corpus, `ru_stress` = stress differs from the OpenRussian dataset, `ipa_ok`/`ipa_wikt` = Wiktionary
  RP IPA (IGNORE notation differences: ̯ ◌͡◌ ̩, ɹ vs r, ɛ vs e, (ə), syllabic n/l vs ən/əl, (t) — only real
  errors like a wrong or missing consonant/vowel or wrong stress syllable matter).

## What to check for EVERY entry

1. Tajik headword — standard, modern, the natural equivalent for THIS sense. Entries with 0–1 `sources`
   need a real decision: read the dictionary snippets; use `dict.py`, `tjfreq.py`, `conc.py`; a word that
   is neither in the dictionary nor attested in the corpus is almost certainly wrong. Prefer modern usage
   when the old dictionary and the corpus disagree.
2. Tajik example — grammar (izofa, -ро, agreement, possessive spelling, verb forms), natural collocations,
   same meaning as the English, contains the headword (inflected OK). Check doubtful constructions with
   `conc.py`. Common model errors: calques from Russian/English, wrong light verb (кардан/шудан/задан),
   "парид" for aircraft, "дошт" for "wore/caught", ordinal placement, "ҳастам" for temporary states.
3. Russian headword (correct stress mark U+0301, natural equivalent, dictionary form) and Russian example
   (natural, contains the headword, no stress marks).
4. English example (natural, British spelling) and IPA (see above).
5. Image: `none` or a Fluent emoji that genuinely shows the word; replace misleading pictures with `none`.
6. Duplicates: if another entry in the app or any batch already has the SAME Russian AND Tajik headword
   (check with `find.py`), change one translation to a more precise one or delete the weaker entry.

If an entry cannot be made clearly correct, DELETE it (a missing word is better than a wrong one).

## Tools (content/tools/)

`dict.py tj|ru WORD…`, `conc.py 'phrase' [-n N]`, `tjfreq.py WORD…`, `stress.py СЛОВО…`, `ipa.py WORD…`,
`emoji.py KEYWORD…`, `find.py WORD…`, `validate.py FILE`. The dictionary site is rate-limited and shared with
other agents — batch several words per call and look up only what you need.

## Output

- Edit the batch file(s) in place; run `validate.py` on each until it reports 0 errors.
- Log EVERY change to `content/cache/reviews/<name>.changes.jsonl`, one JSON object per line:
  `{"id": "...", "field": "tj|ex.tj|ru|ex.ru|en|ex.en|ipa|image|deleted", "old": "...", "new": "...", "reason": "short", "evidence": "tool output that supports it"}`
- Final answer: per batch — entries reviewed, changed, deleted; then the 5 most important problems you found
  (one line each). No other prose.
