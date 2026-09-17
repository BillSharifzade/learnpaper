import SwiftUI

struct TodayView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L10n.f("home.scheduleEvery", L10n.interval(model.settings.intervalMinutes)))
                        .font(.subheadline).foregroundStyle(.secondary)
                    if let line = nextChangeLine { Text(line).font(.subheadline).foregroundStyle(.secondary) }

                    StatsRow(stats: model.stats)

                    HStack(alignment: .top, spacing: 16) {
                        CardPreview(settings: model.settings, word: model.currentWord ?? model.previewWord, paletteIndex: model.progress.paletteIndex)
                        VStack(alignment: .leading, spacing: 8) {
                            if let word = model.currentWord {
                                WordSummary(word: word)
                                HStack(spacing: 8) {
                                    let fav = model.progress.favorites.contains(word.id)
                                    Button { model.toggleFavorite(word.id) } label: { Image(systemName: fav ? "heart.fill" : "heart") }
                                        .buttonStyle(.bordered).tint(fav ? .pink : .secondary)
                                    let learned = model.progress.learned.contains(word.id)
                                    Button { model.toggleLearned(word.id) } label: { Image(systemName: "checkmark") }
                                        .buttonStyle(.bordered).tint(learned ? .green : .secondary)
                                }
                            } else {
                                Text(L10n.t("home.noWord")).foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Button { model.nextWord() } label: {
                        Label(L10n.t("home.nextWord"), systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    NavigationLink { ShortcutsGuideView() } label: {
                        Label(L10n.t("ios.shortcuts.guide"), systemImage: "wand.and.stars").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .padding(24)
            }
            .navigationTitle("LearnPaper")
        }
    }

    private var nextChangeLine: String? {
        let p = model.progress
        guard p.scheduleAnchor > 0 else { return nil }
        let s = model.settings
        if s.isQuiet(at: Date()) { return L10n.f("home.quietNow", L10n.minutes(s.quietEnd)) }
        let next = Schedule.ticks(progress: p, settings: s, until: Date().millis + Int64(s.intervalMinutes) * 60_000).first
        guard let next else { return nil }
        return L10n.f("home.nextChange", Date(millis: next).formatted(date: .omitted, time: .shortened))
    }
}

struct StatsRow: View {
    let stats: Stats

    var body: some View {
        HStack(spacing: 8) {
            tile(stats.streakDays, L10n.t("stat.streak"))
            tile(stats.wordsSeen, L10n.t("stat.seen"))
            tile(stats.wordsLearned, L10n.t("stat.learned"))
            tile(stats.dueToday, L10n.t("stat.due"))
        }
    }

    private func tile(_ value: Int, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text("\(value)").font(.title3.weight(.semibold))
            Text(label).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }
}

struct WordSummary: View {
    @EnvironmentObject private var model: AppModel
    let word: Word

    var body: some View {
        let s = model.settings
        let headline = word.entry(s.headline)
        HStack {
            Text(headline.text).font(.title2.weight(.semibold))
            if model.canSpeak(s.headline) {
                Button { model.speak(headline.text, lang: s.headline) } label: { Image(systemName: "play.fill") }.buttonStyle(.borderless)
            }
        }
        if !headline.tr.isEmpty {
            Text(CardRenderer.formatTr(s.headline, headline.tr)).font(.subheadline).foregroundStyle(.secondary)
        }
        ForEach(s.translations) { lang in
            let e = word.entry(lang)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(lang.label)  \(e.text)").font(.headline)
                if !e.tr.isEmpty { Text(CardRenderer.formatTr(lang, e.tr)).font(.caption).foregroundStyle(.secondary) }
            }
        }
    }
}
