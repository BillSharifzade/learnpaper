import SwiftUI

/// Step-by-step guide for the one thing iOS does not let us do ourselves: setting the wallpaper.
/// The user builds a shortcut "Get LearnPaper card → Set Wallpaper" and Time of Day automations
/// that run it. See docs/DESIGN.md §3 and §12.
struct ShortcutsGuideView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(L10n.t("ios_guide_intro"))
                    .textStyle(TypeScale.bodyLarge)
                    .foregroundStyle(Theme.onSurface)
                ForEach(1...6, id: \.self) { n in
                    HStack(alignment: .top, spacing: 12) {
                        Text(verbatim: "\(n)")
                            .textStyle(TypeScale.titleSmall)
                            .foregroundStyle(Theme.onPrimaryContainer)
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(Theme.primaryContainer))
                        Text(L10n.t("ios_guide_step\(n)"))
                            .textStyle(TypeScale.bodyLarge)
                            .foregroundStyle(Theme.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 3)
                    }
                }
                Text(L10n.t("ios_guide_tip"))
                    .textStyle(TypeScale.bodySmall)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).fill(Theme.surfaceContainerHigh))
                if let url = URL(string: "shortcuts://") {
                    Link(destination: url) {
                        Label(L10n.t("ios_guide_open_shortcuts"), systemImage: "arrow.up.forward.app")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
            }
            .padding(24)
        }
        .background(Theme.background)
        .navigationTitle(L10n.t("ios_shortcuts_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(L10n.t("dialog_close")) { dismiss() }
            }
        }
        .onAppear { model.markGuideSeen() }
    }
}
