import SwiftUI
import UIKit

/// The real card for `word` with `settings`, rendered off the main thread (half resolution by
/// default) and shown in a phone-shaped frame. Finished renders are cached, so a view that is built
/// again (a tab coming back, or the whole tree after a language switch) shows its card in the first
/// frame; a new word or look cross-fades in. `showClock` draws a lock-screen clock where iOS puts
/// it, so the user sees why the card sits low. Port of android/.../ui/components/CardPreview.kt.
struct CardPreview: View {
    let settings: Settings
    let word: Word?
    let paletteIndex: Int
    var corner: CGFloat = 30
    var showClock = false
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
        let base = baseKey(palette: palette, width: renderWidth)
        let key = base + "|" + L10n.language.tag
        // Exactly this card if it was rendered before; else the same card in another interface language
        // (they differ only in the small part-of-speech tag) or, for a full-size view, the half-size one,
        // until the exact render cross-fades in.
        let shown = rendered?.key == key ? rendered
            : Self.cached(key) ?? rendered ?? Self.cached(base) ?? Self.cached(baseKey(palette: palette, width: 540))
        ZStack {
            Color(uiColor: palette.bg)
            if let shown {
                Image(uiImage: shown.image)
                    .resizable()
                    .scaledToFill()
                    .id(shown.key)
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
        .animation(.easeInOut(duration: 0.3), value: shown?.key)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spokenDescription)
        .task(id: key) {
            guard let word, rendered?.key != key else { return }
            if let hit = Self.cached(key) {
                rendered = hit
                return
            }
            let image = await Self.render(settings: settings, word: word, paletteIndex: paletteIndex, width: renderWidth)
            guard !Task.isCancelled else { return }
            let result = Rendered(key: key, image: image)
            if renderWidth <= 720 {
                Self.cache.setObject(RenderBox(result), forKey: key as NSString)
                Self.cache.setObject(RenderBox(result), forKey: base as NSString)
            }
            rendered = result
        }
    }

    /// Everything the rendered card depends on except the interface language, as a cache key.
    private func baseKey(palette: Palette, width: CGFloat) -> String {
        let s = settings
        return [
            word?.id ?? "-", palette.id, String(Int(width)), s.headline.code,
            s.translations.map { $0.code }.joined(separator: "+"), String(s.showTranscriptions), String(s.showExamples),
            s.layout.rawValue,
        ].joined(separator: "|")
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

    /// The last few half-size renders, each under its exact key and under its language-free key
    /// (full-size renders are shown once and not kept).
    private static let cache: NSCache<NSString, RenderBox> = {
        let c = NSCache<NSString, RenderBox>()
        c.countLimit = 10
        return c
    }()

    private static func cached(_ key: String) -> Rendered? { cache.object(forKey: key as NSString)?.value }

    /// A finished render and the key it was made for; the key drives the cross-fade.
    private struct Rendered {
        let key: String
        let image: UIImage
    }

    private final class RenderBox {
        let value: Rendered
        init(_ value: Rendered) { self.value = value }
    }
}
