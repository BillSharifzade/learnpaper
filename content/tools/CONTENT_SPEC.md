# LearnPaper content spec — writing new word entries

LearnPaper turns the phone wallpaper into a vocabulary card. Every card shows ONE word in the language the user
is learning (English, Russian or Tajik — any of the three can be the big headline word), its transcription, the
translations into the other two languages, and ONE short example sentence in all three languages, plus an
illustration. Learners are Tajik speakers learning English/Russian and Russian/English speakers learning Tajik.
So every language must be correct and natural on its own: the card is read in all directions.

Quality beats quantity. A wrong or unnatural Tajik word on someone's lock screen for an hour is worse than a
missing word. There is no native Tajik reviewer, so your checks with the tools below are the quality gate, and
every Tajik word you choose will be re-checked automatically against dictionaries and a corpus afterwards.

## Output format

Append one JSON object per line (JSONL, UTF-8, no trailing commas) to your draft file in content/cache/batches/ (merged into content/source/words.jsonl after review). Key order exactly:

{"id": "achieve", "level": "B1", "pos": "verb", "tags": ["work"], "image": "fluent:Trophy", "en": "achieve", "ipa": "əˈtʃiːv", "ru": "достига́ть", "tj": "ноил шудан", "ex": {"en": "She worked hard to achieve her goal.", "ru": "Она много работала, чтобы достичь своей цели.", "tj": "Ӯ барои ноил шудан ба мақсадаш сахт меҳнат кард."}}

Write with `python3 - <<'EOF' ... json.dumps(obj, ensure_ascii=False) ... EOF` appending to the file, or any
method that produces exactly that format. Never write outside your own draft file.

### Fields

- **id**: lowercase English headword, spaces → hyphens (`bus-stop`). If it would clash with an existing id,
  add the part of speech: `light-adj`, `cook-noun`. Must match `[a-z0-9-]+`. The validator checks clashes.
- **level**: exactly the level given for the candidate (A1, A2, B1, B2, C1, C2).
- **pos**: one of `noun verb adjective adverb numeral phrase pronoun preposition conjunction interjection`
  (almost always one of the first four).
- **tags**: 1–2 topic tags from this list only:
  `people family body health food home clothes objects animals nature weather places transport travel time
  numbers work money shopping school science technology communication society arts music sport feelings
  character mind actions qualities colors basics`
  (`actions` = generic verbs, `qualities` = generic adjectives, `mind` = thinking/knowledge,
  `character` = personality traits, `society` = law/politics/community).
- **image**: `fluent:<Folder Name>` using an EXACT folder name from the Fluent Emoji list (search with
  `emoji.py`), or `none`. Use an emoji only when it genuinely shows the word or its most typical use:
  objects, animals, food, places, weather, jobs (e.g. `Man teacher`), activities (`Person running`),
  feelings (`Smiling face with smiling eyes`), clear symbols (`Trophy` for achieve/win, `Light bulb` for idea,
  `Hourglass done` for wait, `Money bag`, `Balance scale` for law/justice). Do NOT force a vague picture
  onto an abstract word ("consequence", "therefore", "subtle"): use `none` — the card then shows a
  typographic design instead, which is much better than a misleading picture. Reusing the same emoji for
  several words is fine.
- **en**: the English headword, British spelling (colour, centre, organise is fine but -ize also OK if OALD
  lists it first). Lowercase unless it is always capitalised.
- **ipa**: British (RP) pronunciation in Oxford (OALD) style, WITHOUT slashes: `həˈləʊ`, `ˈwɔːtə(r)`,
  `ˌaɪs ˈkriːm`. Symbols: e (not ɛ), æ, ʌ, ɑː, ɒ, ɔː, ʊ, uː, ɜː, ə, i, iː, ɪ, eɪ, aɪ, ɔɪ, əʊ, aʊ, ɪə, eə, ʊə,
  θ ð ʃ ʒ tʃ dʒ ŋ j; stress marks ˈ ˌ BEFORE the stressed syllable; final linking r as `(r)` like OALD.
  If unsure, check with `ipa.py WORD` (English Wiktionary; use the RP/UK variant, convert to the house style).
- **ru**: the most common Russian equivalent for the sense you chose, in dictionary form (nouns nominative
  singular unless plural-only; verbs usually the imperfective infinitive, e.g. `достига́ть`; adjectives masculine
  nominative). Mark stress with a COMBINING ACUTE ACCENT U+0301 right after the stressed vowel on every word of
  two or more syllables (`обезья́на`, `де́тская площа́дка`); one-syllable words and words with ё get no mark.
  Check stress with `stress.py WORD`.
- **tj**: the most common modern literary Tajik equivalent (Cyrillic with Ғғ Ӣӣ Ққ Ӯӯ Ҳҳ Ҷҷ). See the Tajik rules.
- **ex**: one example sentence per language, meaning the same thing in all three.

## Choosing the sense and the words

- Pick the most common, most useful sense of the English word at that level, and make all three languages and
  the example express THAT sense. If the candidate's CEFR part of speech is given, use it.
- Every example sentence must contain the headword of its own language (inflected forms are fine: `достигла`,
  `ноил шуд`). If Russian or Tajik would naturally use a different word in that sentence, change the sentence.
- Examples are natural, everyday, neutral, positive or informative, and self-contained. No real people,
  brands, politics, religion, violence, alcohol, or dating. Avoid "I love you" style clichés.
- Length: A1–A2 up to ~50 characters per sentence; B1–B2 up to ~70; C1–C2 up to ~85. Never more than 90.
  The wallpaper shows at most three lines per sentence.
- English: British spelling; natural modern usage. Russian: natural modern Russian, NO stress marks in
  examples. Tajik: see below.
- Skip a candidate (do not write it) when: it is slang or vulgar, a function word with no clear meaning on its
  own ("just", "even", "anyway"), a proper noun, a rare technical term, offensive, or you cannot find a
  Tajik equivalent you are confident about. Also skip near-duplicates of words already in the app (the
  validator lists clashes). Prefer concrete, useful vocabulary.

## Tajik rules (most errors happen here — be careful)

- Standard literary Tajik (as in Tajik newspapers, textbooks and the Rahimi–Uspenskaya dictionary), written in
  modern orthography: proper letters ғ ӣ қ ӯ ҳ ҷ — never Russian substitutes (х/ҳ, к/қ, и/ӣ, у/ӯ, ч/ҷ, г/ғ).
- Russian loans follow the reformed spelling: no ь, no ц/щ/ы (`палто`, `мотосикл`, `питса`, `телевизор`).
  A Russian loan is fine when that is what Tajiks actually say (`телефон`, `компютер`, `автобус`, `ферма`).
- Prefer the word modern Tajik actually uses (check `tjfreq.py`/`conc.py`) over archaic or Persian-only words;
  when the corpus and the old dictionary disagree, modern usage wins (e.g. `хӯроки нисфирӯзӣ` for lunch).
  Never invent compounds; a word with 0 corpus hits AND no dictionary entry is a red flag.
- Verbs: infinitive (`кӯмак кардан`, `фаромӯш кардан`, `ноил шудан`). Adjectives: base form.
- Grammar in examples: SOV order; `-ро` on definite direct objects; izofa `-и` (`китоби ман`); possessive
  suffixes -ам/-ат/-аш/-амон/-атон/-ашон (after vowels spelled -ям/-ят/-яш… after о/у/ӯ/е, e.g. `бобоям`;
  after а: `хонаам`; after ӣ/и: `бибиам`, `-иатон`); verb agreement in person and number; present-future with
  ме-; ordinal -ин goes BEFORE the noun (`аввалин муаллим`), -ум after it (`синфи панҷум`).
- Collocations learned the hard way: aircraft/birds fly = `парвоз кардан` (not `паридан` for aircraft);
  to visit someone = `аз касе дидан кардан`; to ride = `ба дучарха/асп савор шудан`; to go to a doctor =
  `ба назди духтур рафтан`; a temporary state often uses `шудан` (`хаста шудам` = I am tired, `асабӣ мешавам`);
  "the music is loud" = `садои мусиқӣ баланд аст`; "you are right" = `шумо дуруст мегӯед`.
- Check every Tajik headword you are not 100% sure about with `dict.py tj WORD` (Tajik→Russian) and/or
  `dict.py ru СЛОВО` (Russian→Tajik) and with `tjfreq.py`/`conc.py` for real usage. For example sentences, check
  doubtful constructions with `conc.py 'phrase'`.

## Tools (content/tools/, run from the repo root)

- `python3 dict.py tj|ru|en|tjen WORD...` — sahifa.tj dictionaries (cached; the site is rate-limited, so batch
  several words per call and keep it to what you need, roughly ≤ 1 lookup per entry on average).
- `python3 conc.py 'phrase' ['phrase2'...] [-n N]` — Tajik corpus concordance (564k sentences); trailing `*` = prefix.
- `python3 tjfreq.py WORD... ` — Tajik corpus frequency of exact forms; `палто*` sums a prefix.
- `python3 stress.py СЛОВО...` — Russian stress (OpenRussian dataset).
- `python3 ipa.py WORD...` — English Wiktionary IPA (RP/UK and US lines).
- `python3 emoji.py KEYWORD...` — search Fluent Emoji folder names (exact names for `image`).
- `python3 validate.py YOUR_FILE` — schema + style checks and clashes with existing/other batches. Run it after
  every chunk of ~25 entries and fix all ERRORs; read the WARNs and fix the real problems.

## Process

1. Read your candidate list (word, CEFR part of speech, frequency rank; most useful first).
2. Work in chunks of ~25 candidates: decide sense, look up what you are unsure about, write the entries,
   run the validator, fix. Keep going until you reach your target count or run out of good candidates.
3. Finish with a short report: number of entries written, and the candidates you skipped with a 2–5 word reason
   each (one line, comma-separated). Do not paste the entries into the report.
