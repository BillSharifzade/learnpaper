import SwiftUI

/// Step-by-step guide for the one thing iOS does not let us do ourselves: setting the wallpaper.
/// The user builds a shortcut "Get LearnPaper card → Set Wallpaper" and Time of Day automations
/// that run it. See docs/DESIGN.md §3 and §12.
struct ShortcutsGuideView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(L10n.t("guide.intro")).font(.body)
                ForEach(1...6, id: \.self) { n in
                    HStack(alignment: .top, spacing: 12) {
                        Text("\(n)")
                            .font(.headline)
                            .frame(width: 30, height: 30)
                            .background(Color.accentColor.opacity(0.18), in: Circle())
                        Text(L10n.t("guide.step\(n)")).font(.body)
                    }
                }
                Text(L10n.t("guide.tip")).font(.footnote).foregroundStyle(.secondary)
                if let url = URL(string: "shortcuts://") {
                    Link(destination: url) {
                        Label(L10n.t("guide.openShortcuts"), systemImage: "arrow.up.forward.app").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding(24)
        }
        .navigationTitle(L10n.t("ios.shortcuts.title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
