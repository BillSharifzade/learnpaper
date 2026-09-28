# LearnPaper for iOS

Swift 5.9 / SwiftUI, iOS 17+. Two targets: the app (`LearnPaper/`) and a WidgetKit extension
(`LearnPaperWidget/`), sharing everything in `Shared/`. The content pack comes from
`../content/build` (run `python3 content/scripts/build.py` from the repo root first) and the two
fonts (Onest, Inter) from the Android assets, so there is one copy of each in the repository.

This is the 1.0 redesign, ported from the Android app (`android/`), which is the reference for
behaviour, visuals and every interface text.

## Build (needs a Mac with Xcode 15.4+)

```bash
brew install xcodegen
python3 ../content/scripts/build.py --no-android    # words.json + images, if not built yet
xcodegen generate
open LearnPaper.xcodeproj
```

Then in Xcode: pick your team for both targets (or set `DEVELOPMENT_TEAM` in `project.yml`),
enable the App Group `group.com.learnpaper` for `com.learnpaper.app` and
`com.learnpaper.app.widget` in your developer account, and run on a device or the simulator.

This code was written without access to a Mac, so the first build on Xcode may need small
fixes (typically API availability or a missing import). The design and the logic are settled;
please treat compiler errors as typos, not as design questions.

### First-build checklist

1. `xcodegen generate` (a current release: older ones did not know `.xcstrings` and copy the catalog
   as a plain file) runs without warnings about missing paths (`../content/build/words.json`,
   `../content/build/images`, both fonts under `../android/app/src/main/assets/fonts/`).
2. Project → Info → Localizations lists English, Russian and Tajik (the `en/ru/tg.lproj` folders in
   `Shared/Resources` make XcodeGen add them). After a build, the app bundle must contain
   `tg.lproj/Localizable.strings` and `Localizable.stringsdict`; otherwise the interface falls back
   to English.
3. Both targets build; the widget extension compiles `Shared/` too (including `NextWordIntent`).
4. First launch shows onboarding in Tajik whatever the system language; switching to Русский or
   English on the first step re-labels everything at once, and the choice survives a relaunch.
5. The fonts are Onest (headings, words) and Inter (transcriptions). If text looks like San
   Francisco, the fonts were not registered: check `UIAppFonts` in both Info.plists and that the
   `.ttf` files are in "Copy Bundle Resources".
6. The brand mark (Today top bar, About, empty lists) renders from `Assets.xcassets/BrandMark`.
7. Widgets: small, medium, large and Lock Screen show the current card in the palette; the arrow
   on medium/large advances the word (interactive widget, iOS 17).
8. Shortcuts: "Get LearnPaper card" returns a PNG; "Next LearnPaper word" advances.

## What changed in 1.0 (from the Android redesign)

- **Brand**: Iris/Apricot/Paper/Ink colours with light and dark values (`Shared/Theme.swift`),
  Onest + Inter variable fonts at exact weights (`Shared/Fonts.swift`, CoreText `wght` axis),
  the Android type scale, the app icon, `BrandMark` and `AccentColor` assets, a launch colour.
- **Interface language**: Tajik by default, Russian and English one tap away (see below).
- **Screens**: onboarding in six steps (welcome with the language switch, learned language, level
  cards A1–C2, look with palettes and layouts, rhythm, ready with the iOS routes); Today (brand bar
  with streak, swipeable floating card with a full-size view, schedule chip, word panel with
  listen buttons, favourite / "I know it" / "Next word", stat tiles, the week strip); Words (search
  in any language, filters, level chips, sticky level headers, a detail sheet with "Show on
  wallpaper"); Settings as grouped cards, with licences. Springs, numeric text transitions,
  press-scale buttons with haptics.
- **Card**: Onest headword, Inter transcriptions, gradient background with blurred blobs, a drop-cap
  tile for words without an illustration, two dark palettes (Midnight, Pine).
- **Widget**: the Android widget look (lightened palette background, illustration or drop-cap
  tile, word, transcription, "RU слово   TJ калима", example) and a "next word" button.
- **Content**: words are indexed by id, rotation replays use a prebuilt index, images are decoded
  once (WebP, NSCache) and thumbnails through ImageIO.

## How the language switch works

`AppLanguage` (`Shared/L10n.swift`) mirrors Android's `AppLocale`: `tj` ("tg", Тоҷикӣ), `ru`, `en`,
default `tj` regardless of the system language. The choice is stored in the App Group defaults
(`appLanguage`), so the widget and the intents use it too.

`Localizable.xcstrings` is the only source of texts (keys are the Android resource names; iOS-only
texts start with `ios_`). Xcode compiles it into `en/ru/tg.lproj`. `L10n.t(key)` looks the key up
in the bundle of the chosen language (`Bundle.main.path(forResource: "tg", ofType: "lproj")`),
falling back to English; `L10n.f(key, args…)` formats with that language's locale, which also picks
the plural form (Russian one/few/many/other). The root view is keyed by the language and gets
`.environment(\.locale, …)`, so switching rebuilds every screen with the new texts at once, with
dates and numbers following — no restart. Onboarding keeps its step and draft in `AppModel`, so
nothing is lost when the language changes on the first step. The widget re-reads the language
before each timeline, and the app reloads widget timelines when it changes.

To update texts: edit the Android `strings.xml` files (shared texts) or the `IOS` table in
`tools/sync_strings.py` (iOS-only texts), then run `python3 ios/tools/sync_strings.py` from the repo
root. It rewrites `Shared/Resources/Localizable.xcstrings` with the keys listed in the script; a key
the Swift code uses (`L10n.t("…")`) must be listed there.

## How it works (see docs/DESIGN.md §3 and §12)

iOS gives apps no way to set the wallpaper. So:

- **Widget** (`CardWidget`): small, medium, large and Lock Screen (rectangular) families. The
  timeline for the next 24 hours is precomputed by replaying the tick schedule, so the card
  changes on the hour with no app launch and within WidgetKit's refresh budget.
- **Shortcuts** (`GetCardIntent`, "Get LearnPaper card"): returns the current card as a PNG at
  screen resolution. The user builds a shortcut `Get LearnPaper card → Set Wallpaper` and Time
  of Day automations; the in-app guide (`ShortcutsGuideView`) walks through it. `NextWordIntent`
  advances the word on demand (also the widget's arrow button).
- "Show on wallpaper" in the Words library makes the word current (`Rotation.show`): widgets show
  it at once, the wallpaper the next time the user's shortcut runs.

### The schedule

Nothing on iOS runs our code at a fixed interval, so `Schedule` derives changes from time:
ticks at `scheduleAnchor + k * interval` (the anchor is the start of the hour when onboarding
finished, so hourly changes land on the hour and match Time of Day automations). Whoever needs
the current card (app on launch, widget building its timeline, the intent) replays every tick
since `lastTick` with `Rotation.advance`, which is deterministic for a given tick time. App,
widget and wallpaper therefore always agree, with no background process. Quiet hours skip the
change but consume the tick, like the Android worker. While the app is open it catches up at the
next tick by itself.

### Parity with Android

`Models`, `Settings`, `Progress`, `Rotation` (spaced repetition, `show`), `Stats`, `Palettes`,
`WordSearch` and `CardRenderer` are ports of the Kotlin code in `android/`; the JSON shapes match,
including the `tj` key for Tajik. The Android unit tests in
`android/app/src/test/java/com/learnpaper/domain` describe the expected behaviour of `Rotation`,
`Stats` and `WordSearch` and are the reference for an XCTest port.

Not on iOS: downloadable content packs (the bundled pack is all there is), the Live/Classic
wallpaper choice and the lock-screen clock style (the iOS clock is always at the top, so the lock
layout always keeps clear of it).
