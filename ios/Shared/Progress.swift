import Foundation

struct HistoryEntry: Codable, Hashable {
    var id: String
    /// Milliseconds since 1970, like the Android app.
    var at: Int64
}

/// Spaced-repetition state of one word: how many times it was shown and when it is due again.
struct Review: Codable, Hashable {
    var stage: Int = 0
    var dueAt: Int64 = 0
}

/// Everything the app remembers about what the user has seen. Stored as one JSON blob.
struct Progress: Codable, Equatable {
    /// Shuffle seed; 0 means "not chosen yet".
    var seed: Int64 = 0
    /// Sorted, comma-joined levels the queue was built for.
    var levelsKey = ""
    /// Word ids still to be shown in this pass.
    var queue: [String] = []
    var currentId: String?
    var lastChangeAt: Int64 = 0
    /// Newest first, capped.
    var history: [HistoryEntry] = []
    var learned: Set<String> = []
    var favorites: Set<String> = []
    /// Incremented on every change; drives palette rotation.
    var paletteIndex = 0
    /// One entry per word that has been shown at least once.
    var reviews: [String: Review] = [:]
    /// Local calendar days (days since epoch) on which a word was shown; ascending, capped.
    var activeDays: [Int64] = []
    /// Start of the tick schedule (ms). Cards change at anchor + k * interval; see `Schedule`.
    var scheduleAnchor: Int64 = 0
    /// The last tick (ms) that has been applied.
    var lastTick: Int64 = 0

    static let historyCap = 500
    static let activeDaysCap = 400

    enum CodingKeys: String, CodingKey {
        case seed, levelsKey, queue, currentId, lastChangeAt, history, learned, favorites, paletteIndex, reviews, activeDays, scheduleAnchor, lastTick
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        seed = try c.decodeIfPresent(Int64.self, forKey: .seed) ?? 0
        levelsKey = try c.decodeIfPresent(String.self, forKey: .levelsKey) ?? ""
        queue = try c.decodeIfPresent([String].self, forKey: .queue) ?? []
        currentId = try c.decodeIfPresent(String.self, forKey: .currentId)
        lastChangeAt = try c.decodeIfPresent(Int64.self, forKey: .lastChangeAt) ?? 0
        history = try c.decodeIfPresent([HistoryEntry].self, forKey: .history) ?? []
        learned = try c.decodeIfPresent(Set<String>.self, forKey: .learned) ?? []
        favorites = try c.decodeIfPresent(Set<String>.self, forKey: .favorites) ?? []
        paletteIndex = try c.decodeIfPresent(Int.self, forKey: .paletteIndex) ?? 0
        reviews = try c.decodeIfPresent([String: Review].self, forKey: .reviews) ?? [:]
        activeDays = try c.decodeIfPresent([Int64].self, forKey: .activeDays) ?? []
        scheduleAnchor = try c.decodeIfPresent(Int64.self, forKey: .scheduleAnchor) ?? 0
        lastTick = try c.decodeIfPresent(Int64.self, forKey: .lastTick) ?? 0
    }
}

extension Date {
    var millis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
    init(millis: Int64) { self.init(timeIntervalSince1970: TimeInterval(millis) / 1000) }
}
