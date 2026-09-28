import SwiftUI
import UIKit

/// Onboarding until the user finishes it, then the three tabs. The whole tree is keyed by the
/// interface language: switching it rebuilds every screen with the new texts at once (the iOS
/// counterpart of Android recreating the activity), and `\.locale` makes dates and numbers follow.
struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if model.settings.onboarded {
                MainTabs()
                    .transition(.opacity)
            } else {
                OnboardingView()
                    .transition(.opacity)
            }
        }
        .id(model.language)
        .overlay {
            if model.showFullPreview {
                FullScreenPreview()
                    .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .overlay(alignment: .bottom) {
            if let toast = model.toast {
                ToastView(text: toast.text)
                    .padding(.bottom, model.settings.onboarded ? 64 : 96)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .id(toast.id)
                    .task(id: toast.id) {
                        try? await Task.sleep(nanoseconds: 2_500_000_000)
                        if model.toast?.id == toast.id {
                            withAnimation(.easeOut(duration: 0.25)) { model.toast = nil }
                        }
                    }
            }
        }
        .environment(\.locale, model.language.locale)
        .tint(Theme.primary)
    }
}

private struct MainTabs: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView(selection: $model.tab) {
            TodayView()
                .modifier(TabEntrance())
                .tabItem { Label(L10n.t("nav_today"), systemImage: "rectangle.stack.fill") }
                .tag(AppModel.Tab.today)
            WordsView()
                .modifier(TabEntrance())
                .tabItem { Label(L10n.t("nav_words"), systemImage: "book.fill") }
                .tag(AppModel.Tab.words)
            SettingsView()
                .modifier(TabEntrance())
                .tabItem { Label(L10n.t("nav_settings"), systemImage: "slider.horizontal.3") }
                .tag(AppModel.Tab.settings)
        }
        .sensoryFeedback(.selection, trigger: model.tab)
    }
}

/// The card at full size, as it looks on the phone; tap anywhere to close.
private struct FullScreenPreview: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
            CardPreview(
                settings: model.settings,
                word: model.currentWord ?? model.previewWord,
                paletteIndex: model.progress.paletteIndex,
                corner: 36,
                showClock: true,
                renderWidth: min(UIScreen.main.nativeBounds.width, 1440)
            )
            .padding(24)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { model.showFullPreview = false }
        }
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(L10n.t("dialog_close"))
    }
}
