import SwiftUI

/// First run: welcome (with the interface language), learned language, level, look, rhythm, ready.
/// Port of android/.../ui/onboarding/OnboardingScreen.kt. Steps and the draft settings live in
/// `AppModel`, so switching the interface language on the first step keeps them.
struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showGuide = false

    private var step: Int { model.onboardingStep }
    private var count: Int { AppModel.onboardingSteps }

    var body: some View {
        VStack(spacing: 0) {
            topBar

            ZStack {
                stepContent(step)
                    .id(step)
                    .transition(.asymmetric(
                        insertion: .offset(x: model.onboardingForward ? 120 : -120).combined(with: .opacity),
                        removal: .opacity
                    ))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()

            Button {
                model.onboardingNext()
            } label: {
                HStack(spacing: 8) {
                    Text(L10n.t(buttonKey))
                    if step < count - 1 {
                        Image(systemName: "arrow.right")
                            .font(.system(size: 15, weight: .semibold))
                    }
                }
            }
            .buttonStyle(PrimaryButtonStyle(height: 58))
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
        }
        .background(Theme.background)
        .sheet(isPresented: $showGuide) {
            NavigationStack { ShortcutsGuideView() }
                .environmentObject(model)
        }
    }

    private var buttonKey: String {
        if step == 0 { return "onb_start" }
        if step == count - 1 { return "ios_onb_finish" }
        return "onb_next"
    }

    /// Back button, gradient progress and "n/6".
    private var topBar: some View {
        HStack(spacing: 0) {
            Button {
                model.onboardingBack()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle(scale: 0.9))
            .opacity(step > 0 ? 1 : 0)
            .disabled(step == 0)
            .accessibilityLabel(L10n.t("onb_back"))
            .padding(.leading, 8)
            .animation(.easeOut(duration: 0.2), value: step > 0)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.surfaceContainerHighest)
                    Capsule()
                        .fill(LinearGradient(colors: [Brand.irisLight, Brand.iris], startPoint: .leading, endPoint: .trailing))
                        .frame(width: geo.size.width * CGFloat(step + 1) / CGFloat(count))
                }
            }
            .frame(height: 6)
            .animation(.spring(response: 0.45, dampingFraction: 0.8), value: step)
            .accessibilityHidden(true)

            Text(verbatim: "\(step + 1)/\(count)")
                .textStyle(TypeScale.labelLarge)
                .foregroundStyle(Theme.onSurfaceVariant)
                .contentTransition(.numericText())
                .animation(.easeOut(duration: 0.2), value: step)
                .padding(.leading, 16)
                .padding(.trailing, 20)
        }
        .frame(height: 56)
    }

    @ViewBuilder
    private func stepContent(_ step: Int) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                switch step {
                case 0: welcome
                case 1: languages
                case 2: level
                case 3: look
                case 4: rhythm
                default: ready
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - steps

    @ViewBuilder
    private var welcome: some View {
        Hero()
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
        Text(L10n.t("onb_welcome_title"))
            .textStyle(TypeScale.headlineMedium)
            .foregroundStyle(Theme.onBackground)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 22)
        Text(L10n.t("onb_welcome_body"))
            .textStyle(TypeScale.bodyLarge)
            .foregroundStyle(Theme.onSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 8)
        Overline(text: L10n.t("onb_app_language"))
            .padding(.top, 20)
        AppLanguagePicker()
    }

    @ViewBuilder
    private var languages: some View {
        StepHeader(title: L10n.t("onb_languages_title"), subtitle: L10n.t("onb_languages_body"))
        LanguageControls(settings: $model.draft)
        preview(widthFraction: 0.44, corner: 24)
            .padding(.top, 28)
    }

    @ViewBuilder
    private var level: some View {
        StepHeader(title: L10n.t("onb_level_title"), subtitle: L10n.t("onb_level_body"))
        LevelControls(settings: $model.draft, available: model.content.levels, counts: model.content.levelCounts)
    }

    @ViewBuilder
    private var look: some View {
        StepHeader(title: L10n.t("onb_look_title"), subtitle: L10n.t("onb_look_body"))
        preview(widthFraction: 0.46, corner: 24)
        PaletteControls(settings: $model.draft)
            .padding(.top, 24)
        LayoutControls(settings: $model.draft)
            .padding(.top, 20)
    }

    @ViewBuilder
    private var rhythm: some View {
        StepHeader(title: L10n.t("onb_schedule_title"), subtitle: L10n.t("onb_schedule_body"))
        IntervalControls(settings: $model.draft)
        QuietHoursControls(settings: $model.draft)
            .padding(.top, 16)
    }

    @ViewBuilder
    private var ready: some View {
        StepHeader(title: L10n.t("onb_ready_title"), subtitle: L10n.t("ios_onb_ready_body"))
        preview(widthFraction: 0.56, corner: 30, float: true)
        Overline(text: L10n.t("ios_how_title"))
            .padding(.top, 28)
        Text(L10n.t("ios_how_body"))
            .textStyle(TypeScale.bodyMedium)
            .foregroundStyle(Theme.onSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, 14)
        DeliveryRoutes()
        Button {
            showGuide = true
        } label: {
            Label(L10n.t("ios_guide_open"), systemImage: "wand.and.stars")
                .textStyle(TypeScale.labelLarge)
                .foregroundStyle(Theme.primary)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(Capsule().fill(Theme.primaryContainer.opacity(0.6)))
        }
        .buttonStyle(PressableStyle())
        .padding(.top, 16)
    }

    private func preview(widthFraction: CGFloat, corner: CGFloat, float: Bool = false) -> some View {
        CardPreview(settings: model.draft, word: model.previewWord, paletteIndex: 0, corner: corner, showClock: true, float: float)
            .frame(width: CardPreview.screenWidth * widthFraction)
            .frame(maxWidth: .infinity)
    }
}

private struct StepHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .textStyle(TypeScale.headlineLarge)
                .foregroundStyle(Theme.onBackground)
                .fixedSize(horizontal: false, vertical: true)
            Text(subtitle)
                .textStyle(TypeScale.bodyLarge)
                .foregroundStyle(Theme.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 12)
        .padding(.bottom, 24)
    }
}

/// The logo made real: the live card floating on a tilted apricot card, like the two cards of the icon.
private struct Hero: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let width = CardPreview.screenWidth * 0.43
        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(LinearGradient(colors: [Brand.apricot, Brand.apricot.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: width - 28, height: width / CardPreview.aspect)
                .offset(x: 14)
                .rotationEffect(.degrees(8))
            CardPreview(settings: model.draft, word: model.previewWord, paletteIndex: 0, corner: 28, showClock: true, float: true)
                .frame(width: width)
                .rotationEffect(.degrees(-4))
        }
        .padding(.vertical, 18)
    }
}
