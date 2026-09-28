import Foundation
import WidgetKit

/// Settings and progress, shared between the app, the widget and the Shortcuts intent through the
/// App Group's UserDefaults. Everything is stored as JSON so the shape matches the Android app.
final class Store {
    static let appGroup = "group.com.learnpaper"
    /// The App Group's defaults (falls back to the app's own when the group is not provisioned).
    static let defaults: UserDefaults = UserDefaults(suiteName: appGroup) ?? .standard
    static let shared = Store()

    private let ud: UserDefaults
    private let queue = DispatchQueue(label: "com.learnpaper.store")
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(defaults: UserDefaults? = nil) {
        ud = defaults ?? Store.defaults
    }

    var settings: Settings {
        get { load("settings") ?? Settings() }
        set { save(newValue, key: "settings") }
    }

    var progress: Progress {
        get { load("progress") ?? Progress() }
        set { save(newValue, key: "progress") }
    }

    /// Whether the user has opened the Shortcuts guide once (hides the setup banner on Today).
    var guideSeen: Bool {
        get { ud.bool(forKey: "guideSeen") }
        set { ud.set(newValue, forKey: "guideSeen") }
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
            p = Schedule.catchUp(p, settings: s, index: ContentStore.shared.index, now: now.millis)
        }
    }

    /// Shows the next word right now (the "Next word" button), independent of the tick schedule.
    @discardableResult
    func advanceNow(now: Date = Date()) -> Progress {
        let s = settings
        let ms = now.millis
        return updateProgress { p in
            p = Rotation.advance(p, settings: s, index: ContentStore.shared.index, now: ms, tzOffsetMs: Schedule.tzOffset(at: ms))
        }
    }

    /// Makes a word picked in the Words library the current card.
    @discardableResult
    func show(wordId: String, now: Date = Date()) -> Progress {
        let ms = now.millis
        return updateProgress { p in
            p = Rotation.show(p, wordId: wordId, now: ms, tzOffsetMs: Schedule.tzOffset(at: ms))
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
        guard let data = ud.data(forKey: key) else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, key: String) {
        if let data = try? encoder.encode(value) { ud.set(data, forKey: key) }
    }
}
