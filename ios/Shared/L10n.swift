import Foundation

/// Language of the app's interface (not of the words on the card). The raw value is the ISO code
/// of the translation folder (`tg.lproj`); `nativeName` is how the option is shown in pickers.
/// Port of android/.../i18n/AppLocale.kt: Tajik by default, regardless of the system language.
enum AppLanguage: String, CaseIterable, Identifiable {
    case tj = "tg"
    case ru = "ru"
    case en = "en"

    var id: String { rawValue }
    var tag: String { rawValue }

    var nativeName: String {
        switch self {
        case .tj: return "Тоҷикӣ"
        case .ru: return "Русский"
        case .en: return "English"
        }
    }

    /// Locale for dates, numbers and plural rules. English uses the British locale so times read
    /// "14:00" like on Android.
    var locale: Locale {
        switch self {
        case .tj: return Locale(identifier: "tg_TJ")
        case .ru: return Locale(identifier: "ru_RU")
        case .en: return Locale(identifier: "en_GB")
        }
    }

    /// The interface is Tajik until the user picks something else.
    static let defaultLanguage: AppLanguage = .tj

    static func from(tag: String?) -> AppLanguage? {
        guard let tag else { return nil }
        let base = tag.lowercased().split(whereSeparator: { $0 == "-" || $0 == "_" }).first.map(String.init) ?? ""
        return allCases.first { $0.tag == base }
    }
}

/// Interface texts, looked up in the chosen language's bundle.
///
/// Xcode compiles Localizable.xcstrings into en.lproj, ru.lproj and tg.lproj; `t` reads the table of
/// the language picked in the app (stored in the App Group, so the widget and the intents follow it)
/// instead of the one iOS would pick from the system language. `t` and `f` are the only API.
enum L10n {
    static let languageKey = "appLanguage"

    private static let lock = NSLock()
    private static var cached: AppLanguage?
    private static var bundles: [String: Bundle] = [:]
    private static let missing = "\u{1}missing\u{1}"

    /// The current interface language.
    static var language: AppLanguage {
        lock.lock()
        defer { lock.unlock() }
        if let cached { return cached }
        let stored = AppLanguage.from(tag: Store.defaults.string(forKey: languageKey)) ?? AppLanguage.defaultLanguage
        cached = stored
        return stored
    }

    /// Switches the interface language and remembers it for the widget and the intents.
    static func setLanguage(_ lang: AppLanguage) {
        Store.defaults.set(lang.tag, forKey: languageKey)
        lock.lock()
        cached = lang
        lock.unlock()
    }

    /// Forgets the cached choice so the next lookup re-reads it (the widget calls this before it
    /// builds a timeline, because the user may have switched the language in the app meanwhile).
    static func reload() {
        lock.lock()
        cached = nil
        lock.unlock()
    }

    static func t(_ key: String) -> String {
        t(key, in: language)
    }

    /// The text in a given language (used to reserve the room of the longest translation).
    static func t(_ key: String, in lang: AppLanguage) -> String {
        if let value = lookup(key, tag: lang.tag) { return value }
        if lang != .en, let value = lookup(key, tag: AppLanguage.en.tag) { return value }
        return Bundle.main.localizedString(forKey: key, value: key, table: nil)
    }

    /// Formatted variant for `%@` / `%lld` placeholders and plural variations of the catalog.
    static func f(_ key: String, _ args: CVarArg...) -> String {
        String(format: t(key), locale: language.locale, arguments: args)
    }

    private static func lookup(_ key: String, tag: String) -> String? {
        guard let bundle = bundle(tag) else { return nil }
        let value = bundle.localizedString(forKey: key, value: missing, table: nil)
        return value == missing ? nil : value
    }

    private static func bundle(_ tag: String) -> Bundle? {
        lock.lock()
        defer { lock.unlock() }
        if let bundle = bundles[tag] { return bundle }
        guard let path = Bundle.main.path(forResource: tag, ofType: "lproj"), let bundle = Bundle(path: path) else {
            return nil
        }
        bundles[tag] = bundle
        return bundle
    }

    // MARK: - shared phrases

    static func langName(_ lang: Lang) -> String { t("lang_\(lang.code)") }

    /// "Beginner", "Elementary", … for A1…C2.
    static func levelName(_ level: String) -> String {
        Levels.order.contains(level) ? t("level_\(level.lowercased())") : level
    }

    static func wordsCount(_ count: Int) -> String { f("words_count", count) }

    /// "15 min", "2 h", "1 h 30 min", "Once a day".
    static func interval(_ minutes: Int) -> String {
        if minutes >= 1440 { return t("interval_daily") }
        if minutes >= 60 && minutes % 60 == 0 { return f("interval_hours", minutes / 60) }
        if minutes >= 60 { return f("interval_hours_minutes", minutes / 60, minutes % 60) }
        return f("interval_minutes", minutes)
    }

    /// "07:30" for minutes since midnight.
    static func minutes(_ minutesOfDay: Int) -> String {
        String(format: "%02d:%02d", (minutesOfDay / 60) % 24, minutesOfDay % 60)
    }

    /// "14:00" in the local time zone.
    static func time(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return minutes((c.hour ?? 0) * 60 + (c.minute ?? 0))
    }

    /// "just now", "5 min ago", "3 h ago", "yesterday", or a short date, in the interface language.
    static func relativeTime(_ millis: Int64, now: Date = Date()) -> String {
        let diff = max(0, now.millis - millis)
        let minutes = Int(diff / 60_000)
        let hours = minutes / 60
        if minutes < 1 { return t("time_just_now") }
        if minutes < 60 { return f("time_minutes_ago", minutes) }
        if hours < 24 { return f("time_hours_ago", hours) }
        if hours < 48 { return t("time_yesterday") }
        let formatter = DateFormatter()
        formatter.locale = language.locale
        formatter.setLocalizedDateFormatFromTemplate("dMMM")
        return formatter.string(from: Date(millis: millis))
    }

    /// Transcription in its usual brackets: /IPA/ for English, [transliteration] otherwise.
    static func transcription(_ lang: Lang, _ value: String) -> String {
        lang == .en ? "/\(value)/" : "[\(value)]"
    }
}

/// Part-of-speech keys from the content (`noun`, `verb`, …), localised through the catalog.
enum PosNames {
    static let known: Set<String> = [
        "noun", "verb", "adjective", "adverb", "interjection", "phrase", "pronoun", "preposition", "numeral", "conjunction",
    ]

    static func localized(_ pos: String) -> String {
        known.contains(pos) ? L10n.t("pos_\(pos)") : pos
    }
}
