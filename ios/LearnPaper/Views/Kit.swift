import SwiftUI
import UIKit

// Building blocks shared by every screen; port of android/.../ui/components/Kit.kt.

/// Gentle shrink while pressed, springing back on release, with a light haptic tap: the tactile feel
/// of every tappable surface.
struct PressableStyle: ButtonStyle {
    var scale: CGFloat = 0.96
    var haptic = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light, intensity: 0.6), trigger: configuration.isPressed) { _, pressed in
                haptic && pressed
            }
    }
}

/// The big Iris button ("Next word", "Continue", "Show on wallpaper").
struct PrimaryButtonStyle: ButtonStyle {
    var height: CGFloat = 56
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .textStyle(TypeScale.labelLarge)
            .foregroundStyle(Theme.onPrimary)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(Capsule().fill(Theme.primary))
            .opacity(isEnabled ? 1 : 0.5)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light, intensity: 0.7), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

/// Rounded card that groups related content; the building block of Today and Settings.
struct SectionCard<Content: View>: View {
    var title: String? = nil
    var systemImage: String? = nil
    var insets = EdgeInsets(top: 18, leading: 20, bottom: 18, trailing: 20)
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                HStack(spacing: 12) {
                    if let systemImage {
                        Image(systemName: systemImage)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Theme.onPrimaryContainer)
                            .frame(width: 30, height: 30)
                            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.primaryContainer))
                    }
                    Text(title)
                        .textStyle(TypeScale.titleMedium)
                        .foregroundStyle(Theme.onSurface)
                }
                .padding(.bottom, 14)
                .accessibilityAddTraits(.isHeader)
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(insets)
        .background(RoundedRectangle(cornerRadius: Radius.large, style: .continuous).fill(Theme.surfaceContainerLowest))
        .overlay(RoundedRectangle(cornerRadius: Radius.large, style: .continuous).strokeBorder(Theme.outlineVariant.opacity(0.7), lineWidth: 1))
    }
}

/// Small uppercase label above a group of controls.
struct Overline: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .textStyle(TypeScale.overline)
            .foregroundStyle(Theme.onSurfaceVariant)
            .padding(.bottom, 10)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A title/subtitle row with a switch; the whole row toggles.
struct ToggleRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .textStyle(TypeScale.bodyLarge)
                    .foregroundStyle(Theme.onSurface)
                if let subtitle {
                    Text(subtitle)
                        .textStyle(TypeScale.bodySmall)
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
        }
        .tint(Theme.primary)
        .padding(.vertical, 8)
        .sensoryFeedback(.selection, trigger: isOn)
    }
}

/// A row that opens something: icon, title, optional value on the right.
struct ActionRow: View {
    let title: String
    var systemImage: String? = nil
    var value: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(width: 24)
                }
                Text(title)
                    .textStyle(TypeScale.bodyLarge)
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let value {
                    Text(value)
                        .textStyle(TypeScale.titleSmall)
                        .foregroundStyle(Theme.primary)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.outline)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle(scale: 0.98))
    }
}

/// Pill-shaped single choice with a sliding thumb: the interface language, the learned language.
struct SegmentedControl<Item: Hashable>: View {
    let items: [Item]
    let selection: Item
    var height: CGFloat = 46
    let label: (Item) -> String
    let onSelect: (Item) -> Void
    @Namespace private var thumb

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items, id: \.self) { item in
                let selected = item == selection
                Button {
                    if !selected { onSelect(item) }
                } label: {
                    Text(label(item))
                        .textStyle(TypeScale.labelLarge)
                        .foregroundStyle(selected ? Theme.onSurface : Theme.onSurfaceVariant)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .padding(.horizontal, 6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            if selected {
                                Capsule()
                                    .fill(Theme.surfaceContainerLowest)
                                    .shadow(color: Theme.cardShadow, radius: 3, y: 1)
                                    .matchedGeometryEffect(id: "thumb", in: thumb)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(4)
        .frame(height: height)
        .background(Capsule().fill(Theme.surfaceContainerHigh))
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: selection)
        .sensoryFeedback(.selection, trigger: selection)
    }
}

/// Rounded selectable chip with a soft fill when selected.
struct ChoiceChip: View {
    let text: String
    let selected: Bool
    var systemImage: String? = nil
    /// Icon colour while not selected.
    var imageTint: Color? = nil
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(selected ? Theme.onPrimary : (imageTint ?? Theme.onSurfaceVariant))
                }
                Text(text)
                    .textStyle(TypeScale.labelLarge)
                    .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurface)
                    .lineLimit(1)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(Capsule().fill(selected ? Theme.primary : Theme.surfaceContainerHigh))
            .animation(.easeOut(duration: 0.2), value: selected)
        }
        .buttonStyle(PressableStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// CEFR level badge in the level's own tint.
struct LevelBadge: View {
    let level: String

    var body: some View {
        Text(level)
            .textStyle(TypeScale.labelMedium.weighted(700))
            .foregroundStyle(Theme.onLevel)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(Theme.level(level)))
    }
}

/// "EN" / "RU" / "TJ" tag in front of a translation.
struct LangTag: View {
    let lang: Lang

    var body: some View {
        Text(lang.label)
            .textStyle(TypeScale.labelSmall)
            .foregroundStyle(Theme.onSurfaceVariant)
            .frame(minWidth: 18)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 7, style: .continuous).fill(Theme.surfaceContainerHigh))
    }
}

/// Round icon button with a tinted fill; used for favourite / learned / listen.
struct CircleIconButton: View {
    let systemImage: String
    let label: String
    var container: Color = Theme.surfaceContainerHigh
    var content: Color = Theme.onSurface
    var size: CGFloat = 52
    var iconSize: CGFloat = 21
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: iconSize, weight: .semibold))
                .foregroundStyle(content)
                .frame(width: size, height: size)
                .background(Circle().fill(container))
                .contentShape(Circle())
                .animation(.easeOut(duration: 0.2), value: systemImage)
        }
        .buttonStyle(PressableStyle(scale: 0.9))
        .accessibilityLabel(label)
    }
}

/// A number with a caption, e.g. "42 / seen"; the number rolls when it changes.
struct StatTile: View {
    let value: Int
    let label: String
    var accent: Color = Theme.onSurface

    var body: some View {
        VStack(spacing: 2) {
            Text(verbatim: "\(value)")
                .textStyle(TypeScale.headlineSmall)
                .foregroundStyle(accent)
                .contentTransition(.numericText())
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: value)
            Text(label)
                .textStyle(TypeScale.labelMedium)
                .foregroundStyle(Theme.onSurfaceVariant)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .padding(.horizontal, 8)
        .background(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).fill(Theme.surfaceContainerLowest))
        .overlay(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous).strokeBorder(Theme.outlineVariant.opacity(0.7), lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

/// The two cards of the logo (Assets.xcassets/BrandMark).
struct BrandMark: View {
    var size: CGFloat = 34

    var body: some View {
        Image("BrandMark")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// "Learn" in ink + "Paper" in Iris.
struct Wordmark: View {
    var style: TypeStyle = TypeScale.titleLarge.weighted(800)

    var body: some View {
        (Text(verbatim: "Learn").foregroundColor(Theme.onBackground) + Text(verbatim: "Paper").foregroundColor(Theme.primary))
            .textStyle(style)
            .accessibilityLabel(Text(verbatim: "LearnPaper"))
    }
}

/// Centred brand mark + message for empty lists.
struct EmptyState: View {
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            BrandMark(size: 88)
                .opacity(0.9)
            Text(message)
                .textStyle(TypeScale.bodyLarge)
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 40)
        .padding(.vertical, 48)
    }
}

/// The word's illustration (decoded off the main thread and cached), or its first letter on the
/// level tint for words without one.
struct WordThumb: View {
    let word: Word
    var size: CGFloat = 48
    var headline: Lang = .en
    @State private var image: UIImage? = nil

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                .fill(Theme.level(word.level).opacity(0.7))
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * 0.7, height: size * 0.7)
            } else if word.image == nil {
                Text(String(word.entry(headline).text.prefix(1)).uppercased())
                    .font(.onest(size * 0.44, 800))
                    .foregroundStyle(Theme.onLevel.opacity(0.75))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
        .task(id: word.id) {
            image = await ContentStore.shared.loadThumbnail(for: word, maxPixels: Int(size * 3))
        }
    }
}

/// Snackbar-like message.
struct ToastView: View {
    let text: String

    var body: some View {
        Text(text)
            .textStyle(TypeScale.bodyMedium)
            .foregroundStyle(Theme.inverseOnSurface)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: Radius.large, style: .continuous).fill(Theme.inverseSurface))
            .shadow(color: Theme.cardShadow, radius: 12, y: 4)
            .padding(.horizontal, 24)
    }
}

/// Lays children out left to right and wraps them onto new lines.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                widest = max(widest, x - spacing)
                x = 0
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        widest = max(widest, x - spacing)
        return CGSize(width: proposal.width ?? max(0, widest), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// Fades and slightly grows a tab's content in each time the tab is shown (the Android tab switch).
struct TabEntrance: ViewModifier {
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown ? 1 : 0.985)
            .onAppear {
                withAnimation(.easeOut(duration: 0.24).delay(0.05)) { shown = true }
            }
            .onDisappear { shown = false }
    }
}
