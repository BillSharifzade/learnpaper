import SwiftUI

/// Shared controls used by onboarding and settings, mirroring the Android `Controls.kt`.

struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.medium))
            .tracking(1.2)
            .foregroundStyle(.secondary)
            .padding(.bottom, 4)
    }
}

struct Chip: View {
    let title: String
    let selected: Bool
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground), in: Capsule())
                .overlay(Capsule().stroke(selected ? Color.accentColor : Color(.separator), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x + s.width > width, x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += s.width + spacing
            rowH = max(rowH, s.height)
        }
        return CGSize(width: width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x + s.width > bounds.maxX, x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing
            rowH = max(rowH, s.height)
        }
    }
}

/// Headline language + translation languages.
struct LanguageControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: L10n.t("label.learning"))
            FlowLayout {
                ForEach(Lang.allCases) { lang in
                    Chip(title: L10n.langName(lang), selected: settings.headline == lang) {
                        if settings.headline != lang {
                            settings.headline = lang
                            settings.shown = Set(Lang.allCases.filter { $0 != lang })
                        }
                    }
                }
            }
            SectionLabel(text: L10n.t("label.showTranslations")).padding(.top, 12)
            FlowLayout {
                ForEach(Lang.allCases.filter { $0 != settings.headline }) { lang in
                    Chip(title: L10n.langName(lang), selected: settings.shown.contains(lang)) {
                        if settings.shown.contains(lang) { settings.shown.remove(lang) } else { settings.shown.insert(lang) }
                    }
                }
            }
        }
    }
}

struct LevelControls: View {
    @Binding var settings: Settings
    let available: [String]

    var body: some View {
        FlowLayout {
            ForEach(Levels.order.prefix(4), id: \.self) { level in
                let enabled = available.contains(level)
                Chip(title: enabled ? level : "\(level) · \(L10n.t("level.comingSoon"))", selected: settings.levels.contains(level), enabled: enabled) {
                    var next = settings.levels
                    if next.contains(level) { next.remove(level) } else { next.insert(level) }
                    if !next.isEmpty { settings.levels = next }
                }
            }
        }
    }
}

struct PaletteControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: L10n.t("label.palette"))
            FlowLayout(spacing: 12) {
                ForEach(Palettes.all) { p in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle().fill(Color(p.bg))
                            Circle().stroke(!settings.rotatePalette && settings.paletteId == p.id ? Color.accentColor : Color(.separator), lineWidth: !settings.rotatePalette && settings.paletteId == p.id ? 3 : 1)
                            Circle().fill(Color(p.text)).frame(width: 14, height: 14)
                        }
                        .frame(width: 52, height: 52)
                        Text(L10n.t(p.nameKey)).font(.caption2).lineLimit(1)
                    }
                    .frame(width: 68)
                    .onTapGesture {
                        settings.paletteId = p.id
                        settings.rotatePalette = false
                    }
                }
            }
            Toggle(L10n.t("palette.rotate"), isOn: $settings.rotatePalette).padding(.top, 4)
        }
    }
}

struct LayoutControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: L10n.t("label.layout"))
            Picker(L10n.t("label.layout"), selection: $settings.layout) {
                ForEach(LayoutPreset.allCases, id: \.self) { preset in
                    Text(L10n.t("layout.\(preset.rawValue)")).tag(preset)
                }
            }
            .pickerStyle(.segmented)
            Text(L10n.t("layout.\(settings.layout.rawValue).desc")).font(.footnote).foregroundStyle(.secondary)
            Text(L10n.t("layout.note")).font(.footnote).foregroundStyle(.secondary)
        }
    }
}

struct IntervalControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: L10n.t("label.interval"))
            FlowLayout {
                ForEach(Settings.intervals, id: \.self) { minutes in
                    Chip(title: L10n.interval(minutes), selected: settings.intervalMinutes == minutes) {
                        settings.intervalMinutes = minutes
                    }
                }
            }
        }
    }
}

struct HourStepper: View {
    let label: String
    @Binding var minutes: Int

    var body: some View {
        VStack(spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            HStack {
                Button { minutes = ((minutes - 60) % 1440 + 1440) % 1440 } label: { Image(systemName: "chevron.left") }
                Text(L10n.minutes(minutes)).font(.headline).monospacedDigit().frame(minWidth: 56)
                Button { minutes = (minutes + 60) % 1440 } label: { Image(systemName: "chevron.right") }
            }
            .buttonStyle(.borderless)
        }
        .frame(maxWidth: .infinity)
    }
}

struct QuietHoursControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: $settings.quietEnabled) {
                VStack(alignment: .leading) {
                    Text(L10n.t("label.quiet"))
                    Text(L10n.t("quiet.desc")).font(.footnote).foregroundStyle(.secondary)
                }
            }
            if settings.quietEnabled {
                HStack(spacing: 16) {
                    HourStepper(label: L10n.t("quiet.from"), minutes: $settings.quietStart)
                    HourStepper(label: L10n.t("quiet.to"), minutes: $settings.quietEnd)
                }
            }
        }
    }
}

struct NotificationControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: Binding(
                get: { settings.notifyDaily },
                set: { on in
                    settings.notifyDaily = on
                    if on { DailyNotification.requestPermission() }
                }
            )) {
                VStack(alignment: .leading) {
                    Text(L10n.t("notif.daily"))
                    Text(L10n.t("notif.daily.desc")).font(.footnote).foregroundStyle(.secondary)
                }
            }
            if settings.notifyDaily {
                HourStepper(label: L10n.t("notif.at"), minutes: $settings.notifyMinute)
            }
        }
    }
}
