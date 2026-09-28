import CoreText
import SwiftUI
import UIKit

/// The two bundled variable fonts (see project.yml; the files live in android/app/src/main/assets/fonts).
///
/// Onest is the brand face: headings, words, translations, examples and interface text. It covers
/// Latin, Russian and every Tajik letter. Inter is used for transcriptions only, because it has every
/// IPA symbol and the macron/acute vowels of the Latin transliterations. Do not switch fonts without
/// re-checking coverage.
enum FontFace: String {
    case onest = "Onest-Variable"
    case inter = "Inter-Variable"

    /// PostScript name of the default instance, used to find the registered font.
    var postScriptName: String {
        switch self {
        case .onest: return "Onest-Regular"
        case .inter: return "Inter-Regular"
        }
    }
}

enum Fonts {
    private static let lock = NSLock()
    private static var registered = false
    private static var cache: [String: UIFont] = [:]
    private static var ctCache: [String: CTFont] = [:]
    private static let wghtTag = 0x7767_6874 // 'wght'

    /// Registers the bundled font files. `UIAppFonts` in Info.plist does this too; calling it is
    /// harmless (a second registration just fails) and covers an extension whose plist lacks the key.
    static func register() {
        lock.lock()
        defer { lock.unlock() }
        registerLocked()
    }

    private static func registerLocked() {
        guard !registered else { return }
        registered = true
        for face in [FontFace.onest, FontFace.inter] {
            if let url = Bundle.main.url(forResource: face.rawValue, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    /// The face at an exact weight (100…900), set through the variable font's `wght` axis.
    static func ui(_ face: FontFace, size: CGFloat, weight: Int) -> UIFont {
        lock.lock()
        defer { lock.unlock() }
        registerLocked()
        let key = "\(face.rawValue)|\(weight)|\(size)"
        if let font = cache[key] { return font }
        let font = make(face, size: size, weight: weight)
        if cache.count > 500 { cache.removeAll() }
        cache[key] = font
        return font
    }

    /// The same face as a Core Text font, for SwiftUI (`Font(CTFont)` keeps the variation axis).
    static func ct(_ face: FontFace, size: CGFloat, weight: Int) -> CTFont {
        lock.lock()
        defer { lock.unlock() }
        registerLocked()
        let key = "\(face.rawValue)|\(weight)|\(size)"
        if let font = ctCache[key] { return font }
        let attributes: [CFString: Any] = [
            kCTFontNameAttribute: face.postScriptName,
            kCTFontVariationAttribute: [NSNumber(value: wghtTag): NSNumber(value: weight)],
        ]
        let descriptor = CTFontDescriptorCreateWithAttributes(attributes as CFDictionary)
        let font = CTFontCreateWithFontDescriptor(descriptor, size, nil)
        if ctCache.count > 500 { ctCache.removeAll() }
        ctCache[key] = font
        return font
    }

    static func onest(size: CGFloat, weight: Int) -> UIFont { ui(.onest, size: size, weight: weight) }

    static func inter(size: CGFloat, weight: Int) -> UIFont { ui(.inter, size: size, weight: weight) }

    private static func make(_ face: FontFace, size: CGFloat, weight: Int) -> UIFont {
        guard let base = UIFont(name: face.postScriptName, size: size) else {
            return UIFont.systemFont(ofSize: size, weight: systemWeight(weight))
        }
        let variation = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
        let descriptor = base.fontDescriptor.addingAttributes([
            variation: [NSNumber(value: wghtTag): NSNumber(value: weight)],
        ])
        return UIFont(descriptor: descriptor, size: size)
    }

    private static func systemWeight(_ w: Int) -> UIFont.Weight {
        switch w {
        case ..<350: return .light
        case ..<450: return .regular
        case ..<550: return .medium
        case ..<650: return .semibold
        case ..<750: return .bold
        default: return .heavy
        }
    }
}

extension Font {
    /// A brand font for SwiftUI at an exact size and weight (fixed sizes, like the Android type scale).
    static func brand(_ face: FontFace, size: CGFloat, weight: Int) -> Font {
        Font(Fonts.ct(face, size: size, weight: weight))
    }

    static func onest(_ size: CGFloat, _ weight: Int = 400) -> Font { brand(.onest, size: size, weight: weight) }

    static func inter(_ size: CGFloat, _ weight: Int = 400) -> Font { brand(.inter, size: size, weight: weight) }
}
