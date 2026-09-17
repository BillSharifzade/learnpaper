import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var confirmReset = false

    /// Edits go straight to the model; the binding reads the published settings.
    private var settings: Binding<Settings> {
        Binding(get: { model.settings }, set: { new in model.updateSettings { $0 = new } })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(L10n.t("settings.languages")) { LanguageControls(settings: settings) }
                Section(L10n.t("settings.content")) {
                    SectionLabel(text: L10n.t("onb.level.title"))
                    LevelControls(settings: settings, available: model.content.availableLevels)
                    Toggle(L10n.t("settings.showTranscriptions"), isOn: settings.showTranscriptions)
                    Toggle(L10n.t("settings.showExamples"), isOn: settings.showExamples)
                }
                Section(L10n.t("settings.look")) {
                    PaletteControls(settings: settings)
                    LayoutControls(settings: settings)
                }
                Section(L10n.t("settings.schedule")) {
                    IntervalControls(settings: settings)
                    QuietHoursControls(settings: settings)
                    Text(L10n.t("settings.schedule.iosNote")).font(.footnote).foregroundStyle(.secondary)
                }
                Section(L10n.t("settings.wallpaper")) {
                    NavigationLink(L10n.t("ios.shortcuts.guide")) { ShortcutsGuideView() }
                }
                Section(L10n.t("settings.notifications")) { NotificationControls(settings: settings) }
                Section(L10n.t("settings.about")) {
                    Text(L10n.t("settings.about.body")).font(.footnote)
                    Text(L10n.f("settings.version", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""))
                        .font(.footnote).foregroundStyle(.secondary)
                    Button(L10n.t("settings.reset"), role: .destructive) { confirmReset = true }
                }
            }
            .navigationTitle(L10n.t("nav.settings"))
            .confirmationDialog(L10n.t("settings.reset.confirm"), isPresented: $confirmReset, titleVisibility: .visible) {
                Button(L10n.t("dialog.reset"), role: .destructive) { model.resetProgress() }
                Button(L10n.t("dialog.cancel"), role: .cancel) {}
            }
        }
    }
}
