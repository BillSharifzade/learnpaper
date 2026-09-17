import Foundation

/// Deterministic generator so a pass is shuffled the same way wherever it is replayed
/// (the widget timeline and the app must agree on the order). SplitMix64.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: Int64) { state = UInt64(bitPattern: seed) &+ 0x9E37_79B9_7F4A_7C15 }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

/// Picks the next word. Port of the Android `Rotation` object; see its documentation.
///
/// Light spaced repetition: every shown word gets a `Review` with a due time that grows with each
/// showing (`stepsDays`). A word that is due is shown before anything else. Otherwise words come
/// from the queue: unseen words of the selected levels, shuffled once per pass with a stable seed,
/// followed by seen words in the order they become due. Learned words are skipped until nothing
/// else is left.
enum Rotation {
    /// Days until a word is shown again after its 1st, 2nd, … showing.
    static let stepsDays = [1, 3, 7, 14, 30, 60]
    static let dayMs: Int64 = 86_400_000

    static func advance(_ progress: Progress, settings: Settings, words: [Word], now: Int64, tzOffsetMs: Int64 = 0) -> Progress {
        let levelsKey = settings.levels.sorted().joined(separator: ",")
        let seed = progress.seed == 0 ? now : progress.seed
        let byId = Dictionary(uniqueKeysWithValues: words.map { ($0.id, $0) })
        let eligible: (String) -> Bool = { id in
            !progress.learned.contains(id) && byId[id].map { settings.levels.contains($0.level) } == true
        }

        var queue = progress.queue.filter(eligible)
        if progress.levelsKey != levelsKey || queue.isEmpty {
            queue = buildQueue(words: words, levels: settings.levels, progress: progress, now: now, seed: seed &+ Int64(progress.history.count))
            // Avoid showing the same word twice in a row when a new pass starts.
            if queue.count > 1, queue.first == progress.currentId {
                queue = Array(queue.dropFirst()) + [queue[0]]
            }
        }

        let due = dueNow(words: words, levels: settings.levels, progress: progress, now: now).first { $0 != progress.currentId }
        guard let nextId = due ?? queue.first else {
            var p = progress
            p.seed = seed
            p.levelsKey = levelsKey
            p.queue = []
            return p
        }

        let stage = min(progress.reviews[nextId]?.stage ?? 0, stepsDays.count - 1)
        let review = Review(stage: stage + 1, dueAt: now + Int64(stepsDays[stage]) * dayMs)
        let day = epochDay(now, tzOffsetMs: tzOffsetMs)
        var days = progress.activeDays
        if days.last != day {
            days.append(day)
            if days.count > Progress.activeDaysCap { days.removeFirst(days.count - Progress.activeDaysCap) }
        }

        var p = progress
        p.seed = seed
        p.levelsKey = levelsKey
        if let i = queue.firstIndex(of: nextId) { queue.remove(at: i) }
        p.queue = queue
        p.currentId = nextId
        p.lastChangeAt = now
        p.history = Array(([HistoryEntry(id: nextId, at: now)] + progress.history).prefix(Progress.historyCap))
        p.paletteIndex = progress.paletteIndex + 1
        p.reviews[nextId] = review
        p.activeDays = days
        return p
    }

    /// Unlearned words of `levels` that are due for review, earliest first.
    static func dueNow(words: [Word], levels: Set<String>, progress: Progress, now: Int64) -> [String] {
        words.compactMap { w -> (String, Int64)? in
            guard levels.contains(w.level), !progress.learned.contains(w.id),
                  let r = progress.reviews[w.id], r.dueAt <= now else { return nil }
            return (w.id, r.dueAt)
        }
        .sorted { $0.1 < $1.1 }
        .map(\.0)
    }

    /// Unseen words shuffled by `seed`, then seen ones by due time. Falls back to learned words when nothing else is left.
    static func buildQueue(words: [Word], levels: Set<String>, progress: Progress, now: Int64, seed: Int64) -> [String] {
        let pool = words.filter { levels.contains($0.level) }
        let unlearned = pool.filter { !progress.learned.contains($0.id) }
        let chosen = unlearned.isEmpty ? pool : unlearned
        let seen = chosen.filter { progress.reviews[$0.id] != nil }
        let unseen = chosen.filter { progress.reviews[$0.id] == nil }
        var rng = SeededGenerator(seed: seed)
        return unseen.map(\.id).shuffled(using: &rng)
            + seen.sorted { progress.reviews[$0.id]!.dueAt < progress.reviews[$1.id]!.dueAt }.map(\.id)
    }

    static func epochDay(_ now: Int64, tzOffsetMs: Int64) -> Int64 {
        let v = now + tzOffsetMs
        return v >= 0 ? v / dayMs : (v - dayMs + 1) / dayMs
    }
}
