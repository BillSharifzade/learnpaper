import SwiftUI

/// Settings as grouped rounded cards. Port of android/.../ui/settings/SettingsScreen.kt; the
/// Wallpaper card explains the iOS routes (widget, Shortcuts) instead of Android's live wallpaper.
struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var confirmReset = false
    @State private var showLicenses = false
    @State private var showGuide = false

    /// Edits go straight to the model; the binding reads the published settings.
    private var settings: Binding<Settings> {
        Binding(get: { model.settings }, set: { new in model.updateSettings { $0 = new } })
    }

    private var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    var body: some View {
        let s = model.settings
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.t("nav_settings"))
                    .textStyle(TypeScale.headlineLarge)
                    .foregroundStyle(Theme.onBackground)
                    .padding(.leading, 4)
                    .padding(.top, 14)
                    .padding(.bottom, 2)
                    .accessibilityAddTraits(.isHeader)

                SectionCard(title: L10n.t("settings_app_language"), systemImage: "character.bubble.fill") {
                    AppLanguagePicker()
                }

                SectionCard(title: L10n.t("settings_learning"), systemImage: "graduationcap.fill") {
                    LanguageControls(settings: settings)
                    Overline(text: L10n.t("settings_levels"))
                        .padding(.top, 20)
                    LevelControls(settings: settings, available: model.content.levels, counts: model.content.levelCounts)
                    ToggleRow(
                        title: L10n.t("settings_show_transcriptions"),
                        subtitle: L10n.t("settings_show_transcriptions_desc"),
                        isOn: settings.showTranscriptions
                    )
                    .padding(.top, 8)
                    ToggleRow(
                        title: L10n.t("settings_show_examples"),
                        subtitle: L10n.t("settings_show_examples_desc"),
                        isOn: settings.showExamples
                    )
                }

                SectionCard(title: L10n.t("settings_appearance"), systemImage: "paintpalette.fill") {
                    CardPreview(
                        settings: s,
                        word: model.currentWord ?? model.previewWord,
                        paletteIndex: model.progress.paletteIndex,
                        corner: 22,
                        showClock: true
                    )
                    .frame(width: CardPreview.screenWidth * 0.42)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 18)
                    PaletteControls(settings: settings)
                    LayoutControls(settings: settings)
                        .padding(.top, 18)
                }

                SectionCard(title: L10n.t("settings_wallpaper"), systemImage: "photo.on.rectangle.angled") {
                    Text(L10n.t("ios_how_body"))
                        .textStyle(TypeScale.bodySmall)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.bottom, 14)
                    DeliveryRoutes()
                    Button {
                        showGuide = true
                    } label: {
                        Label(L10n.t("ios_guide_open"), systemImage: "wand.and.stars")
                    }
                    .buttonStyle(PrimaryButtonStyle(height: 50))
                    .padding(.top, 16)
                }

                SectionCard(title: L10n.t("settings_schedule"), systemImage: "clock.fill") {
                    IntervalControls(settings: settings)
                    QuietHoursControls(settings: settings)
                        .padding(.top, 14)
                    Text(L10n.t("ios_schedule_note"))
                        .textStyle(TypeScale.bodySmall)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 10)
                }

                SectionCard(title: L10n.t("settings_notifications"), systemImage: "bell.fill") {
                    NotificationControls(settings: settings)
                }

                SectionCard(title: L10n.t("settings_about"), systemImage: "info.circle.fill") {
                    HStack(spacing: 10) {
                        BrandMark(size: 44)
                        VStack(alignment: .leading, spacing: 0) {
                            Wordmark()
                            Text(L10n.f("settings_version", version))
                                .textStyle(TypeScale.bodySmall)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                    Text(L10n.t("app_tagline"))
                        .textStyle(TypeScale.titleSmall)
                        .foregroundStyle(Theme.onSurface)
                        .padding(.top, 12)
                    Text(L10n.t("settings_privacy"))
                        .textStyle(TypeScale.bodyMedium)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 6)
                    Text(L10n.t("settings_about_body"))
                        .textStyle(TypeScale.bodySmall)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 10)
                        .padding(.bottom, 6)
                    ActionRow(title: L10n.t("settings_licenses"), systemImage: "doc.text") { showLicenses = true }
                    ActionRow(title: L10n.t("settings_reset"), systemImage: "arrow.counterclockwise") { confirmReset = true }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Theme.background)
        .alert(L10n.t("settings_reset"), isPresented: $confirmReset) {
            Button(L10n.t("dialog_reset"), role: .destructive) { model.resetProgress() }
            Button(L10n.t("dialog_cancel"), role: .cancel) {}
        } message: {
            Text(L10n.t("settings_reset_confirm"))
        }
        .sheet(isPresented: $showLicenses) {
            LicensesView()
                .environment(\.locale, model.language.locale)
        }
        .sheet(isPresented: $showGuide) {
            NavigationStack { ShortcutsGuideView() }
                .environmentObject(model)
        }
    }
}

/// The two ways a card reaches an iPhone screen: the widget (automatic) and Shortcuts (the real wallpaper).
struct DeliveryRoutes: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            route(icon: "square.grid.2x2.fill", title: L10n.t("ios_widget_title"), detail: L10n.t("ios_widget_body"))
            route(icon: "wand.and.stars", title: L10n.t("ios_shortcuts_title"), detail: L10n.t("ios_shortcuts_body"))
        }
    }

    private func route(icon: String, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.onSecondaryContainer)
                .frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.secondaryContainer))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .textStyle(TypeScale.titleSmall)
                    .foregroundStyle(Theme.onSurface)
                Text(detail)
                    .textStyle(TypeScale.bodySmall)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Third-party licences bundled from Shared/Resources/licenses (copied from the Android assets).
struct LicensesView: View {
    @Environment(\.dismiss) private var dismiss
    private let text = LicensesView.load()

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text)
                    .font(.inter(12))
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .textSelection(.enabled)
            }
            .background(Theme.surfaceContainerLowest)
            .navigationTitle(L10n.t("settings_licenses"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.t("dialog_close")) { dismiss() }
                }
            }
        }
    }

    static let files = ["FluentEmoji-MIT", "Inter-OFL", "Onest-OFL"]

    static func load() -> String {
        files.compactMap { name -> String? in
            guard let url = Bundle.main.url(forResource: name, withExtension: "txt"),
                  let body = try? String(contentsOf: url, encoding: .utf8)
            else { return nil }
            return "— \(name.replacingOccurrences(of: "-", with: " ")) —\n\n" + body.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        .joined(separator: "\n\n\n")
    }
}
