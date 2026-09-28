import SwiftUI
import UIKit

// Controls shared by onboarding and settings; port of android/.../ui/components/Controls.kt.

/// Interface language: Тоҷикӣ / Русский / English. Switching re-labels everything at once.
struct AppLanguagePicker: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        SegmentedControl(
            items: AppLanguage.allCases,
            selection: model.language,
            label: { $0.nativeName },
            onSelect: { model.setLanguage($0) }
        )
    }
}

/// Language being learned (the big word) and the translation languages under it.
struct LanguageControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Overline(text: L10n.t("label_learning"))
            SegmentedControl(
                items: Lang.allCases,
                selection: settings.headline,
                label: { L10n.langName($0) },
                onSelect: { lang in
                    var s = settings
                    s.headline = lang
                    s.shown = Set(Lang.allCases.filter { $0 != lang })
                    settings = s
                }
            )
            Overline(text: L10n.t("label_show_translations"))
                .padding(.top, 18)
            FlowLayout {
                ForEach(Lang.allCases.filter { $0 != settings.headline }) { lang in
                    let on = settings.shown.contains(lang)
                    ChoiceChip(text: L10n.langName(lang), selected: on, systemImage: on ? "checkmark" : nil) {
                        var s = settings
                        s.shown.formSymmetricDifference([lang])
                        settings = s
                    }
                }
            }
        }
    }
}

/// Two-column grid of level cards; at least one level stays selected.
struct LevelControls: View {
    @Binding var settings: Settings
    let available: [String]
    let counts: [String: Int]

    var body: some View {
        let levels = Levels.order.filter { available.contains($0) }
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(levels, id: \.self) { level in
                LevelCard(level: level, count: counts[level] ?? 0, selected: settings.levels.contains(level)) {
                    var next = settings.levels
                    next.formSymmetricDifference([level])
                    if !next.isEmpty { settings.levels = next }
                }
            }
        }
    }
}

private struct LevelCard: View {
    let level: String
    let count: Int
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    LevelBadge(level: level)
                    Spacer(minLength: 4)
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(selected ? Theme.primary : Theme.outline)
                        .contentTransition(.symbolEffect(.replace))
                }
                Text(L10n.levelName(level))
                    .textStyle(TypeScale.titleSmall)
                    .foregroundStyle(Theme.onSurface)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.top, 10)
                Text(L10n.wordsCount(count))
                    .textStyle(TypeScale.bodySmall)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Radius.medium, style: .continuous)
                    .fill(selected ? Theme.level(level).opacity(0.55) : Theme.surfaceContainerLowest)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Radius.medium, style: .continuous)
                    .strokeBorder(selected ? Theme.primary : Theme.outlineVariant, lineWidth: selected ? 2 : 1)
            )
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selected)
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The twelve palettes as round swatches, plus "new colour with every word".
struct PaletteControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Overline(text: L10n.t("label_palette"))
            FlowLayout(spacing: 10, lineSpacing: 12) {
                ForEach(Palettes.all) { palette in
                    PaletteSwatch(palette: palette, selected: !settings.rotatePalette && settings.paletteId == palette.id) {
                        var s = settings
                        s.paletteId = palette.id
                        s.rotatePalette = false
                        settings = s
                    }
                }
            }
            ToggleRow(title: L10n.t("palette_rotate"), isOn: $settings.rotatePalette)
                .padding(.top, 6)
        }
    }
}

private struct PaletteSwatch: View {
    let palette: Palette
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    Circle().fill(Color(uiColor: palette.bg))
                    Circle().strokeBorder(Color.black.opacity(0.06), lineWidth: 1)
                    Circle().fill(Color(uiColor: palette.accent)).frame(width: 16, height: 16)
                    Image(systemName: "checkmark")
                        .font(.system(size: 8, weight: .black))
                        .foregroundStyle(.white)
                        .scaleEffect(selected ? 1 : 0.01)
                        .opacity(selected ? 1 : 0)
                }
                .padding(selected ? 5 : 2)
                .overlay(Circle().strokeBorder(Theme.primary, lineWidth: selected ? 3 : 0))
                .frame(width: 50, height: 50)
                .animation(.spring(response: 0.3, dampingFraction: 0.6), value: selected)
                Text(L10n.t(palette.nameKey))
                    .textStyle(TypeScale.labelSmall)
                    .foregroundStyle(selected ? Theme.primary : Theme.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(width: 58)
        }
        .buttonStyle(PressableStyle(scale: 0.88))
        .accessibilityLabel(L10n.t(palette.nameKey))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Where the card sits: three small phone diagrams.
struct LayoutControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Overline(text: L10n.t("label_layout"))
            HStack(alignment: .top, spacing: 10) {
                ForEach(LayoutPreset.allCases, id: \.self) { preset in
                    LayoutOption(preset: preset, selected: settings.layout == preset) {
                        settings.layout = preset
                    }
                }
            }
            Text(L10n.t("layout_note"))
                .textStyle(TypeScale.bodySmall)
                .foregroundStyle(Theme.onSurfaceVariant)
                .padding(.top, 10)
        }
    }
}

private struct LayoutOption: View {
    let preset: LayoutPreset
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let key = "layout_\(preset.rawValue)"
        Button(action: action) {
            VStack(spacing: 0) {
                LayoutDiagram(preset: preset)
                    .aspectRatio(9.0 / 18.0, contentMode: .fit)
                    .padding(.horizontal, 14)
                Text(L10n.t(key))
                    .textStyle(TypeScale.labelLarge)
                    .foregroundStyle(Theme.onSurface)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.top, 8)
                Text(L10n.t(key + "_desc"))
                    .textStyle(TypeScale.bodySmall)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 8)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: Radius.medium, style: .continuous)
                    .fill(selected ? Theme.primaryContainer.opacity(0.45) : Theme.surfaceContainerLowest)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Radius.medium, style: .continuous)
                    .strokeBorder(selected ? Theme.primary : Theme.outlineVariant, lineWidth: selected ? 2 : 1)
            )
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selected)
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// A miniature screen: clock bar (lock and compact), tile, word and translation lines in the band
/// the renderer uses for this preset.
private struct LayoutDiagram: View {
    let preset: LayoutPreset

    private static func band(_ preset: LayoutPreset) -> (top: CGFloat, height: CGFloat) {
        switch preset {
        case .lock: return (0.34, 0.52)
        case .home: return (0.18, 0.64)
        case .compact: return (0.62, 0.26)
        }
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let inner = max(0, w - 16)
            let band = Self.band(preset)
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.secondaryContainer)
                RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Theme.outlineVariant, lineWidth: 1)
                if preset != .home {
                    Capsule()
                        .fill(Theme.primary.opacity(0.35))
                        .frame(width: inner * 0.42, height: 5)
                        .offset(y: h * 0.08)
                }
                VStack(spacing: 3) {
                    if preset != .compact {
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(Theme.secondary.opacity(0.7))
                            .frame(width: 12, height: 12)
                            .padding(.bottom, 1)
                    }
                    Capsule().fill(Theme.primary).frame(width: inner * 0.7, height: 4)
                    Capsule().fill(Theme.primary.opacity(0.45)).frame(width: inner * 0.5, height: 3)
                    if preset != .compact {
                        Capsule().fill(Theme.primary.opacity(0.3)).frame(width: inner * 0.55, height: 3)
                    }
                }
                .frame(width: w, height: h * band.height)
                .offset(y: h * band.top)
            }
        }
        .accessibilityHidden(true)
    }
}

struct IntervalControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Overline(text: L10n.t("label_interval"))
            FlowLayout {
                ForEach(Settings.intervals, id: \.self) { minutes in
                    ChoiceChip(text: L10n.interval(minutes), selected: settings.intervalMinutes == minutes) {
                        settings.intervalMinutes = minutes
                    }
                }
            }
        }
    }
}

struct QuietHoursControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ToggleRow(title: L10n.t("label_quiet"), subtitle: L10n.t("quiet_desc"), isOn: $settings.quietEnabled)
            if settings.quietEnabled {
                HStack(spacing: 10) {
                    TimeButton(label: L10n.t("quiet_from"), minutes: $settings.quietStart)
                    TimeButton(label: L10n.t("quiet_to"), minutes: $settings.quietEnd)
                }
                .padding(.top, 6)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: settings.quietEnabled)
    }
}

/// Daily notification switch with its time. Turning it on asks for permission.
struct NotificationControls: View {
    @Binding var settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ToggleRow(
                title: L10n.t("notif_daily"),
                subtitle: L10n.t("notif_daily_desc"),
                isOn: Binding(
                    get: { settings.notifyDaily },
                    set: { on in
                        if on { DailyNotification.requestPermission() }
                        settings.notifyDaily = on
                    }
                )
            )
            if settings.notifyDaily {
                TimeButton(label: L10n.t("notif_at"), minutes: $settings.notifyMinute)
                    .padding(.top, 6)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: settings.notifyDaily)
    }
}

/// Tappable time field that opens a 24-hour wheel picker in a small sheet.
struct TimeButton: View {
    let label: String
    @Binding var minutes: Int
    @State private var open = false
    @State private var picked = Date()

    var body: some View {
        Button {
            picked = Self.date(from: minutes)
            open = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "clock")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Theme.onSurfaceVariant)
                VStack(alignment: .leading, spacing: 0) {
                    Text(label)
                        .textStyle(TypeScale.labelMedium)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(L10n.minutes(minutes))
                        .textStyle(TypeScale.titleMedium)
                        .foregroundStyle(Theme.onSurface)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).fill(Theme.surfaceContainerHigh))
        }
        .buttonStyle(PressableStyle())
        .sheet(isPresented: $open) {
            TimePickerSheet(date: $picked) { confirmed in
                if confirmed { minutes = Self.minutes(from: picked) }
                open = false
            }
        }
    }

    static func date(from minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: (minutes / 60) % 24, minute: minutes % 60, second: 0, of: Date()) ?? Date()
    }

    static func minutes(from date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}

private struct TimePickerSheet: View {
    @Binding var date: Date
    let onDone: (Bool) -> Void

    var body: some View {
        VStack(spacing: 4) {
            Text(L10n.t("time_picker_title"))
                .textStyle(TypeScale.titleLarge)
                .foregroundStyle(Theme.onSurface)
                .padding(.top, 24)
            DatePicker(selection: $date, displayedComponents: .hourAndMinute) {
                EmptyView()
            }
            .datePickerStyle(.wheel)
            .labelsHidden()
            HStack(spacing: 12) {
                Button {
                    onDone(false)
                } label: {
                    Text(L10n.t("dialog_cancel"))
                        .textStyle(TypeScale.labelLarge)
                        .foregroundStyle(Theme.primary)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Capsule().fill(Theme.surfaceContainerHigh))
                }
                .buttonStyle(PressableStyle())
                Button(L10n.t("dialog_ok")) { onDone(true) }
                    .buttonStyle(PrimaryButtonStyle(height: 50))
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity)
        .background(Theme.surfaceContainerLowest)
        .presentationDetents([.height(380)])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(Radius.extraLarge)
    }
}
