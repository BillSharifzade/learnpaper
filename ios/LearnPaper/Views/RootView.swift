import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        if model.settings.onboarded {
            MainTabs()
        } else {
            OnboardingView()
        }
    }
}

private struct MainTabs: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label(L10n.t("nav.today"), systemImage: "house.fill") }
            HistoryView()
                .tabItem { Label(L10n.t("nav.history"), systemImage: "calendar") }
            SettingsView()
                .tabItem { Label(L10n.t("nav.settings"), systemImage: "gearshape.fill") }
        }
        .overlay(alignment: .bottom) {
            if let message = model.message {
                Text(message)
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.thinMaterial, in: Capsule())
                    .padding(.bottom, 60)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(for: .seconds(2.5))
                        withAnimation { model.message = nil }
                    }
            }
        }
    }
}
