import SwiftUI

/// The word library: search in any language, filters, level groups with sticky headers, and a detail
/// sheet. Port of android/.../ui/words/WordsScreen.kt (replaces the old History screen). The list
/// (search, filters, sorting over ~2,500 words) is computed in a background task whenever its inputs
/// change, so typing and opening the tab never wait for it.
struct WordsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = ""
    @State private var filter: WordFilter = .all
    @State private var levelFilter: Set<String> = []
    @State private var selected: Word? = nil
    @State private var rows: [WordRowItem] = []
    @State private var shownKey: RowsKey? = nil
    @FocusState private var searchFocused: Bool

    private var rowsKey: RowsKey {
        RowsKey(
            query: trimmedQuery, filter: filter, levels: levelFilter, headline: model.settings.headline,
            history: model.progress.history.first?.at ?? 0, favorites: model.progress.favorites, learned: model.progress.learned
        )
    }

    var body: some View {
        let key = rowsKey
        let grouped = filter == .all && trimmedQuery.isEmpty
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                HStack(alignment: .lastTextBaseline) {
                    Text(L10n.t("words_title"))
                        .textStyle(TypeScale.headlineLarge)
                        .foregroundStyle(Theme.onBackground)
                    Spacer(minLength: 8)
                    Text(L10n.wordsCount(rows.count))
                        .textStyle(TypeScale.labelLarge)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .contentTransition(.numericText())
                        .animation(.easeOut(duration: 0.2), value: rows.count)
                }
                .padding(.top, 14)
                .padding(.bottom, 12)
                searchField
                    .padding(.bottom, 12)
            }
            .padding(.horizontal, 20)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(WordFilter.allCases, id: \.self) { f in
                        ChoiceChip(text: f.title, selected: filter == f, systemImage: f.systemImage, imageTint: f.tint) {
                            filter = f
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(model.content.levels, id: \.self) { level in
                        let on = levelFilter.contains(level)
                        ChoiceChip(text: level, selected: on) {
                            levelFilter.formSymmetricDifference([level])
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.top, 8)
            .padding(.bottom, 6)

            if rows.isEmpty {
                ScrollView {
                    // Only a finished, empty result gets the message; not the moment before the first one.
                    if shownKey == key {
                        EmptyState(message: L10n.t(emptyKey))
                    }
                }
                .scrollDismissesKeyboard(.immediately)
            } else {
                list(rows, grouped: grouped)
            }
        }
        .background(Theme.background)
        .task(id: key) {
            // A keystroke waits a moment, so fast typing searches once; everything else updates at once.
            if let shown = shownKey, shown.query != key.query {
                try? await Task.sleep(nanoseconds: 120_000_000)
            }
            let library = await model.library()
            let progress = model.progress
            guard !Task.isCancelled else { return }
            let result = await Task.detached(priority: .userInitiated) {
                WordsView.makeRows(key: key, library: library, progress: progress)
            }.value
            guard !Task.isCancelled else { return }
            rows = result
            shownKey = key
        }
        .sheet(item: $selected) { word in
            WordDetailSheet(word: word)
                .environmentObject(model)
        }
    }

    private var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var emptyKey: String {
        if !trimmedQuery.isEmpty { return "words_empty_search" }
        switch filter {
        case .all: return "words_empty_search"
        case .history: return "words_empty_history"
        case .favorites: return "words_empty_favorites"
        case .learned: return "words_empty_learned"
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Theme.onSurfaceVariant)
            TextField(L10n.t("words_search_hint"), text: $query)
                .font(TypeScale.bodyLarge.font)
                .foregroundStyle(Theme.onSurface)
                .focused($searchFocused)
                .submitLabel(.search)
                .onSubmit { searchFocused = false }
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("words_clear_search"))
                .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 52)
        .background(Capsule().fill(Theme.surfaceContainerHigh))
        .animation(.easeOut(duration: 0.15), value: query.isEmpty)
    }

    private func list(_ rows: [WordRowItem], grouped: Bool) -> some View {
        let settings = model.settings
        let progress = model.progress
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6, pinnedViews: grouped ? [.sectionHeaders] : []) {
                    Color.clear.frame(height: 0).id("top")
                    if grouped {
                        ForEach(groups(rows), id: \.level) { group in
                            Section {
                                ForEach(group.rows) { row in
                                    rowView(row, settings: settings, progress: progress, showLevel: false)
                                }
                            } header: {
                                LevelHeader(level: group.level, count: group.rows.count)
                            }
                        }
                    } else {
                        ForEach(rows) { row in
                            rowView(row, settings: settings, progress: progress, showLevel: true)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.immediately)
            .onChange(of: filter) { proxy.scrollTo("top", anchor: .top) }
            .onChange(of: levelFilter) { proxy.scrollTo("top", anchor: .top) }
        }
    }

    private func rowView(_ row: WordRowItem, settings: Settings, progress: Progress, showLevel: Bool) -> some View {
        WordRow(
            row: row,
            settings: settings,
            favorite: progress.favorites.contains(row.word.id),
            learned: progress.learned.contains(row.word.id),
            current: progress.currentId == row.word.id,
            showLevel: showLevel
        ) {
            searchFocused = false
            selected = row.word
        }
    }

    /// The rows for `key`; pure, runs off the main thread.
    nonisolated static func makeRows(key: RowsKey, library: LibraryIndex, progress: Progress) -> [WordRowItem] {
        let content = ContentStore.shared
        let found: [Word]? = key.query.isEmpty ? nil : library.search.search(key.query)
        let hits: Set<String>? = found.map { words in Set(words.map { $0.id }) }
        let base: [WordRowItem]
        switch key.filter {
        case .all:
            base = (found ?? library.order(key.headline)).map { WordRowItem(word: $0, shownAt: nil) }
        case .history:
            var seen = Set<String>()
            var list: [WordRowItem] = []
            for entry in progress.history where !seen.contains(entry.id) {
                seen.insert(entry.id)
                if let word = content.word(entry.id) { list.append(WordRowItem(word: word, shownAt: entry.at)) }
            }
            base = list
        case .favorites:
            base = WordSearch.sorted(progress.favorites.compactMap { content.word($0) }, headline: key.headline).map { WordRowItem(word: $0, shownAt: nil) }
        case .learned:
            base = WordSearch.sorted(progress.learned.compactMap { content.word($0) }, headline: key.headline).map { WordRowItem(word: $0, shownAt: nil) }
        }
        return base.filter { row in
            (key.levels.isEmpty || key.levels.contains(row.word.level)) && (hits?.contains(row.word.id) ?? true)
        }
    }

    private func groups(_ rows: [WordRowItem]) -> [LevelGroup] {
        var byLevel: [String: [WordRowItem]] = [:]
        for row in rows { byLevel[row.word.level, default: []].append(row) }
        let known = Levels.order.filter { byLevel[$0] != nil }
        let other = byLevel.keys.filter { !Levels.order.contains($0) }.sorted()
        return (known + other).map { LevelGroup(level: $0, rows: byLevel[$0] ?? []) }
    }
}

enum WordFilter: CaseIterable {
    case all, history, favorites, learned

    var title: String {
        switch self {
        case .all: return L10n.t("words_filter_all")
        case .history: return L10n.t("words_filter_history")
        case .favorites: return L10n.t("words_filter_favorites")
        case .learned: return L10n.t("words_filter_learned")
        }
    }

    var systemImage: String? {
        switch self {
        case .all: return nil
        case .history: return "clock.arrow.circlepath"
        case .favorites: return "heart.fill"
        case .learned: return "checkmark.circle.fill"
        }
    }

    var tint: Color? {
        switch self {
        case .all, .history: return nil
        case .favorites: return Theme.favorite
        case .learned: return Theme.success
        }
    }
}

/// Everything the list depends on; a change starts a new background computation.
struct RowsKey: Equatable {
    let query: String
    let filter: WordFilter
    let levels: Set<String>
    let headline: Lang
    /// Newest history entry, standing in for the whole history (it only grows at the front).
    let history: Int64
    let favorites: Set<String>
    let learned: Set<String>
}

struct WordRowItem: Identifiable {
    let word: Word
    /// When the word was last shown (Seen filter only).
    let shownAt: Int64?
    var id: String { word.id }
}

private struct LevelGroup {
    let level: String
    let rows: [WordRowItem]
}

/// Sticky section header: level badge, level name, word count.
private struct LevelHeader: View {
    let level: String
    let count: Int

    var body: some View {
        HStack(spacing: 10) {
            LevelBadge(level: level)
            Text(L10n.levelName(level))
                .textStyle(TypeScale.titleSmall)
                .foregroundStyle(Theme.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(L10n.wordsCount(count))
                .textStyle(TypeScale.labelMedium)
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 10)
        .background(Theme.background)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

private struct WordRow: View {
    let row: WordRowItem
    let settings: Settings
    let favorite: Bool
    let learned: Bool
    let current: Bool
    let showLevel: Bool
    let onTap: () -> Void

    var body: some View {
        let word = row.word
        Button(action: onTap) {
            HStack(spacing: 12) {
                WordThumb(word: word, size: 46, headline: settings.headline)
                VStack(alignment: .leading, spacing: 1) {
                    Text(word.entry(settings.headline).text)
                        .textStyle(TypeScale.titleMedium)
                        .foregroundStyle(Theme.onSurface)
                        .lineLimit(1)
                    Text(settings.translations.map { word.entry($0).text }.joined(separator: "  ·  "))
                        .textStyle(TypeScale.bodyMedium)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 4) {
                    HStack(spacing: 4) {
                        if learned {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.success)
                        }
                        if favorite {
                            Image(systemName: "heart.fill").foregroundStyle(Theme.favorite)
                        }
                        if showLevel {
                            LevelBadge(level: word.level)
                        }
                    }
                    .font(.system(size: 13, weight: .semibold))
                    if let at = row.shownAt {
                        Text(L10n.relativeTime(at))
                            .textStyle(TypeScale.labelSmall)
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: Radius.medium, style: .continuous)
                    .fill(current ? Theme.primaryContainer.opacity(0.55) : Theme.surfaceContainerLowest)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle(scale: 0.98, haptic: false))
    }
}
