import AppIntents
import UIKit
import UniformTypeIdentifiers

/// "Get LearnPaper card": returns the current card as a PNG at screen resolution so a shortcut can
/// hand it to the system's Set Wallpaper action. Runs in the background (no app launch).
struct GetCardIntent: AppIntent {
    static var title: LocalizedStringResource = "Get LearnPaper card"
    static var description = IntentDescription("Returns the current word card as an image, ready for Set Wallpaper.")
    static var openAppWhenRun = false

    @Parameter(title: "Layout", default: .lock)
    var layout: LayoutChoice

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        Fonts.register()
        let store = Store.shared
        let progress = store.catchUp()
        var settings = store.settings
        settings.layout = layout == .lock ? .lock : settings.layout
        guard let word = progress.currentId.flatMap(ContentStore.shared.word) ?? ContentStore.shared.words.first else {
            throw IntentError.noWords
        }
        let size = await MainActor.run { UIScreen.main.nativeBounds.size }
        let palette = Palettes.forSettings(settings, index: progress.paletteIndex)
        let image = CardRenderer.shared.render(word: word, settings: settings, palette: palette, width: min(size.width, size.height), height: max(size.width, size.height))
        guard let data = image.pngData() else { throw IntentError.render }
        return .result(value: IntentFile(data: data, filename: "learnpaper-\(word.id).png", type: .png))
    }
}

/// "Next LearnPaper word": advances to the next word, for shortcuts that want a fresh card on demand.
struct NextWordIntent: AppIntent {
    static var title: LocalizedStringResource = "Next LearnPaper word"
    static var openAppWhenRun = false

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let store = Store.shared
        let progress = store.advanceNow()
        store.reloadWidgets()
        let settings = store.settings
        let text = progress.currentId.flatMap(ContentStore.shared.word).map { $0.entry(settings.headline).text } ?? ""
        return .result(value: text)
    }
}

enum LayoutChoice: String, AppEnum {
    case lock, home

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Layout")
    static var caseDisplayRepresentations: [LayoutChoice: DisplayRepresentation] = [
        .lock: "Lock screen (clock zone free)",
        .home: "Home screen layout",
    ]
}

enum IntentError: Error, CustomLocalizedStringResourceConvertible {
    case noWords, render

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .noWords: return "No words for the selected levels."
        case .render: return "Could not render the card."
        }
    }
}

struct LearnPaperShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: GetCardIntent(),
            phrases: ["Get \(.applicationName) card", "\(.applicationName) wallpaper"],
            shortTitle: "Get card",
            systemImageName: "photo"
        )
        AppShortcut(
            intent: NextWordIntent(),
            phrases: ["Next \(.applicationName) word"],
            shortTitle: "Next word",
            systemImageName: "arrow.clockwise"
        )
    }
}
