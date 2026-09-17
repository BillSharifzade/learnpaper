import Foundation

enum LayoutPreset: String, Codable, CaseIterable { case lock, home, compact }

struct Settings: Codable, Equatable {
    var onboarded = false
    /// Language being learned: the big word on the card.
    var headline: Lang = .en
    /// Languages whose translations are shown under the headline.
    var shown: Set<Lang> = [.ru, .tj]
    var showTranscriptions = true
    var showExamples = true
    var levels: Set<String> = ["A1"]
    var paletteId = "peach"
    var rotatePalette = false
    var layout: LayoutPreset = .lock
    var intervalMinutes = 60
    var quietEnabled = true
    /// Minutes since midnight.
    var quietStart = 23 * 60
    var quietEnd = 7 * 60
    /// Daily "word of the day" notification.
    var notifyDaily = false
    var notifyMinute = 9 * 60

    /// Translation languages in display order, never including the headline.
    var translations: [Lang] { Lang.allCases.filter { $0 != headline && shown.contains($0) } }

    func isQuiet(minuteOfDay: Int) -> Bool {
        guard quietEnabled, quietStart != quietEnd else { return false }
        if quietStart < quietEnd {
            return minuteOfDay >= quietStart && minuteOfDay < quietEnd
        }
        return minuteOfDay >= quietStart || minuteOfDay < quietEnd
    }

    func isQuiet(at date: Date, calendar: Calendar = .current) -> Bool {
        let c = calendar.dateComponents([.hour, .minute], from: date)
        return isQuiet(minuteOfDay: (c.hour ?? 0) * 60 + (c.minute ?? 0))
    }

    static let intervals = [15, 30, 60, 120, 180, 360, 720, 1440]

    // Tolerate missing keys from older versions.
    enum CodingKeys: String, CodingKey {
        case onboarded, headline, shown, showTranscriptions, showExamples, levels, paletteId, rotatePalette, layout
        case intervalMinutes, quietEnabled, quietStart, quietEnd, notifyDaily, notifyMinute
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Settings()
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? d.onboarded
        headline = try c.decodeIfPresent(Lang.self, forKey: .headline) ?? d.headline
        shown = try c.decodeIfPresent(Set<Lang>.self, forKey: .shown) ?? d.shown
        showTranscriptions = try c.decodeIfPresent(Bool.self, forKey: .showTranscriptions) ?? d.showTranscriptions
        showExamples = try c.decodeIfPresent(Bool.self, forKey: .showExamples) ?? d.showExamples
        levels = try c.decodeIfPresent(Set<String>.self, forKey: .levels) ?? d.levels
        paletteId = try c.decodeIfPresent(String.self, forKey: .paletteId) ?? d.paletteId
        rotatePalette = try c.decodeIfPresent(Bool.self, forKey: .rotatePalette) ?? d.rotatePalette
        layout = try c.decodeIfPresent(LayoutPreset.self, forKey: .layout) ?? d.layout
        intervalMinutes = try c.decodeIfPresent(Int.self, forKey: .intervalMinutes) ?? d.intervalMinutes
        quietEnabled = try c.decodeIfPresent(Bool.self, forKey: .quietEnabled) ?? d.quietEnabled
        quietStart = try c.decodeIfPresent(Int.self, forKey: .quietStart) ?? d.quietStart
        quietEnd = try c.decodeIfPresent(Int.self, forKey: .quietEnd) ?? d.quietEnd
        notifyDaily = try c.decodeIfPresent(Bool.self, forKey: .notifyDaily) ?? d.notifyDaily
        notifyMinute = try c.decodeIfPresent(Int.self, forKey: .notifyMinute) ?? d.notifyMinute
    }
}
