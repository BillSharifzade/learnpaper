import SwiftUI

/// Today: the card as it looks now, the word in detail, favourite / "I know it" / "Next word", and
/// progress. Port of android/.../ui/today/TodayScreen.kt.
struct TodayView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showGuide = false

    var body: some View {
        let settings = model.settings
        let word = model.currentWord
        let stats = model.stats
        ScrollView {
            VStack(spacing: 0) {
                TopBar(streak: stats.streakDays)

                if !model.guideSeen {
                    SetupBanner(onOpen: { showGuide = true }, onDismiss: { model.markGuideSeen() })
                        .padding(.bottom, 16)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                SwipeCard(onSwiped: { model.nextWord() }, onTap: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { model.showFullPreview = true }
                }) {
                    CardPreview(
                        settings: settings,
                        word: word ?? model.previewWord,
                        paletteIndex: model.progress.paletteIndex,
                        showClock: true
                    )
                    .frame(width: CardPreview.screenWidth * 0.5)
                }
                .padding(.top, 8)
                .padding(.bottom, 4)

                ScheduleChip(settings: settings, progress: model.progress)
                    .padding(.top, 18)
                    .padding(.bottom, 18)

                Group {
                    if let word {
                        WordPanel(word: word, settings: settings)
                            .id(word.id)
                            .transition(.asymmetric(
                                insertion: .opacity.combined(with: .scale(scale: 0.97)).combined(with: .offset(y: 14)),
                                removal: .opacity.combined(with: .offset(y: -8))
                            ))
                    } else {
                        SectionCard {
                            Text(L10n.t("home_no_word"))
                                .textStyle(TypeScale.bodyLarge)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                }

                HStack(spacing: 12) {
                    if let word {
                        WordToggles(
                            favorite: model.progress.favorites.contains(word.id),
                            learned: model.progress.learned.contains(word.id),
                            onFavorite: { model.toggleFavorite(word.id) },
                            onLearned: { model.toggleLearned(word.id) }
                        )
                    }
                    Button {
                        model.nextWord()
                    } label: {
                        Label(L10n.t("action_next_word"), systemImage: "sparkles")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
                .padding(.top, 16)

                HStack(spacing: 10) {
                    StatTile(value: stats.wordsSeen, label: L10n.t("stat_seen"))
                    StatTile(value: stats.wordsLearned, label: L10n.t("stat_learned"), accent: Theme.success)
                    StatTile(value: stats.dueToday, label: L10n.t("stat_due"), accent: Theme.primary)
                }
                .padding(.top, 16)

                WeekStrip(activeDays: model.progress.activeDays)
                    .padding(.top, 10)
                    .padding(.bottom, 28)
            }
            .padding(.horizontal, 20)
            .animation(.spring(response: 0.4, dampingFraction: 0.85), value: word?.id)
            .animation(.spring(response: 0.4, dampingFraction: 0.85), value: model.guideSeen)
        }
        .background(Theme.background)
        .sheet(isPresented: $showGuide) {
            NavigationStack { ShortcutsGuideView() }
                .environmentObject(model)
        }
    }
}

/// Brand mark + wordmark, and the streak chip with its flame.
private struct TopBar: View {
    let streak: Int

    var body: some View {
        HStack(spacing: 8) {
            BrandMark(size: 34)
            Wordmark()
            Spacer(minLength: 8)
            if streak > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 15, weight: .semibold))
                    Text(L10n.f("stat_streak", streak))
                        .textStyle(TypeScale.labelLarge)
                        .contentTransition(.numericText())
                }
                .foregroundStyle(Theme.streak)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(Theme.streak.opacity(0.14)))
                .transition(.opacity.combined(with: .scale(scale: 0.8)))
            }
        }
        .padding(.vertical, 14)
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: streak)
    }
}

/// iOS stand-in for Android's "one step left" banner: the wallpaper needs a Shortcuts automation.
/// Shown until the user opens the guide or closes the banner.
private struct SetupBanner: View {
    let onOpen: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Text(L10n.t("ios_setup_title"))
                    .textStyle(TypeScale.titleLarge)
                    .foregroundStyle(.white)
                Spacer(minLength: 8)
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white.opacity(0.85))
                        .frame(width: 28, height: 28)
                        .background(Circle().fill(.white.opacity(0.18)))
                }
                .buttonStyle(PressableStyle(scale: 0.9))
                .accessibilityLabel(L10n.t("dialog_close"))
            }
            Text(L10n.t("ios_setup_body"))
                .textStyle(TypeScale.bodyMedium)
                .foregroundStyle(.white.opacity(0.9))
                .padding(.top, 6)
            Button(action: onOpen) {
                Text(L10n.t("ios_setup_action"))
                    .textStyle(TypeScale.labelLarge)
                    .foregroundStyle(Brand.irisDeep)
                    .padding(.horizontal, 20)
                    .frame(minHeight: 44)
                    .background(Capsule().fill(.white))
            }
            .buttonStyle(PressableStyle())
            .padding(.top, 14)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Radius.large, style: .continuous)
                .fill(LinearGradient(colors: [Brand.irisLight, Brand.irisDeep], startPoint: .topLeading, endPoint: .bottomTrailing))
        )
    }
}

/// The hero card: drag sideways for the next word (it tilts with the finger and springs away), tap to
/// see it full size.
private struct SwipeCard<Content: View>: View {
    let onSwiped: () -> Void
    let onTap: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var offset: CGFloat = 0
    @State private var dragging = false
    @State private var swipes = 0
    private let threshold: CGFloat = 90

    var body: some View {
        content()
            .offset(x: offset)
            .rotationEffect(.degrees(Double(offset / 40)))
            .opacity(1 - min(0.5, abs(offset) / (threshold * 4)))
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .simultaneousGesture(
                DragGesture(minimumDistance: 16)
                    .onChanged { value in
                        // Only horizontal drags move the card, so vertical scrolling stays free.
                        if dragging || abs(value.translation.width) > abs(value.translation.height) * 1.2 {
                            dragging = true
                            offset = value.translation.width
                        }
                    }
                    .onEnded { _ in
                        guard dragging else { return }
                        dragging = false
                        finish()
                    }
            )
            .sensoryFeedback(.impact(weight: .medium), trigger: swipes)
            .accessibilityAddTraits(.isButton)
    }

    private func finish() {
        guard abs(offset) > threshold else {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { offset = 0 }
            return
        }
        let out: CGFloat = offset > 0 ? CardPreview.screenWidth : -CardPreview.screenWidth
        swipes += 1
        withAnimation(.easeIn(duration: 0.18)) {
            offset = out
        } completion: {
            onSwiped()
            offset = -out * 0.6
            withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) { offset = 0 }
        }
    }
}

/// "Next word at 14:00 · every 1 h", or the quiet-hours note at night.
private struct ScheduleChip: View {
    let settings: Settings
    let progress: Progress

    var body: some View {
        let quiet = settings.isQuiet(at: Date())
        HStack(spacing: 6) {
            Image(systemName: quiet ? "moon.stars.fill" : "clock")
                .font(.system(size: 13, weight: .semibold))
            Text(text(quiet: quiet))
                .textStyle(TypeScale.labelMedium)
                .lineLimit(2)
                .multilineTextAlignment(.center)
        }
        .foregroundStyle(Theme.onSurfaceVariant)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Capsule().fill(Theme.surfaceContainerHigh))
    }

    private func text(quiet: Bool) -> String {
        if quiet { return L10n.f("today_quiet_now", L10n.minutes(settings.quietEnd)) }
        let every = L10n.f("today_every", L10n.interval(settings.intervalMinutes))
        if progress.lastChangeAt > 0, let next = Schedule.nextChange(progress: progress, settings: settings) {
            return L10n.f("today_next_change", L10n.time(next)) + "  ·  " + every
        }
        return every.prefix(1).uppercased() + every.dropFirst()
    }
}

/// Level, part of speech, the headword with its translations and the example.
private struct WordPanel: View {
    let word: Word
    let settings: Settings

    var body: some View {
        SectionCard {
            HStack(spacing: 8) {
                LevelBadge(level: word.level)
                Text(PosNames.localized(word.pos))
                    .textStyle(TypeScale.labelMedium)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            Headword(word: word, settings: settings)
                .padding(.top, 10)
            if !settings.translations.isEmpty {
                TranslationRows(word: word, settings: settings)
                    .padding(.top, 16)
            }
            if settings.showExamples, !word.example.of(settings.headline).isEmpty {
                Rectangle()
                    .fill(Theme.outlineVariant)
                    .frame(height: 1)
                    .padding(.top, 16)
                    .padding(.bottom, 14)
                ExampleBlock(word: word, settings: settings)
            }
        }
    }
}

/// The last seven days as dots: filled with a flame on days a word was shown.
private struct WeekStrip: View {
    @EnvironmentObject private var model: AppModel
    let activeDays: [Int64]

    var body: some View {
        let now = Date()
        let today = Rotation.epochDay(now.millis, tzOffsetMs: Schedule.tzOffset(at: now.millis))
        let active = Set(activeDays)
        let formatter = Self.formatter(model.language.locale)
        HStack(spacing: 0) {
            ForEach(0..<7, id: \.self) { index in
                let back = 6 - index
                let day = Calendar.current.date(byAdding: .day, value: -back, to: now) ?? now
                let on = active.contains(today - Int64(back))
                let isToday = back == 0
                VStack(spacing: 6) {
                    Text(Self.weekday(day, formatter))
                        .textStyle(TypeScale.labelSmall)
                        .foregroundStyle(isToday ? Theme.onSurface : Theme.onSurfaceVariant)
                    ZStack {
                        Circle().fill(on ? Theme.streak : Theme.surfaceContainerHigh)
                        if isToday {
                            Circle().strokeBorder(Theme.primary, lineWidth: 2)
                        }
                        if on {
                            Image(systemName: "flame.fill")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 26, height: 26)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).fill(Theme.surfaceContainerLowest))
        .overlay(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).strokeBorder(Theme.outlineVariant.opacity(0.7), lineWidth: 1))
        .accessibilityHidden(true)
    }

    private static func formatter(_ locale: Locale) -> DateFormatter {
        let f = DateFormatter()
        f.locale = locale
        f.dateFormat = "EEE"
        return f
    }

    /// Two letters of the weekday name, capitalised ("Mo", "Пн", "Дш").
    private static func weekday(_ date: Date, _ formatter: DateFormatter) -> String {
        let name = String(formatter.string(from: date).prefix(2))
        return name.prefix(1).uppercased() + name.dropFirst()
    }
}
