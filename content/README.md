# Content pipeline

Turns `source/words.jsonl` into the content pack the apps bundle.

```bash
python3 scripts/build.py           # full build → build/ and android assets
python3 scripts/build.py --check   # validate only, no network
python3 scripts/build.py --pack b1=B1 --pack-version 1   # also publish a downloadable pack
```

## Source format

One JSON object per line:

```json
{"id":"umbrella","level":"A1","pos":"noun","tags":["objects","weather"],
 "image":"fluent:Umbrella",
 "en":"umbrella","ipa":"ʌmˈbrelə","ru":"зонт","tj":"чатр",
 "ex":{"en":"I took an umbrella because it was raining.",
       "ru":"Я взял зонт, потому что шёл дождь.",
       "tj":"Ман чатр гирифтам, зеро борон меборид."}}
```

- `id`: lowercase, digits and hyphens; unique.
- `level`: CEFR level (`A1`…`C2`). Levels follow the CEFR-J Wordlist (`source/cefrj-vocabulary-profile-1.5.csv`).
- `pos`: English key (`noun`, `verb`, `adjective`, `adverb`, `interjection`, `phrase`, …); the apps localise it.
- `ru`: mark stress with a combining acute (U+0301) after the vowel, e.g. `вода́`. The build strips it for display and puts the accent on the transliteration (`vodá`).
- `tj`: standard Tajik Cyrillic. Transliteration is generated (`ӯ→ū`, `ӣ→ī`, `ғ→gh`, `қ→q`, `ҳ→h`, `ҷ→j`, `х→kh`).
- `ipa`: English pronunciation without slashes, Oxford-style with `(r)` for non-rhotic r.
- `image`: one of
  - `fluent:<Folder>` — a folder from `source/fluent-emoji-folders.txt` (Fluent Emoji, MIT). Flat style SVG, rasterised to 512 px PNG.
  - `noto:<codepoint>` — Noto Emoji PNG (Apache 2.0), e.g. `noto:1f302`.
  - `file:<name>` — a hand-made illustration in `source/images/`.

## What the build does

1. Validates every line (fields, levels, no Latin letters in `ru`/`tj`, examples present).
2. Generates `tr` for Russian and Tajik.
3. Downloads and rasterises the illustration (cached under `cache/`).
4. Cross-checks each Tajik word against the English Wiktionary dump for Tajik (kaikki.org, downloaded to `cache/` on first run) and prints words that are missing or whose glosses do not mention the English word. Warnings only: a mismatch usually means a synonym, not an error.
5. Writes `build/words.json`, `build/images/*.png`, `build/report.txt`, and copies the pack into `../android/app/src/main/assets/content/`.

## Downloadable packs

Everything in `source/words.jsonl` is bundled into the apps. Extra content can also be shipped
without an app update: `--pack ID=LEVELS` zips the words of those levels (`words.json` +
`images/`) into `packs/<ID>-v<N>.zip` and lists it in `packs/manifest.json`. The Android app
fetches that manifest from this repository on GitHub
(`raw.githubusercontent.com/BillSharifzade/learnpapper/main/content/packs/manifest.json`, see
`PACKS_MANIFEST_URL` in `android/app/build.gradle.kts`), installs a pack under
`files/packs/<ID>/` and merges it with the bundled words (same id → the pack wins, so packs can
also carry corrections). Bump `--pack-version` to publish an update. The manifest is empty until a
level that is not bundled exists; to try the flow locally, serve `packs/` with
`python3 -m http.server 8765` and install with
`./gradlew :app:installDebug -PpacksUrl=http://10.0.2.2:8765/manifest.json`.

## Review process

Russian and Tajik text is drafted by a language model and must be checked by a native speaker
before release. The first 101 words were reviewed on 17 Sep 2026; words added after that
(ids from `banana` onwards) are still drafts. The Wiktionary dump for Tajik is small, so
"not in Wiktionary" is expected for most everyday words; look at the gloss mismatches instead.

## Licences

- CEFR-J Wordlist v1.5 (Tono Laboratory, TUFS): free for research and commercial use with citation.
- Fluent Emoji (Microsoft): MIT — `licenses/MIT-FluentEmoji.txt`.
- Inter (Rasmus Andersson and contributors): SIL OFL 1.1 — `licenses/OFL-Inter.txt`.
- Wiktionary data via kaikki.org: CC BY-SA 3.0 (used only to cross-check, not shipped).
