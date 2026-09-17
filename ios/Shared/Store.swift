import Foundation
import WidgetKit

/// Settings and progress, shared between the app, the widget and the Shortcuts intent through the
/// App Group's UserDefaults. Everything is stored as JSON so the shape matches the Android app.
final class Store {
    static let appGroup = "group.com.learnpaper"
    static let shared = Store()

    private let defaults: UserDefaults
    private let queue = DispatchQueue(label: "com.learnpaper.store")
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: Store.appGroup) ?? .standard
    }

    var settings: Settings {
        get { load("settings") ?? Settings() }
        set { save(newValue, key: "settings") }
    }

    var progress: Progress {
        get { load("progress") ?? Progress() }
        set { save(newValue, key: "progress") }
    }

    func updateProgress(_ transform: (inout Progress) -> Void) -> Progress {
        queue.sync {
            var p = load("progress") ?? Progress()
            transform(&p)
            save(p, key: "progress")
            return p
        }
    }

    /// Applies every scheduled tick up to now and persists the result. Returns the current progress.
    @discardableResult
    func catchUp(now: Date = Date()) -> Progress {
        let s = settings
        guard s.onboarded else { return progress }
        return updateProgress { p in
            p = Schedule.catchUp(p, settings: s, words: ContentStore.shared.words, now: now.millis)
        }
    }

    /// Shows the next word right now (the "Next word" button), independent of the tick schedule.
    @discardableResult
    func advanceNow(now: Date = Date()) -> Progress {
        let s = settings
        let ms = now.millis
        return updateProgress { p in
            p = Rotation.advance(p, settings: s, words: ContentStore.shared.words, now: ms, tzOffsetMs: Schedule.tzOffset(at: ms))
        }
    }

    func currentWord() -> Word? {
        progress.currentId.flatMap { ContentStore.shared.word($0) }
    }

    /// Tell WidgetKit to rebuild its timeline after anything the widget shows has changed.
    func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func load<T: Decodable>(_ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, key: String) {
        if let data = try? encoder.encode(value) { defaults.set(data, forKey: key) }
    }
}
