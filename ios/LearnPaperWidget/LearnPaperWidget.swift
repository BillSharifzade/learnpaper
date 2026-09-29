import AppIntents
import SwiftUI
import UIKit
import WidgetKit

@main
struct LearnPaperWidgetBundle: WidgetBundle {
    var body: some Widget {
        CardWidget()
    }
}

struct CardEntry: TimelineEntry {
    let date: Date
    let word: Word?
    let settings: Settings
    let palette: Palette
}

/// Precomputes the cards ahead by replaying the tick schedule (see `Schedule`), so the widget changes
/// on time without the app running. One timeline covers up to 24 hours or `maxEntries` changes; with
/// short intervals (down to one minute) it asks for the next timeline when its entries run out, which
/// stays well inside WidgetKit's daily refresh budget.
struct CardProvider: TimelineProvider {
    /// Changes precomputed per timeline: an hour of one-minute words, or a whole day of hourly ones.
    static let maxEntries = 60

    func placeholder(in context: Context) -> CardEntry {
        let settings = Settings()
        return CardEntry(date: Date(), word: ContentStore.shared.words.first, settings: settings, palette: Palettes.byId(settings.paletteId))
    }

    func getSnapshot(in context: Context, completion: @escaping (CardEntry) -> Void) {
        Fonts.register()
        L10n.reload()
        let store = Store.shared
        let settings = store.settings
        let progress = store.catchUp()
        completion(entry(at: Date(), progress: progress, settings: settings))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CardEntry>) -> Void) {
        Fonts.register()
        // The user may have switched the interface language in the app since the last timeline.
        L10n.reload()
        let store = Store.shared
        let settings = store.settings
        let index = ContentStore.shared.index
        let progress = store.catchUp()
        let now = Date()
        var entries = [entry(at: now, progress: progress, settings: settings)]

        var p = progress
        let horizon = now.millis + 24 * 3_600_000
        let ticks = Schedule.ticks(progress: progress, settings: settings, until: horizon)
        for t in ticks.prefix(Self.maxEntries) {
            p.lastTick = t
            if settings.isQuiet(at: Date(millis: t)) { continue }
            p = Rotation.advance(p, settings: settings, index: index, now: t, tzOffsetMs: Schedule.tzOffset(at: t))
            entries.append(entry(at: Date(millis: t), progress: p, settings: settings))
        }
        // Short intervals use up the entries before the day is over: reload right after the last one.
        let covered = ticks.count <= Self.maxEntries
        let last = entries.last?.date ?? now
        let refresh = covered ? max(last, now.addingTimeInterval(3600)) : max(last, now.addingTimeInterval(300))
        completion(Timeline(entries: entries, policy: .after(refresh)))
    }

    private func entry(at date: Date, progress: Progress, settings: Settings) -> CardEntry {
        let word = progress.currentId.flatMap { ContentStore.shared.word($0) }
            ?? ContentStore.shared.words.first { settings.levels.contains($0.level) }
        return CardEntry(date: date, word: word, settings: settings, palette: Palettes.forSettings(settings, index: progress.paletteIndex))
    }
}

struct CardWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.learnpaper.card", provider: CardProvider()) { entry in
            CardWidgetView(entry: entry)
        }
        .configurationDisplayName(L10n.t("widget_name"))
        .description(L10n.t("widget_description"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

/// The card in the wallpaper's palette: illustration tile (or the drop-cap letter), word,
/// transcription, "RU слово   TJ калима" and the example, like the Android widget.
struct CardWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CardEntry

    private var p: Palette { entry.palette }
    private var s: Settings { entry.settings }
    private var ink: Color { Color(uiColor: p.text) }
    private var muted: Color { Color(uiColor: p.muted) }
    private var tileColor: Color { Color(uiColor: p.tile) }

    /// A little lighter than the wallpaper, so the widget reads as a card on top of it.
    private var background: Color {
        Color(uiColor: Palette.mix(p.bgHex, 0xFFFFFF, p.isDark ? 0.08 : 0.5))
    }

    var body: some View {
        Group {
            if let word = entry.word {
                switch family {
                case .accessoryRectangular: lockScreen(word)
                case .systemSmall: small(word)
                case .systemMedium: medium(word)
                default: large(word)
                }
            } else {
                empty
            }
        }
        .containerBackground(for: .widget) {
            if family == .accessoryRectangular { Color.clear } else { background }
        }
    }

    // MARK: - families

    private func lockScreen(_ word: Word) -> some View {
        let headline = word.entry(s.headline)
        return VStack(alignment: .leading, spacing: 1) {
            Text(headline.text)
                .font(.onest(17, 700))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if s.showTranscriptions, !headline.tr.isEmpty {
                Text(L10n.transcription(s.headline, headline.tr))
                    .font(.inter(12))
                    .lineLimit(1)
            }
            if let lang = s.translations.first {
                Text(verbatim: "\(lang.label) \(word.entry(lang).text)")
                    .font(.onest(13, 500))
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func small(_ word: Word) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            tile(word, size: 50)
            Spacer(minLength: 0)
            Text(word.entry(s.headline).text)
                .font(.onest(20, 700))
                .foregroundStyle(ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let lang = s.translations.first {
                Text(word.entry(lang).text)
                    .font(.onest(14, 500))
                    .foregroundStyle(muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    private func medium(_ word: Word) -> some View {
        let headline = word.entry(s.headline)
        let example = s.showExamples ? word.example.of(s.headline) : ""
        return HStack(alignment: .center, spacing: 14) {
            tile(word, size: 72)
            VStack(alignment: .leading, spacing: 0) {
                Text(headline.text)
                    .font(.onest(24, 700))
                    .tracking(-0.24)
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                if s.showTranscriptions, !headline.tr.isEmpty {
                    Text(L10n.transcription(s.headline, headline.tr))
                        .font(.inter(13))
                        .foregroundStyle(muted)
                        .lineLimit(1)
                }
                if !s.translations.isEmpty {
                    translationsLine(word)
                        .font(.onest(15, 600))
                        .lineLimit(2)
                        .padding(.top, 6)
                }
                if !example.isEmpty {
                    Text(example)
                        .font(.onest(12))
                        .foregroundStyle(muted)
                        .lineLimit(2)
                        .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            nextButton
                .frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private func large(_ word: Word) -> some View {
        let headline = word.entry(s.headline)
        let tag = [word.level, PosNames.localized(word.pos)].filter { !$0.isEmpty }.joined(separator: "  ·  ").uppercased()
        let example = s.showExamples ? word.example.of(s.headline) : ""
        return VStack(spacing: 8) {
            tile(word, size: 92)
            Text(headline.text)
                .font(.onest(34, 700))
                .tracking(-0.5)
                .foregroundStyle(ink)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.top, 4)
            if s.showTranscriptions, !headline.tr.isEmpty {
                Text(L10n.transcription(s.headline, headline.tr))
                    .font(.inter(15))
                    .foregroundStyle(muted)
                    .lineLimit(1)
            }
            Text(tag)
                .font(.onest(11, 600))
                .tracking(1.3)
                .foregroundStyle(muted)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Capsule().fill(tileColor))
            ForEach(s.translations) { lang in
                let entry = word.entry(lang)
                HStack(spacing: 8) {
                    Text(lang.label)
                        .font(.onest(11, 700))
                        .foregroundStyle(muted)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(tileColor))
                    Text(entry.text)
                        .font(.onest(19, 600))
                        .foregroundStyle(ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    if s.showTranscriptions, !entry.tr.isEmpty {
                        Text(L10n.transcription(lang, entry.tr))
                            .font(.inter(13))
                            .foregroundStyle(muted)
                            .lineLimit(1)
                    }
                }
            }
            if !example.isEmpty {
                Rectangle()
                    .fill(muted.opacity(0.35))
                    .frame(width: 110, height: 1)
                    .padding(.vertical, 4)
                Text(example)
                    .font(.onest(14, 500))
                    .foregroundStyle(ink)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                ForEach(s.translations) { lang in
                    let translated = word.example.of(lang)
                    if !translated.isEmpty {
                        Text(translated)
                            .font(.onest(12))
                            .foregroundStyle(muted)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .topTrailing) { nextButton }
    }

    /// Before the first word (or when no level has words): the drop-cap tile and the hint.
    private var empty: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous).fill(tileColor)
                Text(verbatim: "ā")
                    .font(.onest(32, 800))
                    .foregroundStyle(Color(uiColor: p.accent))
            }
            .frame(width: 72, height: 72)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: "LearnPaper")
                    .font(.onest(20, 800))
                    .foregroundStyle(ink)
                Text(L10n.t("home_no_word"))
                    .font(.onest(13))
                    .foregroundStyle(muted)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - parts

    /// Rounded tile with the illustration, or the first letter of the word in the accent colour.
    private func tile(_ word: Word, size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(tileColor)
            if let image = ContentStore.shared.thumbnail(for: word, maxPixels: Int(size * 3)) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .padding(size * 0.17)
            } else {
                Text(String(word.entry(s.headline).text.prefix(1)).uppercased())
                    .font(.onest(size * 0.44, 800))
                    .foregroundStyle(Color(uiColor: p.accent))
            }
        }
        .frame(width: size, height: size)
    }

    /// "RU  слово   TJ калима", the language tags in the muted colour.
    private func translationsLine(_ word: Word) -> Text {
        var line = Text(verbatim: "")
        for (i, lang) in s.translations.enumerated() {
            if i > 0 { line = line + Text(verbatim: "   ") }
            line = line
                + Text(verbatim: lang.label + "  ").foregroundColor(muted)
                + Text(verbatim: word.entry(lang).text).foregroundColor(ink)
        }
        return line
    }

    /// Shows the next word right from the widget (interactive widgets, iOS 17).
    private var nextButton: some View {
        Button(intent: NextWordIntent()) {
            Image(systemName: "arrow.triangle.2.circlepath")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(muted)
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L10n.t("action_next_word"))
    }
}
