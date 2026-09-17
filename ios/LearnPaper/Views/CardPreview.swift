import SwiftUI

/// Live preview of the card for `word` with `settings`, rendered at a reduced resolution.
struct CardPreview: View {
    @EnvironmentObject private var model: AppModel
    let settings: Settings
    let word: Word?
    let paletteIndex: Int
    var corner: CGFloat = 28

    @State private var image: UIImage?

    private var aspect: CGFloat {
        let s = UIScreen.main.bounds.size
        return min(s.width, s.height) / max(s.width, s.height)
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: corner).fill(Color(.secondarySystemBackground))
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
            }
        }
        .aspectRatio(aspect, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: corner))
        .task(id: RenderKey(settings: settings, wordId: word?.id, paletteIndex: paletteIndex)) {
            guard let word else { return }
            let w: CGFloat = 540
            image = await model.renderPreview(settings: settings, word: word, paletteIndex: paletteIndex, width: w, height: (w / aspect).rounded())
        }
    }

    private struct RenderKey: Equatable {
        let settings: Settings
        let wordId: String?
        let paletteIndex: Int
    }
}
