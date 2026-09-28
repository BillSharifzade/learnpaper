import SwiftUI
import UIKit

@main
struct LearnPaperApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        Fonts.register()
        Self.styleTabBar()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { model.onForeground() }
                }
        }
    }

    /// The tab bar in the brand's surface colour with Onest labels (SwiftUI has no API for either).
    private static func styleTabBar() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(light: UIColor(rgb: 0xFFFDFA), dark: UIColor(rgb: 0x17141E))
        appearance.shadowColor = UIColor(light: UIColor(rgb: 0xE8E2DA), dark: UIColor(rgb: 0x34303F))
        let font = Fonts.onest(size: 11, weight: 600)
        for item in [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance, appearance.compactInlineLayoutAppearance] {
            item.normal.titleTextAttributes = [.font: font]
            item.selected.titleTextAttributes = [.font: font]
        }
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}
