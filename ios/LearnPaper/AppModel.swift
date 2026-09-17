import AVFoundation
import SwiftUI
import UserNotifications

/// State for the whole app: settings, progress and the actions the screens trigger.
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var settings: Settings
    @Published private(set) var progress: Progress
    @Published var busy = false
    @Published var message: String?

    let content = ContentStore.shared
    private let store = Store.shared
    private let synthesizer = AVSpeechSynthesizer()

    init() {
        settings = store.settings
        progress = store.catchUp()
    }

    var currentWord: Word? { progress.currentId.flatMap(content.word) }

    /// Word used for previews before anything has been applied.
    var previewWord: Word? {
        currentWord ?? content.words.first { settings.levels.contains($0.level) } ?? content.words.first
    }

    var stats: Stats {
        let now = Date().millis
        return StatsCalculator.of(progress, levels: settings.levels, wordLevels: Dictionary(uniqueKeysWithValues: content.words.map { ($0.id, $0.level) }), now: now, tzOffsetMs: Schedule.tzOffset(at: now))
    }

    func word(_ id: String) -> Word? { content.word(id) }

    // MARK: - actions

    func onForeground() {
        settings = store.settings
        progress = store.catchUp()
        DailyNotification.reschedule(settings: settings, word: currentWord)
    }

    func updateSettings(_ transform: (inout Settings) -> Void) {
        var s = settings
        transform(&s)
        s.onboarded = settings.onboarded
        settings = s
        store.settings = s
        store.reloadWidgets()
        DailyNotification.reschedule(settings: s, word: currentWord)
    }

    func completeOnboarding(_ draft: Settings) {
        var s = draft
        s.onboarded = true
        settings = s
        store.settings = s
        let anchor = Schedule.newAnchor()
        _ = store.updateProgress { p in
            p.scheduleAnchor = anchor
            p.lastTick = anchor
        }
        nextWord()
    }

    func nextWord() {
        progress = store.advanceNow()
        store.reloadWidgets()
        DailyNotification.reschedule(settings: settings, word: currentWord)
        message = L10n.t("msg.applied")
    }

    func toggleFavorite(_ id: String) {
        progress = store.updateProgress { p in
            if p.favorites.contains(id) { p.favorites.remove(id) } else { p.favorites.insert(id) }
        }
    }

    func toggleLearned(_ id: String) {
        progress = store.updateProgress { p in
            if p.learned.contains(id) { p.learned.remove(id) } else { p.learned.insert(id) }
        }
    }

    func resetProgress() {
        let anchor = Schedule.newAnchor()
        progress = store.updateProgress { p in
            p = Progress()
            p.scheduleAnchor = anchor
            p.lastTick = anchor
        }
        store.reloadWidgets()
    }

    func canSpeak(_ lang: Lang) -> Bool { lang != .tj }

    func speak(_ text: String, lang: Lang) {
        guard canSpeak(lang) else { return }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: lang.localeIdentifier)
        synthesizer.stopSpeaking(at: .immediate)
        synthesizer.speak(utterance)
    }

    /// Renders a card off the main thread for in-app previews.
    nonisolated func renderPreview(settings: Settings, word: Word, paletteIndex: Int, width: CGFloat, height: CGFloat) async -> UIImage {
        await Task.detached(priority: .userInitiated) {
            CardRenderer.shared.render(word: word, settings: settings, palette: Palettes.forSettings(settings, index: paletteIndex), width: width, height: height)
        }.value
    }
}

/// One local notification a day with the current word, at the time chosen in settings.
enum DailyNotification {
    static let id = "daily-word"

    static func reschedule(settings: Settings, word: Word?) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        guard settings.onboarded, settings.notifyDaily, let word else { return }
        let headline = word.entry(settings.headline)
        let content = UNMutableNotificationContent()
        content.title = headline.text + (headline.tr.isEmpty ? "" : "   " + CardRenderer.formatTr(settings.headline, headline.tr))
        let translations = settings.translations.map { word.entry($0).text }.joined(separator: "  ·  ")
        let example = settings.showExamples ? word.example.of(settings.headline) : ""
        content.body = [translations, example].filter { !$0.isEmpty }.joined(separator: "\n")
        content.sound = .default
        var components = DateComponents()
        components.hour = settings.notifyMinute / 60
        components.minute = settings.notifyMinute % 60
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }
}
