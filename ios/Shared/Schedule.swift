import Foundation

/// When cards change on iOS.
///
/// Nothing on iOS can run our code at a fixed interval, so the schedule is *derived* from time:
/// ticks happen at `scheduleAnchor + k * interval`. Anyone who needs the current card (the app on
/// launch, the widget building its timeline, the Shortcuts intent) replays every tick since
/// `lastTick` with `Rotation.advance`, which is deterministic given the tick time. The app and the
/// widget therefore always agree on which card is current, and the widget can precompute the
/// cards for the next hours without any communication.
enum Schedule {
    /// Ticks in (progress.lastTick, until], oldest first.
    static func ticks(progress: Progress, settings: Settings, until: Int64) -> [Int64] {
        let interval = Int64(max(settings.intervalMinutes, 15)) * 60_000
        guard progress.scheduleAnchor > 0 else { return [] }
        var result: [Int64] = []
        var t = progress.scheduleAnchor
        if progress.lastTick >= t {
            // First tick strictly after the last applied one.
            let k = (progress.lastTick - progress.scheduleAnchor) / interval + 1
            t = progress.scheduleAnchor + k * interval
        }
        while t <= until && result.count < 2000 {
            result.append(t)
            t += interval
        }
        return result
    }

    /// Applies every tick up to `now`. Quiet hours skip the change but still consume the tick.
    static func catchUp(_ progress: Progress, settings: Settings, words: [Word], now: Int64, calendar: Calendar = .current) -> Progress {
        var p = progress
        for t in ticks(progress: progress, settings: settings, until: now) {
            if !settings.isQuiet(at: Date(millis: t), calendar: calendar) {
                p = Rotation.advance(p, settings: settings, words: words, now: t, tzOffsetMs: tzOffset(at: t, calendar: calendar))
            }
            p.lastTick = t
        }
        return p
    }

    /// Anchor for a fresh schedule: the start of the current hour, so hourly changes land on the hour
    /// and match Time of Day automations the user creates in Shortcuts.
    static func newAnchor(now: Date = Date(), calendar: Calendar = .current) -> Int64 {
        let c = calendar.dateComponents([.year, .month, .day, .hour], from: now)
        return (calendar.date(from: c) ?? now).millis
    }

    static func tzOffset(at millis: Int64, calendar: Calendar = .current) -> Int64 {
        Int64(calendar.timeZone.secondsFromGMT(for: Date(millis: millis))) * 1000
    }
}
