import SwiftUI
import UIKit

/// The real card for `word` with `settings`, rendered off the main thread (half resolution by
/// default) and shown in a phone-shaped frame. A new word or look cross-fades in. `showClock` draws
/// a lock-screen clock where iOS puts it, so the user sees why the card sits low; `float` makes the
/// card bob gently. Port of android/.../ui/components/CardPreview.kt.
struct CardPreview: View {
    let settings: Settings
    let word: Word?
    let paletteIndex: Int
    var corner: CGFloat = 30
    var showClock = false
    var float = false
    /// Render width in pixels; full-screen views use the real screen width.
    var renderWidth: CGFloat = 540

    @State private var rendered: Rendered? = nil

    /// Portrait screen proportions (width / height).
    static var aspect: CGFloat {
        let size = UIScreen.main.bounds.size
        return min(size.width, size.height) / max(size.width, size.height)
    }

    static var screenWidth: CGFloat {
        let size = UIScreen.main.bounds.size
        return min(size.width, size.height)
    }

    var body: some View {
        let palette = Palettes.forSettings(settings, index: paletteIndex)
        let shape = RoundedRectangle(cornerRadius: corner, style: .continuous)
        ZStack {
            Color(uiColor: palette.bg)
            if let rendered {
                Image(uiImage: rendered.image)
                    .resizable()
                    .scaledToFill()
                    .id(rendered.id)
                    .transition(.opacity)
            }
            if showClock && settings.layout != .home {
                GeometryReader { geo in
                    Text(verbatim: "09:41")
                        .font(.onest(geo.size.width * 0.22, 300))
                        .foregroundStyle(Color(uiColor: palette.text).opacity(0.9))
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .padding(.top, geo.size.height * 0.1)
                }
                .accessibilityHidden(true)
            }
        }
        .aspectRatio(Self.aspect, contentMode: .fit)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Color.black.opacity(0.06), lineWidth: 1))
        .shadow(color: Theme.cardShadow, radius: 16, y: 10)
        .modifier(FloatEffect(enabled: float))
        .animation(.easeInOut(duration: 0.45), value: rendered?.id)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spokenDescription)
        .task(id: RenderKey(settings: settings, wordId: word?.id, paletteIndex: paletteIndex, width: renderWidth)) {
            guard let word else { return }
            let image = await Self.render(settings: settings, word: word, paletteIndex: paletteIndex, width: renderWidth)
            if !Task.isCancelled { rendered = Rendered(id: (rendered?.id ?? 0) + 1, image: image) }
        }
    }

    /// "word — перевод — тарҷума" for VoiceOver.
    private var spokenDescription: String {
        guard let word else { return "" }
        return ([settings.headline] + settings.translations).map { word.entry($0).text }.joined(separator: " — ")
    }

    /// Renders a card on a background thread.
    static func render(settings: Settings, word: Word, paletteIndex: Int, width: CGFloat) async -> UIImage {
        let height = (width / aspect).rounded()
        return await Task.detached(priority: .userInitiated) {
            CardRenderer.shared.render(
                word: word,
                settings: settings,
                palette: Palettes.forSettings(settings, index: paletteIndex),
                width: width,
                height: height
            )
        }.value
    }

    /// A finished render; the counter drives the cross-fade (comparing images would be slow).
    private struct Rendered {
        let id: Int
        let image: UIImage
    }

    private struct RenderKey: Equatable {
        let settings: Settings
        let wordId: String?
        let paletteIndex: Int
        let width: CGFloat
    }
}

/// Slow up-and-down bob of a floating card.
private struct FloatEffect: ViewModifier {
    let enabled: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.phaseAnimator([false, true]) { view, up in
                view.offset(y: up ? -6 : 0)
            } animation: { _ in
                .easeInOut(duration: 3.8)
            }
        } else {
            content
        }
    }
}
