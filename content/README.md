# Content pipeline

Turns `source/words.jsonl` into the content pack the apps bundle.

```bash
python3 scripts/build.py           # full build → build/ and android assets
python3 scripts/build.py --check   # validate only, no network
```

## Source format

One JSON object per line:

```json
{"id":"umbrella","level":"A1","pos":"noun","tags":["objects","weather"],
 "image":"fluent:Umbrella",
 "en":"umbrella","ipa":"ʌmˈbrelə","ru":"зонт","tg":"чатр",
 "ex":{"en":"I took an umbrella because it was raining.",
       "ru":"Я взял зонт, потому что шёл дождь.",
       "tg":"Ман чатр гирифтам, зеро борон меборид."}}
```

- `id`: lowercase, digits and hyphens; unique.
- `level`: CEFR level (`A1`…`C2`). Levels follow the CEFR-J Wordlist (`source/cefrj-vocabulary-profile-1.5.csv`).
- `pos`: English key (`noun`, `verb`, `adjective`, `adverb`, `interjection`, `phrase`, …); the apps localise it.
- `ru`: mark stress with a combining acute (U+0301) after the vowel, e.g. `вода́`. The build strips it for display and puts the accent on the transliteration (`vodá`).
- `tg`: standard Tajik Cyrillic. Transliteration is generated (`ӯ→ū`, `ӣ→ī`, `ғ→gh`, `қ→q`, `ҳ→h`, `ҷ→j`, `х→kh`).
- `ipa`: English pronunciation without slashes, Oxford-style with `(r)` for non-rhotic r.
- `image`: one of
  - `fluent:<Folder>` — a folder from `source/fluent-emoji-folders.txt` (Fluent Emoji, MIT). Flat style SVG, rasterised to 512 px PNG.
  - `noto:<codepoint>` — Noto Emoji PNG (Apache 2.0), e.g. `noto:1f302`.
  - `file:<name>` — a hand-made illustration in `source/images/`.

## What the build does

1. Validates every line (fields, levels, no Latin letters in `ru`/`tg`, examples present).
2. Generates `tr` for Russian and Tajik.
3. Downloads and rasterises the illustration (cached under `cache/`).
4. Cross-checks each Tajik word against the English Wiktionary dump for Tajik (kaikki.org, downloaded to `cache/` on first run) and prints words that are missing or whose glosses do not mention the English word. Warnings only: a mismatch usually means a synonym, not an error.
5. Writes `build/words.json`, `build/images/*.png`, `build/report.txt`, and copies the pack into `../android/app/src/main/assets/content/`.

## Review process

Russian and Tajik text is drafted by a language model and must be checked by a native speaker
before release. Keep `build/report.txt` clean of new warnings you cannot explain.

## Licences

- CEFR-J Wordlist v1.5 (Tono Laboratory, TUFS): free for research and commercial use with citation.
- Fluent Emoji (Microsoft): MIT — `licenses/MIT-FluentEmoji.txt`.
- Inter (Rasmus Andersson and contributors): SIL OFL 1.1 — `licenses/OFL-Inter.txt`.
- Wiktionary data via kaikki.org: CC BY-SA 3.0 (used only to cross-check, not shipped).
