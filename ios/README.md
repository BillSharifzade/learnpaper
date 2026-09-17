# LearnPaper for iOS

Swift 5.9 / SwiftUI, iOS 17+. Two targets: the app (`LearnPaper/`) and a WidgetKit extension
(`LearnPaperWidget/`), sharing everything in `Shared/`. The content pack comes from
`../content/build` (run `python3 content/scripts/build.py` from the repo root first) and the
Inter font from the Android assets, so there is one copy of each in the repository.

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

## How it works (see docs/DESIGN.md §3 and §12)

iOS gives apps no way to set the wallpaper. So:

- **Widget** (`CardWidget`): small, medium, large and Lock Screen (rectangular) families. The
  timeline for the next 24 hours is precomputed by replaying the tick schedule, so the card
  changes on the hour with no app launch and within WidgetKit's refresh budget.
- **Shortcuts** (`GetCardIntent`, "Get LearnPaper card"): returns the current card as a PNG at
  screen resolution. The user builds a shortcut `Get LearnPaper card → Set Wallpaper` and Time
  of Day automations; the in-app guide (`ShortcutsGuideView`) walks through it. `NextWordIntent`
  advances the word on demand for people who prefer a "next card" shortcut.

### The schedule

Nothing on iOS runs our code at a fixed interval, so `Schedule` derives changes from time:
ticks at `scheduleAnchor + k * interval` (the anchor is the start of the hour when onboarding
finished, so hourly changes land on the hour and match Time of Day automations). Whoever needs
the current card (app on launch, widget building its timeline, the intent) replays every tick
since `lastTick` with `Rotation.advance`, which is deterministic for a given tick time. App,
widget and wallpaper therefore always agree, with no background process. Quiet hours skip the
change but consume the tick, like the Android worker.

### Parity with Android

`Models`, `Settings`, `Progress`, `Rotation` (spaced repetition), `Stats`, `Palettes` and
`CardRenderer` are line-for-line ports of the Kotlin code in `android/`; the JSON shapes match,
including the `tj` key for Tajik. The Android unit tests in
`android/app/src/test/java/com/learnpaper/domain` describe the expected behaviour of
`Rotation` and `Stats` and are the reference for an XCTest port.

Not on iOS (yet): downloadable content packs (the bundled pack is all there is) and the
"Classic"/"Live" wallpaper choice, which has no meaning here.
