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

/// The words of the content pack with lookup tables, built once per load. Replaying a long
/// schedule calls `Rotation.advance` hundreds of times, so these are not rebuilt per call.
struct WordIndex {
    let words: [Word]
    let byId: [String: Word]
    /// Position of each word in the pack; ties are broken by it, like a stable sort on Android.
    let position: [String: Int]

    init(_ words: [Word]) {
        self.words = words
        var byId: [String: Word] = [:]
        var position: [String: Int] = [:]
        byId.reserveCapacity(words.count)
        position.reserveCapacity(words.count)
        for (i, w) in words.enumerated() where byId[w.id] == nil {
            byId[w.id] = w
            position[w.id] = i
        }
        self.byId = byId
        self.position = position
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
        advance(progress, settings: settings, index: WordIndex(words), now: now, tzOffsetMs: tzOffsetMs)
    }

    static func advance(_ progress: Progress, settings: Settings, index: WordIndex, now: Int64, tzOffsetMs: Int64 = 0) -> Progress {
        let levelsKey = settings.levels.sorted().joined(separator: ",")
        let seed = progress.seed == 0 ? now : progress.seed
        let eligible: (String) -> Bool = { id in
            !progress.learned.contains(id) && index.byId[id].map { settings.levels.contains($0.level) } == true
        }

        var queue = progress.queue.filter(eligible)
        if progress.levelsKey != levelsKey || queue.isEmpty {
            queue = buildQueue(words: index.words, levels: settings.levels, progress: progress, now: now, seed: seed &+ Int64(progress.history.count))
            // Avoid showing the same word twice in a row when a new pass starts.
            if queue.count > 1, queue.first == progress.currentId {
                queue = Array(queue.dropFirst()) + [queue[0]]
            }
        }

        let due = dueNow(index: index, levels: settings.levels, progress: progress, now: now).first { $0 != progress.currentId }
        guard let nextId = due ?? queue.first else {
            var p = progress
            p.seed = seed
            p.levelsKey = levelsKey
            p.queue = []
            return p
        }

        var p = progress
        p.seed = seed
        p.levelsKey = levelsKey
        p.queue = queue
        return markShown(p, id: nextId, now: now, tzOffsetMs: tzOffsetMs)
    }

    /// Puts `wordId` on the card because the user picked it (from the Words library), with the same
    /// bookkeeping as `advance`: history, review stage, streak day, palette step. The word leaves the
    /// current queue.
    static func show(_ progress: Progress, wordId: String, now: Int64, tzOffsetMs: Int64 = 0) -> Progress {
        markShown(progress, id: wordId, now: now, tzOffsetMs: tzOffsetMs)
    }

    private static func markShown(_ progress: Progress, id: String, now: Int64, tzOffsetMs: Int64) -> Progress {
        let stage = min(progress.reviews[id]?.stage ?? 0, stepsDays.count - 1)
        let review = Review(stage: stage + 1, dueAt: now + Int64(stepsDays[stage]) * dayMs)
        let day = epochDay(now, tzOffsetMs: tzOffsetMs)
        var days = progress.activeDays
        if days.last != day {
            days.append(day)
            if days.count > Progress.activeDaysCap { days.removeFirst(days.count - Progress.activeDaysCap) }
        }

        var p = progress
        if let i = p.queue.firstIndex(of: id) { p.queue.remove(at: i) }
        p.currentId = id
        p.lastChangeAt = now
        p.history = Array(([HistoryEntry(id: id, at: now)] + progress.history).prefix(Progress.historyCap))
        p.paletteIndex = progress.paletteIndex + 1
        p.reviews[id] = review
        p.activeDays = days
        return p
    }

    /// Unlearned words of `levels` that are due for review, earliest first (ties in pack order).
    static func dueNow(words: [Word], levels: Set<String>, progress: Progress, now: Int64) -> [String] {
        dueNow(index: WordIndex(words), levels: levels, progress: progress, now: now)
    }

    static func dueNow(index: WordIndex, levels: Set<String>, progress: Progress, now: Int64) -> [String] {
        var due: [(id: String, dueAt: Int64, position: Int)] = []
        for (id, review) in progress.reviews where review.dueAt <= now && !progress.learned.contains(id) {
            guard let word = index.byId[id], levels.contains(word.level), let position = index.position[id] else { continue }
            due.append((id, review.dueAt, position))
        }
        due.sort { $0.dueAt != $1.dueAt ? $0.dueAt < $1.dueAt : $0.position < $1.position }
        return due.map { $0.id }
    }

    /// Unseen words shuffled by `seed`, then seen ones by due time. Falls back to learned words when nothing else is left.
    static func buildQueue(words: [Word], levels: Set<String>, progress: Progress, now: Int64, seed: Int64) -> [String] {
        let pool = words.filter { levels.contains($0.level) }
        let unlearned = pool.filter { !progress.learned.contains($0.id) }
        let chosen = unlearned.isEmpty ? pool : unlearned
        var seen: [(id: String, dueAt: Int64, position: Int)] = []
        var unseen: [String] = []
        for (i, w) in chosen.enumerated() {
            if let r = progress.reviews[w.id] { seen.append((w.id, r.dueAt, i)) } else { unseen.append(w.id) }
        }
        seen.sort { $0.dueAt != $1.dueAt ? $0.dueAt < $1.dueAt : $0.position < $1.position }
        var rng = SeededGenerator(seed: seed)
        return unseen.shuffled(using: &rng) + seen.map { $0.id }
    }

    static func epochDay(_ now: Int64, tzOffsetMs: Int64) -> Int64 {
        let v = now + tzOffsetMs
        return v >= 0 ? v / dayMs : (v - dayMs + 1) / dayMs
    }
}
