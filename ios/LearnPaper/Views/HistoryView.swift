import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var favoritesOnly = false
    @State private var selected: Word?

    var body: some View {
        NavigationStack {
            Group {
                let entries = model.progress.history.filter { !favoritesOnly || model.progress.favorites.contains($0.id) }
                if entries.isEmpty {
                    Text(L10n.t("history.empty")).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(32)
                } else {
                    List(entries, id: \.at) { entry in
                        if let word = model.word(entry.id) {
                            row(word, entry).onTapGesture { selected = word }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(L10n.t("nav.history"))
            .toolbar {
                Toggle(isOn: $favoritesOnly) { Label(L10n.t("history.favoritesOnly"), systemImage: "heart.fill") }
                    .toggleStyle(.button)
            }
            .sheet(item: $selected) { word in
                WordDetail(word: word).presentationDetents([.medium, .large])
            }
        }
    }

    private func row(_ word: Word, _ entry: HistoryEntry) -> some View {
        let s = model.settings
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(word.entry(s.headline).text).font(.headline)
                Text(s.translations.map { word.entry($0).text }.joined(separator: "  ·  ")).font(.subheadline).foregroundStyle(.secondary)
                Text(Date(millis: entry.at), style: .relative).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { model.toggleFavorite(word.id) } label: {
                Image(systemName: model.progress.favorites.contains(word.id) ? "heart.fill" : "heart")
            }
            .buttonStyle(.borderless)
            Button { model.toggleLearned(word.id) } label: {
                Image(systemName: "checkmark").foregroundStyle(model.progress.learned.contains(word.id) ? Color.green : Color.secondary)
            }
            .buttonStyle(.borderless)
        }
        .contentShape(Rectangle())
    }
}

struct WordDetail: View {
    @EnvironmentObject private var model: AppModel
    let word: Word

    var body: some View {
        let s = model.settings
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                WordSummary(word: word)
                Text([L10n.f("level.label", word.level), PosNames.localized(word.pos)].filter { !$0.isEmpty }.joined(separator: "  ·  "))
                    .font(.caption).foregroundStyle(.secondary)
                let example = word.example.of(s.headline)
                if !example.isEmpty {
                    Divider()
                    SectionLabel(text: L10n.t("detail.example"))
                    Text(example)
                    ForEach(s.translations) { lang in
                        let t = word.example.of(lang)
                        if !t.isEmpty { Text(t).font(.subheadline).foregroundStyle(.secondary) }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
    }
}
