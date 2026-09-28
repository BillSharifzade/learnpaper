import AppIntents
import Foundation

/// "Next LearnPaper word": advances to the next word. Used by the widget's arrow button (interactive
/// widgets run the intent in the extension) and by shortcuts that want a fresh card on demand, so it
/// is compiled into both targets.
struct NextWordIntent: AppIntent {
    static var title: LocalizedStringResource = "Next LearnPaper word"
    static var openAppWhenRun = false

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let store = Store.shared
        let progress = store.advanceNow()
        store.reloadWidgets()
        let settings = store.settings
        let text = progress.currentId.flatMap { ContentStore.shared.word($0) }.map { $0.entry(settings.headline).text } ?? ""
        return .result(value: text)
    }
}
