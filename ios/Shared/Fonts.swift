import UIKit
import CoreText

/// Inter variable font, bundled once for both targets (see project.yml). Covers Latin, IPA,
/// Russian and Tajik Cyrillic; do not switch fonts without re-checking coverage.
enum Fonts {
    private static var registered = false
    private static let wghtTag: Int = 0x7767_6874 // 'wght'

    /// Registers the bundled font file. `UIAppFonts` in Info.plist does this too; calling it is
    /// harmless and covers extensions whose plist was not updated.
    static func register() {
        guard !registered else { return }
        registered = true
        if let url = Bundle.main.url(forResource: "Inter-Variable", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    private static var familyName: String? = {
        register()
        return UIFont.familyNames.first { $0.hasPrefix("Inter") }
    }()

    static func inter(size: CGFloat, weight: Int) -> UIFont {
        guard let family = familyName else {
            return UIFont.systemFont(ofSize: size, weight: systemWeight(weight))
        }
        let variation = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: family,
            variation: [NSNumber(value: wghtTag): NSNumber(value: weight)],
        ])
        return UIFont(descriptor: descriptor, size: size)
    }

    private static func systemWeight(_ w: Int) -> UIFont.Weight {
        switch w {
        case ..<450: return .regular
        case ..<550: return .medium
        case ..<650: return .semibold
        default: return .bold
        }
    }
}
