import Foundation

struct Stats: Equatable {
    var streakDays: Int
    var wordsSeen: Int
    var wordsLearned: Int
    var dueToday: Int
}

enum StatsCalculator {
    static func of(_ progress: Progress, levels: Set<String>, wordLevels: [String: String], now: Int64, tzOffsetMs: Int64) -> Stats {
        let today = Rotation.epochDay(now, tzOffsetMs: tzOffsetMs)
        let inLevels: (String) -> Bool = { wordLevels[$0].map(levels.contains) == true }
        return Stats(
            streakDays: streak(progress.activeDays, today: today),
            wordsSeen: progress.reviews.keys.filter(inLevels).count,
            wordsLearned: progress.learned.filter(inLevels).count,
            dueToday: progress.reviews.filter { inLevels($0.key) && !progress.learned.contains($0.key) && $0.value.dueAt <= now }.count
        )
    }

    /// Consecutive days with activity ending today or yesterday. `activeDays` must be ascending and distinct.
    static func streak(_ activeDays: [Int64], today: Int64) -> Int {
        guard let last = activeDays.last, last >= today - 1 else { return 0 }
        var count = 0
        var expected = last
        for day in activeDays.reversed() {
            if day != expected { break }
            count += 1
            expected -= 1
        }
        return count
    }
}
