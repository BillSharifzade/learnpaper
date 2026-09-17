import Foundation

/// Runtime lookup into Localizable.xcstrings by key.
enum L10n {
    static func t(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }

    /// Formatted variant: `%@` and `%d` placeholders as in the string catalog.
    static func f(_ key: String, _ args: CVarArg...) -> String {
        String(format: t(key), locale: Locale.current, arguments: args)
    }

    static func minutes(_ minutesOfDay: Int) -> String {
        String(format: "%02d:%02d", (minutesOfDay / 60) % 24, minutesOfDay % 60)
    }

    static func interval(_ minutes: Int) -> String {
        if minutes >= 1440 { return t("interval.daily") }
        if minutes >= 60 { return f("interval.hours", minutes / 60) }
        return f("interval.minutes", minutes)
    }

    static func langName(_ lang: Lang) -> String { t("lang.\(lang.code)") }
}
