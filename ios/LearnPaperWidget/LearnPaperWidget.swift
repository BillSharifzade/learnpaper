import SwiftUI
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

/// Precomputes the cards for the next day by replaying the tick schedule (see `Schedule`), so the
/// widget changes on the hour without the app running. WidgetKit's refresh budget is not touched:
/// one timeline covers 24 hours.
struct CardProvider: TimelineProvider {
    func placeholder(in context: Context) -> CardEntry {
        let settings = Settings()
        return CardEntry(date: Date(), word: ContentStore.shared.words.first, settings: settings, palette: Palettes.byId(settings.paletteId))
    }

    func getSnapshot(in context: Context, completion: @escaping (CardEntry) -> Void) {
        let store = Store.shared
        let settings = store.settings
        let progress = store.catchUp()
        completion(entry(at: Date(), progress: progress, settings: settings))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CardEntry>) -> Void) {
        Fonts.register()
        let store = Store.shared
        let settings = store.settings
        let words = ContentStore.shared.words
        let progress = store.catchUp()
        let now = Date()
        var entries = [entry(at: now, progress: progress, settings: settings)]

        var p = progress
        let horizon = now.millis + 24 * 3_600_000
        for t in Schedule.ticks(progress: progress, settings: settings, until: horizon).prefix(48) {
            p.lastTick = t
            if settings.isQuiet(at: Date(millis: t)) { continue }
            p = Rotation.advance(p, settings: settings, words: words, now: t, tzOffsetMs: Schedule.tzOffset(at: t))
            entries.append(entry(at: Date(millis: t), progress: p, settings: settings))
        }
        let refresh = entries.last.map { max($0.date, now.addingTimeInterval(3600)) } ?? now.addingTimeInterval(3600)
        completion(Timeline(entries: entries, policy: .after(refresh)))
    }

    private func entry(at date: Date, progress: Progress, settings: Settings) -> CardEntry {
        let word = progress.currentId.flatMap(ContentStore.shared.word)
            ?? ContentStore.shared.words.first { settings.levels.contains($0.level) }
        return CardEntry(date: date, word: word, settings: settings, palette: Palettes.forSettings(settings, index: progress.paletteIndex))
    }
}

struct CardWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.learnpaper.card", provider: CardProvider()) { entry in
            CardWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("widget.name"))
        .description(Text("widget.description"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

struct CardWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CardEntry

    private var p: Palette { entry.palette }
    private var s: Settings { entry.settings }

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
                Text("home.noWord").font(.caption)
            }
        }
        .containerBackground(for: .widget) {
            if family == .accessoryRectangular { Color.clear } else { Color(p.bg) }
        }
    }

    private func lockScreen(_ word: Word) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(word.entry(s.headline).text).font(.headline).lineLimit(1)
            let tr = word.entry(s.headline).tr
            if s.showTranscriptions, !tr.isEmpty { Text(CardRenderer.formatTr(s.headline, tr)).font(.caption2).lineLimit(1) }
            if let lang = s.translations.first {
                Text("\(lang.label) \(word.entry(lang).text)").font(.caption).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func small(_ word: Word) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            emoji(word, size: 44)
            Spacer(minLength: 0)
            Text(word.entry(s.headline).text).font(.title3.weight(.semibold)).foregroundStyle(Color(p.text)).lineLimit(1).minimumScaleFactor(0.7)
            if let lang = s.translations.first {
                Text(word.entry(lang).text).font(.subheadline).foregroundStyle(Color(p.muted)).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    private func medium(_ word: Word) -> some View {
        HStack(spacing: 14) {
            emoji(word, size: 64)
            VStack(alignment: .leading, spacing: 3) {
                Text(word.entry(s.headline).text).font(.title2.weight(.semibold)).foregroundStyle(Color(p.text)).lineLimit(1).minimumScaleFactor(0.7)
                let tr = word.entry(s.headline).tr
                if s.showTranscriptions, !tr.isEmpty { Text(CardRenderer.formatTr(s.headline, tr)).font(.caption).foregroundStyle(Color(p.muted)) }
                Text(s.translations.map { "\($0.label) \(word.entry($0).text)" }.joined(separator: "   "))
                    .font(.subheadline.weight(.medium)).foregroundStyle(Color(p.text)).lineLimit(2)
                if s.showExamples {
                    Text(word.example.of(s.headline)).font(.caption).foregroundStyle(Color(p.muted)).lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func large(_ word: Word) -> some View {
        VStack(spacing: 8) {
            emoji(word, size: 96)
            Text(word.entry(s.headline).text).font(.largeTitle.weight(.bold)).foregroundStyle(Color(p.text)).lineLimit(1).minimumScaleFactor(0.6)
            let tr = word.entry(s.headline).tr
            if s.showTranscriptions, !tr.isEmpty { Text(CardRenderer.formatTr(s.headline, tr)).font(.subheadline).foregroundStyle(Color(p.muted)) }
            Text([word.level, PosNames.localized(word.pos)].filter { !$0.isEmpty }.joined(separator: "  ·  ").uppercased())
                .font(.caption2.weight(.semibold)).tracking(1.5).foregroundStyle(Color(p.muted))
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(Color(p.tile), in: Capsule())
            ForEach(s.translations) { lang in
                HStack(spacing: 8) {
                    Text(lang.label).font(.caption2.weight(.bold)).foregroundStyle(Color(p.muted))
                        .padding(.horizontal, 7).padding(.vertical, 3).background(Color(p.tile), in: Capsule())
                    Text(word.entry(lang).text).font(.title3.weight(.semibold)).foregroundStyle(Color(p.text)).lineLimit(1)
                    let t = word.entry(lang).tr
                    if s.showTranscriptions, !t.isEmpty { Text(CardRenderer.formatTr(lang, t)).font(.footnote).foregroundStyle(Color(p.muted)).lineLimit(1) }
                }
            }
            if s.showExamples {
                let main = word.example.of(s.headline)
                if !main.isEmpty {
                    Rectangle().fill(Color(p.muted).opacity(0.35)).frame(width: 120, height: 1).padding(.vertical, 4)
                    Text(main).font(.footnote.weight(.medium)).foregroundStyle(Color(p.text)).multilineTextAlignment(.center).lineLimit(2)
                    ForEach(s.translations) { lang in
                        let t = word.example.of(lang)
                        if !t.isEmpty { Text(t).font(.caption2).foregroundStyle(Color(p.muted)).multilineTextAlignment(.center).lineLimit(2) }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func emoji(_ word: Word, size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.18).fill(Color(p.tile))
            if let image = ContentStore.shared.image(for: word) {
                Image(uiImage: image).resizable().scaledToFit().frame(width: size * 0.64, height: size * 0.64)
            }
        }
        .frame(width: size, height: size)
    }
}
