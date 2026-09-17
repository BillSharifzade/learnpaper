import SwiftUI

private enum Step: Int, CaseIterable { case welcome, languages, level, look, schedule, ios, ready }

struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var step: Step = .welcome
    @State private var draft = Settings()

    var body: some View {
        NavigationStack {
            content
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                ForEach(Step.allCases, id: \.rawValue) { s in
                    Capsule()
                        .fill(s.rawValue <= step.rawValue ? Color.accentColor : Color(.separator))
                        .frame(width: s == step ? 24 : 6, height: 6)
                }
                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case .welcome:
                        Text(L10n.t("onb.welcome.title")).font(.largeTitle.weight(.semibold))
                        Text(L10n.t("onb.welcome.body")).font(.body)
                        CardPreview(settings: draft, word: model.previewWord, paletteIndex: 0)
                            .frame(maxWidth: 240)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 12)
                    case .languages:
                        header("onb.languages.title", "onb.languages.body")
                        LanguageControls(settings: $draft)
                    case .level:
                        header("onb.level.title", "onb.level.body")
                        LevelControls(settings: $draft, available: model.content.availableLevels)
                    case .look:
                        header("onb.look.title", nil)
                        CardPreview(settings: draft, word: model.previewWord, paletteIndex: 0)
                            .frame(maxWidth: 190)
                            .frame(maxWidth: .infinity)
                        PaletteControls(settings: $draft)
                        LayoutControls(settings: $draft)
                    case .schedule:
                        header("onb.schedule.title", nil)
                        IntervalControls(settings: $draft)
                        QuietHoursControls(settings: $draft)
                    case .ios:
                        header("onb.ios.title", "onb.ios.body")
                        IosSetupSummary()
                    case .ready:
                        header("onb.ready.title", "onb.ready.body")
                        CardPreview(settings: draft, word: model.previewWord, paletteIndex: 0)
                            .frame(maxWidth: 210)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }

            HStack(spacing: 12) {
                if step != .welcome {
                    Button(L10n.t("onb.back")) { step = Step(rawValue: step.rawValue - 1) ?? .welcome }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                }
                Button {
                    if step == .ready { model.completeOnboarding(draft) } else { step = Step(rawValue: step.rawValue + 1) ?? .ready }
                } label: {
                    Text(L10n.t(step == .welcome ? "onb.start" : step == .ready ? "onb.finish" : "onb.next"))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .layoutPriority(1)
            }
            .padding(24)
        }
        .onAppear { draft = model.settings }
    }

    @ViewBuilder
    private func header(_ title: String, _ body: String?) -> some View {
        Text(L10n.t(title)).font(.title.weight(.semibold))
        if let body { Text(L10n.t(body)).font(.subheadline).foregroundStyle(.secondary) }
    }
}

/// The honest iOS story: the widget works by itself; the real wallpaper needs Shortcuts.
struct IosSetupSummary: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.t("ios.widget.title")).font(.headline)
                    Text(L10n.t("ios.widget.body")).font(.subheadline).foregroundStyle(.secondary)
                }
            } icon: { Image(systemName: "square.grid.2x2.fill").foregroundStyle(Color.accentColor) }
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.t("ios.shortcuts.title")).font(.headline)
                    Text(L10n.t("ios.shortcuts.body")).font(.subheadline).foregroundStyle(.secondary)
                }
            } icon: { Image(systemName: "wand.and.stars").foregroundStyle(Color.accentColor) }
            NavigationLink { ShortcutsGuideView() } label: {
                Text(L10n.t("ios.shortcuts.guide")).frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }
}
