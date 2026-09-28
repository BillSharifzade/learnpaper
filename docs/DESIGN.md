# LearnPaper — Design Document

Status: **v1.0** (27 Sep 2026). Android release-ready in `android/`; iOS ported in `ios/` but not compiled
yet (no Mac). Decisions are logged in §11; what changed for 1.0 is in §13.

## 1. Concept

A native Android and iOS app that turns the phone wallpaper into a vocabulary
flashcard. On a schedule (hourly by default, configurable) the wallpaper changes
to a new card: a pastel background chosen by the user, an English word with its
IPA transcription, translations into Russian and Tajik with transcriptions, one
simple example sentence in all three languages, and an illustration of the word.

Fully offline. No account, no backend, no network needed. The interface is in Tajik by default, with
Russian and English one tap away inside the app.

## 2. The card

Rendered at the device's native resolution. Lock-screen preset shown.

```
┌──────────────────────────────┐
│                              │
│        (clock zone)          │  top ~28% kept empty so the lock-screen
│                              │  clock and notifications don't cover text
│                              │
│         ┌──────────┐         │
│         │  image   │         │  illustration, ~30% width, rounded,
│         └──────────┘         │  on a slightly darker tint of the bg
│                              │
│          umbrella            │  EN word, ~72sp, bold
│          /ʌmˈbrelə/          │  IPA, ~30sp, muted
│                              │
│   зонт          [zont]       │  RU + transcription, ~36sp
│   чатр          [chatr]      │  TJ + transcription, ~36sp
│                              │
│   ────────────────────────   │
│   I took an umbrella         │  example EN, ~26sp
│   because it was raining.    │
│   Я взял зонт, потому что    │  example RU, ~24sp, muted
│   шёл дождь.                 │
│   Ман чатр гирифтам, зеро    │  example TJ, ~24sp, muted
│   борон меборид.             │
│                              │
│         (dock zone)          │  bottom ~10% kept empty
└──────────────────────────────┘
```

Layout presets (they apply to the **home screen**; the lock screen always uses
the *lower* layout so the clock zone stays clear, and when the two would be
identical one bitmap is set for both screens):

- **Lower**: content in the lower ~60%. Clock and notifications sit above.
- **Centered**: card in the middle; onboarding tells the user to keep their
  first home page sparse. **Compact** (word + translations only, in the lower
  third) is offered for busy home screens.

The **headline language** (the big word) is the language the user is learning;
the other two appear as translations. Tajik speakers learn English or Russian,
Russian and English speakers learn Tajik, so all three directions are first-class
and the layout is symmetric: every language has text + transcription (IPA for
English, Latin transliteration for Russian and Tajik, with Russian stress marked).

Every text line can be toggled in settings (hide TJ transcription, hide
examples, and so on) so the card never feels crowded. Text auto-shrinks when
example sentences run long.

## 3. Platform reality check

This section shapes everything else.

### Android — fully supported

- `WallpaperManager.setBitmap(bitmap, null, true, FLAG_SYSTEM | FLAG_LOCK)`
  sets home and lock wallpapers independently. `SET_WALLPAPER` is a normal
  permission (auto-granted). Rendering happens in memory; no storage permission.
- Scheduling via `WorkManager` `PeriodicWorkRequest`. Minimum period is 15 min.
  Survives reboot. Doze can delay a run by minutes; acceptable here.
- OEM quirks: some Xiaomi/HyperOS and Samsung One UI builds ignore or reset the
  lock-screen wallpaper set by apps. Needs real-device testing; the app offers a
  "home screen only" fallback.
- Side effect, measured on 17 Sep 2026: Android 12+ extracts a Material You seed
  colour from every new static wallpaper. The seed is the most *chromatic* colour
  in the dominant hue band, so with a pastel background it comes from the text or
  from the emoji and differs from card to card. Each new seed regenerates the
  system theme (SystemUI logs `Applying overlays` on every change), which
  recreates the launcher and every dynamic-colour app; on Xiaomi HyperOS the
  launcher restarts visibly. This is the "phone reloads its UI on every change"
  bug.
- Therefore the default delivery is a **live wallpaper** (`WallpaperService`):
  a new card is a redraw of our own surface — no wallpaper-changed broadcast, no
  colour extraction — and `onComputeColors()` reports one fixed colour set per
  palette, so the theme never regenerates. Verified on the API 36 emulator: zero
  theme events across changes, home and lock screen both served (Android 14+
  tells the engine which screen it draws for; older versions get the lock
  layout everywhere so the clock zone stays clear). The static path stays as
  "Classic" for devices whose lock screen does not show live wallpapers.

### iOS — no public API to set the wallpaper

Third-party apps cannot change the wallpaper directly. Two delivery channels:

**A. Shortcuts automation — the real wallpaper**

- The app exposes an App Intent `GetNextWordWallpaper` (App Intents framework,
  `openAppWhenRun = false`) that renders the next card in the background and
  returns it as an image file.
- We publish a shortcut via iCloud link (one tap to install):
  `Get Next Word Wallpaper → Set Wallpaper (Lock Screen + Home Screen)`.
- The user creates **Time of Day** automations in the Shortcuts app with
  **Run Immediately**. There is no "every N hours" trigger, so an hourly
  schedule means one automation per hour (8:00–22:00 = 15 automations).
  Apps cannot create or import automations, so the user does this by hand
  following our in-app step-by-step guide. This is the honest cost of iOS.
- Constraints: the active wallpaper must be in "Photo" mode (not Photo
  Shuffle); iOS 17+ for automations that run without confirmation.
- Lower-effort triggers the guide will also suggest: "When charger connected",
  "When I open [app]", "When Focus changes". Fewer steps, less regular.

**B. Widgets — reliable, zero-setup companion**

- WidgetKit Home Screen widget (medium and large) showing the full card on an
  hourly timeline. WidgetKit's refresh budget (~40–70/day) makes hourly safe.
- Lock Screen widget (rectangular) showing the word and one translation.
- Not the wallpaper, but fully under our control and it just works. Onboarding
  presents the widget as the default iOS experience and the Shortcuts path as
  an opt-in for "true wallpaper".

Decision: ship both A and B on iOS. Android can get a Glance widget cheaply
later.

Sources checked on 2026-09-16:
[MacMost — 4 ways to change iPhone wallpaper automatically](https://macmost.com/4-ways-to-make-your-iphone-wallpaper-change-automatically.html),
[How-To Geek — change wallpaper with Shortcuts](https://www.howtogeek.com/705985/how-to-automatically-change-wallpaper-on-iphone-and-ipad-using-shortcuts/),
[hourly wallpaper via 24 automations](https://5-oclock-somewhere.com/how-to-auto-change-iphone-wallpaper),
[Apple — event triggers in Shortcuts](https://support.apple.com/guide/shortcuts/event-triggers-apd932ff833f/ios),
[Android WallpaperManager reference](https://developer.android.com/reference/android/app/WallpaperManager).

## 4. Tech stack

### Recommended: native per platform, shared content

- **Android**: Kotlin, Jetpack Compose UI, WorkManager, DataStore (progress and
  settings), Canvas rendering. minSdk 26.
- **iOS**: Swift, SwiftUI, App Intents, WidgetKit, `ImageRenderer` to turn the
  SwiftUI card view into a UIImage. Min iOS 17.
- **Shared**: `content/` (word dataset JSON, images, palette definitions, layout
  spec) bundled into both apps as assets.

Why native instead of Flutter / React Native / KMP:

- The heart of this app is platform integration: WorkManager + WallpaperManager
  on Android, App Intents + WidgetKit on iOS. The iOS pieces must be Swift and
  run in extensions where Flutter/RN engines do not fit.
- The UI is small (onboarding, today's card, history, settings). Two thin UIs
  cost less than fighting a cross-platform layer at the extension boundary.
- The card is one view, written twice from one spec. Cheap.

### Alternatives, if you want one language

- **Flutter**: pre-render a queue of PNGs while the app is open; the Android
  worker and iOS intent just pick the next file. Workable, but the iOS widget
  and intent are still Swift, and pre-rendering means a palette change applies
  only after the app is reopened.
- **Kotlin Multiplatform (+ Compose Multiplatform)**: share model, dataset
  loading, and rotation logic; still Swift for App Intents and WidgetKit.
  Sensible if you are Kotlin-first.

## 5. Content

### Dataset

- Levels come from the **CEFR-J Wordlist v1.5** (7,800 English headwords tagged
  A1–B2; free for commercial use with citation). There is no ready-made
  English–Russian–Tajik CEFR-tagged dictionary, so translations are drafted by a
  language model and **cross-checked against the English Wiktionary Tajik dump**
  (kaikki.org, 4,541 entries) by the build script, then reviewed by a person.
  The first pass: **101 A1 words, all with illustrations**, 95 of 101 Tajik
  words confirmed by Wiktionary glosses (the rest are synonyms or missing there).
  Target: 500 words A1–A2, then B1.
- One record per word:

```json
{
  "id": "umbrella",
  "level": "A1",
  "tags": ["objects", "weather"],
  "en": { "text": "umbrella", "ipa": "ʌmˈbrelə", "pos": "noun" },
  "ru": { "text": "зонт", "tr": "zont" },
  "tj": { "text": "чатр", "tr": "chatr" },
  "example": {
    "en": "I took an umbrella because it was raining.",
    "ru": "Я взял зонт, потому что шёл дождь.",
    "tj": "Ман чатр гирифтам, зеро борон меборид."
  },
  "image": "umbrella.webp"
}
```

- Transcription systems: IPA for English. For Russian and Tajik, Latin
  transliteration (more readable than IPA for non-linguists). Whether RU/TJ
  transcriptions are needed at all depends on who the learner is (open
  decision 3).

### Images

- **v1 (decided)**: Microsoft's Fluent Emoji, flat style, MIT licence. 1,595
  consistent illustrations, predictable to fetch, rasterised to 512 px PNG at
  build time (~16 KB each). Zero cost, offline, one visual style, and it covers
  almost all concrete A1–B1 vocabulary. Each word names its emoji explicitly in
  the source file, so the pick is curated, not automatic.
- **Later**: per-word hand-made or AI-generated illustrations in one pastel
  style can replace any emoji via `image: file:<name>` without touching the app.
- **Rejected**: runtime photo APIs (network, inconsistent look, attribution)
  and runtime AI generation (cost, latency, needs a backend).

### Pipeline (`content/`, Python scripts)

1. `words.csv` — source list with level and tags
2. LLM draft of translations, transliterations, example sentences
3. Human review, especially Tajik (LLM quality for Tajik is markedly weaker
   than for Russian)
4. Image generation and review
5. Validation: every field present, IPA charset, sentence length limits, image
   exists, no duplicate ids
6. Build `words.json` + `images/`, copy into
   `android/app/src/main/assets/content/` and `ios/LearnPaper/Content/`

Content packs are versioned so the apps can later fetch updates from a static
CDN without an app release.

## 6. Visual design

- **Palettes**: ~10 curated pastels (peach, mint, lavender, sky, sand, rose,
  sage, butter, lilac, powder). Each defines background, primary text, muted
  text, and image-tile tint, checked for WCAG AA contrast (4.5:1 on body text).
  User picks one fixed palette or "rotate per word". Dimmed variants for quiet
  hours are optional.
- **Typography**: one family covering Latin, IPA, Russian Cyrillic, and Tajik
  Cyrillic (Ғғ Ӣӣ Ққ Ӯӯ Ҳҳ Ҷҷ). Noto Sans is the safe choice; Inter is nicer
  and probably covers it — to be verified in the spike. The font is bundled in
  both apps so rendering matches.
- **Rendering**: native display size with safe insets; illustration in a soft
  rounded tile; long examples shrink rather than wrap past two lines each.

## 7. Scheduling and rotation

- **Intervals**: 15 min, 30 min, 1 h (default), 2 h, 3 h, 6 h, 12 h, daily.
  On iOS the wallpaper interval is whatever automations the user created; the
  in-app setting drives the widget timeline and the setup guide.
- **Quiet hours** (default 23:00–07:00): no changes. Saves battery and words.
- **Order**: sequential through the chosen level(s), shuffled once per level.
  "Mark as learned" removes a word from rotation; "favorite" keeps it around.
  Later: every 5th card is a recently shown word (light review).
- **Manual controls**: Next word, Preview, Apply now.
- **History**: every word shown, tap for the full card and pronunciation via
  the platform TTS engine (offline, free; EN and RU available on both
  platforms, Tajik TTS is not available on-device and is omitted).

## 8. Scope

### v1.0 (MVP)

- Onboarding: palette, level, layout, interval, quiet hours, then
  platform-specific setup (Android: one tap; iOS: add widget, optional
  Shortcuts guide).
- Automatic rotation as described per platform.
- Today's card, Next word, History, Learned and Favorites.
- 500 words A1–A2 with images.
- Settings, including per-line visibility toggles.
- UI localized in EN, RU, TJ.

### v1.x — done on Android on 17 Sep 2026

- Content packs downloaded from the repository on GitHub (`content/packs/manifest.json`);
  the manifest is empty until a level that is not bundled exists.
- Light spaced repetition (`Rotation`: due words first, intervals 1/3/7/14/30/60 days).
- Home screen widget; live-wallpaper mode (now the default, see §3).
- Daily "word of the day" notification.
- Stats: streak, words seen, learned, due today.

### Out of scope for now

Accounts, sync, social, monetization, any backend.

## 9. Risks — what the spike must prove before real code

1. **iOS**: App Intent returning an image + "Set Wallpaper" works end-to-end
   from a Time of Day automation while the phone is locked, on iOS 17.
2. **iOS**: the WidgetKit hourly timeline actually refreshes about hourly over
   a full day.
3. **Android**: WorkManager hourly job + `FLAG_LOCK` behaves on Pixel, Samsung,
   Xiaomi. Document per-OEM behavior.
4. **Font**: chosen family renders every Tajik letter and IPA symbol.
5. **Content**: LLM draft quality for Tajik on a 30-word sample, reviewed by a
   Tajik speaker.

Spike output: a pass/fail checklist per device with screenshots. 2–3 days.

## 10. Repository layout

```
learnpaper/
  docs/       design docs, iOS Shortcuts setup guide, OEM test matrix
  content/    source word list, pipeline scripts, built bundle
  android/    Kotlin / Compose app
  ios/        Swift / SwiftUI app, widget extension, intents
```

## 11. Decisions (16 Sep 2026)

1. **Stack**: native. Kotlin + Jetpack Compose on Android, Swift + SwiftUI on iOS.
2. **Audience**: Tajik speakers learning Russian and English, and the reverse.
   The headline language is a setting; all three languages carry transcriptions.
3. **Images**: Fluent Emoji (MIT) for v1, replaceable per word later.
4. **Grades**: CEFR levels via the CEFR-J Wordlist; the user picks one or more.
5. **Vocabulary**: seeded from classic A1 vocabulary, cross-checked with
   Wiktionary, hand-reviewed. Grows towards 500 A1–A2 words.
6. **Order**: Android first (done as MVP), then content growth, then iOS.
8. **Naming (17 Sep 2026)**: Tajik is shown as **TJ** everywhere the user can see
   it (card labels, content key `tj`, `Lang.TJ`); only the platform locale
   identifier keeps the ISO code `tg`, because that is how the system selects
   the Tajik translation of the UI.
9. **Android delivery (17 Sep 2026)**: live wallpaper by default, static as
   "Classic". Reason in §3.
10. **iOS (17 Sep 2026)**: the Shortcuts + widget approach is accepted and
    implemented in `ios/` (Swift, XcodeGen spec); it has not been compiled yet
    because no Mac was available. The card changes on a tick schedule that every
    party replays deterministically (see `ios/README.md`).
11. **Content (17 Sep 2026)**: 507 words (388 A1, 119 A2). The first 101 were
    reviewed by a Tajik speaker; the rest are model drafts awaiting review.
7. **Repo**: monorepo as in section 10.

12. **Tajik review (26 Sep 2026)**: no Tajik reviewer is available; words 102–507 were checked against
    online dictionaries and the corpus instead (65 fixes), and the same evidence-based process was used
    for every new word (§13). Real-device tests of the Android app were reported good by the owner.
13. **Version 1.0 (27 Sep 2026)**: Tajik-default interface with in-app RU/EN switch, new brand, redesigned
    UI, all CEFR levels bundled, word library with search, release signing — see §13.

Still open:

- **iOS build**: generate the project with XcodeGen on a Mac, fix whatever the
  compiler flags, verify the widget timeline and the Set Wallpaper action on
  iOS 17/18.

## 13. Version 1.0 (27 Sep 2026)

**Interface language.** Tajik is the default interface language regardless of the phone's language;
Russian and English are switchable in onboarding and settings (Android 13+ also shows it in the system's
per-app language screen). Tajik UI terminology follows GNOME's professional Tajik translation
(wallpaper = "тасвири экран", lock screen = "экрани қулф", notifications = "огоҳиҳо") and was reviewed
against dictionaries and the corpus. The Play bundle ships all three translations (no language splits).

**Brand.** Two stacked flashcards with a bold "ā" (the macron of dictionary pronunciations and of the
Tajik Ӣ/Ӯ). Iris violet + Apricot on warm Paper, Ink text; Onest as the brand typeface (full Tajik
coverage), Inter for transcriptions (full IPA). Everything is generated from `branding/make_brand.py`;
see `branding/README.md`.

**App structure.** Onboarding (6 steps: welcome + interface language, languages, levels, look, rhythm,
ready) → three tabs: *Today* (live card preview, the word with pronunciation, translations and example,
favourite / "I know it" / next word, stats), *Words* (the whole library: search in any language that
tolerates missing Tajik letters and Latin transliteration, filters for seen / favourites / learned,
level filters, detail sheet with "Show on wallpaper"), *Settings* (grouped cards). Motion: spring
press feedback, sliding segmented controls, cross-fading previews, animated word changes and counters.
Dark theme throughout; two dark wallpaper palettes (Midnight, Pine) join the ten pastels.

**Card.** Onest headword and translations, Inter transcriptions, a soft gradient background; abstract
words without an illustration get a drop-cap tile instead of a forced picture.

**Content.** All six CEFR levels, bundled offline (no download needed): 2,433 words — A1 490, A2 382,
B1 517, B2 538, C1 292, C2 214; 1,131 illustrated (637 shared images), the rest abstract words with a
drop-cap card. New words were drawn from the
CEFR-J Wordlist (A1–B2) and the Octanove C1/C2 profile by frequency, written to a strict spec
(`content/tools/CONTENT_SPEC.md`), checked automatically (dictionary + Wiktionary confirmation of every
Tajik word, Russian stress against OpenRussian, IPA against Wiktionary, corpus attestation of every
example word) and reviewed a second time (`REVIEW_SPEC.md`); anything that could not be confirmed was
fixed or dropped. Images are lossless WebP shared between words (≈70% smaller than PNG). Tools:
`content/tools/`.

**Lock screen.** Stock Android on Pixels draws a large centred clock when there are no notifications,
which covered the card; the lock screen now uses a compact layout under that clock ("Lock screen clock:
Auto / Top / Middle", Auto = large clock on Google phones), while Xiaomi/Samsung-style small top clocks keep
the full card.

**Today** also supports swiping the card for the next word, tapping it for a full-size view, and a
seven-day streak strip. **Widget** gained a next-word button; **releases** are signed with an upload key kept out of git
(`docs/RELEASE.md`).

## 12. iOS, explained plainly

On Android an app may call "set this image as the wallpaper" and it happens.
On iOS Apple gives apps no such call at all; only the user can change the
wallpaper, through Settings or the Shortcuts app. So no iOS app, ours or anyone
else's, can change the wallpaper in the background by itself.

The one door Apple leaves open is Shortcuts. The user installs a small shortcut
we publish. The shortcut asks our app for the next card image and hands it to
Apple's own "Set Wallpaper" action. Then the user tells the Shortcuts app *when*
to run it: "every day at 09:00", "every day at 10:00", and so on. Each time is a
separate automation the user creates by hand, and Apple does not let apps create
them. That is why hourly on iOS means one automation per hour, set up once.

Because that setup is tedious, the iOS app also ships widgets. A Home Screen
widget shows the same card and refreshes itself hourly with no setup at all. It
is not the wallpaper, but it is reliable, and it is what most iOS users will
actually use. Both paths run from the same Swift rendering code.
