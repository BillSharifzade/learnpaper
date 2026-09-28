import AVFoundation
import SwiftUI
import UserNotifications

/// A short message at the bottom of the screen (the Android snackbar).
struct Toast: Equatable {
    let id = UUID()
    let text: String
}

/// State for the whole app: settings, progress, the interface language and the actions the screens
/// trigger. Port of the Android `AppViewModel`.
@MainActor
final class AppModel: ObservableObject {
    enum Tab: Hashable { case today, words, settings }

    @Published private(set) var settings: Settings
    @Published private(set) var progress: Progress
    /// Interface language. Changing it rebuilds the view tree (see `RootView`), so everything is
    /// re-labelled at once without a restart.
    @Published private(set) var language: AppLanguage
    @Published var tab: Tab = .today
    @Published var toast: Toast?
    @Published var showFullPreview = false
    @Published private(set) var guideSeen: Bool

    // Onboarding state lives here so it survives a language switch on the first step.
    @Published var draft: Settings
    @Published private(set) var onboardingStep = 0
    @Published private(set) var onboardingForward = true
    static let onboardingSteps = 6

    let content = ContentStore.shared
    private let store = Store.shared
    private let synthesizer = AVSpeechSynthesizer()
    private var searchIndex: WordSearch?
    private var libraryCache: (headline: Lang, words: [Word])?
    private var tickTask: Task<Void, Never>?

    init() {
        let store = Store.shared
        let current = store.settings
        settings = current
        draft = current
        progress = store.catchUp()
        language = L10n.language
        guideSeen = store.guideSeen
        scheduleTick()
    }

    // MARK: - derived

    var currentWord: Word? { progress.currentId.flatMap { content.word($0) } }

    /// Word used for previews before anything has been applied.
    var previewWord: Word? {
        currentWord ?? content.words.first { settings.levels.contains($0.level) } ?? content.words.first
    }

    var stats: Stats {
        let now = Date().millis
        return StatsCalculator.of(progress, levels: settings.levels, wordLevels: content.wordLevels, now: now, tzOffsetMs: Schedule.tzOffset(at: now))
    }

    var search: WordSearch {
        if let searchIndex { return searchIndex }
        let index = WordSearch(content.words)
        searchIndex = index
        return index
    }

    /// Library order (level, then alphabetical in the learned language), cached per headline language.
    func libraryOrder(_ headline: Lang) -> [Word] {
        if let cache = libraryCache, cache.headline == headline { return cache.words }
        let sorted = WordSearch.sorted(content.words, headline: headline)
        libraryCache = (headline, sorted)
        return sorted
    }

    func word(_ id: String) -> Word? { content.word(id) }

    // MARK: - lifecycle

    func onForeground() {
        settings = store.settings
        progress = store.catchUp()
        DailyNotification.reschedule(settings: settings, word: currentWord)
        scheduleTick()
    }

    /// Catches up with the schedule when the next change is due while the app stays open.
    private func scheduleTick() {
        tickTask?.cancel()
        guard settings.onboarded, let next = Schedule.nextChange(progress: progress, settings: settings) else { return }
        let delay = max(1, next.timeIntervalSinceNow + 1)
        tickTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                self.progress = self.store.catchUp()
            }
            self.scheduleTick()
        }
    }

    // MARK: - settings and language

    func updateSettings(_ transform: (inout Settings) -> Void) {
        var s = settings
        transform(&s)
        s.onboarded = settings.onboarded
        guard s != settings else { return }
        settings = s
        store.settings = s
        store.reloadWidgets()
        DailyNotification.reschedule(settings: s, word: currentWord)
        scheduleTick()
    }

    func setLanguage(_ lang: AppLanguage) {
        guard lang != language else { return }
        L10n.setLanguage(lang)
        withAnimation(.easeInOut(duration: 0.25)) { language = lang }
        store.reloadWidgets()
    }

    // MARK: - onboarding

    func onboardingNext() {
        if onboardingStep >= Self.onboardingSteps - 1 {
            completeOnboarding()
        } else {
            goToStep(onboardingStep + 1)
        }
    }

    func onboardingBack() {
        if onboardingStep > 0 { goToStep(onboardingStep - 1) }
    }

    /// The incoming step slides in from the side it is travelling from (see `OnboardingView`); the
    /// outgoing one fades, so only the new view needs to know the direction.
    private func goToStep(_ step: Int) {
        onboardingForward = step > onboardingStep
        withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) { onboardingStep = step }
    }

    func completeOnboarding() {
        var s = draft
        s.onboarded = true
        let anchor = Schedule.newAnchor()
        _ = store.updateProgress { p in
            p.scheduleAnchor = anchor
            p.lastTick = anchor
        }
        store.settings = s
        withAnimation(.easeInOut(duration: 0.4)) {
            settings = s
            tab = .today
        }
        nextWord()
    }

    // MARK: - words

    func nextWord() {
        let before = progress.lastChangeAt
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            progress = store.advanceNow()
        }
        store.reloadWidgets()
        DailyNotification.reschedule(settings: settings, word: currentWord)
        let changed = progress.currentId != nil && progress.lastChangeAt != before
        show(L10n.t(changed ? "ios_msg_applied" : "msg_no_words"))
        scheduleTick()
    }

    /// Makes a word picked in the library the current card (widgets follow at once, the wallpaper
    /// the next time the user's shortcut runs).
    func showWord(_ id: String) {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            progress = store.show(wordId: id)
        }
        store.reloadWidgets()
        DailyNotification.reschedule(settings: settings, word: currentWord)
        show(L10n.t("ios_msg_shown"))
    }

    func toggleFavorite(_ id: String) {
        progress = store.updateProgress { p in
            p.favorites.formSymmetricDifference([id])
        }
    }

    func toggleLearned(_ id: String) {
        progress = store.updateProgress { p in
            p.learned.formSymmetricDifference([id])
        }
        // Learned words leave the rotation, so the widget's precomputed timeline changes.
        store.reloadWidgets()
    }

    func resetProgress() {
        let anchor = Schedule.newAnchor()
        progress = store.updateProgress { p in
            p = Progress()
            p.scheduleAnchor = anchor
            p.lastTick = anchor
        }
        store.reloadWidgets()
        DailyNotification.reschedule(settings: settings, word: currentWord)
        scheduleTick()
    }

    func markGuideSeen() {
        guard !guideSeen else { return }
        guideSeen = true
        store.guideSeen = true
    }

    func show(_ message: String) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { toast = Toast(text: message) }
    }

    // MARK: - speech

    /// English and Russian only; iOS has no Tajik voice.
    func canSpeak(_ lang: Lang) -> Bool { lang != .tj }

    func speak(_ text: String, lang: Lang) {
        guard canSpeak(lang) else { return }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: lang.localeIdentifier)
        synthesizer.stopSpeaking(at: .immediate)
        synthesizer.speak(utterance)
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
        content.title = headline.text + (headline.tr.isEmpty ? "" : "   " + L10n.transcription(settings.headline, headline.tr))
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
