import Foundation
import UIKit

/// Loads the bundled content pack (words.json + images/, produced by content/scripts/build.py and
/// copied into both the app and the widget bundle).
final class ContentStore {
    static let shared = ContentStore()

    private(set) lazy var words: [Word] = {
        guard let url = Bundle.main.url(forResource: "words", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let pack = try? JSONDecoder().decode(ContentPack.self, from: data)
        else { return [] }
        return pack.words
    }()

    private lazy var byId: [String: Word] = Dictionary(uniqueKeysWithValues: words.map { ($0.id, $0) })

    func word(_ id: String) -> Word? { byId[id] }

    var availableLevels: [String] { Levels.sorted(words.map(\.level)) }

    /// The word's illustration, or nil when it has none.
    func image(for word: Word) -> UIImage? {
        guard let name = word.image else { return nil }
        let base = (name as NSString).deletingPathExtension
        let ext = (name as NSString).pathExtension
        guard let url = Bundle.main.url(forResource: base, withExtension: ext, subdirectory: "images")
            ?? Bundle.main.url(forResource: base, withExtension: ext)
        else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
}
