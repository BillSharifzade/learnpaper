import Foundation
import ImageIO
import UIKit

/// The bundled content pack (words.json + images/, produced by content/scripts/build.py and copied
/// into both the app and the widget bundle). Loaded once; safe to use from any thread.
final class ContentStore {
    static let shared = ContentStore()

    let words: [Word]
    let index: WordIndex
    /// Levels present in the content, in CEFR order.
    let levels: [String]
    let levelCounts: [String: Int]
    /// Word id → level, for the stats.
    let wordLevels: [String: String]

    private let images = NSCache<NSString, UIImage>()
    private let thumbnails = NSCache<NSString, UIImage>()

    init(bundle: Bundle = .main) {
        var loaded: [Word] = []
        if let url = bundle.url(forResource: "words", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let pack = try? JSONDecoder().decode(ContentPack.self, from: data) {
            loaded = pack.words
        }
        words = loaded
        index = WordIndex(loaded)
        levels = Levels.sorted(loaded.map { $0.level })
        var counts: [String: Int] = [:]
        var byLevel: [String: String] = [:]
        for w in loaded {
            counts[w.level, default: 0] += 1
            byLevel[w.id] = w.level
        }
        levelCounts = counts
        wordLevels = byLevel
        images.countLimit = 16
        thumbnails.totalCostLimit = 12 * 1024 * 1024
    }

    func word(_ id: String) -> Word? { index.byId[id] }

    var availableLevels: [String] { levels }

    private func url(for word: Word) -> URL? {
        guard let name = word.image, !name.isEmpty else { return nil }
        let base = (name as NSString).deletingPathExtension
        let ext = (name as NSString).pathExtension
        return Bundle.main.url(forResource: base, withExtension: ext, subdirectory: "images")
            ?? Bundle.main.url(forResource: base, withExtension: ext)
    }

    /// The word's illustration at full size (512 px lossless WebP), or nil when it has none.
    func image(for word: Word) -> UIImage? {
        guard let name = word.image, let url = url(for: word) else { return nil }
        let key = name as NSString
        if let cached = images.object(forKey: key) { return cached }
        guard let image = UIImage(contentsOfFile: url.path) else { return nil }
        images.setObject(image, forKey: key)
        return image
    }

    /// A small decoded copy of the illustration for lists and widgets, cached. `maxPixels` is the
    /// longest side in pixels.
    func thumbnail(for word: Word, maxPixels: Int = 144) -> UIImage? {
        guard let name = word.image, let url = url(for: word) else { return nil }
        let key = "\(name)@\(maxPixels)" as NSString
        if let cached = thumbnails.object(forKey: key) { return cached }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixels,
        ]
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        else { return nil }
        let image = UIImage(cgImage: cgImage)
        thumbnails.setObject(image, forKey: key, cost: cgImage.bytesPerRow * cgImage.height)
        return image
    }

    /// The thumbnail if it is already decoded; never touches the disk.
    func cachedThumbnail(for word: Word, maxPixels: Int = 144) -> UIImage? {
        guard let name = word.image else { return nil }
        return thumbnails.object(forKey: "\(name)@\(maxPixels)" as NSString)
    }

    /// Thumbnail decoded off the main thread, for list rows.
    func loadThumbnail(for word: Word, maxPixels: Int = 144) async -> UIImage? {
        if word.image == nil { return nil }
        return await Task.detached(priority: .userInitiated) { [self] in
            thumbnail(for: word, maxPixels: maxPixels)
        }.value
    }
}
